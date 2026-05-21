# 机器人运营管理平台后端

当前目录是运营管理平台后端，不包含机器人授权平台能力。

## 技术栈

- Java 26
- Spring Boot 3
- Spring Security + JWT
- MyBatis-Plus
- PostgreSQL
- Redis
- Flyway

## 本平台边界

本后端负责：

- 运营人员登录、刷新、注销
- 已授权机器人同步后的业务副本
- 机器人列表、详情、同步状态
- 巡检、任务下发、告警处理、人员访问授权

本后端不负责：

- APP ID 管理
- API Key / API Secret 管理
- EMQX 机器人认证回调
- 机器人首次激活

这些能力属于独立的机器人授权平台。

## 中间件配置

默认端口：`8080`

中间件连接信息放在独立配置文件：

```text
back/config/application-middleware.yml
```

可以从示例文件复制：

```bash
cp config/application-middleware.example.yml config/application-middleware.yml
```

需要补充：

- PostgreSQL 16：host、port、database、username、password
- Redis 7：host、port、password、database
- EMQX：MQTT host/port、账号密码、Dashboard 地址

`application-middleware.yml` 已加入 `.gitignore`，避免提交远程服务器密码。

首次启动时会自动创建：

```text
租户：默认租户
账号：admin
密码：admin123
```

如果连接的是已有开发库，默认账号密码可能已经被改过。需要临时联调时可在启动参数中打开本地开发种子：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--app.bootstrap.local-dev-seed=true
```

这会创建/更新本地管理员：

```text
账号：local_admin
密码：local123
```

## 启动

需要先安装 Java 26 和 Maven。

```bash
mvn spring-boot:run
```

健康检查：

```text
GET http://localhost:8080/actuator/health
```

Swagger：

```text
http://localhost:8080/swagger-ui.html
```

## 已实现接口

```text
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
GET  /api/auth/me

GET  /api/robots
GET  /api/robots/{robotId}
GET  /api/robots/{robotId}/realtime
GET  /api/robots/{robotId}/connection
GET  /api/robots/{robotId}/state
GET  /api/robots/{robotId}/position

GET    /api/operators
POST   /api/operators
PUT    /api/operators/{operatorId}/role
DELETE /api/operators/{operatorId}
GET    /api/operators/{operatorId}/robots
POST   /api/operators/{operatorId}/robots
DELETE /api/operators/{operatorId}/robots/{robotId}

POST /api/robots/{robotId}/orders
GET  /api/robots/{robotId}/orders
GET  /api/robots/{robotId}/orders/{orderRecordId}
PUT  /api/robots/{robotId}/orders/{orderRecordId}/cancel
POST /api/robots/{robotId}/instant-actions

GET  /api/alerts
GET  /api/alerts/summary
GET  /api/alerts/{alertId}
PUT  /api/alerts/{alertId}/resolve
```

机器人不再通过 HTTP 同步接口导入。用户在账号资料中绑定 `appid / apikey / apisecret` 后，机器人通过 MQTT `connection ONLINE` 完成鉴权并自动入库。

## MQTT / WebSocket 实时闭环

后端启动后会按 `app.middleware.emqx.mqtt` 配置连接 EMQX，订阅：

```text
uagv/v3/#
```

已支持消费：

```text
uagv/v3/{manufacturer}/{serialNumber}/connection
uagv/v3/{manufacturer}/{serialNumber}/state
uagv/v3/{manufacturer}/{serialNumber}/visualization
uagv/v3/{manufacturer}/{serialNumber}/factsheet
```

`connection ONLINE` 必须携带 `identity(appid, apikey, apisecret, robotUniqueId)`。鉴权成功后服务会创建或更新机器人，后续 `state`、`visualization`、`factsheet` 命中已认证设备会话才允许入库。

消息会写入 `robot_connections`、`robot_states`、`robot_positions`、`robot_factsheets`，刷新 Redis `robot:{id}:online`，并推送：

```text
/topic/robots/{robotId}/connection
/topic/robots/{robotId}/state
/topic/robots/{robotId}/position
/topic/robots/{robotId}/order-status
/topic/alerts
```

任务下发和即时指令会发布到：

```text
uagv/v3/{manufacturer}/{serialNumber}/order
uagv/v3/{manufacturer}/{serialNumber}/instantActions
```

下发前会检查 Redis 在线状态、MQTT 认证会话、当前用户访问权限和 factsheet 能力；机器人离线、未鉴权或能力不满足时会返回错误。

## MVP 验收

完整手工验收项见：

```text
docs/mvp-acceptance-checklist.md
```
