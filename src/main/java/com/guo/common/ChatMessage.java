package com.guo.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 轻量级对话消息体
 * <p>
 * 用于 Redis 序列化，不依赖 DashScope SDK 的 MultiModalMessage，
 * 避免 SDK 升级时序列化兼容问题。
 *
 * @author gcs
 * @create 2026-09-22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    /** 角色：system / user / assistant */
    private String role;
    /** 文本内容 */
    private String content;
}