package com.intematrix.scheduler.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 分片配置请求。
 */
@Data
public class ShardConfigRequest {

    @NotNull(message = "分片数不能为空")
    @Min(value = 1, message = "分片数至少为 1")
    @Max(value = 64, message = "分片数最多 64")
    private Integer shardTotal;
}
