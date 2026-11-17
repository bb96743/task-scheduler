package com.intematrix.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点心跳。
 */
@Data
@TableName("sched_node")
public class SchedNode {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String nodeId;
    private String host;
    private LocalDateTime lastHeartbeat;
    private String status;
    private LocalDateTime createTime;
}
