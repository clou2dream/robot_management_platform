# 机器人运营管理平台 — MVP 开发计划

> 版本：v1.0 | 日期：2026-05-13  
> 范围：单人 + AI 辅助，12个工作日，交付可用版本

---

## 一、范围说明

### 纳入 MVP

| 功能 | 说明 |
|------|------|
| 服务器 + 中间件部署 | ECS、PostgreSQL+TimescaleDB、Redis、EMQX、Nginx |
| 登录认证 | 用户名密码登录、JWT、Token 自动刷新、注销 |
| 机器人自动注册激活 | EMQX 认证回调、配额校验、凭证下发 |
| 实时巡检 | MQTT 订阅、状态卡片、实时地图（位置+轨迹）、WebSocket 推送 |
| 任务下发 | 地图选点、Order 下发、即时指令（暂停/继续/取消任务）、任务列表；安全急停仅作为状态告警展示 |
| 告警 | 告警引擎、前端红点 + 弹窗通知（无告警中心页） |

### 后续迭代

- 飞书 Webhook 通知
- 人员管理页（前端）
- APP ID 管理页（前端）
- 告警中心页（前端）
- 系统设置页
- 机器人详情历史轨迹/历史状态 Tab
- operator 细粒度机器人授权（MVP 只有 admin 角色）
- 审计日志查询接口
- CI/CD 流水线

---

## 二、技术栈确认

| 层次 | 技术 |
|------|------|
| 后端 | Java 17 + Spring Boot 3 + MyBatis-Plus + Spring Security |
| 前端 | React 18 + TypeScript + Vite + Ant Design 5 + Zustand |
| 地图 | Konva.js（react-konva） |
| 实时通信 | STOMP over WebSocket |
| MQTT | Spring Integration MQTT + EMQX |
| 数据库 | PostgreSQL 16 + TimescaleDB |
| 缓存 | Redis 7 |
| 部署 | Docker Compose + Nginx + 阿里云 ECS |

---

## 三、详细日程

### Day 1 — 服务器 + 中间件部署

**目标：** 所有基础设施跑起来，能从本地访问。

**任务清单**

- [ ] 购买阿里云 ECS（4核8G，Ubuntu 22.04），配置安全组（开放 80/443/1883/8083）
- [ ] 购买域名，配置 DNS 解析到 ECS 公网 IP
- [ ] 申请阿里云免费 SSL 证书，下载证书文件
- [ ] 服务器安装 Docker + Docker Compose
- [ ] 编写并启动 `docker-compose.yml`，包含：
  - PostgreSQL 16 + TimescaleDB（端口 5432，挂载数据卷）
  - Redis 7（端口 6379，配置密码，开启 AOF 持久化）
  - EMQX 5（端口 1883/8083/18083，挂载配置目录）
  - Nginx（端口 80/443，挂载证书和配置）
- [ ] 执行建表 SQL（全部表结构、索引、TimescaleDB hypertable + retention policy）
- [ ] 验证：psql 能连接，redis-cli ping 通，EMQX Dashboard 能打开


**完成标志：** `docker-compose ps` 全部 healthy，EMQX Dashboard 登录成功

---

### Day 2 — 后端骨架 + 登录认证

**目标：** 后端项目跑起来，登录接口可用。

**任务清单**

- [ ] 创建 Spring Boot 项目，配置依赖（MyBatis-Plus、Spring Security、JJWT、Redis、Swagger）
- [ ] 配置 `application.yml`（数据库、Redis、EMQX 连接信息）
- [ ] 实现 `JwtAuthenticationFilter`（提取 JWT、验签、查黑名单、写 SecurityContext）
- [ ] 实现登录接口 `POST /api/auth/login`（BCrypt 验密、失败计数锁定、签发 Access Token，Refresh Token 写 Redis 并通过 httpOnly Cookie 下发）
- [ ] 实现刷新接口 `POST /api/auth/refresh`（从 Cookie 读取 Refresh Token，查 Redis，签发新 Access Token，滑动续期并重新 Set-Cookie）
- [ ] 实现注销接口 `POST /api/auth/logout`（jti 写黑名单、删 Refresh Token，并清除 Cookie）
- [ ] 实现 `GET /api/auth/me`（返回当前登录人信息）
- [ ] 初始化一条 admin 账号数据（SQL 脚本）
- [ ] 验证：Postman 登录拿到 token，携带 token 访问 /me 返回用户信息


**完成标志：** Postman 能完整走通登录 → 访问接口 → 刷新 → 注销流程

