package com.intematrix.scheduler.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.intematrix.scheduler.entity.SchedAlertConfig;
import com.intematrix.scheduler.entity.SchedAlertRecord;
import com.intematrix.scheduler.entity.SchedTask;
import com.intematrix.scheduler.entity.SchedTaskExecution;
import com.intematrix.scheduler.mapper.SchedAlertConfigMapper;
import com.intematrix.scheduler.mapper.SchedAlertRecordMapper;
import com.intematrix.scheduler.mapper.SchedTaskExecutionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警服务：连续终态失败达到阈值时写告警记录（5 分钟内去重）。
 */
@Slf4j
@Component
public class AlertService {

    private static final int DEFAULT_THRESHOLD = 3;
    private static final int DEDUP_MINUTES = 5;

    private final SchedAlertConfigMapper alertConfigMapper;
    private final SchedAlertRecordMapper alertRecordMapper;
    private final SchedTaskExecutionMapper executionMapper;

    public AlertService(SchedAlertConfigMapper alertConfigMapper,
                        SchedAlertRecordMapper alertRecordMapper,
                        SchedTaskExecutionMapper executionMapper) {
        this.alertConfigMapper = alertConfigMapper;
        this.alertRecordMapper = alertRecordMapper;
        this.executionMapper = executionMapper;
    }

    public void onExecutionFailed(SchedTask task, SchedTaskExecution execution) {
        SchedAlertConfig config = alertConfigMapper.selectOne(new LambdaQueryWrapper<SchedAlertConfig>()
                .eq(SchedAlertConfig::getTaskId, task.getId())
                .last("LIMIT 1"));
        if (config != null && config.getEnabled() != null && config.getEnabled() == 0) {
            return;
        }
        int threshold = config == null || config.getFailThreshold() == null
                ? DEFAULT_THRESHOLD : Math.max(1, config.getFailThreshold());

        int consecutive = countConsecutiveFailures(task.getId(), threshold);
        if (consecutive < threshold) {
            return;
        }
        // 滑动窗口去重：距该任务上一条告警不足 5 分钟就跳过。
        // 用最近一条记录的时间判断，而不是按时间片取整，避免跨片边界时两次告警只隔几秒。
        SchedAlertRecord latest = alertRecordMapper.selectOne(new LambdaQueryWrapper<SchedAlertRecord>()
                .eq(SchedAlertRecord::getTaskId, task.getId())
                .orderByDesc(SchedAlertRecord::getId)
                .last("LIMIT 1"));
        if (latest != null && latest.getTriggeredAt() != null
                && latest.getTriggeredAt().isAfter(LocalDateTime.now().minusMinutes(DEDUP_MINUTES))) {
            log.debug("告警去重命中滑动窗口：task={} 上次告警于 {}", task.getId(), latest.getTriggeredAt());
            return;
        }
        // 时间片唯一键只做并发兜底：多条执行同时转终态时，让数据库裁决谁写入
        long bucket = System.currentTimeMillis() / (DEDUP_MINUTES * 60_000L);
        String dedupKey = task.getId() + ":" + bucket;

        String channel = config == null || config.getNotifyChannel() == null
                ? "LOG" : config.getNotifyChannel();
        SchedAlertRecord record = new SchedAlertRecord();
        record.setTaskId(task.getId());
        record.setExecutionId(execution.getId());
        record.setConsecutiveFail(consecutive);
        record.setNotifyChannel(channel);
        record.setDedupKey(dedupKey);
        record.setMessage("任务 [" + task.getTaskName() + "] 连续失败 " + consecutive
                + " 次，已触发告警（阈值 " + threshold + "）");
        record.setTriggeredAt(LocalDateTime.now());
        try {
            alertRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            log.debug("告警去重命中唯一约束，跳过本次写入：dedupKey={}", dedupKey);
            return;
        }
        log.warn("触发告警：{}", record.getMessage());
    }

    /**
     * 从最近往前统计连续失败次数。
     */
    private int countConsecutiveFailures(Long taskId, int limit) {
        List<SchedTaskExecution> recent = executionMapper.selectList(
                new LambdaQueryWrapper<SchedTaskExecution>()
                        .eq(SchedTaskExecution::getTaskId, taskId)
                        .orderByDesc(SchedTaskExecution::getId)
                        .last("LIMIT " + Math.max(limit, 5)));
        int count = 0;
        for (SchedTaskExecution e : recent) {
            if ("FAILED".equals(e.getStatus())) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }
}
