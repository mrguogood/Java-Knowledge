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
import com.github.benmanes.caffeine.cache.Cache;
import com.guo.common.ChatSentinelHandler;
import com.guo.service.chatService.ChatService;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.Collections;
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

    private MultiModalMessage buildSystemMsg() {
        return MultiModalMessage.builder()
                .role(Role.SYSTEM.getValue()) // 系统角色
                .content(Arrays.asList(Collections.singletonMap("text", SYSTEM_PROMPT))) // 系统提示
                .build();
    }

    private MultiModalMessage buildUserMsg(String text) {
        return MultiModalMessage.builder()
                .role(Role.USER.getValue()) // 用户角色
                .content(Arrays.asList(Collections.singletonMap("text", text))) // 用户问题
                .build();
    }

    private MultiModalConversationParam buildParam(String userMessage) {
        return MultiModalConversationParam.builder()
                .apiKey(apiKey)
                .model(model) // 模型名称
                .messages(Arrays.asList(buildSystemMsg(), buildUserMsg(userMessage))) // 对话消息列表
                .temperature(temperature) // 温度参数，控制输出的随机性
                .maxTokens(maxTokens) // 最大输出token数
                .incrementalOutput(true) // 开启增量输出
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

    @Override
    public void streamChat(String question, SseEmitter emitter) throws Exception {
        Entry entry = null;
        boolean acquired = false;
        try {
            // 1 Sentinel 限流检查
            entry = SphU.entry("chat_stream_interface");

            // 2 Semaphore 并发控制
            acquired = modelSemaphore.tryAcquire(30, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("流式接口信号量获取失败，问题：{}", question);
                sendAndComplete(emitter, "当前咨询人数较多，请稍后再试");
                return;
            }

            // 3 缓存检查：命中后逐字推送，与流式一致的打字机效果
            String cacheKey = question.trim().toLowerCase();
            String cachedAnswer = chatAnswerCache.getIfPresent(cacheKey);
            if (cachedAnswer != null) {
                log.info("流式接口缓存命中，问题：{}", question);
                try { Thread.sleep(80); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }

                for (int i = 0; i < cachedAnswer.length(); i++) {
                    char c = cachedAnswer.charAt(i);
                    if (c == '\n') {
                        emitter.send(SseEmitter.event().name("message").data("__NL__"));
                    } else {
                        emitter.send(SseEmitter.event().name("message").data(String.valueOf(c)));
                    }
                    if (i % 3 == 0) {
                        try { Thread.sleep(12); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                    }
                }
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
                return;
            }

            // 4 流式调用大模型
            MultiModalConversationParam param = buildParam(question);
            MultiModalConversation conv = new MultiModalConversation();
            Flowable<MultiModalConversationResult> flow = conv.streamCall(param);

            StringBuilder fullAnswer = new StringBuilder();

            // 回调式消费 Flowable（类似 ResultCallback 的 onEvent/onComplete/onError）
            flow.blockingForEach(result -> {
                String chunk = extractContent(result);
                if (chunk.isEmpty()) {
                    return;
                }
                fullAnswer.append(chunk);
                try {
                    emitter.send(SseEmitter.event().name("message").data(chunk.replace("\n", "__NL__")));
                } catch (IllegalStateException ex) {
                    throw new RuntimeException("SSE emitter already completed, abort streaming", ex);
                }
            });

            // 5 完整答案写入缓存
            if (fullAnswer.length() > 0) {
                chatAnswerCache.put(cacheKey, fullAnswer.toString());
            }
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();

        } catch (BlockException e) {
            // Sentinel 限流/熔断 → 推送兜底消息
            log.warn("流式接口被 Sentinel 限流/熔断，问题：{}", question);
            sendAndComplete(emitter, ChatSentinelHandler.chatBlockHandler(question, e));
        } catch (Exception e) {
            // 业务异常 → 记录到 Sentinel 熔断统计
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

    private void sendAndComplete(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("message").data(message));
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (Exception ignored) {
        }
    }
}