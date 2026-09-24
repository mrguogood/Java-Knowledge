package com.guo.common;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Sentinel 限流/熔断的兜底处理类
 * <p>
 * 放到外部类时 Sentinel 要求方法必须是 static，
 * chatFallback 需要访问 Caffeine 缓存，通过 @Autowired setter 注入到静态字段。
 *
 * @author gcs
 * @create 2026-09-22
 */
@Component
public class ChatSentinelHandler {

    private static Cache<String, String> cache;

    @Autowired
    public void setChatAnswerCache(Cache<String, String> chatAnswerCache) {
        ChatSentinelHandler.cache = chatAnswerCache;
    }

    public static String chatBlockHandler(String question, BlockException e) {
        return "当前咨询人数较多，请稍后再试，或查看底部常见问题文档";
    }

    public static String chatFallback(String question) {
        if (cache != null) {
            String cacheKey = question.trim().toLowerCase();
            String cacheAnswer = cache.getIfPresent(cacheKey);
            if (cacheAnswer != null) {
                return cacheAnswer;
            }
        }
        return "当前系统繁忙，请稍后重试，您也可以查看常见问题文档快速获取答案";
    }
}