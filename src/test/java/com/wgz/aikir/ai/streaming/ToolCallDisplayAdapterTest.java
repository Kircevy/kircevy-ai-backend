package com.wgz.aikir.ai.streaming;

import cn.hutool.json.JSONUtil;
import com.wgz.aikir.ai.model.message.ToolExecutedMessage;
import com.wgz.aikir.ai.model.message.ToolRequestMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolCallDisplayAdapterTest {

    @Test
    void publishesTheExistingRequestAndResultMessagesForOneToolCall() {
        List<String> events = new ArrayList<>();
        ToolCallDisplayAdapter adapter = new ToolCallDisplayAdapter(events::add);
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1")
                .name("file_write")
                .arguments("{\"path\":\"src/App.vue\"}")
                .build();

        adapter.beforeToolExecution(request);
        adapter.onToolExecuted(ToolExecution.builder()
                .request(request)
                .result("written")
                .invocationContext(InvocationContext.builder().build())
                .build());

        ToolRequestMessage requestMessage = JSONUtil.toBean(events.getFirst(), ToolRequestMessage.class);
        ToolExecutedMessage executedMessage = JSONUtil.toBean(events.getLast(), ToolExecutedMessage.class);
        assertEquals("tool_request", requestMessage.getType());
        assertEquals("call-1", requestMessage.getId());
        assertEquals("file_write", requestMessage.getName());
        assertEquals("tool_executed", executedMessage.getType());
        assertEquals("written", executedMessage.getResult());
    }
}
