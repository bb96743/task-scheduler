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
    /** 去重键：taskId + 5 分钟时间片，由唯一约束保证并发下只落一条。 */
    private String dedupKey;
    private LocalDateTime triggeredAt;
}
