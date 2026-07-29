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
@ConfigurationProperties(prefix = "langchain4j.open-ai.streaming-chat-model")
@Data
public class StreamingChatModelConfig {

    @Resource
    private AiModelMonitorListener aiModelMonitorListener;

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private Duration timeout;

    private boolean logRequests;

    private boolean logResponses;

    private Map<String, Object> customParameters;

    @Bean
    @Scope("prototype")
    public StreamingChatModel streamingChatModelPrototype() {
        return builder().build();
    }

    @Bean
    @Scope("prototype")
    public StreamingChatModel executionManifestStreamingChatModelPrototype() {
        return builder().responseFormat("json_object").build();
    }

    private OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder() {
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .customParameters(customParameters)
                .listeners(List.of(aiModelMonitorListener));
        if (timeout != null) {
            builder.timeout(timeout);
        }
        return builder;
    }
}
