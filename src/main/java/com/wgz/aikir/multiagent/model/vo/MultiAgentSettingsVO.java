package com.wgz.aikir.multiagent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 对管理端暴露的多智能体功能开关快照。 */
@Data
@AllArgsConstructor
public class MultiAgentSettingsVO {

    private boolean enabled;

    private boolean fullstackOnly;

    private boolean planningEnabled;
}
