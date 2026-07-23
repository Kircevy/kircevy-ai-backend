package com.wgz.aikir.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps an active code-generation stream alive independently from a browser SSE connection.
 */
@Slf4j
@Service
public class CodeGenerationTaskService {

    private final Map<Long, Flux<String>> activeStreams = new ConcurrentHashMap<>();

    /**
     * Registers a stream once. replay() lets a user who returns to the page receive all output
     * already produced by the still-running task, while autoConnect() keeps the upstream task
     * alive after the original browser disconnects.
     */
    public Flux<String> start(Long appId, Flux<String> source) {
        Flux<String> existingStream = activeStreams.get(appId);
        if (existingStream != null) {
            return existingStream;
        }

        Flux<String> stream = source
                .doFinally(signalType -> {
                    activeStreams.remove(appId);
                    log.info("Code generation task finished for appId: {}, signal: {}", appId, signalType);
                })
                .replay()
                .autoConnect(1);
        Flux<String> racedStream = activeStreams.putIfAbsent(appId, stream);
        return racedStream == null ? stream : racedStream;
    }

    public boolean isRunning(Long appId) {
        return activeStreams.containsKey(appId);
    }

    public Flux<String> subscribe(Long appId) {
        Flux<String> stream = activeStreams.get(appId);
        if (stream == null) {
            return Flux.empty();
        }
        // A returning browser can receive a long replay. Batch fragments so the UI does not
        // render Markdown once per token and become unresponsive while catching up.
        return stream
                .bufferTimeout(64, Duration.ofMillis(120))
                .filter(chunks -> !chunks.isEmpty())
                .map(chunks -> String.join("", chunks));
    }
}
