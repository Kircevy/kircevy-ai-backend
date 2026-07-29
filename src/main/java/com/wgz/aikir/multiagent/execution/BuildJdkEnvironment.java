package com.wgz.aikir.multiagent.execution;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

/** Keeps Maven child processes on the same JDK as the running generation service. */
final class BuildJdkEnvironment {

    private final Path javaHome;

    BuildJdkEnvironment(Path javaHome) {
        this.javaHome = javaHome;
    }

    void apply(Map<String, String> environment) {
        environment.put("JAVA_HOME", javaHome.toString());
        String pathKey = environment.containsKey("Path") ? "Path" : "PATH";
        String currentPath = environment.getOrDefault(pathKey, "");
        environment.put(pathKey, javaHome.resolve("bin") + File.pathSeparator + currentPath);
    }
}
