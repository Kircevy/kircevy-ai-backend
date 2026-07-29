package com.wgz.aikir.config;

import com.wgz.aikir.monitor.AiModelMonitorListener;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.reasoning-streaming-chat-model")
@Data
public class ReasoningStreamingChatModelConfig {

    @Resource
    private AiModelMonitorListener aiModelMonitorListener;

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Duration timeout;

    private Boolean logRequests = false;

    private Boolean logResponses = false;

    private String reasoningEffort;

    private Boolean returnThinking = true;

    private Boolean sendThinking = true;

    private Boolean accumulateToolCallId = false;

    private Map<String, Object> customParameters;

    @Bean
    @Scope("prototype")
    public StreamingChatModel reasoningStreamingChatModelPrototype() {
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .reasoningEffort(reasoningEffort)
                .returnThinking(returnThinking)
                .sendThinking(sendThinking)
                .accumulateToolCallId(accumulateToolCallId)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .customParameters(customParameters)
                .listeners(List.of(aiModelMonitorListener));
        if (timeout != null) {
            builder.timeout(timeout);
        }
        return builder.build();
    }
}
