package com.wgz.aikir.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.exception.ErrorCode;
import com.wgz.aikir.exception.ThrowUtils;
import com.wgz.aikir.service.ProjectDownloadService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileFilter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

@Service
@Slf4j
public class ProjectDownloadServiceImpl implements ProjectDownloadService {

    /**
     * 需要过滤的文件和目录名称
     */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules",
            ".git",
            "dist",
            "build",
            ".DS_Store",
            ".env",
            "target",
            ".mvn",
            ".idea",
            ".vscode"
    );

    /**
     * 需要过滤的文件扩展名
     */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log",
            ".tmp",
            ".cache"
    );


    /**
     * 项目打包下载
     *
     * @param projectPath 项目路径
     * @param downloadFileName 项目名称
     * @param response    响应
     */
    @Override
    public void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response) {
        // 参数校验
        ThrowUtils.throwIf(StrUtil.isBlank(projectPath), ErrorCode.PARAMS_ERROR, "项目路径错误");
        ThrowUtils.throwIf(StrUtil.isBlank(downloadFileName), ErrorCode.PARAMS_ERROR, "项目名称错误");
        // 构建项目文件
        File projectDir = new File(projectPath);
        ThrowUtils.throwIf(!projectDir.exists(), ErrorCode.PARAMS_ERROR, "项目文件不存在");
        ThrowUtils.throwIf(!projectDir.isDirectory(), ErrorCode.PARAMS_ERROR, "项目文件不是目录结构");
        log.info("开始打包下载项目：{} -> {}.zip", projectPath, downloadFileName);
        // 构建 HTTP 响应头
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                String.format("attachment; filename=\"%s.zip\"", downloadFileName));
        // 创建文件过滤器
        FileFilter fileFilter = file -> isPathAllowed(projectDir.toPath(), file.toPath());
        // 文件压缩为zip
        try {
            ZipUtil.zip(response.getOutputStream(), StandardCharsets.UTF_8 , false, fileFilter, projectDir);
            log.info("项目打包完成：{} -> {}.zip", projectPath, downloadFileName);
        } catch (Exception e) {
            log.error("项目打包失败：{} -> {}.zip", projectPath, downloadFileName);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "项目打包下载失败");
        }
    }

    /**
     * 判断路径是否允许下载
     *
     * @param projectRoot 项目根目录
     * @param fullPath 完整路径
     * @return
     */
    private boolean isPathAllowed(Path projectRoot, Path fullPath) {
        // 获取相对路径
        Path relativePath = projectRoot.relativize(fullPath);
        for (Path part : relativePath) {
            String pathName = part.toString();
            // 判断是否在要忽略的文件中
            if (IGNORED_NAMES.contains(pathName)) {
                return false;
            }
            // 判断是否在要过滤的文件扩展名中，有一个就返回true
            if (IGNORED_EXTENSIONS.stream().anyMatch(pathName::endsWith)){
                return false;
            }
        }
        return true;
    }
}
