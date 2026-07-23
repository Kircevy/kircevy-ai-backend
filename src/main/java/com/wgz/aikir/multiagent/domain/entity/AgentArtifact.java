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

/** 在后续智能体任务间传递的带版本产物。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("agent_artifact")
public class AgentArtifact implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    @Column("runId")
    private String runId;

    @Column("taskId")
    private Long taskId;

    @Column("artifactType")
    private String artifactType;

    @Column("artifactVersion")
    private Integer artifactVersion;

    @Column("relativePath")
    private String relativePath;

    private String checksum;

    private String summary;

    private String payload;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
