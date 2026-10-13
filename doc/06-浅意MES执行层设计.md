# LineNexus — 浅意 MES（执行层）设计

> 状态：**设计 + P0 已实现可联调**（详见 `07-浅意MES执行层开发记录.md`）  
> 更新：2026-10-02  
> 定位：ISA-95 **Level 3 子集**（执行），不是完整 MES  
> 范围：**工位 + 配方 + 批次执行 + 资源冲突 + 设备态**  
> 关联：`01-总体架构设计.md`、`03-数据存储.md`、`05-组态HMI模块开发记录.md`、`07-浅意MES执行层开发记录.md`

---

## 1. 目标与边界

### 1.1 要解决什么

当前系统偏 **L2 监视**（点表 / 网关 / 组态读写）。目标岗位需要能讲清：

> 业务单如何变成设备动作，多任务如何抢资源，设备异常如何 Hold / 放行。

本设计补一层 **薄执行壳（Execution）**，对外可称「浅意 MES / MOM 执行层」。

### 1.2 做 / 不做

| 做（P0） | 不做（明确砍掉） |
| --- | --- |
| 产线 / 工位主数据 | BOM、库存、采购 |
| 配方模板 + 参数下发 | APS 高级排产、产能仿真 |
| 批次单全生命周期 | 完整质量模块 / SPC |
| 工位资源锁与冲突队列 | AGV 路径规划 |
| 设备状态机（Idle/Running/…） | 多工厂、多法人组织树 |
| 关键步人工放行（可选） | 电子签名合规认证（仅留接口） |
| 与 HMI 写值共用锁 | 替换现有点表/组态 |

### 1.3 一句话产品定义

**批次选配方 → 占用工位 → 按工步读写点 → 冲突排队/抢占 → 设备态门禁 → 完成回写。**  
点表与组态仍是「手和眼」，执行层是「大脑的派工」。

---

## 2. 领域模型

### 2.1 对象关系

```text
Tenant
 └── Site（厂 / 实验室）
      └── Line（产线 / 实验线）
           └── Station（工位）←── 资源锁打在这一层
                 └── DeviceBinding[]  → edge gatewayId + deviceId
                       └── Point 仍在边端权威

Recipe（配方模板）
 └── RecipeStep[]     读/写/等条件/延时/人工确认
      └── 参数占位符 {{temp}} 等

BatchOrder（生产订单）
 └── 订单号 orderNo + 绑定 Recipe + 目标 Station
      └── BatchRun（一次执行实例）
            └── StepRun[] + JobLog[]
```

> 对外产品名：**生产订单**。表名仍为 `batch_order`（历史兼容）；API 主路径 `/orders`，`/batches` 为别名。

### 2.2 核心实体

| 实体 | 含义 | 关键字段 |
| --- | --- | --- |
| `Site` | 场地 | id, tenant_id, name |
| `Line` | 线体 | id, site_id, name |
| `Station` | 工位（锁粒度） | id, line_id, code, gateway_id, device_ids[], enabled |
| `EquipmentState` | 工位设备态 | station_id, state, fault_code, updated_at |
| `Recipe` | 配方 | id, name, version, status(draft/published) |
| `RecipeStep` | 工步 | seq, type, station_role?, binding, param_map, timeout_sec |
| `BatchOrder` | **生产订单** | id, **order_no**, recipe_id, station_id, params_json, priority, status |
| `BatchRun` | 执行实例 | id, batch_id, status, locked_resource, started/ended |
| `ResourceLock` | 资源锁 | resource_key, owner_run_id, priority, expire_at |
| `ExecLog` | 审计日志 | run_id, step_seq, event, detail, ts |

说明：

- **锁打在 Station**，不打在单个 point（避免锁碎片；多点同属一工位）。
- Device / Point **继续以边端为准**；云侧 Station 只存绑定关系。
- `BatchOrder` ≈ 浅意 MES 的「生产订单」；对外文案用订单，表名 `batch_order`；不要再引入第二套 WorkOrder。

### 2.3 工步类型（P0）

| type | 行为 |
| --- | --- |
| `WRITE` | 写点（值来自配方参数或常量） |
| `READ_ASSERT` | 读点并断言（等于 / 大于 / 区间） |
| `DELAY` | 等待 N 秒 |
| `WAIT_CONDITION` | 轮询直到条件满足或超时 |
| `OPERATOR_ACK` | 人工确认后才继续（浅合规） |

---

## 3. 状态机

### 3.1 设备态（Station / Equipment）

```text
                    reset
         ┌──────────────────┐
         ▼                  │
      IDLE ──start──► RUNNING ──fault──► FAULT
        ▲                 │                │
        │               hold               reset(允许后)
        │                 ▼                │
        │               HOLD ──────────────┘
        │                 │
        └──── release ◄───┘

      IDLE ──maintain──► MAINTENANCE ──release──► IDLE
```

