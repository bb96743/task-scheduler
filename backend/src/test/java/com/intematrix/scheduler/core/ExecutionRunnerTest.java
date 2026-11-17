package com.intematrix.scheduler.core;

import com.intematrix.scheduler.entity.SchedTask;
import com.intematrix.scheduler.entity.SchedTaskExecution;
import com.intematrix.scheduler.mapper.SchedTaskExecLogMapper;
import com.intematrix.scheduler.mapper.SchedTaskExecutionMapper;
import com.intematrix.scheduler.mapper.SchedTaskShardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionRunnerTest {

    @Mock
    private SchedTaskExecutionMapper executionMapper;
    @Mock
    private SchedTaskShardMapper shardMapper;
    @Mock
    private SchedTaskExecLogMapper logMapper;
    @Mock
    private HandlerRegistry handlerRegistry;
    @Mock
    private RetryService retryService;
    @Mock
    private AlertService alertService;

    private ExecutionRunner runner;

    private static ExecutorService direct() {
        return new AbstractExecutorService() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }

            @Override
            public void shutdown() {
            }

            @Override
            public List<Runnable> shutdownNow() {
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return true;
            }
        };
    }

    @BeforeEach
    void setUp() {
        runner = new ExecutionRunner(executionMapper, shardMapper, logMapper,
                handlerRegistry, retryService, alertService, direct(), direct());
    }

    private SchedTask task(int shards) {
        SchedTask task = new SchedTask();
        task.setId(1L);
        task.setTaskName("t1");
        task.setHandlerName("h1");
        task.setShardTotal(shards);
        task.setRetryMax(2);
        task.setTimeoutMs(5000);
        return task;
    }

    private SchedTaskExecution execution() {
        SchedTaskExecution e = new SchedTaskExecution();
        e.setId(10L);
        e.setTaskId(1L);
        e.setStatus("RUNNING");
        return e;
    }

    @Test
    void shouldSkipWhenAlreadyClaimed() {
        when(executionMapper.updateStatusToRunning(anyLong(), any())).thenReturn(0);
        runner.run(task(1), execution());
        verify(shardMapper, never()).insert(any(com.intematrix.scheduler.entity.SchedTaskShard.class));
    }

    @Test
    void shouldMarkSuccessWhenAllShardsSucceed() {
        when(executionMapper.updateStatusToRunning(anyLong(), any())).thenReturn(1);
        when(shardMapper.insert(any(com.intematrix.scheduler.entity.SchedTaskShard.class))).thenReturn(1);
        when(handlerRegistry.resolve("h1")).thenReturn(ctx -> ShardResult.ok("done"));

        runner.run(task(2), execution());

        ArgumentCaptor<SchedTaskExecution> captor = ArgumentCaptor.forClass(SchedTaskExecution.class);
        verify(executionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(captor.getValue().getSuccessShard()).isEqualTo(2);
        verify(retryService, never()).scheduleRetry(any(), any(), any());
    }

    @Test
    void shouldRetryWhenShardFails() {
        when(executionMapper.updateStatusToRunning(anyLong(), any())).thenReturn(1);
        when(shardMapper.insert(any(com.intematrix.scheduler.entity.SchedTaskShard.class))).thenReturn(1);
        when(handlerRegistry.resolve("h1")).thenReturn(ctx -> {
            throw new IllegalStateException("boom");
        });
        when(retryService.scheduleRetry(any(), any(), any())).thenReturn(true);

        runner.run(task(1), execution());

        ArgumentCaptor<SchedTaskExecution> captor = ArgumentCaptor.forClass(SchedTaskExecution.class);
        verify(executionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getFailedShard()).isEqualTo(1);
        verify(alertService, never()).onExecutionFailed(any(), any());
    }

    @Test
    void shouldAlertWhenRetryExhausted() {
        when(executionMapper.updateStatusToRunning(anyLong(), any())).thenReturn(1);
        when(shardMapper.insert(any(com.intematrix.scheduler.entity.SchedTaskShard.class))).thenReturn(1);
        when(handlerRegistry.resolve("h1")).thenReturn(ctx -> {
            throw new IllegalStateException("boom");
        });
        when(retryService.scheduleRetry(any(), any(), any())).thenReturn(false);

        runner.run(task(1), execution());

        verify(alertService).onExecutionFailed(any(), any());
    }

    @Test
    void shouldCaptureShardFailureSummary() {
        when(executionMapper.updateStatusToRunning(anyLong(), any())).thenReturn(1);
        when(shardMapper.insert(any(com.intematrix.scheduler.entity.SchedTaskShard.class))).thenReturn(1);
        when(handlerRegistry.resolve("h1")).thenReturn(ctx -> ShardResult.fail("业务校验不通过"));
        when(retryService.scheduleRetry(any(), any(), any())).thenReturn(false);

        runner.run(task(1), execution());

        ArgumentCaptor<SchedTaskExecution> captor = ArgumentCaptor.forClass(SchedTaskExecution.class);
        verify(executionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getErrorMessage()).contains("业务校验不通过");
    }

    @Test
    void dispatchShouldRunJobWhenNoTransaction() {
        when(executionMapper.updateStatusToRunning(anyLong(), any())).thenReturn(1);
        when(shardMapper.insert(any(com.intematrix.scheduler.entity.SchedTaskShard.class))).thenReturn(1);
        when(handlerRegistry.resolve("h1")).thenReturn(ctx -> ShardResult.ok("ok"));

        runner.dispatch(task(1), execution());

        verify(executionMapper).updateById(any(SchedTaskExecution.class));
    }

    @Test
    void shouldHandleHandlerResolveFailure() {
        when(executionMapper.updateStatusToRunning(anyLong(), any())).thenReturn(1);
        when(shardMapper.insert(any(com.intematrix.scheduler.entity.SchedTaskShard.class))).thenReturn(1);
        when(handlerRegistry.resolve("h1")).thenThrow(new com.intematrix.scheduler.common.BizException(
                com.intematrix.scheduler.common.ErrorCode.HANDLER_NOT_FOUND));
        when(retryService.scheduleRetry(any(), any(), any())).thenReturn(false);

        runner.run(task(1), execution());

        ArgumentCaptor<SchedTaskExecution> captor = ArgumentCaptor.forClass(SchedTaskExecution.class);
        verify(executionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
    }
}
