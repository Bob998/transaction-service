# SQL 索引优化记录

## 优化场景

查询 USD 币种最近创建的 20 笔交易：

```sql
SELECT
    transaction_no,
    amount,
    currency,
    status,
    created_at
FROM payment_transaction
WHERE currency = 'USD'
ORDER BY created_at DESC
LIMIT 20;
```

## 索引前

- 扫描方式：Seq Scan
- 额外排序：top-N heapsort
- 扫描总行数：约 30,004
- 被过滤行数：20,004
- 数据块：549
- 执行时间：6.366 ms

## 添加索引

索引通过 Flyway 迁移 `V6__add_currency_created_at_index.sql` 创建：

```sql
CREATE INDEX idx_payment_transaction_currency_created_at
    ON payment_transaction (currency, created_at DESC);
```

## 索引后

- 扫描方式：Index Scan
- 额外排序：无
- 返回 20 行后停止
- 数据块：4（shared hit=2, read=2）
- 执行时间：0.221 ms

## 优化结论

联合索引同时支持了 `currency` 等值过滤和 `created_at DESC` 排序。
执行计划从全表顺序扫描加排序变为索引扫描，读取的数据块从 549
减少到 4，执行时间从 6.366 ms 降低到 0.221 ms，本次测试约提升
28.8 倍。

实际耗时会受到缓存、硬件和数据分布影响，因此优化判断不能只比较单次
耗时，还应同时观察扫描方式、扫描行数、排序步骤和缓冲区读取情况。

## B+ 树与联合索引

PostgreSQL 默认创建 B-tree 索引。该联合索引先按 `currency` 排序，
同一币种内部再按 `created_at DESC` 排序。因此数据库能够直接定位
`USD` 对应的索引范围，按时间倒序读取，并在取得 20 行后停止。

联合索引的字段顺序很重要。此索引适合以下查询形式：

```sql
WHERE currency = ?
ORDER BY created_at DESC
```

但它不能同等有效地支持只按 `created_at` 过滤且不提供 `currency`
条件的所有查询场景。

## 回表说明

优化后的执行计划为 `Index Scan`，而不是 `Index Only Scan`。索引中
只有 `currency` 和 `created_at`，查询还需要 `transaction_no`、
`amount` 和 `status`。PostgreSQL 通过索引定位记录后，仍要访问数据表
页面读取这些列，这个过程通常称为“回表”。

可以通过覆盖索引的 `INCLUDE` 包含额外返回列来减少回表，但覆盖索引会
占用更多磁盘空间并增加写入维护成本，应根据真实查询频率和性能瓶颈决定，
而不是为所有查询盲目增加列。
