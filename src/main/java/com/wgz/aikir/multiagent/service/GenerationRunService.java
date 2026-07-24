package com.wgz.aikir.multiagent.service;

import com.mybatisflex.core.service.IService;
import com.wgz.aikir.multiagent.domain.entity.AgentEvent;
import com.wgz.aikir.multiagent.domain.entity.AgentArtifact;
import com.wgz.aikir.multiagent.domain.entity.AgentTask;
import com.wgz.aikir.model.entity.App;
import com.wgz.aikir.multiagent.domain.entity.GenerationRun;
import com.wgz.aikir.model.entity.User;
import com.wgz.aikir.model.enums.CodeGenTypeEnum;
import com.wgz.aikir.multiagent.domain.enums.AgentTaskStatusEnum;
import com.wgz.aikir.multiagent.domain.enums.GenerationRunStatusEnum;

import java.util.List;

/** 二阶段运行记录与事件的持久化服务接口。 */
public interface GenerationRunService extends IService<GenerationRun> {

    /** 当前请求未启用二阶段功能开关时返回 null。 */
    GenerationRun startDirectRunIfEnabled(App app, User user, CodeGenTypeEnum codeGenType, String prompt);

    /** 为 M1 及后续里程碑创建协作运行，不会触发一期代码生成。 */
    GenerationRun startMultiAgentRun(App app, User user, CodeGenTypeEnum codeGenType, String prompt);

    /**
     * 应用重启后将未结束的协作规划标记为失败，避免旧进程中断的任务永久占用运行状态。
     *
     * @return 被恢复处理的运行数量
     */
    int recoverInterruptedMultiAgentRuns();

    /** 创建协作任务并记录待执行状态。 */
    AgentTask createTask(String runId, String taskKey, String role, String dependsOn, String inputArtifacts);

    /** 标记任务开始执行。 */
    void startTask(AgentTask task);

    /** 标记任务结束，并保存输出产物标识。 */
    void finishTask(AgentTask task, AgentTaskStatusEnum status, String outputArtifacts, String errorMessage);

    /** 保存版本化协作产物并发布产物就绪事件。 */
    AgentArtifact saveArtifact(String runId, Long taskId, String artifactType, String relativePath,
                               String summary, String payload);

    /** 更新运行状态并发布运行结束事件。 */
    void finishRun(String runId, GenerationRunStatusEnum status, String errorMessage);

    void completeDirectRun(String runId);

    void failDirectRun(String runId, Throwable error);

    GenerationRun getRunForOwner(String runId, User user);

    GenerationRun getLatestRunForOwner(Long appId, User user);

    List<AgentEvent> listEventsForOwner(String runId, Long afterSequence, User user);

    /** 查询当前用户有权访问的协作产物。 */
    List<AgentArtifact> listArtifactsForOwner(String runId, User user);
}
