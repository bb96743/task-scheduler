package com.intematrix.scheduler.core;

import com.intematrix.scheduler.entity.SchedRetryRecord;
import com.intematrix.scheduler.entity.SchedTask;
import com.intematrix.scheduler.entity.SchedTaskExecution;
import com.intematrix.scheduler.mapper.SchedTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryScannerTest {

    @Mock
    private RetryService retryService;
    @Mock
    private SchedTaskMapper taskMapper;
    @Mock
    private ExecutionFactory executionFactory;
    @Mock
    private ExecutionRunner executionRunner;

    @InjectMocks
    private RetryScanner scanner;

    private SchedRetryRecord record() {
        SchedRetryRecord r = new SchedRetryRecord();
        r.setId(1L);
        r.setTaskId(2L);
        r.setRetryCount(1);
        r.setNextRetryTime(LocalDateTime.now().minusSeconds(1));
        r.setStatus("PENDING");
        return r;
    }

    @Test
    void shouldDispatchDueRetry() {
        when(retryService.findDue(20)).thenReturn(List.of(record()));
        when(retryService.claim(1L)).thenReturn(true);
        SchedTask task = new SchedTask();
        task.setId(2L);
        task.setTaskName("t2");
        when(taskMapper.selectById(2L)).thenReturn(task);
        SchedTaskExecution exec = new SchedTaskExecution();
        exec.setId(9L);
        when(executionFactory.create(task, "RETRY", null, 1)).thenReturn(exec);

        scanner.scan();

        verify(executionRunner).dispatch(task, exec);
    }

    @Test
    void shouldSkipWhenClaimFails() {
        when(retryService.findDue(20)).thenReturn(List.of(record()));
        when(retryService.claim(1L)).thenReturn(false);

        scanner.scan();

        verify(executionFactory, never()).create(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
        verify(executionRunner, never()).dispatch(any(), any());
    }

    @Test
    void shouldSkipWhenTaskMissing() {
        when(retryService.findDue(20)).thenReturn(List.of(record()));
        when(retryService.claim(1L)).thenReturn(true);
        when(taskMapper.selectById(2L)).thenReturn(null);

        scanner.scan();

        verify(executionRunner, never()).dispatch(any(), any());
    }
}
