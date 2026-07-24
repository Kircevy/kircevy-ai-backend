package com.wgz.aikir.multiagent.domain.enums;

import lombok.Getter;

/** M1 固定规划产物类型及其隔离工作区文件名。 */
@Getter
public enum PlanningArtifactTypeEnum {

    PRODUCT_SPEC("PRODUCT_SPEC", "product-spec.json"),
    ARCHITECTURE("ARCHITECTURE", "architecture.json"),
    API_CONTRACT("API_CONTRACT", "api-contract.yaml"),
    TASK_MANIFEST("TASK_MANIFEST", "task-manifest.json");

    private final String value;
    private final String fileName;

    PlanningArtifactTypeEnum(String value, String fileName) {
        this.value = value;
        this.fileName = fileName;
    }
}
