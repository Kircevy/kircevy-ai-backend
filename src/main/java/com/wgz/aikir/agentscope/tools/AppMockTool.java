package com.wgz.aikir.agentscope.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AppMockTool {

    private final Map<Long, String> appStates = new ConcurrentHashMap<>();

    @Tool(name = "get_app_status",
            description = "Query application generation and deployment status by appId")
    public String getAppStatus(
            @ToolParam(name = "app_id", description = "Application ID to query") Long appId) {
        if (appId == null || appId <= 0) {
            return "{\"error\": \"invalid appId\"}";
        }
        String state = appStates.getOrDefault(appId, "GENERATED");
        return String.format("""
                {
                  "appId": %d,
                  "codeStatus": "%s",
                  "deployStatus": "NOT_DEPLOYED",
                  "message": "App is in %s state."
                }""", appId, state, state);
    }

    @Tool(name = "build_project",
            description = "Build the application project by appId. Only use when user explicitly asks to build.")
    public String buildProject(
            @ToolParam(name = "app_id", description = "Application ID to build") Long appId) {
        if (appId == null || appId <= 0) {
            return "{\"error\": \"invalid appId\"}";
        }
        appStates.put(appId, "BUILT");
        return String.format("""
                {
                  "appId": %d,
                  "buildStatus": "SUCCESS",
                  "buildTime": "12.5s",
                  "outputDir": "/tmp/build/%d"
                }""", appId, appId);
    }

    @Tool(name = "deploy_project",
            description = "Deploy the application project by appId. Only use when user explicitly asks to deploy. " +
                    "Can only deploy if the project has been built first.")
    public String deployProject(
            @ToolParam(name = "app_id", description = "Application ID to deploy") Long appId) {
        if (appId == null || appId <= 0) {
            return "{\"error\": \"invalid appId\"}";
        }
        String state = appStates.getOrDefault(appId, "GENERATED");
        if (!"BUILT".equals(state)) {
            return String.format("""
                    {
                      "appId": %d,
                      "deployStatus": "FAILED",
                      "reason": "Project must be built before deploying. Current state: %s"
                    }""", appId, state);
        }
        appStates.put(appId, "DEPLOYED");
        return String.format("""
                {
                  "appId": %d,
                  "deployStatus": "SUCCESS",
                  "deployUrl": "https://app-%d.example.com"
                }""", appId, appId);
    }
}
