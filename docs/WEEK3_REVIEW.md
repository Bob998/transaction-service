# 第三周项目复盘：Spring 事务与并发状态更新

## 本周目标

在 PostgreSQL 交易服务上实现可靠的交易状态机，使用 Spring 事务保证多表写入一致性，使用乐观锁防止并发重复更新，并使用 Outbox 表可靠记录待发布的业务事件。

## 已完成内容

- 将交易状态扩展为 `CREATED`、`RISK_APPROVED`、`PROCESSING`、`SUCCEEDED`、`FAILED` 和 `CANCELLED`；
- 限制每个业务操作允许的来源状态，拒绝非法状态转换；
- 为交易表增加 `version` 字段并实现乐观锁更新；
- 将状态、尝试记录、审计日志和 Outbox 事件放入同一事务；
- 创建 `outbox_event` 表并为待发布事件建立索引；
- 验证事务中途抛出运行时异常时所有写入一起回滚；
- 使用两个线程模拟同一笔交易被并发确认；
- 验证只有一次确认、一次成功尝试和一条 Outbox 事件；
- 使用两份相同版本的快照稳定复现乐观锁冲突；
- 验证 Spring `@Transactional` 自调用失效；
- 验证拆分到另一个 Spring Bean 后事务恢复生效；
- 编写状态机图和事务实验说明；
- 将自动化测试扩充至 17 个。

## 状态机

合法主流程为：

```text
CREATED → RISK_APPROVED → PROCESSING → SUCCEEDED
                                      ↘ FAILED
```

允许取消的路径为：

```text
CREATED → CANCELLED
RISK_APPROVED → CANCELLED
```

`SUCCEEDED`、`FAILED` 和 `CANCELLED` 是终态，不能继续转换。详细设计见 `docs/STATE_MACHINE.md`。

## 事务一致性

每次状态转换在一个本地数据库事务中依次执行：

1. 使用版本号更新交易状态；
2. 写入交易尝试记录；
3. 写入审计日志；
4. 写入 Outbox 事件；
5. 统一提交事务。

任何一步抛出运行时异常，Spring 都会回滚整个事务，从而避免交易状态已经改变但审计或事件丢失。

## Outbox 设计

业务事务不直接依赖外部消息系统，而是先将事件写入 `outbox_event`。事件初始状态为 `PENDING`，后续发布程序可以读取并发布，再将状态更新为 `PUBLISHED`。

当前记录的事件包括：

- `TRANSACTION_RISK_APPROVED`；
- `TRANSACTION_PROCESSING`；
- `TRANSACTION_SUCCEEDED`；
- `TRANSACTION_FAILED`；
- `TRANSACTION_CANCELLED`。

这种设计解决的是业务数据提交与事件记录之间的原子性问题。

## 乐观锁与并发确认

更新交易时使用条件：

```sql
WHERE transaction_no = :transactionNo
  AND version = :version
```

更新成功后数据库执行 `version = version + 1`。如果另一个请求仍携带旧版本，SQL 影响行数为零，系统抛出 `OptimisticLockConflictException`。

并发实验使用两个线程同时确认同一笔交易，最终结果为：

```text
成功确认数 = 1
最终状态 = RISK_APPROVED
成功确认尝试数 = 1
Outbox 事件数 = 1
```

## 事务传播与自调用

Spring 默认的 `REQUIRED` 传播行为表示：有事务时加入现有事务，没有事务时创建新事务。

`this.transactionalMethod()` 属于同类自调用，没有经过 Spring 代理，因此方法上的 `@Transactional` 不生效。实验通过 `TransactionSynchronizationManager` 验证了以下结果：

```text
通过 Spring 代理外部调用：事务有效
同一个类内部自调用：事务无效
调用另一个 Spring Bean：事务有效
```

本项目采用“拆分到另一个 Bean”的方式演示推荐修复方案。

## 本周遇到的问题

### 1. INSERT 字段和值数量不一致

交易表新增 `version` 后，INSERT 列表包含 10 个字段但 VALUES 只有 9 个表达式。补充 `#{version}` 后恢复正常。

### 2. 新交易版本为 NULL

数据库默认值不会覆盖显式插入的 NULL。将 Java 模型中的 `version` 初始化为 `0L`，确保新交易满足非空约束。

### 3. Outbox 查询没有数据

建表不会自动为历史交易生成事件，只有重启最新代码并执行新的状态转换才会写入 Outbox。

### 4. 端口被旧服务占用

旧 Java 进程仍监听 8080，使用 `lsof` 定位 PID，优先使用普通 `kill` 优雅停止，必要时才使用 `kill -9`。

### 5. 自调用实验方法遗漏

添加跨 Bean 修复实验时遗漏了原有内部调用方法，导致测试无法编译。补回方法后，三种调用场景均验证通过。

## 技术收获

- 能够用显式状态机表达交易生命周期；
- 理解数据库事务对多表一致性的保障；
- 理解 `REQUIRED` 传播行为与运行时异常回滚规则；
- 理解 Spring AOP 代理与事务自调用失效原因；
- 能够使用乐观锁防止并发旧数据覆盖；
- 能够编写多线程集成测试验证并发业务约束；
- 理解 Outbox 模式解决的原子写入问题及其边界。

## 可用于简历的项目描述

基于 Spring Boot、MyBatis 与 PostgreSQL 实现交易状态机，通过 `@Transactional` 保证交易状态、尝试记录、审计日志与 Outbox 事件的原子提交；使用版本号乐观锁处理并发状态更新，并编写双线程集成测试验证同一交易只会被确认一次。通过故障注入验证事务回滚，同时复现并解释 Spring 事务自调用失效及跨 Bean 修复方式。

## 后续方向

- 实现 Outbox 事件定时发布与失败重试；
- 为事件发布增加幂等消费设计；
- 使用 Testcontainers 隔离集成测试数据库；
- 将手工 JSON 拼接替换为 Jackson 序列化；
- 增加状态转换指标、日志和告警。
