# 简历项目描述

## 中文版

**可靠交易处理与支付路由平台｜Java 17 / Spring Boot / PostgreSQL / Redis / Kafka / Kubernetes**

- 设计交易状态机、幂等键与乐观锁，使用 Spring 事务保证业务状态和 Outbox 事件原子提交，避免并发重复确认。
- 建设 Kafka 事件链路与消费者幂等机制，实现风控、通道路由、支付和对账闭环，并支持重试、死信与积压监控。
- 使用 Redis 实现限流、滑动窗口、额度与短期缓存，同时坚持 PostgreSQL 为最终事实源并设计故障降级。
- 实现基于健康度、成本和成功率的动态路由、Aviator 风控规则、熔断切换、审计及日终对账差异发现。
- 建立 JUnit/Mockito/Testcontainers/WireMock 测试体系及 GitHub Actions 流水线；本地实验共 72 项测试通过、Checkstyle 0违规。
- 通过 Prometheus/Grafana、JMeter、jstack/jcmd/jmap 定位连接池与CPU瓶颈；实验中完整交易吞吐从84.8提升至109.4笔/秒，P99从2116ms降至334ms。
- 完成多阶段非root镜像与Kubernetes部署，验证readiness摘流、滚动发布、失败回滚、灰度流量及HPA 2→6扩容。

> 面试时应明确：这是个人工程实战与本地实验数据，不描述为生产业绩。

## English version

**Reliable Transaction Processing and Payment Routing Platform | Java 17, Spring Boot, PostgreSQL, Redis, Kafka, Kubernetes**

- Designed a transaction state machine with idempotency keys and optimistic locking; used Spring transactions to atomically persist business changes and Outbox events.
- Built an event-driven risk, routing, payment and reconciliation flow with Kafka, idempotent consumers, retries, DLQ handling and lag monitoring.
- Implemented Redis-based rate limiting, sliding windows, quota control and short-lived caches while keeping PostgreSQL as the source of truth with graceful degradation.
- Developed health/cost/success-rate-aware routing, Aviator-based risk rules, circuit-breaker failover, audit trails and reconciliation discrepancy detection.
- Established automated tests with JUnit, Mockito, Testcontainers and WireMock plus GitHub Actions; the local verified suite passed 72 tests with zero Checkstyle violations.
- Diagnosed database-pool and CPU bottlenecks using Prometheus/Grafana, JMeter and JVM tools; improved experimental full-transaction throughput from 84.8 to 109.4 tx/s and P99 from 2116ms to 334ms.
- Containerized the service as a multi-stage non-root image and validated Kubernetes readiness removal, rolling updates, rollback, canary traffic and HPA scaling from 2 to 6 replicas.
