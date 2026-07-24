package com.wgz.aikir.service;

import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

/**
 * Docker Compose 部署服务
 * 负责检测 Docker 环境、执行 docker-compose up -d、返回服务访问地址
 */
@Slf4j
@Service
public class DockerComposeDeployService {

    private static final int COMMAND_OUTPUT_LIMIT = 6000;

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
     * Docker Compose 项目的实时运行信息。
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    @lombok.Builder
    public static class DockerRuntimeInfo {
        private String status;
        private String statusMessage;
        private String frontendUrl;
        private String backendUrl;
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
        DeployPorts deployPorts = resolveDeployPorts(projectName);
        String composePreparationError = prepareDockerComposePorts(dockerComposeFile, deployPorts);
        if (composePreparationError != null) {
            return DockerDeployResult.builder()
                    .success(false)
                    .errorMessage(composePreparationError)
                    .build();
        }
        String sqlPreparationError = prepareMysqlInitializationSql(projectDir);
        if (sqlPreparationError != null) {
            return DockerDeployResult.builder()
                    .success(false)
                    .errorMessage(sqlPreparationError)
                    .build();
        }
        String downCmd = String.format("%s -p %s down --remove-orphans", buildComposeBinary(), projectName);
        log.info("清理旧实例: {}", downCmd);
        executeCommand(projectDir, downCmd, 120);

        // 3. 执行 docker-compose up -d（构建并后台启动）
        // 使用 -p 指定项目名隔离不同应用实例
        String upCmd = String.format("%s -p %s up -d --build", buildComposeBinary(), projectName);
        log.info("执行 Docker Compose 部署，项目: {}，命令: {}", deployKey, upCmd);
        CommandResult upResult = executeCommandWithOutput(projectDir, upCmd, 600);
        if (!upResult.success()) {
            return DockerDeployResult.builder()
                    .success(false)
                    .errorMessage("Docker Compose 构建或启动失败。请检查生成的 Dockerfile、docker-compose.yml 与端口占用情况。\n"
                            + summarizeOutput(upResult.output()))
                    .build();
        }

        // 4. 等待后端服务就绪（简单等待，不阻塞太久）
        if (!waitForServiceReady(deployPorts.backendPort(), 90)) {
            String statusCommand = String.format("%s -p %s ps -a", buildComposeBinary(), projectName);
            CommandResult statusResult = executeCommandWithOutput(projectDir, statusCommand, 30);
            String logCommand = String.format("%s -p %s logs --tail 80", buildComposeBinary(), projectName);
            CommandResult logsResult = executeCommandWithOutput(projectDir, logCommand, 30);
            return DockerDeployResult.builder()
                    .success(false)
                    .errorMessage("容器已尝试启动，但后端健康检查在 90 秒内未通过。"
                            + "请检查数据库初始化、后端启动日志和 3306/8080/80 端口是否被占用。\n"
                            + "容器状态：\n" + summarizeOutput(statusResult.output())
                            + "\n最近日志：\n" + summarizeOutput(logsResult.output()))
                    .build();
        }

