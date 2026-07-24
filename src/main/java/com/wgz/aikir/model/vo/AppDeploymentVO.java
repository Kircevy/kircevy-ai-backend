package com.wgz.aikir.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户 Docker 部署应用的运行信息。
 */
@Data
@Builder
public class AppDeploymentVO {

    private Long appId;
    private String appName;
    private String codeGenType;
    private String composeProjectName;
    private String frontendUrl;
    private String backendUrl;
    private String status;
    private String statusMessage;
    private LocalDateTime deployedTime;
}
