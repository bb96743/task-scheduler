package com.intematrix.scheduler.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 任务启停请求。
 */
@Data
public class TaskStatusRequest {

    @NotBlank(message = "状态不能为空")
    private String status;
}
