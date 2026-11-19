# Transaction Service

一个使用 Java 17、Spring Boot、MyBatis 和 PostgreSQL 实现的最小交易服务。

## 功能

- 创建交易
- 查询交易
- 确认交易
- 取消交易
- 请求参数校验
- 统一错误响应
- Swagger/OpenAPI 文档
- PostgreSQL 数据持久化
- Flyway 数据库迁移
- 唯一交易号和唯一幂等键
- 支付通道、交易尝试记录和审计日志
- 自动化测试

## 运行环境

- Java 17
- Spring Boot 3.5.7
- Maven Wrapper
- MyBatis
- PostgreSQL 17
- Flyway
- Docker Compose

## Docker Compose 一键启动

```bash
git clone <repository-url>
cd transaction-service
docker compose up --build -d
```

该命令会构建应用镜像并启动：

- transaction-app（Spring Boot）
- transaction-postgres（PostgreSQL）
- transaction-redis（Redis）
- transaction-kafka（Kafka）

检查全部服务状态：

```bash
docker compose ps
```

状态全部显示 `healthy` 后，应用可通过 `http://localhost:8080` 访问。停止服务：

```bash
docker compose down
```

默认使用 Docker volume 保存数据，普通的 `docker compose down` 不会删除数据库内容。

如需修改密码、端口或镜像版本，复制环境变量模板：

```bash
cp .env.example .env
```

Compose 中的应用通过容器服务名访问依赖，宿主机仍可通过 `localhost` 访问端口。

## 运行测试

测试使用 Testcontainers 自动启动隔离的 PostgreSQL 和 Redis：

```bash
./mvnw test
```

## 启动服务

```bash
./mvnw spring-boot:run
```

应用启动时，Flyway 会自动校验并执行尚未应用的数据库迁移。

## CI 流水线

GitHub Actions 在每次 push 和 pull request 时自动执行：

```text
编译 → 自动测试 → Checkstyle静态检查 → Compose校验 → 构建版本化镜像 → 非root校验
```

本地执行同一质量门禁：

```bash
./mvnw verify
```

CI 镜像使用 Git commit SHA 的前12位作为标签，例如：

```text
transaction-service:a1b2c3d4e5f6
```

服务默认运行在：

```text
http://localhost:8080
```

## API 文档

启动服务后访问：

- Swagger UI：http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON：http://localhost:8080/v3/api-docs

## API 列表

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/transactions` | 创建交易 |
| GET | `/api/transactions/{id}` | 查询交易 |
| POST | `/api/transactions/{id}/confirm` | 确认交易 |
| POST | `/api/transactions/{id}/cancel` | 取消交易 |

## 创建交易示例

请求：

```http
POST /api/transactions
Content-Type: application/json
```

```json
{
  "amount": 100.50,
  "description": "购买商品",
  "idempotencyKey": "order-20261112-0001",
  "currency": "CNY",
  "channelCode": "DEMO"
}
```

成功响应：

```json
{
  "id": "TXN-a5a9d058-0132-45e1-811b-7535e5b1eb7f",
  "amount": 100.5000,
  "description": "购买商品",
  "status": "PENDING",
  "createdAt": "2026-11-12T07:44:00Z",
  "idempotencyKey": "order-20261112-0001",
  "channelId": 1,
  "currency": "CNY",
  "updatedAt": "2026-11-12T07:44:00Z"
}
```

## 架构图

```mermaid
flowchart LR
    Client[客户端 / Swagger / curl]
    Controller[TransactionController]
    Service[TransactionService]
    Repository[Repository]
    Mapper[MyBatis Mapper / SQL]
    Database[(PostgreSQL)]
    Flyway[Flyway Migration]

    Client -->|HTTP 请求| Controller
    Controller --> Service
    Service --> Repository
    Repository --> Mapper
    Mapper --> Database
    Flyway -->|初始化与升级结构| Database
```

数据库包含四张业务表：

- `payment_transaction`：交易主表；
- `payment_channel`：支付通道；
- `transaction_attempt`：交易尝试历史；
- `audit_log`：状态变化审计记录。

## 错误响应

所有业务错误和参数校验错误使用统一结构：

```json
{
  "timestamp": "2026-11-11T19:29:53",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "请求参数校验失败",
  "errors": {
    "amount": "金额必须大于等于0.01",
    "description": "交易描述不能为空"
  }
}
```

### 错误代码

| HTTP 状态码 | 错误代码 | 说明 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 请求参数校验失败 |
| 404 | `TRANSACTION_NOT_FOUND` | 交易不存在 |
| 404 | `CHANNEL_NOT_AVAILABLE` | 支付通道不存在或已停用 |
| 409 | `INVALID_TRANSACTION_STATE` | 当前交易状态不允许执行该操作 |
| 409 | `DUPLICATE_IDEMPOTENCY_KEY` | 幂等键已经使用，拒绝重复创建 |

## 数据库文档

- [数据模型说明](docs/DATA_MODEL.md)
- [SQL 索引优化记录](docs/SQL_OPTIMIZATION.md)

## 监控与故障排查

`docker compose up --build -d` 会同时启动 Prometheus、Grafana 和 Kafka Exporter：

- Grafana：`http://localhost:3000`（默认 `admin/admin`）；
- Prometheus：`http://localhost:9091`；
- 应用指标：`http://localhost:8080/actuator/prometheus`。

指标设计和慢通道熔断实验见
[第9周可观测性文档](docs/WEEK9_OBSERVABILITY.md)。

## 第10周：JMeter、JVM与线程池调优

- 参数化JMeter交易全链路压测；
- 线程池耗尽、队列积压、数据库连接池耗尽、慢SQL实验；
- 受上限保护的内存增长与CPU压力实验；
- G1 GC、Tomcat、Hikari、CPU、GC和自定义线程池指标；
- 容器内置 `jcmd`、`jstack`、`jmap`、`top` 与 `ps`；
- 优化前后使用同一负载复测并记录新的系统容量边界。

详见 [第10周容量与调优报告](docs/WEEK10_PERFORMANCE_TUNING.md)。
