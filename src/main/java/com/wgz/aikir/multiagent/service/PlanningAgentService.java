package com.wgz.aikir.multiagent.service;

import com.wgz.aikir.model.entity.App;
import com.wgz.aikir.model.entity.User;
import com.wgz.aikir.multiagent.domain.entity.GenerationRun;

/** M1 产品与架构智能体的编排服务。 */
public interface PlanningAgentService {

    /** 生成并持久化四类规划产物，不生成或修改应用代码。 */
    GenerationRun createPlanningRun(App app, User user, String message, boolean autoExecute);

    /** 由独立线程执行产品与架构规划。 */
    void executePlanningRun(GenerationRun run, User user, String message, boolean autoExecute);
}
