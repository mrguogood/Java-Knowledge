package com.guo.controller;

import com.guo.common.ResponseResult;
import com.guo.dto.ChatRequestDto;
import com.guo.service.chatService.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService qwenChatService;

    @Autowired
    private ThreadPoolTaskExecutor streamExecutor;

    /**
     * 受 Sentinel 限流/熔断保护的单轮对话接口
     * chat() 内部有 @SentinelResource 注解，
     * 被限流时走 chatBlockHandler，熔断时走 chatFallback，
     * Controller 只负责统一包装成 ResponseResult
     */
    @PostMapping("/send")
    public ResponseResult<String> chat(@RequestBody ChatRequestDto request) {
        log.info("单轮对话接口，用户问题：{}", request.getMessage());
        try {
            String reply = qwenChatService.chat(request.getMessage());
            log.info("模型回复：{}", reply);
            return ResponseResult.success(reply);
        } catch (Exception e) {
            log.error("模型调用失败：{}", e.getMessage(), e);
            return ResponseResult.fail("模型调用失败");
        }
    }

    /**
     * 带缓存的问答接口
     * 优先从 Caffeine 本地缓存查询，未命中再调大模型并写入缓存
     */
    @PostMapping("/cache")
    public ResponseResult<String> chatWithCache(@RequestBody ChatRequestDto request) {
        log.info("缓存接口，用户问题：{}", request.getMessage());
        try {
            String reply = qwenChatService.chatWithCache(request.getMessage());
            log.info("缓存接口回复：{}", reply);
            return ResponseResult.success(reply);
        } catch (Exception e) {
            log.error("缓存接口调用失败：{}", e.getMessage(), e);
            return ResponseResult.fail("模型调用失败");
        }
    }

    /**
     * 带并发控制的问答接口
     * 通过 Semaphore 信号量限制同时进行的大模型调用数
     */
    @PostMapping("/concurrency")
    public ResponseResult<String> chatWithConcurrencyControl(@RequestBody ChatRequestDto request) {
        log.info("并发控制接口，用户问题：{}", request.getMessage());
        String reply = qwenChatService.chatWithConcurrencyControl(request.getMessage());
        log.info("并发控制接口回复：{}", reply);
        return ResponseResult.success(reply);
    }

    /**
     * 流式问答接口（融合限流 + 并发控制 + 缓存 + 流式输出）
     * <p>
     * SSE 事件格式：
     *   event: message  → 内容片段（前端逐字拼接）
     *   event: done     → 结束标记 [DONE]
     * <p>
     * 执行顺序：Sentinel 限流 → Semaphore 并发 → Cache 缓存 → 流式调用大模型
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam String question) {
        log.info("流式接口，用户问题：{}", question);
        SseEmitter emitter = new SseEmitter(120_000L); // SseEmitter作用：管理流式输出，超时时间120秒

        emitter.onCompletion(() -> log.info("流式接口完成，问题：{}", question));
        emitter.onTimeout(() -> {
            log.warn("流式接口超时，问题：{}", question);
            emitter.complete();
        });
        emitter.onError((ex) -> {
            log.warn("流式接口连接异常，问题：{}，异常：{}", question, ex.getMessage());
        });

        streamExecutor.execute(() -> {
            try {
                qwenChatService.streamChat(question, emitter);
            } catch (Exception e) {
                log.error("流式接口异常：{}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().name("message").data("模型调用失败"));
                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                } catch (Exception ignored) {
                }
                emitter.complete(); // 流式输出完成
            }
        });

        return emitter;
    }

}