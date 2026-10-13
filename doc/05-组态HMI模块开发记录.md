# LineNexus — 组态 HMI 模块开发记录

> 状态：P0 已落地并可验证读写；编辑能力仍为最小集  
> 更新：2026-09-29  
> 用途：新开对话继续完善组态时的上下文入口  
> 关联：`01-总体架构设计.md`、`03-数据存储.md`、`04-Web组态与可视化技术对比.md`

---

## 1. 目标与阶段结论

### 1.1 产品目标

在 LineNexus **平台内**提供自有 Web 组态能力（不只是设备列表读写页）：

- 浏览器拖拽编辑画面
- 绑到边端 `gatewayId / deviceId / pointId`
- 运行态显示点值；点击可写值

### 1.2 选型结论（已定）

| 项 | 结论 |
| --- | --- |
| 画布底座 | **Konva + vue-konva**（MIT，自主可控） |
| 不做主底座 | Meta2d（商业边界易混）、整包 FUXA/ThingsBoard、直接嵌 Ignition |
| 复杂管线 | 主画布保持 Konva；自动布线需求高时再**局部**考虑 AntV X6 |
| 实时推送 | **未做**；P0 运行态为 HTTP 轮询 `read`；后续规划 MQTT ingest + WebSocket |

技术对比详见 `04-Web组态与可视化技术对比.md`。

### 1.3 已验证

- 创建 / 保存 / 发布画面
- 编辑器拖入图元、绑点（网关→设备→点）
- 运行态轮询读值、按钮/开关写值到设备（基础功能验证通过）

---

## 2. 架构（当前）

```text
浏览器 linenexus-web
  ├─ /hmi/screens          画面列表
  ├─ /hmi/screens/:id/edit 编辑态（Konva）
  └─ /hmi/screens/:id/runtime 运行态（Konva 只读 + 绑点刷新）
           │
           │ HTTP
           ▼
API Gateway :18080  /api/assets/**
           │
           ▼
linenexus-asset :18081
  ├─ /hmi/screens*          画面 CRUD / publish（MySQL hmi_screen）
  └─ /edge-gateways/...     点表代理 + read/write → 边端 HTTP
           │
           ▼
linenexus-edge :8081        设备/点权威 + 南向读写
```

要点：

- Konva **只负责画布**；点真源、写值仍走 asset 代理边端。
- HMI API 短期挂在 **asset**，路径设计为 `/hmi/*`，便于日后迁到 `monitor`。
- 元素 **不存协议地址**，只绑 `gatewayId + deviceId + pointId`。

---

## 3. 数据模型

### 3.1 MySQL 表 `hmi_screen`

库：`linenexus_asset`（见 `linenexus-asset/src/main/resources/schema.sql`）

| 字段 | 说明 |
| --- | --- |
| id | 画面 ID |
| tenant_id | 租户，默认 `tenant-dev` |
| name | 名称 |
| version | 版本，保存/发布递增 |
| status | `draft` / `published` |
| document_json | 画面 JSON（LONGTEXT） |
| updated_by | 更新人 |
| created_at / updated_at | 时间戳 |

### 3.2 `document_json` 结构（schemaVersion = 1）

```text
schemaVersion
canvas: { width, height, background }
layers: [{ id, name, visible, locked }]     -- P0 仅默认一层；UI 未做图层面板
elements: [{
  id, type, layerId,
  transform: { x, y, w, h, rotation },
  style: {...},
  props: {...},
  binding?: {
    gatewayId, deviceId, pointId,
    kind: text | color | visible | clickWrite,
    map?: {...}
  }
}]
meta: { grid, snap }                        -- 为对齐吸附预留；P0 未启用逻辑
```

前端解析/工厂：`linenexus-web/src/hmi/model/types.js`  
（计划里曾写 `types.ts`，实际为 **JS + JSDoc**，与现有 Vue 工程一致。）

---

## 4. API

经网关前缀：`/api/assets`（StripPrefix 后 asset 收到 `/hmi/screens...`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/assets/hmi/screens` | 列表；可选 `?status=` |
| GET | `/api/assets/hmi/screens/{id}` | 详情 |
| POST | `/api/assets/hmi/screens` | 创建（可带 documentJson） |
| PUT | `/api/assets/hmi/screens/{id}` | 保存草稿（status→draft，version+1） |
| POST | `/api/assets/hmi/screens/{id}/publish` | 发布 |
| DELETE | `/api/assets/hmi/screens/{id}` | 删除 |

绑点读写复用现有 asset 代理：

- `POST .../devices/{deviceId}/read`
- `POST .../devices/{deviceId}/write`

前端封装：`linenexus-web/src/api/hmi.js`、`src/api/asset.js`

---

## 5. 前端目录与路由

```text
linenexus-cloud/linenexus-web/
├── package.json                 # 依赖含 konva、vue-konva
├── src/main.js                  # app.use(VueKonva)
├── src/App.vue                  # 菜单：监控 → 组态画面
├── src/router/index.js
├── src/api/hmi.js
└── src/hmi/
    ├── model/types.js           # 文档模型、createElement、collectPointKeys
    ├── canvas/StageHost.vue     # Konva 舞台；edit / runtime
    ├── editor/commands.js       # applyPatch + 历史栈接口（undo 默认关闭）
    ├── symbols/registry.js      # pump/valve/lamp/tank/motor
    ├── runtime/applyBindings.js # BindingAdapter：点值→图元；写值解析
    └── （views）
        views/hmi/HmiScreenListView.vue
        views/hmi/HmiEditorView.vue
        views/hmi/HmiRuntimeView.vue
