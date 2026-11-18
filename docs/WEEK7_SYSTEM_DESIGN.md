# 第7周：金融路由、风控与对账闭环设计

## 1. 目标

本周将前六周的 REST、PostgreSQL、事务、Redis、Kafka 和 Outbox 能力连接为一条可验证的金融交易链路：

```text
创建交易
  → 幂等检查
  → 用户额度与 Aviator 风控
  → 动态选择健康通道
  → 支付执行
  → 状态、账务流水与 Outbox 落库
  → Kafka 异步事件
  → 日终对账
  → 差异处理与审计
```

## 2. 核心模块

| 模块 | 主要职责 | 最终事实保存位置 |
|---|---|---|
| TransactionService | 幂等、状态机、事务边界 | `payment_transaction` |
| AviatorRiskEngine | 执行数据库配置的风控表达式 | `risk_rule`、`risk_evaluation` |
| DynamicChannelRouter | 按健康度、成本、成功率和优先级选通道 | `payment_channel` |
| PaymentExecutionService | 调用支付通道并区分成功、失败、未知 | `transaction_attempt` |
| LedgerEntryRepository | 交易成功后生成账务流水 | `ledger_entry` |
| OutboxEventPublisher | 将本地事务事件可靠发布到 Kafka | `outbox_event` |
| ReconciliationService | 比较本地成功交易与通道账单 | `reconciliation_*` |
| AuditLogRepository | 保存状态变化和差异处理记录 | `audit_log` |

## 3. 动态路由

路由只查询 `ACTIVE` 且健康状态为 `HEALTHY/DEGRADED` 的通道。评分由优先级、预计成本、失败率和降级惩罚组成，分数最低者胜出。

```text
score = priority
      + estimatedCost × 10
      + failureRate × 1000
      + degradedPenalty
```

当首选通道被标记为 `UNHEALTHY` 时，它会立即退出候选集合，Redis 中对应路由缓存也会被清除，下一笔交易自动选择备用通道。

## 4. 风控和额度

创建交易时先执行 Redis 滑动窗口，再加载用户画像和数据库统计，组成 Aviator 上下文：用户等级、本次金额、日累计金额、日限额、近期笔数和失败次数。

规则存放在数据库并带版本号，评估时保存表达式和上下文快照，便于解释“为什么拒绝”。Redis 只负责快速窗口控制，数据库仍是额度和交易事实来源。

## 5. 通道超时策略

支付请求超时不等于支付失败，因为通道可能已经扣款但响应丢失。因此系统：

- 不自动重试支付请求；
- 保持交易状态为 `PROCESSING`；
- 将尝试结果记录为 `UNKNOWN`；
- 后续通过通道查询或对账确定最终状态。

这避免了盲目重试造成重复扣款。通道幂等键仍是额外保护，但不能替代本地状态机和账务约束。

## 6. 账务一致性

交易从 `PROCESSING` 变为 `SUCCEEDED` 时，同一个 Spring 事务内写入 `ledger_entry`。任何一步失败都会整体回滚。

`(transaction_id, entry_type)` 唯一约束保证同一交易不会重复生成支付流水。账务记录和 PostgreSQL 是最终事实，Redis 与 Kafka 都不是账本。

## 7. 可靠事件

业务数据和 Outbox 事件在同一个数据库事务提交。后台发布器领取 `PENDING` 事件并发送 Kafka；发送失败会退回待重试状态。消费者使用 `processed_event` 唯一记录实现消费幂等。

Outbox 解决“数据库成功但消息丢失”，代价是增加轮询延迟、表清理、重试与监控复杂度。

## 8. 日终对账

对账比较本地 `SUCCEEDED` 交易和通道账单，产生三种差异：

- `CHANNEL_MISSING`：本地成功、通道无记录；
- `LOCAL_MISSING`：通道有记录、本地无交易；
- `AMOUNT_MISMATCH`：双方金额不同。

差异只能从 `OPEN` 转为 `RESOLVED` 或 `IGNORED`。处理时记录操作人、说明、时间，并写入审计日志；条件更新避免两名操作员重复处理。

## 9. 关键正确性保障

| 风险 | 保障措施 |
|---|---|
| 重复创建 | Redis 短期缓存 + 数据库幂等键唯一约束 |
| 并发状态更新 | 合法状态机 + version 乐观锁 |
| 重复记账 | 事务边界 + 账务唯一约束 |
| 通道故障 | 健康状态过滤 + 备用通道 |
| 通道超时 | UNKNOWN 状态，不盲目重试 |
| 消息丢失 | Transactional Outbox |
| 重复消费 | processed_event 唯一约束 |
| 账实不符 | 日终对账 + 差异处理 |
| 无法追责 | 风控快照 + audit_log |

## 10. 当前边界与后续改进

当前项目是可运行的学习版闭环。生产化还应增加通道主动查询、双分录会计模型、对账文件下载与校验、差异工单审批、规则灰度发布、路由指标回灌、分库分表和完整可观测性。
