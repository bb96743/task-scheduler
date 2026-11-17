package com.intematrix.scheduler.handler;

import com.intematrix.scheduler.core.ShardContext;
import com.intematrix.scheduler.core.ShardResult;
import com.intematrix.scheduler.core.TaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 演示处理器：每次成功。
 */
@Slf4j
@Component("demoSuccessHandler")
public class DemoSuccessHandler implements TaskHandler {

    @Override
    public ShardResult execute(ShardContext context) {
        context.info("开始执行分片 " + context.getShardIndex());
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        context.info("分片 " + context.getShardIndex() + " 执行成功");
        return ShardResult.ok("第 " + context.getShardIndex() + " 分片处理 100 条数据");
    }
}
