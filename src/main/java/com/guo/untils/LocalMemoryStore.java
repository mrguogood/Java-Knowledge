package com.guo.untils;

import com.alibaba.dashscope.common.MultiModalMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地内存会话记忆存储（单机版）
 * <p>
 * 使用 ConcurrentHashMap 存储每个会话的历史消息列表。
 * 核心规则：
 * - 第一条 system 消息永远是"人设"，裁剪时永远不动它
 * - 超出 {@link #MAX_MESSAGES} 条时，从第二条开始删最旧的消息
 * - 默认保留 20 条（1 system + 约 9 轮对话）
 *
 * @author gcs
 * @create 2026-09-22
 */
@Slf4j
@Component
public class LocalMemoryStore {

    /** 单会话最大消息数（含 system 消息） */
    private static final int MAX_MESSAGES = 20;

    /** sessionId → 消息历史列表 */
    private final ConcurrentHashMap<String, List<MultiModalMessage>> store = new ConcurrentHashMap<>();

    /**
     * 获取指定会话的历史消息列表（不存在则返回空列表）
     */
    public List<MultiModalMessage> getHistory(String sessionId) {
        return store.getOrDefault(sessionId, new ArrayList<>());
    }

    /**
     * 将用户消息和 AI 回复追加到会话历史
     * <p>
     * 追加后自动裁剪，确保总条数不超过 {@link #MAX_MESSAGES}，
     * 且第一条 system 消息永远不会被删。
     */
    public void append(String sessionId, MultiModalMessage userMsg, MultiModalMessage assistantMsg) {
        List<MultiModalMessage> history = store.computeIfAbsent(sessionId, k -> new ArrayList<>());

        // 加锁保证追加 + 裁剪的原子性
        synchronized (history) {
            history.add(userMsg);
            history.add(assistantMsg);
            trimHistory(history);
        }

        log.debug("会话 {} 消息数: {}", sessionId, history.size());
    }

    /**
     * 裁剪历史消息
     * <p>
     * rules[0] = system 人设 → 永远不动
     * rules[1..] = 历史对话 → 超出 max 时删最旧的
     */
    private void trimHistory(List<MultiModalMessage> messages) {
        if (messages.size() <= MAX_MESSAGES) {
            return;
        }

        // 删掉 index 1 到 (size - MAX) 之间的旧消息
        int removeEnd = messages.size() - MAX_MESSAGES + 1;
        messages.subList(1, removeEnd).clear();

        log.debug("历史消息已裁剪，当前条数: {}", messages.size());
    }

    /**
     * 清空指定会话（主要用于测试或手动重置）
     */
    public void clear(String sessionId) {
        store.remove(sessionId);
    }

    /**
     * 获取当前存储的会话数
     */
    public int sessionCount() {
        return store.size();
    }
}