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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 在产物提升前，对 OpenAPI 路径和字段执行确定性存在性检查。 */
@Component
public class ApiContractVerifier {

    private static final Pattern MAPPING_PATTERN = Pattern.compile(
            "@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\\s*"
                    + "(?:\\(([^)]*)\\))?");
    private static final Pattern MAPPING_PATH_PATTERN = Pattern.compile(
            "(?:value|path)\\s*=\\s*\\\"([^\\\"]*)\\\"|^\\s*\\\"([^\\\"]*)\\\"");
    private static final Pattern REQUEST_METHOD_PATTERN = Pattern.compile("RequestMethod\\.([A-Z]+)");
    private static final Set<String> HTTP_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    public VerificationResult verify(String apiContractYaml, Path frontendRoot, Path backendRoot) {
        Object loaded = new Yaml().load(apiContractYaml);
        if (!(loaded instanceof Map<?, ?> root) || !(root.get("paths") instanceof Map<?, ?> paths) || paths.isEmpty()) {
            return VerificationResult.failed("API 契约缺少 paths");
        }
        String frontend = stripComments(readSources(frontendRoot));
        String backend = stripComments(readSources(backendRoot));
        String contextPath = readContextPath(backendRoot);
        Set<RouteMapping> backendMappings = extractMappings(backend);
        List<ContractOperation> contractOperations = extractContractOperations(paths);
        if (contractOperations.isEmpty()) {
            return VerificationResult.failed("API 契约未定义受支持的 HTTP 操作");
        }
        for (ContractOperation operation : contractOperations) {
            String path = operation.path();
            if (hasDuplicatedContextPrefix(path, contextPath, backendMappings)) {
                return VerificationResult.failed("后端接口前缀重复：" + path);
            }
            if (!containsBackendMapping(operation, contextPath, backendMappings)) {
                return VerificationResult.failed("后端未以 " + operation.method() + " 实现契约路径：" + path);
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

    private boolean hasDuplicatedContextPrefix(String contractPath, String contextPath, Set<RouteMapping> mappings) {
        return !contextPath.isEmpty()
                && (contractPath.equals(contextPath) || contractPath.startsWith(contextPath + "/"))
                && mappings.stream().map(RouteMapping::path).anyMatch(contextPath::equals);
    }

    private boolean containsBackendMapping(ContractOperation operation, String contextPath, Set<RouteMapping> mappings) {
        String contractPath = operation.path();
        String expectedPath;
        if (contextPath.isEmpty() || !contractPath.startsWith(contextPath)) {
            expectedPath = normalizePath(contractPath);
        } else {
            String relativePath = contractPath.substring(contextPath.length());
            expectedPath = normalizePath(relativePath);
        }
        return mappings.stream().anyMatch(mapping -> mapping.path().equals(expectedPath)
                && mapping.accepts(operation.method()));
    }

    private Set<RouteMapping> extractMappings(String backend) {
        Set<RouteMapping> mappings = new LinkedHashSet<>();
        Matcher matcher = MAPPING_PATTERN.matcher(backend);
        while (matcher.find()) {
            String annotation = matcher.group(1);
            String arguments = matcher.group(2);
            mappings.add(new RouteMapping(extractMappingPath(arguments), extractMappingMethods(annotation, arguments)));
        }
        return mappings;
    }

    private String extractMappingPath(String arguments) {
        if (arguments == null) {
            return "";
        }
        Matcher matcher = MAPPING_PATH_PATTERN.matcher(arguments);
        if (!matcher.find()) {
            return "";
        }
        String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return normalizePath(value);
    }

    private Set<String> extractMappingMethods(String annotation, String arguments) {
        if (!"RequestMapping".equals(annotation)) {
            return Set.of(annotation.replace("Mapping", "").toUpperCase());
        }
        if (arguments == null) {
            return Set.of();
        }
        Set<String> methods = new LinkedHashSet<>();
        Matcher matcher = REQUEST_METHOD_PATTERN.matcher(arguments);
        while (matcher.find()) {
            methods.add(matcher.group(1));
        }
        return methods;
    }

    private List<ContractOperation> extractContractOperations(Map<?, ?> paths) {
        List<ContractOperation> operations = new java.util.ArrayList<>();
        for (Map.Entry<?, ?> pathEntry : paths.entrySet()) {
            String path = String.valueOf(pathEntry.getKey());
            if (!(pathEntry.getValue() instanceof Map<?, ?> operationMap)) {
                continue;
            }
            for (Object rawMethod : operationMap.keySet()) {
                String method = String.valueOf(rawMethod).toUpperCase();
                if (HTTP_METHODS.contains(method)) {
                    operations.add(new ContractOperation(path, method));
                }
            }
        }
        return operations;
    }

    private String readContextPath(Path backendRoot) {
        for (String fileName : List.of("application.yml", "application.yaml")) {
            Path application = backendRoot.resolve("src/main/resources").resolve(fileName);
            if (!Files.isRegularFile(application)) {
                continue;
            }
            try {
                Object loaded = new Yaml().load(Files.readString(application));
                if (loaded instanceof Map<?, ?> root
                        && root.get("server") instanceof Map<?, ?> server
                        && server.get("servlet") instanceof Map<?, ?> servlet
                        && servlet.get("context-path") != null) {
                    return normalizePath(String.valueOf(servlet.get("context-path")));
                }
            } catch (IOException ignored) {
                return "";
            }
        }
        return "";
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path.trim())) {
            return "";
        }
        String normalized = path.trim().startsWith("/") ? path.trim() : "/" + path.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
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

    private String stripComments(String source) {
        StringBuilder result = new StringBuilder(source.length());
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : 0;
            if (quote != 0) {
                result.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\"' || current == '\'' || current == '`') {
                quote = current;
                result.append(current);
                continue;
            }
            if (current == '/' && next == '/') {
                index++;
                while (index + 1 < source.length() && source.charAt(index + 1) != '\n') {
                    index++;
                }
                continue;
            }
            if (current == '/' && next == '*') {
                index++;
                while (index + 1 < source.length()
                        && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) {
                    if (source.charAt(index) == '\n') {
                        result.append('\n');
                    }
                    index++;
                }
                index++;
                continue;
            }
            result.append(current);
        }
        return result.toString();
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

    private record ContractOperation(String path, String method) {
    }

    private record RouteMapping(String path, Set<String> methods) {
        private boolean accepts(String method) {
            return methods.isEmpty() || methods.contains(method);
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
