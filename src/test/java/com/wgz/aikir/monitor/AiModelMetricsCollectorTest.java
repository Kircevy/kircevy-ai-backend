package com.wgz.aikir.monitor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AiModelMetricsCollectorTest {

    @Test
    void shouldUseUnknownTagWhenModelNameIsMissing() {
        AiModelMetricsCollector collector = new AiModelMetricsCollector();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(collector, "meterRegistry", meterRegistry);

        collector.recordRequest("user", "app", null, "success");

        var counter = meterRegistry.find("ai_model_request_total")
                .tag("model_name", "unknown")
                .counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }
}
