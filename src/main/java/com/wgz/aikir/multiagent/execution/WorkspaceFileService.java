package com.wgz.aikir.multiagent.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 将模型返回的文件清单安全落盘到单一受权工作区。 */
@Component
public class WorkspaceFileService {

    private static final int MAX_FILE_COUNT = 80;
    private static final int MAX_FILE_CONTENT_LENGTH = 200_000;

    private final ObjectMapper objectMapper;

    public WorkspaceFileService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> writeBundle(Path workspace, String rawBundle) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBundle);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "执行 Agent 未返回合法文件清单");
        }
        if (root == null || !root.isObject() || root.size() != 1 || !root.has("files")) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "执行 Agent 文件清单顶层结构不合法");
        }
        JsonNode files = root.get("files");
        if (!files.isArray() || files.isEmpty() || files.size() > MAX_FILE_COUNT) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "执行 Agent 文件数量不合法");
        }
        Path normalizedWorkspace = workspace.toAbsolutePath().normalize();
        List<String> writtenFiles = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        for (JsonNode file : files) {
            if (!file.isObject() || file.size() != 2 || !file.hasNonNull("path") || !file.hasNonNull("content")
                    || !file.get("path").isTextual() || !file.get("content").isTextual()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "执行 Agent 文件项不完整");
            }
            String relativePath = file.get("path").asText();
            String content = file.get("content").asText();
            validateRelativePath(relativePath);
            if (!seenPaths.add(relativePath)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "执行 Agent 返回了重复文件路径：" + relativePath);
            }
            if (content.length() > MAX_FILE_CONTENT_LENGTH) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "执行 Agent 单个文件过大：" + relativePath);
            }
            Path target = normalizedWorkspace.resolve(relativePath).normalize();
            if (!target.startsWith(normalizedWorkspace)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "执行 Agent 尝试越权写入");
            }
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, content, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "写入协作工作区失败：" + relativePath);
            }
            writtenFiles.add(relativePath.replace('\\', '/'));
        }
        return writtenFiles;
    }

    private void validateRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || relativePath.contains("\\")
                || relativePath.startsWith("/") || relativePath.contains("..") || relativePath.contains(":")) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "执行 Agent 文件路径不合法");
        }
    }
}
