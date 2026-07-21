package com.wgz.aikir.service;

import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Docker Compose 部署服务
 * 负责检测 Docker 环境、执行 docker-compose up -d、返回服务访问地址
 */
@Slf4j
@Service
public class DockerComposeDeployService {

    /**
     * Docker 部署结果
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    @lombok.Builder
    public static class DockerDeployResult {
        /** 是否部署成功 */
        private boolean success;
        /** 前端访问地址（nginx 暴露 80 端口） */
        private String frontendUrl;
        /** 后端 API 地址（Spring Boot 暴露 8080 端口） */
        private String backendUrl;
        /** 失败时的错误信息 */
        private String errorMessage;
    }

    /**
     * 默认前端端口（与 fullstack prompt 中 docker-compose.yml 约定一致）
     */
    private static final int FRONTEND_PORT = 80;
    private static final int BACKEND_PORT = 8080;

    /**
     * 部署全栈项目到 Docker
     *
     * @param projectPath 项目根目录路径（包含 docker-compose.yml）
     * @param deployKey   部署标识，用于区分不同实例（端口映射偏移量）
     * @return 部署结果
     */
    public DockerDeployResult deploy(String projectPath, String deployKey) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            return DockerDeployResult.builder()
                    .success(false)
                    .errorMessage("项目目录不存在: " + projectPath)
                    .build();
        }

        File dockerComposeFile = new File(projectDir, "docker-compose.yml");
        if (!dockerComposeFile.exists()) {
            return DockerDeployResult.builder()
                    .success(false)
                    .errorMessage("docker-compose.yml 不存在，无法执行 Docker 部署")
                    .build();
        }

        // 1. 检测 Docker 环境
        if (!checkDockerAvailable()) {
            return DockerDeployResult.builder()
                    .success(false)
                    .errorMessage("宿主机未安装 Docker 或 Docker 服务未启动，请选择「代码下载」模式获取源码后本地运行")
                    .build();
        }

        // 2. 先清理同项目名的旧实例（避免端口占用）
        String projectName = sanitizeProjectName(deployKey);
        String downCmd = String.format("%s -p %s down --remove-orphans", buildComposeBinary(), projectName);
        log.info("清理旧实例: {}", downCmd);
        executeCommand(projectDir, downCmd, 120);

        // 3. 执行 docker-compose up -d（构建并后台启动）
        // 使用 -p 指定项目名隔离不同应用实例
        String upCmd = String.format("%s -p %s up -d --build", buildComposeBinary(), projectName);
        log.info("执行 Docker Compose 部署，项目: {}，命令: {}", deployKey, upCmd);
        boolean upSuccess = executeCommand(projectDir, upCmd, 600);
        if (!upSuccess) {
            return DockerDeployResult.builder()
                    .success(false)
                    .errorMessage("docker-compose up 执行失败，请检查生成的 Dockerfile 和 docker-compose.yml 是否正确")
                    .build();
        }

        // 4. 等待后端服务就绪（简单等待，不阻塞太久）
        waitForServiceReady(BACKEND_PORT, 30);

        String host = getHostAddress();
        String frontendUrl = String.format("http://%s:%d", host, FRONTEND_PORT);
        String backendUrl = String.format("http://%s:%d/api", host, BACKEND_PORT);

        log.info("Docker 部署成功，前端: {}，后端: {}", frontendUrl, backendUrl);
        return DockerDeployResult.builder()
                .success(true)
                .frontendUrl(frontendUrl)
                .backendUrl(backendUrl)
                .build();
    }

    /**
     * 停止并清理 Docker 部署的容器
     *
     * @param projectPath 项目根目录路径
     * @param deployKey   部署标识
     * @return 是否成功
     */
    public boolean stop(String projectPath, String deployKey) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists()) {
            return false;
        }
        String composeCmd = String.format("%s -p %s down --remove-orphans",
                buildComposeBinary(), sanitizeProjectName(deployKey));
        return executeCommand(projectDir, composeCmd, 120);
    }

    /**
     * 检测 Docker 环境是否可用
     */
    private boolean checkDockerAvailable() {
        try {
            String dockerCmd = isWindows() ? "docker.exe version" : "docker version";
            Process process = RuntimeUtil.exec(dockerCmd.split("\\s+"));
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            boolean available = process.exitValue() == 0;
            if (!available) {
                log.warn("Docker 不可用，退出码: {}", process.exitValue());
            }
            return available;
        } catch (Exception e) {
            log.warn("检测 Docker 环境失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取 docker-compose 二进制命令（兼容 v1/v2）
     */
    private String buildComposeBinary() {
        // docker compose（v2）优先，回退 docker-compose（v1）
        // 这里统一用 docker compose，现代 Docker Desktop 均支持
        return isWindows() ? "docker.exe compose" : "docker compose";
    }

    /**
     * 清理项目名（docker compose -p 只允许 [a-z0-9_-]）
     */
    private String sanitizeProjectName(String deployKey) {
        if (StrUtil.isBlank(deployKey)) {
            return "kircevy-app";
        }
        return "app-" + deployKey.toLowerCase().replaceAll("[^a-z0-9-]", "");
    }

    /**
     * 等待后端服务就绪（简单轮询 health 端点）
     */
    private void waitForServiceReady(int backendPort, int maxWaitSeconds) {
        String host = "127.0.0.1";
        String healthUrl = String.format("http://%s:%d/api/actuator/health", host, backendPort);
        for (int i = 0; i < maxWaitSeconds; i++) {
            try {
                Process process = RuntimeUtil.exec(
                        isWindows() ? "curl.exe -s -o /dev/null -w \"%{http_code}\" " + healthUrl
                                : "curl -s -o /dev/null -w \"%{http_code}\" " + healthUrl);
                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                if (finished && process.exitValue() == 0) {
                    String output = new String(process.getInputStream().readAllBytes());
                    if (output.contains("200") || output.contains("UP")) {
                        log.info("后端服务已就绪，等待 {} 秒", i);
                        return;
                    }
                }
            } catch (Exception e) {
                // 忽略，继续等待
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("后端服务在 {} 秒内未就绪，可能仍在启动中", maxWaitSeconds);
    }

    /**
     * 获取宿主机可访问的 IP 地址
     * 优先返回局域网 IP，便于远程访问；回退 localhost
     */
    private String getHostAddress() {
        try {
            java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
            String hostAddress = localHost.getHostAddress();
            if (StrUtil.isNotBlank(hostAddress) && !"127.0.0.1".equals(hostAddress)) {
                return hostAddress;
            }
        } catch (Exception e) {
            log.warn("获取主机地址失败: {}", e.getMessage());
        }
        return "localhost";
    }

    /**
     * 执行命令（参考 FullStackProjectBuilder 的实现风格）
     */
    private boolean executeCommand(File workingDir, String command, int timeoutSeconds) {
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);
            Process process = RuntimeUtil.exec(null, workingDir, command.split("\\s+"));
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

    private String truncateForLog(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(truncated " + (text.length() - maxLength) + " chars)";
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }
}
