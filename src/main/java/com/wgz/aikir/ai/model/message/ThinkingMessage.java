package com.wgz.aikir.ai.model.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * AI 思考内容流式消息。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class ThinkingMessage extends StreamMessage {

    private String data;

    public ThinkingMessage(String data) {
        super(StreamMessageTypeEnum.THINKING.getValue());
        this.data = data;
    }
}
