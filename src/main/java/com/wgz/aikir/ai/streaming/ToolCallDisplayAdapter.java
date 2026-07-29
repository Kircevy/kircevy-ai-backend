package com.wgz.aikir.ai.streaming;

import cn.hutool.json.JSONUtil;
import com.wgz.aikir.ai.model.message.ToolExecutedMessage;
import com.wgz.aikir.ai.model.message.ToolRequestMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecution;

import java.util.function.Consumer;

public class ToolCallDisplayAdapter {

    private final Consumer<String> eventPublisher;

    public ToolCallDisplayAdapter(Consumer<String> eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void beforeToolExecution(ToolExecutionRequest toolExecutionRequest) {
        eventPublisher.accept(JSONUtil.toJsonStr(new ToolRequestMessage(toolExecutionRequest)));
    }

    public void onToolExecuted(ToolExecution toolExecution) {
        eventPublisher.accept(JSONUtil.toJsonStr(new ToolExecutedMessage(toolExecution)));
    }
}
