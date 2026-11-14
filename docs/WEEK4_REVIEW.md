
## 本周目标

建立可在本地和 CI 重复运行的分层测试体系，让后续功能在提交时自动获得单元测试、数据库集成测试、API 测试、第三方通道测试和覆盖率证据。

## 已完成内容

- 使用 JUnit 5 组织和执行测试；
- 使用 Mockito 隔离 Repository，验证 Service 编排和失败无副作用；
- 使用 Spring Boot Test 验证完整应用上下文；
- 使用 Testcontainers 自动启动临时 PostgreSQL 17；
- 使用 Flyway 自动初始化临时测试数据库；
- 将原有事务、并发和乐观锁测试迁移到 Testcontainers；
- 使用 MockMvc 完成 API 集成测试；
- 使用 WireMock 模拟外部支付通道；
- 覆盖支付通道成功、HTTP 500 和响应超时；
- 使用随机真实端口完成端到端冒烟测试；
- 使用 JaCoCo 生成覆盖率报告；
- 创建 GitHub Actions 自动测试工作流；
- 编写测试分层与边界说明；
- 完整测试达到 27 个，全部通过。

## 测试分层

### 单元测试

`TransactionServiceTest` 和 `TransactionServiceMockitoTest` 不启动 Spring 或数据库，重点验证状态机、业务编排、异常分支和失败无副作用。

Mockito 的核心能力包括：

- `@Mock` 创建假的 Java 依赖；
- `when(...).thenReturn(...)` 定义依赖行为；
- `verify(...)` 验证调用与参数；
- `never()` 和 `verifyNoInteractions(...)` 验证失败后没有副作用。

### API 集成测试

`TransactionApiIntegrationTest` 使用 MockMvc、Spring Boot、MyBatis 和临时 PostgreSQL，覆盖：

- 创建并查询交易；
- 重复幂等键；
- 参数校验；
- 非法状态转换；
- 统一错误响应。

MockMvc 会经过 Spring MVC，但不启动真实 TCP 端口，因此比端到端测试更快。

### PostgreSQL 集成测试

Testcontainers 在测试开始时启动干净的 PostgreSQL，测试结束后自动删除容器。它验证了：

- Flyway 能从空数据库执行 V1 到 V8；
- MyBatis SQL 与 PostgreSQL 真实兼容；
- 乐观锁和并发更新有效；
- 事务异常后状态、审计、尝试记录和 Outbox 一起回滚。

### 第三方 HTTP 集成测试

`PaymentGatewayClientTest` 使用 WireMock 启动随机端口的假支付通道，验证真实 HTTP 请求与响应处理。

覆盖场景：

- 成功响应与 JSON 解析；
- HTTP 500 转换为 `PaymentGatewayException`；
- 固定延迟触发 `PaymentGatewayTimeoutException`；
- 请求路径和 JSON 请求体正确。

### 端到端测试

`TransactionEndToEndTest` 使用随机端口启动真实 Tomcat，再通过 RestClient 发送真实 TCP 请求完成创建和查询交易。

端到端测试最接近真实用户路径，但运行更慢，所以只保留少量关键冒烟流程。

## Testcontainers 设计

所有数据库测试继承 `PostgresContainerTest`。整次 Maven 测试共享一个 PostgreSQL 容器，避免容器关闭后 Spring 仍复用旧数据源地址。

测试不依赖：

- 本机预装 PostgreSQL；
- 固定数据库端口；
- 手工创建测试库；
- 手工执行迁移。

测试仍需要可用的 Docker Engine，因为临时 PostgreSQL 运行在 Docker 容器中。

## 本周遇到的问题

### 1. 集成测试仍连接 localhost:5432

原因是测试只创建了容器基类，但没有继承它。将所有 Spring 数据库测试继承 `PostgresContainerTest` 后，`@ServiceConnection` 自动提供动态数据源。

### 2. Docker Engine 29 与默认 API 版本不兼容

Testcontainers 能找到 socket，但 Docker API 返回 HTTP 400。通过 Maven Surefire 设置 `api.version=1.44` 后恢复兼容。

### 3. 每个测试类启停容器导致旧连接失效

第一个容器关闭后，Spring 缓存的上下文仍指向旧随机端口。将容器改为整次测试共享的静态单例后解决。

### 4. RestClient 与 WireMock 出现 RST_STREAM

默认客户端尝试 HTTP/2，WireMock 连接被重置。支付客户端改用 `SimpleClientHttpRequestFactory`，明确走 HTTP/1.1。

### 5. 超时异常被多层包装

底层 `SocketTimeoutException` 可能被包装成不同的 Spring 异常。客户端沿 cause 链识别真实超时原因，再转换为领域异常。

### 6. 超时测试在完整套件中偶发误判

统一使用 200ms 导致高负载时正常请求也超时。正常测试改为 2 秒，只有故障实验使用专门的 200ms 客户端。

## 覆盖率

JaCoCo 报告：

```text
target/site/jacoco/index.html
```

最终结果：

| 范围 | 指令覆盖率 | 分支覆盖率 | 行覆盖率 |
|---|---:|---:|---:|
| 全项目 | 84.8% | 75.0% | 84.0% |
| 核心 Service + Gateway | 95.0% | 72.2% | 96.2% |

核心领域逻辑行覆盖率超过 80% 目标。覆盖率作为测试盲区指标使用，不通过无意义测试追求 100%。

## CI 自动测试

GitHub Actions 工作流位于：

```text
.github/workflows/test.yml
```

每次 push 或 Pull Request 会自动：

1. 检出代码；
2. 安装 Java 17；
3. 缓存 Maven 依赖；
4. 执行 `./mvnw --batch-mode test`；
5. 启动 Testcontainers PostgreSQL；
6. 运行完整测试；
7. 上传 JaCoCo HTML 报告。

当前本地目录尚未配置 Git remote，因此工作流已创建，但需要推送到 GitHub 后才能获得线上执行记录。

## 交付标准核对

- 本地可自动运行测试：完成；
- CI 工作流：完成配置；
- 不依赖本机 PostgreSQL：完成；
- JUnit 5：完成；
- Mockito：完成；
- Spring Boot Test：完成；
- Testcontainers：完成；
- API 集成测试：完成；
- PostgreSQL 集成测试：完成；
- WireMock 支付通道：完成；
- 核心逻辑覆盖率约 80% 以上：完成；
- 能说明单元、集成和端到端边界：完成。

## 可用于简历的项目描述

为 Spring Boot 交易服务建立分层自动化测试体系，使用 JUnit 5 与 Mockito 验证状态机及失败无副作用，使用 Testcontainers PostgreSQL 和 Flyway 构建可重复的数据库集成环境，并通过 MockMvc 覆盖幂等、参数校验和非法状态转换。使用 WireMock 模拟支付通道成功、HTTP 500 与超时场景，增加真实随机端口端到端冒烟测试，并集成 JaCoCo 与 GitHub Actions；核心 Service 与 Gateway 行覆盖率达到 96.2%。
