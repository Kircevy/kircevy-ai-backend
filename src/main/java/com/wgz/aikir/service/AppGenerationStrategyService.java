package com.wgz.aikir.service;

import com.wgz.aikir.exception.ErrorCode;
import com.wgz.aikir.exception.ThrowUtils;
import com.wgz.aikir.model.entity.App;
import com.wgz.aikir.model.entity.User;
import com.wgz.aikir.model.enums.CodeGenTypeEnum;
import com.wgz.aikir.multiagent.config.MultiAgentProperties;
import com.wgz.aikir.multiagent.domain.enums.GenerationStrategyEnum;
import com.wgz.aikir.multiagent.service.GenerationRunService;
import com.wgz.aikir.multiagent.strategy.GenerationStrategyPolicy;
import org.springframework.stereotype.Service;

/** 在首次生成前持久化用户选择的生成方式。 */
@Service
public class AppGenerationStrategyService {

    private final AppService appService;
    private final GenerationRunService generationRunService;
    private final GenerationStrategyPolicy generationStrategyPolicy;
    private final MultiAgentProperties multiAgentProperties;

    public AppGenerationStrategyService(AppService appService, GenerationRunService generationRunService,
                                        GenerationStrategyPolicy generationStrategyPolicy,
                                        MultiAgentProperties multiAgentProperties) {
        this.appService = appService;
        this.generationRunService = generationRunService;
        this.generationStrategyPolicy = generationStrategyPolicy;
        this.multiAgentProperties = multiAgentProperties;
    }

    public App select(Long appId, String requestedStrategy, User user) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_LOGIN_ERROR);
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(!user.getId().equals(app.getUserId()), ErrorCode.NO_AUTH_ERROR, "无权选择该应用的生成方式");
        ThrowUtils.throwIf(generationRunService.hasAnyRun(appId, user.getId()), ErrorCode.OPERATION_ERROR,
                "应用已开始生成，不能再修改生成方式");

        CodeGenTypeEnum codeGenType = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        ThrowUtils.throwIf(codeGenType == null, ErrorCode.PARAMS_ERROR, "应用代码生成类型错误");
        GenerationStrategyEnum strategy = generationStrategyPolicy.validateSelection(
                codeGenType, requestedStrategy, multiAgentProperties);
        App update = new App();
        update.setId(appId);
        update.setGenerationStrategy(strategy.name());
        ThrowUtils.throwIf(!appService.updateById(update), ErrorCode.OPERATION_ERROR, "保存生成方式失败");
        app.setGenerationStrategy(strategy.name());
        return app;
    }
}