| 状态 | 含义 | 可否接新批次 |
| --- | --- | --- |
| `IDLE` | 空闲 | 是 |
| `RUNNING` | 有执行中批次 | 否（后来者排队或抢占） |
| `HOLD` | 暂停（等人工/条件） | 否 |
| `FAULT` | 故障 | 否；需复位 |
| `MAINTENANCE` | 维护 | 否 |

**门禁规则：** 仅 `IDLE` 可新开跑；`RUNNING` 时同工位冲突走锁队列；`FAULT`/`MAINTENANCE` 直接拒绝并写明原因。

设备态来源（当前实现）：

1. **执行引擎写入**（启动→RUNNING，结束→IDLE，异常/人工确认→FAULT/HOLD）仍为主路径之一。  
2. **边端 Profile 状态同步**（约 3s）：读 `status.error/running/ready` 刷新工位态；复位写 `commands.reset`。  
3. `status.done` 用于启机后完成等待，不单独改工位 IDLE。

详见 `07-浅意MES执行层开发记录.md` §4。

### 3.2 批次单状态（BatchOrder）

```text
DRAFT → RELEASED → DISPATCHED → COMPLETED
                 ↘ CANCELLED
                 ↘ FAILED → CLOSED（确认异常后进历史）
```

界面建议（已落地）：**在制** = DRAFT/RELEASED/DISPATCHED/FAILED（含 Run HOLD 待放行）；**历史** = COMPLETED/CANCELLED/CLOSED。

### 3.3 执行实例状态（BatchRun）

```text
PENDING → QUEUED → RUNNING → COMPLETED
                  ↘ RUNNING → HOLD → RUNNING
                  ↘ PREEMPTED → QUEUED（可选再调度）
                  ↘ FAILED / CANCELLED
```

| 状态 | 界面展示要点 |
| --- | --- |
| `QUEUED` | 「等待工位锁：被 run-xxx 占用」 |
| `PREEMPTED` | 「被更高优先级批次打断」 |
| `HOLD` | 「等待人工放行：步骤 N」 |

---

## 4. 冲突与调度

### 4.1 资源键

```text
resource_key = "station:{stationId}"
```

同一 Station 同时最多 **1** 个 `RUNNING` 的 BatchRun。

### 4.2 裁决策略（P0 写死，可配置化留后）

1. 申请锁时 Station 必须为 `IDLE` 或（抢占场景下）`RUNNING`。
2. 若无锁：加锁 → Run=`RUNNING` → 设备态=`RUNNING`。
3. 若有锁且新任务 **优先级 ≤ 当前**：新任务 `QUEUED`，记 `wait_reason`。
4. 若有锁且新任务 **优先级 > 当前** 且允许抢占：  
   当前 Run → `PREEMPTED`（尽量停在安全步；P0 可先发取消）→ 释放锁 → 新任务获得锁。
5. 锁带 `expire_at`（默认 30min，可按配方覆盖），超时强制释放并 `FAULT`/`FAILED`，防死锁。
6. **HMI 写值**：写前申请同一 `station` 锁（短租，如 15s）；失败则前端提示「工位被批次占用」。

### 4.3 与「调度模块」关系

本设计中 **冲突调度是执行层内建能力**，不单独再开 `mes` + `scheduler` 两套。  
若以后要做周期巡检类 Job，可共用 `ResourceLock` 表，Job 也占 `station:*`。

---

## 5. 模块与部署

### 5.1 服务划分

| 方案 | 说明 | 建议 |
| --- | --- | --- |
| A. 新服务 `linenexus-execution` | 独立库 `linenexus_execution`，经 Gateway 暴露 `/api/execution/**` | **推荐**（简历叙事清晰） |
| B. 挂在 `linenexus-asset` | 快，但 asset 会变胖 | 仅当赶进度时过渡 |

一期推荐 **方案 A**；表与 API 前缀按 execution 设计，即使短期同进程也不改模型。

技术栈与现网一致：Spring Boot 3/4 + JPA + MySQL + Nacos；执行下发先走 **asset 代理边端 HTTP 读写**（与 HMI 相同），MQTT 命令通道成熟后再切。

### 5.2 边端

P0 **不强制**边端理解批次：云按步调用现有 `read`/`write` 即可。  
P1：边端增加 `task` 执行器（整段步骤一次下发 + 进度回执），降低云边往返与断网风险。

### 5.3 库表（MySQL `linenexus_execution`）

```text
site
line
station                 -- device_bindings JSON: [{gatewayId, deviceId}]
equipment_state         -- station_id PK, state, fault_code, updated_at
recipe
recipe_step             -- recipe_id + seq
batch_order
batch_run
resource_lock           -- resource_key PK
exec_log
```

