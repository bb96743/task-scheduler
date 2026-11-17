package com.intematrix.scheduler.core;

import lombok.Builder;
import lombok.Data;

/**
 * 分片执行上下文。传给 TaskHandler 的运行时信息。
 */
@Data
@Builder
public class ShardContext {

    private Long taskId;
    private Long executionId;
    private String taskName;
    private int shardIndex;
    private int shardTotal;

    /** 执行日志回调，实现方保证写入 sched_task_exec_log。 */
    private transient LogSink logSink;

    public void log(String level, String message) {
        if (logSink != null) {
            logSink.log(level, message);
        }
    }

    public void info(String message) {
        log("INFO", message);
    }

    public void error(String message) {
        log("ERROR", message);
    }

    /**
     * 日志写入函数式接口。
     */
    @FunctionalInterface
    public interface LogSink {
        void log(String level, String message);
    }
}
