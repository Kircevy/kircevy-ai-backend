package com.wgz.aikir.multiagent.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskFailureSummaryTest {

    @Test
    void preservesFailuresFromBothParallelAgents() {
        String summary = new TaskFailureSummary().summarize("frontend build failed", "backend compile failed");

        assertTrue(summary.contains("frontend build failed"));
        assertTrue(summary.contains("backend compile failed"));
    }
}
