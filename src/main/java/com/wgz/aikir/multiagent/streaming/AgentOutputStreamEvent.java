package com.wgz.aikir.multiagent.streaming;

/** 单个执行 Agent 的可恢复输出事件。 */
public record AgentOutputStreamEvent(
        long sequence,
        String runId,
        String taskKey,
        String eventType,
        String content
) {
}
