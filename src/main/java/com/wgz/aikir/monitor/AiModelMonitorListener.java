package com.wgz.aikir.monitor;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
@Slf4j
public class AiModelMonitorListener implements ChatModelListener {

    private static final String REQUEST_START_TIME_KEY = "request_start_time";

    private static final String MONITOR_CONTEXT_KEY = "monitor_context";

    @Resource
    private AiModelMetricsCollector aiModelMetricsCollector;

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        // 首先记录请求到达的瞬时时间点，精确到纳米级
        requestContext.attributes().put(REQUEST_START_TIME_KEY, Instant.now());
        // 从监控上下文中获取自定义信息
        MonitorContext context = MonitorContextHolder.getContext();
        String userId = context.getUserId();
        String appId = context.getAppId();
        requestContext.attributes().put(MONITOR_CONTEXT_KEY, context);
        // 调用指标收集器
        String modelName = requestContext.chatRequest().modelName();
        // 记录请求指标
        aiModelMetricsCollector.recordRequest(userId, appId, modelName, "start");
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        // 从属性中获取监控信息
        Map<Object, Object> attributes = responseContext.attributes();
        // 从监控上下文中获取信息
        MonitorContext context = (MonitorContext) responseContext.attributes().get(MONITOR_CONTEXT_KEY);
        String userId = context.getUserId();
        String appId = context.getAppId();
        String modelName = responseContext.chatResponse().modelName();
        // 记录成功请求指标
        aiModelMetricsCollector.recordRequest(userId, appId, modelName, "success");
        // 记录响应指标
        recordResponseTime(attributes, userId, appId, modelName);
        // 记录 TOKEN 使用情况
        recordTokenUsage(responseContext, userId, appId, modelName);
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        MonitorContext context = MonitorContextHolder.getContext();
        String userId = context.getUserId();
        String appId = context.getAppId();
        // 获取模型名称和错误信息
        String modelName = errorContext.chatRequest().modelName();
        String errorMessage = errorContext.error().getMessage();
        // 记录失败请求指标
        aiModelMetricsCollector.recordRequest(userId, appId, modelName, "error");
        aiModelMetricsCollector.recordError(userId, appId, modelName, errorMessage);
        // 记录响应时间, 即使是错误响应
        Map<Object, Object> attributes = errorContext.attributes();
        recordResponseTime(attributes, userId, appId, modelName);
    }

    /**
     * 记录响应时间指标
     * @param attributes
     * @param userId
     * @param appId
     * @param modelName
     */
    private void recordResponseTime(Map<Object, Object> attributes, String userId, String appId, String modelName) {
        Instant startTime = (Instant) attributes.get(REQUEST_START_TIME_KEY);
        Duration duration = Duration.between(startTime, Instant.now());
        aiModelMetricsCollector.recordResponseTimer(userId, appId, modelName, duration);
    }

    /**
     * 记录 Token 消耗数量
     * @param responseContext
     * @param userId
     * @param appId
     * @param modelName
     */
    private void recordTokenUsage(ChatModelResponseContext responseContext, String userId, String appId, String modelName) {
        TokenUsage tokenUsage = responseContext.chatResponse().metadata().tokenUsage();
        if (tokenUsage != null) {
            Integer inputTokenCount = tokenUsage.inputTokenCount();
            Integer outputTokenCount = tokenUsage.outputTokenCount();
            Integer totalTokenCount = tokenUsage.totalTokenCount();
            aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "input", inputTokenCount);
            aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "output", outputTokenCount);
            aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "total", totalTokenCount);
        }
    }
}
