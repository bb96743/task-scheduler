package com.intematrix.scheduler.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.intematrix.scheduler.config.SchedulerProperties;
import com.intematrix.scheduler.entity.SchedTask;
import com.intematrix.scheduler.entity.SchedTaskExecution;
import com.intematrix.scheduler.entity.SchedTaskFire;
import com.intematrix.scheduler.mapper.SchedTaskExecutionMapper;
import com.intematrix.scheduler.mapper.SchedTaskFireMapper;
import com.intematrix.scheduler.mapper.SchedTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 调度引擎：扫描到期任务 → 原子抢占触发权 → 创建执行实例 → 派发。
 * <p>
 * 不重复执行的核心：每次触发先向 sched_task_fire 插入 (task_id, fire_time)，
 * 依赖唯一约束 uk_task_fire，多节点只有一台能插入成功，其余跳过。
 */
@Slf4j
@Component
public class SchedulerEngine {

    private final SchedTaskMapper taskMapper;
    private final SchedTaskFireMapper fireMapper;
    private final SchedTaskExecutionMapper executionMapper;
    private final ExecutionRunner executionRunner;
    private final SchedulerProperties properties;

    public SchedulerEngine(SchedTaskMapper taskMapper,
                           SchedTaskFireMapper fireMapper,
                           SchedTaskExecutionMapper executionMapper,
                           ExecutionRunner executionRunner,
                           SchedulerProperties properties) {
        this.taskMapper = taskMapper;
        this.fireMapper = fireMapper;
        this.executionMapper = executionMapper;
        this.executionRunner = executionRunner;
        this.properties = properties;
    }

    /**
     * 启动时补齐 next_fire_time 为空的任务。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initNextFireTime() {
        List<SchedTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<SchedTask>()
                .eq(SchedTask::getStatus, "ENABLED")
                .isNull(SchedTask::getNextFireTime));
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        for (SchedTask task : tasks) {
            if (!CronUtils.isValid(task.getCronExpression())) {
                log.warn("任务 [{}] cron 非法：{}", task.getTaskName(), task.getCronExpression());
                continue;
            }
            SchedTask update = new SchedTask();
            update.setId(task.getId());
            update.setNextFireTime(CronUtils.next(task.getCronExpression(), now));
            update.setVersion(task.getVersion());
            taskMapper.updateById(update);
        }
    }

    /**
     * 调度主循环。每个节点都在扫，靠唯一约束保证不重复。
     */
    @Scheduled(fixedDelayString = "${scheduler.scan-interval-ms:1000}")
    public void scan() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        List<SchedTask> dueTasks = taskMapper.selectList(new LambdaQueryWrapper<SchedTask>()
                .eq(SchedTask::getStatus, "ENABLED")
                .isNotNull(SchedTask::getNextFireTime)
                .le(SchedTask::getNextFireTime, now)
                .last("LIMIT 50"));

        for (SchedTask task : dueTasks) {
            try {
                fire(task, now);
            } catch (Exception e) {
                log.error("调度任务 [{}] 失败", task.getTaskName(), e);
            }
        }
    }

    private void fire(SchedTask task, LocalDateTime now) {
        LocalDateTime fireTime = task.getNextFireTime() == null ? now : task.getNextFireTime();

        // 1) 抢占触发权：唯一约束冲突说明别的节点已抢到
        SchedTaskFire fire = new SchedTaskFire();
        fire.setTaskId(task.getId());
        fire.setFireTime(fireTime);
        fire.setNodeId(properties.getNodeId());
        fire.setCreateTime(LocalDateTime.now());
        try {
            fireMapper.insert(fire);
        } catch (DuplicateKeyException e) {
            log.debug("触发 [{} @ {}] 已被其他节点抢占，跳过", task.getTaskName(), fireTime);
            return;
        }

        // 2) 推进下次触发时间
        LocalDateTime next = CronUtils.next(task.getCronExpression(), now);
        SchedTask update = new SchedTask();
        update.setId(task.getId());
        update.setLastFireTime(fireTime);
        update.setNextFireTime(next);
        update.setVersion(task.getVersion());
        taskMapper.updateById(update);

        // 3) 创建执行实例（唯一约束作为第二道保险）
        SchedTaskExecution execution = new SchedTaskExecution();
        execution.setTaskId(task.getId());
        execution.setFireTime(fireTime);
        execution.setTriggerType("CRON");
        execution.setStatus("RUNNING");
        execution.setNodeId(properties.getNodeId());
        execution.setShardTotal(task.getShardTotal() == null ? 1 : task.getShardTotal());
        execution.setSuccessShard(0);
        execution.setFailedShard(0);
        execution.setRetryCount(0);
        execution.setCreateTime(LocalDateTime.now());
        try {
            executionMapper.insert(execution);
        } catch (DuplicateKeyException e) {
            log.debug("执行实例 [{} @ {}] 已存在，跳过", task.getTaskName(), fireTime);
            return;
        }

        log.info("节点 {} 触发任务 [{}]，执行实例 {}", properties.getNodeId(), task.getTaskName(), execution.getId());
        executionRunner.dispatch(task, execution);
    }
}
