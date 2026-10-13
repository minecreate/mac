# LineNexus Cloud Web

Vue 3 + Vite 控制台。开发时通过 Vite 代理访问 Spring Cloud Gateway（云 API 入口）。

## 启动

```bash
cd linenexus-web
npm install
npm run dev
```

浏览器打开 http://127.0.0.1:5173/

需要本机已运行：

- Nacos（注册中心 8848，控制台 18088）
- MySQL `linenexus_asset`（含 `alarm_rule` / `alarm_event`，见 `schema-patch-alarm.sql`）
- `docker compose up -d`（在 `linenexus-cloud/`：TDengine + Mosquitto）
- `linenexus-gateway`（Spring Cloud Gateway，18080）
- `linenexus-asset`（18081）
- `linenexus-monitor`（18082，MQTT ingest → TDengine；历史/告警 API）

页面请求 `/api/*` 由 Vite 转发到 `http://127.0.0.1:18080`。

监控菜单：

- `/monitor/history` 历史曲线
- `/ops/alarms` 告警规则与活动告警
