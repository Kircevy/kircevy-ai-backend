package com.wgz.aikir.multiagent.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

/** 对 M1 固定规划产物执行结构化模式校验。 */
@Component
public class StructuredPlanningValidator {

    private final ObjectMapper objectMapper;

    public StructuredPlanningValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode validateProductSpec(String rawJson) {
        JsonNode root = parseJsonObject(rawJson, "产品规格");
        requireText(root, "version", "产品规格");
        requireText(root, "summary", "产品规格");
        requireTextArray(root, "userRoles", "产品规格");
        requireTextArray(root, "functionalRequirements", "产品规格");
        requireTextArray(root, "acceptanceCriteria", "产品规格");
        JsonNode pages = requireArray(root, "pages", "产品规格");
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("产品规格的 pages 不能为空");
        }
        for (JsonNode page : pages) {
            requireObject(page, "产品规格中的页面");
            requireText(page, "name", "页面");
            requireText(page, "path", "页面");
            requireTextArray(page, "features", "页面");
        }
        return root;
    }

    public ArchitectureBundle validateArchitectureBundle(String rawJson) {
        JsonNode root = parseJsonObject(rawJson, "架构规划结果");
        JsonNode architecture = requireObjectField(root, "architecture", "架构规划结果");
        requireText(architecture, "version", "架构");
        JsonNode technologyStack = requireObjectField(architecture, "technologyStack", "架构");
        requireText(technologyStack, "frontend", "技术栈");
        requireText(technologyStack, "backend", "技术栈");
        requireText(technologyStack, "database", "技术栈");
        JsonNode modules = requireArray(architecture, "modules", "架构");
        if (modules.isEmpty()) {
            throw new IllegalArgumentException("架构的 modules 不能为空");
        }
        for (JsonNode module : modules) {
            requireObject(module, "架构模块");
            requireText(module, "name", "架构模块");
            requireText(module, "responsibility", "架构模块");
            requireStringArray(module, "dependsOn", "架构模块", false);
        }
        JsonNode dataModels = requireArray(architecture, "dataModels", "架构");
        for (JsonNode dataModel : dataModels) {
            requireObject(dataModel, "数据模型");
            requireText(dataModel, "name", "数据模型");
            JsonNode fields = requireArray(dataModel, "fields", "数据模型");
            for (JsonNode field : fields) {
                requireObject(field, "数据模型字段");
                requireText(field, "name", "数据模型字段");
                requireText(field, "type", "数据模型字段");
                if (!field.has("required") || !field.get("required").isBoolean()) {
                    throw new IllegalArgumentException("数据模型字段的 required 必须为布尔值");
                }
            }
        }

        String apiContractYaml = requireText(root, "apiContractYaml", "架构规划结果");
        validateApiContractYaml(apiContractYaml);

        JsonNode taskManifest = requireObjectField(root, "taskManifest", "架构规划结果");
        validateTaskManifest(taskManifest);
        return new ArchitectureBundle(architecture, apiContractYaml, taskManifest);
    }

    private void validateApiContractYaml(String yamlContent) {
        Object loaded;
        try {
            loaded = new Yaml().load(yamlContent);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("apiContractYaml 不是有效 YAML：" + exception.getMessage());
        }
        if (!(loaded instanceof Map<?, ?> document)) {
            throw new IllegalArgumentException("apiContractYaml 必须是 YAML 对象");
        }
        Object openapi = document.get("openapi");
        Object info = document.get("info");
        Object paths = document.get("paths");
        if (!(openapi instanceof String version) || !version.startsWith("3.")) {
            throw new IllegalArgumentException("apiContractYaml 必须声明 OpenAPI 3 版本");
        }
        if (!(info instanceof Map<?, ?>) || !(paths instanceof Map<?, ?> pathMap) || pathMap.isEmpty()) {
            throw new IllegalArgumentException("apiContractYaml 必须包含非空的 info 和 paths");
        }
        boolean hasOperation = pathMap.values().stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .anyMatch(pathItem -> pathItem.keySet().stream()
                        .anyMatch(key -> List.of("get", "post", "put", "patch", "delete").contains(String.valueOf(key))));
        if (!hasOperation) {
            throw new IllegalArgumentException("apiContractYaml 的 paths 中至少需要一个 HTTP 操作");
        }
    }

    private void validateTaskManifest(JsonNode taskManifest) {
        requireText(taskManifest, "version", "任务清单");
        JsonNode tasks = requireArray(taskManifest, "tasks", "任务清单");
        boolean hasFrontendTask = false;
        boolean hasBackendTask = false;
        for (JsonNode task : tasks) {
            requireObject(task, "任务清单中的任务");
            String taskKey = requireText(task, "taskKey", "任务");
            requireText(task, "role", "任务");
            requireStringArray(task, "dependsOn", "任务", false);
            requireStringArray(task, "inputArtifacts", "任务", false);
            requireStringArray(task, "outputArtifacts", "任务", false);
            JsonNode writeScopes = requireTextArray(task, "writeScopes", "任务");
            if ("frontend_generation".equals(taskKey)) {
                hasFrontendTask = true;
                requireExactWriteScope(writeScopes, "frontend/**", taskKey);
            }
            if ("backend_generation".equals(taskKey)) {
                hasBackendTask = true;
                requireExactWriteScope(writeScopes, "backend/**", taskKey);
            }
        }
        if (!hasFrontendTask || !hasBackendTask) {
            throw new IllegalArgumentException("任务清单必须包含 frontend_generation 与 backend_generation");
        }
    }

    private void requireExactWriteScope(JsonNode scopes, String expectedScope, String taskKey) {
        if (scopes.size() != 1 || !expectedScope.equals(scopes.get(0).asText())) {
            throw new IllegalArgumentException(taskKey + " 的 writeScopes 只能为 " + expectedScope);
        }
    }

    private JsonNode parseJsonObject(String rawJson, String objectName) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(rawJson));
            return requireObject(root, objectName);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(objectName + "不是有效 JSON：" + exception.getOriginalMessage());
        }
    }

    private String stripCodeFence(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd > 0 && lastFence > firstLineEnd) {
                return trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private JsonNode requireObjectField(JsonNode parent, String fieldName, String objectName) {
        if (!parent.has(fieldName)) {
            throw new IllegalArgumentException(objectName + "缺少字段 " + fieldName);
        }
        return requireObject(parent.get(fieldName), objectName + "的 " + fieldName);
    }

    private JsonNode requireObject(JsonNode value, String objectName) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(objectName + "必须是对象");
        }
        return value;
    }

    private String requireText(JsonNode parent, String fieldName, String objectName) {
        if (!parent.has(fieldName) || !parent.get(fieldName).isTextual() || parent.get(fieldName).asText().isBlank()) {
            throw new IllegalArgumentException(objectName + "缺少非空字符串字段 " + fieldName);
        }
        return parent.get(fieldName).asText();
    }

    private JsonNode requireArray(JsonNode parent, String fieldName, String objectName) {
        if (!parent.has(fieldName) || !parent.get(fieldName).isArray()) {
            throw new IllegalArgumentException(objectName + "缺少数组字段 " + fieldName);
        }
        return parent.get(fieldName);
    }

    private JsonNode requireTextArray(JsonNode parent, String fieldName, String objectName) {
        return requireStringArray(parent, fieldName, objectName, true);
    }

    private JsonNode requireStringArray(JsonNode parent, String fieldName, String objectName, boolean requireNonEmpty) {
        JsonNode array = requireArray(parent, fieldName, objectName);
        if (requireNonEmpty && array.isEmpty()) {
            throw new IllegalArgumentException(objectName + "的 " + fieldName + " 必须是非空字符串数组");
        }
        for (JsonNode item : array) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new IllegalArgumentException(objectName + "的 " + fieldName + " 必须是字符串数组");
            }
        }
        return array;
    }

    public record ArchitectureBundle(JsonNode architecture, String apiContractYaml, JsonNode taskManifest) {
    }
}
