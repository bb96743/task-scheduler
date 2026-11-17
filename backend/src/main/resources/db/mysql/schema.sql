-- 分布式任务调度中心 · MySQL 8 建表脚本
-- 使用前先建库：CREATE DATABASE scheduler DEFAULT CHARACTER SET utf8mb4;

USE scheduler;

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
    task_name       VARCHAR(128)  NOT NULL COMMENT '任务名（唯一）',
    task_group      VARCHAR(64)   DEFAULT 'DEFAULT' COMMENT '分组',
    cron_expression VARCHAR(64)   NOT NULL COMMENT 'cron 表达式',
    handler_name    VARCHAR(128)  NOT NULL COMMENT '处理器 bean 名',
    shard_total     INT           DEFAULT 1 COMMENT '分片总数',
    retry_max       INT           DEFAULT 3 COMMENT '最大重试次数',
    retry_interval  INT           DEFAULT 5 COMMENT '重试间隔基数(秒)',
    timeout_ms      INT           DEFAULT 60000 COMMENT '单次执行超时(ms)',
    status          VARCHAR(16)   DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    next_fire_time  DATETIME      NULL COMMENT '下次触发时间',
    last_fire_time  DATETIME      NULL COMMENT '上次触发时间',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本',
    remark          VARCHAR(512),
    deleted         TINYINT       DEFAULT 0,
    create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_name (task_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务定义';

-- 2. 执行实例
CREATE TABLE sched_task_execution (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id        BIGINT        NOT NULL,
    fire_time      DATETIME      NOT NULL COMMENT '触发时刻',
    trigger_type   VARCHAR(16)   DEFAULT 'CRON' COMMENT 'CRON/MANUAL/RETRY/COMPENSATE',
    status         VARCHAR(16)   DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED',
    node_id        VARCHAR(64)   COMMENT '执行节点',
    shard_total    INT           DEFAULT 1,
    success_shard  INT           DEFAULT 0,
    failed_shard   INT           DEFAULT 0,
    start_time     DATETIME      NULL,
    end_time       DATETIME      NULL,
    duration_ms    BIGINT        DEFAULT 0,
    error_message  VARCHAR(1000),
    compensated_from BIGINT      NULL COMMENT '补偿来源执行ID',
    retry_count    INT           DEFAULT 0 COMMENT '重试链序号',
    create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_exec_task_fire (task_id, fire_time),
    KEY idx_exec_task (task_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行实例';

-- 3. 执行日志
CREATE TABLE sched_task_exec_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id  BIGINT        NOT NULL,
    task_id       BIGINT        NOT NULL,
    shard_index   INT           DEFAULT 0,
    log_level     VARCHAR(8)    DEFAULT 'INFO',
    message       VARCHAR(2000),
    create_time   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    KEY idx_log_exec (execution_id),
    KEY idx_log_task_time (task_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行日志';

-- 4. 分片
CREATE TABLE sched_task_shard (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id   BIGINT        NOT NULL,
    shard_index    INT           NOT NULL,
    shard_total    INT           NOT NULL,
    status         VARCHAR(16)   DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    result_summary VARCHAR(1000),
    start_time     DATETIME      NULL,
    end_time       DATETIME      NULL,
    duration_ms    BIGINT        DEFAULT 0,
    create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    KEY idx_shard_exec (execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分片';

-- 5. 告警配置
CREATE TABLE sched_alert_config (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id        BIGINT        NOT NULL,
    fail_threshold INT           DEFAULT 3 COMMENT '连续失败阈值',
    notify_channel VARCHAR(64)   DEFAULT 'LOG' COMMENT 'LOG/WEBHOOK',
    webhook_url    VARCHAR(512),
    enabled        TINYINT       DEFAULT 1,
    create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_alert_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警配置';

-- 6. 告警记录
CREATE TABLE sched_alert_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         BIGINT        NOT NULL,
    execution_id    BIGINT        NULL,
    consecutive_fail INT          NOT NULL,
    message         VARCHAR(1000),
    notify_channel  VARCHAR(64),
    dedup_key       VARCHAR(64)   NOT NULL,
    triggered_at    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    KEY idx_alert_task (task_id, triggered_at),
    UNIQUE KEY uk_alert_dedup (dedup_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警记录';

-- 7. 重试队列
CREATE TABLE sched_retry_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id    BIGINT        NOT NULL,
    task_id         BIGINT        NOT NULL,
    retry_count     INT           DEFAULT 0,
    next_retry_time DATETIME      NOT NULL,
    status          VARCHAR(16)   DEFAULT 'PENDING' COMMENT 'PENDING/DONE/EXHAUSTED',
    create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_retry_time (status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='重试队列';

-- 8. 节点心跳
CREATE TABLE sched_node (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    node_id        VARCHAR(64)   NOT NULL,
    host           VARCHAR(128),
    last_heartbeat DATETIME      NULL,
    status         VARCHAR(16)   DEFAULT 'ONLINE',
    create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_id (node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点心跳';

-- 9. 触发去重（不重复执行核心）
CREATE TABLE sched_task_fire (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id     BIGINT        NOT NULL,
    fire_time   DATETIME      NOT NULL,
    node_id     VARCHAR(64),
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_fire (task_id, fire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='触发去重';
