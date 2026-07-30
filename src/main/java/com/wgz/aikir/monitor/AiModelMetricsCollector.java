package com.wgz.aikir.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.Duration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 指标收集器（请求次数、错误记录、Token消耗数量、响应时间）
 */
@Component
@Slf4j
public class AiModelMetricsCollector {

    @Resource
    private MeterRegistry meterRegistry;

    // 缓存已创建的指标，避免重复创建（按指标类型分离缓存）
    private final ConcurrentMap<String, Counter> requestCountersCache  = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> errorCountersCache  = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> tokenCountersCache  = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> responseTimersCache  = new ConcurrentHashMap<>();

    /**
     * 记录请求次数
     */
    public void recordRequest(String userId, String appId, String modelName, String status) {
        String safeUserId = tagValue(userId);
        String safeAppId = tagValue(appId);
        String safeModelName = tagValue(modelName);
        String safeStatus = tagValue(status);
        String key = String.format("%s_%s_%s_%s", safeUserId, safeAppId, safeModelName, safeStatus);
        Counter counter = requestCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_model_request_total")
                        .description("AI 模型请求次数")
                        .tag("user_id", safeUserId)
                        .tag("app_id", safeAppId)
                        .tag("model_name", safeModelName)
                        .tag("status", safeStatus)
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * 记录错误次数
     */
    public void recordError(String userId, String appId, String modelName, String errorMessage) {
        String safeUserId = tagValue(userId);
        String safeAppId = tagValue(appId);
        String safeModelName = tagValue(modelName);
        String safeErrorMessage = tagValue(errorMessage);
        String key = String.format("%s_%s_%s_%s", safeUserId, safeAppId, safeModelName, safeErrorMessage);
        Counter counter = errorCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_model_errors_total")
                        .description("AI 模型错误次数")
                        .tag("user_id", safeUserId)
                        .tag("app_id", safeAppId)
                        .tag("model_name", safeModelName)
                        .tag("error_message", safeErrorMessage)
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * 记录Token消耗次数
     */
    public void recordTokenUsage(String userId, String appId, String modelName, String tokenType, long tokenCount) {
        String safeUserId = tagValue(userId);
        String safeAppId = tagValue(appId);
        String safeModelName = tagValue(modelName);
        String safeTokenType = tagValue(tokenType);
        String key = String.format("%s_%s_%s_%s", safeUserId, safeAppId, safeModelName, safeTokenType);
        Counter counter = tokenCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_model_tokens_total")
                        .description("AI 模型Token消耗次数")
                        .tag("user_id", safeUserId)
                        .tag("app_id", safeAppId)
                        .tag("model_name", safeModelName)
                        .tag("token_type", safeTokenType)
                        .register(meterRegistry)
        );
        counter.increment(tokenCount);
    }

    /**
     * 记录模型响应时间
     */
    public void recordResponseTimer(String userId, String appId, String modelName, Duration duration) {
        String safeUserId = tagValue(userId);
        String safeAppId = tagValue(appId);
        String safeModelName = tagValue(modelName);
        String key = String.format("%s_%s_%s", safeUserId, safeAppId, safeModelName);
        Timer timer = responseTimersCache.computeIfAbsent(key, k ->
                Timer.builder("ai_model_response_duration_seconds")
                        .description("AI 模型响应时间")
                        .tag("user_id", safeUserId)
                        .tag("app_id", safeAppId)
                        .tag("model_name", safeModelName)
                        .register(meterRegistry)
        );
        timer.record(duration);
    }

    private String tagValue(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
