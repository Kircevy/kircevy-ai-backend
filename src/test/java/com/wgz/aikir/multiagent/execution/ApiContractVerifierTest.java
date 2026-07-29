package com.wgz.aikir.multiagent.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiContractVerifierTest {

    private static final String CONTRACT = """
            paths:
              /api/brand-content:
                get: {}
            """;

    private static final String POST_CONTRACT = """
            paths:
              /api/brand-content:
                post: {}
            """;

    @TempDir
    Path tempDir;

    @Test
    void acceptsAControllerRouteRelativeToTheConfiguredContextPath() throws IOException {
        Path frontendRoot = writeFrontend();
        Path backendRoot = writeBackend("@GetMapping(\"/brand-content\")");

        ApiContractVerifier.VerificationResult result = new ApiContractVerifier()
                .verify(CONTRACT, frontendRoot, backendRoot);

        assertTrue(result.success(), result.summary());
    }

    @Test
    void rejectsAnApiPrefixDeclaredBothAsContextPathAndControllerMapping() throws IOException {
        Path frontendRoot = writeFrontend();
        Path backendRoot = writeBackend("@RequestMapping(\"/api\")\n@GetMapping(\"/brand-content\")");

        ApiContractVerifier.VerificationResult result = new ApiContractVerifier()
                .verify(CONTRACT, frontendRoot, backendRoot);

        assertFalse(result.success());
        assertTrue(result.summary().contains("重复"), result.summary());
    }

    @Test
    void rejectsAControllerThatUsesTheWrongHttpMethod() throws IOException {
        Path frontendRoot = writeFrontend("fetch('/api/brand-content', { method: 'POST' });");
        Path backendRoot = writeBackend("@GetMapping(\"/brand-content\")");

        ApiContractVerifier.VerificationResult result = new ApiContractVerifier()
                .verify(POST_CONTRACT, frontendRoot, backendRoot);

        assertFalse(result.success());
        assertTrue(result.summary().contains("POST"), result.summary());
    }

    @Test
    void rejectsRoutesThatExistOnlyInComments() throws IOException {
        Path frontendRoot = writeFrontend("// fetch('/api/brand-content');");
        Path backendRoot = writeBackend("// @GetMapping(\"/brand-content\")");

        ApiContractVerifier.VerificationResult result = new ApiContractVerifier()
                .verify(CONTRACT, frontendRoot, backendRoot);

        assertFalse(result.success());
    }

    @Test
    void rejectsPathsWithoutASupportedHttpOperation() throws IOException {
        Path frontendRoot = writeFrontend();
        Path backendRoot = writeBackend("@GetMapping(\"/brand-content\")");
        String contract = """
                paths:
                  /api/brand-content:
                    parameters: []
                """;

        ApiContractVerifier.VerificationResult result = new ApiContractVerifier()
                .verify(contract, frontendRoot, backendRoot);

        assertFalse(result.success());
    }

    private Path writeFrontend() throws IOException {
        return writeFrontend("fetch('/api/brand-content');");
    }

    private Path writeFrontend(String source) throws IOException {
        Path frontend = tempDir.resolve("frontend");
        Path api = frontend.resolve("src/api.ts");
        Files.createDirectories(api.getParent());
        Files.writeString(api, source);
        return frontend;
    }

    private Path writeBackend(String mapping) throws IOException {
        Path backend = tempDir.resolve("backend");
        Path controller = backend.resolve("src/main/java/com/example/app/BrandContentController.java");
        Path application = backend.resolve("src/main/resources/application.yml");
        Files.createDirectories(controller.getParent());
        Files.createDirectories(application.getParent());
        Files.writeString(controller, "@RestController\n" + mapping);
        Files.writeString(application, "server:\n  servlet:\n    context-path: /api\n");
        return backend;
    }
}
