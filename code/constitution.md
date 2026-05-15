# 开放平台系统宪法（Constitution）

> 本文件是系统的最高纲领，定义系统的愿景、边界、领域划分、架构约束与编码规范。
> 所有 AI 生成代码、人工编写代码、架构决策均须以本文件为准绳。

---

## 1. 系统愿景

构建一个**面向机器人生态的开放平台**，将机器人算法能力与数据监测能力以标准化 API 的形式对外开放，支持第三方开发者接入、应用上架、按量计费，形成可持续运营的机器人能力生态。

---

## 2. 核心原则

| 原则 | 说明 |
|------|------|
| **领域驱动** | 严格按 DDD 划分上下文边界，禁止跨域直接调用，通过事件或防腐层通信 |
| **开放优先** | 所有对外能力通过统一网关暴露，内部服务不直接对外 |
| **契约先行** | API 先定义契约（OpenAPI/AsyncAPI），再实现，契约即文档 |
| **可观测性** | 每个服务必须暴露健康检查、指标、链路追踪三类数据 |
| **最小权限** | 服务间调用遵循最小权限原则，认证鉴权在网关层统一处理 |
| **幂等设计** | 所有写操作接口必须支持幂等，通过幂等键（idempotency-key）保证 |

---

## 3. 系统整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        外部调用方                            │
│              （第三方开发者 / 合作伙伴 / 内部系统）           │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTPS / WebSocket
┌──────────────────────────▼──────────────────────────────────┐
│                      流量网关层                               │
│                  Traffic Gateway                             │
│         （限流 / 熔断 / 路由 / SSL 终止 / 日志）              │
└──────────────────────────┬──────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
┌───────▼──────┐  ┌────────▼───────┐  ┌──────▼────────┐
│   核心域      │  │   支撑域        │  │   通用域       │
│  Core Domain │  │ Supporting     │  │ Generic       │
│              │  │ Domain         │  │ Domain        │
└──────────────┘  └────────────────┘  └───────────────┘
```

---

## 4. 领域划分（DDD Bounded Context）

### 4.1 核心域（Core Domain）

> 核心竞争力所在，优先投入资源，自研为主。

#### 4.1.1 机器人算法能力开放上下文（Robot Algorithm Capability Context）

**职责**：将机器人底层算法能力（路径规划、视觉识别、运动控制等）封装为标准化 API，供外部调用。

| 要素 | 说明 |
|------|------|
| 聚合根 | `AlgorithmCapability`（算法能力）、`AlgorithmVersion`（版本） |
| 核心实体 | `AlgorithmTask`（算法任务）、`TaskResult`（任务结果） |
| 值对象 | `CapabilitySpec`（能力规格）、`InvokeParam`（调用参数） |
| 领域事件 | `AlgorithmTaskCreated`、`AlgorithmTaskCompleted`、`AlgorithmTaskFailed` |
| 对外契约 | REST API（同步调用）、消息队列（异步回调） |

#### 4.1.2 机器人数据监测能力开放上下文（Robot Data Monitoring Context）

**职责**：采集、聚合、开放机器人运行时数据（状态、轨迹、告警、健康度等）。

| 要素 | 说明 |
|------|------|
| 聚合根 | `MonitoringSubscription`（监测订阅）、`RobotDataStream`（数据流） |
| 核心实体 | `RobotSnapshot`（机器人快照）、`AlertRecord`（告警记录） |
| 值对象 | `MetricPoint`（指标点）、`DataFilter`（数据过滤器） |
| 领域事件 | `RobotStatusChanged`、`AlertTriggered`、`DataStreamOpened` |
| 对外契约 | WebSocket（实时推送）、REST API（历史查询）、Webhook（事件回调） |

---

### 4.2 支撑域（Supporting Domain）

> 支撑核心域运转，可适度引入成熟方案。

#### 4.2.1 开发者与生态管理上下文（Developer & Ecosystem Context）

**职责**：开发者注册、认证、应用创建、生态伙伴管理、开发者门户。

| 要素 | 说明 |
|------|------|
| 聚合根 | `Developer`（开发者）、`EcosystemPartner`（生态伙伴） |
| 核心实体 | `DeveloperApp`（开发者应用）、`ApiCredential`（API 凭证） |
| 值对象 | `DeveloperProfile`（开发者信息）、`PartnerTier`（伙伴等级） |
| 领域事件 | `DeveloperRegistered`、`AppCreated`、`CredentialIssued` |

#### 4.2.2 应用与 API 生命周期管理上下文（App & API Lifecycle Context）

**职责**：API 定义、版本管理、上架审核、下线、变更通知。

| 要素 | 说明 |
|------|------|
| 聚合根 | `ApiProduct`（API 产品）、`ApiVersion`（API 版本） |
| 核心实体 | `ApiSpec`（API 规格）、`ReviewRecord`（审核记录） |
| 值对象 | `VersionTag`（版本标签）、`DeprecationPolicy`（废弃策略） |
| 领域事件 | `ApiPublished`、`ApiDeprecated`、`ApiRetired` |

#### 4.2.3 计量与计费上下文（Metering & Billing Context）

**职责**：API 调用量计量、套餐管理、账单生成、欠费处理。

| 要素 | 说明 |
|------|------|
| 聚合根 | `BillingAccount`（计费账户）、`UsagePlan`（用量套餐） |
| 核心实体 | `UsageRecord`（用量记录）、`Invoice`（账单）、`Quota`（配额） |
| 值对象 | `PricingRule`（计价规则）、`BillingCycle`（计费周期） |
| 领域事件 | `QuotaExceeded`、`InvoiceGenerated`、`AccountSuspended` |

#### 4.2.4 API 网关上下文（API Gateway Context）

**职责**：请求路由、认证鉴权、限流熔断、协议转换、请求/响应变换。

| 要素 | 说明 |
|------|------|
| 核心能力 | 路由规则管理、插件链（认证→鉴权→限流→转发→日志） |
| 关键配置 | `RouteConfig`、`PluginConfig`、`UpstreamConfig` |
| 与其他域关系 | 消费认证域的 Token 验证结果；上报调用记录到计量域 |

---

### 4.3 通用域（Generic Domain）

> 通用能力，优先采购或使用开源成熟方案，不重复造轮子。

| 上下文 | 职责 | 推荐方案 |
|--------|------|----------|
| **支付（Payment）** | 对接第三方支付，处理充值、退款、对账 | 支付宝 / 微信支付 SDK 封装 |
| **通知（Notification）** | 短信、邮件、站内信、Webhook 推送 | 消息中间件 + 模板引擎 |
| **用户认证与权限（IAM）** | 用户登录、OAuth2、RBAC 权限模型 | Keycloak / 自研 JWT 服务 |
| **运维与监控（Ops & Monitoring）** | 日志采集、指标监控、链路追踪、告警 | ELK + Prometheus + Jaeger |
| **数据存储（Data Storage）** | 关系型、缓存、对象存储、时序数据库 | PostgreSQL + Redis + MinIO + InfluxDB |

---

## 5. 服务间通信规范

### 5.1 同步通信

- 协议：**REST over HTTPS**（默认）、**gRPC**（高性能内部调用）
- 请求格式：JSON（REST）、Protobuf（gRPC）
- 超时：默认 5s，最长不超过 30s
- 重试：幂等接口最多重试 3 次，指数退避

### 5.2 异步通信

- 协议：**消息队列**（Kafka / RabbitMQ）
- 事件格式：CloudEvents 规范
- 消费保证：至少一次（at-least-once），消费者须做幂等处理
- 死信队列：所有 Topic 必须配置 DLQ

### 5.3 实时推送

- 协议：**WebSocket**（机器人数据实时流）
- 心跳：30s 间隔，3 次无响应断开重连
- 数据格式：JSON，字段遵循 camelCase

### 5.4 跨域集成规范

```
核心域 ──事件──▶ 支撑域（计量域消费调用事件）
支撑域 ──防腐层──▶ 通用域（IAM、支付、通知）
禁止：核心域直接调用通用域（必须经过支撑域或防腐层）
```

---

## 6. API 设计规范

### 6.1 REST API

```
# URL 结构
/api/v{version}/{domain}/{resource}/{id}/{sub-resource}

