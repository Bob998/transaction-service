# 第二周项目复盘：PostgreSQL 与数据建模

## 本周目标

将第一周基于内存的交易服务迁移到 PostgreSQL，完成数据库建模、迁移管理、幂等控制、索引设计和执行计划分析。

## 已完成内容

- 使用 Docker Compose 一键启动 PostgreSQL 17；
- 使用 Spring Boot 和 MyBatis 编写可见、可分析的 SQL；
- 使用 Flyway 自动初始化和升级数据库结构；
- 创建交易表、支付通道表、交易尝试表和审计日志表；
- 为对外交易号和客户端幂等键添加唯一约束；
- 创建主键、外键、检查约束和基础索引；
- 使用 `NUMERIC(19,4)` 与 Java `BigDecimal` 保存金额；
- 使用 `TIMESTAMPTZ` 与 Java `OffsetDateTime` 保存时间点；
- 创建 `DEMO` 支付通道并关联交易；
- 确认或取消交易时记录交易尝试和审计日志；
- 使用 Spring `@Transactional` 保证状态、尝试记录和审计日志同时成功或回滚；
- 将重复幂等键转换为统一的 `409 Conflict` 错误响应；
- 对真实 SQL 比较添加联合索引前后的执行计划；
- 编写数据模型说明和 SQL 优化记录；
- 保持 8 个自动化测试全部通过。

## 数据模型

核心业务表包括：

1. `payment_transaction`：保存交易主数据；
2. `payment_channel`：保存支付通道配置；
3. `transaction_attempt`：保存每次交易操作尝试；
4. `audit_log`：保存交易状态变化历史。

交易表使用数据库内部 `BIGSERIAL id` 关联其他表，同时使用独立的 `transaction_no` 作为对外交易号，避免暴露连续内部主键。

## 幂等设计

客户端创建交易时必须传入 `idempotencyKey`。数据库对 `idempotency_key` 建立唯一约束，作为并发情况下的最终保障。

重复提交相同幂等键时，PostgreSQL 拒绝插入，Spring 将异常转换为业务响应：

```text
HTTP 409 Conflict
DUPLICATE_IDEMPOTENCY_KEY
```

## 索引与执行计划

测试查询为：

```sql
SELECT transaction_no, amount, currency, status, created_at
FROM payment_transaction
WHERE currency = 'USD'
ORDER BY created_at DESC
LIMIT 20;
```

索引前：

- `Seq Scan`；
- 扫描约 30,004 行；
- 过滤 20,004 行；
- 使用 `top-N heapsort`；
- 读取 549 个缓冲区块；
- 执行时间 6.366 ms。

添加联合索引：

```sql
CREATE INDEX idx_payment_transaction_currency_created_at
    ON payment_transaction (currency, created_at DESC);
```

索引后：

- `Index Scan`；
- 无额外排序；
- 获取 20 行后停止；
- 读取 4 个缓冲区块；
- 执行时间 0.221 ms；
- 本次本地测试约提升 28.8 倍。

执行计划仍为 `Index Scan` 而非 `Index Only Scan`，因为查询返回的部分字段不在索引中，需要回表读取。

## 本周遇到的问题

### 1. 内存 Repository 改为 MyBatis 后测试无法编译

原测试直接调用 Repository 无参构造方法。数据库版本需要注入 Mapper，因此新增测试专用 `InMemoryTransactionMapper`，既保留快速单元测试，又让生产环境使用 PostgreSQL。

### 2. Maven 启动会先编译测试

`spring-boot:run` 在启动前执行 `test-compile`，因此测试代码构造方法不匹配会阻止服务启动。通过同步更新测试依赖解决。

### 3. 重复幂等键最初返回 500

数据库唯一约束已经生效，但接口缺少异常转换。Repository 捕获 `DuplicateKeyException` 并转换为 `DuplicateIdempotencyKeyException`，全局异常处理器最终返回 409。

### 4. 数据库内部主键出现在 API 响应中

交易尝试和审计日志需要内部主键，但客户端不应看到它。通过 Jackson `@JsonIgnore` 隐藏 `databaseId`，同时保留服务内部关联能力。

## 技术收获

- 理解 MyBatis Mapper、Repository 和 Service 的职责边界；
- 理解 Flyway 迁移文件只能追加版本，不应修改已执行迁移；
- 理解唯一约束不仅用于数据校验，也用于并发幂等保障；
- 理解 B-tree 联合索引的字段顺序与查询条件、排序的关系；
- 能够阅读 `Seq Scan`、`Index Scan`、`Sort`、`Buffers` 和 `Execution Time`；
- 理解索引扫描后的回表，以及覆盖索引的收益和写入成本；
- 理解金额不应使用浮点类型；
- 理解时间点应使用 `TIMESTAMPTZ` 和 `OffsetDateTime`；
- 理解多个数据库写操作需要事务保证一致性。

## 可用于简历的项目描述

使用 Java 17、Spring Boot、MyBatis 和 PostgreSQL 开发交易服务；通过 Flyway 管理交易、支付通道、交易尝试和审计日志等数据库结构；利用数据库唯一约束实现交易号与幂等键保护，并使用 Spring 事务保证状态更新、尝试记录和审计日志的一致性。针对按币种查询最近交易的 SQL 设计联合索引，通过 `EXPLAIN ANALYZE` 将执行计划从全表扫描与排序优化为索引扫描，本地测试读取缓冲区块从 549 降至 4。

## 下一步建议

- 使用 Testcontainers 建立不依赖手工启动数据库的集成测试；
- 为创建交易、状态更新和重复幂等键补充数据库集成测试；
- 将数据库账号密码迁移到环境变量；
- 使用 Jackson 安全序列化 JSONB，而不是手工拼接 JSON；
- 改进高并发下尝试序号生成方式；
- 增加分页查询和慢 SQL 监控。
