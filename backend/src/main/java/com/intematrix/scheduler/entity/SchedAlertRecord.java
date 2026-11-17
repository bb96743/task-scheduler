package com.intematrix.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警记录。
 */
@Data
@TableName("sched_alert_record")
public class SchedAlertRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long executionId;
    private Integer consecutiveFail;
    private String message;
    private String notifyChannel;
    private LocalDateTime triggeredAt;
}
