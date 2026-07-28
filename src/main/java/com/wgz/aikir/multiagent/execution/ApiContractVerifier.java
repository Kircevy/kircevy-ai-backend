package com.wgz.aikir.multiagent.execution;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 在产物提升前，对 OpenAPI 路径和字段执行确定性存在性检查。 */
@Component
public class ApiContractVerifier {

    public VerificationResult verify(String apiContractYaml, Path frontendRoot, Path backendRoot) {
        Object loaded = new Yaml().load(apiContractYaml);
        if (!(loaded instanceof Map<?, ?> root) || !(root.get("paths") instanceof Map<?, ?> paths) || paths.isEmpty()) {
            return VerificationResult.failed("API 契约缺少 paths");
        }
        String frontend = readSources(frontendRoot);
        String backend = readSources(backendRoot);
        for (Object rawPath : paths.keySet()) {
            String path = String.valueOf(rawPath);
            if (!backend.contains(path)) {
                return VerificationResult.failed("后端未实现契约路径：" + path);
            }
            if (!frontend.contains(path)) {
                return VerificationResult.failed("前端未使用契约路径：" + path);
            }
        }
        for (String property : collectSchemaProperties(root)) {
            if (!frontend.contains(property) || !backend.contains(property)) {
                return VerificationResult.failed("接口字段在前后端产物中不一致：" + property);
            }
        }
        return VerificationResult.succeeded("API 路径已在前后端产物中匹配");
    }

    private String readSources(Path root) {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".ts")
                            || path.toString().endsWith(".vue") || path.toString().endsWith(".js"))
                    .map(this::readQuietly)
                    .reduce("", String::concat);
        } catch (IOException exception) {
            return "";
        }
    }

    private String readQuietly(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ignored) {
            return "";
        }
    }

    private Set<String> collectSchemaProperties(Object node) {
        Set<String> properties = new LinkedHashSet<>();
        collectSchemaProperties(node, properties);
        properties.removeAll(Set.of("code", "message", "data"));
        return properties;
    }

    @SuppressWarnings("unchecked")
    private void collectSchemaProperties(Object node, Set<String> properties) {
        if (node instanceof Map<?, ?> map) {
            Object rawProperties = map.get("properties");
            if (rawProperties instanceof Map<?, ?> schemaProperties) {
                for (Object name : schemaProperties.keySet()) {
                    properties.add(String.valueOf(name));
                }
            }
            map.values().forEach(value -> collectSchemaProperties(value, properties));
        } else if (node instanceof List<?> list) {
            list.forEach(value -> collectSchemaProperties(value, properties));
        }
    }

    public record VerificationResult(boolean success, String summary) {
        static VerificationResult succeeded(String summary) {
            return new VerificationResult(true, summary);
        }

        static VerificationResult failed(String summary) {
            return new VerificationResult(false, summary);
        }
    }
}
