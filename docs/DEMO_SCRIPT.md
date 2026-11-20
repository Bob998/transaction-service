# 系统演示脚本

## 准备

Compose 方式：

```bash
docker compose up --build -d
BASE_URL=http://localhost:8080 ./scripts/demo-system.sh
```

Kubernetes 方式：

```bash
kubectl -n transaction-system port-forward service/transaction-service 18080:8080
./scripts/demo-system.sh
```

## 3分钟讲解顺序

1. **工程可运行**：展示 readiness、容器/Pod 状态和非root镜像。
2. **业务闭环**：运行脚本，依次创建、风控确认、进入处理、执行支付并查询最终状态。
3. **一致性**：说明幂等键、状态机、乐观锁，以及业务数据与Outbox同事务提交。
4. **异步可靠性**：展示 Outbox `PUBLISHED` 与 `processed_event` 唯一消费记录。
5. **故障恢复**：展示通道延迟→P99升高→熔断→备用通道接管，或 readiness 摘除实验。
6. **工程证据**：展示测试、GitHub Actions、性能报告、Grafana和Kubernetes发布/回滚。

脚本成功时最后输出 `演示完成，transactionId=...`。若失败，应保留输出并检查服务健康、状态转换和依赖，而不是跳过错误。
