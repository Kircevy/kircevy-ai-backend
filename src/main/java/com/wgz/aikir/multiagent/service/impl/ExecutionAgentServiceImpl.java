package com.wgz.aikir.multiagent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.wgz.aikir.constant.AppConstant;
import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.exception.ErrorCode;
import com.wgz.aikir.exception.ThrowUtils;
import com.wgz.aikir.model.entity.User;
import com.wgz.aikir.multiagent.ai.PlanningAiServiceFactory;
import com.wgz.aikir.multiagent.config.MultiAgentProperties;
import com.wgz.aikir.multiagent.domain.entity.AgentArtifact;
import com.wgz.aikir.multiagent.domain.entity.AgentTask;
import com.wgz.aikir.multiagent.domain.entity.GenerationRun;
import com.wgz.aikir.multiagent.domain.enums.AgentTaskStatusEnum;
import com.wgz.aikir.multiagent.domain.enums.GenerationRunStatusEnum;
import com.wgz.aikir.multiagent.execution.ApiContractVerifier;
import com.wgz.aikir.multiagent.execution.FileManifestRetryExecutor;
import com.wgz.aikir.multiagent.execution.WorkspaceBuildService;
import com.wgz.aikir.multiagent.execution.WorkspaceFileService;
import com.wgz.aikir.multiagent.streaming.AgentStreamingResponseCollector;
import com.wgz.aikir.multiagent.streaming.AgentOutputStreamHub;
import com.wgz.aikir.multiagent.mapper.AgentArtifactMapper;
import com.wgz.aikir.multiagent.service.ExecutionAgentService;
import com.wgz.aikir.multiagent.service.ExecutionAgentWorker;
import com.wgz.aikir.multiagent.service.GenerationRunService;
import dev.langchain4j.service.TokenStream;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** M2 编排：冻结规划产物后并行编码，提前发布预览，并在确定性检查通过后提升产物。 */
@Slf4j
@Service
public class ExecutionAgentServiceImpl implements ExecutionAgentService {

    private static final String FRONTEND_TASK_KEY = "frontend_generation";
    private static final String BACKEND_TASK_KEY = "backend_generation";
    private static final String FRONTEND_MANIFEST_STREAM_KEY = "frontend_manifest";
    private static final String BACKEND_MANIFEST_STREAM_KEY = "backend_manifest";
    private static final String INTEGRATION_TASK_KEY = "integration";
    private static final String PRODUCT_SPEC = "PRODUCT_SPEC";
    private static final String ARCHITECTURE = "ARCHITECTURE";
    private static final String API_CONTRACT = "API_CONTRACT";
    private static final String TASK_MANIFEST = "TASK_MANIFEST";

    @Resource
    private MultiAgentProperties multiAgentProperties;

    @Resource
    private GenerationRunService generationRunService;

    @Resource
    private AgentArtifactMapper agentArtifactMapper;

    @Resource
    private PlanningAiServiceFactory planningAiServiceFactory;

    @Resource
    private WorkspaceFileService workspaceFileService;

    @Resource
    private FileManifestRetryExecutor fileManifestRetryExecutor;

    @Resource
    private WorkspaceBuildService workspaceBuildService;

    @Resource
    private ApiContractVerifier apiContractVerifier;

    @Resource
    private AgentStreamingResponseCollector streamingResponseCollector;

    @Resource
    private AgentOutputStreamHub agentOutputStreamHub;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    @Qualifier("multiAgentExecutionExecutor")
    private Executor executionExecutor;

    @Lazy
    @Resource
    private ExecutionAgentWorker executionAgentWorker;

