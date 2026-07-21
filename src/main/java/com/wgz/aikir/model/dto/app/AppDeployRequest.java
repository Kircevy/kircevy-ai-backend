package com.wgz.aikir.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 应用部署请求
 */
@Data
public class AppDeployRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 部署模式（枚举值）：
     * - code_download：代码下载模式，下载完整源码 zip
     * - docker_compose：Docker 一键部署模式，平台自动执行 docker-compose up
     * 为空时默认 code_download
     */
    private String deployMode;

    private static final long serialVersionUID = 1L;
}