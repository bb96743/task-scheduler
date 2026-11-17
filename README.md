# 分布式任务调度中心

一套可运行、可演示、可验收的分布式定时任务调度中心，解决"定时任务散落各系统、重复执行、失败无人知"的问题。

- **不重复执行**：数据库唯一约束 + 原子抢占，双节点部署下同一任务同一时刻只在一个节点执行
- **失败可闭环**：可配重试（指数退避）→ 连续失败告警 → 手工补偿（有边界、幂等）
- **分片并行**：大任务拆 N 个分片并行执行，结果与耗时逐片可查
- **开箱即用**：`demo` profile 使用 H2 内存库，一条命令启动，无需安装中间件

---

## 技术栈

| 层 | 选型 |
|----|------|
| 后端 | Java 21 · Spring Boot 3.5.14 · MyBatis-Plus 3.5.14 · springdoc-openapi 2.8.9 |
| 存储 | MySQL 8（正式）/ H2 2.3（demo 演示） |
| 前端 | Vue 3 · Element Plus · Vite 6 · Axios |
| 测试 | JUnit 5 · Mockito · JaCoCo 0.8.12 |

> 分布式锁方案：**不依赖 Redis**，用数据库原子插入 + 唯一约束实现跨节点互斥，干净机器可直接跑。

---

## 目录结构

```
task-scheduler/
├─ backend/                       后端（Spring Boot）
│  ├─ src/main/java/com/intematrix/scheduler/
│  │  ├─ core/                    调度引擎、执行器、重试、告警、心跳
│  │  ├─ controller/              REST 接口
│  │  ├─ service/                 业务服务
│  │  ├─ entity/ mapper/ dto/     数据模型
│  │  ├─ handler/                 示例任务处理器
│  │  └─ config/                  线程池、MyBatis-Plus 配置
│  ├─ src/main/resources/db/
│  │  ├─ mysql/schema.sql         MySQL 建表脚本
│  │  └─ h2/schema.sql + data.sql H2 建表 + 5 个示例任务
│  └─ src/test/java/              单元测试
├─ frontend/                      管理台（Vue 3）
├─ docs/                          体验路径、双节点实测记录、截图
└─ README.md
```

---

## 快速开始

### 方式一：demo 一键启动（推荐，零依赖）

需要 JDK 21。前端已构建进 jar，无需 Node。

```bash
cd backend
mvn -DskipTests package
java -jar target/task-scheduler-1.0.0.jar --spring.profiles.active=demo
```

打开：

- 管理台：http://localhost:8080/
- Swagger：http://localhost:8080/swagger-ui.html
- H2 控制台：http://localhost:8080/h2-console （JDBC URL `jdbc:h2:mem:scheduler`，用户 `sa`，密码空）

demo 启动即预置 5 个示例任务，调度自动开始。

### 方式二：MySQL 正式环境

```bash
# 1. 建库并导入表结构
mysql -uroot -p -e "CREATE DATABASE scheduler DEFAULT CHARACTER SET utf8mb4;"
mysql -uroot -p scheduler < backend/src/main/resources/db/mysql/schema.sql

# 2. 启动（账号密码用环境变量注入，不写死在代码里）
DB_HOST=localhost DB_PORT=3306 DB_NAME=scheduler \
DB_USER=root DB_PASSWORD=yourpwd \
java -jar backend/target/task-scheduler-1.0.0.jar --spring.profiles.active=mysql
```

