package com.neulbom.backend.analysis;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class CistClipPrefetchConfiguration {
    @Bean("cistClipPrefetchExecutor")
    public Executor cistClipPrefetchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(32);
        // A full queue may drop prefetch; the final analysis computes cache misses.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setThreadNamePrefix("cist-clip-prefetch-");
        executor.initialize();
        return executor;
    }
}
