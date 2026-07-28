package com.wgz.aikir.multiagent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgz.aikir.constant.AppConstant;
import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.exception.ErrorCode;
import com.wgz.aikir.exception.ThrowUtils;
import com.wgz.aikir.model.entity.App;
import com.wgz.aikir.model.entity.User;
import com.wgz.aikir.model.enums.CodeGenTypeEnum;
import com.wgz.aikir.multiagent.ai.ArchitecturePlanningAiService;
import com.wgz.aikir.multiagent.ai.PlanningAiServiceFactory;
import com.wgz.aikir.multiagent.ai.ProductPlanningAiService;
import com.wgz.aikir.multiagent.domain.entity.AgentArtifact;
import com.wgz.aikir.multiagent.domain.entity.AgentTask;
import com.wgz.aikir.multiagent.domain.entity.GenerationRun;
import com.wgz.aikir.multiagent.domain.enums.AgentTaskStatusEnum;
import com.wgz.aikir.multiagent.domain.enums.GenerationRunStatusEnum;
import com.wgz.aikir.multiagent.domain.enums.PlanningArtifactTypeEnum;
import com.wgz.aikir.multiagent.service.GenerationRunService;
import com.wgz.aikir.multiagent.service.ExecutionAgentService;
import com.wgz.aikir.multiagent.service.PlanningAgentService;
import com.wgz.aikir.multiagent.service.PlanningAgentWorker;
import com.wgz.aikir.multiagent.config.MultiAgentProperties;
import com.wgz.aikir.multiagent.validation.StructuredPlanningValidator;
import com.wgz.aikir.multiagent.strategy.GenerationStrategyPolicy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/** M1 规划编排：产品规格 -> 架构、接口契约、任务清单。 */
@Slf4j
@Service
public class PlanningAgentServiceImpl implements PlanningAgentService {

    private static final String PRODUCT_TASK_KEY = "product_planning";
    private static final String ARCHITECTURE_TASK_KEY = "architecture_planning";

    @Resource
    private GenerationRunService generationRunService;

    @Resource
    private PlanningAiServiceFactory planningAiServiceFactory;

    @Resource
    private StructuredPlanningValidator structuredPlanningValidator;

    @Resource
    private ObjectMapper objectMapper;

    @Lazy
    @Resource
    private PlanningAgentWorker planningAgentWorker;

    @Resource
    private MultiAgentProperties multiAgentProperties;

    @Lazy
    @Resource
    private ExecutionAgentService executionAgentService;

    @Resource
    private GenerationStrategyPolicy generationStrategyPolicy;

    @Override
    public GenerationRun createPlanningRun(App app, User user, String message, boolean autoExecute) {
        ThrowUtils.throwIf(app == null || app.getId() == null, ErrorCode.PARAMS_ERROR, "应用不能为空");
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(message == null || message.isBlank(), ErrorCode.PARAMS_ERROR, "规划需求不能为空");
        CodeGenTypeEnum codeGenType = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        ThrowUtils.throwIf(codeGenType != CodeGenTypeEnum.FULLSTACK, ErrorCode.PARAMS_ERROR,
                "协作规划当前仅支持全栈工程模式");
        generationStrategyPolicy.requireMultiAgentSelected(app);
        ThrowUtils.throwIf(autoExecute && !multiAgentProperties.isExecutionEnabled(), ErrorCode.NO_AUTH_ERROR,
                "当前未启用协作执行功能");

        GenerationRun run = generationRunService.startMultiAgentRun(app, user, codeGenType, message);
        planningAgentWorker.execute(run, user, message, autoExecute);
        return run;
    }

    @Override
    public void executePlanningRun(GenerationRun run, User user, String message, boolean autoExecute) {
        try {
            AgentArtifact productSpec = generateProductSpec(run, message);
            generateArchitectureBundle(run, productSpec);
            generationRunService.finishRun(run.getRunId(), GenerationRunStatusEnum.SUCCEEDED, null);
            if (autoExecute) {
                executionAgentService.startExecutionRun(run.getRunId(), user);
            }
        } catch (Exception exception) {
            String errorMessage = getSafeErrorMessage(exception);
            log.warn("M1 协作规划失败，runId：{}，原因：{}", run.getRunId(), errorMessage);
            generationRunService.finishRun(run.getRunId(), GenerationRunStatusEnum.FAILED, errorMessage);
            if (exception instanceof BusinessException businessException) {
                log.debug("协作规划业务异常已记录，runId：{}", run.getRunId(), businessException);
                return;
            }
        }
    }

