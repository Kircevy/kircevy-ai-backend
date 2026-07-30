package com.wgz.aikir.ai.streaming;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenStreamFluxAdapterTest {

    @Test
    void shouldForwardPartialResponsesToReactorSubscribers() {
        TokenStream tokenStream = mock(TokenStream.class);
        ArgumentCaptor<Consumer<String>> partialResponseCaptor = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Consumer<ChatResponse>> completeResponseCaptor = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Consumer<Throwable>> errorCaptor = ArgumentCaptor.forClass(Consumer.class);
        when(tokenStream.onPartialResponse(partialResponseCaptor.capture())).thenReturn(tokenStream);
        when(tokenStream.onCompleteResponse(completeResponseCaptor.capture())).thenReturn(tokenStream);
        when(tokenStream.onError(errorCaptor.capture())).thenReturn(tokenStream);

        List<String> chunks = new ArrayList<>();
        new TokenStreamFluxAdapter().toFlux(tokenStream).doOnNext(chunks::add).subscribe();

        partialResponseCaptor.getValue().accept("<html>");
        partialResponseCaptor.getValue().accept("</html>");
        completeResponseCaptor.getValue().accept(mock(ChatResponse.class));

        assertEquals(List.of("<html>", "</html>"), chunks);
        verify(tokenStream).start();
    }
}
