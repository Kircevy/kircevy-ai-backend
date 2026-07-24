package com.wgz.aikir.multiagent.ai;

import com.wgz.aikir.utils.SpringContextUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;

/** 创建无对话记忆、无工具权限的规划智能体，确保 M1 不产生代码写入副作用。 */
@Component
public class PlanningAiServiceFactory {

    public ProductPlanningAiService createProductPlanningAgent() {
        return AiServices.builder(ProductPlanningAiService.class)
                .chatModel(createPlanningModel())
                .build();
    }

    public ArchitecturePlanningAiService createArchitecturePlanningAgent() {
        return AiServices.builder(ArchitecturePlanningAiService.class)
                .chatModel(createPlanningModel())
                .build();
    }

    private ChatModel createPlanningModel() {
        return SpringContextUtil.getBean("routingChatModelPrototype", ChatModel.class);
    }
}