---

### Day 3 — 前端骨架 + 登录页

**目标：** 前端项目跑起来，能登录跳转到首页。

**任务清单**

- [ ] 创建 Vite + React + TypeScript 项目，安装依赖（Ant Design、Zustand、Axios、React Router v6、STOMP.js、react-konva）
- [ ] 配置 Vite 代理（开发环境 `/api` 转发到后端）
- [ ] 封装 Axios 实例（baseURL、请求拦截器注入 Access Token、开启 `withCredentials`、响应拦截器处理 401 自动刷新）
- [ ] 实现并发 401 队列（多个请求同时 401 时只触发一次刷新，其余排队等待）
- [ ] 实现 AuthStore（Zustand，存 accessToken + currentUser）
- [ ] 实现路由结构（React Router v6，含 PrivateRoute 权限守卫）
- [ ] 实现登录页（用户名密码表单、错误提示、账号锁定提示）
- [ ] 实现顶部导航栏（Logo、当前用户名、退出登录按钮）
- [ ] 实现左侧菜单（巡检面板、任务下发、机器人列表，根据角色控制可见性）
- [ ] 验证：浏览器打开登录页，登录后跳转首页，刷新后保持登录状态，退出后跳回登录页


**完成标志：** 登录 → 首页 → 刷新保持登录 → 退出 全流程跑通

---

### Day 4 — 机器人授权（后端）

**目标：** 机器人能通过 EMQX 认证，首次上线自动注册并收到凭证。

**任务清单**

- [ ] 实现 EMQX HTTP 认证回调 `POST /internal/mqtt/auth`（仅内网访问）
  - 阶段一：APP ID + SN 认证（查 tenant_app_ids、配额校验、创建 robots 记录、返回受限 ACL）
  - 阶段二：API Key + API Secret 认证（查 api_credentials、验 hash、返回完整 ACL；若 robots.status=pending 则更新为 active，写审计日志 ROBOT_ACTIVATED）
- [ ] 配置 EMQX HTTP 认证插件，指向后端内网地址
- [ ] 实现 connection Topic 处理器（首次上线逻辑）：
  - connectionState=ONLINE 且 robots.status=pending 且无 api_credentials 记录 → 生成 app_id + API Key/Secret → 发布到 activation Topic（QoS 1 + Retain）
  - connectionState=ONLINE 且 robots.status=active → 检查 Redis robot:reissue:{id} → 若存在则吊销旧凭证、重新生成并下发
  - connectionState=OFFLINE/CONNECTION_BROKEN → 更新 Redis 在线状态 → 写 robot_connections
- [ ] 实现机器人列表接口 `GET /api/robots`（含在线状态从 Redis 读取）
- [ ] 实现机器人状态接口 `GET /api/robots/{id}/state`（读 Redis 快照）
- [ ] 用 MQTTX 工具模拟机器人，验证完整激活流程


**完成标志：** MQTTX 用 APP ID+SN 连接 → 收到 activation 消息 → 用 API Key+Secret 重连 → robots 表 status=active

---

### Day 5 — 实时巡检（后端）

**目标：** 机器人上报的数据能存库、推 WebSocket。

**任务清单**

- [ ] 配置 Spring Integration MQTT（订阅 `uagv/v3/#`，按 Topic 分发处理器）
- [ ] 实现 state 处理器：
  - 解析 VDA5050 State JSON
  - 异步批量写入 robot_states（TimescaleDB）
  - 更新 Redis `robot:{id}:state` 快照（覆盖写）
  - 刷新 Redis `robot:{id}:online` TTL（10s）
  - 推送 WebSocket `/topic/robots/{id}/state`
  - 触发告警引擎（电量、安全急停状态、errors 数组）
- [ ] 实现 visualization 处理器：
  - 解析位置数据
  - 异步写入 robot_positions
  - 更新 Redis `robot:{id}:position`
  - 推送 WebSocket `/topic/robots/{id}/position`
- [ ] 配置 Spring WebSocket + STOMP（`/ws` 端点，按 tenant_id 隔离推送）
- [ ] 实现历史状态查询接口 `GET /api/robots/{id}/states`（分页，读 TimescaleDB）
- [ ] 用 MQTTX 持续发布模拟 state 消息，验证写库和 WebSocket 推送


**完成标志：** MQTTX 发布 state 消息 → robot_states 表有数据 → WebSocket 客户端收到推送

---

### Day 6 — 实时巡检（前端）

