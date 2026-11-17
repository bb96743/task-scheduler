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
        // 去重键按时间片取整（5 分钟一格），配合 uk_alert_dedup 唯一约束做最终裁决：
        // 多个执行并发转终态时，预检查可能同时通过，只有抢到唯一键的那条能落库。
        long bucket = System.currentTimeMillis() / (DEDUP_MINUTES * 60_000L);
        String dedupKey = task.getId() + ":" + bucket;
        Long recent = alertRecordMapper.selectCount(new LambdaQueryWrapper<SchedAlertRecord>()
                .eq(SchedAlertRecord::getDedupKey, dedupKey));
        if (recent != null && recent > 0) {
            return;
        }

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
