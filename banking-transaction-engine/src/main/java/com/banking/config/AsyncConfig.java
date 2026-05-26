package com.banking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async executor configuration for @Async methods.
 * Used by the concurrent simulation service.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "bankingTaskExecutor")
    public Executor bankingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("BankingThread-");
        executor.initialize();
        return executor;
    }
}