# 示例
GET    /api/v1/algorithm/capabilities          # 查询算法能力列表
POST   /api/v1/algorithm/tasks                 # 创建算法任务
GET    /api/v1/algorithm/tasks/{taskId}        # 查询任务结果
GET    /api/v1/monitoring/robots/{robotId}/snapshots  # 查询机器人快照
```

### 6.2 统一响应格式

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "abc123",
  "timestamp": 1715000000000
}
```

### 6.3 错误码规范

| 范围 | 含义 |
|------|------|
| 0 | 成功 |
| 1xxx | 客户端错误（参数、认证、权限） |
| 2xxx | 业务错误（资源不存在、状态异常） |
| 5xxx | 服务端错误 |

### 6.4 版本策略

- URL 路径版本（`/v1/`、`/v2/`）
- 旧版本至少维护 12 个月后才可下线
- 废弃前 3 个月通过 `Deprecation` 响应头提前告知

---

## 7. 安全规范

| 层次 | 措施 |
|------|------|
| 传输层 | 全链路 TLS 1.2+，禁止 HTTP 明文 |
| 认证层 | OAuth2 + JWT，Token 有效期 2h，RefreshToken 7d |
| 鉴权层 | RBAC + API 级别权限控制，网关统一拦截 |
| 数据层 | 敏感字段加密存储，日志脱敏，禁止记录密钥/Token |
| 接口层 | 参数校验、SQL 注入防护、XSS 过滤、CSRF Token |
| 限流层 | 按 AppKey 限流，默认 1000 QPS，可按套餐调整 |