**目标：** 巡检面板能实时展示机器人状态和位置。

**任务清单**

- [ ] 实现 WebSocket 连接管理 Hook（`useWebSocket`）：
  - 登录后自动连接 STOMP
  - 指数退避断线重连（1s→2s→4s→最大30s）
  - 切换机器人时自动取消旧订阅、建立新订阅
  - 重连后补拉最新状态（调用 REST 接口）
- [ ] 实现 RobotOnlineStore（Zustand，维护 robotId → 是否在线的 Map）
- [ ] 实现巡检面板页面布局（左侧机器人列表 + 右侧详情区）
- [ ] 实现机器人列表组件（在线状态实时更新、点击切换详情、按状态筛选）
- [ ] 实现状态卡片组件（电量、速度、运行模式、当前任务、安全急停横幅）
- [ ] 实现即时指令操作区（暂停/继续/取消任务按钮，暂停/取消有确认弹窗，viewer 隐藏；安全急停仅展示状态和告警，不在 MVP 中远程触发）
- [ ] 实现实时地图（react-konva）：
  - 加载地图背景图
  - 机器人图标 + 方向箭头
  - 实时位置更新（直接操作 Konva Layer，不走 React 状态）
  - 历史轨迹线（保留最近5分钟）
  - 鼠标滚轮缩放 + 拖拽平移
  - 点击机器人图标显示悬浮信息卡


**完成标志：** 模拟机器人持续上报，浏览器能看到机器人在地图上移动，状态卡片实时刷新

---

### Day 7 — 告警引擎 + 机器人管理页（后端+前端）

**目标：** 异常能触发告警写库并推送前端，前端能看到机器人列表。

**任务清单**

后端：
- [ ] 实现告警引擎（由 state 处理器和 connection 处理器调用）：
  - 触发条件检测（电量<10%、安全急停状态、FATAL错误、CONNECTION_BROKEN）
  - 告警去重（同机器人同 error_type 未解决不重复创建）
  - 自动恢复（条件消失时更新 resolved_at）
  - 写入 alerts 表
- [ ] 实现告警接口 `GET /api/alerts`、`PUT /api/alerts/{id}/resolve`
- [ ] 实现机器人 CRUD 接口（吊销、重新激活）

前端：
- [ ] 实现机器人列表页（SN、厂商、状态、在线状态、激活时间、吊销/重新激活操作）
- [ ] 导航栏告警红点（订阅 `/topic/alerts`，CRITICAL/FATAL 弹出全局通知）


**完成标志：** MQTTX 发布低电量 state → alerts 表有记录 → 前端导航栏出现告警红点；机器人列表页正常展示

---

### Day 8 — 任务下发（后端+前端）

**目标：** 能向机器人下发导航任务和即时指令。

**任务清单**

后端：
- [ ] 实现 Order 下发接口 `POST /api/robots/{robotId}/orders`：
  - 权限校验（viewer 拒绝）
  - 在线状态检查（离线返回 409）
  - 构造 VDA5050 Order 报文（自动填充 headerId、sequenceId、orderId 等）
  - 发布到 EMQX（QoS 1）
  - 写入 orders 表（status=pending）
- [ ] 实现 instantActions 下发接口 `POST /api/robots/{robotId}/instant-actions`
- [ ] 实现任务状态跟踪（state 处理器中解析 orderId/nodeStates/actionStates 更新 orders 表）
- [ ] 实现任务取消接口 `PUT /api/robots/{robotId}/orders/{orderId}/cancel`
- [ ] 实现任务列表/详情接口 `GET /api/robots/{robotId}/orders`
- [ ] Redis headerId 计数器（`robot:header:{robot_id}` INCR）

前端：
- [ ] 实现任务下发页布局（左侧配置区 + 右侧任务列表）
- [ ] 实现地图选点（Konva，点击地图添加节点，节点可配置坐标/朝向/动作）
- [ ] 实现节点列表（可删除，显示坐标和动作数量）
- [ ] 实现边配置（相邻节点间自动生成边，可配置 maxSpeed）
- [ ] 实现下发按钮（前端校验至少2个节点，调用接口，成功后刷新任务列表）


**完成标志：** 在页面配置2个节点后下发，MQTTX 订阅 order Topic 能收到完整 VDA5050 报文

---

### Day 9 — 任务列表 + 全链路联调

**目标：** 任务状态实时更新，前后端全链路跑通。

**任务清单**

