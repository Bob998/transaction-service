# 第10周：容量、JVM与线程池调优报告

## 1. 目标

本周不以“背JVM参数”为目标，而是建立一套可重复的性能排查闭环：

```text
建立基线 → 制造单一瓶颈 → 用指标定位 → 用JVM/Linux工具取证
→ 修改容量参数或代码 → 相同流量复测 → 明确新的容量边界
```

所有故障接口都位于 `/api/operations/performance`，默认关闭。Docker学习环境通过
`PERFORMANCE_LAB_ENABLED=true` 开启，并限制任务数量、持续时间和内存上限。

## 2. 当前容量模型

| 资源 | 默认配置 | 作用 |
|---|---:|---|
| JVM Heap | 256MB～512MB | G1管理的Java堆 |
| 容器内存 | 768MB | 堆、Metaspace、线程栈、直接内存共同使用 |
| Tomcat线程 | 10～100 | 处理HTTP请求 |
| Tomcat等待队列 | 100 | 工作线程繁忙时暂存连接 |
| Hikari连接池 | 优化前2～10，优化后2～20 | 限制同时执行数据库操作的连接数 |
| 性能实验线程池 | core=4, max=8, queue=20 | 观察线程耗尽与拒绝 |
| 数据库压力线程 | 20 | 制造10个活动连接与等待连接 |

容量必须沿最窄环节评估。Tomcat允许100个并发线程，不代表数据库也能同时执行100条SQL；
当Hikari只有10条连接时，其余请求会等待连接，等待超过2秒就失败。

## 3. 可重复实验

### 3.1 JMeter基线与过载

基线：10线程、60秒。

```bash
JMETER_THREADS=10 JMETER_RAMP_SECONDS=10 JMETER_DURATION_SECONDS=60 \
docker compose --profile load-test run --rm jmeter
```

过载：100线程、120秒。

```bash
JMETER_THREADS=100 JMETER_RAMP_SECONDS=20 JMETER_DURATION_SECONDS=120 \
docker compose --profile load-test run --rm jmeter
```

对比RPS、错误率、P95/P99、Tomcat busy、Hikari pending、CPU、GC暂停和Kafka Lag。

### 3.2 线程池耗尽与队列积压

```bash
curl -X POST \
  "http://localhost:18080/api/operations/performance/thread-pool?tasks=40&durationMs=5000"
curl http://localhost:18080/api/operations/performance | jq
```

默认线程池最多同时运行8个任务、队列容纳20个任务。因此40个任务会出现：

```text
8 active + 20 queued + 12 rejected
```

这证明有界队列和拒绝策略能保护进程，代价是超出容量的任务必须快速失败或上游限流。

### 3.3 数据库连接池耗尽

```bash
curl -X POST \
  "http://localhost:18080/api/operations/performance/database-pool?tasks=20&durationMs=5000"
```

20个线程同时申请10条Hikari连接。预期看到：

- `hikaricp_connections_active` 接近10；
- `hikaricp_connections_pending` 上升；
- 等待超过2秒的任务失败；
- HTTP线程或实验线程继续堆积时，P99上升。

### 3.4 慢SQL

```bash
time curl -X POST \
  "http://localhost:18080/api/operations/performance/slow-query?durationMs=2000"
```

实验使用PostgreSQL `pg_sleep` 模拟慢SQL，并记录
`performance_lab_database_query_duration_seconds` 直方图。真实排查还应结合
`pg_stat_activity`、`EXPLAIN (ANALYZE, BUFFERS)` 和慢查询日志。

### 3.5 内存增长与GC

```bash
curl -X POST \
  "http://localhost:18080/api/operations/performance/memory?megabytes=32"
curl -X DELETE \
  http://localhost:18080/api/operations/performance/memory
```

每次最多保留128MB，防止学习实验直接造成OOM。观察Heap、GC暂停和
`performance_lab_memory_retained_bytes`，释放引用后等待G1回收。

### 3.6 CPU压力

```bash
curl -X POST \
  "http://localhost:18080/api/operations/performance/cpu?tasks=8&durationMs=5000"
```

观察 `process_cpu_usage`、系统CPU、HTTP P99和线程栈。CPU饱和时继续增加线程通常只会
增加上下文切换，不会提高吞吐量。

### 3.7 第三方接口延迟

复用第9周通道故障接口：

```bash
curl -X POST \
  "http://localhost:18080/api/operations/channels/SAFE_PAY/fault?delayMs=1500"
```

观察通道P99、Tomcat busy、超时、熔断和路由切换。超时结果是UNKNOWN，不能盲目重试。

## 4. JVM与Linux取证

应用镜像使用JDK而不是JRE，保留 `jcmd`、`jstack`、`jmap`；同时安装 `top` 和 `ps`。

```bash
./diagnostics/capture-jvm.sh
docker stats transaction-app
docker exec transaction-app top -H -b -n 1
```

