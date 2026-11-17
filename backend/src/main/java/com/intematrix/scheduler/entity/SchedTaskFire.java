package com.intematrix.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 触发去重记录。唯一约束 uk_task_fire(task_id, fire_time) 是跨节点防重复执行的最终保障。
 */
@Data
@TableName("sched_task_fire")
public class SchedTaskFire {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private LocalDateTime fireTime;
    private String nodeId;
    private LocalDateTime createTime;
}
