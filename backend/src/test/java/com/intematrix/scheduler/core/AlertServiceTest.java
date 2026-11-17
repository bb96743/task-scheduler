package com.intematrix.scheduler.core;

import com.intematrix.scheduler.entity.SchedAlertConfig;
import com.intematrix.scheduler.entity.SchedAlertRecord;
import com.intematrix.scheduler.entity.SchedTask;
import com.intematrix.scheduler.entity.SchedTaskExecution;
import com.intematrix.scheduler.mapper.SchedAlertConfigMapper;
import com.intematrix.scheduler.mapper.SchedAlertRecordMapper;
import com.intematrix.scheduler.mapper.SchedTaskExecutionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private SchedAlertConfigMapper alertConfigMapper;
    @Mock
    private SchedAlertRecordMapper alertRecordMapper;
    @Mock
    private SchedTaskExecutionMapper executionMapper;

    @InjectMocks
    private AlertService alertService;

    private SchedTask task() {
        SchedTask task = new SchedTask();
        task.setId(1L);
        task.setTaskName("t1");
        return task;
    }

    private SchedTaskExecution failedExec(long id) {
        SchedTaskExecution e = new SchedTaskExecution();
        e.setId(id);
        e.setTaskId(1L);
        e.setStatus("FAILED");
        return e;
    }

    private List<SchedTaskExecution> executions(String... statuses) {
        List<SchedTaskExecution> list = new ArrayList<>();
        long id = 100;
        for (String status : statuses) {
            SchedTaskExecution e = failedExec(id--);
            e.setStatus(status);
            list.add(e);
        }
        return list;
    }

    @Test
    void shouldAlertWhenConsecutiveFailuresReachThreshold() {
        when(alertConfigMapper.selectOne(any())).thenReturn(null);
        when(executionMapper.selectList(any())).thenReturn(executions("FAILED", "FAILED", "FAILED"));
        when(alertRecordMapper.selectCount(any())).thenReturn(0L);
        when(alertRecordMapper.insert(any(SchedAlertRecord.class))).thenReturn(1);

        alertService.onExecutionFailed(task(), failedExec(100));

        verify(alertRecordMapper).insert(any(SchedAlertRecord.class));
    }

    @Test
    void shouldSkipWhenFailuresBelowThreshold() {
        when(alertConfigMapper.selectOne(any())).thenReturn(null);
        when(executionMapper.selectList(any())).thenReturn(executions("FAILED", "SUCCESS", "FAILED"));

        alertService.onExecutionFailed(task(), failedExec(100));

        verify(alertRecordMapper, never()).insert(any(SchedAlertRecord.class));
    }

    @Test
    void shouldSkipWhenAlertConfigDisabled() {
        SchedAlertConfig config = new SchedAlertConfig();
        config.setTaskId(1L);
        config.setEnabled(0);
        when(alertConfigMapper.selectOne(any())).thenReturn(config);

        alertService.onExecutionFailed(task(), failedExec(100));

        verify(alertRecordMapper, never()).insert(any(SchedAlertRecord.class));
    }

    @Test
    void shouldDeduplicateRecentAlert() {
        when(alertConfigMapper.selectOne(any())).thenReturn(null);
        when(executionMapper.selectList(any())).thenReturn(executions("FAILED", "FAILED", "FAILED"));
        when(alertRecordMapper.selectCount(any())).thenReturn(1L);

        alertService.onExecutionFailed(task(), failedExec(100));

        verify(alertRecordMapper, never()).insert(any(SchedAlertRecord.class));
    }
}
