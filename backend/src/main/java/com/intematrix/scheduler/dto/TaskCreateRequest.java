package com.intematrix.scheduler.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务注册请求。
 */
@Data
public class TaskCreateRequest {

    @NotBlank(message = "任务名不能为空")
    @Size(max = 128, message = "任务名最多 128 字符")
    private String taskName;

    private String taskGroup;

    @NotBlank(message = "cron 表达式不能为空")
    private String cronExpression;

    @NotBlank(message = "处理器不能为空")
    private String handlerName;

    @Min(value = 1, message = "分片数至少为 1")
    @Max(value = 64, message = "分片数最多 64")
    private Integer shardTotal;

    @Min(value = 0, message = "重试次数不能为负")
    private Integer retryMax;

    @Min(value = 1, message = "重试间隔至少 1 秒")
    private Integer retryInterval;

    @Min(value = 1000, message = "超时时间至少 1000ms")
    private Integer timeoutMs;

    @Size(max = 512)
    private String remark;
}
