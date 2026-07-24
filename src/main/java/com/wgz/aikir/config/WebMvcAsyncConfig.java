package com.wgz.aikir.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 为代码生成 SSE 请求配置专用异步线程池和超时，避免阻塞 MVC 请求线程。 */
@Configuration
public class WebMvcAsyncConfig implements WebMvcConfigurer {

    private final AsyncTaskExecutor mvcStreamingTaskExecutor;

    public WebMvcAsyncConfig(@Qualifier("mvcStreamingTaskExecutor") AsyncTaskExecutor mvcStreamingTaskExecutor) {
        this.mvcStreamingTaskExecutor = mvcStreamingTaskExecutor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcStreamingTaskExecutor);
        configurer.setDefaultTimeout(600_000L);
    }
}
