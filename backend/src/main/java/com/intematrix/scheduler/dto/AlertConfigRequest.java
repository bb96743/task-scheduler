package com.intematrix.scheduler.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 告警配置请求。
 */
@Data
public class AlertConfigRequest {

    @Min(value = 1, message = "失败阈值至少为 1")
    private Integer failThreshold;

    private String notifyChannel;

    private String webhookUrl;

    private Integer enabled;
}
