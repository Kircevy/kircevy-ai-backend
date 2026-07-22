package com.wgz.aikir.service;

import com.wgz.aikir.constant.AppConstant;
import com.wgz.aikir.core.builder.FullStackProjectBuilder;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Builds a full-stack frontend preview after frontend writes have been idle for a short time.
 */
@Slf4j
@Service
public class FrontendPreviewBuildService {

    private static final long BUILD_DEBOUNCE_SECONDS = 3;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "frontend-preview-builder");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<Long, ScheduledFuture<?>> pendingBuilds = new ConcurrentHashMap<>();

    private final Set<Long> pendingFirstPreviewScreenshots = ConcurrentHashMap.newKeySet();

    @Resource
    private FullStackProjectBuilder fullStackProjectBuilder;

    @Resource
    @Lazy
    private AppService appService;

    @Value("${server.port:8123}")
    private int serverPort;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    /**
     * Called once when a new full-stack generation starts. The screenshot is delayed until the
     * first successful frontend build, so it never blocks generation or preview rendering.
     */
    public void markGenerationStarted(Long appId) {
        pendingFirstPreviewScreenshots.add(appId);
    }

    public void scheduleBuild(Long appId) {
        ScheduledFuture<?> previousBuild = pendingBuilds.get(appId);
        if (previousBuild != null) {
            previousBuild.cancel(false);
        }
        ScheduledFuture<?> scheduledBuild = executor.schedule(() -> buildPreview(appId),
                BUILD_DEBOUNCE_SECONDS, TimeUnit.SECONDS);
        pendingBuilds.put(appId, scheduledBuild);
    }

    private void buildPreview(Long appId) {
        try {
            String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "fullstack_" + appId;
            if (fullStackProjectBuilder.buildFrontendPreview(projectPath)) {
                log.info("Frontend preview is ready for appId: {}", appId);
                if (pendingFirstPreviewScreenshots.remove(appId)) {
                    String previewUrl = String.format("http://127.0.0.1:%d%s/static/fullstack_%d/frontend/dist/index.html#/",
                            serverPort, contextPath, appId);
                    // generateScreenShotAsync starts a virtual thread; do not block this build executor.
                    appService.generateScreenShotAsync(appId, previewUrl);
                }
            }
        } catch (Exception exception) {
            // Later frontend writes automatically schedule a new build attempt.
            log.debug("Frontend preview build attempt failed for appId: {}", appId, exception);
        } finally {
            pendingBuilds.remove(appId);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
