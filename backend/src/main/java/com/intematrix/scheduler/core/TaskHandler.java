package com.intematrix.scheduler.core;

/**
 * 任务处理器契约。业务实现注册为 Spring Bean，handler 名即 Bean 名。
 */
public interface TaskHandler {

    /**
     * 执行单个分片。
     *
     * @param context 分片上下文
     * @return 分片结果
     * @throws Exception 抛出即视为该分片失败
     */
    ShardResult execute(ShardContext context) throws Exception;
}
