package com.wgz.aikir.ai.streaming;

import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThinkingDisplayAdapterTest {

    @Test
    void shouldPublishThinkingAsASeparateStreamMessage() {
        AtomicReference<String> output = new AtomicReference<>();
        ThinkingDisplayAdapter adapter = new ThinkingDisplayAdapter(output::set);

        adapter.onPartialThinking("先分析项目结构");

        assertEquals("thinking", JSONUtil.parseObj(output.get()).getStr("type"));
        assertEquals("先分析项目结构", JSONUtil.parseObj(output.get()).getStr("data"));
    }
}
