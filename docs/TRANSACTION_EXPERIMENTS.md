# Spring 事务与并发实验

## 1. 事务传播

Spring 默认传播行为是：

```java
@Transactional(propagation = Propagation.REQUIRED)
```

`REQUIRED` 的含义：

- 当前存在事务：加入当前事务；
- 当前没有事务：创建新事务。

本项目的回滚实验中：

```text
TransactionFailureExperimentService.confirmThenFail()
    → TransactionService.confirm()
```

外层方法先开启事务，`confirm()` 加入该事务。最后外层抛出异常，因此：

- 交易状态回滚；
- 交易尝试记录回滚；
- 审计日志回滚；
- Outbox 事件回滚。

## 2. 事务回滚规则

Spring 默认在以下异常发生时回滚：

- `RuntimeException`；
- `Error`。

默认不会因为普通受检异常自动回滚。如需对受检异常回滚，可以使用：

```java
@Transactional(rollbackFor = Exception.class)
```

本项目使用 `IllegalStateException` 模拟运行时故障，所以事务自动回滚。

## 3. 事务自调用失效

以下调用不会经过 Spring 代理：

```java
public void outerMethod() {
    this.transactionalMethod();
}

@Transactional
public void transactionalMethod() {
}
```

由于 `this` 是当前对象本身，而不是 Spring 代理对象，因此 `@Transactional` 不会生效。

实验结果：

```text
外部通过代理调用事务方法：事务有效
同一个类内部自调用：事务无效
调用另一个 Bean 的事务方法：事务有效
```

推荐修复方式：

- 将事务方法拆到另一个 Spring Bean；
- 通过依赖注入调用该 Bean；
- 避免使用 `this` 调用事务方法。

## 4. 乐观锁

交易表使用：

```text
version BIGINT NOT NULL DEFAULT 0
```

更新时执行：

```sql
UPDATE payment_transaction
SET status = :status,
    version = version + 1
WHERE transaction_no = :transactionNo
  AND version = :version;
```

如果两个请求都读取 `version = 0`：

```text
请求 A 更新成功：version 0 → 1
请求 B 仍使用 version 0：更新行数为 0
```

请求 B 抛出：

```text
OptimisticLockConflictException
```

乐观锁适合：

- 冲突概率相对较低；
- 读取频繁；
- 不希望长期持有数据库锁的业务。

## 5. 并发确认实验

实验使用两个线程，同时确认同一笔 `CREATED` 交易。

验证结果：

```text
成功确认线程数 = 1
最终状态 = RISK_APPROVED
成功确认记录数 = 1
Outbox 事件数 = 1
```

失败线程可能遇到：

- 乐观锁冲突；
- 非法状态转换。

无论线程执行顺序如何，一笔交易都不会被重复确认。

## 6. Outbox 一致性

状态更新与 Outbox 写入位于同一个本地数据库事务中：

```text
更新交易
写尝试记录
写审计日志
写 Outbox
提交事务
```

这样可以避免：

```text
业务数据提交成功
但消息发送失败
```

业务事务只负责可靠写入 `outbox_event`。后续发布程序读取 `PENDING` 事件，发布成功后再更新为 `PUBLISHED`。

## 7. 实验结论

本周实现了：

- 显式交易状态机；
- 合法状态转换检查；
- Spring 声明式事务；
- 事务回滚验证；
- 乐观锁版本控制；
- 并发重复确认防护；
- 事务自调用失效实验；
- Outbox 事件原子写入。