### 前端开发模式

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173，/api 自动代理到 8080
npm run build    # 产物输出到 backend/src/main/resources/static
```

---

## 接口清单

| # | 方法 | 路径 | 说明 |
|---|------|------|------|
| 1 | POST | `/api/tasks` | 任务注册 |
| 2 | GET | `/api/tasks` | 任务列表 |
| 3 | GET | `/api/tasks/{id}` | 任务详情 |
| 4 | PUT | `/api/tasks/{id}/status` | 启用 / 停用 |
| 5 | POST | `/api/tasks/{id}/run` | 立即执行 |
| 6 | GET | `/api/tasks/{id}/executions` | 执行记录（按任务与时间） |
| 7 | PUT | `/api/tasks/{id}/shards` | 分片配置 |
| 8 | PUT | `/api/tasks/{id}/alert` | 告警配置 |
| 9 | GET | `/api/tasks/{id}/alert` | 查询告警配置 |
| 10 | GET | `/api/executions/{id}` | 执行详情 |
| 11 | GET | `/api/executions/{id}/logs` | 日志查询（按级别与时间） |
| 12 | GET | `/api/executions/{id}/shards` | 分片明细 |
| 13 | POST | `/api/executions/{id}/retry` | 手动重试 |
| 14 | POST | `/api/executions/{id}/compensate` | 手工补偿 |
| 15 | GET | `/api/nodes` | 节点列表 |
| 16 | GET | `/api/alerts` | 告警记录 |

统一响应体：`{ code, message, data, timestamp }`，`code=0` 表示成功。

错误码：1001 任务不存在 / 1002 任务名重复 / 1004 cron 非法 / 1005 处理器不存在 / 2001 执行不存在 / 2002 非失败终态不可补偿 / 2003 已补偿 / 2004 超出补偿窗口 / 2005 执行中不可操作。

---

## 核心机制

### 1. 不重复执行（双节点）

每次触发先向 `sched_task_fire` 插入 `(task_id, fire_time)`：

```
唯一约束 uk_task_fire 冲突 → 别的节点已抢到，跳过
插入成功               → 本节点获得执行权，创建执行实例并派发
```

`sched_task_execution` 上另有 `uk_exec_task_fire(task_id, fire_time)` 作为第二道保险。两道唯一约束都在数据库层，跨节点强一致。

### 2. 分片并行

执行实例按 `shard_total` 拆成 N 条 `sched_task_shard`，用独立线程池并行执行，逐片记录状态、耗时、结果摘要，结束后汇总成功/失败数写回执行实例。

### 3. 失败重试

失败 → 写 `sched_retry_record`，间隔按 `retry_interval × 2^(n-1)` 指数退避；重试扫描器到期重新派发。重试序号通过执行记录的 `retry_count` 串联，超过 `retry_max` 转终态失败。

### 4. 告警

**仅在终态失败**（重试耗尽）时判断；同一任务连续失败达到阈值 → 写告警记录，5 分钟内去重避免刷屏。

### 5. 手工补偿（有边界）

- 仅 `FAILED` 终态可补偿
- 仅补偿 7 天内（`scheduler.compensation-window-days` 可配）
- 同一执行只能补偿一次（幂等）
- 补偿生成新执行（`trigger_type=COMPENSATE`，记录 `compensated_from`），原记录保留可追溯

---

## 测试与覆盖率

```bash
cd backend
mvn test          # 62 个用例
```

覆盖率（JaCoCo，报告见 `backend/target/site/jacoco/index.html`）：

| 范围 | 指令覆盖率 | 行覆盖率 |
|------|-----------|---------|
| core + service（核心逻辑） | **88.1%** | 91.9% |
| core | 87.0% | 90.6% |
| service | 90.5% | 94.7% |
| 全项目 | 77.5% | 79.4% |

---

## 双节点部署验证

```bash
# 节点 A
java -jar target/task-scheduler-1.0.0.jar --spring.profiles.active=nodes \
  --server.port=8081 --scheduler.node-id=node-a

# 节点 B（同一数据库，不重复初始化）
java -jar target/task-scheduler-1.0.0.jar --spring.profiles.active=nodes \
  --server.port=8082 --scheduler.node-id=node-b --spring.sql.init.mode=never
```

`nodes` profile 使用 H2 文件库 + `AUTO_SERVER=TRUE`。生产环境换成同一 MySQL 即可。

验证结论见 [`docs/双节点实测记录.md`](docs/双节点实测记录.md)。

---

## 配置项

| 配置 | 默认 | 说明 |
|------|------|------|
| `scheduler.node-id` | node-1 | 节点标识（双节点必须不同） |
| `scheduler.scan-interval-ms` | 1000 | 调度扫描间隔 |
| `scheduler.heartbeat-interval-ms` | 5000 | 心跳间隔 |
| `scheduler.node-timeout-ms` | 30000 | 节点离线判定超时 |
| `scheduler.compensation-window-days` | 7 | 补偿时间窗口（天） |
| `scheduler.executor-pool-size` | 8 | 线程池基数 |
| `scheduler.retry-scan-interval-ms` | 5000 | 重试扫描间隔 |

所有敏感项（数据库账号密码）通过环境变量注入，源码中无硬编码密钥。

---

## 示例任务处理器

| 处理器 | 行为 |
|--------|------|
| `demoSuccessHandler` | 每次成功，耗时约 300ms |
| `demoFailHandler` | 固定失败，用于观察重试与告警 |
| `demoShardHandler` | 每分片耗时约 800ms，用于观察分片并行 |
| `demoFlakyHandler` | 约一半概率失败，用于观察重试成功/最终失败 |

业务接入方式：实现 `com.intematrix.scheduler.core.TaskHandler` 并注册为 Spring Bean，Bean 名即 `handlerName`。

---

## 已知边界

- 调度精度为秒级；扫描间隔 1s，触发时刻误差取决于扫描周期
- 分片为单节点内并行，暂不支持跨节点分片分发
- 告警通道目前落地为 LOG + 记录表，WEBHOOK 为预留扩展点
- demo 使用 H2 内存库，重启数据清空；需要持久化请用 MySQL 或 `nodes` 的 H2 文件模式
