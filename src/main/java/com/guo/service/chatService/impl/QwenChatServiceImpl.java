package com.guo.service.chatService.impl;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.github.benmanes.caffeine.cache.Cache;
import com.guo.common.ChatSentinelHandler;
import com.guo.untils.LocalMemoryStore;
import com.guo.service.chatService.ChatService;
import com.guo.untils.QwenChatMessageBuilder;
import com.guo.untils.RedisChatHistoryStore;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    @Autowired
    private Semaphore modelSemaphore;

    @Autowired
    private Cache<String, String> chatAnswerCache;

    @Autowired
    private LocalMemoryStore localMemoryStore;

    @Autowired
    private QwenChatMessageBuilder messageBuilder;

    @Autowired
    private RedisChatHistoryStore redisChatHistoryStore;

    // ==================== 单轮对话（无记忆）====================

    @Override
    public String singleChat(String userMessage) throws Exception {
        MultiModalConversationParam param = messageBuilder.buildParam(userMessage);
        MultiModalConversationResult result = new MultiModalConversation().call(param);
        return QwenChatMessageBuilder.extractContent(result);
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
            MultiModalConversationParam param = messageBuilder.buildParam(question);
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
                history.add(messageBuilder.buildSystemMsg());
            }

            // 构建带记忆的请求参数
            MultiModalConversationParam param = messageBuilder.buildMemoryParam(question, history);
            String fullAnswer = doStreamCall(param, emitter);

            // 将本轮问答追加到历史
            localMemoryStore.append(sessionId,
                    messageBuilder.buildUserMsg(question),
                    messageBuilder.buildAssistantMsg(fullAnswer));

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
        if (!redisChatHistoryStore.isAvailable()) {
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

            // 从 Redis 获取历史消息（system 拼在最前）
            List<MultiModalMessage> history = redisChatHistoryStore.getHistory(sessionId);

            // 如果是全新的会话，写入 system 人设
            boolean isNew = history.isEmpty();
            if (isNew) {
                MultiModalMessage systemMsg = messageBuilder.buildSystemMsg();
                history.add(systemMsg);
                redisChatHistoryStore.initSession(sessionId, systemMsg);
            }

            // 构建带记忆的请求参数
            MultiModalConversationParam param = messageBuilder.buildMemoryParam(question, history);
            String fullAnswer = doStreamCall(param, emitter);

            // 将本轮 user + assistant 追加到 Redis
            redisChatHistoryStore.append(sessionId,
                    messageBuilder.buildUserMsg(question),
                    messageBuilder.buildAssistantMsg(fullAnswer));

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

    // ==================== 工具方法 ====================

    /**
     * 执行流式调用，返回完整回答
     */
    private String doStreamCall(MultiModalConversationParam param, SseEmitter emitter) throws Exception {
        MultiModalConversation conv = new MultiModalConversation();
        Flowable<MultiModalConversationResult> flow = conv.streamCall(param);

        StringBuilder fullAnswer = new StringBuilder();
        flow.blockingForEach(result -> {
            String chunk = QwenChatMessageBuilder.extractContent(result);
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