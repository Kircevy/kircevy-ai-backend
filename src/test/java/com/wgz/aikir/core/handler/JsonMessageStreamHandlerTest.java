package com.wgz.aikir.core.handler;

import cn.hutool.json.JSONUtil;
import com.wgz.aikir.ai.model.message.ToolPartialMessage;
import com.wgz.aikir.model.entity.User;
import com.wgz.aikir.service.ChatHistoryService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonMessageStreamHandlerTest {

    @Test
    void passesThroughToolPartialContent() {
        JsonMessageStreamHandler handler = new JsonMessageStreamHandler();
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        when(chatHistoryService.save(any())).thenReturn(true);
        when(chatHistoryService.updateById(any())).thenReturn(true);

        User loginUser = User.builder().id(1L).build();
        ToolPartialMessage partial = new ToolPartialMessage("writeFile", 0, "[工具调用] 写入文件 a.txt\n```\nhello", true);
        String chunk = JSONUtil.toJsonStr(partial);

        Flux<String> result = handler.handle(Flux.just(chunk), chatHistoryService, 1L, loginUser);

        List<String> outputs = result.collectList().block();
        assertNotNull(outputs);
        assertEquals(1, outputs.size());
        assertTrue(outputs.get(0).contains("hello"));
        assertTrue(outputs.get(0).contains("a.txt"));
    }
}
// @zbiti-ai:f:38:19c35540
