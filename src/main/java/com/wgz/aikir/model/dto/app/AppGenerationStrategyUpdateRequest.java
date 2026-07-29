package com.wgz.aikir.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 首次生成前选择应用生成方式。 */
@Data
public class AppGenerationStrategyUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long appId;

    private String generationStrategy;
}
