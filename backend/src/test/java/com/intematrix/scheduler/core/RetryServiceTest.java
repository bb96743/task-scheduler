package com.intematrix.scheduler.core;

import com.intematrix.scheduler.entity.SchedRetryRecord;
import com.intematrix.scheduler.entity.SchedTask;
import com.intematrix.scheduler.entity.SchedTaskExecution;
import com.intematrix.scheduler.mapper.SchedRetryRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryServiceTest {

    @Mock
    private SchedRetryRecordMapper retryRecordMapper;

    @InjectMocks
    private RetryService retryService;

    private SchedTask task(int retryMax, int interval) {
        SchedTask task = new SchedTask();
        task.setId(1L);
        task.setTaskName("t1");
        task.setRetryMax(retryMax);
        task.setRetryInterval(interval);
        return task;
    }

    private SchedTaskExecution execution(int retryCount) {
        SchedTaskExecution e = new SchedTaskExecution();
        e.setId(10L);
        e.setTaskId(1L);
        e.setRetryCount(retryCount);
        return e;
    }

    @Test
    void shouldNotRetryWhenMaxIsZero() {
        boolean willRetry = retryService.scheduleRetry(task(0, 5), execution(0), "boom");
        assertThat(willRetry).isFalse();
        verify(retryRecordMapper, never()).insert(any(SchedRetryRecord.class));
    }

    @Test
    void shouldScheduleFirstRetry() {
        when(retryRecordMapper.insert(any(SchedRetryRecord.class))).thenReturn(1);
        boolean willRetry = retryService.scheduleRetry(task(2, 5), execution(0), "boom");

        ArgumentCaptor<SchedRetryRecord> captor = ArgumentCaptor.forClass(SchedRetryRecord.class);
        verify(retryRecordMapper).insert(captor.capture());
        SchedRetryRecord saved = captor.getValue();

        assertThat(willRetry).isTrue();
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getNextRetryTime()).isAfter(java.time.LocalDateTime.now().plusSeconds(3));
    }

    @Test
    void shouldUseExponentialBackoff() {
        when(retryRecordMapper.insert(any(SchedRetryRecord.class))).thenReturn(1);
        retryService.scheduleRetry(task(5, 10), execution(2), "boom");

        ArgumentCaptor<SchedRetryRecord> captor = ArgumentCaptor.forClass(SchedRetryRecord.class);
        verify(retryRecordMapper).insert(captor.capture());
        // 第 3 次重试：10 * 2^(3-1) = 40 秒
        assertThat(captor.getValue().getNextRetryTime())
                .isAfter(java.time.LocalDateTime.now().plusSeconds(35));
    }

    @Test
    void shouldStopWhenExceedsMax() {
        boolean willRetry = retryService.scheduleRetry(task(2, 5), execution(2), "boom");
        assertThat(willRetry).isFalse();
        verify(retryRecordMapper, never()).insert(any(SchedRetryRecord.class));
    }
}
