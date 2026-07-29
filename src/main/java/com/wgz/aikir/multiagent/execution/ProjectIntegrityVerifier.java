package com.wgz.aikir.multiagent.execution;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 在构建前验证生成项目的本地配置与源码依赖闭环。 */
@Component
public class ProjectIntegrityVerifier {

    private static final Pattern TSCONFIG_REFERENCES_PATTERN = Pattern.compile(
            "\\\"references\\\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern REFERENCE_PATH_PATTERN = Pattern.compile("\\\"path\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern PROJECT_IMPORT_PATTERN = Pattern.compile(
            "(?m)^\\s*import\\s+(com\\.example\\.[A-Za-z0-9_.]+);");

    public VerificationResult verifyFrontend(Path frontendRoot) {
        List<String> failures = new ArrayList<>();
        requireFile(frontendRoot, "package.json", failures);
        requireFile(frontendRoot, "tsconfig.json", failures);
        requireFile(frontendRoot, "src/main.ts", failures);
        Path tsconfig = frontendRoot.resolve("tsconfig.json");
        if (Files.isRegularFile(tsconfig)) {
            try {
                Matcher references = TSCONFIG_REFERENCES_PATTERN.matcher(Files.readString(tsconfig));
                if (references.find()) {
                    Matcher paths = REFERENCE_PATH_PATTERN.matcher(references.group(1));
                    while (paths.find()) {
                        requireReferencedFile(frontendRoot, paths.group(1), failures);
                    }
                }
            } catch (IOException exception) {
                failures.add("无法读取 tsconfig.json");
            }
        }
        return result(failures);
    }

    public VerificationResult verifyBackend(Path backendRoot) {
        List<String> failures = new ArrayList<>();
        requireFile(backendRoot, "pom.xml", failures);
        Path sourceRoot = backendRoot.resolve("src/main/java");
        if (!Files.isDirectory(sourceRoot)) {
            failures.add("缺少 src/main/java");
            return result(failures);
        }
        boolean usesLombok = false;
        try (var sources = Files.walk(sourceRoot)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String content = Files.readString(source);
                usesLombok |= content.contains("import lombok.");
                Matcher imports = PROJECT_IMPORT_PATTERN.matcher(content);
                while (imports.find()) {
                    String importedClass = imports.group(1);
                    Path importedSource = sourceRoot.resolve(importedClass.replace('.', '/') + ".java");
                    if (!Files.isRegularFile(importedSource)) {
                        failures.add("缺少项目导入：" + importedClass);
                    }
                }
            }
        } catch (IOException exception) {
            failures.add("无法读取后端源码");
        }
        if (usesLombok && !pomDeclaresLombok(backendRoot.resolve("pom.xml"))) {
            failures.add("代码使用 Lombok 但 pom.xml 未声明 Lombok");
        }
        return result(failures);
    }

    private void requireFile(Path root, String relativePath, List<String> failures) {
        if (!Files.isRegularFile(root.resolve(relativePath))) {
            failures.add("缺少 " + relativePath);
        }
    }

    private void requireReferencedFile(Path root, String reference, List<String> failures) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path target = normalizedRoot.resolve(reference).normalize();
        if (!target.startsWith(normalizedRoot) || !Files.isRegularFile(target)) {
            failures.add("tsconfig 引用了不存在的文件：" + reference);
        }
    }

    private boolean pomDeclaresLombok(Path pom) {
        try {
            return Files.isRegularFile(pom) && Files.readString(pom).contains("<artifactId>lombok</artifactId>");
        } catch (IOException exception) {
            return false;
        }
    }

    private VerificationResult result(List<String> failures) {
        return failures.isEmpty() ? VerificationResult.succeeded() : VerificationResult.failed(String.join("；", failures));
    }

    public record VerificationResult(boolean success, String summary) {
        static VerificationResult succeeded() {
            return new VerificationResult(true, "完整性校验通过");
        }

        static VerificationResult failed(String summary) {
            return new VerificationResult(false, summary);
        }
    }
}
