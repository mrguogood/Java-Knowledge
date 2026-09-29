package com.guo.dto;

import lombok.Data;

/**
 * @author gcs
 * @create 2026-09-22-16:56
 */
@Data
public class ChatRequestDto {
    /** 用户问题 */
    private String message;
    /** 会话ID（用于记忆型接口区分不同对话） */
    private String sessionId;
}