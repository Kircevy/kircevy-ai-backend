package com.wgz.aikir.multiagent.service.impl;

import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.exception.ErrorCode;
import com.wgz.aikir.exception.ThrowUtils;
import com.wgz.aikir.multiagent.config.MultiAgentProperties;
import com.wgz.aikir.multiagent.model.request.MultiAgentSettingsUpdateRequest;
import com.wgz.aikir.multiagent.model.vo.MultiAgentSettingsVO;
import com.wgz.aikir.multiagent.service.MultiAgentSettingsService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** 通过外部 YAML 保存管理员开关，避免改写打包在 JAR 内的 application.yml。 */
@Slf4j
@Service
public class MultiAgentSettingsServiceImpl implements MultiAgentSettingsService {

    private static final String SETTINGS_FILE_NAME = "multi-agent-settings.yml";

    @Resource
    private MultiAgentProperties multiAgentProperties;

    private final Path settingsFile = Path.of(System.getProperty("user.dir"), "config", SETTINGS_FILE_NAME)
            .toAbsolutePath().normalize();

    @PostConstruct
    public synchronized void loadPersistedSettings() {
        if (!Files.exists(settingsFile)) {
            return;
        }
        try {
            Object loaded = new Yaml().load(Files.readString(settingsFile, StandardCharsets.UTF_8));
            if (!(loaded instanceof Map<?, ?> root) || !(root.get("multi-agent") instanceof Map<?, ?> settings)) {
                log.warn("多智能体外部配置格式无效，继续使用 application.yml 默认值：{}", settingsFile);
                return;
            }
            multiAgentProperties.setEnabled(readBoolean(settings, "enabled", multiAgentProperties.isEnabled()));
            multiAgentProperties.setFullstackOnly(readBoolean(settings, "fullstack-only", multiAgentProperties.isFullstackOnly()));
            multiAgentProperties.setPlanningEnabled(readBoolean(settings, "planning-enabled", multiAgentProperties.isPlanningEnabled()));
            log.info("已加载多智能体外部配置：{}", settingsFile);
        } catch (Exception exception) {
            log.error("加载多智能体外部配置失败，继续使用 application.yml 默认值：{}", settingsFile, exception);
        }
    }

    @Override
    public synchronized MultiAgentSettingsVO getSettings() {
        return new MultiAgentSettingsVO(
                multiAgentProperties.isEnabled(),
                multiAgentProperties.isFullstackOnly(),
                multiAgentProperties.isPlanningEnabled());
    }

    @Override
    public synchronized MultiAgentSettingsVO updateSettings(MultiAgentSettingsUpdateRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "开关配置不能为空");
        if (request.getEnabled() != null) {
            multiAgentProperties.setEnabled(request.getEnabled());
        }
        if (request.getFullstackOnly() != null) {
            multiAgentProperties.setFullstackOnly(request.getFullstackOnly());
        }
        if (request.getPlanningEnabled() != null) {
            multiAgentProperties.setPlanningEnabled(request.getPlanningEnabled());
        }
        persistSettings();
        return getSettings();
    }

    private void persistSettings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("enabled", multiAgentProperties.isEnabled());
        settings.put("fullstack-only", multiAgentProperties.isFullstackOnly());
        settings.put("planning-enabled", multiAgentProperties.isPlanningEnabled());
        Map<String, Object> root = Map.of("multi-agent", settings);
        String yamlContent = new Yaml().dump(root);
        try {
            Files.createDirectories(settingsFile.getParent());
            Path temporaryFile = Files.createTempFile(settingsFile.getParent(), "multi-agent-settings-", ".tmp");
            Files.writeString(temporaryFile, yamlContent, StandardCharsets.UTF_8);
            try {
                Files.move(temporaryFile, settingsFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryFile, settingsFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存多智能体 YAML 配置失败：" + exception.getMessage());
        }
    }

    private boolean readBoolean(Map<?, ?> settings, String key, boolean defaultValue) {
        Object value = settings.get(key);
        return value instanceof Boolean booleanValue ? booleanValue : defaultValue;
    }
}
