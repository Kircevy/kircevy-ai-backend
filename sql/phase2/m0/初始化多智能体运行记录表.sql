-- 二阶段 M0：多智能体运行记录的持久化基础设施。
-- 启用 multi-agent.enabled 前，请在 kircevy_ai 数据库中执行一次本脚本。

create table if not exists generation_run
(
    id             bigint       not null primary key comment '主键',
    runId          varchar(64)  not null comment '对外运行标识',
    appId          bigint       not null comment '应用标识',
    userId         bigint       not null comment '所属用户标识',
    strategy       varchar(32)  not null comment '执行策略：DIRECT 或 MULTI_AGENT',
    status         varchar(32)  not null comment '运行状态',
    codeGenType    varchar(64)  not null comment '代码生成类型',
    promptDigest   char(64)     null comment '提示词 SHA-256 摘要',
    configSnapshot mediumtext   null comment '运行配置快照',
    errorMessage   text         null comment '结束时的错误信息',
    startedTime    datetime     null,
    finishedTime   datetime     null,
    createTime     datetime     not null default current_timestamp,
    updateTime     datetime     not null default current_timestamp on update current_timestamp,
    isDelete       tinyint      not null default 0,
    unique key uk_generation_run_run_id (runId),
    key idx_generation_run_app_time (appId, createTime),
    key idx_generation_run_user_time (userId, createTime),
    key idx_generation_run_status (status)
) comment '生成运行持久化记录' collate = utf8mb4_unicode_ci;

create table if not exists agent_task
(
    id             bigint       not null primary key,
    runId          varchar(64)  not null,
    taskKey        varchar(128) not null,
    role           varchar(64)  not null,
    status         varchar(32)  not null,
    attempt        int          not null default 1,
    dependsOn      text         null,
    inputArtifacts text         null,
    outputArtifacts text        null,
    errorMessage   text         null,
    startedTime    datetime     null,
    finishedTime   datetime     null,
    createTime     datetime     not null default current_timestamp,
    updateTime     datetime     not null default current_timestamp on update current_timestamp,
    isDelete       tinyint      not null default 0,
    unique key uk_agent_task_run_key_attempt (runId, taskKey, attempt),
    key idx_agent_task_run_status (runId, status)
) comment '智能体任务项' collate = utf8mb4_unicode_ci;

create table if not exists agent_artifact
(
    id              bigint       not null primary key,
    runId           varchar(64)  not null,
    taskId          bigint       null,
    artifactType    varchar(64)  not null,
    artifactVersion int          not null default 1,
    relativePath    varchar(1024) null,
    checksum        varchar(128) null,
    summary         text         null,
    payload         mediumtext   null,
    createTime      datetime     not null default current_timestamp,
    updateTime      datetime     not null default current_timestamp on update current_timestamp,
    isDelete        tinyint      not null default 0,
    unique key uk_agent_artifact_version (runId, artifactType, artifactVersion),
    key idx_agent_artifact_task (taskId)
) comment '带版本的智能体产物' collate = utf8mb4_unicode_ci;

create table if not exists agent_event
(
    id             bigint       not null primary key,
    runId          varchar(64)  not null,
    taskId         bigint       null,
    eventSeq       bigint       not null,
    eventType      varchar(64)  not null,
    payload        mediumtext   null,
    createTime     datetime     not null default current_timestamp,
    updateTime     datetime     not null default current_timestamp on update current_timestamp,
    isDelete       tinyint      not null default 0,
    unique key uk_agent_event_run_seq (runId, eventSeq),
    key idx_agent_event_run_time (runId, createTime)
) comment '可重放的智能体事件' collate = utf8mb4_unicode_ci;
