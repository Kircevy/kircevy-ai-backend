package com.wgz.aikir.multiagent.config;

import com.wgz.aikir.multiagent.service.GenerationRunService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 服务重启会终止内存中的异步智能体线程；应用就绪后清理这类遗留运行记录，避免界面永久显示“正在规划”。
 */
@Slf4j
@Component
public class MultiAgentRunRecoveryListener {

    @Resource
    private GenerationRunService generationRunService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedRuns() {
        int recoveredCount = generationRunService.recoverInterruptedMultiAgentRuns();
        if (recoveredCount > 0) {
            log.warn("已将 {} 条因服务重启中断的协作规划标记为失败", recoveredCount);
        }
    }
}
