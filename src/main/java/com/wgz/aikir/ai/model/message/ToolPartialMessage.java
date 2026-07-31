package com.wgz.aikir.ai.model.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ToolPartialMessage extends StreamMessage {

    private String toolName;

    private int index;

    private String partialContent;

    private boolean isFirst;

    public ToolPartialMessage(String toolName, int index, String partialContent, boolean isFirst) {
        super(StreamMessageTypeEnum.TOOL_PARTIAL.getValue());
        this.toolName = toolName;
        this.index = index;
        this.partialContent = partialContent;
        this.isFirst = isFirst;
    }
}
// @zbiti-ai:f:27:8ea9a1c5
