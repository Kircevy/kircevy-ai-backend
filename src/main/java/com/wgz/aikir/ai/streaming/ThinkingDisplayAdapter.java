package com.wgz.aikir.ai.streaming;

import cn.hutool.json.JSONUtil;
import com.wgz.aikir.ai.model.message.ThinkingMessage;

import java.util.function.Consumer;

/**
 * 将模型思考分片转换为前端可识别的流式消息。
 */
public class ThinkingDisplayAdapter {

    private final Consumer<String> eventPublisher;

    public ThinkingDisplayAdapter(Consumer<String> eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void onPartialThinking(String partialThinking) {
        eventPublisher.accept(JSONUtil.toJsonStr(new ThinkingMessage(partialThinking)));
    }
}
