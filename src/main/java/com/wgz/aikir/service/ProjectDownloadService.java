package com.wgz.aikir.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface ProjectDownloadService {

    /**
     * 下载项目代码压缩包
     *
     * @param projectPath 项目路径
     * @param projectName 项目名称
     * @param response    响应
     */
    void downloadProjectAsZip(String projectPath, String projectName, HttpServletResponse response);
}
