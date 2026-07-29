package com.wgz.aikir.multiagent.streaming;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import reactor.core.Disposable;
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

    /** 使用模型完成事件中的完整文本修正可能缺失的增量流。 */
    public void replace(String runId, String taskKey, String content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        RunOutputState state = state(runId);
        synchronized (state) {
            state.outputs.put(taskKey, new StringBuffer(content));
            long sequence = state.sequence.incrementAndGet();
            state.sink.tryEmitNext(new AgentOutputStreamEvent(sequence, runId, taskKey, "SNAPSHOT", content));
        }
    }

    public Flux<AgentOutputStreamEvent> subscribe(String runId) {
        return Flux.create(emitter -> {
            RunOutputState state = state(runId);
            Disposable[] subscription = new Disposable[1];
            emitter.onDispose(() -> {
                if (subscription[0] != null) {
                    subscription[0].dispose();
                }
            });
            synchronized (state) {
                long snapshotSequence = state.sequence.get();
                List<AgentOutputStreamEvent> snapshots = state.outputs.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                        .map(entry -> new AgentOutputStreamEvent(snapshotSequence, runId, entry.getKey(),
                                "SNAPSHOT", entry.getValue().toString()))
                        .toList();
                List<AgentOutputStreamEvent> pendingEvents = new java.util.ArrayList<>();
                boolean[] snapshotDelivered = {false};
                subscription[0] = state.sink.asFlux()
                        .filter(event -> event.sequence() > snapshotSequence)
                        .subscribe(event -> {
                            if (snapshotDelivered[0]) {
                                emitter.next(event);
                            } else {
                                pendingEvents.add(event);
                            }
                        }, emitter::error);
                snapshots.forEach(emitter::next);
                snapshotDelivered[0] = true;
                pendingEvents.forEach(emitter::next);
            }
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
