package com.intematrix.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 重试队列。
 */
@Data
@TableName("sched_retry_record")
public class SchedRetryRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long executionId;
    private Long taskId;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
