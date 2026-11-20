# 第11周：Kubernetes、灰度和回滚

## 目标

把已经具备镜像、健康检查和指标的交易服务部署到Kubernetes，并建立发布闭环：

```text
配置与Secret注入 → 探针控制流量 → 滚动发布 → 失败回滚 → 灰度验证 → HPA扩缩
```

## 资源关系

```text
Ingress（可选）
  → Service transaction-service
    → stable Deployment Pods
    → canary Deployment Pod（灰度时才存在）

应用Pod
  → PostgreSQL Service → PostgreSQL Pod + PVC
  → Redis Service      → Redis Pod
  → Kafka Service      → Kafka Pod
```

## 探针边界

- startupProbe：允许慢启动，成功前不执行另外两个探针；
- readinessProbe：失败时Pod仍运行，但从Service Endpoint移除；
- livenessProbe：连续失败表示进程无法自愈，kubelet重启容器。

readiness不能用来掩盖永久故障，liveness也不应依赖偶发变慢的外部服务，否则可能造成
无意义的重启风暴。

## 配置与Secret

ConfigMap保存数据库地址、线程池等非敏感配置。Secret保存数据库密码，通过环境变量注入。
镜像只包含应用，不包含任何环境密码。示例Secret不会被Kustomize加载，真实Secret由本机或
CI/CD的安全变量创建。生产环境应进一步使用External Secrets或云密钥服务。

## 发布策略

稳定Deployment使用：

```text
replicas=2
maxUnavailable=0
maxSurge=1
minReadySeconds=10
```

新Pod必须通过readiness并稳定10秒后，旧Pod才会缩容。如果新镜像无法启动，旧Pod继续
提供服务；通过Deployment历史可以回滚到上一个ReplicaSet。

## 滚动发布与灰度发布

滚动发布的目标是最终全部替换：新版本逐步增加，旧版本逐步减少，最后只剩新版本。

灰度发布的目标是控制风险：稳定版和新版本并存，只让少量用户或流量进入新版本，观察
错误率、P95/P99和业务成功率后再决定扩大或回退。基础实验通过Pod数量近似分流，不保证
精确比例；生产环境需要支持权重或用户规则的流量入口。

## HPA原理

CPU HPA根据目标Deployment中Pod的平均CPU利用率调整副本：

```text
期望副本数 ≈ 当前副本数 × 当前利用率 / 目标利用率
```

CPU利用率以`requests.cpu`为分母，因此没有requests就无法正确使用该模式。扩容也不是瞬时
的，Pod启动和readiness通过需要时间，所以HPA不能替代限流、超时和队列保护。

## 交付检查

- 应用及依赖可以通过Kustomize部署；
- readiness可受控失败并从Service Endpoint移除；
- Deployment支持零不可用滚动更新；
- 不存在的镜像可演示失败发布和rollback；
- ConfigMap与Secret和镜像解耦；
- HPA配置CPU目标和扩缩容稳定窗口；
- canary Deployment可以和stable版本并存。
