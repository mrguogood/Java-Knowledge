package com.guo.service.chatService.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.guo.common.ChatMessage;
import com.guo.common.ChatSentinelHandler;
import com.guo.service.chatService.ChatService;
import com.guo.service.chatService.memory.LocalMemoryStore;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 通义千问问答服务实现
 *
 * @author gcs
 * @create 2026-09-22
 */
@Slf4j
@Service
public class QwenChatServiceImpl implements ChatService {

    private static final String SYSTEM_PROMPT = "你是专业的Java开发助手，回答精准简洁，只讲技术干货。";

    /** Redis 会话记忆 key 前缀 */
    private static final String REDIS_SESSION_KEY_PREFIX = "chat:session:";
    /** Redis 会话过期时间：1 小时 */
    private static final long REDIS_SESSION_TTL_SECONDS = 3600;
    /** Redis 单会话最大消息数 */
    private static final int REDIS_MAX_MESSAGES = 20;

    @Value("${ai.qwen.api-key}")
    private String apiKey;

    @Value("${ai.qwen.model}")
    private String model;

    @Value("${ai.qwen.temperature}")
    private Float temperature;

    @Value("${ai.qwen.max-tokens}")
    private Integer maxTokens;

    @Autowired
    private Semaphore modelSemaphore;

    @Autowired
    private Cache<String, String> chatAnswerCache;

    @Autowired
    private LocalMemoryStore localMemoryStore;

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 基础构建方法 ====================

    private MultiModalMessage buildSystemMsg() {
        return MultiModalMessage.builder()
                .role(Role.SYSTEM.getValue())
                .content(Arrays.asList(Collections.singletonMap("text", SYSTEM_PROMPT)))
                .build();
    }

    private MultiModalMessage buildUserMsg(String text) {
        return MultiModalMessage.builder()
                .role(Role.USER.getValue())
                .content(Arrays.asList(Collections.singletonMap("text", text)))
                .build();
    }

    private MultiModalMessage buildAssistantMsg(String text) {
        return MultiModalMessage.builder()
                .role(Role.ASSISTANT.getValue())
                .content(Arrays.asList(Collections.singletonMap("text", text)))
                .build();
    }

    /**
     * 构建单轮对话参数（无记忆）
     */
    private MultiModalConversationParam buildParam(String userMessage) {
        return MultiModalConversationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .messages(Arrays.asList(buildSystemMsg(), buildUserMsg(userMessage)))
                .temperature(temperature)
                .maxTokens(maxTokens)
                .incrementalOutput(true)
                .build();
    }

    /**
     * 构建带记忆的对话参数
     * <p>
     * 从历史消息列表中提取 system 消息 + 历史对话 + 当前用户问题，
     * 确保 system 人设始终在第一条。
     */
    private MultiModalConversationParam buildMemoryParam(String userMessage, List<MultiModalMessage> history) {
        List<MultiModalMessage> messages = new ArrayList<>();
        // 1. 从历史中找 system 消息放第一条（一定有，初始化时写入的）
        boolean hasSystem = false;
        for (MultiModalMessage msg : history) {
            if (Role.SYSTEM.getValue().equals(msg.getRole())) {
                messages.add(msg);
                hasSystem = true;
                break;
            }
        }
        // 兜底：历史里没有 system 就新建一个
        if (!hasSystem) {
            messages.add(buildSystemMsg());
        }
        // 2. 追加历史中的 user/assistant 消息（不含 system，因为已经加了）
        for (MultiModalMessage msg : history) {
            if (!Role.SYSTEM.getValue().equals(msg.getRole())) {
                messages.add(msg);
            }
        }
        // 3. 追加当前用户问题
        messages.add(buildUserMsg(userMessage));

        return MultiModalConversationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .messages(messages)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .incrementalOutput(true)
                .build();
    }

    private String extractContent(MultiModalConversationResult result) {
        if (result.getOutput() == null
                || result.getOutput().getChoices() == null
                || result.getOutput().getChoices().isEmpty()) {
            return "";
        }
        MultiModalMessage message = result.getOutput().getChoices().get(0).getMessage();
        if (message.getContent() == null || message.getContent().isEmpty()) {
            return "";
        }
        Object text = message.getContent().get(0).get("text");
        return text != null ? text.toString() : "";
    }

    // ==================== 单轮对话（无记忆）====================

