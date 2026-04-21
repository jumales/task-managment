package com.demo.common.config;

import com.demo.common.web.MdcTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Shared async executor configuration with MDC propagation.
 * Centralises {@code @EnableAsync} so per-service application classes do not need it.
 * Named {@code "taskExecutor"} so Spring picks it up as the default {@code @Async} executor.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Thread pool with {@link MdcTaskDecorator} that copies the calling thread's MDC context
     * into each async thread before execution and clears it on completion.
     * Graceful shutdown: waits up to 10 s for queued tasks to finish before the context closes.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("async-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