    @Override
    public synchronized GenerationRun startExecutionRun(String runId, User user) {
        ThrowUtils.throwIf(!multiAgentProperties.isEnabled() || !multiAgentProperties.isExecutionEnabled(),
                ErrorCode.NO_AUTH_ERROR, "当前未启用协作执行功能");
        GenerationRun run = generationRunService.getRunForOwner(runId, user);
        ThrowUtils.throwIf(!GenerationRunStatusEnum.SUCCEEDED.name().equals(run.getStatus()), ErrorCode.OPERATION_ERROR,
                "仅已完成规划且尚未执行的协作运行可以启动 M2");
        ThrowUtils.throwIf(agentArtifactMapper.selectCountByQuery(QueryWrapper.create()
                        .eq("runId", runId).eq("artifactType", "VALIDATION_REPORT")) > 0,
                ErrorCode.OPERATION_ERROR, "当前协作运行已完成 M2 执行，不能重复提升产物");
        PlanningInputs inputs = loadInputs(runId);
        validateManifest(inputs.taskManifest());
        run.setStatus(GenerationRunStatusEnum.EXECUTING.name());
        run.setErrorMessage(null);
        run.setFinishedTime(null);
        run.setUpdateTime(LocalDateTime.now());
        generationRunService.updateById(run);
        executionAgentWorker.execute(runId);
        return run;
    }

    @Override
    public void executeRun(String runId) {
        try {
            GenerationRun run = generationRunService.getOne(QueryWrapper.create().eq("runId", runId));
            if (run == null || !GenerationRunStatusEnum.EXECUTING.name().equals(run.getStatus())) {
                return;
            }
            PlanningInputs inputs = loadInputs(runId);
            Path runRoot = runRoot(runId);
            Path frontendRoot = runRoot.resolve("workspace").resolve("frontend");
            Path backendRoot = runRoot.resolve("workspace").resolve("backend");
            Files.createDirectories(frontendRoot);
            Files.createDirectories(backendRoot);

            CompletableFuture<TaskResult> frontendFuture = CompletableFuture.supplyAsync(
                    () -> generateFrontend(run, inputs, frontendRoot), executionExecutor);
            CompletableFuture<TaskResult> backendFuture = CompletableFuture.supplyAsync(
                    () -> generateBackend(run, inputs, backendRoot), executionExecutor);
            TaskResult frontend = frontendFuture.join();
            TaskResult backend = backendFuture.join();
            if (!frontend.success() || !backend.success()) {
                generationRunService.finishRun(runId, GenerationRunStatusEnum.FAILED,
                        firstFailure(frontend, backend));
                return;
            }
            integrateAndPromote(run, inputs, frontendRoot, backendRoot);
        } catch (Exception exception) {
            String message = safeMessage(exception);
            log.warn("M2 协作执行失败，runId：{}，原因：{}", runId, message);
            generationRunService.finishRun(runId, GenerationRunStatusEnum.FAILED, message);
        }
    }