所有业务表带 `tenant_id`。建表脚本随模块：`schema.sql`。

---

## 6. API（经 Gateway）

前缀：`/api/execution`（StripPrefix 后服务内 `/...`）

### 6.1 主数据

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| CRUD | `/sites` `/lines` `/stations` | 工位绑定 gateway/device |
| GET/PUT | `/stations/{id}/equipment-state` | 查/人工复位（FAULT→IDLE） |

### 6.2 配方

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| CRUD | `/recipes` | 草稿 |
| POST | `/recipes/{id}/publish` | 发布，版本+1 |
| GET | `/recipes/{id}/steps` | 工步列表 |

### 6.3 生产订单与执行

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| CRUD | `/orders`（别名 `/batches`） | 建单、改参数/优先级；含 orderNo |
| POST | `/orders/{id}/release` | DRAFT→RELEASED |
| POST | `/orders/{id}/dispatch` | 创建 BatchRun 并尝试抢锁调度 |
| GET | `/orders` `/runs` `/runs/{id}` | 列表与详情（含步骤进度） |
| POST | `/runs/{id}/cancel` | 取消 |
| POST | `/runs/{id}/ack` | 人工放行（HOLD→继续） |
| GET | `/locks` | 当前占用 |
| DELETE | `/locks/{resourceKey}` | 强制释放（记审计） |

### 6.4 写点门禁（供 HMI / asset 调用）

```text
POST /locks/try
  body: { resourceKey, ownerType: "hmi"|"batch", ownerId, ttlSec, priority }
→ 200 locked | 409 conflict { ownerRunId, reason }
```

HMI 运行态写值前先 `try`；批次引擎持锁期间 `ownerType=batch`。

---

## 7. 前端页面

菜单分组建议：

```text
资产    → 边端网关（已有）
监视    → 组态画面（已有）
执行    → 工位 / 配方 / 生产订单 / 运行与锁     ← 新增
```

| 路由 | 页面 | 职责 |
| --- | --- | --- |
| `/exec/dashboard` | 执行看板 | KPI + 设备态/订单分布 + 近7日趋势 + 异常待办 |
| `/exec/stations` | 工位列表 | 建工位、绑设备、看设备态、维护/复位 |
| `/exec/recipes` | 配方列表 | 编辑工步、参数定义、发布 |
| `/exec/recipes/:id/edit` | 配方编辑 | 步骤表格式编辑即可（不必图形化） |
| `/exec/orders` | 生产订单 | 订单号 + 选配方+工位+参数+优先级；释放/下发 |
| `/exec/runs` | 运行监控 | **冲突演示主页面**：排队原因、进度、放行 |
| `/exec/locks` | 资源占用 | 谁锁了哪工位；强制释放 |

**冲突必须在 `/exec/runs` 一眼可见**，不要埋在日志里。

---

## 8. 执行引擎（云侧 P0）

### 8.1 调度循环（简化）

```text
每 N 秒 / 或 dispatch 事件触发：
  1. 处理 QUEUED：按 priority desc, created_at asc 尝试抢锁
  2. 对 RUNNING：推进当前 StepRun
  3. 步骤成功 → 下一步；失败 → FAULT + BatchRun FAILED
  4. OPERATOR_ACK → HOLD，等 /ack
  5. 全部完成 → 释锁 → 设备态 IDLE → BatchOrder COMPLETED
```

P0 可用 Spring `@Scheduled` + 行锁（`SELECT … FOR UPDATE`）实现；不必上 RocketMQ。  
P1 再引入 MQ：步骤命令与回执异步化。

### 8.2 参数替换

配方步骤中的值支持：

```json
{ "pointId": "sp_temp", "valueFrom": "param:targetTemp" }
```

批次单 `params_json`：`{"targetTemp": 37.5, "holdSec": 60}`。

### 8.3 与组态关系

- 组态仍绑 `gatewayId/deviceId/pointId`。
- 运行态写值走锁；只读刷新不占锁。
- 后续可在画面上显示「本工位当前批次号」（非 P0）。

---

## 9. 演示脚本（面试 / 联调金句）

固定实验室剧本（建议写进 README）：

1. 建工位 `ST-INCUBATOR-01`，绑 CODESYS 设备。  
2. 配方「培养初始化」：写启动位 → 等温度到位 → 延时 → 写保持。  
3. 批次 A 优先级 5 下发 → 工位 RUNNING。  
4. 批次 B 优先级 5 同工位下发 → **QUEUED**，界面显示等待 A。  
5. 批次 C 优先级 9 下发 → **抢占** A（或拒绝抢占，二选一配置）并展示原因。  
6. 模拟故障点 → 设备态 FAULT，新批次被拒。  
7. 复位后，队列中下一批自动起。  
8. HMI 在 A 执行中点写值 → 提示工位占用。

