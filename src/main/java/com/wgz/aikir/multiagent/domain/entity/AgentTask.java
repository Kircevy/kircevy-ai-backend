package com.wgz.aikir.multiagent.domain.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 单次生成运行中的可恢复任务单元。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("agent_task")
public class AgentTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    @Column("runId")
    private String runId;

    @Column("taskKey")
    private String taskKey;

    private String role;

    private String status;

    private Integer attempt;

    @Column("dependsOn")
    private String dependsOn;

    @Column("inputArtifacts")
    private String inputArtifacts;

    @Column("outputArtifacts")
    private String outputArtifacts;

    @Column("errorMessage")
    private String errorMessage;

    @Column("startedTime")
    private LocalDateTime startedTime;

    @Column("finishedTime")
    private LocalDateTime finishedTime;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
