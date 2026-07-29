package com.wgz.aikir.ai.model.message;

import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThinkingMessageTest {

    @Test
    void shouldExposeThinkingAsASeparateStreamMessageType() {
        ThinkingMessage message = new ThinkingMessage("分析项目结构");

        assertEquals("thinking", message.getType());
        assertEquals("分析项目结构", JSONUtil.parseObj(JSONUtil.toJsonStr(message)).getStr("data"));
    }
}
