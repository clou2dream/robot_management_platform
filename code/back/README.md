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
GET  /api/robots/{robotId}/sync-status
POST /api/robots/sync
GET  /api/robots/{robotId}/realtime
GET  /api/robots/{robotId}/connection
GET  /api/robots/{robotId}/state
GET  /api/robots/{robotId}/position
POST /api/robots/{robotId}/telemetry/demo

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
POST /api/alerts/demo
```

`POST /api/robots/sync` 不传 body 时会同步内置演示机器人，方便前端联调。

`POST /api/alerts/demo` 用于中间件未接入前生成演示告警，方便跑通告警列表、详情和处理流程。

`POST /api/robots/{robotId}/telemetry/demo` 用于中间件未接入前生成演示连接、状态、位置快照，方便跑通实时巡检页面。
