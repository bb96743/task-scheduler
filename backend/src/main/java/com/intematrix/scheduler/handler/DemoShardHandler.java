package com.intematrix.scheduler.handler;

import com.intematrix.scheduler.core.ShardContext;
import com.intematrix.scheduler.core.ShardResult;
import com.intematrix.scheduler.core.TaskHandler;
import org.springframework.stereotype.Component;

/**
 * 演示处理器：多分片并行，用于观察分片执行与结果汇总。
 */
@Component("demoShardHandler")
public class DemoShardHandler implements TaskHandler {

    @Override
    public ShardResult execute(ShardContext context) {
        int index = context.getShardIndex();
        int total = context.getShardTotal();
        context.info("分片 " + index + "/" + total + " 开始，处理区间 [" + index * 250 + ", " + (index + 1) * 250 + ")");
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        context.info("分片 " + index + "/" + total + " 完成");
        return ShardResult.ok("分片 " + index + " 处理 250 条，耗时约 800ms");
    }
}
