package com.intematrix.scheduler.core;

import com.intematrix.scheduler.common.BizException;
import com.intematrix.scheduler.entity.SchedTask;
import com.intematrix.scheduler.entity.SchedTaskExecLog;
import com.intematrix.scheduler.entity.SchedTaskExecution;
import com.intematrix.scheduler.entity.SchedTaskShard;
import com.intematrix.scheduler.mapper.SchedTaskExecLogMapper;
import com.intematrix.scheduler.mapper.SchedTaskExecutionMapper;
import com.intematrix.scheduler.mapper.SchedTaskShardMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 执行器：把一个执行实例拆成分片并行跑，汇总结果，并触发重试/告警。
 */
@Slf4j
@Component
public class ExecutionRunner {

    private final SchedTaskExecutionMapper executionMapper;
    private final SchedTaskShardMapper shardMapper;
    private final SchedTaskExecLogMapper logMapper;
    private final HandlerRegistry handlerRegistry;
    private final RetryService retryService;
    private final AlertService alertService;
    private final ExecutorService dispatchExecutor;
    private final ExecutorService shardExecutor;

    public ExecutionRunner(SchedTaskExecutionMapper executionMapper,
                           SchedTaskShardMapper shardMapper,
                           SchedTaskExecLogMapper logMapper,
                           HandlerRegistry handlerRegistry,
                           RetryService retryService,
                           AlertService alertService,
                           @Qualifier("taskDispatchExecutor") ExecutorService dispatchExecutor,
                           @Qualifier("shardExecutor") ExecutorService shardExecutor) {
        this.executionMapper = executionMapper;
        this.shardMapper = shardMapper;
        this.logMapper = logMapper;
        this.handlerRegistry = handlerRegistry;
        this.retryService = retryService;
        this.alertService = alertService;
        this.dispatchExecutor = dispatchExecutor;
        this.shardExecutor = shardExecutor;
    }

