# 第9周：可观测性与生产排障

## 目标

本周建立一条能回答问题的指标链路：

1. HTTP P99 是否异常；
2. 哪个支付通道变慢；
3. Tomcat 线程和 Hikari 连接是否被占满；
4. 熔断器是否打开；
5. 新交易是否切换到了其他健康通道；
6. 延迟和成功率是否恢复。

## 组件与地址

- Spring Boot Actuator：JVM、Tomcat、Hikari 和 HTTP 指标；
- Micrometer：交易、风控、通道、路由、Redis、对账指标；
- Prometheus：每 5 秒采集一次；
- Kafka Exporter：按 Consumer Group 暴露 Lag；
- Grafana：预置生产排障面板。

访问地址：

- Grafana: <http://localhost:3000>，默认 `admin/admin`；
- Prometheus: <http://localhost:9091>；
- 应用指标: <http://localhost:8080/actuator/prometheus>。

## 关键指标

| 问题 | Prometheus 指标 |
| --- | --- |
| 请求量、错误率、P95/P99 | `http_server_requests_seconds_*` |
| JVM | `jvm_*` |
| Tomcat 线程池 | `tomcat_threads_*` |
| Hikari 连接池 | `hikaricp_connections_*` |
| Kafka Lag | `kafka_consumergroup_lag` |
| Redis 降级 | `technical_redis_errors_total` |
| 交易成功率 | `business_transaction_completed_total` |
| 风控拒绝率 | `business_risk_decision_total` |
| 通道成功率和延迟 | `business_channel_request_duration_seconds_*` |
| 熔断状态 | `technical_channel_circuit_open` |
| 路由切换 | `business_routing_failover_total` |
| 对账差异 | `business_reconciliation_differences_total` |

## 故障实验

启动后，让默认首选通道 `SAFE_PAY` 延迟 1500ms，而客户端读取超时为 1000ms：

```bash
curl -X POST \
  "http://localhost:8080/api/operations/channels/SAFE_PAY/fault?delayMs=1500"
```

创建三笔新交易，每笔依次调用：

```text
POST /api/transactions
POST /api/transactions/{id}/confirm
POST /api/transactions/{id}/processing
POST /api/transactions/{id}/execute
```

第三次超时后，`SAFE_PAY` 熔断并被标记为 `UNHEALTHY`。查询状态：

```bash
curl http://localhost:8080/api/operations/channels
```

此时 Grafana 应显示 `SAFE_PAY` P99 上升、成功率下降、熔断状态变为 1、路由切换次数增加；新交易会选择其他健康通道。

恢复实验：

```bash
curl -X DELETE \
  http://localhost:8080/api/operations/channels/SAFE_PAY/fault
curl -X POST \
  http://localhost:8080/api/operations/channels/SAFE_PAY/circuit/reset
```

再次创建并执行交易，应恢复成功。

## 生产边界

内置模拟通道和故障接口只用于学习。真实生产中通道是独立外部系统，运维接口必须鉴权，熔断应使用成熟组件并考虑多实例状态，告警应基于 SLO 持续时间而非单个样本。
