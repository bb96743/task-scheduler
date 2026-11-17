package com.intematrix.scheduler.handler;

import com.intematrix.scheduler.core.ShardContext;
import com.intematrix.scheduler.core.ShardResult;
import com.intematrix.scheduler.core.TaskHandler;
import org.springframework.stereotype.Component;

/**
 * 演示处理器：固定失败，用于观察重试与告警。
 */
@Component("demoFailHandler")
public class DemoFailHandler implements TaskHandler {

    @Override
    public ShardResult execute(ShardContext context) {
        context.error("模拟业务异常：下游接口返回 500");
        throw new IllegalStateException("模拟失败：下游接口不可用");
    }
}
