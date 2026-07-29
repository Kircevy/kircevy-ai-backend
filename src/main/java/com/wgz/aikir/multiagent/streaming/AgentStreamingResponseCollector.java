package com.wgz.aikir.multiagent.streaming;

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
                .onCompleteResponse(completeResponse -> {
                    String completeText = completeResponse.aiMessage() == null
                            ? "" : completeResponse.aiMessage().text();
                    String collectedText = response.toString();
                    String resolvedText = completeText == null || completeText.isBlank() ? collectedText : completeText;
                    if (!resolvedText.equals(collectedText)) {
                        outputStreamHub.replace(runId, taskKey, resolvedText);
                    }
                    completion.complete(resolvedText);
                })
                .onError(completion::completeExceptionally)
                .start();
        try {
            return completion.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            String message = cause.getMessage();
            throw new StreamingGenerationException(
                    message == null || message.isBlank() ? "执行 Agent 流式生成失败" : message,
                    response.toString(), cause);
        }
    }

    public static class StreamingGenerationException extends RuntimeException {
        private final String partialResponse;

        public StreamingGenerationException(String message, String partialResponse, Throwable cause) {
            super(message, cause);
            this.partialResponse = partialResponse;
        }

        public String partialResponse() {
            return partialResponse;
        }
    }
}
