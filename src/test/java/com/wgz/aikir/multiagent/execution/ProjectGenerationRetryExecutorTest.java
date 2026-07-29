package com.wgz.aikir.multiagent.execution;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectGenerationRetryExecutorTest {

    @Test
    void retriesOnceWithThePreviousFailureFeedback() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        String[] feedback = new String[1];

        String result = new ProjectGenerationRetryExecutor().execute((attempt, previousFailure) -> {
            attempts.incrementAndGet();
            feedback[0] = previousFailure;
            return attempt == 1
                    ? ProjectGenerationRetryExecutor.AttemptResult.failed("missing tsconfig.node.json")
                    : ProjectGenerationRetryExecutor.AttemptResult.succeeded("ready");
        });

        assertEquals("ready", result);
        assertEquals(2, attempts.get());
        assertEquals("missing tsconfig.node.json", feedback[0]);
    }
}
