package com.intematrix.scheduler.core;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 分片执行结果。
 */
@Data
@AllArgsConstructor
public class ShardResult {

    private boolean success;
    private String summary;

    public static ShardResult ok(String summary) {
        return new ShardResult(true, summary);
    }

    public static ShardResult fail(String summary) {
        return new ShardResult(false, summary);
    }
}
