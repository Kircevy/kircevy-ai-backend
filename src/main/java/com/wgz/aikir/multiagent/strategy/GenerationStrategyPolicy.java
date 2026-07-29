package com.wgz.aikir.multiagent.strategy;

import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.exception.ErrorCode;
import com.wgz.aikir.model.entity.App;
import com.wgz.aikir.model.enums.CodeGenTypeEnum;
import com.wgz.aikir.multiagent.config.MultiAgentProperties;
import com.wgz.aikir.multiagent.domain.enums.GenerationStrategyEnum;
import org.springframework.stereotype.Component;

/** 校验生成方式选择，并确保协作生成只进入受支持的全栈链路。 */
@Component
public class GenerationStrategyPolicy {

    public GenerationStrategyEnum validateSelection(CodeGenTypeEnum codeGenType, String requestedStrategy,
                                                     MultiAgentProperties properties) {
        GenerationStrategyEnum strategy;
        try {
            strategy = GenerationStrategyEnum.valueOf(requestedStrategy);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的生成方式");
        }
        if (strategy == GenerationStrategyEnum.DIRECT) {
            return strategy;
        }
        if (codeGenType != CodeGenTypeEnum.FULLSTACK) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非全栈应用只能使用快速生成");
        }
        if (!multiAgentAvailable(properties)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "协作生成功能当前未启用");
        }
        return strategy;
    }

    public boolean shouldStartMultiAgent(App app, MultiAgentProperties properties) {
        return app != null
                && CodeGenTypeEnum.FULLSTACK.getValue().equals(app.getCodeGenType())
                && GenerationStrategyEnum.MULTI_AGENT.name().equals(app.getGenerationStrategy())
                && multiAgentAvailable(properties);
    }

    public boolean isMultiAgentSelected(App app) {
        return app != null && GenerationStrategyEnum.MULTI_AGENT.name().equals(app.getGenerationStrategy());
    }

    public void requireMultiAgentSelected(App app) {
        if (!isMultiAgentSelected(app)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该应用未选择协作生成方式");
        }
    }

    private boolean multiAgentAvailable(MultiAgentProperties properties) {
        return properties != null
                && properties.isEnabled()
                && properties.isPlanningEnabled()
                && properties.isExecutionEnabled();
    }
}
