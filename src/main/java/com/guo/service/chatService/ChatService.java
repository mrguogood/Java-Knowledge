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

    /**
     * 基础版流式：本地内存实现会话记忆
     * 使用 ConcurrentHashMap 存储，支持最大条数裁剪，保留第一条 system 消息
     */
    void streamChatWithLocalMemory(String question, String sessionId, SseEmitter emitter);

    /**
     * 进阶版流式：Redis 实现分布式会话记忆
     * 使用 Redis List 存储，设置过期时间，支持集群共享
     */
    void streamChatWithRedisMemory(String question, String sessionId, SseEmitter emitter);
}