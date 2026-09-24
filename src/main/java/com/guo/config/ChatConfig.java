package com.guo.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 大模型相关 Bean 配置
 *
 * @author gcs
 * @create 2026-09-22
 */
@Slf4j
@Configuration
public class ChatConfig {

    @PostConstruct
    public void initSentinelRules() {
        FlowRule streamRule = new FlowRule("chat_stream_interface")
                .setCount(5)
                .setGrade(RuleConstant.FLOW_GRADE_QPS);
        FlowRule chatRule = new FlowRule("chat_interface")
                .setCount(10)
                .setGrade(RuleConstant.FLOW_GRADE_QPS);
        FlowRuleManager.loadRules(Arrays.asList(streamRule, chatRule));
        log.info("Sentinel 限流规则已加载: chat_stream_interface QPS=5, chat_interface QPS=10");
    }

    /**
     * 大模型并发信号量：最多允许 10 路请求同时打到模型
     */
    @Bean
    public Semaphore modelSemaphore() {
        return new Semaphore(10);
    }

    /**
     * 问答结果本地缓存：最多 1000 条，写入后 1 小时过期
     */
    @Bean
    public Cache<String, String> chatAnswerCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
    }

    /**
     * 流式调用专用线程池（Spring ThreadPoolTaskExecutor）
     * <p>
     * 优势：
     * - 容器关闭时自动优雅停机（无需 @PreDestroy）
     * - 线程名带前缀 ai-stream-，日志里一眼可辨
     * - 自动传递 Slf4j MDC 上下文（traceId 不丢失）
     */
    @Bean
    public ThreadPoolTaskExecutor streamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ai-stream-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}