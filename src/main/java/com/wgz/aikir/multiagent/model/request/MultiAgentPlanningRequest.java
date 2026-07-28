package com.wgz.aikir.multiagent.model.request;

import lombok.Data;

/** 发起 M1 协作规划的请求参数。 */
@Data
public class MultiAgentPlanningRequest {

    /** 用户对应用的需求描述。 */
    private String message;

    /** 规划成功后是否自动进入 M2 前后端并行执行。 */
    private boolean autoExecute;
}
