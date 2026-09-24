package com.guo.service.chatService;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 大模型问答服务接口
 *
 * @author gcs
 * @create 2026-09-22
 */
public interface ChatService {

    /**
     * 单轮对话：一问一答，不带记忆
     */
    String singleChat(String userMessage) throws Exception;

    /**
     * 受限流/熔断保护的同步问答接口
     */
    String chat(String question) throws Exception;

    /**
     * 带缓存的模型调用
     */
    String chatWithCache(String question) throws Exception;

    /**
     * 带并发控制的模型调用
     */
    String chatWithConcurrencyControl(String question);

    /**
     * 流式问答接口（融合限流 + 并发控制 + 缓存 + 流式输出）
     */
    void streamChat(String question, SseEmitter emitter) throws Exception;
}