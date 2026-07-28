package com.wgz.aikir.multiagent.streaming;

import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.exception.ErrorCode;
import dev.langchain4j.service.TokenStream;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** 收集完整文件包供校验落盘，同时把模型增量转发到对应 Agent 输出流。 */
@Component
public class AgentStreamingResponseCollector {

    private final AgentOutputStreamHub outputStreamHub;

    public AgentStreamingResponseCollector(AgentOutputStreamHub outputStreamHub) {
        this.outputStreamHub = outputStreamHub;
    }

    public String collect(String runId, String taskKey, TokenStream tokenStream) {
        StringBuilder response = new StringBuilder();
        CompletableFuture<String> completion = new CompletableFuture<>();
        tokenStream.onPartialResponse(partialResponse -> {
                    response.append(partialResponse);
                    outputStreamHub.append(runId, taskKey, partialResponse);
                })
                .onCompleteResponse(ignored -> completion.complete(response.toString()))
                .onError(completion::completeExceptionally)
                .start();
        try {
            return completion.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            String message = cause.getMessage();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    message == null || message.isBlank() ? "执行 Agent 流式生成失败" : message);
        }
    }
}
