package com.wgz.aikir.agentscope.config;

import com.wgz.aikir.agentscope.agent.CodeProjectAssistantAgent;
import com.wgz.aikir.agentscope.tools.AppMockTool;
import io.agentscope.core.formatter.openai.DeepSeekFormatter;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentScopeDemoConfig {

    @Value("${langchain4j.open-ai.chat-model.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.api-key:}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name:deepseek-chat}")
    private String modelName;

    @Bean
    public AppMockTool appMockTool() {
        return new AppMockTool();
    }

    @Bean
    public OpenAIChatModel agentScopeChatModel() {
        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .formatter(new DeepSeekFormatter())
                .stream(false)
                .build();
    }

    @Bean
    public CodeProjectAssistantAgent codeProjectAssistantAgent(
            OpenAIChatModel agentScopeChatModel, AppMockTool appMockTool) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(appMockTool);
        return new CodeProjectAssistantAgent(agentScopeChatModel, toolkit);
    }
}
