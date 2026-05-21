# MVP 验收清单

## 前置条件

- PostgreSQL、Redis、EMQX 已启动并可访问。
- `config/application-middleware.yml` 已配置真实中间件连接信息。
- 后端服务运行在 `http://localhost:8080`。
- 前端服务运行在 `http://localhost:5173`。
- 当前用户已在账号资料中绑定至少一组 `appid / apikey / apisecret`。
- 机器人端已按 MQTT 协议发布 `connection ONLINE`，携带完整 `identity`，并能持续发布 `state` / `visualization` / `factsheet`。

## 自动检查

在 `code/back` 目录执行：

```powershell
mvn test
```

在 `code/front` 目录执行：

```powershell
npm.cmd run build
```

通过标准：

- 后端测试通过。
- 前端生产构建通过。
- 真实接口验收按下面的手工页面验收执行。

## 手工页面验收

- 登录页：使用 `admin / admin123` 登录成功，失败时有明确错误提示。
- 工作台：展示真实机器人数量、在线数量、未处理告警、近期任务。
- 机器人列表：可查看 MQTT 鉴权接入后的机器人，可跳转巡检页。
- 实时巡检：可选择机器人，看到连接状态、位置、电量、任务状态；真实 MQTT 上报后页面刷新。
- 任务下发：可选择在线机器人，生成节点任务并下发，成功后跳转订单列表。
- 任务订单：可按机器人查看订单，看到刚下发的任务。
- 告警中心：可筛选、查看详情、处理告警，处理后状态变为已处理。
- 操作员管理：admin 可查看操作员列表，并管理角色和机器人授权。

## 生产化前置收口

- 替换默认管理员密码，关闭或限制本地开发种子账号。
- 根据部署域名调整 `CORS_ALLOWED_ORIGINS` 和 `COOKIE_SECURE`。
- 按实际机器人型号继续扩展 factsheet 能力校验和异常映射。
- 对前端包做按路由拆分，消除 Vite chunk 体积提醒。

## 已知 MVP 边界

- 地图选点仍是轻量预览，不是完整地图编辑器。
- 任务生命周期主要覆盖创建、查询、取消；机器人回执驱动的完整状态机可作为二期。
- 当前 WebSocket 使用内置 simple broker，生产大规模连接可再切换外部 broker。
- 多租户管理已有数据隔离基础，但租户后台、租户创建和租户运营报表不在本轮 MVP。
