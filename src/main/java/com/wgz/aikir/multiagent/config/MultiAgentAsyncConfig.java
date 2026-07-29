package com.wgz.aikir.multiagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** 为协作规划提供受控的异步执行线程池。 */
@EnableAsync
@Configuration
public class MultiAgentAsyncConfig {

    @Bean("multiAgentPlanningExecutor")
    public Executor multiAgentPlanningExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("multi-agent-planning-");
        executor.initialize();
        return executor;
    }

    /** M2 前端与后端任务在该线程池中并行，避免占用 MVC 请求线程。 */
    @Bean("multiAgentExecutionExecutor")
    public Executor multiAgentExecutionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("multi-agent-execution-");
        executor.initialize();
        return executor;
    }

    /** M2 协调任务与前后端子任务必须使用不同线程池，避免父任务等待子任务时耗尽工作线程。 */
    @Bean("multiAgentCoordinatorExecutor")
    public Executor multiAgentCoordinatorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("multi-agent-coordinator-");
        executor.initialize();
        return executor;
    }

    /** 为 SSE 等 MVC 异步响应提供受控线程池，避免使用默认的临时线程执行器。 */
    @Bean("mvcStreamingTaskExecutor")
    public AsyncTaskExecutor mvcStreamingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mvc-streaming-");
        executor.initialize();
        return executor;
    }
}
