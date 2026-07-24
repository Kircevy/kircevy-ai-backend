package com.wgz.aikir.multiagent.model.request;

import lombok.Data;

/** 管理员更新多智能体功能开关的请求参数。 */
@Data
public class MultiAgentSettingsUpdateRequest {

    /** 是否启用二阶段能力。 */
    private Boolean enabled;

    /** 是否仅允许全栈工程进入二阶段能力。 */
    private Boolean fullstackOnly;

    /** 是否允许创建 M1 协作规划。 */
    private Boolean planningEnabled;
}
