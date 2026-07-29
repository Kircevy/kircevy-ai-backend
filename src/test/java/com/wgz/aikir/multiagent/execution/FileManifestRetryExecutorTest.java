package com.wgz.aikir.multiagent.execution;

import com.wgz.aikir.exception.BusinessException;
import com.wgz.aikir.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileManifestRetryExecutorTest {

    @Test
    void retriesOnceWhenTheFirstManifestFailsValidation() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        String[] secondPrompt = new String[1];

        List<String> files = new FileManifestRetryExecutor().generate(
                (prompt, attempt) -> {
                    if (attempts.incrementAndGet() == 2) {
                        secondPrompt[0] = prompt;
                    }
                    return attempt == 1 ? "not-json" : "{\"files\":[\"src/main.ts\"]}";
                },
                rawManifest -> {
                    if ("not-json".equals(rawManifest)) {
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "invalid manifest");
                    }
                    return List.of("src/main.ts");
                },
                "original prompt");

        assertEquals(List.of("src/main.ts"), files);
        assertEquals(2, attempts.get());
        assertTrue(secondPrompt[0].contains("invalid manifest"));
    }
}
