# 第8周：Docker 与 CI 流水线

## 运行路径

```text
源码
  → Docker builder 阶段编译可执行 JAR
  → runtime 阶段只保留 JRE、curl 和 JAR
  → UID/GID 10001 非 root 用户运行
  → Compose 等待 PostgreSQL、Redis、Kafka 健康
  → Spring Boot 启动并执行 Flyway
  → Actuator readiness 通过
```

## 环境变量

`application.properties` 保存适合本机开发的默认值；Compose 通过 Spring Boot relaxed binding 覆盖数据库、Redis、Kafka、端口和支付通道地址。密码和环境差异不写死在镜像中。

## Kafka 双监听器

- `EXTERNAL://localhost:9092`：宿主机上的命令和应用连接；
- `INTERNAL://kafka:29092`：Compose 网络中的应用容器连接。

Kafka 返回给客户端的 advertised listener 必须是客户端能够解析的地址，因此容器内外不能共用 `localhost:9092`。

## 健康检查

- PostgreSQL：`pg_isready`；
- Redis：`redis-cli ping`；
- Kafka：列出 Topic；
- 应用：`/actuator/health/readiness`。

应用通过 `depends_on.condition: service_healthy` 等待三个依赖，但这只是启动编排；运行期间仍要靠连接池、重试、降级和监控处理依赖故障。

## CI 主线

GitHub Actions 每次 push 和 pull request 执行：

1. Checkout；
2. 安装 Java 17 并缓存 Maven 依赖；
3. `mvn verify` 编译、测试、生成 JaCoCo 报告并运行 Checkstyle；
4. 上传测试和覆盖率报告；
5. 校验 Compose；
6. 用 Git SHA 构建版本化镜像；
7. 检查镜像用户为 `10001:10001`。

当前流水线只构建镜像而不推送仓库，避免在学习项目中保存 registry 密钥。后续可增加 GHCR 登录和推送，并为主分支额外维护语义版本标签。
