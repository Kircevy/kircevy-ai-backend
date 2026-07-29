package com.wgz.aikir.core.handler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.wgz.aikir.ai.model.message.*;
import com.wgz.aikir.ai.tools.BaseTool;
import com.wgz.aikir.ai.tools.ToolManager;
import com.wgz.aikir.constant.AppConstant;
import com.wgz.aikir.core.builder.VueProjectBuilder;
import com.wgz.aikir.model.entity.User;
import com.wgz.aikir.model.entity.ChatHistory;
import com.wgz.aikir.model.enums.ChatHistoryMessageTypeEnum;
import com.wgz.aikir.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;

/**
 * JSON 消息流处理器
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    @Resource
    private ToolManager toolManager;

    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        // 收集数据用于生成后端记忆格式
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        ChatHistory aiChatHistory = ChatHistory.builder()
                .appId(appId)
                .userId(loginUser.getId())
                .messageType(ChatHistoryMessageTypeEnum.AI.getValue())
                .message("正在生成代码，请稍候…")
                .build();
        chatHistoryService.save(aiChatHistory);
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .map(chunk -> {
                    // 解析每个 JSON 消息块
                    return handleJsonMessageChunk(chunk, chatHistoryStringBuilder, seenToolIds);
                })
                .filter(StrUtil::isNotEmpty) // 过滤空字串
                .doOnComplete(() -> {
                    // 流式响应完成后，添加 AI 消息到对话历史
                    updateAiChatHistory(chatHistoryService, aiChatHistory, chatHistoryStringBuilder.toString());
//                    // 异步构建 Vue 项目
//                    String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId;
//                    vueProjectBuilder.buildVueProjectAsync(projectPath);
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    updateAiChatHistory(chatHistoryService, aiChatHistory, "AI回复失败: " + error.getMessage());
                });
    }

    private void updateAiChatHistory(ChatHistoryService chatHistoryService, ChatHistory chatHistory, String message) {
        chatHistory.setMessage(StrUtil.isBlank(message) ? "AI 未返回可展示内容" : message);
        if (!chatHistoryService.updateById(chatHistory)) {
            log.warn("Failed to update AI chat history, id: {}", chatHistory.getId());
        }
    }

    /**
     * 解析并收集 TokenStream 数据
     */
    private String handleJsonMessageChunk(String chunk, StringBuilder chatHistoryStringBuilder, Set<String> seenToolIds) {
        // 解析 JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiMessage.getData();
                // 直接拼接响应
                chatHistoryStringBuilder.append(data);
                return data;
            }
            case THINKING -> {
                // 思考内容仅透传给当前前端，不写入后续对话历史。
                return chunk;
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                // 检查是否是第一次看到这个工具 ID
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    // 第一次调用这个工具，记录 ID 并完整返回工具信息
                    seenToolIds.add(toolId);
                    BaseTool tool = toolManager.getTool(toolRequestMessage.getName());
                    return tool.generateToolRequestResponse();
                } else {
                    // 不是第一次调用这个工具，直接返回空
                    return "";
                }
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                String toolName = toolExecutedMessage.getName();
                BaseTool tool = toolManager.getTool(toolName);
                try {
                    JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                    String result = tool.generateToolExecutedResult(jsonObject);
                    // 输出前端和要持久化的内容
                    String output = String.format("\n\n%s\n\n", result);
                    chatHistoryStringBuilder.append(output);
                    return output;
                } catch (cn.hutool.json.JSONException e) {
                    // AI 返回的工具参数 JSON 可能因流式传输截断或特殊字符未转义而导致解析失败
                    log.error("工具参数 JSON 解析失败，工具名: {}，参数内容: {}", toolName, toolExecutedMessage.getArguments(), e);
                    String fallbackOutput = String.format("\n\n[工具调用] %s（参数解析失败）\n\n", tool != null ? tool.getDisplayName() : toolName);
                    chatHistoryStringBuilder.append(fallbackOutput);
                    return fallbackOutput;
                }
            }
            default -> {
                log.error("不支持的消息类型: {}", typeEnum);
                return "";
            }
        }
    }
}
