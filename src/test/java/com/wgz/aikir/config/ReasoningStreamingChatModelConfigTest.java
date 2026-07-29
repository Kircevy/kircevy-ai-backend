package com.wgz.aikir.config;

import com.wgz.aikir.monitor.AiModelMonitorListener;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ReasoningStreamingChatModelConfigTest {

    @Test
    void shouldPassTemperatureToTheReasoningModel() {
        ReasoningStreamingChatModelConfig config = new ReasoningStreamingChatModelConfig();
        config.setAiModelMonitorListener(mock(AiModelMonitorListener.class));
        config.setBaseUrl("http://localhost:28081/v1");
        config.setApiKey("test-key");
        config.setModelName("qwen3.6-27b");
        config.setTemperature(0.1D);

        StreamingChatModel model = config.reasoningStreamingChatModelPrototype();

        assertEquals(0.1D, ((OpenAiStreamingChatModel) model).defaultRequestParameters().temperature());
    }
}
