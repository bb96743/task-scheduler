package com.intematrix.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警配置。
 */
@Data
@TableName("sched_alert_config")
public class SchedAlertConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer failThreshold;
    private String notifyChannel;
    private String webhookUrl;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
