package com.wgz.aikir.multiagent.service;

import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 从 HTTP 请求线程异步执行 M2 编码与集成流程。 */
@Component
public class ExecutionAgentWorker {

    @Resource
    private ExecutionAgentService executionAgentService;

    @Async("multiAgentCoordinatorExecutor")
    public void execute(String runId) {
        executionAgentService.executeRun(runId);
    }
}
