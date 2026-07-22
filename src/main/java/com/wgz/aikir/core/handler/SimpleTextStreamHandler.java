package com.wgz.aikir.core.handler;

import com.wgz.aikir.model.entity.User;
import com.wgz.aikir.model.entity.ChatHistory;
import com.wgz.aikir.model.enums.ChatHistoryMessageTypeEnum;
import com.wgz.aikir.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 简单文本流处理器
 * 处理 HTML 和 MULTI_FILE 类型的流式响应
 */
@Slf4j
public class SimpleTextStreamHandler {

    /**
     * 处理传统流（HTML, MULTI_FILE）
     * 直接收集完整的文本响应
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
        StringBuilder aiResponseBuilder = new StringBuilder();
        ChatHistory aiChatHistory = ChatHistory.builder()
                .appId(appId)
                .userId(loginUser.getId())
                .messageType(ChatHistoryMessageTypeEnum.AI.getValue())
                .message("正在生成代码，请稍候…")
                .build();
        chatHistoryService.save(aiChatHistory);
        return originFlux
                .map(chunk -> {
                    // 收集AI响应内容
                    aiResponseBuilder.append(chunk);
                    return chunk;
                })
                .doOnComplete(() -> {
                    // 流式响应完成后，添加AI消息到对话历史
                    updateAiChatHistory(chatHistoryService, aiChatHistory, aiResponseBuilder.toString());
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    updateAiChatHistory(chatHistoryService, aiChatHistory, "AI回复失败: " + error.getMessage());
                });
    }

    private void updateAiChatHistory(ChatHistoryService chatHistoryService, ChatHistory chatHistory, String message) {
        chatHistory.setMessage(message == null || message.isBlank() ? "AI 未返回可展示内容" : message);
        if (!chatHistoryService.updateById(chatHistory)) {
            log.warn("Failed to update AI chat history, id: {}", chatHistory.getId());
        }
    }
}
