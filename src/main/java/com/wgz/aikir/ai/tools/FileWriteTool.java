package com.wgz.aikir.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import com.wgz.aikir.constant.AppConstant;
import com.wgz.aikir.service.FrontendPreviewBuildService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 文件写入工具
 * 支持 AI 通过工具调用的方式写入文件
 */
@Slf4j
@Component
public class FileWriteTool extends BaseTool {

    @Resource
    private FrontendPreviewBuildService frontendPreviewBuildService;
    @Tool("写入文件到指定目录")
    public String writeFile(
            @P("文件的相对路径") String relativeFilePath,
            @P("要写入文件的内容") String content,
            @ToolMemoryId Long appId
    ) {
        try {
            Path path = Paths.get(relativeFilePath);
            if (!path.isAbsolute()){
                // 动态获取项目类型前缀，支持多种项目类型（vue_project / springboot / fullstack 等）
                String projectDirName = getProjectDirName(appId);
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                path = projectRoot.resolve(relativeFilePath);
            }
            // 创建父目录 （如果不存在）
            String validationError = validateJavaSource(path, content);
            if (validationError != null) {
                log.warn("拒绝写入非法 Java 源码: {}, 原因: {}", path, validationError);
                return "文件未写入：" + validationError;
            }
            Path parentDir = path.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            // 写入文件内容
            Files.write(path, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功写入文件: {}", path.toAbsolutePath());
            scheduleFrontendPreviewBuild(path, appId);
            // 注意要返回相对路径， 不能让 AI 把文件绝对路径返回给用户
            return "文件写入成功：" + relativeFilePath;
        }catch (IOException e){
            String errorMessage = "文件写入失败：" + relativeFilePath + ", 错误:  " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }

    }

    private String validateJavaSource(Path path, String content) {
        if (!path.toString().endsWith(".java")) {
            return null;
        }
        for (String line : content.split("\\R")) {
            String trimmedLine = line.trim();
            if (trimmedLine.startsWith("import ") && trimmedLine.contains("-")) {
                return "Java import 不能包含连字符，请使用合法包名（例如 com.mybatisflex，而不是 com.mybatis-flex）";
            }
        }
        return null;
    }

    private void scheduleFrontendPreviewBuild(Path path, Long appId) {
        String projectDirName = getProjectDirName(appId);
        Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName)
                .toAbsolutePath()
                .normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (projectDirName.startsWith("fullstack_")
                && normalizedPath.startsWith(projectRoot.resolve("frontend"))) {
            frontendPreviewBuildService.scheduleBuild(appId);
        } else if (projectDirName.startsWith("vue_project_")
                && normalizedPath.startsWith(projectRoot)) {
            frontendPreviewBuildService.scheduleVueBuild(appId);
        }
    }

    @Override
    public String getToolName() {
        return "writeFile";
    }

    @Override
    public String getDisplayName() {
        return "写入文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String suffix = FileUtil.getSuffix(relativeFilePath);
        String content = arguments.getStr("content");
        return String.format("""
                        [工具调用] 写入文件 %s
                        ```%s
                        %s
                        ```
                        """, relativeFilePath, suffix, content);
    }
}
