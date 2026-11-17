package com.intematrix.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 执行日志。
 */
@Data
@TableName("sched_task_exec_log")
public class SchedTaskExecLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long executionId;
    private Long taskId;
    private Integer shardIndex;
    private String logLevel;
    private String message;
    private LocalDateTime createTime;
}
