package com.wgz.aikir.multiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 二阶段运行基础设施的功能开关。 */
@Data
@Component
@ConfigurationProperties(prefix = "multi-agent")
public class MultiAgentProperties {

    /** 保持一阶段为默认执行路径。 */
    private boolean enabled = false;

    /** M0 阶段仅记录全栈生成的基线运行数据。 */
    private boolean fullstackOnly = true;
}
