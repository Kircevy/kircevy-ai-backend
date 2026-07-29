package com.wgz.aikir.multiagent.service.impl;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wgz.aikir.multiagent.config.MultiAgentProperties;
import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.exception.ErrorCode;
import com.wgz.aikir.exception.ThrowUtils;
import com.wgz.aikir.multiagent.mapper.AgentEventMapper;
import com.wgz.aikir.multiagent.mapper.AgentArtifactMapper;
import com.wgz.aikir.multiagent.mapper.AgentTaskMapper;
import com.wgz.aikir.multiagent.mapper.GenerationRunMapper;
import com.wgz.aikir.multiagent.domain.entity.AgentEvent;
import com.wgz.aikir.multiagent.domain.entity.AgentArtifact;
import com.wgz.aikir.multiagent.domain.entity.AgentTask;
import com.wgz.aikir.model.entity.App;
import com.wgz.aikir.multiagent.domain.entity.GenerationRun;
import com.wgz.aikir.model.entity.User;
import com.wgz.aikir.multiagent.domain.enums.AgentEventTypeEnum;
import com.wgz.aikir.multiagent.domain.enums.AgentTaskStatusEnum;
import com.wgz.aikir.model.enums.CodeGenTypeEnum;
import com.wgz.aikir.multiagent.domain.enums.GenerationRunStatusEnum;
import com.wgz.aikir.multiagent.domain.enums.GenerationStrategyEnum;
import com.wgz.aikir.multiagent.service.GenerationRunService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * M0 阶段在不改动一阶段生成器的前提下，持久化记录全栈生成运行过程。
 * 后续里程碑会复用运行、任务和事件记录实现真正的智能体协作编排。
 */
