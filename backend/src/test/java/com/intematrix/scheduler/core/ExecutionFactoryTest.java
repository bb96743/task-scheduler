package com.intematrix.scheduler.core;

import com.intematrix.scheduler.common.BizException;
import com.intematrix.scheduler.entity.SchedTask;
import com.intematrix.scheduler.entity.SchedTaskExecution;
import com.intematrix.scheduler.mapper.SchedTaskExecutionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionFactoryTest {

    @Mock
    private SchedTaskExecutionMapper executionMapper;

    @InjectMocks
    private ExecutionFactory factory;

    private SchedTask task() {
        SchedTask task = new SchedTask();
        task.setId(1L);
        task.setShardTotal(3);
        return task;
    }

    @Test
    void shouldCreateExecutionOnFirstTry() {
        when(executionMapper.insert(any(SchedTaskExecution.class))).thenReturn(1);

        SchedTaskExecution execution = factory.create(task(), "MANUAL", null);

        assertThat(execution.getTaskId()).isEqualTo(1L);
        assertThat(execution.getTriggerType()).isEqualTo("MANUAL");
        assertThat(execution.getStatus()).isEqualTo("RUNNING");
        assertThat(execution.getShardTotal()).isEqualTo(3);
    }

    @Test
    void shouldShiftFireTimeOnDuplicate() {
        when(executionMapper.insert(any(SchedTaskExecution.class)))
                .thenThrow(new DuplicateKeyException("dup"))
                .thenReturn(1);

        SchedTaskExecution execution = factory.create(task(), "RETRY", null, 2);

        assertThat(execution.getRetryCount()).isEqualTo(2);
        verify(executionMapper, times(2)).insert(any(SchedTaskExecution.class));
    }

    @Test
    void shouldFailAfterTwentyDuplicates() {
        when(executionMapper.insert(any(SchedTaskExecution.class)))
                .thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> factory.create(task(), "MANUAL", null))
                .isInstanceOf(BizException.class);
    }
}
