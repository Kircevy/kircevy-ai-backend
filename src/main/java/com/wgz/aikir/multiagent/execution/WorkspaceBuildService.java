package com.wgz.aikir.multiagent.execution;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 仅允许在隔离工作区执行固定构建命令，模型不拥有 Shell 权限。 */
@Component
public class WorkspaceBuildService {

    private static final int OUTPUT_LIMIT = 4_000;

    public BuildResult buildFrontend(Path frontendRoot) {
        if (!Files.isRegularFile(frontendRoot.resolve("package.json"))) {
            return BuildResult.failed("前端缺少 package.json");
        }
        BuildResult install = execute(frontendRoot, List.of(command("npm"), "install", "--ignore-scripts"), 300);
        if (!install.success()) {
            return install;
        }
        return execute(frontendRoot, List.of(command("npm"), "run", "build"), 180);
    }

    public BuildResult compileBackend(Path backendRoot) {
        if (!Files.isRegularFile(backendRoot.resolve("pom.xml"))) {
            return BuildResult.failed("后端缺少 pom.xml");
        }
        return execute(backendRoot, List.of(command("mvn"), "-q", "-DskipTests", "compile"), 300);
    }

    private BuildResult execute(Path workingDirectory, List<String> command, int timeoutSeconds) {
        Process process;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true);
            new BuildJdkEnvironment(Path.of(System.getProperty("java.home")))
                    .apply(processBuilder.environment());
            process = processBuilder.start();
        } catch (IOException exception) {
            return BuildResult.failed("无法启动构建命令：" + exception.getMessage());
        }
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return BuildResult.failed("构建超时（" + timeoutSeconds + " 秒）");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String summary = output.length() <= OUTPUT_LIMIT ? output : output.substring(output.length() - OUTPUT_LIMIT);
            return process.exitValue() == 0 ? BuildResult.succeeded(summary) : BuildResult.failed(summary);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return BuildResult.failed("构建被中断");
        } catch (IOException exception) {
            return BuildResult.failed("读取构建结果失败：" + exception.getMessage());
        }
    }

    private String command(String command) {
        return System.getProperty("os.name").toLowerCase().contains("windows") ? command + ".cmd" : command;
    }

    public record BuildResult(boolean success, String summary) {
        static BuildResult succeeded(String summary) {
            return new BuildResult(true, summary);
        }

        static BuildResult failed(String summary) {
            return new BuildResult(false, summary);
        }
    }
}
