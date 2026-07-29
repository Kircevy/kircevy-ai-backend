package com.wgz.aikir.multiagent.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectIntegrityVerifierTest {

    @TempDir
    Path workspace;

    @Test
    void rejectsFrontendConfigThatReferencesAMissingFile() throws IOException {
        Files.writeString(workspace.resolve("package.json"), "{}");
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/main.ts"), "console.log('ready');");
        Files.writeString(workspace.resolve("tsconfig.json"), """
                {"references":[{"path":"./tsconfig.node.json"}]}
                """);

        ProjectIntegrityVerifier.VerificationResult result = new ProjectIntegrityVerifier().verifyFrontend(workspace);

        assertFalse(result.success());
        assertTrue(result.summary().contains("tsconfig.node.json"));
    }

    @Test
    void rejectsBackendSourcesThatImportMissingProjectClasses() throws IOException {
        Path source = workspace.resolve("src/main/java/com/example/app/service/ArticleService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(workspace.resolve("pom.xml"), "<project></project>");
        Files.writeString(source, """
                package com.example.app.service;
                import com.example.app.entity.Article;
                class ArticleService { Article article; }
                """);

        ProjectIntegrityVerifier.VerificationResult result = new ProjectIntegrityVerifier().verifyBackend(workspace);

        assertFalse(result.success());
        assertTrue(result.summary().contains("com.example.app.entity.Article"));
    }
}
