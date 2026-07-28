package com.wgz.aikir.multiagent.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgz.aikir.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkspaceFileServiceTest {

    @TempDir
    Path temporaryDirectory;

    private final WorkspaceFileService workspaceFileService = new WorkspaceFileService(new ObjectMapper());

    @Test
    void shouldWriteOnlyRelativeFilesInsideWorkspace() throws Exception {
        var written = workspaceFileService.writeBundle(temporaryDirectory,
                "{\"files\":[{\"path\":\"src/main.ts\",\"content\":\"export {}\"}]}");

        assertEquals(java.util.List.of("src/main.ts"), written);
        assertEquals("export {}", Files.readString(temporaryDirectory.resolve("src/main.ts")));
    }

    @Test
    void shouldRejectPathTraversal() {
        assertThrows(BusinessException.class, () -> workspaceFileService.writeBundle(temporaryDirectory,
                "{\"files\":[{\"path\":\"../outside.txt\",\"content\":\"blocked\"}]}"));
    }

    @Test
    void shouldRejectDuplicatePathsAndUnexpectedFields() {
        assertThrows(BusinessException.class, () -> workspaceFileService.writeBundle(temporaryDirectory,
                "{\"files\":[{\"path\":\"src/a.ts\",\"content\":\"one\"},"
                        + "{\"path\":\"src/a.ts\",\"content\":\"two\"}]}"));
        assertThrows(BusinessException.class, () -> workspaceFileService.writeBundle(temporaryDirectory,
                "{\"files\":[],\"explanation\":\"not allowed\"}"));
    }
}
