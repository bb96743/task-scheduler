package com.intematrix.scheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 调度配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "scheduler")
public class SchedulerProperties {

    private String nodeId = "node-1";
    private long scanIntervalMs = 1000;
    private long heartbeatIntervalMs = 5000;
    private long nodeTimeoutMs = 30000;
    private int compensationWindowDays = 7;
    private int executorPoolSize = 8;
    private long retryScanIntervalMs = 5000;
}