        String host = getHostAddress();
        String frontendUrl = String.format("http://%s:%d", host, deployPorts.frontendPort());
        String backendUrl = String.format("http://%s:%d/api", host, deployPorts.backendPort());

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
     * 停止容器但不删除容器和数据卷，供用户稍后再次启动。
     */
    public boolean stopKeepingContainers(String projectPath, String deployKey) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !checkDockerAvailable()) {
            return false;
        }
        String composeCmd = String.format("%s -p %s stop", buildComposeBinary(), sanitizeProjectName(deployKey));
        return executeCommand(projectDir, composeCmd, 120);
    }

    /**
     * 启动保留的 Compose 容器，不重新构建镜像。
     */
    public boolean start(String projectPath, String deployKey) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !checkDockerAvailable()) {
            return false;
        }
        String composeCmd = String.format("%s -p %s start", buildComposeBinary(), sanitizeProjectName(deployKey));
        return executeCommand(projectDir, composeCmd, 120);
    }

    /**
     * 查询 Compose 服务状态与实际宿主机映射端口。
     */
    public DockerRuntimeInfo getRuntimeInfo(String projectPath, String deployKey) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists()) {
            return DockerRuntimeInfo.builder().status("NOT_FOUND").statusMessage("生成目录不存在").build();
        }
        if (!checkDockerAvailable()) {
            return DockerRuntimeInfo.builder().status("UNKNOWN").statusMessage("Docker Desktop 未启动").build();
        }
        String projectName = sanitizeProjectName(deployKey);
        String servicesCommand = String.format("%s -p %s ps --status running --services", buildComposeBinary(), projectName);
        CommandResult servicesResult = executeCommandWithOutput(projectDir, servicesCommand, 30);
        if (!servicesResult.success()) {
            return DockerRuntimeInfo.builder().status("NOT_FOUND").statusMessage("未找到已部署容器").build();
        }
        String services = servicesResult.output();
        boolean frontendRunning = services.lines().anyMatch("frontend"::equals);
        boolean backendRunning = services.lines().anyMatch("backend"::equals);
        if (!frontendRunning && !backendRunning) {
            return DockerRuntimeInfo.builder().status("STOPPED").statusMessage("容器已停止").build();
        }
        String frontendUrl = frontendRunning ? resolveServiceUrl(projectDir, projectName, "frontend", 80, false) : null;
        String backendUrl = backendRunning ? resolveServiceUrl(projectDir, projectName, "backend", 8080, true) : null;
        String status = frontendRunning && backendRunning ? "RUNNING" : "PARTIAL";
        String statusMessage = "RUNNING".equals(status) ? "前后端容器运行中" : "部分容器正在运行，请检查部署日志";
        return DockerRuntimeInfo.builder()
                .status(status)
                .statusMessage(statusMessage)
                .frontendUrl(frontendUrl)
                .backendUrl(backendUrl)
                .build();
    }

    private String resolveServiceUrl(File projectDir, String projectName, String service, int containerPort, boolean appendApi) {
        String command = String.format("%s -p %s port %s %d", buildComposeBinary(), projectName, service, containerPort);
        CommandResult result = executeCommandWithOutput(projectDir, command, 30);
        if (!result.success() || StrUtil.isBlank(result.output())) {
            return null;
        }
        String address = result.output().lines().findFirst().orElse("").trim();
        int separatorIndex = address.lastIndexOf(':');
        if (separatorIndex < 0) {
            return null;
        }
        String host = address.substring(0, separatorIndex);
        String port = address.substring(separatorIndex + 1);
        if ("0.0.0.0".equals(host) || "[::]".equals(host)) {
            host = "localhost";
        }
        return "http://" + host + ":" + port + (appendApi ? "/api" : "");
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
        String projectName = deployKey.toLowerCase().replaceAll("[^a-z0-9_-]", "");
        return StrUtil.isBlank(projectName) ? "kircevy_app" : projectName;
    }

    /**
     * 对生成项目中最常见的 MySQL 保留字字段 key 进行兼容处理，保持 Java 实体字段名不变。
     */
    private String prepareMysqlInitializationSql(File projectDir) {
        Path initSqlPath = projectDir.toPath().resolve("backend/sql/init.sql");
        if (!Files.exists(initSqlPath)) {
            return null;
        }
        try {
            String sql = Files.readString(initSqlPath, StandardCharsets.UTF_8);
            String normalizedSql = sql
                    .replaceAll("(?i)(?<![A-Za-z0-9_`])key(?=\\s+(VARCHAR|CHAR|TEXT|INT|BIGINT|DOUBLE|DECIMAL|DATE|DATETIME|BOOLEAN))", "`key`")
                    .replaceAll("(?i)(,\\s*)key(?=\\s*,)", "$1`key`")
                    .replaceAll("(?i)\\(\\s*key\\s*\\)", "(`key`)");
            if (!normalizedSql.equals(sql)) {
                Files.writeString(initSqlPath, normalizedSql, StandardCharsets.UTF_8);
                log.warn("已自动修复初始化 SQL 中未转义的 MySQL 保留字 key: {}", initSqlPath);
            }
            return null;
        } catch (Exception exception) {
            log.error("准备初始化 SQL 失败: {}", initSqlPath, exception);
            return "无法读取或修复 backend/sql/init.sql，部署已取消：" + exception.getMessage();
        }
    }

    /**
     * 等待后端服务就绪（简单轮询 health 端点）
     */
    /**
     * 为每个 Compose 项目分配稳定且互不干扰的宿主机端口，避免多应用部署时抢占 80、8080、3306。
     */
    private DeployPorts resolveDeployPorts(String projectName) {
        int offset = Math.floorMod(projectName.hashCode(), 10000);
        return new DeployPorts(40000 + offset, 30000 + offset, 20000 + offset);
    }

    /**
     * 将 AI 生成模板中的固定端口映射替换为当前项目的专属端口，Docker Desktop 将显示可点击的前端端口。
     */
    private String prepareDockerComposePorts(File dockerComposeFile, DeployPorts deployPorts) {
        try {
            String compose = Files.readString(dockerComposeFile.toPath(), StandardCharsets.UTF_8);
            String normalizedCompose = compose
                    .replaceAll("(?m)(-\\s*\")\\d+:3306(\")", "$1" + deployPorts.mysqlPort() + ":3306$2")
                    .replaceAll("(?m)(-\\s*\")\\d+:8080(\")", "$1" + deployPorts.backendPort() + ":8080$2")
                    .replaceAll("(?m)(-\\s*\")\\d+:80(\")", "$1" + deployPorts.frontendPort() + ":80$2");
            if (!normalizedCompose.equals(compose)) {
                Files.writeString(dockerComposeFile.toPath(), normalizedCompose, StandardCharsets.UTF_8);
                log.info("已为 Compose 项目分配端口，前端: {}，后端: {}，MySQL: {}",
                        deployPorts.frontendPort(), deployPorts.backendPort(), deployPorts.mysqlPort());
            }
            return null;
        } catch (Exception exception) {
            log.error("准备 Docker Compose 端口映射失败: {}", dockerComposeFile, exception);
            return "无法准备 Docker Compose 的专属端口映射，部署已取消：" + exception.getMessage();
        }
    }

    private boolean waitForServiceReadyLegacy(int backendPort, int maxWaitSeconds) {
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
                        return true;
                    }
                }
            } catch (Exception e) {
                // 忽略，继续等待
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        log.warn("后端服务在 {} 秒内未就绪，可能仍在启动中", maxWaitSeconds);
        return false;
    }

    /**
     * 获取宿主机可访问的 IP 地址
     * 优先返回局域网 IP，便于远程访问；回退 localhost
     */
    /**
     * 生成项目未必暴露 Actuator，因此通过宿主机端口可连接性判断后端是否已完成启动。
     */
    private boolean waitForServiceReady(int backendPort, int maxWaitSeconds) {
        for (int i = 0; i < maxWaitSeconds; i++) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", backendPort), 1000);
                log.info("后端服务端口已就绪，等待 {} 秒", i);
                return true;
            } catch (Exception ignored) {
                // 服务尚未监听端口，继续等待。
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        log.warn("后端服务端口在 {} 秒内未就绪", maxWaitSeconds);
        return false;
    }

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
    private boolean executeCommandLegacy(File workingDir, String command, int timeoutSeconds) {
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

    private boolean executeCommand(File workingDir, String command, int timeoutSeconds) {
        return executeCommandWithOutput(workingDir, command, timeoutSeconds).success();
    }

    /**
     * 持续读取 Docker 子进程输出，避免 Maven、npm 或 BuildKit 输出填满管道后造成部署进程假死。
     */
    private CommandResult executeCommandWithOutput(File workingDir, String command, int timeoutSeconds) {
        StringBuilder output = new StringBuilder();
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);
            Process process = new ProcessBuilder(command.split("\\s+"))
                    .directory(workingDir)
                    .redirectErrorStream(true)
                    .start();
            Thread outputReader = Thread.startVirtualThread(() -> readCommandOutput(process, output));
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{} 秒），强制终止进程", timeoutSeconds);
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
                outputReader.join(5000);
                return new CommandResult(false, output.toString());
            }
            outputReader.join(5000);
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("命令执行成功: {}", command);
                return new CommandResult(true, output.toString());
            }
            log.error("命令执行失败（退出码: {}），命令: {}", exitCode, command);
            if (!output.isEmpty()) {
                log.error("命令输出: {}", summarizeOutput(output.toString()));
            }
            return new CommandResult(false, output.toString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("命令执行被中断: {}", command);
            return new CommandResult(false, output.toString());
        } catch (Exception exception) {
            log.error("执行命令失败: {}，错误信息: {}", command, exception.getMessage(), exception);
            return new CommandResult(false, output.toString());
        }
    }

    private void readCommandOutput(Process process, StringBuilder output) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendOutput(output, line);
                log.info("Docker Compose | {}", line);
            }
        } catch (Exception exception) {
            log.warn("读取 Docker Compose 输出失败: {}", exception.getMessage());
        }
    }

    private void appendOutput(StringBuilder output, String line) {
        output.append(line).append(System.lineSeparator());
        int excessLength = output.length() - COMMAND_OUTPUT_LIMIT;
        if (excessLength > 0) {
            output.delete(0, excessLength);
        }
    }

    private String summarizeOutput(String output) {
        return StrUtil.isBlank(output) ? "未获取到 Docker 命令输出。" : output;
    }

    private record CommandResult(boolean success, String output) {
    }

    private record DeployPorts(int mysqlPort, int backendPort, int frontendPort) {
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
