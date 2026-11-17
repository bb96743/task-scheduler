-- 分布式任务调度中心 · H2 建表脚本（MODE=MySQL）
-- 说明：字段与 MySQL 版本保持一致，仅类型适配 H2

DROP TABLE IF EXISTS sched_task_fire;
DROP TABLE IF EXISTS sched_retry_record;
DROP TABLE IF EXISTS sched_alert_record;
DROP TABLE IF EXISTS sched_alert_config;
DROP TABLE IF EXISTS sched_task_shard;
DROP TABLE IF EXISTS sched_task_exec_log;
DROP TABLE IF EXISTS sched_task_execution;
DROP TABLE IF EXISTS sched_task;
DROP TABLE IF EXISTS sched_node;

-- 1. 任务定义
CREATE TABLE sched_task (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name       VARCHAR(128)  NOT NULL,
    task_group      VARCHAR(64)   DEFAULT 'DEFAULT',
    cron_expression VARCHAR(64)   NOT NULL,
    handler_name    VARCHAR(128)  NOT NULL,
    shard_total     INT           DEFAULT 1,
    retry_max       INT           DEFAULT 3,
    retry_interval  INT           DEFAULT 5,
    timeout_ms      INT           DEFAULT 60000,
    status          VARCHAR(16)   DEFAULT 'ENABLED',
    next_fire_time  TIMESTAMP     NULL,
    last_fire_time  TIMESTAMP     NULL,
    version         INT           DEFAULT 0,
    remark          VARCHAR(512),
    deleted         TINYINT       DEFAULT 0,
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_task_name UNIQUE (task_name)
);

-- 2. 执行实例
CREATE TABLE sched_task_execution (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id        BIGINT        NOT NULL,
    fire_time      TIMESTAMP     NOT NULL,
    trigger_type   VARCHAR(16)   DEFAULT 'CRON',
    status         VARCHAR(16)   DEFAULT 'RUNNING',
    node_id        VARCHAR(64),
    shard_total    INT           DEFAULT 1,
    success_shard  INT           DEFAULT 0,
    failed_shard   INT           DEFAULT 0,
    start_time     TIMESTAMP     NULL,
    end_time       TIMESTAMP     NULL,
    duration_ms    BIGINT        DEFAULT 0,
    error_message  VARCHAR(1000),
    compensated_from BIGINT      NULL,
    retry_count    INT           DEFAULT 0,
    create_time    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_exec_task_fire UNIQUE (task_id, fire_time)
);

-- 3. 执行日志
CREATE TABLE sched_task_exec_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id  BIGINT        NOT NULL,
    task_id       BIGINT        NOT NULL,
    shard_index   INT           DEFAULT 0,
    log_level     VARCHAR(8)    DEFAULT 'INFO',
    message       VARCHAR(2000),
    create_time   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_log_exec ON sched_task_exec_log (execution_id);
CREATE INDEX idx_log_task_time ON sched_task_exec_log (task_id, create_time);

-- 4. 分片
CREATE TABLE sched_task_shard (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id   BIGINT        NOT NULL,
    shard_index    INT           NOT NULL,
    shard_total    INT           NOT NULL,
    status         VARCHAR(16)   DEFAULT 'PENDING',
    result_summary VARCHAR(1000),
    start_time     TIMESTAMP     NULL,
    end_time       TIMESTAMP     NULL,
    duration_ms    BIGINT        DEFAULT 0,
    create_time    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_shard_exec ON sched_task_shard (execution_id);

-- 5. 告警配置
CREATE TABLE sched_alert_config (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id        BIGINT        NOT NULL,
    fail_threshold INT           DEFAULT 3,
    notify_channel VARCHAR(64)   DEFAULT 'LOG',
    webhook_url    VARCHAR(512),
    enabled        TINYINT       DEFAULT 1,
    create_time    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_alert_task UNIQUE (task_id)
);

-- 6. 告警记录
CREATE TABLE sched_alert_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         BIGINT        NOT NULL,
    execution_id    BIGINT        NULL,
    consecutive_fail INT          NOT NULL,
    message         VARCHAR(1000),
    notify_channel  VARCHAR(64),
    triggered_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_alert_task ON sched_alert_record (task_id, triggered_at);

-- 7. 重试队列
CREATE TABLE sched_retry_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id    BIGINT        NOT NULL,
    task_id         BIGINT        NOT NULL,
    retry_count     INT           DEFAULT 0,
    next_retry_time TIMESTAMP     NOT NULL,
    status          VARCHAR(16)   DEFAULT 'PENDING',
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_retry_time ON sched_retry_record (status, next_retry_time);

-- 8. 节点心跳
CREATE TABLE sched_node (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    node_id        VARCHAR(64)   NOT NULL,
    host           VARCHAR(128),
    last_heartbeat TIMESTAMP     NULL,
    status         VARCHAR(16)   DEFAULT 'ONLINE',
    create_time    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_node_id UNIQUE (node_id)
);

-- 9. 触发去重（不重复执行的核心）
-- 唯一约束 uk_task_fire 保证同一 (task_id, fire_time) 全局只落一条，是跨节点防重的最终保障
CREATE TABLE sched_task_fire (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id     BIGINT        NOT NULL,
    fire_time   TIMESTAMP     NOT NULL,
    node_id     VARCHAR(64),
    create_time TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_task_fire UNIQUE (task_id, fire_time)
);
