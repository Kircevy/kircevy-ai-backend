package com.wgz.aikir.ai.streaming;

import dev.langchain4j.model.chat.response.PartialToolCall;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolPartialExtractorTest {

    @Test
    void extractsContentFromSingleCompleteChunk() {
        ToolPartialExtractor extractor = new ToolPartialExtractor();
        String arguments = "{\"relativeFilePath\":\"package.json\",\"content\":\"hello world\"}";
        PartialToolCall partial = PartialToolCall.builder()
                .index(0)
                .name("writeFile")
                .partialArguments(arguments)
                .build();

        ToolPartialExtractor.ExtractResult result = extractor.extract(partial);

        assertTrue(result.hasOutput());
        assertTrue(result.isFirst());
        assertTrue(result.partialContent().contains("[工具调用] 写入文件 package.json"));
        assertTrue(result.partialContent().contains("hello world"));
    }

    @Test
    void extractsContentAcrossMultipleChunks() {
        ToolPartialExtractor extractor = new ToolPartialExtractor();
        String[] chunks = {
                "{\"relativeFilePath\":\"App.vue\",\"content\":\"<templ",
                "ate>\\n  <div>hello</div>\\n</template>\"}"
        };

        StringBuilder allOutput = new StringBuilder();
        boolean sawFirst = false;

        for (String chunk : chunks) {
            PartialToolCall partial = PartialToolCall.builder()
                    .index(0)
                    .name("writeFile")
                    .partialArguments(chunk)
                    .build();
            ToolPartialExtractor.ExtractResult result = extractor.extract(partial);
            if (result.hasOutput()) {
                allOutput.append(result.partialContent());
                if (result.isFirst()) sawFirst = true;
            }
        }

        assertTrue(sawFirst);
        assertTrue(allOutput.toString().contains("[工具调用] 写入文件 App.vue"));
        assertTrue(allOutput.toString().contains("<template>"));
        assertTrue(allOutput.toString().contains("<div>hello</div>"));
    }

    @Test
    void isolatesMultipleToolCallsByIndex() {
        ToolPartialExtractor extractor = new ToolPartialExtractor();

        PartialToolCall call0a = PartialToolCall.builder()
                .index(0).name("writeFile")
                .partialArguments("{\"relativeFilePath\":\"a.txt\",\"content\":\"aaa")
                .build();
        PartialToolCall call1a = PartialToolCall.builder()
                .index(1).name("writeFile")
                .partialArguments("{\"relativeFilePath\":\"b.txt\",\"content\":\"bbb")
                .build();
        PartialToolCall call0b = PartialToolCall.builder()
                .index(0).name("writeFile")
                .partialArguments("\"}")
                .build();
        PartialToolCall call1b = PartialToolCall.builder()
                .index(1).name("writeFile")
                .partialArguments("\"}")
                .build();

        ToolPartialExtractor.ExtractResult r0a = extractor.extract(call0a);
        ToolPartialExtractor.ExtractResult r1a = extractor.extract(call1a);
        ToolPartialExtractor.ExtractResult r0b = extractor.extract(call0b);
        ToolPartialExtractor.ExtractResult r1b = extractor.extract(call1b);

        assertTrue(r0a.isFirst());
        assertTrue(r0a.partialContent().contains("a.txt"));
        assertTrue(r1a.isFirst());
        assertTrue(r1a.partialContent().contains("b.txt"));
        assertFalse(r0b.isFirst());
        assertFalse(r1b.isFirst());
    }

    @Test
    void handlesEscapeSequencesInContent() {
        ToolPartialExtractor extractor = new ToolPartialExtractor();
        String arguments = "{\"relativeFilePath\":\"f.js\",\"content\":\"line1\\nline2\\ttab\\\"quote\\\\back\"}";
        PartialToolCall partial = PartialToolCall.builder()
                .index(0).name("writeFile")
                .partialArguments(arguments)
                .build();

        ToolPartialExtractor.ExtractResult result = extractor.extract(partial);

        String output = result.partialContent();
        assertTrue(output.contains("line1\nline2\ttab\"quote\\back"));
    }
}
// @zbiti-ai:f:106:b21199ce