重点判断：

- 大量线程停在Hikari `getConnection`：数据库连接池瓶颈；
- 大量线程停在HTTP Socket读取：第三方通道慢；
- RUNNABLE线程持续占CPU：CPU热点；
- Old区持续增长且Full GC后不下降：可能存在内存泄漏；
- 队列持续增长且完成速率低于进入速率：系统已超过稳定容量。

## 5. 实测结果与优化前后对比

2026-11-19在同一台开发机、同一JMeter完整交易脚本下完成60秒测试。每笔完整交易包含
创建、确认、进入处理和执行支付四个HTTP请求。

| 场景 | 完整交易数 | 完整交易吞吐 | HTTP吞吐 | 错误率 | 平均耗时 | P95 | P99 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 优化前：连接池10、并发10 | 5,195 | 84.8/s | 约339/s | 0.96%（50次） | 98ms | 224ms | 2,116ms |
| 只扩大连接池：连接池20、并发10 | 5,547 | 92.5/s | 约370/s | 0.69%（38次） | 86.8ms | 220ms | 1,062ms |
| 调整稳定并发：连接池20、并发5 | 6,551 | 109.4/s | 439.6/s | 0% | 39ms | 117ms | 334ms |

瓶颈证据是应用日志中的 `HikariPool-1 - Connection is not available`，以及连接池指标达到
`active=10、max=10、pending=10`。因此先将连接池上限从10调整为20，再把持续并发从10降到5。
结果说明增加并发不等于增加吞吐；超过当前资源组合的最佳并发后，连接争抢和上下文切换
反而会降低吞吐并抬高P99。

当前暂定的单实例稳定边界是：连接池20、5个持续并发用户时，约109笔完整交易/秒，
约440个HTTP请求/秒，错误率0%，完整交易P95约117ms、P99约334ms。该结果来自60秒本机
测试，只能作为阶段性边界；生产定容仍需10～30分钟以上的稳定性测试和更接近真实的数据量。

### 5.1 受控瓶颈实验

| 实验 | 实测证据 | 结论 |
|---|---|---|
| 线程池耗尽 | 40个任务：8运行、20排队、12拒绝 | 有界队列和拒绝策略防止无限堆积 |
| 数据库连接池耗尽 | 10 active、10 pending；200ms SQL总耗时约2.061s | 主要时间花在等待连接，不是执行SQL |
| 连接池恢复 | 同一200ms SQL总耗时约0.258s | 下游资源恢复后延迟恢复 |
| 内存增长 | 主动保留64MB后Heap约185～193MB；清除并GC后约39MB | 引用释放后内存可回收，没有形成泄漏证据 |
| CPU压力 | 进程CPU约56%，热点线程约82%～94% | `jstack`定位到`burnCpu`的RUNNABLE线程 |
| 第三方延迟 | SAFE_PAY延迟800ms，调用约0.997s | 下游延迟会直接传导到接口尾延迟 |

### 5.2 JVM与GC实测

`jcmd GC.heap_info`显示G1堆已提交256MB、最大512MB、Region为1MB；Metaspace使用约98MB。
`jmap -histo:live`中占用较多的是 `byte[]`、`String` 和集合节点，没有发现异常增长的业务对象。
NMT显示总提交内存约539MB，其中Heap 256MB、线程栈约10MB、Code约62MB、GC约60MB，
也说明容器内存不能只按照 `-Xmx` 计算。

GC日志中的正常Young GC将约188MB降至36MB，暂停约6～14ms。一次
`Pause Full (Heap Inspection Initiated GC)` 将171MB降至36MB，暂停257.9ms；这是执行
`jmap -histo:live`进行存活对象统计时由诊断工具主动触发，不是应用自然发生的异常Full GC。

### 5.3 JMeter进程内存说明

JMeter日志里显示的约4GB `JVM_ARGS`属于独立的JMeter压测容器，由该镜像入口脚本根据
容器环境计算；它不是被测应用的JVM。应用容器仍使用 `JAVA_OPTS` 中的
`-Xms256m -Xmx512m`，已通过 `jcmd GC.heap_info`验证。压测机与被测机的JVM必须分开判断。

## 6. 调优原则

1. 先测量再修改，一次只改变一个主要变量。
2. 线程池、连接池和下游容量要协同，不能只扩大Tomcat线程数。
3. 有界队列、超时、限流、熔断是容量保护，不应依靠无限堆积。
4. G1的暂停目标是软目标；堆过小会频繁GC，堆过大也会增加扫描成本。
5. 慢SQL优先优化查询、索引和数据访问量，而不是先扩大连接池。
6. 新系统边界必须同时包含吞吐、延迟、错误率和持续时间，例如：
   “持续10分钟、P99低于300ms、错误率低于1%时，稳定处理X RPS”。