    private AgentArtifact generateProductSpec(GenerationRun run, String message) throws IOException {
        AgentTask task = generationRunService.createTask(run.getRunId(), PRODUCT_TASK_KEY, "PRODUCT_AGENT", "[]", "[]");
        generationRunService.startTask(task);
        try {
            ProductPlanningAiService productAgent = planningAiServiceFactory.createProductPlanningAgent();
            JsonNode productSpec = invokeWithSingleRetry(
                    productAgent::createProductSpec,
                    structuredPlanningValidator::validateProductSpec,
                    message,
                    "产品规格");
            String payload = toPrettyJson(productSpec);
            AgentArtifact artifact = savePlanningArtifact(run, task, PlanningArtifactTypeEnum.PRODUCT_SPEC,
                    "用户角色、页面、功能需求和验收标准", payload);
            generationRunService.finishTask(task, AgentTaskStatusEnum.SUCCEEDED,
                    toArtifactReferences(List.of(artifact)), null);
            return artifact;
        } catch (Exception exception) {
            generationRunService.finishTask(task, AgentTaskStatusEnum.FAILED, "[]", getSafeErrorMessage(exception));
            throw exception;
        }
    }

    private void generateArchitectureBundle(GenerationRun run, AgentArtifact productSpec) throws IOException {
        AgentTask task = generationRunService.createTask(run.getRunId(), ARCHITECTURE_TASK_KEY, "ARCHITECT_AGENT",
                "[\"product_planning\"]", toArtifactReferences(List.of(productSpec)));
        generationRunService.startTask(task);
        try {
            ArchitecturePlanningAiService architectureAgent = planningAiServiceFactory.createArchitecturePlanningAgent();
            StructuredPlanningValidator.ArchitectureBundle bundle = invokeWithSingleRetry(
                    architectureAgent::createArchitectureBundle,
                    structuredPlanningValidator::validateArchitectureBundle,
                    productSpec.getPayload(),
                    "架构规划");
            AgentArtifact architecture = savePlanningArtifact(run, task, PlanningArtifactTypeEnum.ARCHITECTURE,
                    "技术栈、模块边界与数据模型", toPrettyJson(bundle.architecture()));
            AgentArtifact apiContract = savePlanningArtifact(run, task, PlanningArtifactTypeEnum.API_CONTRACT,
                    "OpenAPI 3 接口契约", bundle.apiContractYaml());
            AgentArtifact taskManifest = savePlanningArtifact(run, task, PlanningArtifactTypeEnum.TASK_MANIFEST,
                    "前后端后续执行任务图与写入范围", toPrettyJson(bundle.taskManifest()));
            generationRunService.finishTask(task, AgentTaskStatusEnum.SUCCEEDED,
                    toArtifactReferences(List.of(architecture, apiContract, taskManifest)), null);
        } catch (Exception exception) {
            generationRunService.finishTask(task, AgentTaskStatusEnum.FAILED, "[]", getSafeErrorMessage(exception));
            throw exception;
        }
    }

    private AgentArtifact savePlanningArtifact(GenerationRun run, AgentTask task, PlanningArtifactTypeEnum type,
                                               String summary, String payload) throws IOException {
        String relativePath = "multiagent_" + run.getRunId() + "/planning/" + type.getFileName();
        writePlanningFile(relativePath, payload);
        return generationRunService.saveArtifact(run.getRunId(), task.getId(), type.getValue(), relativePath, summary, payload);
    }

    private void writePlanningFile(String relativePath, String payload) throws IOException {
        Path workspaceRoot = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR).toAbsolutePath().normalize();
        Path targetPath = workspaceRoot.resolve(relativePath).normalize();
        if (!targetPath.startsWith(workspaceRoot)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "协作产物路径不合法");
        }
        Files.createDirectories(targetPath.getParent());
        Files.writeString(targetPath, payload, StandardCharsets.UTF_8);
    }

    private <T> T invokeWithSingleRetry(Function<String, String> generator, Function<String, T> validator,
                                        String input, String stageName) {
        String currentInput = input;
        IllegalArgumentException validationException = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            String rawOutput = generator.apply(currentInput);
            try {
                return validator.apply(rawOutput);
            } catch (IllegalArgumentException exception) {
                validationException = exception;
                if (attempt == 1) {
                    currentInput = input + "\n\n上一轮输出未通过结构校验，原因：" + exception.getMessage()
                            + "。请严格按系统消息指定结构重新完整输出。";
                }
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, stageName + "结构校验失败：" + validationException.getMessage());
    }

    private String toPrettyJson(JsonNode jsonNode) throws IOException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
    }

    private String toArtifactReferences(List<AgentArtifact> artifacts) {
        return artifacts.stream().map(AgentArtifact::getArtifactType).toList().toString();
    }

    private String getSafeErrorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "未知异常" : message.substring(0, Math.min(message.length(), 500));
    }
}
