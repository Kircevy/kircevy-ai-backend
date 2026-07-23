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

/** 单次生成执行的持久化记录。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("generation_run")
public class GenerationRun implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    @Column("runId")
    private String runId;

    @Column("appId")
    private Long appId;

    @Column("userId")
    private Long userId;

    private String strategy;

    private String status;

    @Column("codeGenType")
    private String codeGenType;

    @Column("promptDigest")
    private String promptDigest;

    @Column("configSnapshot")
    private String configSnapshot;

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
