package com.intematrix.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.intematrix.scheduler.common.BizException;
import com.intematrix.scheduler.common.ErrorCode;
import com.intematrix.scheduler.core.CronUtils;
import com.intematrix.scheduler.core.ExecutionFactory;
import com.intematrix.scheduler.core.ExecutionRunner;
import com.intematrix.scheduler.core.HandlerRegistry;
import com.intematrix.scheduler.dto.AlertConfigRequest;
import com.intematrix.scheduler.dto.TaskCreateRequest;
import com.intematrix.scheduler.entity.SchedAlertConfig;
import com.intematrix.scheduler.entity.SchedTask;
import com.intematrix.scheduler.entity.SchedTaskExecution;
import com.intematrix.scheduler.mapper.SchedAlertConfigMapper;
import com.intematrix.scheduler.mapper.SchedTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * 任务服务。
 */
@Slf4j
@Service
public class TaskService {

    private static final Set<String> VALID_STATUS = Set.of("ENABLED", "DISABLED");

    private final SchedTaskMapper taskMapper;
    private final SchedAlertConfigMapper alertConfigMapper;
    private final HandlerRegistry handlerRegistry;
    private final ExecutionFactory executionFactory;
    private final ExecutionRunner executionRunner;

    public TaskService(SchedTaskMapper taskMapper,
                       SchedAlertConfigMapper alertConfigMapper,
                       HandlerRegistry handlerRegistry,
                       ExecutionFactory executionFactory,
                       ExecutionRunner executionRunner) {
        this.taskMapper = taskMapper;
        this.alertConfigMapper = alertConfigMapper;
        this.handlerRegistry = handlerRegistry;
        this.executionFactory = executionFactory;
        this.executionRunner = executionRunner;
    }

    @Transactional
    public SchedTask create(TaskCreateRequest request) {
        if (!CronUtils.isValid(request.getCronExpression())) {
            throw new BizException(ErrorCode.CRON_INVALID, request.getCronExpression());
        }
        if (!handlerRegistry.exists(request.getHandlerName())) {
            throw new BizException(ErrorCode.HANDLER_NOT_FOUND, request.getHandlerName());
        }
        Long dup = taskMapper.selectCount(new LambdaQueryWrapper<SchedTask>()
                .eq(SchedTask::getTaskName, request.getTaskName()));
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.TASK_NAME_DUPLICATED, request.getTaskName());
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        SchedTask task = new SchedTask();
        task.setTaskName(request.getTaskName());
        task.setTaskGroup(request.getTaskGroup() == null ? "DEFAULT" : request.getTaskGroup());
        task.setCronExpression(request.getCronExpression());
        task.setHandlerName(request.getHandlerName());
        task.setShardTotal(request.getShardTotal() == null ? 1 : request.getShardTotal());
        task.setRetryMax(request.getRetryMax() == null ? 0 : request.getRetryMax());
        task.setRetryInterval(request.getRetryInterval() == null ? 5 : request.getRetryInterval());
        task.setTimeoutMs(request.getTimeoutMs() == null ? 60000 : request.getTimeoutMs());
        task.setStatus("ENABLED");
        task.setNextFireTime(CronUtils.next(request.getCronExpression(), now));
        task.setVersion(0);
        task.setDeleted(0);
        task.setRemark(request.getRemark());
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.insert(task);
        log.info("注册任务 [{}] id={}", task.getTaskName(), task.getId());
        return task;
    }

    public SchedTask getById(Long id) {
        SchedTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND, String.valueOf(id));
        }
        return task;
    }

    public List<SchedTask> list() {
        return taskMapper.selectList(new LambdaQueryWrapper<SchedTask>()
                .orderByDesc(SchedTask::getId));
    }

    @Transactional
    public SchedTask updateStatus(Long id, String status) {
        if (!VALID_STATUS.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 ENABLED / DISABLED");
        }
        SchedTask task = getById(id);
        SchedTask update = new SchedTask();
        update.setId(id);
        update.setStatus(status);
        update.setVersion(task.getVersion());
        if ("ENABLED".equals(status)) {
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            update.setNextFireTime(CronUtils.next(task.getCronExpression(), now));
        } else {
            update.setNextFireTime(null);
        }
        taskMapper.updateById(update);
        log.info("任务 [{}] 状态改为 {}", task.getTaskName(), status);
        return getById(id);
    }

    /**
     * 立即执行一次（手动触发，不占 cron 触发位）。
     */
    @Transactional
    public SchedTaskExecution runNow(Long id) {
        SchedTask task = getById(id);
        if (!"ENABLED".equals(task.getStatus())) {
            throw new BizException(ErrorCode.TASK_DISABLED, task.getTaskName());
        }
        if (!handlerRegistry.exists(task.getHandlerName())) {
            throw new BizException(ErrorCode.HANDLER_NOT_FOUND, task.getHandlerName());
        }
        SchedTaskExecution execution = executionFactory.create(task, "MANUAL", null);
        executionRunner.dispatch(task, execution);
        log.info("任务 [{}] 手动触发，执行实例 {}", task.getTaskName(), execution.getId());
        return execution;
    }

    @Transactional
    public SchedTask updateShard(Long id, Integer shardTotal) {
        getById(id);
        SchedTask update = new SchedTask();
        update.setId(id);
        update.setShardTotal(shardTotal);
        taskMapper.updateById(update);
        return getById(id);
    }

    @Transactional
    public SchedAlertConfig updateAlert(Long id, AlertConfigRequest request) {
        getById(id);
        SchedAlertConfig config = alertConfigMapper.selectOne(new LambdaQueryWrapper<SchedAlertConfig>()
                .eq(SchedAlertConfig::getTaskId, id)
                .last("LIMIT 1"));
        if (config == null) {
            config = new SchedAlertConfig();
            config.setTaskId(id);
            config.setFailThreshold(request.getFailThreshold() == null ? 3 : request.getFailThreshold());
            config.setNotifyChannel(request.getNotifyChannel() == null ? "LOG" : request.getNotifyChannel());
            config.setWebhookUrl(request.getWebhookUrl());
            config.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
            config.setCreateTime(LocalDateTime.now());
            config.setUpdateTime(LocalDateTime.now());
            alertConfigMapper.insert(config);
        } else {
            if (request.getFailThreshold() != null) {
                config.setFailThreshold(request.getFailThreshold());
            }
            if (request.getNotifyChannel() != null) {
                config.setNotifyChannel(request.getNotifyChannel());
            }
            if (request.getWebhookUrl() != null) {
                config.setWebhookUrl(request.getWebhookUrl());
            }
            if (request.getEnabled() != null) {
                config.setEnabled(request.getEnabled());
            }
            config.setUpdateTime(LocalDateTime.now());
            alertConfigMapper.updateById(config);
        }
        return config;
    }

    public SchedAlertConfig getAlert(Long id) {
        return alertConfigMapper.selectOne(new LambdaQueryWrapper<SchedAlertConfig>()
                .eq(SchedAlertConfig::getTaskId, id)
                .last("LIMIT 1"));
    }
}
