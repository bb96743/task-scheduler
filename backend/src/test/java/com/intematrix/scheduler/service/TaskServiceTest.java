package com.intematrix.scheduler.service;

import com.intematrix.scheduler.common.BizException;
import com.intematrix.scheduler.core.ExecutionFactory;
import com.intematrix.scheduler.core.ExecutionRunner;
import com.intematrix.scheduler.core.HandlerRegistry;
import com.intematrix.scheduler.dto.TaskCreateRequest;
import com.intematrix.scheduler.entity.SchedTask;
import com.intematrix.scheduler.mapper.SchedAlertConfigMapper;
import com.intematrix.scheduler.mapper.SchedTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private SchedTaskMapper taskMapper;
    @Mock
    private SchedAlertConfigMapper alertConfigMapper;
    @Mock
    private HandlerRegistry handlerRegistry;
    @Mock
    private ExecutionFactory executionFactory;
    @Mock
    private ExecutionRunner executionRunner;

    @InjectMocks
    private TaskService taskService;

    private TaskCreateRequest request() {
        TaskCreateRequest req = new TaskCreateRequest();
        req.setTaskName("new-task");
        req.setCronExpression("0/30 * * * * ?");
        req.setHandlerName("demoSuccessHandler");
        req.setShardTotal(2);
        req.setRetryMax(1);
        req.setRetryInterval(5);
        return req;
    }

    @Test
    void shouldRejectInvalidCron() {
        TaskCreateRequest req = request();
        req.setCronExpression("bad cron");
        assertThatThrownBy(() -> taskService.create(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("cron");
        verify(taskMapper, never()).insert(any(SchedTask.class));
    }

    @Test
    void shouldRejectUnknownHandler() {
        when(handlerRegistry.exists("demoSuccessHandler")).thenReturn(false);
        assertThatThrownBy(() -> taskService.create(request()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("处理器");
    }

    @Test
    void shouldRejectDuplicateName() {
        when(handlerRegistry.exists("demoSuccessHandler")).thenReturn(true);
        when(taskMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> taskService.create(request()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("任务名已存在");
    }

    @Test
    void shouldCreateTaskWithNextFireTime() {
        when(handlerRegistry.exists("demoSuccessHandler")).thenReturn(true);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        when(taskMapper.insert(any(SchedTask.class))).thenReturn(1);

        SchedTask created = taskService.create(request());

        assertThat(created.getTaskName()).isEqualTo("new-task");
        assertThat(created.getStatus()).isEqualTo("ENABLED");
        assertThat(created.getNextFireTime()).isNotNull();
        assertThat(created.getShardTotal()).isEqualTo(2);
    }

    @Test
    void getByIdShouldThrowWhenMissing() {
        when(taskMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> taskService.getById(9L)).isInstanceOf(BizException.class);
    }

    @Test
    void shouldListTasks() {
        when(taskMapper.selectList(any())).thenReturn(java.util.List.of(new SchedTask()));
        assertThat(taskService.list()).hasSize(1);
    }

    @Test
    void runNowShouldDispatchWhenEnabled() {
        SchedTask task = new SchedTask();
        task.setId(1L);
        task.setTaskName("t1");
        task.setStatus("ENABLED");
        task.setHandlerName("demoSuccessHandler");
        com.intematrix.scheduler.entity.SchedTaskExecution execution =
                new com.intematrix.scheduler.entity.SchedTaskExecution();
        execution.setId(88L);
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(handlerRegistry.exists("demoSuccessHandler")).thenReturn(true);
        when(executionFactory.create(any(SchedTask.class), any(), any())).thenReturn(execution);

        assertThat(taskService.runNow(1L).getId()).isEqualTo(88L);
        verify(executionRunner).dispatch(task, execution);
    }

    @Test
    void runNowShouldRejectDisabledTask() {
        SchedTask task = new SchedTask();
        task.setId(1L);
        task.setTaskName("t1");
        task.setStatus("DISABLED");
        task.setHandlerName("demoSuccessHandler");
        when(taskMapper.selectById(1L)).thenReturn(task);

        assertThatThrownBy(() -> taskService.runNow(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("停用");
        verify(executionFactory, never()).create(any(), any(), any());
    }

    @Test
    void shouldRejectInvalidStatus() {
        assertThatThrownBy(() -> taskService.updateStatus(1L, "WHATEVER"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("ENABLED");
    }

    @Test
    void disableShouldClearNextFireTime() {
        SchedTask task = new SchedTask();
        task.setId(1L);
        task.setCronExpression("0/30 * * * * ?");
        task.setVersion(0);
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.updateById(any(SchedTask.class))).thenReturn(1);

        taskService.updateStatus(1L, "DISABLED");

        org.mockito.ArgumentCaptor<SchedTask> captor = org.mockito.ArgumentCaptor.forClass(SchedTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getNextFireTime()).isNull();
    }

    @Test
    void enableShouldSetNextFireTime() {
        SchedTask task = new SchedTask();
        task.setId(1L);
        task.setCronExpression("0/30 * * * * ?");
        task.setVersion(0);
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.updateById(any(SchedTask.class))).thenReturn(1);

        taskService.updateStatus(1L, "ENABLED");

        org.mockito.ArgumentCaptor<SchedTask> captor = org.mockito.ArgumentCaptor.forClass(SchedTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getNextFireTime()).isNotNull();
    }

    @Test
    void shouldUpdateShardConfig() {
        SchedTask task = new SchedTask();
        task.setId(1L);
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.updateById(any(SchedTask.class))).thenReturn(1);

        taskService.updateShard(1L, 8);

        org.mockito.ArgumentCaptor<SchedTask> captor = org.mockito.ArgumentCaptor.forClass(SchedTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getShardTotal()).isEqualTo(8);
    }

    @Test
    void shouldInsertAlertConfigWhenAbsent() {
        SchedTask task = new SchedTask();
        task.setId(1L);
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(alertConfigMapper.selectOne(any())).thenReturn(null);

        com.intematrix.scheduler.dto.AlertConfigRequest req =
                new com.intematrix.scheduler.dto.AlertConfigRequest();
        req.setFailThreshold(5);
        taskService.updateAlert(1L, req);

        verify(alertConfigMapper).insert(any(com.intematrix.scheduler.entity.SchedAlertConfig.class));
    }

    @Test
    void shouldUpdateAlertConfigWhenPresent() {
        SchedTask task = new SchedTask();
        task.setId(1L);
        when(taskMapper.selectById(1L)).thenReturn(task);
        com.intematrix.scheduler.entity.SchedAlertConfig config =
                new com.intematrix.scheduler.entity.SchedAlertConfig();
        config.setId(3L);
        config.setTaskId(1L);
        config.setFailThreshold(2);
        when(alertConfigMapper.selectOne(any())).thenReturn(config);

        com.intematrix.scheduler.dto.AlertConfigRequest req =
                new com.intematrix.scheduler.dto.AlertConfigRequest();
        req.setFailThreshold(9);
        req.setEnabled(0);
        taskService.updateAlert(1L, req);

        verify(alertConfigMapper).updateById(any(com.intematrix.scheduler.entity.SchedAlertConfig.class));
    }

    @Test
    void shouldReturnAlertConfig() {
        when(alertConfigMapper.selectOne(any())).thenReturn(new com.intematrix.scheduler.entity.SchedAlertConfig());
        assertThat(taskService.getAlert(1L)).isNotNull();
    }
}