前端：
- [ ] 实现任务列表组件（状态筛选、实时状态更新、展开详情、取消按钮）
- [ ] 任务状态颜色标识（pending蓝、active绿闪烁、completed灰、failed红、cancelled橙）
- [ ] WebSocket 订阅 `/topic/robots/{id}/order-status`，实时更新任务状态

联调：
- [ ] 联调登录 → 机器人激活 → 巡检面板实时展示 完整流程
- [ ] 联调任务下发 → 机器人收到 Order → 任务状态 pending→active→completed 完整流程
- [ ] 联调即时指令（暂停/继续/取消任务）
- [ ] 联调告警触发 → 前端红点
- [ ] 修复联调中发现的 bug

**完成标志：** 完整走通"机器人上线 → 巡检监控 → 任务下发 → 任务完成 → 告警触发"全流程

---

### Day 10 — 生产部署 + 上线检查

**目标：** 部署到生产环境，可以交付使用。

**任务清单**

- [ ] 前端 `vite build` 打包，产物放到 Nginx 静态目录
- [ ] 后端 `mvn package` 打 jar 包，配置生产环境 `application-prod.yml`
- [ ] 更新 `docker-compose.yml` 加入后端服务容器
- [ ] Nginx 配置：静态资源服务、`/api` 反向代理到后端、`/ws` WebSocket 代理
- [ ] 配置 EMQX HTTP 认证插件指向后端内网地址（`http://backend:8080/internal/mqtt/auth`）
- [ ] 生产环境端到端测试（用真实 MQTT 客户端走完激活流程）
- [ ] 配置阿里云 SLS 日志采集（Logtail Agent，采集后端日志）
- [ ] 配置数据库定期备份（crontab + pg_dump，备份到本地或 OSS）
- [ ] 检查安全组规则（5432/6379 不对外暴露，1883 仅机器人 IP 段可访问）
- [ ] 记录运维手册（服务重启命令、日志查看方式、常见问题）

**完成标志：** 通过公网域名能正常访问平台，机器人能连接生产 EMQX 完成激活

---

### Day 11 — 缓冲 / Bug 修复

**目标：** 消化联调和上线中发现的问题。

- [ ] 修复 Day 9-10 遗留 bug
- [ ] 补充遗漏的错误处理（离线提示、网络异常提示）
- [ ] 前端样式收尾（响应式、空状态、加载状态）
- [ ] 补充 Swagger 接口文档注释

---

### Day 12 — 缓冲 / 真实机器人对接

**目标：** 用真实机器人验证，处理厂商协议差异。

- [ ] 真实机器人连接 EMQX，验证 VDA5050 报文格式是否与设计一致
- [ ] 处理厂商私有字段（factsheet 解析、非标准 errorType 等）
- [ ] 验证高频 visualization 消息下前端地图渲染性能
- [ ] 压测 WebSocket 并发（模拟多台机器人同时上报）

---

## 四、关键风险

| 风险 | 发生概率 | 影响 | 应对 |
|------|---------|------|------|
| EMQX HTTP 认证调试耗时（Day 4） | 高 | 延期1天 | 提前安装 MQTTX，准备好抓包工具；我提供完整配置 |
| Konva 地图实时渲染性能问题（Day 6） | 中 | 延期0.5天 | 高频位置更新直接操作 Layer 不走 React 状态，我提供完整实现 |
| 真实机器人 VDA5050 报文不标准（Day 12） | 高 | 延期1-2天 | 尽早拿到厂商 SDK 或报文样例，Day 4 前确认格式 |
| 前后端接口联调返工（Day 9） | 中 | 延期0.5天 | Day 2 结束前确认接口契约，前端用 Mock 数据并行开发 |
| 服务器网络/安全组配置问题（Day 1） | 中 | 延期0.5天 | 按检查清单逐项验证，遇到问题及时抛出 |

---

## 五、每日工作节奏建议

- **上午**：写代码（我辅助生成，你 review + 调试）
- **下午**：联调验证 + 修 bug + 准备次日任务
- **每天结束**：对照完成标志确认当天目标是否达成，未达成的任务评估是否影响后续

---

## 六、后续迭代（MVP 之后）

| 优先级 | 功能 |
|--------|------|
| 高 | 飞书 Webhook 告警通知 |
| 高 | 告警中心页（前端） |
| 高 | operator 细粒度机器人授权 |
| 中 | 人员管理页 |
| 中 | 机器人历史轨迹回放 |
| 低 | APP ID 管理页 |
| 低 | 系统设置页（告警规则配置 UI） |
| 低 | 审计日志查询页 |