    private TaskResult generateFrontend(GenerationRun run, PlanningInputs inputs, Path frontendRoot) {
        AgentTask task = generationRunService.createTask(run.getRunId(), FRONTEND_TASK_KEY, "FRONTEND_AGENT", "[]",
                "[\"PRODUCT_SPEC\",\"ARCHITECTURE\",\"API_CONTRACT\",\"TASK_MANIFEST\"]");
        generationRunService.startTask(task);
        try {
            List<String> files = generateFrontendFiles(run, inputs, frontendRoot);
            WorkspaceBuildService.BuildResult build = workspaceBuildService.buildFrontend(frontendRoot);
            if (!build.success()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "前端构建失败：" + compact(build.summary()));
            }
            Path previewRoot = runRoot(run.getRunId()).resolve("preview");
            copyDirectory(frontendRoot.resolve("dist"), previewRoot);
            AgentArtifact artifact = saveArtifact(run, task, "FRONTEND_WORKSPACE", "artifacts/frontend-workspace.json",
                    "前端工作区文件 " + files.size() + " 个", Map.of("files", files));
            AgentArtifact preview = saveArtifact(run, task, "PREVIEW", "artifacts/preview.json",
                    "前端预览已构建", Map.of("previewKey", "multiagent_" + run.getRunId() + "/preview"));
            generationRunService.publishEvent(run.getRunId(), task.getId(), "PREVIEW_READY",
                    Map.of("previewKey", "multiagent_" + run.getRunId() + "/preview"));
            generationRunService.finishTask(task, AgentTaskStatusEnum.SUCCEEDED,
                    List.of(artifact.getArtifactType(), preview.getArtifactType()).toString(), null);
            return TaskResult.succeeded();
        } catch (Exception exception) {
            generationRunService.finishTask(task, AgentTaskStatusEnum.FAILED, "[]", safeMessage(exception));
            return TaskResult.failed(safeMessage(exception));
        }
    }

    private TaskResult generateBackend(GenerationRun run, PlanningInputs inputs, Path backendRoot) {
        AgentTask task = generationRunService.createTask(run.getRunId(), BACKEND_TASK_KEY, "BACKEND_AGENT", "[]",
                "[\"PRODUCT_SPEC\",\"ARCHITECTURE\",\"API_CONTRACT\",\"TASK_MANIFEST\"]");
        generationRunService.startTask(task);
        try {
            List<String> files = generateBackendFiles(run, inputs, backendRoot);
            WorkspaceBuildService.BuildResult build = workspaceBuildService.compileBackend(backendRoot);
            if (!build.success()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "后端编译失败：" + compact(build.summary()));
            }
            AgentArtifact artifact = saveArtifact(run, task, "BACKEND_WORKSPACE", "artifacts/backend-workspace.json",
                    "后端工作区文件 " + files.size() + " 个", Map.of("files", files));
            generationRunService.finishTask(task, AgentTaskStatusEnum.SUCCEEDED,
                    List.of(artifact.getArtifactType()).toString(), null);
            return TaskResult.succeeded();
        } catch (Exception exception) {
            generationRunService.finishTask(task, AgentTaskStatusEnum.FAILED, "[]", safeMessage(exception));
            return TaskResult.failed(safeMessage(exception));
        }
    }

    private List<String> generateFrontendFiles(GenerationRun run, PlanningInputs inputs, Path workspace) throws IOException {
        List<String> files = fileManifestRetryExecutor.generate((prompt, attempt) -> collectAndSaveRawResponse(
                run, FRONTEND_MANIFEST_STREAM_KEY, FRONTEND_TASK_KEY, "manifest-" + attempt,
                planningAiServiceFactory.createFrontendManifestAgent().generateFrontendFileManifest(prompt)),
                workspaceFileService::parseFileManifest, inputs.toPrompt());
        String projectContext = inputs.toPrompt() + "\n\nfile-manifest.json:\n" + objectMapper.writeValueAsString(Map.of("files", files));
        for (int index = 0; index < files.size(); index++) {
            String path = files.get(index);
            agentOutputStreamHub.append(run.getRunId(), FRONTEND_TASK_KEY, "\n\n// " + path + "\n");
            String source = collectAndSaveRawResponse(run, FRONTEND_TASK_KEY, FRONTEND_TASK_KEY, "file-" + index,
                    planningAiServiceFactory.createFrontendExecutionAgent().generateFrontendSourceFile(
                            projectContext + "\n\ntarget-file: " + path));
            workspaceFileService.writeFile(workspace, path, source);
        }
        return files;
    }

    private List<String> generateBackendFiles(GenerationRun run, PlanningInputs inputs, Path workspace) throws IOException {
        List<String> files = fileManifestRetryExecutor.generate((prompt, attempt) -> collectAndSaveRawResponse(
                run, BACKEND_MANIFEST_STREAM_KEY, BACKEND_TASK_KEY, "manifest-" + attempt,
                planningAiServiceFactory.createBackendManifestAgent().generateBackendFileManifest(prompt)),
                workspaceFileService::parseFileManifest, inputs.toPrompt());
        String projectContext = inputs.toPrompt() + "\n\nfile-manifest.json:\n" + objectMapper.writeValueAsString(Map.of("files", files));
        for (int index = 0; index < files.size(); index++) {
            String path = files.get(index);
            agentOutputStreamHub.append(run.getRunId(), BACKEND_TASK_KEY, "\n\n// " + path + "\n");
            String source = collectAndSaveRawResponse(run, BACKEND_TASK_KEY, BACKEND_TASK_KEY, "file-" + index,
                    planningAiServiceFactory.createBackendExecutionAgent().generateBackendSourceFile(
                            projectContext + "\n\ntarget-file: " + path));
            workspaceFileService.writeFile(workspace, path, source);
        }
        return files;
    }

    private String collectAndSaveRawResponse(GenerationRun run, String streamKey, String taskKey, String stage,
                                              TokenStream tokenStream) throws IOException {
        try {
            String content = streamingResponseCollector.collect(run.getRunId(), streamKey, tokenStream);
            saveRawResponse(run, taskKey, stage, content);
            return content;
        } catch (AgentStreamingResponseCollector.StreamingGenerationException exception) {
            saveRawResponse(run, taskKey, stage, exception.partialResponse());
            throw exception;
        }
    }

    private void saveRawResponse(GenerationRun run, String taskKey, String stage, String content) throws IOException {
        Path directory = runRoot(run.getRunId()).resolve("diagnostics").resolve(taskKey).normalize();
        if (!directory.startsWith(runRoot(run.getRunId()))) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "诊断响应写入路径不合法");
        }
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(stage + ".txt"), content, StandardCharsets.UTF_8);
    }

    private void integrateAndPromote(GenerationRun run, PlanningInputs inputs, Path frontendRoot, Path backendRoot)
            throws IOException {
        AgentTask task = generationRunService.createTask(run.getRunId(), INTEGRATION_TASK_KEY, "INTEGRATION_AGENT",
                "[\"frontend_generation\",\"backend_generation\"]", "[\"API_CONTRACT\"]");
        generationRunService.startTask(task);
        try {
            run.setStatus(GenerationRunStatusEnum.BUILDING.name());
            run.setUpdateTime(LocalDateTime.now());
            generationRunService.updateById(run);
            ApiContractVerifier.VerificationResult verification = apiContractVerifier.verify(
                    inputs.apiContract(), frontendRoot, backendRoot);
            if (!verification.success()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, verification.summary());
            }
            promote(run, frontendRoot, backendRoot);
            AgentArtifact report = saveArtifact(run, task, "VALIDATION_REPORT", "artifacts/validation-report.json",
                    "契约检查、前端构建与后端编译均已通过", Map.of("apiContract", verification.summary(),
                            "frontendBuild", "passed", "backendCompile", "passed"));
            generationRunService.finishTask(task, AgentTaskStatusEnum.SUCCEEDED,
                    List.of(report.getArtifactType()).toString(), null);
            generationRunService.finishRun(run.getRunId(), GenerationRunStatusEnum.SUCCEEDED, null);
        } catch (Exception exception) {
            generationRunService.finishTask(task, AgentTaskStatusEnum.FAILED, "[]", safeMessage(exception));
            throw exception;
        }
    }

    private void promote(GenerationRun run, Path frontendRoot, Path backendRoot) throws IOException {
        Path outputRoot = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR).toAbsolutePath().normalize();
        Path candidate = outputRoot.resolve("multiagent_" + run.getRunId()).resolve("promotion");
        Path target = outputRoot.resolve("fullstack_" + run.getAppId());
        Path backup = outputRoot.resolve("fullstack_" + run.getAppId() + ".backup." + run.getRunId());
        deleteDirectory(candidate);
        Files.createDirectories(candidate);
        copyDirectory(frontendRoot, candidate.resolve("frontend"));
        copyDirectory(backendRoot, candidate.resolve("backend"));
        boolean previousOutputMoved = false;
        try {
            if (Files.exists(target)) {
                deleteDirectory(backup);
                moveDirectory(target, backup);
                previousOutputMoved = true;
            }
            moveDirectory(candidate, target);
            savePromotionMarker(run, target);
        } catch (IOException exception) {
            if (previousOutputMoved && !Files.exists(target) && Files.exists(backup)) {
                try {
                    moveDirectory(backup, target);
                } catch (IOException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }
            throw exception;
        }
    }

    private void savePromotionMarker(GenerationRun run, Path target) throws IOException {
        Files.writeString(target.resolve(".multiagent-run.json"), objectMapper.writeValueAsString(Map.of(
                "runId", run.getRunId(), "promotedAt", LocalDateTime.now().toString())), StandardCharsets.UTF_8);
    }

    private AgentArtifact saveArtifact(GenerationRun run, AgentTask task, String type, String relativePath,
                                       String summary, Object payload) throws IOException {
        String content = objectMapper.writeValueAsString(payload);
        Path file = runRoot(run.getRunId()).resolve(relativePath).normalize();
        if (!file.startsWith(runRoot(run.getRunId()))) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "协作产物路径不合法");
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return generationRunService.saveArtifact(run.getRunId(), task.getId(), type,
                "multiagent_" + run.getRunId() + "/" + relativePath, summary, content);
    }

    private PlanningInputs loadInputs(String runId) {
        List<AgentArtifact> artifacts = agentArtifactMapper.selectListByQuery(QueryWrapper.create().eq("runId", runId));
        return new PlanningInputs(requiredArtifact(artifacts, PRODUCT_SPEC), requiredArtifact(artifacts, ARCHITECTURE),
                requiredArtifact(artifacts, API_CONTRACT), requiredArtifact(artifacts, TASK_MANIFEST));
    }

    private String requiredArtifact(List<AgentArtifact> artifacts, String type) {
        return artifacts.stream().filter(artifact -> type.equals(artifact.getArtifactType()))
                .max(java.util.Comparator.comparing(AgentArtifact::getArtifactVersion))
                .map(AgentArtifact::getPayload)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR, "缺少冻结规划产物：" + type));
    }

    private void validateManifest(String taskManifest) {
        try {
            JsonNode tasks = objectMapper.readTree(taskManifest).path("tasks");
            boolean frontend = false;
            boolean backend = false;
            for (JsonNode task : tasks) {
                String key = task.path("taskKey").asText();
                if (FRONTEND_TASK_KEY.equals(key)) {
                    frontend = task.path("writeScopes").size() == 1 && "frontend/**".equals(task.path("writeScopes").get(0).asText());
                }
                if (BACKEND_TASK_KEY.equals(key)) {
                    backend = task.path("writeScopes").size() == 1 && "backend/**".equals(task.path("writeScopes").get(0).asText());
                }
            }
            ThrowUtils.throwIf(!frontend || !backend, ErrorCode.OPERATION_ERROR, "任务清单的前后端写入范围不合法");
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务清单无法解析");
        }
    }

    private Path runRoot(String runId) {
        return Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR).toAbsolutePath().normalize().resolve("multiagent_" + runId).normalize();
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private void deleteDirectory(Path target) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private String firstFailure(TaskResult frontend, TaskResult backend) {
        return !frontend.success() ? frontend.message() : backend.message();
    }

    private String safeMessage(Exception exception) {
        if (hasTimeoutCause(exception)) {
            return "模型网关响应超时，请确认推理服务负载后重新执行协作任务";
        }
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "未知异常" : compact(message);
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getClass().getSimpleName().contains("Timeout")
                    || (current.getMessage() != null && current.getMessage().contains("Read timed out"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String compact(String text) {
        return text.substring(0, Math.min(text.length(), 500));
    }

    private record PlanningInputs(String productSpec, String architecture, String apiContract, String taskManifest) {
        String toPrompt() {
            return "product-spec.json:\n" + productSpec + "\n\narchitecture.json:\n" + architecture
                    + "\n\napi-contract.yaml:\n" + apiContract + "\n\ntask-manifest.json:\n" + taskManifest;
        }
    }

    private record TaskResult(boolean success, String message) {
        static TaskResult succeeded() {
            return new TaskResult(true, "");
        }

        static TaskResult failed(String message) {
            return new TaskResult(false, message);
        }
    }
}
