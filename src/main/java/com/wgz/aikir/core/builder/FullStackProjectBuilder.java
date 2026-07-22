package com.wgz.aikir.core.builder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 全栈项目构建器
 * 在 AI 生成完所有文件后，执行结构校验、后端编译、前端构建
 */
@Slf4j
@Component
public class FullStackProjectBuilder {

    /**
     * 构建全栈项目（AI 生成完成后的后处理）
     *
     * @param projectPath 项目根目录路径（如 fullstack_123）
     * @return 是否构建成功
     */
    public synchronized boolean buildProject(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("全栈项目目录不存在: {}", projectPath);
            return false;
        }

        // 1. 校验项目结构
        if (!validateProjectStructure(projectDir)) {
            log.warn("全栈项目结构不完整，但不会阻断流程");
        }

        // 2. 构建后端（Maven 编译验证）
        File backendDir = new File(projectDir, "backend");
        if (backendDir.exists() && backendDir.isDirectory()) {
            File pomXml = new File(backendDir, "pom.xml");
            if (pomXml.exists()) {
                if (!executeMavenCompile(backendDir)) {
                    log.error("后端 Maven 编译失败，路径: {}", backendDir.getAbsolutePath());
                    return false;
                }
                log.info("全栈项目后端编译通过，路径: {}", backendDir.getAbsolutePath());
            } else {
                log.warn("后端 pom.xml 不存在，跳过 Maven 编译: {}", backendDir.getAbsolutePath());
            }
        } else {
            log.warn("后端目录不存在，跳过后端构建");
        }

        // 3. 构建前端（npm install + npm run build）
        File frontendDir = new File(projectDir, "frontend");
        if (frontendDir.exists() && frontendDir.isDirectory()) {
            if (!executeNpmInstall(frontendDir)) {
                log.error("前端 npm install 执行失败，路径: {}", frontendDir.getAbsolutePath());
                return false;
            }
            log.info("全栈项目前端依赖安装完成，路径: {}", frontendDir.getAbsolutePath());

            if (!executeNpmBuild(frontendDir)) {
                log.error("前端 npm run build 执行失败，路径: {}", frontendDir.getAbsolutePath());
                return false;
            }

            // 验证 dist 目录是否生成
            File distDir = new File(frontendDir, "dist");
            if (!distDir.exists() || !distDir.isDirectory()) {
                log.error("前端构建完成但 dist 目录未生成: {}", distDir.getAbsolutePath());
                return false;
            }
            log.info("全栈项目前端构建成功，dist 目录: {}", distDir.getAbsolutePath());
        } else {
            log.warn("前端目录不存在，跳过前端构建: {}", frontendDir.getAbsolutePath());
        }

        return true;
    }

    /**
     * 校验全栈项目目录结构
     */
    public synchronized boolean buildFrontendPreview(String projectPath) {
        File frontendDir = new File(projectPath, "frontend");
        File packageJson = new File(frontendDir, "package.json");
        File indexHtml = new File(frontendDir, "index.html");
        File mainJs = new File(frontendDir, "src/main.js");
        File mainTs = new File(frontendDir, "src/main.ts");

        if (!packageJson.isFile() || !indexHtml.isFile() || (!mainJs.isFile() && !mainTs.isFile())) {
            log.debug("Frontend preview build deferred because required entry files are not ready: {}", frontendDir);
            return false;
        }
        if (!executeNpmInstall(frontendDir) || !executeNpmBuild(frontendDir)) {
            log.warn("Frontend preview build failed: {}", frontendDir);
            return false;
        }
        return new File(frontendDir, "dist/index.html").isFile();
    }

    private boolean validateProjectStructure(File projectDir) {
        boolean valid = true;

        // 检查 backend 目录
        File backendDir = new File(projectDir, "backend");
        if (!backendDir.exists()) {
            log.warn("后端目录不存在: {}", backendDir.getAbsolutePath());
            valid = false;
        } else {
            File pomXml = new File(backendDir, "pom.xml");
            if (!pomXml.exists()) {
                log.warn("后端 pom.xml 不存在: {}", pomXml.getAbsolutePath());
                valid = false;
            }
        }

        // 检查 frontend 目录
        File frontendDir = new File(projectDir, "frontend");
        if (!frontendDir.exists()) {
            log.warn("前端目录不存在: {}", frontendDir.getAbsolutePath());
            valid = false;
        } else {
            File packageJson = new File(frontendDir, "package.json");
            if (!packageJson.exists()) {
                log.warn("前端 package.json 不存在: {}", packageJson.getAbsolutePath());
                valid = false;
            }
        }

        // 检查 docker-compose.yml
        File dockerCompose = new File(projectDir, "docker-compose.yml");
        if (!dockerCompose.exists()) {
            log.warn("docker-compose.yml 不存在: {}", dockerCompose.getAbsolutePath());
            valid = false;
        }

        return valid;
    }

    /**
     * 执行 Maven 编译命令
     */
    private boolean executeMavenCompile(File backendDir) {
        log.info("在全栈项目后端目录执行 Maven 编译...");
        String mvnCmd = isWindows() ? "mvn.cmd" : "mvn";
        return executeCommand(backendDir, mvnCmd + " compile -q", 300);
    }

    /**
     * 执行 npm install 命令
     */
    private boolean executeNpmInstall(File frontendDir) {
        log.info("在全栈项目前端目录执行 npm install...");
        String command = String.format("%s install", buildCommand("npm"));
        return executeCommand(frontendDir, command, 300);
    }

    /**
     * 执行 npm run build 命令
     */
    private boolean executeNpmBuild(File frontendDir) {
        log.info("在全栈项目前端目录执行 npm run build...");
        String command = String.format("%s run build", buildCommand("npm"));
        return executeCommand(frontendDir, command, 180);
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private String buildCommand(String baseCommand) {
        if (isWindows()) {
            return baseCommand + ".cmd";
        }
        return baseCommand;
    }

    /**
     * 执行命令（捕获 stdout/stderr 用于调试）
     *
     * @param workingDir     工作目录
     * @param command        命令字符串
     * @param timeoutSeconds 超时时间（秒）
     * @return 是否执行成功
     */
    private boolean executeCommand(File workingDir, String command, int timeoutSeconds) {
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);
            Process process = startProcess(workingDir, command.split("\\s+"));
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止进程", timeoutSeconds);
                process.destroyForcibly();
                return false;
            }
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("命令执行成功: {}", command);
                return true;
            } else {
                // 捕获错误输出用于调试
                String errorOutput = new String(process.getErrorStream().readAllBytes());
                String stdOutput = new String(process.getInputStream().readAllBytes());
                log.error("命令执行失败（退出码: {}），命令: {}", exitCode, command);
                if (!errorOutput.isBlank()) {
                    log.error("stderr: {}", truncateForLog(errorOutput, 2000));
                }
                if (!stdOutput.isBlank()) {
                    log.error("stdout: {}", truncateForLog(stdOutput, 2000));
                }
                return false;
            }
        } catch (Exception e) {
            log.error("执行命令失败: {}, 错误信息: {}", command, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 让子进程使用运行当前服务的 JDK。
     * IDE 启动服务时，PATH 中的 mvn.cmd 可能仍关联到低版本 JDK，导致生成项目即使声明
     * Java 21 也会出现“ 不支持发行版本 21 ”。
     */
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

    /**
     * 截断过长输出，避免日志过大
     */
    private String truncateForLog(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(truncated " + (text.length() - maxLength) + " chars)";
    }
}
