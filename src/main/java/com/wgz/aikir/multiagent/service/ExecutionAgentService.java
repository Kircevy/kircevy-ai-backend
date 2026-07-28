package com.wgz.aikir.multiagent.service;

import com.wgz.aikir.model.entity.User;
import com.wgz.aikir.multiagent.domain.entity.GenerationRun;

/** M2：在冻结的 M1 规划产物基础上启动并执行前后端协作生成。 */
public interface ExecutionAgentService {

    GenerationRun startExecutionRun(String runId, User user);

    void executeRun(String runId);
}
