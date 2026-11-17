package com.intematrix.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.intematrix.scheduler.common.BizException;
import com.intematrix.scheduler.common.ErrorCode;
import com.intematrix.scheduler.config.SchedulerProperties;
import com.intematrix.scheduler.core.ExecutionFactory;
import com.intematrix.scheduler.core.ExecutionRunner;
import com.intematrix.scheduler.entity.SchedTask;
import com.intematrix.scheduler.entity.SchedTaskExecLog;
import com.intematrix.scheduler.entity.SchedTaskExecution;
import com.intematrix.scheduler.entity.SchedTaskShard;
import com.intematrix.scheduler.mapper.SchedTaskExecLogMapper;
import com.intematrix.scheduler.mapper.SchedTaskExecutionMapper;
import com.intematrix.scheduler.mapper.SchedTaskMapper;
import com.intematrix.scheduler.mapper.SchedTaskShardMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 执行服务：查询、日志、手动重试、手工补偿。
 */
@Slf4j
@Service
public class ExecutionService {

    private final SchedTaskExecutionMapper executionMapper;
    private final SchedTaskExecLogMapper logMapper;
    private final SchedTaskShardMapper shardMapper;
    private final SchedTaskMapper taskMapper;
    private final ExecutionFactory executionFactory;
    private final ExecutionRunner executionRunner;
    private final SchedulerProperties properties;

    public ExecutionService(SchedTaskExecutionMapper executionMapper,
                            SchedTaskExecLogMapper logMapper,
                            SchedTaskShardMapper shardMapper,
                            SchedTaskMapper taskMapper,
                            ExecutionFactory executionFactory,
                            ExecutionRunner executionRunner,
                            SchedulerProperties properties) {
        this.executionMapper = executionMapper;
        this.logMapper = logMapper;
        this.shardMapper = shardMapper;
        this.taskMapper = taskMapper;
        this.executionFactory = executionFactory;
        this.executionRunner = executionRunner;
        this.properties = properties;
    }

    public List<SchedTaskExecution> listByTask(Long taskId, String status,
                                               LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<SchedTaskExecution> query = new LambdaQueryWrapper<SchedTaskExecution>()
                .eq(SchedTaskExecution::getTaskId, taskId)
                .eq(status != null && !status.isBlank(), SchedTaskExecution::getStatus, status)
                .ge(startTime != null, SchedTaskExecution::getCreateTime, startTime)
                .le(endTime != null, SchedTaskExecution::getCreateTime, endTime)
                .orderByDesc(SchedTaskExecution::getId)
                .last("LIMIT 200");
        return executionMapper.selectList(query);
    }

    public SchedTaskExecution getById(Long id) {
        SchedTaskExecution execution = executionMapper.selectById(id);
        if (execution == null) {
            throw new BizException(ErrorCode.EXECUTION_NOT_FOUND, String.valueOf(id));
        }
        return execution;
    }

    public List<SchedTaskShard> shards(Long executionId) {
        getById(executionId);
        return shardMapper.selectList(new LambdaQueryWrapper<SchedTaskShard>()
                .eq(SchedTaskShard::getExecutionId, executionId)
                .orderByAsc(SchedTaskShard::getShardIndex));
    }

    public List<SchedTaskExecLog> logs(Long executionId, String level,
                                       LocalDateTime startTime, LocalDateTime endTime) {
        getById(executionId);
        return logMapper.selectList(new LambdaQueryWrapper<SchedTaskExecLog>()
                .eq(SchedTaskExecLog::getExecutionId, executionId)
                .eq(level != null && !level.isBlank(), SchedTaskExecLog::getLogLevel, level)
                .ge(startTime != null, SchedTaskExecLog::getCreateTime, startTime)
                .le(endTime != null, SchedTaskExecLog::getCreateTime, endTime)
                .orderByAsc(SchedTaskExecLog::getId)
                .last("LIMIT 500"));
    }

    /**
     * 手动重试：对失败执行再跑一次。
     */
    @Transactional
    public SchedTaskExecution retry(Long executionId) {
        SchedTaskExecution source = getById(executionId);
        if ("RUNNING".equals(source.getStatus())) {
            throw new BizException(ErrorCode.EXECUTION_RUNNING);
        }
        SchedTask task = mustGetTask(source.getTaskId());
        SchedTaskExecution execution = executionFactory.create(task, "RETRY", null);
        executionRunner.dispatch(task, execution);
        log.info("手动重试执行 {} → 新执行 {}", executionId, execution.getId());
        return execution;
    }

    /**
     * 手工补偿：仅失败终态、时间窗口内、未补偿过；生成新执行并保留可追溯来源。
     */
    @Transactional
    public SchedTaskExecution compensate(Long executionId) {
        SchedTaskExecution source = getById(executionId);
        if (!"FAILED".equals(source.getStatus())) {
            throw new BizException(ErrorCode.EXECUTION_NOT_FAILED);
        }
        LocalDateTime windowStart = LocalDateTime.now().minusDays(properties.getCompensationWindowDays());
        if (source.getCreateTime() != null && source.getCreateTime().isBefore(windowStart)) {
            throw new BizException(ErrorCode.EXECUTION_OUT_OF_WINDOW,
                    "仅支持补偿 " + properties.getCompensationWindowDays() + " 天内的执行");
        }
        Long compensated = executionMapper.selectCount(new LambdaQueryWrapper<SchedTaskExecution>()
                .eq(SchedTaskExecution::getCompensatedFrom, executionId));
        if (compensated != null && compensated > 0) {
            throw new BizException(ErrorCode.EXECUTION_ALREADY_COMPENSATED);
        }
        SchedTask task = mustGetTask(source.getTaskId());
        SchedTaskExecution execution = executionFactory.create(task, "COMPENSATE", executionId);
        executionRunner.dispatch(task, execution);
        log.info("手工补偿执行 {} → 新执行 {}", executionId, execution.getId());
        return execution;
    }

    private SchedTask mustGetTask(Long taskId) {
        SchedTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND, String.valueOf(taskId));
        }
        return task;
    }
}
