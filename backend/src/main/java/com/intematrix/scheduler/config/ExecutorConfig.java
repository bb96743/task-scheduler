package com.intematrix.scheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池配置。执行级与分片级分开，避免同池提交+等待造成死锁。
 */
@Configuration
public class ExecutorConfig {

    @Bean(name = "taskDispatchExecutor", destroyMethod = "shutdown")
    public ExecutorService taskDispatchExecutor(SchedulerProperties properties) {
        return Executors.newFixedThreadPool(Math.max(2, properties.getExecutorPoolSize() / 2),
                namedFactory("task-dispatch-"));
    }

    @Bean(name = "shardExecutor", destroyMethod = "shutdown")
    public ExecutorService shardExecutor(SchedulerProperties properties) {
        return Executors.newFixedThreadPool(Math.max(4, properties.getExecutorPoolSize()),
                namedFactory("shard-"));
    }

    private ThreadFactory namedFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + seq.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
