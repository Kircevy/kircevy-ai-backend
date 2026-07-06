package com.wgz.aikir.ai;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wgz.aikir.ai.guardrail.PromptSafetyInputGuardrail;
import com.wgz.aikir.ai.tools.*;
import com.wgz.aikir.config.RedisChatMemoryStoreConfig;
import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.exception.ErrorCode;
import com.wgz.aikir.model.enums.CodeGenTypeEnum;
import com.wgz.aikir.service.ChatHistoryService;
import com.wgz.aikir.utils.SpringContextUtil;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import javax.swing.*;
import java.time.Duration;

@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory{

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    @Lazy
    private ChatHistoryService chatHistoryService;

    @Resource
    private ToolManager toolManager;


    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.info("AI 服务实例被移除，缓存键为：{}, 原因为：{}", key, cause);
            })
            .build();

    /**
     * 根据appId获取服务（带缓存）这个方法是为了兼容历史逻辑
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId){
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
    }

    /**
     * 根据appId 和代码生产类型获取服务（带缓存）
     *
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum genTypeEnum) {
        String cacheKey = buildCacheKey(appId, genTypeEnum);
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, genTypeEnum));
    }

    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum genTypeEnum){
        log.info("为appId：{}，创建新的 AI 实例", appId);
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .maxMessages(20)
                .chatMemoryStore(redisChatMemoryStore)
                .build();
        // 在创建AI服务时，直接从数据库中加载对话历史
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        return switch (genTypeEnum) {
            case VUE_PROJECT, SPRINGBOOT -> {
                StreamingChatModel reasoningStreamingChatModel = SpringContextUtil.getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                    .streamingChatModel(reasoningStreamingChatModel)
                    .chatMemoryProvider(memoryId -> chatMemory)
                    .tools(toolManager.getAllTools())
                    .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                            toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()
                    ))
                    .inputGuardrails(new PromptSafetyInputGuardrail())
                    .maxSequentialToolsInvocations(20)
                    .build();
            }
            case HTML, MULTI_FILE -> {
                StreamingChatModel openAiStreamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                    .chatModel(chatModel)
                    .streamingChatModel(openAiStreamingChatModel)
                    .chatMemory(chatMemory)
                    .inputGuardrails(new PromptSafetyInputGuardrail())
                    .build();
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + genTypeEnum);
        };
    }

    /**
     * 创建 AI 代码生成器服务
     *
     * @return
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0L);
    }

    /**
     * 获取缓存键
     * @param appId
     * @param genTypeEnum
     * @return
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum genTypeEnum) {
        return appId + "-" + genTypeEnum.getValue();
    }

}
