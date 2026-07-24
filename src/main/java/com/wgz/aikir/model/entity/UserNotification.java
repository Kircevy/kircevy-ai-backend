package com.wgz.aikir.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内通知实体。
 */
@Data
@Table("user_notification")
public class UserNotification implements Serializable {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    @Column("userId")
    private Long userId;

    private String type;
    private String title;
    private String content;

    @Column("targetPath")
    private String targetPath;

    @Column("isRead")
    private Integer isRead;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
