package com.wgz.aikir.multiagent.streaming;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 在运行期间隔离并转发前端、后端 Agent 的模型输出，同时为页面重连提供当前快照。 */
@Component
public class AgentOutputStreamHub {

    private final Cache<String, RunOutputState> states = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .maximumSize(200)
            .build();

    public void append(String runId, String taskKey, String content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        RunOutputState state = state(runId);
        synchronized (state) {
            state.outputs.computeIfAbsent(taskKey, ignored -> new StringBuffer()).append(content);
            long sequence = state.sequence.incrementAndGet();
            state.sink.tryEmitNext(new AgentOutputStreamEvent(sequence, runId, taskKey, "DELTA", content));
        }
    }

    public Flux<AgentOutputStreamEvent> subscribe(String runId) {
        return Flux.defer(() -> {
            RunOutputState state = state(runId);
            long snapshotSequence;
            List<AgentOutputStreamEvent> snapshots;
            synchronized (state) {
                snapshotSequence = state.sequence.get();
                snapshots = state.outputs.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                        .map(entry -> new AgentOutputStreamEvent(snapshotSequence, runId, entry.getKey(),
                                "SNAPSHOT", entry.getValue().toString()))
                        .toList();
            }
            return Flux.concat(
                    Flux.fromIterable(snapshots),
                    state.sink.asFlux().filter(event -> event.sequence() > snapshotSequence)
            );
        });
    }

    private RunOutputState state(String runId) {
        return states.get(runId, ignored -> new RunOutputState());
    }

    private static final class RunOutputState {
        private final AtomicLong sequence = new AtomicLong();
        private final Map<String, StringBuffer> outputs = new ConcurrentHashMap<>();
        private final Sinks.Many<AgentOutputStreamEvent> sink = Sinks.many().replay().limit(64);
    }
}
