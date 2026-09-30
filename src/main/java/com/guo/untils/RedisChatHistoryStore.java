package com.guo.untils;

import com.alibaba.dashscope.common.MultiModalMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guo.common.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式会话历史存储
 * <p>
 * 采用「双 key」设计，彻底解决裁剪时误删 system 人设的问题：
 * <ul>
 *     <li>{@code chat:system:{sessionId}}：String 类型，单独保存第一条 system 人设消息，永不被裁剪</li>
 *     <li>{@code chat:history:{sessionId}}：List 类型，只保存 user/assistant 对话消息，可直接左侧裁剪</li>
 * </ul>
 * 读取时 system 拼在最前，保证传给大模型时人设始终在第一条。
 * 两个 key 都设置相同 TTL，长时间不活跃的会话自动清理。
 *
 * @author gcs
 * @create 2026-09-29
 */
@Slf4j
@Component
public class RedisChatHistoryStore {

    /** Redis system 人设 key 前缀 */
    public static final String REDIS_SYSTEM_KEY_PREFIX = "chat:system:";
    /** Redis 对话历史 key 前缀 */
    public static final String REDIS_HISTORY_KEY_PREFIX = "chat:history:";
    /** 会话过期时间：1 小时 */
    private static final long REDIS_SESSION_TTL_SECONDS = 3600;
    /** 单个会话允许的最大历史消息数（含 system） */
    private static final int REDIS_MAX_MESSAGES = 20;
    /** 对话消息最大条数（system 单独存放，故减 1） */
    private static final int REDIS_MAX_DIALOG_MESSAGES = REDIS_MAX_MESSAGES - 1;

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Redis 是否可用（未配置时返回 false，外部降级）
     */
    public boolean isAvailable() {
        return redisTemplate != null;
    }

    /**
     * 读取会话历史：system 拼在最前 + 对话列表
     */
    public List<MultiModalMessage> getHistory(String sessionId) {
        List<MultiModalMessage> history = new ArrayList<>();

        // 1. system 人设单独读
        String systemJson = redisTemplate.opsForValue().get(systemKey(sessionId));
        if (systemJson != null) {
            ChatMessage system = deserialize(systemJson, sessionId);
            if (system != null) {
                history.add(toMultiModalMessage(system));
            }
        }

        // 2. 对话历史
        List<String> jsonList = redisTemplate.opsForList()
                .range(historyKey(sessionId), 0, -1);
        if (jsonList != null && !jsonList.isEmpty()) {
            for (String json : jsonList) {
                ChatMessage cm = deserialize(json, sessionId);
                if (cm != null) {
                    history.add(toMultiModalMessage(cm));
                }
            }
        }
        return history;
    }

    /**
     * 新会话初始化：写 system 人设 + 刷新 TTL
     */
    public void initSession(String sessionId, MultiModalMessage systemMsg) {
        String content = firstText(systemMsg);
        String json = serialize(new ChatMessage(systemMsg.getRole(), content), sessionId);
        if (json == null) {
            return;
        }
        redisTemplate.opsForValue().set(systemKey(sessionId), json);
        refreshTtl(sessionId);
    }

    /**
     * 追加 user + assistant 到对话历史，并做裁剪、刷新 TTL
     */
    public void append(String sessionId, MultiModalMessage userMsg, MultiModalMessage assistantMsg) {
        String history = historyKey(sessionId);

        String userJson = serialize(new ChatMessage(userMsg.getRole(), firstText(userMsg)), sessionId);
        String assistantJson = serialize(new ChatMessage(assistantMsg.getRole(), firstText(assistantMsg)), sessionId);
        if (userJson == null || assistantJson == null) {
            return;
        }

        redisTemplate.opsForList().rightPushAll(history, userJson, assistantJson);

        // 对话列表里全是 user/assistant，可直接从左裁剪（system 在独立 key，安全）
        Long size = redisTemplate.opsForList().size(history);
        if (size != null && size > REDIS_MAX_DIALOG_MESSAGES) {
            long removeCount = size - REDIS_MAX_DIALOG_MESSAGES;
            redisTemplate.opsForList().trim(history, removeCount, -1);
            log.debug("Redis 会话 {} 历史已裁剪，保留 {} 条对话", sessionId, REDIS_MAX_DIALOG_MESSAGES);
        }

        refreshTtl(sessionId);
    }

    // ==================== 私有工具 ====================

    private String systemKey(String sessionId) {
        return REDIS_SYSTEM_KEY_PREFIX + sessionId;
    }

    private String historyKey(String sessionId) {
        return REDIS_HISTORY_KEY_PREFIX + sessionId;
    }

    private void refreshTtl(String sessionId) {
        redisTemplate.expire(systemKey(sessionId), REDIS_SESSION_TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.expire(historyKey(sessionId), REDIS_SESSION_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private String firstText(MultiModalMessage msg) {
        return msg.getContent() != null && !msg.getContent().isEmpty()
                ? msg.getContent().get(0).get("text").toString() : "";
    }

    private String serialize(ChatMessage msg, String sessionId) {
        try {
            return objectMapper.writeValueAsString(msg);
        } catch (Exception e) {
            log.warn("Redis 消息序列化失败，session: {}", sessionId, e);
            return null;
        }
    }

    private ChatMessage deserialize(String json, String sessionId) {
        try {
            return objectMapper.readValue(json, ChatMessage.class);
        } catch (Exception e) {
            log.warn("Redis 消息反序列化失败，将跳过，session: {}", sessionId, e);
            return null;
        }
    }

    private MultiModalMessage toMultiModalMessage(ChatMessage cm) {
        return MultiModalMessage.builder()
                .role(cm.getRole())
                .content(Arrays.asList(Collections.singletonMap("text", cm.getContent())))
                .build();
    }
}