# JMeter容量测试

测试计划会反复执行完整链路：

```text
创建交易 → 风控确认 → PROCESSING → 调用支付通道
```

基线测试（10线程、60秒）：

```bash
JMETER_THREADS=10 \
JMETER_RAMP_SECONDS=10 \
JMETER_DURATION_SECONDS=60 \
docker compose --profile load-test run --rm jmeter
```

过载测试（100线程、120秒）：

```bash
JMETER_THREADS=100 \
JMETER_RAMP_SECONDS=20 \
JMETER_DURATION_SECONDS=120 \
docker compose --profile load-test run --rm jmeter
```

结果写入 `load-test/results/`，该目录已被Git忽略。每次运行前，
JMeter的 `-f` 参数会清理上一轮同名结果。

压测时同时观察：

- Grafana的RPS、HTTP P99、Tomcat线程、Hikari连接池；
- `docker stats transaction-app`；
- `jcmd`、`jstack`和GC日志；
- PostgreSQL慢查询与活动连接。
