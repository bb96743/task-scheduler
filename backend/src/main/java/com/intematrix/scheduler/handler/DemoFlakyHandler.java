package com.intematrix.scheduler.handler;

import com.intematrix.scheduler.core.ShardContext;
import com.intematrix.scheduler.core.ShardResult;
import com.intematrix.scheduler.core.TaskHandler;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 演示处理器：约一半概率失败，用于观察重试成功与最终失败。
 */
@Component("demoFlakyHandler")
public class DemoFlakyHandler implements TaskHandler {

    @Override
    public ShardResult execute(ShardContext context) {
        boolean success = ThreadLocalRandom.current().nextInt(2) == 0;
        if (!success) {
            context.error("模拟偶发失败：调用第三方超时");
            throw new IllegalStateException("模拟偶发失败：第三方超时");
        }
        context.info("本次执行成功");
        return ShardResult.ok("偶发任务本次成功");
    }
}
