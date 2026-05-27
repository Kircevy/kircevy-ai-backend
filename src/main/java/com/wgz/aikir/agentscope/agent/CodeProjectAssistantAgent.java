package com.wgz.aikir.agentscope.agent;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;

public class CodeProjectAssistantAgent {

    private final ReActAgent agent;

    public CodeProjectAssistantAgent(Model model, Toolkit toolkit) {
        this.agent = ReActAgent.builder()
                .name("CodeProjectAssistant")
                .sysPrompt("""
                        You are an AI code generation project assistant. \
                        You have three tools: get_app_status (query state), \
                        build_project (build code), deploy_project (deploy to server). \
                        Choose the right tool based on the user's request. \
                        Always answer based on tool results. Do not fabricate app status.""")
                .model(model)
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .maxIters(10)
                .build();
    }

    public String chat(String message) {
        Msg response = agent.call(Msg.builder().textContent(message).build()).block();
        if (response == null) {
            return "No response from agent.";
        }
        return response.getTextContent();
    }
}
