package com.wgz.aikir.multiagent.streaming;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentStreamingResponseCollectorTest {

    @Test
    void collectsTheCompleteResponseWhilePublishingEveryPartialChunk() {
        TokenStream tokenStream = mock(TokenStream.class);
        AgentOutputStreamHub hub = new AgentOutputStreamHub();
        ArgumentCaptor<Consumer<String>> partialCaptor = consumerCaptor();
        ArgumentCaptor<Consumer<ChatResponse>> completeCaptor = consumerCaptor();
        when(tokenStream.onPartialResponse(partialCaptor.capture())).thenReturn(tokenStream);
        when(tokenStream.onCompleteResponse(completeCaptor.capture())).thenReturn(tokenStream);
        when(tokenStream.onError(any())).thenReturn(tokenStream);
        doAnswer(invocation -> {
            partialCaptor.getValue().accept("file-a");
            partialCaptor.getValue().accept("-file-b");
            completeCaptor.getValue().accept(mock(ChatResponse.class));
            return null;
        }).when(tokenStream).start();

        String response = new AgentStreamingResponseCollector(hub)
                .collect("run-3", "frontend_generation", tokenStream);
        List<AgentOutputStreamEvent> snapshots = hub.subscribe("run-3")
                .take(1)
                .collectList()
                .block(Duration.ofSeconds(1));

        assertEquals("file-a-file-b", response);
        assertEquals("file-a-file-b", snapshots.getFirst().content());
    }

    @Test
    void fallsBackToTheCompleteResponseWhenTheProviderDoesNotPublishPartialChunks() {
        TokenStream tokenStream = mock(TokenStream.class);
        ArgumentCaptor<Consumer<String>> partialCaptor = consumerCaptor();
        ArgumentCaptor<Consumer<ChatResponse>> completeCaptor = consumerCaptor();
        when(tokenStream.onPartialResponse(partialCaptor.capture())).thenReturn(tokenStream);
        when(tokenStream.onCompleteResponse(completeCaptor.capture())).thenReturn(tokenStream);
        when(tokenStream.onError(any())).thenReturn(tokenStream);
        doAnswer(invocation -> {
            completeCaptor.getValue().accept(ChatResponse.builder()
                    .aiMessage(AiMessage.from("complete-source"))
                    .build());
            return null;
        }).when(tokenStream).start();

        String response = new AgentStreamingResponseCollector(new AgentOutputStreamHub())
                .collect("run-4", "backend_generation", tokenStream);

        assertEquals("complete-source", response);
    }

    @Test
    void preservesPartialResponseWhenTheStreamFails() {
        TokenStream tokenStream = mock(TokenStream.class);
        ArgumentCaptor<Consumer<String>> partialCaptor = consumerCaptor();
        ArgumentCaptor<Consumer<Throwable>> errorCaptor = consumerCaptor();
        when(tokenStream.onPartialResponse(partialCaptor.capture())).thenReturn(tokenStream);
        when(tokenStream.onCompleteResponse(any())).thenReturn(tokenStream);
        when(tokenStream.onError(errorCaptor.capture())).thenReturn(tokenStream);
        doAnswer(invocation -> {
            partialCaptor.getValue().accept("partial-source");
            errorCaptor.getValue().accept(new IllegalStateException("provider disconnected"));
            return null;
        }).when(tokenStream).start();

        AgentStreamingResponseCollector.StreamingGenerationException exception = assertThrows(
                AgentStreamingResponseCollector.StreamingGenerationException.class,
                () -> new AgentStreamingResponseCollector(new AgentOutputStreamHub())
                        .collect("run-5", "backend_generation", tokenStream));

        assertEquals("partial-source", exception.partialResponse());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> ArgumentCaptor<Consumer<T>> consumerCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Consumer.class);
    }
}
