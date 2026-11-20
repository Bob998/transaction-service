# 第11周 Kubernetes 实操

## 目录

- `base/`：Namespace、ConfigMap、基础设施、应用、Service、Ingress和HPA；
- `canary/`：一个独立的灰度Deployment，默认不部署；
- `scripts/`：构建镜像、创建Secret和滚动发布脚本。

真实密码不写入镜像和Git。`secret.example.yaml`只说明键名，基础
`kustomization.yaml`不会加载它；使用脚本从本机环境变量创建Secret。

## 首次部署

在Docker Desktop中启用Kubernetes，确认：

```bash
kubectl config current-context
kubectl get nodes
```

构建本地镜像、创建Secret并部署：

```bash
./k8s/scripts/build-image.sh week11-v1
POSTGRES_PASSWORD=transaction_password \
  ./k8s/scripts/create-secret.sh
kubectl apply -k k8s/base
kubectl -n transaction-system rollout status deployment/postgres
kubectl -n transaction-system rollout status deployment/redis
kubectl -n transaction-system rollout status deployment/kafka
kubectl -n transaction-system rollout status deployment/transaction-service
```

Docker Desktop Kubernetes可以直接使用本机Docker镜像；其他集群需要把镜像推送到
镜像仓库，并修改Deployment中的完整镜像地址。

访问应用：

```bash
kubectl -n transaction-system port-forward \
  service/transaction-service 18080:8080
curl http://localhost:18080/api/operations/release
```

Ingress清单使用`transaction.local`和`nginx` IngressClass。只有集群已安装NGINX
Ingress Controller并把域名解析到入口地址时才会生效；本地学习优先使用port-forward。

## Readiness实验

选择一个Pod并只转发到该Pod：

```bash
POD=$(kubectl -n transaction-system get pod \
  -l app=transaction-service,track=stable \
  -o jsonpath='{.items[0].metadata.name}')
kubectl -n transaction-system port-forward pod/$POD 18081:8080
```

另开终端让它拒绝流量：

```bash
curl -X POST \
  'http://localhost:18081/api/operations/readiness?ready=false'
kubectl -n transaction-system get pods
kubectl -n transaction-system get endpoints transaction-service
```

Pod仍然`Running`且liveness为UP，但Ready变为`0/1`并从Service Endpoint中移除。
恢复：

```bash
curl -X POST \
  'http://localhost:18081/api/operations/readiness?ready=true'
```

## 滚动发布和回滚

构建v2并更新：

```bash
./k8s/scripts/build-image.sh week11-v2
./k8s/scripts/set-release.sh week11-v2
kubectl -n transaction-system rollout history deployment/transaction-service
```

模拟发布不存在的镜像，再回滚：

```bash
kubectl -n transaction-system set image \
  deployment/transaction-service \
  app=transaction-service:week11-broken
kubectl -n transaction-system rollout status \
  deployment/transaction-service --timeout=60s
kubectl -n transaction-system rollout undo deployment/transaction-service
kubectl -n transaction-system rollout status deployment/transaction-service
```

`maxUnavailable: 0`保证旧Pod在新PodReady之前继续服务，`maxSurge: 1`允许滚动期间
额外创建一个Pod。

## 灰度实验

先构建独立的canary版本，然后部署一个canary Pod：

```bash
./k8s/scripts/build-image.sh week11-v3-canary
kubectl apply -f k8s/canary/deployment.yaml
kubectl -n transaction-system rollout status \
  deployment/transaction-service-canary
```

Service按`app=transaction-service`选择稳定和灰度Pod。基础环境是2个stable和1个canary，
请求会近似分散，但由于连接复用等因素并非精确三分之一。观察完成后删除：

```bash
kubectl delete -f k8s/canary/deployment.yaml
```

普通滚动发布逐步用新版本替换全部旧Pod；灰度发布让少量新版本与稳定版本长期并存，
先观察指标和错误，再决定扩大、全量或回退。生产中的精确流量比例通常需要Ingress、
Gateway API或Service Mesh支持。

## HPA

```bash
kubectl -n transaction-system get hpa
kubectl -n transaction-system top pods
```

HPA根据`当前CPU用量 / requests.cpu`计算利用率。本清单目标70%，副本范围2～6。
本地集群需要Metrics Server；没有它时HPA会显示`<unknown>`，但不影响手动部署。
