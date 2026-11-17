package com.intematrix.scheduler.core;

import com.intematrix.scheduler.entity.SchedRetryRecord;
import com.intematrix.scheduler.entity.SchedTask;
import com.intematrix.scheduler.entity.SchedTaskExecution;
import com.intematrix.scheduler.mapper.SchedTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 重试扫描器：到期重试记录重新派发执行。
 */
@Slf4j
@Component
public class RetryScanner {

    private final RetryService retryService;
    private final SchedTaskMapper taskMapper;
    private final ExecutionFactory executionFactory;
    private final ExecutionRunner executionRunner;

    public RetryScanner(RetryService retryService,
                        SchedTaskMapper taskMapper,
                        ExecutionFactory executionFactory,
                        ExecutionRunner executionRunner) {
        this.retryService = retryService;
        this.taskMapper = taskMapper;
        this.executionFactory = executionFactory;
        this.executionRunner = executionRunner;
    }

    @Scheduled(fixedDelayString = "${scheduler.retry-scan-interval-ms:5000}")
    public void scan() {
        List<SchedRetryRecord> due = retryService.findDue(20);
        for (SchedRetryRecord record : due) {
            try {
                if (!retryService.claim(record.getId())) {
                    continue;
                }
                SchedTask task = taskMapper.selectById(record.getTaskId());
                if (task == null) {
                    log.warn("重试记录 {} 对应任务不存在", record.getId());
                    continue;
                }
                SchedTaskExecution execution = executionFactory.create(task, "RETRY", null, record.getRetryCount());
                log.info("任务 [{}] 第 {} 次重试派发，执行实例 {}", task.getTaskName(),
                        record.getRetryCount(), execution.getId());
                executionRunner.dispatch(task, execution);
            } catch (Exception e) {
                log.error("处理重试记录 {} 失败", record.getId(), e);
            }
        }
    }
}
