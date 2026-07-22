package com.wgz.aikir.ai.tools;

import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Maven 构建工具
 * 支持 AI 通过工具调用的方式执行 Maven 编译命令，验证后端代码是否正确
 */
@Slf4j
@Component
public class MavenTool extends BaseTool {

    @Tool("执行 Maven 编译命令，验证后端 Java 项目代码是否能通过编译")
    public String executeMaven(
            @ToolMemoryId Long appId,
            @P("Maven 命令（如 compile, test, package, clean install）") String command,
            @P("项目根目录的绝对路径") String projectPath
    ) {
        log.info("在目录 {} 中执行 Maven 命令: {}", projectPath, command);
        Path projectRoot = Paths.get(getProjectRootPath(appId)).toAbsolutePath().normalize();
        Path requestedPath = Paths.get(projectPath);
        if (requestedPath.isAbsolute()) {
            return "错误：项目路径必须相对于生成项目根目录，例如 backend；不要使用绝对路径 " + projectPath;
        }
        Path resolvedPath = projectRoot.resolve(requestedPath).normalize();
        if (!resolvedPath.startsWith(projectRoot)) {
            return "错误：项目路径不能位于生成项目目录之外 - " + projectPath;
        }
        File projectDir = resolvedPath.toFile();
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            return "错误：项目目录不存在 - " + projectPath;
        }
        // 检查 pom.xml 是否存在
        File pomFile = new File(projectDir, "pom.xml");
        if (!pomFile.exists()) {
            return "错误：项目目录中未找到 pom.xml 文件";
        }

        String mvnCmd = isWindows() ? "mvn.cmd" : "mvn";
        String fullCommand = mvnCmd + " " + command;

        try {
            log.info("执行命令: {}", fullCommand);
            Process process = startProcess(projectDir, (mvnCmd + " " + command).split("\\s+"));
            // 等待进程完成，设置 5 分钟超时
            boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "错误：Maven 命令执行超时（300秒）";
            }
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return "Maven " + command + " 执行成功！项目编译通过。";
            } else {
                // 读取错误输出
                String errorOutput = new String(process.getErrorStream().readAllBytes());
                String stdOutput = new String(process.getInputStream().readAllBytes());
                String errorSummary = extractErrorSummary(errorOutput, stdOutput);
                return "Maven 命令执行失败（退出码: " + exitCode + "）。\n错误摘要:\n" + errorSummary;
            }
        } catch (Exception e) {
            String errorMsg = "Maven 命令执行异常: " + e.getMessage();
            log.error(errorMsg, e);
            return "错误: " + errorMsg;
        }
    }

    /**
     * 从 Maven 输出中提取关键错误信息
     */
    private String extractErrorSummary(String errorOutput, String stdOutput) {
        StringBuilder summary = new StringBuilder();
        String combined = errorOutput + "\n" + stdOutput;
        String[] lines = combined.split("\n");
        boolean inError = false;
        int errorCount = 0;
        for (String line : lines) {
            if (line.contains("ERROR") || line.contains("error:")) {
                summary.append(line).append("\n");
                errorCount++;
                inError = true;
            } else if (inError && line.startsWith("\t") && errorCount <= 20) {
                summary.append(line).append("\n");
            } else {
                inError = false;
            }
        }
        if (summary.isEmpty()) {
            // 如果没找到明确的 ERROR 行，返回最后 20 行
            int start = Math.max(0, lines.length - 20);
            for (int i = start; i < lines.length; i++) {
                summary.append(lines[i]).append("\n");
            }
        }
        return summary.toString();
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private Process startProcess(File workingDir, String... command) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDir);

        Map<String, String> environment = processBuilder.environment();
        String javaHome = System.getProperty("java.home");
        environment.put("JAVA_HOME", javaHome);
        String pathKey = environment.containsKey("Path") ? "Path" : "PATH";
        String currentPath = environment.getOrDefault(pathKey, "");
        environment.put(pathKey, javaHome + File.separator + "bin" + File.pathSeparator + currentPath);

        return processBuilder.start();
    }

    @Override
    public String getToolName() {
        return "executeMaven";
    }

    @Override
    public String getDisplayName() {
        return "Maven 编译";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String command = arguments.getStr("command");
        String projectPath = arguments.getStr("projectPath");
        return String.format("[工具调用] Maven %s\n项目路径: %s", command, projectPath);
    }
}