```

| 路由 | 页面 |
| --- | --- |
| `/hmi/screens` | 列表 |
| `/hmi/screens/:id/edit` | 编辑 |
| `/hmi/screens/:id/runtime` | 运行 |
| `/monitor/realtime` | 重定向到 `/hmi/screens` |

---

## 6. 后端目录

```text
linenexus-asset/src/main/java/com/linenexus/asset/hmi/
├── HmiScreenController.java
├── HmiScreenService.java
├── HmiScreenEntity.java
├── HmiScreenRepository.java
├── HmiScreen.java / HmiScreenRequest.java
└── HmiDocuments.java            # 空文档默认 JSON
```

注意：修改 Java 后需 **重新 package 并重启** asset；旧 JAR 无 `/hmi` 会 404（创建失败曾因此出现）。

---

## 7. P0 功能清单（已实现）

### 编辑器

- 左侧图元：数显、矩形、指示灯、按钮、开关、管线 + 符号（泵/阀/灯/罐/电机）
- 选中、拖动、Transformer 缩放旋转、删除
- 右侧属性：位置尺寸、部分样式、点位绑定
- 保存 draft、发布、预览运行

### 运行态

- 加载画面文档，Konva 只读
- **每 2s** 按 binding 涉及的设备调用 `read`，经 `applyBindings` 刷新
- `clickWrite` / 按钮 / 开关：确认后 `writePoint`

### 为 B 期预留（代码边界已有，UI/逻辑未全开）

| 扩展点 | 位置 | 说明 |
| --- | --- | --- |
| EditorCommand + 历史栈 | `editor/commands.js` | `setEnabled(true)` 可开 undo/redo |
| layers[] | document 模型 + StageHost 按层过滤 | 无图层面板 |
| SymbolRegistry | `symbols/registry.js` | 注册即可扩图元 |
| BindingAdapter | `runtime/applyBindings.js` | 新 bind_kind 加在此 |
| meta.grid / snap | document.meta | 对齐吸附未实现 |

---

## 8. 已知问题与已修缺陷

| 项 | 说明 |
| --- | --- |
| 绑点表单曾无法选齐 | 半成品 binding 被清空触发 watch 重置表单；已改为只在 `selectedId` 变化时回填，选齐三点再写入 binding |
| 图元偏少 | **P0 故意裁剪**；扩库走 SymbolRegistry，不是 Konva 能力不足 |
| 运行态延迟 | 轮询 2s + 边端采集周期；未接 MQTT/WS |
| 鉴权 | 无细粒度 `hmi:edit` / `hmi:write`；写值无审计表 |
| asset 进程 | 必须跑含 HMI 的新包；JDK 建议 21+（本机曾用 jdk-27 编译） |

---

## 9. 本地怎么跑（续作时）

1. MySQL：确保存在表 `hmi_screen`（执行 asset 的 `schema.sql` 或已建库）。  
2. 启动 Nacos、gateway `:18080`、**重新打包后的** asset `:18081`、边端 `:8081`。  
3. `linenexus-web`：`npm run dev` → `http://127.0.0.1:5173/hmi/screens`。  
4. 菜单：**监控 → 组态画面**。

写值/读值依赖边端已配置设备与点，与「设备与点表」页同一数据源。

---

## 10. 新对话建议完善顺序（组态模块）

目标：验证「Konva 框架能否支撑最终组态编辑效果」，优先编辑体验，实时链路可并行稍后。

建议探针（按优先级）：

1. **多选 / 框选**  
2. **对齐吸附**（启用 `meta.grid/snap`）  
3. **撤销重做**（打开 `commands` 历史栈）  
4. **图元库扩充**（SVG/自定义 SymbolRegistry，10～20 个）  
5. **100～200 图元压测**（卡顿再优化层缓存）  
6. （可选）简单 pipe 增强；复杂自动布线再评估 X6  
7. 实时：MQTT ingest + WebSocket 替换轮询（平台能力，非画布本身）  
8. 写权限 + 操作审计  

**明确暂缓：** 整库换 Meta2d、引入 FUXA 替换平台、Go 网关大重构、完整多租户 IAM。

---

## 11. 关键代码锚点（给 Agent 续作）

| 意图 | 优先打开 |
| --- | --- |
| 改编辑交互 | `views/hmi/HmiEditorView.vue`、`hmi/canvas/StageHost.vue` |
| 改图元种类/默认尺寸 | `hmi/model/types.js`、`hmi/symbols/registry.js` |
| 改绑点/写值映射 | `hmi/runtime/applyBindings.js` |
| 改存盘结构 | `types.js` + 后端 `HmiScreen*`（注意兼容已有 document_json） |
| 改列表/发布 | `views/hmi/HmiScreenListView.vue`、`api/hmi.js` |
| 改运行刷新策略 | `views/hmi/HmiRuntimeView.vue`（当前 `POLL_MS = 2000`） |

---

## 12. 一句话交接

**P0：Konva 组态已能编、绑、存、读、写设备；编辑器仍是最小集，扩展点已留。新对话请从「编辑体验探针」（多选/对齐/撤销/图元库）继续完善，实时 WS 与权限审计按平台节奏另排。**
