# 中英文项目介绍

## 中文30秒

这是一个面向金融交易场景的可靠交易与支付路由服务。它不只是CRUD：核心包括幂等、状态机、乐观锁、事务Outbox、Kafka幂等消费、Redis限流、动态路由、风控、对账和审计。工程侧有自动化测试、CI、Docker、Kubernetes和可观测性。我还用真实故障实验验证了并发、回滚、消息重复、通道超时、熔断切换和发布回滚。

## 中文3分钟主线

从请求进入开始，幂等键防止重复创建，Redis做限流和前置额度保护，但数据库仍是最终事实。交易通过严格状态机流转，乐观锁解决并发更新；状态变化和Outbox在同一个数据库事务提交，解决“数据库成功但消息没发”的双写窗口。Publisher把事件发到Kafka，交易号作为分区键，消费者通过processed_event去重，串起风控、路由、支付和对账。

路由会综合通道健康、成本和成功率；通道变慢会表现为P99升高，连续失败触发熔断，随后切到备用通道。关键动作进入审计日志，日终对账能生成差异记录。测试分为领域单元测试、Spring/PostgreSQL集成测试和API端到端测试，并由GitHub Actions自动执行。部署使用非root Docker镜像和Kubernetes，验证了readiness摘流、滚动更新、回滚、灰度以及HPA。这个项目最重要的价值，是每项可靠性设计都有测试、指标或故障实验作为证据。

## English — 30 seconds

This is a reliable transaction processing and payment-routing service for financial scenarios. It goes beyond CRUD by combining idempotency, a strict state machine, optimistic locking, transactional Outbox, idempotent Kafka consumers, Redis rate limiting, dynamic routing, risk controls, reconciliation and audit trails. It also includes automated testing, CI, containers, Kubernetes and observability, with failure experiments covering concurrency, rollback, duplicate delivery, channel timeout, failover and deployment rollback.

## English — 3 minutes

At the API boundary, an idempotency key prevents duplicate transactions. Redis provides rate limiting and fast pre-checks, but PostgreSQL remains the source of truth. Transactions follow a strict state machine, and optimistic locking prevents concurrent duplicate updates. Each business change and its Outbox event are committed in one database transaction, closing the database/message dual-write gap.

An Outbox publisher sends events to Kafka using the transaction ID as the partition key. Consumers record processed event IDs for idempotency and drive the risk, routing, payment and reconciliation stages. Routing considers channel health, cost and success rate. A slow channel raises tail latency; repeated failures open the circuit and traffic moves to a backup channel. All critical actions are audited and reconciliation records discrepancies.

The project has unit, integration and end-to-end tests automated by GitHub Actions. A multi-stage non-root image runs on Kubernetes, where I validated readiness-based traffic removal, rolling updates, rollback, canary traffic and HPA. The key value is not the technology list: every reliability claim is supported by a test, metric or failure experiment.
