-- 5 个预置示例任务（验收第 9 条：预置不少于 5 个示例任务）
-- 使用 FixedHandler（内置示例处理器），便于开箱演示

INSERT INTO sched_task (task_name, task_group, cron_expression, handler_name, shard_total, retry_max, retry_interval, timeout_ms, status, next_fire_time, remark)
VALUES ('demo-success-task', 'DEMO', '0/30 * * * * ?', 'demoSuccessHandler', 1, 3, 5, 60000, 'ENABLED', CURRENT_TIMESTAMP, '演示：每次都成功，用于观察执行记录与耗时');

INSERT INTO sched_task (task_name, task_group, cron_expression, handler_name, shard_total, retry_max, retry_interval, timeout_ms, status, next_fire_time, remark)
VALUES ('demo-fail-task', 'DEMO', '0/60 * * * * ?', 'demoFailHandler', 1, 2, 5, 60000, 'ENABLED', CURRENT_TIMESTAMP, '演示：每次失败，用于观察重试与告警');

INSERT INTO sched_task (task_name, task_group, cron_expression, handler_name, shard_total, retry_max, retry_interval, timeout_ms, status, next_fire_time, remark)
VALUES ('demo-shard-task', 'DEMO', '0/45 * * * * ?', 'demoShardHandler', 4, 1, 5, 120000, 'ENABLED', CURRENT_TIMESTAMP, '演示：4 分片并行，用于观察分片执行与结果汇总');

INSERT INTO sched_task (task_name, task_group, cron_expression, handler_name, shard_total, retry_max, retry_interval, timeout_ms, status, next_fire_time, remark)
VALUES ('demo-flaky-task', 'DEMO', '0/90 * * * * ?', 'demoFlakyHandler', 1, 3, 10, 60000, 'ENABLED', CURRENT_TIMESTAMP, '演示：约一半概率失败，用于观察重试成功/最终失败');

INSERT INTO sched_task (task_name, task_group, cron_expression, handler_name, shard_total, retry_max, retry_interval, timeout_ms, status, next_fire_time, remark)
VALUES ('demo-paused-task', 'DEMO', '0 0 3 * * ?', 'demoSuccessHandler', 1, 0, 5, 60000, 'DISABLED', NULL, '演示：已停用任务，用于观察启停功能');

-- 告警配置：失败任务连续 2 次失败触发告警
INSERT INTO sched_alert_config (task_id, fail_threshold, notify_channel, enabled)
VALUES (2, 2, 'LOG', 1);
INSERT INTO sched_alert_config (task_id, fail_threshold, notify_channel, enabled)
VALUES (4, 3, 'LOG', 1);

-- 演示节点
INSERT INTO sched_node (node_id, host, last_heartbeat, status)
VALUES ('node-1', '127.0.0.1', CURRENT_TIMESTAMP, 'ONLINE');
