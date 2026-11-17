package com.intematrix.scheduler.core;

import com.intematrix.scheduler.config.SchedulerProperties;
import com.intematrix.scheduler.entity.SchedTask;
import com.intematrix.scheduler.entity.SchedTaskExecution;
import com.intematrix.scheduler.mapper.SchedTaskExecutionMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 执行实例工厂。为手动/重试/补偿生成 fire_time 唯一的执行记录。
 */
@Component
public class ExecutionFactory {

    private final SchedTaskExecutionMapper executionMapper;
    private final SchedulerProperties properties;

    public ExecutionFactory(SchedTaskExecutionMapper executionMapper, SchedulerProperties properties) {
        this.executionMapper = executionMapper;
        this.properties = properties;
    }

    public SchedTaskExecution create(SchedTask task, String triggerType, Long compensatedFrom) {
        return create(task, triggerType, compensatedFrom, 0);
    }

    public SchedTaskExecution create(SchedTask task, String triggerType, Long compensatedFrom, int retryCount) {
        LocalDateTime base = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        for (int i = 0; i < 20; i++) {
            SchedTaskExecution execution = new SchedTaskExecution();
            execution.setTaskId(task.getId());
            execution.setFireTime(base.plusSeconds(i));
            execution.setTriggerType(triggerType);
            execution.setStatus("RUNNING");
            // 记录实际执行节点，双节点排查时能区分是谁跑的
            execution.setNodeId(properties.getNodeId());
            execution.setShardTotal(task.getShardTotal() == null ? 1 : task.getShardTotal());
            execution.setSuccessShard(0);
            execution.setFailedShard(0);
            execution.setCompensatedFrom(compensatedFrom);
            execution.setRetryCount(retryCount);
            execution.setCreateTime(LocalDateTime.now());
            try {
                executionMapper.insert(execution);
                return execution;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // fire_time 冲突，顺延 1 秒重试
            }
        }
        throw new com.intematrix.scheduler.common.BizException(
                com.intematrix.scheduler.common.ErrorCode.INTERNAL_ERROR, "无法生成唯一执行时刻");
    }
}