@Slf4j
@Service
public class GenerationRunServiceImpl extends ServiceImpl<GenerationRunMapper, GenerationRun>
        implements GenerationRunService {

    private static final String DIRECT_TASK_KEY = "direct_generation";

    @Resource
    private MultiAgentProperties multiAgentProperties;

    @Resource
    private AgentTaskMapper agentTaskMapper;

    @Resource
    private AgentArtifactMapper agentArtifactMapper;

    @Resource
    private AgentEventMapper agentEventMapper;

    private final Map<String, Long> directTaskIds = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> eventSequences = new ConcurrentHashMap<>();

    @Override
    public GenerationRun startDirectRunIfEnabled(App app, User user, CodeGenTypeEnum codeGenType, String prompt) {
        if (!isEnabledFor(codeGenType)) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        String runId = UUID.randomUUID().toString();
        GenerationRun run = GenerationRun.builder()
                .runId(runId)
                .appId(app.getId())
                .userId(user.getId())
                .strategy(GenerationStrategyEnum.DIRECT.name())
                .status(GenerationRunStatusEnum.EXECUTING.name())
                .codeGenType(codeGenType.getValue())
                .promptDigest(SecureUtil.sha256(prompt))
                .configSnapshot(JSONUtil.toJsonStr(Map.of(
                        "multiAgentEnabled", true,
                        "phase", "M0",
                        "strategy", GenerationStrategyEnum.DIRECT.name()
                )))
                .startedTime(now)
                .createTime(now)
                .updateTime(now)
                .isDelete(0)
                .build();
        boolean saved = this.save(run);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "创建协作运行记录失败");

        AgentTask task = AgentTask.builder()
                .runId(runId)
                .taskKey(DIRECT_TASK_KEY)
                .role("DIRECT_GENERATOR")
                .status(AgentTaskStatusEnum.RUNNING.name())
                .attempt(1)
                .dependsOn("[]")
                .inputArtifacts("[]")
                .outputArtifacts("[]")
                .startedTime(now)
                .createTime(now)
                .updateTime(now)
                .isDelete(0)
                .build();
        int inserted = agentTaskMapper.insert(task);
        ThrowUtils.throwIf(inserted != 1, ErrorCode.OPERATION_ERROR, "创建协作任务记录失败");
        directTaskIds.put(runId, task.getId());
        appendEvent(runId, task.getId(), AgentEventTypeEnum.RUN_STARTED, Map.of(
                "strategy", GenerationStrategyEnum.DIRECT.name(),
                "codeGenType", codeGenType.getValue()
        ));
        appendEvent(runId, task.getId(), AgentEventTypeEnum.TASK_STARTED, Map.of(
                "taskKey", DIRECT_TASK_KEY,
                "role", task.getRole()
        ));
        return run;
    }

    @Override
    public synchronized GenerationRun startMultiAgentRun(App app, User user, CodeGenTypeEnum codeGenType, String prompt) {
        ThrowUtils.throwIf(!isEnabledFor(codeGenType) || !multiAgentProperties.isPlanningEnabled(),
                ErrorCode.NO_AUTH_ERROR, "当前未启用协作规划功能");
        ThrowUtils.throwIf(hasActiveMultiAgentRun(app.getId(), user.getId()), ErrorCode.OPERATION_ERROR,
                "当前应用已有协作规划正在执行，请等待完成后再重新生成");
        LocalDateTime now = LocalDateTime.now();
        String runId = UUID.randomUUID().toString();
        GenerationRun run = GenerationRun.builder()
                .runId(runId)
                .appId(app.getId())
                .userId(user.getId())
                .strategy(GenerationStrategyEnum.MULTI_AGENT.name())
                .status(GenerationRunStatusEnum.PLANNING.name())
                .codeGenType(codeGenType.getValue())
                .promptDigest(SecureUtil.sha256(prompt))
                .configSnapshot(JSONUtil.toJsonStr(Map.of(
                        "multiAgentEnabled", true,
                        "phase", "M1",
                        "strategy", GenerationStrategyEnum.MULTI_AGENT.name()
                )))
                .startedTime(now)
                .createTime(now)
                .updateTime(now)
                .isDelete(0)
                .build();
        boolean saved = this.save(run);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "创建协作规划运行记录失败");
        appendEvent(runId, null, AgentEventTypeEnum.RUN_STARTED, Map.of(
                "strategy", GenerationStrategyEnum.MULTI_AGENT.name(),
                "stage", "PLANNING",
                "codeGenType", codeGenType.getValue()
        ));
        return run;
    }

    @Override
    public int recoverInterruptedMultiAgentRuns() {
        List<GenerationRun> interruptedRuns = new ArrayList<>(this.list(QueryWrapper.create()
                .eq("strategy", GenerationStrategyEnum.MULTI_AGENT.name())
                .eq("status", GenerationRunStatusEnum.PLANNING.name())));
        interruptedRuns.addAll(this.list(QueryWrapper.create()
                .eq("strategy", GenerationStrategyEnum.MULTI_AGENT.name())
                .eq("status", GenerationRunStatusEnum.EXECUTING.name())));
        interruptedRuns.addAll(this.list(QueryWrapper.create()
                .eq("strategy", GenerationStrategyEnum.MULTI_AGENT.name())
                .eq("status", GenerationRunStatusEnum.BUILDING.name())));
        for (GenerationRun run : interruptedRuns) {
            finishRun(run.getRunId(), GenerationRunStatusEnum.FAILED,
                    "服务重启导致协作规划中断，请重新生成协作规划");
        }
        return interruptedRuns.size();
    }

    /**
     * 同一应用在同一时刻仅允许一个协作规划运行，避免重复调用智能体并产生互相覆盖的规划结果。
     */
    private boolean hasActiveMultiAgentRun(Long appId, Long userId) {
        QueryWrapper planningQuery = QueryWrapper.create()
                .eq("appId", appId)
                .eq("userId", userId)
                .eq("strategy", GenerationStrategyEnum.MULTI_AGENT.name())
                .eq("status", GenerationRunStatusEnum.PLANNING.name());
        if (this.count(planningQuery) > 0) {
            return true;
        }
        QueryWrapper executingQuery = QueryWrapper.create()
                .eq("appId", appId)
                .eq("userId", userId)
                .eq("strategy", GenerationStrategyEnum.MULTI_AGENT.name())
                .eq("status", GenerationRunStatusEnum.EXECUTING.name());
        if (this.count(executingQuery) > 0) {
            return true;
        }
        return this.count(QueryWrapper.create()
                .eq("appId", appId)
                .eq("userId", userId)
                .eq("strategy", GenerationStrategyEnum.MULTI_AGENT.name())
                .eq("status", GenerationRunStatusEnum.BUILDING.name())) > 0;
    }

    @Override
    public AgentTask createTask(String runId, String taskKey, String role, String dependsOn, String inputArtifacts) {
        ThrowUtils.throwIf(runId == null || runId.isBlank(), ErrorCode.PARAMS_ERROR, "runId 不能为空");
        LocalDateTime now = LocalDateTime.now();
        AgentTask task = AgentTask.builder()
                .runId(runId)
                .taskKey(taskKey)
                .role(role)
                .status(AgentTaskStatusEnum.PENDING.name())
                .attempt(1)
                .dependsOn(dependsOn == null ? "[]" : dependsOn)
                .inputArtifacts(inputArtifacts == null ? "[]" : inputArtifacts)
                .outputArtifacts("[]")
                .createTime(now)
                .updateTime(now)
                .isDelete(0)
                .build();
        int inserted = agentTaskMapper.insert(task);
        ThrowUtils.throwIf(inserted != 1, ErrorCode.OPERATION_ERROR, "创建协作任务记录失败");
        return task;
    }

    @Override
    public void startTask(AgentTask task) {
        ThrowUtils.throwIf(task == null || task.getId() == null, ErrorCode.PARAMS_ERROR, "协作任务不能为空");
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(AgentTaskStatusEnum.RUNNING.name());
        task.setStartedTime(now);
        task.setUpdateTime(now);
        int updated = agentTaskMapper.update(task);
        ThrowUtils.throwIf(updated != 1, ErrorCode.OPERATION_ERROR, "更新协作任务状态失败");
        appendEvent(task.getRunId(), task.getId(), AgentEventTypeEnum.TASK_STARTED, Map.of(
                "taskKey", task.getTaskKey(),
                "role", task.getRole()
        ));
    }

    @Override
    public void finishTask(AgentTask task, AgentTaskStatusEnum status, String outputArtifacts, String errorMessage) {
        ThrowUtils.throwIf(task == null || task.getId() == null, ErrorCode.PARAMS_ERROR, "协作任务不能为空");
        ThrowUtils.throwIf(status != AgentTaskStatusEnum.SUCCEEDED && status != AgentTaskStatusEnum.FAILED,
                ErrorCode.PARAMS_ERROR, "任务结束状态不合法");
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(status.name());
        task.setOutputArtifacts(outputArtifacts == null ? "[]" : outputArtifacts);
        task.setErrorMessage(errorMessage);
        task.setFinishedTime(now);
        task.setUpdateTime(now);
        int updated = agentTaskMapper.update(task);
        ThrowUtils.throwIf(updated != 1, ErrorCode.OPERATION_ERROR, "更新协作任务结果失败");
        appendEvent(task.getRunId(), task.getId(), status == AgentTaskStatusEnum.SUCCEEDED
                ? AgentEventTypeEnum.TASK_COMPLETED : AgentEventTypeEnum.TASK_FAILED,
                Map.of("taskKey", task.getTaskKey(), "status", status.name(),
                        "error", errorMessage == null ? "" : errorMessage));
    }

    @Override
    public AgentArtifact saveArtifact(String runId, Long taskId, String artifactType, String relativePath,
                                      String summary, String payload) {
        ThrowUtils.throwIf(runId == null || runId.isBlank(), ErrorCode.PARAMS_ERROR, "runId 不能为空");
        ThrowUtils.throwIf(artifactType == null || artifactType.isBlank(), ErrorCode.PARAMS_ERROR, "产物类型不能为空");
        List<AgentArtifact> existingArtifacts = agentArtifactMapper.selectListByQuery(QueryWrapper.create()
                .eq("runId", runId)
                .eq("artifactType", artifactType)
                .orderBy("artifactVersion", false)
                .limit(1));
        int nextVersion = existingArtifacts.isEmpty() ? 1 : existingArtifacts.getFirst().getArtifactVersion() + 1;
        LocalDateTime now = LocalDateTime.now();
        AgentArtifact artifact = AgentArtifact.builder()
                .runId(runId)
                .taskId(taskId)
                .artifactType(artifactType)
                .artifactVersion(nextVersion)
                .relativePath(relativePath)
                .checksum(SecureUtil.sha256(payload))
                .summary(summary)
                .payload(payload)
                .createTime(now)
                .updateTime(now)
                .isDelete(0)
                .build();
        int inserted = agentArtifactMapper.insert(artifact);
        ThrowUtils.throwIf(inserted != 1, ErrorCode.OPERATION_ERROR, "保存协作产物失败");
        appendEvent(runId, taskId, AgentEventTypeEnum.ARTIFACT_READY, Map.of(
                "artifactType", artifactType,
                "artifactVersion", nextVersion,
                "relativePath", relativePath == null ? "" : relativePath
        ));
        return artifact;
    }

    @Override
    public void publishEvent(String runId, Long taskId, String eventType, Map<String, Object> payload) {
        ThrowUtils.throwIf(eventType == null || eventType.isBlank(), ErrorCode.PARAMS_ERROR, "事件类型不能为空");
        appendEvent(runId, taskId, AgentEventTypeEnum.valueOf(eventType), payload == null ? Map.of() : payload);
    }

    @Override
    public void finishRun(String runId, GenerationRunStatusEnum status, String errorMessage) {
        ThrowUtils.throwIf(runId == null || runId.isBlank(), ErrorCode.PARAMS_ERROR, "runId 不能为空");
        ThrowUtils.throwIf(status != GenerationRunStatusEnum.SUCCEEDED && status != GenerationRunStatusEnum.FAILED
                        && status != GenerationRunStatusEnum.PARTIAL && status != GenerationRunStatusEnum.CANCELLED,
                ErrorCode.PARAMS_ERROR, "运行结束状态不合法");
        GenerationRun run = this.getOne(QueryWrapper.create().eq("runId", runId));
        ThrowUtils.throwIf(run == null, ErrorCode.NOT_FOUND_ERROR, "协作运行不存在");
        if (GenerationRunStatusEnum.SUCCEEDED.name().equals(run.getStatus())
                || GenerationRunStatusEnum.FAILED.name().equals(run.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        run.setStatus(status.name());
        run.setErrorMessage(errorMessage);
        run.setFinishedTime(now);
        run.setUpdateTime(now);
        this.updateById(run);
        appendEvent(runId, null, status == GenerationRunStatusEnum.SUCCEEDED
                ? AgentEventTypeEnum.RUN_SUCCEEDED : AgentEventTypeEnum.RUN_FAILED,
                Map.of("status", status.name(), "error", errorMessage == null ? "" : errorMessage));
        eventSequences.remove(runId);
    }

    @Override
    public void completeDirectRun(String runId) {
        finishDirectRun(runId, GenerationRunStatusEnum.SUCCEEDED, AgentTaskStatusEnum.SUCCEEDED, null);
    }

    @Override
    public void failDirectRun(String runId, Throwable error) {
        String message = error == null ? "生成任务异常结束" : error.getMessage();
        finishDirectRun(runId, GenerationRunStatusEnum.FAILED, AgentTaskStatusEnum.FAILED, message);
    }

    @Override
    public GenerationRun getRunForOwner(String runId, User user) {
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(runId == null || runId.isBlank(), ErrorCode.PARAMS_ERROR, "runId 不能为空");
        GenerationRun run = this.getOne(QueryWrapper.create().eq("runId", runId));
        ThrowUtils.throwIf(run == null, ErrorCode.NOT_FOUND_ERROR, "协作运行不存在");
        ThrowUtils.throwIf(!user.getId().equals(run.getUserId()), ErrorCode.NO_AUTH_ERROR, "无权访问该协作运行");
        return run;
    }

    @Override
    public GenerationRun getLatestRunForOwner(Long appId, User user) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_LOGIN_ERROR);
        List<GenerationRun> runs = this.list(QueryWrapper.create()
                .eq("appId", appId)
                .eq("userId", user.getId())
                .orderBy("createTime", false)
                .limit(1));
        return runs.isEmpty() ? null : runs.getFirst();
    }

    @Override
    public boolean hasAnyRun(Long appId, Long userId) {
        return this.count(QueryWrapper.create()
                .eq("appId", appId)
                .eq("userId", userId)) > 0;
    }

    @Override
    public List<AgentEvent> listEventsForOwner(String runId, Long afterSequence, User user) {
        getRunForOwner(runId, user);
        long sequence = afterSequence == null ? 0L : Math.max(0L, afterSequence);
        return agentEventMapper.selectListByQuery(QueryWrapper.create()
                .eq("runId", runId)
                .gt("eventSeq", sequence)
                .orderBy("eventSeq", true));
    }

    @Override
    public List<AgentArtifact> listArtifactsForOwner(String runId, User user) {
        getRunForOwner(runId, user);
        return agentArtifactMapper.selectListByQuery(QueryWrapper.create()
                .eq("runId", runId)
                .orderBy("createTime", true));
    }

    private boolean isEnabledFor(CodeGenTypeEnum codeGenType) {
        return multiAgentProperties.isEnabled()
                && (!multiAgentProperties.isFullstackOnly() || codeGenType == CodeGenTypeEnum.FULLSTACK);
    }

    private void finishDirectRun(String runId, GenerationRunStatusEnum runStatus,
                                 AgentTaskStatusEnum taskStatus, String errorMessage) {
        if (runId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        GenerationRun run = this.getOne(QueryWrapper.create().eq("runId", runId));
        if (run == null || !GenerationRunStatusEnum.EXECUTING.name().equals(run.getStatus())) {
            return;
        }
        run.setStatus(runStatus.name());
        run.setErrorMessage(errorMessage);
        run.setFinishedTime(now);
        run.setUpdateTime(now);
        this.updateById(run);

        Long taskId = directTaskIds.remove(runId);
        if (taskId != null) {
            AgentTask task = new AgentTask();
            task.setId(taskId);
            task.setStatus(taskStatus.name());
            task.setErrorMessage(errorMessage);
            task.setFinishedTime(now);
            task.setUpdateTime(now);
            agentTaskMapper.update(task);
        }
        appendEvent(runId, taskId,
                runStatus == GenerationRunStatusEnum.SUCCEEDED
                        ? AgentEventTypeEnum.TASK_COMPLETED : AgentEventTypeEnum.TASK_FAILED,
                Map.of("status", taskStatus.name(), "error", errorMessage == null ? "" : errorMessage));
        appendEvent(runId, taskId,
                runStatus == GenerationRunStatusEnum.SUCCEEDED
                        ? AgentEventTypeEnum.RUN_SUCCEEDED : AgentEventTypeEnum.RUN_FAILED,
                Map.of("status", runStatus.name(), "error", errorMessage == null ? "" : errorMessage));
        eventSequences.remove(runId);
    }

    private void appendEvent(String runId, Long taskId, AgentEventTypeEnum eventType, Map<String, Object> payload) {
        long eventSeq = eventSequences.computeIfAbsent(runId, this::loadLatestEventSequence).incrementAndGet();
        LocalDateTime now = LocalDateTime.now();
        AgentEvent event = AgentEvent.builder()
                .runId(runId)
                .taskId(taskId)
                .eventSeq(eventSeq)
                .eventType(eventType.name())
                .payload(JSONUtil.toJsonStr(payload))
                .createTime(now)
                .updateTime(now)
                .isDelete(0)
                .build();
        int inserted = agentEventMapper.insert(event);
        if (inserted != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "记录协作事件失败");
        }
    }

    /** 从持久化事件中恢复序号，避免服务重启后产生重复序号。 */
    private AtomicLong loadLatestEventSequence(String runId) {
        List<AgentEvent> events = agentEventMapper.selectListByQuery(QueryWrapper.create()
                .eq("runId", runId)
                .orderBy("eventSeq", false)
                .limit(1));
        long latestSequence = events.isEmpty() ? 0L : events.getFirst().getEventSeq();
        return new AtomicLong(latestSequence);
    }
}
