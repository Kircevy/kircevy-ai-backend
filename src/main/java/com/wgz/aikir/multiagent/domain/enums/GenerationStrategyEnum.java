package com.wgz.aikir.multiagent.domain.enums;

/**
 * 生成执行策略。DIRECT 保持一阶段生成路径；
 * MULTI_AGENT 将在二阶段后续里程碑中接入。
 */
public enum GenerationStrategyEnum {
    DIRECT,
    MULTI_AGENT
}
