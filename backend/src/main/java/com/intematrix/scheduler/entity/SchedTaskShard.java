package com.intematrix.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分片。
 */
@Data
@TableName("sched_task_shard")
public class SchedTaskShard {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long executionId;
    private Integer shardIndex;
    private Integer shardTotal;
    private String status;
    private String resultSummary;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private LocalDateTime createTime;
}