    /**
     * 异步派发执行。若当前处于事务中，等提交后再派发，避免异步线程读不到未提交数据。
     */
    public void dispatch(SchedTask task, SchedTaskExecution execution) {
        Runnable job = () -> {
            try {
                run(task, execution);
            } catch (Exception e) {
                log.error("执行实例 {} 异常", execution.getId(), e);
            }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            dispatchExecutor.submit(job);
                        }
                    });
        } else {
            dispatchExecutor.submit(job);
        }
    }

    /**
     * 同步执行一个执行实例。
     */
    public void run(SchedTask task, SchedTaskExecution execution) {
        LocalDateTime startTime = LocalDateTime.now();
        int claimed = executionMapper.updateStatusToRunning(execution.getId(), startTime);
        if (claimed == 0) {
            log.warn("执行实例 {} 已被处理，跳过", execution.getId());
            return;
        }

        int shardTotal = task.getShardTotal() == null ? 1 : Math.max(1, task.getShardTotal());
        List<Future<ShardOutcome>> futures = new ArrayList<>(shardTotal);
        List<SchedTaskShard> shards = new ArrayList<>(shardTotal);

        for (int i = 0; i < shardTotal; i++) {
            SchedTaskShard shard = new SchedTaskShard();
            shard.setExecutionId(execution.getId());
            shard.setShardIndex(i);
            shard.setShardTotal(shardTotal);
            shard.setStatus("PENDING");
            shard.setCreateTime(LocalDateTime.now());
            shardMapper.insert(shard);
            shards.add(shard);

            final int index = i;
            futures.add(shardExecutor.submit(() -> runOneShard(task, execution,
                    shard.getId(), index, shardTotal)));
        }

        int success = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        long timeout = task.getTimeoutMs() == null ? 60000L : task.getTimeoutMs();

        for (int i = 0; i < futures.size(); i++) {
            ShardOutcome outcome;
            try {
                outcome = futures.get(i).get(timeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                futures.get(i).cancel(true);
                outcome = new ShardOutcome(shards.get(i).getId(), i, false, "分片执行超时");
            } catch (ExecutionException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                Throwable cause = e.getCause() == null ? e : e.getCause();
                outcome = new ShardOutcome(shards.get(i).getId(), i, false, "分片异常：" + cause.getMessage());
            }
            if (outcome.success()) {
                success++;
            } else {
                failed++;
                errors.add("分片" + outcome.shardIndex() + "：" + outcome.summary());
            }
        }

        LocalDateTime endTime = LocalDateTime.now();
        long duration = Math.max(1, Duration.between(startTime, endTime).toMillis());

        SchedTaskExecution toUpdate = new SchedTaskExecution();
        toUpdate.setId(execution.getId());
        toUpdate.setStatus(failed == 0 ? "SUCCESS" : "FAILED");
        toUpdate.setSuccessShard(success);
        toUpdate.setFailedShard(failed);
        toUpdate.setEndTime(endTime);
        toUpdate.setDurationMs(duration);
        if (failed > 0) {
            String msg = String.join(" | ", errors);
            toUpdate.setErrorMessage(msg.length() > 990 ? msg.substring(0, 990) : msg);
        }
        executionMapper.updateById(toUpdate);

        execution.setStatus(toUpdate.getStatus());
        execution.setEndTime(endTime);

        if (failed > 0) {
            boolean willRetry = retryService.scheduleRetry(task, execution, msgSafely(errors));
            // 只有终态失败（重试耗尽）才触发告警，避免中间重试噪音
            if (!willRetry) {
                alertService.onExecutionFailed(task, execution);
            }
        }
    }

    private String msgSafely(List<String> errors) {
        String msg = String.join(" | ", errors);
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }

    private ShardOutcome runOneShard(SchedTask task, SchedTaskExecution execution,
                                     Long shardId, int index, int total) {
        LocalDateTime shardStart = LocalDateTime.now();
        SchedTaskShard running = new SchedTaskShard();
        running.setId(shardId);
        running.setStatus("RUNNING");
        running.setStartTime(shardStart);
        shardMapper.updateById(running);

        ShardContext context = ShardContext.builder()
                .taskId(task.getId())
                .executionId(execution.getId())
                .taskName(task.getTaskName())
                .shardIndex(index)
                .shardTotal(total)
                .logSink((level, message) -> writeLog(execution.getId(), task.getId(), index, level, message))
                .build();

        boolean success;
        String summary;
        try {
            TaskHandler handler = handlerRegistry.resolve(task.getHandlerName());
            ShardResult result = handler.execute(context);
            success = result.isSuccess();
            summary = result.getSummary();
        } catch (BizException e) {
            success = false;
            summary = e.getMessage();
            writeLog(execution.getId(), task.getId(), index, "ERROR", "处理器异常：" + e.getMessage());
        } catch (Exception e) {
            success = false;
            summary = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            writeLog(execution.getId(), task.getId(), index, "ERROR", "执行异常：" + summary);
        }

        LocalDateTime shardEnd = LocalDateTime.now();
        SchedTaskShard done = new SchedTaskShard();
        done.setId(shardId);
        done.setStatus(success ? "SUCCESS" : "FAILED");
        done.setResultSummary(summary != null && summary.length() > 990 ? summary.substring(0, 990) : summary);
        done.setEndTime(shardEnd);
        done.setDurationMs(Math.max(0, Duration.between(shardStart, shardEnd).toMillis()));
        shardMapper.updateById(done);

        return new ShardOutcome(shardId, index, success, summary);
    }

    private void writeLog(Long executionId, Long taskId, int shardIndex, String level, String message) {
        SchedTaskExecLog logEntity = new SchedTaskExecLog();
        logEntity.setExecutionId(executionId);
        logEntity.setTaskId(taskId);
        logEntity.setShardIndex(shardIndex);
        logEntity.setLogLevel(level);
        logEntity.setMessage(message);
        logEntity.setCreateTime(LocalDateTime.now());
        logMapper.insert(logEntity);
    }

    public record ShardOutcome(Long shardId, int shardIndex, boolean success, String summary) {
    }
}
