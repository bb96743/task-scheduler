package com.intematrix.scheduler.core;

import com.intematrix.scheduler.config.SchedulerProperties;
import com.intematrix.scheduler.entity.SchedTask;
import com.intematrix.scheduler.entity.SchedTaskExecution;
import com.intematrix.scheduler.entity.SchedTaskFire;
import com.intematrix.scheduler.mapper.SchedTaskExecutionMapper;
import com.intematrix.scheduler.mapper.SchedTaskFireMapper;
import com.intematrix.scheduler.mapper.SchedTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulerEngineTest {

    @Mock
    private SchedTaskMapper taskMapper;
    @Mock
    private SchedTaskFireMapper fireMapper;
    @Mock
    private SchedTaskExecutionMapper executionMapper;
    @Mock
    private ExecutionRunner executionRunner;

    private SchedulerProperties properties;
    private SchedulerEngine engine;

    @BeforeEach
    void setUp() {
        properties = new SchedulerProperties();
        properties.setNodeId("node-test");
        engine = new SchedulerEngine(taskMapper, fireMapper, executionMapper, executionRunner, properties);
    }

    private SchedTask dueTask() {
        SchedTask task = new SchedTask();
        task.setId(1L);
        task.setTaskName("t1");
        task.setCronExpression("0/30 * * * * ?");
        task.setStatus("ENABLED");
        task.setShardTotal(1);
        task.setVersion(0);
        task.setNextFireTime(LocalDateTime.now().minusSeconds(1));
        return task;
    }

    @Test
    void scanShouldFireAndDispatchWhenClaimSucceeds() {
        when(taskMapper.selectList(any())).thenReturn(List.of(dueTask()));
        when(fireMapper.insert(any(SchedTaskFire.class))).thenReturn(1);
        when(taskMapper.updateById(any(SchedTask.class))).thenReturn(1);
        when(executionMapper.insert(any(SchedTaskExecution.class))).thenReturn(1);

        engine.scan();

        verify(executionMapper).insert(any(SchedTaskExecution.class));
        verify(executionRunner).dispatch(any(), any());
    }

    @Test
    void scanShouldSkipWhenAnotherNodeWonTheFireClaim() {
        when(taskMapper.selectList(any())).thenReturn(List.of(dueTask()));
        when(fireMapper.insert(any(SchedTaskFire.class))).thenThrow(new DuplicateKeyException("uk_task_fire"));

        engine.scan();

        verify(executionMapper, never()).insert(any(SchedTaskExecution.class));
        verify(executionRunner, never()).dispatch(any(), any());
    }

    @Test
    void scanShouldNotDispatchWhenExecutionAlreadyExists() {
        when(taskMapper.selectList(any())).thenReturn(List.of(dueTask()));
        when(fireMapper.insert(any(SchedTaskFire.class))).thenReturn(1);
        when(taskMapper.updateById(any(SchedTask.class))).thenReturn(1);
        when(executionMapper.insert(any(SchedTaskExecution.class)))
                .thenThrow(new DuplicateKeyException("uk_exec_task_fire"));

        engine.scan();

        verify(executionRunner, never()).dispatch(any(), any());
    }

    @Test
    void scanShouldSwallowPerTaskExceptions() {
        when(taskMapper.selectList(any())).thenReturn(List.of(dueTask()));
        when(fireMapper.insert(any(SchedTaskFire.class))).thenThrow(new RuntimeException("db down"));

        engine.scan();

        verify(executionRunner, never()).dispatch(any(), any());
    }

    @Test
    void initShouldFillMissingNextFireTime() {
        SchedTask task = dueTask();
        task.setNextFireTime(null);
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(taskMapper.updateById(any(SchedTask.class))).thenReturn(1);

        engine.initNextFireTime();

        ArgumentCaptor<SchedTask> captor = ArgumentCaptor.forClass(SchedTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getNextFireTime()).isNotNull();
    }

    @Test
    void initShouldSkipInvalidCron() {
        SchedTask task = dueTask();
        task.setNextFireTime(null);
        task.setCronExpression("bad cron");
        when(taskMapper.selectList(any())).thenReturn(List.of(task));

        engine.initNextFireTime();

        verify(taskMapper, never()).updateById(any(SchedTask.class));
    }
}
