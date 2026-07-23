package com.wgz.aikir.multiagent.service;

import com.mybatisflex.core.service.IService;
import com.wgz.aikir.multiagent.domain.entity.AgentEvent;
import com.wgz.aikir.model.entity.App;
import com.wgz.aikir.multiagent.domain.entity.GenerationRun;
import com.wgz.aikir.model.entity.User;
import com.wgz.aikir.model.enums.CodeGenTypeEnum;

import java.util.List;

/** 二阶段运行记录与事件的持久化服务接口。 */
public interface GenerationRunService extends IService<GenerationRun> {

    /** 当前请求未启用二阶段功能开关时返回 null。 */
    GenerationRun startDirectRunIfEnabled(App app, User user, CodeGenTypeEnum codeGenType, String prompt);

    void completeDirectRun(String runId);

    void failDirectRun(String runId, Throwable error);

    GenerationRun getRunForOwner(String runId, User user);

    GenerationRun getLatestRunForOwner(Long appId, User user);

    List<AgentEvent> listEventsForOwner(String runId, Long afterSequence, User user);
}
