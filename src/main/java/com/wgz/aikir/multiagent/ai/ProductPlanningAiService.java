package com.wgz.aikir.multiagent.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/** 产品智能体：将用户需求转换为可校验的产品规格。 */
public interface ProductPlanningAiService {

    @SystemMessage(fromResource = "prompt/multiagent/product-planning-system-prompt.txt")
    String createProductSpec(@UserMessage String userRequest);
}
