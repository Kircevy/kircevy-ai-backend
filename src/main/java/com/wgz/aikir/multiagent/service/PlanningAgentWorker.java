package com.wgz.aikir.multiagent.service;

import com.wgz.aikir.model.entity.User;
import com.wgz.aikir.multiagent.domain.entity.GenerationRun;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 将耗时的模型规划从 HTTP 请求线程中分离。 */
@Component
public class PlanningAgentWorker {

    @Resource
    private PlanningAgentService planningAgentService;

    @Async("multiAgentPlanningExecutor")
    public void execute(GenerationRun run, User user, String message, boolean autoExecute) {
        planningAgentService.executePlanningRun(run, user, message, autoExecute);
    }
}
