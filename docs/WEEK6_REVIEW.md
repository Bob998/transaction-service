# 第六周复盘：Kafka与可靠消息

## 本周完成

- Docker Compose启动Kafka 3.9.1 KRaft单节点；
- 创建3分区的`transaction-events` Topic；
- 实现Producer、Consumer Group和交易号分区键；
- 实现交易创建到对账任务的事件驱动链路；
- 实现Transactional Outbox发布、并发领取、失败重试和陈旧锁恢复；
- 实现`processed_event`消费幂等；
- 实现Retry Topic、指数退避和DLT；
- 通过Kafka CLI和Actuator观察Consumer Lag；
- 普通测试关闭真实Kafka后台组件，可在CI独立运行；
- 全量53项测试通过。

## 最重要的认识

可靠消息不是一个配置项，而是一整条链路：

```text
数据库事务写Outbox
→ Publisher至少一次发送
→ Broker多副本持久化
→ Consumer幂等处理
→ 业务与下一事件同事务提交
→ Lag、重试和DLT监控
```

“Exactly Once”不能只靠Kafka口号实现。跨数据库和外部系统时，更实用的方案是至少一次投递、幂等消费、状态机和数据库约束共同保证业务正确性。

## 面试表达

项目使用Transactional Outbox解决交易写库与Kafka发送的双写一致性问题。Publisher通过`FOR UPDATE SKIP LOCKED`支持多实例并发发布，Producer启用`acks=all`与幂等发送。系统接受至少一次投递，Consumer使用`event_id + consumer_group`唯一约束去重，并将去重记录、业务处理和下一阶段Outbox放在同一数据库事务中。相同交易号作为Partition Key保证业务范围内顺序；失败进入Retry Topic，多次失败进入DLT；通过Actuator和Kafka CLI监控Lag。

## 后续可改进

- 将本地Kafka升级为多Broker、多副本实验；
- 为DLT增加管理接口、告警和安全重放工具；
- 为Outbox增加最大重试次数、失败终态和归档清理；
- 使用Prometheus与Grafana展示Lag、发送失败率和Outbox积压；
- 增加专用Kafka Testcontainers端到端测试。
