package com.intematrix.scheduler.service;

import com.intematrix.scheduler.common.BizException;
import com.intematrix.scheduler.config.SchedulerProperties;
import com.intematrix.scheduler.core.ExecutionFactory;
import com.intematrix.scheduler.core.ExecutionRunner;
import com.intematrix.scheduler.entity.SchedTask;
import com.intematrix.scheduler.entity.SchedTaskExecution;
import com.intematrix.scheduler.mapper.SchedTaskExecLogMapper;
import com.intematrix.scheduler.mapper.SchedTaskExecutionMapper;
import com.intematrix.scheduler.mapper.SchedTaskMapper;
import com.intematrix.scheduler.mapper.SchedTaskShardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceTest {

    @Mock
    private SchedTaskExecutionMapper executionMapper;
    @Mock
    private SchedTaskExecLogMapper logMapper;
    @Mock
    private SchedTaskShardMapper shardMapper;
    @Mock
    private SchedTaskMapper taskMapper;
    @Mock
    private ExecutionFactory executionFactory;
    @Mock
    private ExecutionRunner executionRunner;

    private SchedulerProperties properties;
    private ExecutionService executionService;

    @BeforeEach
    void setUp() {
        properties = new SchedulerProperties();
        properties.setCompensationWindowDays(7);
        executionService = new ExecutionService(executionMapper, logMapper, shardMapper,
                taskMapper, executionFactory, executionRunner, properties);
    }

    private SchedTaskExecution exec(String status, LocalDateTime createTime) {
        SchedTaskExecution e = new SchedTaskExecution();
        e.setId(1L);
        e.setTaskId(2L);
        e.setStatus(status);
        e.setCreateTime(createTime);
        return e;
    }

    @Test
    void compensateShouldRejectNonFailedExecution() {
        when(executionMapper.selectById(1L)).thenReturn(exec("SUCCESS", LocalDateTime.now()));
        assertThatThrownBy(() -> executionService.compensate(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅失败终态");
    }

    @Test
    void compensateShouldRejectOutOfWindow() {
        when(executionMapper.selectById(1L)).thenReturn(exec("FAILED", LocalDateTime.now().minusDays(10)));
        assertThatThrownBy(() -> executionService.compensate(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("时间窗口");
    }

    @Test
    void compensateShouldRejectDuplicateCompensation() {
        when(executionMapper.selectById(1L)).thenReturn(exec("FAILED", LocalDateTime.now().minusMinutes(1)));
        when(executionMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> executionService.compensate(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已补偿");
    }

    @Test
    void compensateShouldCreateNewExecution() {
        SchedTaskExecution source = exec("FAILED", LocalDateTime.now().minusMinutes(1));
        when(executionMapper.selectById(1L)).thenReturn(source);
        when(executionMapper.selectCount(any())).thenReturn(0L);

        SchedTask task = new SchedTask();
        task.setId(2L);
        task.setTaskName("t2");
        when(taskMapper.selectById(2L)).thenReturn(task);

        SchedTaskExecution created = exec("RUNNING", LocalDateTime.now());
        created.setId(99L);
        when(executionFactory.create(eq(task), eq("COMPENSATE"), eq(1L))).thenReturn(created);

        SchedTaskExecution result = executionService.compensate(1L);

        assertThat(result.getId()).isEqualTo(99L);
        verify(executionRunner).dispatch(task, created);
    }

    @Test
    void retryShouldRejectRunningExecution() {
        when(executionMapper.selectById(1L)).thenReturn(exec("RUNNING", LocalDateTime.now()));
        assertThatThrownBy(() -> executionService.retry(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("执行中");
        verify(executionFactory, never()).create(any(), any(), any());
    }

    @Test
    void retryShouldCreateAndDispatch() {
        SchedTaskExecution source = exec("FAILED", LocalDateTime.now());
        when(executionMapper.selectById(1L)).thenReturn(source);

        SchedTask task = new SchedTask();
        task.setId(2L);
        task.setTaskName("t2");
        when(taskMapper.selectById(2L)).thenReturn(task);

        SchedTaskExecution created = exec("RUNNING", LocalDateTime.now());
        created.setId(50L);
        when(executionFactory.create(eq(task), eq("RETRY"), any())).thenReturn(created);

        SchedTaskExecution result = executionService.retry(1L);

        assertThat(result.getId()).isEqualTo(50L);
        verify(executionRunner).dispatch(task, created);
    }

    @Test
    void getByIdShouldThrowWhenMissing() {
        when(executionMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> executionService.getById(404L)).isInstanceOf(BizException.class);
    }

    @Test
    void shouldListExecutionsByTask() {
        when(executionMapper.selectList(any())).thenReturn(java.util.List.of(exec("SUCCESS", LocalDateTime.now())));
        assertThat(executionService.listByTask(2L, null, null, null)).hasSize(1);
    }

    @Test
    void shouldListLogs() {
        when(executionMapper.selectById(1L)).thenReturn(exec("SUCCESS", LocalDateTime.now()));
        when(logMapper.selectList(any())).thenReturn(java.util.List.of(new com.intematrix.scheduler.entity.SchedTaskExecLog()));
        assertThat(executionService.logs(1L, "INFO", null, null)).hasSize(1);
    }

    @Test
    void shouldListShards() {
        when(executionMapper.selectById(1L)).thenReturn(exec("SUCCESS", LocalDateTime.now()));
        when(shardMapper.selectList(any())).thenReturn(java.util.List.of(new com.intematrix.scheduler.entity.SchedTaskShard()));
        assertThat(executionService.shards(1L)).hasSize(1);
    }

    @Test
    void shouldThrowWhenTaskMissingOnCompensate() {
        when(executionMapper.selectById(1L)).thenReturn(exec("FAILED", LocalDateTime.now().minusMinutes(1)));
        when(executionMapper.selectCount(any())).thenReturn(0L);
        when(taskMapper.selectById(2L)).thenReturn(null);
        assertThatThrownBy(() -> executionService.compensate(1L)).isInstanceOf(BizException.class);
    }
}
