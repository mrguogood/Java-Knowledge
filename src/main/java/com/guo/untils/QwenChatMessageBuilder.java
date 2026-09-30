package com.guo.untils;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 通义千问消息与参数构建工具
 * <p>
 * 统一封装构造 Message / GenerationParam 的公共逻辑，
 * 以及从响应中提取文本的公共方法，供 Service 层复用解耦。
 *
 * @author gcs
 * @create 2026-09-22
 */
@Component
public class QwenChatMessageBuilder {

    /** 系统人设（裁剪时永远保留第一条 system 消息） */
    public static final String SYSTEM_PROMPT = "你是专业的Java开发助手，回答精准简洁，只讲技术干货。";

    @Value("${ai.qwen.api-key}")
    private String apiKey;

    @Value("${ai.qwen.model}")
    private String model;

    @Value("${ai.qwen.temperature}")
    private Float temperature;

    @Value("${ai.qwen.max-tokens}")
    private Integer maxTokens;

    /**
     * 构建系统人设消息
     */
    public MultiModalMessage buildSystemMsg() {
        return MultiModalMessage.builder()
                .role(Role.SYSTEM.getValue())
                .content(Arrays.asList(Collections.singletonMap("text", SYSTEM_PROMPT)))
                .build();
    }

    /**
     * 构建用户消息
     */
    public MultiModalMessage buildUserMsg(String text) {
        return MultiModalMessage.builder()
                .role(Role.USER.getValue())
                .content(Arrays.asList(Collections.singletonMap("text", text)))
                .build();
    }

    /**
     * 构建 AI 回复消息（用于回写历史）
     */
    public MultiModalMessage buildAssistantMsg(String text) {
        return MultiModalMessage.builder()
                .role(Role.ASSISTANT.getValue())
                .content(Arrays.asList(Collections.singletonMap("text", text)))
                .build();
    }

    /**
     * 构建单轮对话参数（无记忆）
     */
    public MultiModalConversationParam buildParam(String userMessage) {
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
     *
     * @param userMessage 当前用户问题
     * @param history     历史消息列表（可能包含 system）
     */
    public MultiModalConversationParam buildMemoryParam(String userMessage, List<MultiModalMessage> history) {
        List<MultiModalMessage> messages = new ArrayList<>();
        // 1. 从历史中找 system 消息放第一条
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

    /**
     * 从流式/普通响应中提取文本内容
     * <p>
     * 流式返回的某些 chunk 可能只有 usage/finish_reason，choices 或 content 为空，
     * 需要做空值保护。
     */
    public static String extractContent(MultiModalConversationResult result) {
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
}