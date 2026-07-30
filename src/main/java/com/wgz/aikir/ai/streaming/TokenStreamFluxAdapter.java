package com.wgz.aikir.ai.streaming;

import dev.langchain4j.service.TokenStream;
import reactor.core.publisher.Flux;

/**
 * 将 LangChain4j 的回调式流转换为项目现有的 Reactor 流。
 */
public class TokenStreamFluxAdapter {

    public Flux<String> toFlux(TokenStream tokenStream) {
        return Flux.create(sink -> tokenStream
                .onPartialResponse(sink::next)
                .onCompleteResponse(ignored -> sink.complete())
                .onError(sink::error)
                .start());
    }
}