---

## 10. 分期

### P0 — 竖切可演示（优先）

- [x] 表结构 + `linenexus-execution` 骨架  
- [x] 工位 / 设备态 / 配方 / 批次 / Run  
- [x] DB 锁 + 排队（抢占可后做，先做排队）  
- [x] 云逐步调用边端 write/read  
- [x] 页面：工位、配方、批次、运行、锁  
- [x] HMI 写值接 `tryLock`  
- [x] Device + Profile 映射；启机等 `status.done`  
- [x] 边端 status 同步设备态 + reset 写点  

### P1

- [ ] 优先级抢占（配置项已有，默认关）  
- [ ] `commands.stop` 业务入口  
- [ ] 边端 task 执行器 + 回执  
- [x] 故障点映射设备态（已做 status.error 同步）  
- [ ] `/exec/locks` 强制释放审计加固  
- [ ] 演示剧本端到端固化（FAULT→复位→队列续跑）  

### P2

- RocketMQ 削峰、Redis 分布式锁（多实例 execution）  
- 批次↔组态联动展示  
- 简单放行电子记录导出  

---

## 11. 目录建议

```text
linenexus-cloud/
├── linenexus-execution/          # 新模块
│   └── .../execution/
│       ├── site|station|recipe|batch|lock|engine
└── linenexus-web/src/
    ├── api/execution.js
    └── views/exec/
        ├── StationListView.vue
        ├── RecipeListView.vue
        ├── RecipeEditView.vue
        ├── OrderListView.vue
        ├── RunMonitorView.vue
        └── LockListView.vue
```

Gateway 增加路由：`/api/execution/**` → `linenexus-execution`。

---

## 12. 风险与开放问题

1. **抢占是否默认开启？** 建议 P0 默认关闭（只排队），配置项 `preemptionEnabled`。  
2. **多设备工位**：一 Station 绑多 device 时，锁仍按 Station；步骤里写明 deviceId。  
3. **云逐步下发 vs 边端整包执行**：P0 云控；现场延迟大再上 P1。  
4. **租户**：沿用 `tenant-dev`；查询强制带 tenant。  
5. **命名对外**：产品文案用「执行 / 生产订单」；简历可写「浅意 MES 执行层（ISA-95 L3 子集）」。

---

## 13. 下一步实现顺序

1. 建库表 + execution 空服务注册 Nacos。  
2. Station + EquipmentState CRUD。  
3. Recipe + 发布。  
4. ResourceLock + Batch dispatch（先只排队）。  
5. Engine 跑 WRITE/DELAY 两种步。  
6. Run 监控页 + 双批次冲突 demo。  
7. HMI 接锁。  
8. OPERATOR_ACK / FAULT 复位。

---

## 附录 A. 与完整 MES 对照（防范围膨胀）

| MES 常见模块 | 本设计 |
| --- | --- |
| 主生产计划 / APS | ❌ |
| 工单派工 | ✅ 瘦身为 BatchOrder + dispatch |
| 配方 / 工艺路线 | ✅ Recipe + Steps |
| 资源/工位管理 | ✅ Station + Lock |
| 报工 / 产量 | ❌（完成态即可） |
| 质量 / 追溯树 | ❌（仅 exec_log） |
| 仓储物流 | ❌ |

## 附录 C. 设备 Profile（机型对接配置）

> 关系：**订单 → 工位 → 设备(PLC) → Profile**。  
> Profile 挂在执行设备 `equipment_device` 上，工位只绑设备，不再直接绑 Profile。

```text
订单/配方 params_json { "productTime": 30000 }
        ↓
工位.device_bindings[0] → equipment_device.profile_id → equipment_profile
        ↓
params.*.pointId           → 边端写参数
commands.start/stop/reset  → 启停复位
status.running/ready/done/error → 设备态同步 / 完成等待
```

**发布必填：** `commands.start|stop|reset`，`status.running|ready|done|error`（均需非空 `pointId`）。

- API：`/api/execution/profiles`、`/api/execution/devices`
- 页面：`/exec/profiles`、`/exec/devices`、工位只选网关+设备
- 实现细节与联调清单：`07-浅意MES执行层开发记录.md`


| 名词 | 含义 |
| --- | --- |
| 浅意 MES | 只做执行闭环的 L3 子集 |
| 工位 Station | 可占用的最小生产/实验资源 |
| 执行设备 EquipmentDevice | 边端 PLC 实例在执行层的登记，绑 Profile |
| 配方 Recipe | 可版本化的参数化工步模板 |
| 生产订单 BatchOrder | 一次要执行的业务单（含 orderNo） |
| 运行 BatchRun | 订单的一次调度执行实例 |
| 设备态 | 工位级行为门禁，不是单个点的 quality |