---

## 8. 技术栈约束

| 层次 | 技术选型 | 备注 |
|------|----------|------|
| 语言 | Java 21 / Go 1.22 | 核心服务 Java，高性能网关 Go |
| 框架 | Spring Boot 3.x / Gin | — |
| 数据库 | PostgreSQL 16 | 主存储 |
| 缓存 | Redis 7 | 会话、限流、热点数据 |
| 消息队列 | Kafka 3.x | 领域事件、异步解耦 |
| 网关 | Kong / 自研 Go 网关 | 待定，优先评估 Kong |
| 容器 | Docker + Kubernetes | 标准部署单元 |
| CI/CD | GitHub Actions / Jenkins | — |
| 监控 | Prometheus + Grafana + Jaeger | — |

---

## 9. 目录结构规范

```
code/
├── constitution.md              # 本文件，系统宪法
├── docs/                        # 架构文档、ADR 决策记录
│   └── adr/                     # Architecture Decision Records
├── gateway/                     # 流量网关
├── core/                        # 核心域服务
│   ├── algorithm-capability/    # 机器人算法能力开放
│   └── robot-monitoring/        # 机器人数据监测能力开放
├── supporting/                  # 支撑域服务
│   ├── developer-ecosystem/     # 开发者与生态管理
│   ├── api-lifecycle/           # 应用与API生命周期管理
│   ├── metering-billing/        # 计量与计费
│   └── api-gateway/             # API网关
├── generic/                     # 通用域服务
│   ├── iam/                     # 用户认证与权限
│   ├── payment/                 # 支付
│   ├── notification/            # 通知
│   ├── ops-monitoring/          # 运维与监控
│   └── data-storage/            # 数据存储
└── shared/                      # 共享内核（跨域共用的值对象、事件定义）
    ├── events/                  # CloudEvents 事件定义
    └── proto/                   # gRPC Protobuf 定义
```

---

## 10. AI 编码协作规范（SDD 约定）

> 本节约定 AI 辅助编码时的行为边界，确保生成代码与系统宪法一致。

### 10.1 生成代码前必须确认

- [ ] 所属领域上下文（核心域 / 支撑域 / 通用域）
- [ ] 聚合根与实体边界是否清晰
- [ ] 是否需要发布领域事件
- [ ] 对外接口是否已有 OpenAPI 契约定义

### 10.2 禁止行为

- 禁止跨域直接依赖（import 其他域的内部包）
- 禁止在领域层引入框架注解（保持领域模型纯净）
- 禁止硬编码配置（密钥、URL、超时值）
- 禁止生成无测试覆盖的核心业务逻辑
- 禁止在未经审查的情况下修改 `shared/events` 和 `shared/proto`

### 10.3 每次生成任务的输入模板

```
## 任务描述
[描述要实现的功能]

## 所属上下文
[核心域/支撑域/通用域] > [具体上下文名称]

## 涉及聚合根/实体
[列出相关领域对象]

## 接口契约（如有）
[粘贴 OpenAPI 片段或接口描述]

## 约束与注意事项
[特殊要求、性能要求、安全要求等]
```

---

## 11. 变更管理

- 本文件的任何修改须经过架构评审，记录在 `docs/adr/` 下
- 领域边界调整、技术栈变更属于重大变更，需团队评审通过
- 版本号格式：`YYYY-MM-DD-vN`，每次修改递增

---

*文件版本：2026-05-11-v1 | 状态：草稿，待架构评审*