    @Override
    public String singleChat(String userMessage) throws Exception {
        MultiModalConversationParam param = buildParam(userMessage);
        MultiModalConversation conv = new MultiModalConversation();
        MultiModalConversationResult result = conv.call(param);
        return extractContent(result);
    }

    @Override
    @SentinelResource(
            value = "chat_interface",
            blockHandlerClass = ChatSentinelHandler.class,
            blockHandler = "chatBlockHandler",
            fallbackClass = ChatSentinelHandler.class,
            fallback = "chatFallback"
    )
    public String chat(String question) throws Exception {
        return singleChat(question);
    }

    @Override
    public String chatWithCache(String question) throws Exception {
        String cacheKey = question.trim().toLowerCase();

        String cachedAnswer = chatAnswerCache.getIfPresent(cacheKey);
        if (cachedAnswer != null) {
            log.info("缓存命中，问题：{}", question);
            return cachedAnswer;
        }

        String answer = singleChat(question);
        chatAnswerCache.put(cacheKey, answer);
        return answer;
    }

    @Override
    public String chatWithConcurrencyControl(String question) {
        boolean acquired = false;
        try {
            acquired = modelSemaphore.tryAcquire(30, TimeUnit.SECONDS);
            if (!acquired) {
                log.info("模型调用信号量获取失败，问题：{}", question);
                return "当前咨询人数较多，请稍后再试";
            }
            return singleChat(question);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            log.error("模型调用异常，问题：{}", question, e);
            return "请求中断，请重试";
        } finally {
            if (acquired) {
                modelSemaphore.release();
            }
        }
    }

    // ==================== 流式输出（无记忆）====================

    @Override
    public void streamChat(String question, SseEmitter emitter){
        Entry entry = null;
        boolean acquired = false;
        try {
            entry = SphU.entry("chat_stream_interface");

            acquired = modelSemaphore.tryAcquire(30, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("流式接口信号量获取失败，问题：{}", question);
                sendAndComplete(emitter, "当前咨询人数较多，请稍后再试");
                return;
            }

            String cacheKey = question.trim().toLowerCase();
            String cachedAnswer = chatAnswerCache.getIfPresent(cacheKey);
            if (cachedAnswer != null) {
                log.info("流式接口缓存命中，问题：{}", question);
                simulateTyping(emitter, cachedAnswer);
                return;
            }

            // 流式调用大模型
            MultiModalConversationParam param = buildParam(question);
            String fullAnswer = doStreamCall(param, emitter);

            if (fullAnswer.length() > 0) {
                chatAnswerCache.put(cacheKey, fullAnswer);
            }
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();

        } catch (BlockException e) {
            log.warn("流式接口被 Sentinel 限流/熔断，问题：{}", question);
            sendAndComplete(emitter, ChatSentinelHandler.chatBlockHandler(question, e));
        } catch (Exception e) {
            if (entry != null) {
                Tracer.trace(e);
            }
            log.error("流式接口调用异常，问题：{}", question, e);
            sendAndComplete(emitter, ChatSentinelHandler.chatFallback(question));
        } finally {
            if (acquired) {
                modelSemaphore.release();
            }
            if (entry != null) {
                entry.exit();
            }
        }
    }

    // ==================== 基础版：本地内存会话记忆（单机）====================

