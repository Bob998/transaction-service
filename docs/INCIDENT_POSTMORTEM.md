# 事故复盘：Kubernetes Kafka 启动失败

## 摘要

本地 Kubernetes 部署时 Kafka Pod 长时间 `0/1 Running` 并不断重启，导致依赖 Kafka 的事件链路不可用。修复 KRaft controller quorum 地址后恢复。

## 影响与发现

- 影响：Kafka 无法 Ready，交易同步接口仍可运行，但 Outbox 事件无法可靠发布和消费。
- 发现：`kubectl get pods -w` 显示 Kafka restart 持续增长；Pod 退出码/事件表明被健康检查终止。

## 根因

单节点 KRaft 配置把 controller quorum 设置为 `kafka:9093`，但 Kubernetes Service 只暴露 9092。Broker 在自身启动阶段无法通过该 Service 地址完成 controller 通信，liveness 最终发送 SIGTERM。单节点场景改为 `1@localhost:9093` 后正常启动。

## 修复

1. 将 `KAFKA_CONTROLLER_QUORUM_VOTERS` 改为 `1@localhost:9093`。
2. 重新部署并观察 rollout。
3. 验证 Kafka、PostgreSQL、Redis 均为 `1/1 Running`，再部署应用。

## 为什么没有更早发现

- Compose 与 Kubernetes 的网络寻址方式不同，Compose 成功未覆盖 K8s 配置。
- 缺少部署后的 Kafka smoke test。
- liveness 在依赖初始化尚未稳定时只表现为反复重启，根因需要结合日志和配置判断。

## 防止复发

- CI 校验 Kubernetes 渲染结果，并在临时集群增加 Kafka topic 创建/描述 smoke test。
- 为慢启动组件使用合理的 startupProbe，再启用 liveness。
- 把单节点实验配置与生产多节点 quorum 配置明确分离。
- 告警同时关注 restart、readiness 和 Outbox backlog，避免同步API正常掩盖异步链路故障。
