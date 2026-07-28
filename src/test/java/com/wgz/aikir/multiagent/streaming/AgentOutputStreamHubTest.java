package com.wgz.aikir.multiagent.streaming;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentOutputStreamHubTest {

    @Test
    void reconnectReturnsCurrentFrontendAndBackendSnapshots() {
        AgentOutputStreamHub hub = new AgentOutputStreamHub();
        hub.append("run-1", "frontend_generation", "front");
        hub.append("run-1", "backend_generation", "back");

        List<AgentOutputStreamEvent> snapshots = hub.subscribe("run-1")
                .take(2)
                .collectList()
                .block(Duration.ofSeconds(1));

        assertEquals(List.of("backend_generation", "frontend_generation"),
                snapshots.stream().map(AgentOutputStreamEvent::taskKey).sorted().toList());
        assertEquals(List.of("back", "front"),
                snapshots.stream().map(AgentOutputStreamEvent::content).sorted().toList());
    }

    @Test
    void parallelAppendsRemainIsolatedByTask() {
        AgentOutputStreamHub hub = new AgentOutputStreamHub();

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> hub.append("run-2", "frontend_generation", "front-a")),
                CompletableFuture.runAsync(() -> hub.append("run-2", "backend_generation", "back-a")),
                CompletableFuture.runAsync(() -> hub.append("run-2", "frontend_generation", "front-b")),
                CompletableFuture.runAsync(() -> hub.append("run-2", "backend_generation", "back-b"))
        ).join();

        List<AgentOutputStreamEvent> snapshots = hub.subscribe("run-2")
                .take(2)
                .collectList()
                .block(Duration.ofSeconds(1));

        String frontend = snapshots.stream()
                .filter(event -> "frontend_generation".equals(event.taskKey()))
                .findFirst()
                .orElseThrow()
                .content();
        String backend = snapshots.stream()
                .filter(event -> "backend_generation".equals(event.taskKey()))
                .findFirst()
                .orElseThrow()
                .content();
        assertEquals(14, frontend.length());
        assertEquals(12, backend.length());
    }
}