    @Override
    public void streamChatWithLocalMemory(String question, String sessionId, SseEmitter emitter){
        Entry entry = null;
        boolean acquired = false;
        try {
            entry = SphU.entry("chat_stream_interface");

            acquired = modelSemaphore.tryAcquire(30, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("本地记忆接口信号量获取失败，session: {}", sessionId);
                sendAndComplete(emitter, "当前咨询人数较多，请稍后再试");
                return;
            }

            // 从本地内存获取历史消息
            List<MultiModalMessage> history = localMemoryStore.getHistory(sessionId);

            // 如果是全新的会话，写入 system 人设
            if (history.isEmpty()) {
                history.add(buildSystemMsg());
            }

            // 构建带记忆的请求参数
            MultiModalConversationParam param = buildMemoryParam(question, history);
            MultiModalConversation conv = new MultiModalConversation();
            Flowable<MultiModalConversationResult> flow = conv.streamCall(param);

            StringBuilder fullAnswer = new StringBuilder();
            flow.blockingForEach(result -> {
                String chunk = extractContent(result);
                if (chunk.isEmpty()) {
                    return;
                }
                fullAnswer.append(chunk);
                emitter.send(SseEmitter.event().name("message").data(chunk.replace("\n", "__NL__")));
            });

            // 将本轮问答追加到历史（传入 buildMemoryParam 时已包含 history + 当前问题，
            // 但需要把完整的 user question 和 answer 存回去）
            localMemoryStore.append(sessionId, buildUserMsg(question), buildAssistantMsg(fullAnswer.toString()));

            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
            log.info("本地记忆会话 {} 完成，当前消息数：{}", sessionId, history.size());

        } catch (BlockException e) {
            log.warn("本地记忆接口被 Sentinel 限流/熔断，session: {}", sessionId);
            sendAndComplete(emitter, ChatSentinelHandler.chatBlockHandler(question, e));
        } catch (Exception e) {
            if (entry != null) {
                Tracer.trace(e);
            }
            log.error("本地记忆接口异常，session: {}", sessionId, e);
            sendAndComplete(emitter, ChatSentinelHandler.chatFallback(question));
        } finally {
            if (acquired) {
                modelSemaphore.release();
            }
            if (entry != null) {
                entry.exit();
            }
        }
    }

    // ==================== 进阶版：Redis 分布式会话记忆 ====================

    @Override
    public void streamChatWithRedisMemory(String question, String sessionId, SseEmitter emitter){
        if (redisTemplate == null) {
            log.warn("Redis 未配置，降级为本地记忆，session: {}", sessionId);
            streamChatWithLocalMemory(question, sessionId, emitter);
            return;
        }

        Entry entry = null;
        boolean acquired = false;
        try {
            entry = SphU.entry("chat_stream_interface");

            acquired = modelSemaphore.tryAcquire(30, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("Redis 记忆接口信号量获取失败，session: {}", sessionId);
                sendAndComplete(emitter, "当前咨询人数较多，请稍后再试");
                return;
            }

            // 从 Redis 获取历史消息
            List<MultiModalMessage> history = getRedisHistory(sessionId);

            // 如果是全新的会话，写入 system 人设
            boolean isNew = history.isEmpty();
            if (isNew) {
                history.add(buildSystemMsg());
                // 新会话立即保存 system 消息到 Redis
                saveRedisHistory(sessionId, history);
            }

            // 构建带记忆的请求参数
            MultiModalConversationParam param = buildMemoryParam(question, history);
            MultiModalConversation conv = new MultiModalConversation();
            Flowable<MultiModalConversationResult> flow = conv.streamCall(param);

            StringBuilder fullAnswer = new StringBuilder();
            flow.blockingForEach(result -> {
                String chunk = extractContent(result);
                if (chunk.isEmpty()) {
                    return;
                }
                fullAnswer.append(chunk);
                emitter.send(SseEmitter.event().name("message").data(chunk.replace("\n", "__NL__")));
            });

            // 将本轮 user + assistant 追加到 Redis
            appendRedisHistory(sessionId, buildUserMsg(question), buildAssistantMsg(fullAnswer.toString()));

            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
            log.info("Redis 记忆会话 {} 完成", sessionId);

        } catch (BlockException e) {
            log.warn("Redis 记忆接口被 Sentinel 限流/熔断，session: {}", sessionId);
            sendAndComplete(emitter, ChatSentinelHandler.chatBlockHandler(question, e));
        } catch (Exception e) {
            if (entry != null) {
                Tracer.trace(e);
            }
            log.error("Redis 记忆接口异常，session: {}", sessionId, e);
            sendAndComplete(emitter, ChatSentinelHandler.chatFallback(question));
        } finally {
            if (acquired) {
                modelSemaphore.release();
            }
            if (entry != null) {
                entry.exit();
            }
        }
    }

    // ==================== Redis 操作 ====================

    /**
     * 从 Redis 读取会话历史
     * <p>
     * Redis List 中每个元素是 ChatMessage 的 JSON 字符串，
     * 按对话顺序依次存储（LRANGE 0 -1 获取全部）。
     */
    private List<MultiModalMessage> getRedisHistory(String sessionId) {
        String key = REDIS_SESSION_KEY_PREFIX + sessionId;
        List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
        if (jsonList == null || jsonList.isEmpty()) {
            return new ArrayList<>();
        }

        List<MultiModalMessage> history = new ArrayList<>();
        for (String json : jsonList) {
            try {
                ChatMessage cm = objectMapper.readValue(json, ChatMessage.class);
                history.add(MultiModalMessage.builder()
                        .role(cm.getRole())
                        .content(Arrays.asList(Collections.singletonMap("text", cm.getContent())))
                        .build());
            } catch (Exception e) {
                log.warn("Redis 消息反序列化失败，将跳过，session: {}", sessionId, e);
            }
        }
        return history;
    }

