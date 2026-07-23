package com.wgz.aikir.multiagent.controller;

import com.wgz.aikir.common.BaseResponse;
import com.wgz.aikir.common.ResultUtils;
import com.wgz.aikir.multiagent.domain.entity.AgentEvent;
import com.wgz.aikir.multiagent.domain.entity.GenerationRun;
import com.wgz.aikir.model.entity.User;
import com.wgz.aikir.multiagent.service.GenerationRunService;
import com.wgz.aikir.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import reactor.core.publisher.Flux;

import java.time.Duration;

/** M0 阶段的只读接口：用于后续协作时间线和事件重放。 */
@RestController
@RequestMapping("/agent/runs")
public class AgentRunController {

    @Resource
    private GenerationRunService generationRunService;

    @Resource
    private UserService userService;

    @GetMapping("/app/{appId}/latest")
    public BaseResponse<GenerationRun> getLatestRun(@PathVariable Long appId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(generationRunService.getLatestRunForOwner(appId, loginUser));
    }

    @GetMapping("/{runId}")
    public BaseResponse<GenerationRun> getRun(@PathVariable String runId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(generationRunService.getRunForOwner(runId, loginUser));
    }

    @GetMapping("/{runId}/events")
    public BaseResponse<List<AgentEvent>> listEvents(@PathVariable String runId,
                                                      @RequestParam(required = false) Long afterSequence,
                                                      HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(generationRunService.listEventsForOwner(runId, afterSequence, loginUser));
    }

    /**
     * 先重放已持久化的事件，再轮询新增事件，直至收到运行结束事件。
     * M0 阶段刻意与现有代码生成令牌 SSE 流分离，避免影响一阶段生成流程。
     */
    @GetMapping(value = "/{runId}/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentEvent>> streamEvents(@PathVariable String runId,
                                                           @RequestParam(required = false) Long afterSequence,
                                                           HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        generationRunService.getRunForOwner(runId, loginUser);
        AtomicLong cursor = new AtomicLong(afterSequence == null ? 0L : Math.max(0L, afterSequence));
        return Flux.interval(Duration.ZERO, Duration.ofSeconds(1))
                .concatMap(ignored -> Flux.fromIterable(
                        generationRunService.listEventsForOwner(runId, cursor.get(), loginUser)))
                .doOnNext(event -> cursor.set(event.getEventSeq()))
                .map(event -> ServerSentEvent.builder(event)
                        .id(String.valueOf(event.getEventSeq()))
                        .event(event.getEventType())
                        .build())
                .takeUntil(event -> "RUN_SUCCEEDED".equals(event.event()) || "RUN_FAILED".equals(event.event()));
    }
}
