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
import com.wgz.aikir.multiagent.mapper.AgentTaskMapper;
import com.wgz.aikir.multiagent.mapper.GenerationRunMapper;
import com.wgz.aikir.multiagent.domain.entity.AgentEvent;
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
    public List<AgentEvent> listEventsForOwner(String runId, Long afterSequence, User user) {
        getRunForOwner(runId, user);
        long sequence = afterSequence == null ? 0L : Math.max(0L, afterSequence);
        return agentEventMapper.selectListByQuery(QueryWrapper.create()
                .eq("runId", runId)
                .gt("eventSeq", sequence)
                .orderBy("eventSeq", true));
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
        long eventSeq = eventSequences.computeIfAbsent(runId, ignored -> new AtomicLong()).incrementAndGet();
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
}
