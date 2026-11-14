# 测试策略与边界

## 目标

本项目采用分层自动化测试，在保证反馈速度的同时验证真实的 HTTP、Spring、SQL、PostgreSQL 与第三方通道交互。

测试分为：

1. 单元测试；
2. 集成测试；
3. 端到端测试。

## 1. 单元测试

单元测试只验证一个类或一小块业务逻辑，外部依赖使用内存实现或 Mockito 替代。

本项目示例：

- `TransactionServiceTest`；
- `TransactionServiceMockitoTest`。

Mockito 测试不会启动：

- Spring ApplicationContext；
- PostgreSQL；
- Docker；
- HTTP 服务。

它主要验证：

- 合法状态转换；
- 非法状态时抛出正确异常；
- Service 是否调用 Repository；
- 审计日志、尝试记录和 Outbox 是否被调用；
- 失败场景是否没有副作用。

优点：

- 运行快；
- 失败后定位准确；
- 容易覆盖边界和异常分支。

边界：

- 不验证真实 SQL；
- 不验证 Spring 配置；
- 不验证 JSON 和 HTTP；
- Mock 的行为可能与真实依赖不同。

## 2. 集成测试

集成测试验证多个真实组件组合后是否能够正常协作。

### API 集成测试

`TransactionApiIntegrationTest` 使用：

- `@SpringBootTest`；
- `MockMvc`；
- Testcontainers PostgreSQL；
- Flyway；
- 真实 MyBatis Mapper。

测试链路：

```text
MockMvc HTTP 请求
→ Controller
→ 参数校验和异常处理器
→ Service
→ Repository / MyBatis
→ 临时 PostgreSQL
```

覆盖场景：

- 创建并查询交易；
- 重复幂等键；
- 参数校验；
- 非法状态转换；
- 统一错误响应。

MockMvc 不会占用真实 8080 端口，但会经过 Spring MVC 的请求映射、JSON 转换、参数校验和异常处理。

### PostgreSQL 集成测试

相关测试包括：

- `OptimisticLockIntegrationTest`；
- `ConcurrentConfirmationIntegrationTest`；
- `TransactionRollbackIntegrationTest`。

它们使用 Testcontainers 创建临时 PostgreSQL，验证：

- Flyway 能从空数据库执行全部迁移；
- MyBatis SQL 能在真实 PostgreSQL 中运行；
- 乐观锁版本条件有效；
- 两个线程不会重复确认；
- 状态、审计、尝试记录和 Outbox 能一起回滚。

### 第三方 HTTP 集成测试

`PaymentGatewayClientTest` 使用 WireMock 模拟支付通道。

测试链路：

```text
PaymentGatewayClient
→ 真实 HTTP 请求
→ WireMock 假支付通道
```

覆盖场景：

- 成功响应和 JSON 解析；
- HTTP 500；
- 响应超时；
- 请求路径和请求体。

WireMock 不会证明真实支付机构一定符合接口约定，但能稳定验证本系统对约定响应的处理。

## 3. 端到端测试

端到端测试从系统最外部入口出发，尽量使用真实部署形态验证完整业务流程。

典型链路：

```text
真实 HTTP 客户端
→ 启动在随机端口的 Spring Boot 应用
→ PostgreSQL
→ 外部服务测试替身
```

与当前 MockMvc API 集成测试相比，端到端测试通常还会：

- 启动真实 Web Server；
- 经过真实 TCP 端口；
- 使用 `WebTestClient`、`RestClient` 或其他外部客户端；
- 在接近部署环境的配置下运行。

优点：

- 最接近用户真实使用方式；
- 能验证启动、端口、过滤器和网络配置。

边界：

- 运行最慢；
- 失败后定位范围较大；
- 不适合用来覆盖所有业务分支。

本项目目前以单元测试和集成测试为主，后续可以增加少量端到端冒烟测试，但不应使用端到端测试替代大量快速单元测试。

## 4. 测试金字塔

建议数量关系：

```text
          少量端到端测试
        中等数量集成测试
      大量快速单元测试
```

原因：

- 单元测试提供快速反馈；
- 集成测试发现组件连接问题；
- 端到端测试验证关键用户旅程。

三层测试关注的问题不同，不能互相完全替代。

## 5. 本地与 CI

本地统一执行：

```bash
./mvnw test
```

测试会自动：

1. 运行 JUnit 5 和 Mockito 单元测试；
2. 使用 Testcontainers 启动临时 PostgreSQL；
3. 由 Flyway 初始化数据库；
4. 运行 API、事务和并发集成测试；
5. 运行 WireMock 支付通道测试；
6. 生成 JaCoCo 报告。

GitHub Actions 使用同一个 Maven Wrapper 命令，避免本地和 CI 测试方式不一致。

Testcontainers 需要 Docker Engine，但不需要开发者或 CI 预先安装、初始化 PostgreSQL。

## 6. 覆盖率

JaCoCo 报告路径：

```text
target/site/jacoco/index.html
```

当前统计：

| 范围 | 指令覆盖率 | 分支覆盖率 | 行覆盖率 |
|---|---:|---:|---:|
| 全项目 | 84.8% | 75.0% | 84.0% |
| 核心 Service + Gateway | 95.0% | 72.2% | 96.2% |

覆盖率用于发现测试盲区，不应通过无意义断言单纯追求 100%。核心业务场景和失败分支比单一数字更重要。

## 7. 当前重点场景证据

| 场景 | 测试方式 |
|---|---|
| 重复请求 | MockMvc + PostgreSQL 唯一约束 |
| 参数校验 | MockMvc + Jakarta Validation |
| 非法状态转换 | JUnit 单元测试 + API 集成测试 |
| 并发更新 | 两线程 + Testcontainers PostgreSQL |
| 数据库事务回滚 | Spring Boot Test + PostgreSQL |
| 第三方 HTTP 500 | WireMock |
| 通道超时 | WireMock 固定延迟 + 客户端读取超时 |

## 结论

- Mockito 模拟 Java 对象，适合快速单元测试；
- WireMock 模拟第三方 HTTP 服务，适合客户端集成测试；
- Testcontainers 提供临时真实数据库，消除本机数据库依赖；
- MockMvc 验证 Spring MVC API 链路但不启动真实端口；
- 端到端测试验证部署后的完整外部链路，应少量保留给关键流程。
