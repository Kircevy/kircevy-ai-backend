package com.wgz.aikir.multiagent.execution;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildJdkEnvironmentTest {

    @Test
    void usesTheRunningServiceJdkForMavenChildProcesses() {
        Path javaHome = Path.of("C:/runtime-jdk");
        Map<String, String> environment = new HashMap<>();
        environment.put("Path", "existing-path");

        new BuildJdkEnvironment(javaHome).apply(environment);

        assertEquals(javaHome.toString(), environment.get("JAVA_HOME"));
        assertEquals(javaHome.resolve("bin") + File.pathSeparator + "existing-path", environment.get("Path"));
    }
}
