package com.wgz.aikir.multiagent.ai;

import com.wgz.aikir.utils.SpringContextUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
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

    /** M2 代码执行使用无工具模型，文件写入统一由受限工作区服务完成。 */
    public FrontendExecutionAiService createFrontendExecutionAgent() {
        return AiServices.builder(FrontendExecutionAiService.class)
                .streamingChatModel(createStreamingExecutionModel())
                .build();
    }

    public FrontendExecutionAiService createFrontendManifestAgent() {
        return AiServices.builder(FrontendExecutionAiService.class)
                .streamingChatModel(createManifestModel())
                .build();
    }

    /** M2 代码执行使用无工具模型，文件写入统一由受限工作区服务完成。 */
    public BackendExecutionAiService createBackendExecutionAgent() {
        return AiServices.builder(BackendExecutionAiService.class)
                .streamingChatModel(createStreamingExecutionModel())
                .build();
    }

    public BackendExecutionAiService createBackendManifestAgent() {
        return AiServices.builder(BackendExecutionAiService.class)
                .streamingChatModel(createManifestModel())
                .build();
    }

    private ChatModel createPlanningModel() {
        return SpringContextUtil.getBean("routingChatModelPrototype", ChatModel.class);
    }

    private StreamingChatModel createStreamingExecutionModel() {
        return SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
    }

    private StreamingChatModel createManifestModel() {
        return SpringContextUtil.getBean("executionManifestStreamingChatModelPrototype", StreamingChatModel.class);
    }
}
