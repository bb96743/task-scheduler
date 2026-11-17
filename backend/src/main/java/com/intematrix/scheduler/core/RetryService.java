package com.intematrix.scheduler.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.intematrix.scheduler.entity.SchedRetryRecord;
import com.intematrix.scheduler.entity.SchedTask;
import com.intematrix.scheduler.entity.SchedTaskExecution;
import com.intematrix.scheduler.mapper.SchedRetryRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 重试服务：只负责重试记录的写入与查询，派发由 RetryScanner 完成（避免循环依赖）。
 */
@Slf4j
@Component
public class RetryService {

    private final SchedRetryRecordMapper retryRecordMapper;

    public RetryService(SchedRetryRecordMapper retryRecordMapper) {
        this.retryRecordMapper = retryRecordMapper;
    }

    /**
     * 执行失败后按指数退避安排重试。
     * 重试序号通过执行记录的 retry_count 串联，保证同一条链上次数正确累加。
     *
     * @return true 表示仍会重试；false 表示已达上限（终态失败）
     */
    public boolean scheduleRetry(SchedTask task, SchedTaskExecution execution, String error) {
        int retryMax = task.getRetryMax() == null ? 0 : task.getRetryMax();
        int prevCount = execution.getRetryCount() == null ? 0 : execution.getRetryCount();
        int nextCount = prevCount + 1;

        if (nextCount > retryMax) {
            log.info("任务 [{}] 执行 {} 失败且重试已达上限 {}，转终态失败", task.getTaskName(),
                    execution.getId(), retryMax);
            return false;
        }

        int interval = task.getRetryInterval() == null ? 5 : Math.max(1, task.getRetryInterval());
        long delaySeconds = (long) interval * (1L << (nextCount - 1));
        LocalDateTime nextTime = LocalDateTime.now().plusSeconds(delaySeconds);

        SchedRetryRecord record = new SchedRetryRecord();
        record.setExecutionId(execution.getId());
        record.setTaskId(task.getId());
        record.setRetryCount(nextCount);
        record.setNextRetryTime(nextTime);
        record.setStatus("PENDING");
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        retryRecordMapper.insert(record);
        log.info("任务 [{}] 失败，安排第 {}/{} 次重试，预计 {} 执行", task.getTaskName(),
                nextCount, retryMax, nextTime);
        return true;
    }

    public List<SchedRetryRecord> findDue(int limit) {
        return retryRecordMapper.selectList(new LambdaQueryWrapper<SchedRetryRecord>()
                .eq(SchedRetryRecord::getStatus, "PENDING")
                .le(SchedRetryRecord::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(SchedRetryRecord::getNextRetryTime)
                .last("LIMIT " + Math.max(1, limit)));
    }

    /**
     * 抢占重试记录，避免多节点重复处理。
     */
    public boolean claim(Long retryId) {
        SchedRetryRecord record = retryRecordMapper.selectById(retryId);
        if (record == null || !"PENDING".equals(record.getStatus())) {
            return false;
        }
        record.setStatus("DONE");
        record.setUpdateTime(LocalDateTime.now());
        return retryRecordMapper.updateById(record) > 0;
    }
}
