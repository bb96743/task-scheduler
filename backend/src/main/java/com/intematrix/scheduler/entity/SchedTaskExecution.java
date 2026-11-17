package com.intematrix.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 执行实例。
 */
@Data
@TableName("sched_task_execution")
public class SchedTaskExecution {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private LocalDateTime fireTime;
    private String triggerType;
    private String status;
    private String nodeId;
    private Integer shardTotal;
    private Integer successShard;
    private Integer failedShard;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String errorMessage;
    private Long compensatedFrom;
    private Integer retryCount;
    private LocalDateTime createTime;
}
