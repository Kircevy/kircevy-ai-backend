package com.wgz.aikir.multiagent.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/** 架构智能体：根据产品规格生成架构、接口契约和后续任务图。 */
public interface ArchitecturePlanningAiService {

    @SystemMessage(fromResource = "prompt/multiagent/architecture-planning-system-prompt.txt")
    String createArchitectureBundle(@UserMessage String productSpecJson);
}