    /**
     * 保存完整历史到 Redis（覆写）
     * 用于新会话初始化
     */
    private void saveRedisHistory(String sessionId, List<MultiModalMessage> history) {
        String key = REDIS_SESSION_KEY_PREFIX + sessionId;
        redisTemplate.delete(key);

        for (MultiModalMessage msg : history) {
            String content = msg.getContent() != null && !msg.getContent().isEmpty()
                    ? msg.getContent().get(0).get("text").toString() : "";
            ChatMessage cm = new ChatMessage(msg.getRole(), content);
            try {
                String json = objectMapper.writeValueAsString(cm);
                redisTemplate.opsForList().rightPush(key, json);
            } catch (Exception e) {
                log.warn("Redis 消息序列化失败，session: {}", sessionId, e);
            }
        }
        // 设置过期时间
        redisTemplate.expire(key, REDIS_SESSION_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 将用户消息 + AI 回复追加到 Redis 历史
     * <p>
     * 追加后做裁剪：超出 REDIS_MAX_MESSAGES 时删最旧的 user/assistant，
     * 保留第一条 system 消息。
     */
    private void appendRedisHistory(String sessionId, MultiModalMessage userMsg, MultiModalMessage assistantMsg) {
        String key = REDIS_SESSION_KEY_PREFIX + sessionId;

        // 序列化并追加 user 消息
        ChatMessage userCm = new ChatMessage(userMsg.getRole(),
                userMsg.getContent().get(0).get("text").toString());
        try {
            redisTemplate.opsForList().rightPush(key, objectMapper.writeValueAsString(userCm));
        } catch (Exception e) {
            log.warn("Redis user 消息序列化失败", e);
        }

        // 序列化并追加 assistant 消息
        ChatMessage assistantCm = new ChatMessage(assistantMsg.getRole(),
                assistantMsg.getContent().get(0).get("text").toString());
        try {
            redisTemplate.opsForList().rightPush(key, objectMapper.writeValueAsString(assistantCm));
        } catch (Exception e) {
            log.warn("Redis assistant 消息序列化失败", e);
        }

        // 裁剪：超出 max 时从左端（最旧）删，保留 system（index 0）
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > REDIS_MAX_MESSAGES) {
            long removeCount = size - REDIS_MAX_MESSAGES;
            // LTRIM 保留 index range，从左删 removeCount 条（跳过 system 消息）
            // system 在 index 0，所以删 index 1 到 removeCount
            redisTemplate.opsForList().trim(key, removeCount, -1);
        }

        // 刷新过期时间
        redisTemplate.expire(key, REDIS_SESSION_TTL_SECONDS, TimeUnit.SECONDS);
    }

    // ==================== 工具方法 ====================

    /**
     * 执行流式调用，返回完整回答
     */
    private String doStreamCall(MultiModalConversationParam param, SseEmitter emitter) throws Exception {
        MultiModalConversation conv = new MultiModalConversation();
        Flowable<MultiModalConversationResult> flow = conv.streamCall(param);

        StringBuilder fullAnswer = new StringBuilder();
        flow.blockingForEach(result -> {
            String chunk = extractContent(result);
            if (chunk.isEmpty()) {
                return;
            }
            fullAnswer.append(chunk);
            emitter.send(SseEmitter.event().name("message").data(chunk.replace("\n", "__NL__")));
        });
        return fullAnswer.toString();
    }

    /**
     * 缓存命中时模拟打字机效果逐字推送
     */
    private void simulateTyping(SseEmitter emitter, String text) throws Exception {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                emitter.send(SseEmitter.event().name("message").data("__NL__"));
            } else {
                emitter.send(SseEmitter.event().name("message").data(String.valueOf(c)));
            }
            if (i % 3 == 0) {
                try { Thread.sleep(12); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            }
        }
    }

    private void sendAndComplete(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("message").data(message));
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (Exception ignored) {
        }
    }
}