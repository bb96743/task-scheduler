package com.intematrix.scheduler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务定义。
 */
@Data
@TableName("sched_task")
public class SchedTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskName;
    private String taskGroup;
    private String cronExpression;
    private String handlerName;
    private Integer shardTotal;
    private Integer retryMax;
    private Integer retryInterval;
    private Integer timeoutMs;
    private String status;
    private LocalDateTime nextFireTime;
    private LocalDateTime lastFireTime;
    @Version
    private Integer version;
    private String remark;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
