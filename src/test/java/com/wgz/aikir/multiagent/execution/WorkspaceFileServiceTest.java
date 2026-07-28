package com.wgz.aikir.multiagent.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgz.aikir.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkspaceFileServiceTest {

    @TempDir
    Path workspace;

    private final WorkspaceFileService service = new WorkspaceFileService(new ObjectMapper());

    @Test
    void acceptsASmallFileManifestAndWritesEachSourceFileIndependently() throws IOException {
        List<String> paths = service.parseFileManifest("""
                {"files":["package.json","src/main.ts"]}
                """);

        service.writeFile(workspace, paths.getFirst(), "{\"scripts\":{}}");
        service.writeFile(workspace, paths.get(1), "console.log('ready');");

        assertEquals(List.of("package.json", "src/main.ts"), paths);
        assertEquals("console.log('ready');", Files.readString(workspace.resolve("src/main.ts")));
    }

    @Test
    void rejectsDuplicateOrUnsafePathsBeforeAnySourceFileIsWritten() {
        assertThrows(BusinessException.class, () -> service.parseFileManifest("""
                {"files":["src/main.ts","../outside.ts"]}
                """));
        assertThrows(BusinessException.class, () -> service.parseFileManifest("""
                {"files":["src/main.ts","src/main.ts"]}
                """));
    }
}
