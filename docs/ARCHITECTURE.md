# Transaction Service 架构

## 总体架构

```mermaid
flowchart TB
    U[客户端 / curl / Swagger] --> I[Ingress]
    I --> S[Kubernetes Service]
    S --> A1[Spring Boot Pod]
    S --> A2[Spring Boot Pod]

    subgraph app[交易服务]
      API[REST API] --> TX[交易状态机与事务]
      TX --> RISK[Aviator 风控]
      TX --> ROUTE[动态通道路由]
      TX --> OUTBOX[Outbox 写入]
      PUB[Outbox Publisher] --> KPROD[Kafka Producer]
      KCONS[幂等 Consumer] --> FLOW[风控→路由→支付→对账]
    end

    A1 --> API
    A2 --> API
    TX --> PG[(PostgreSQL\n最终事实/审计/对账)]
    TX --> REDIS[(Redis\n限流/额度/短期缓存)]
    OUTBOX --> PG
    PUB --> PG
    KPROD --> K[(Kafka)]
    K --> KCONS
    ROUTE --> CH[模拟支付通道]

    A1 --> PROM[Prometheus]
    A2 --> PROM
    PROM --> GRAF[Grafana]
    CI[GitHub Actions] --> IMG[Docker Image]
    IMG --> DEPLOY[Kubernetes Deployment]
```
## 正确性主线

1. PostgreSQL 保存交易、状态版本、Outbox、审计和对账结果，是最终事实源。
2. `@Transactional` 保证业务变化与 Outbox 事件同事务提交；乐观锁阻止并发重复更新。
3. Kafka 使用交易号作为分区键保持单笔交易范围内顺序；消费者用 `processed_event` 去重。
4. Redis 只做加速和前置保护，故障时降级到数据库逻辑，不能替代账本。
5. readiness 控制流量准入，滚动发布和回滚控制发布风险，指标用于定位而非仅展示。

## 关键链路

```mermaid
sequenceDiagram
    participant C as Client
    participant A as Transaction Service
    participant D as PostgreSQL
    participant K as Kafka
    participant P as Payment Channel
    C->>A: 创建交易（幂等键）
    A->>D: 额度/幂等检查 + 交易 + Outbox（同事务）
    A-->>C: transactionId
    A->>K: Outbox 异步发布
    K->>A: 风控/路由事件
    A->>P: 带超时的支付请求
    P-->>A: 成功/失败/未知
    A->>D: 状态、审计、账务与后续 Outbox
    K->>A: 发起对账
    A->>D: 对账结果/差异记录
```
