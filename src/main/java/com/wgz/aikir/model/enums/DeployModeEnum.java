package com.wgz.aikir.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 应用部署模式枚举
 */
@Getter
public enum DeployModeEnum {

    /**
     * 代码下载模式：下载完整源码 zip，用户本地运行
     */
    CODE_DOWNLOAD("代码下载模式", "code_download"),

    /**
     * Docker 一键部署模式：平台自动执行 docker-compose up -d
     */
    DOCKER_COMPOSE("Docker 一键部署模式", "docker_compose");

    private final String text;
    private final String value;

    DeployModeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的 value
     * @return 枚举值
     */
    public static DeployModeEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (DeployModeEnum anEnum : DeployModeEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
