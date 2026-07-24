package com.wgz.aikir.multiagent.model.request;

import lombok.Data;

/** 发起 M1 协作规划的请求参数。 */
@Data
public class MultiAgentPlanningRequest {

    /** 用户对应用的需求描述。 */
    private String message;
}
