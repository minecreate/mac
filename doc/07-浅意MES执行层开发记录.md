# LineNexus — 浅意 MES 执行层开发记录

> 状态：**P0 竖切已可联调**；设备 Profile 映射 + 边端状态同步已接入  
> 更新：2026-10-02  
> 用途：新开对话继续完善执行层时的上下文入口  
> 关联：`01-总体架构设计.md`、`06-浅意MES执行层设计.md`、`05-组态HMI模块开发记录.md`、`02-开发进展.md`

---

## 0. 登录与角色（P0）

| 角色 | 账号 / 密码 | 能力 |
| --- | --- | --- |
| admin | admin / admin123 | 全部 |
| operator | operator / op123 | 执行 + 组态写；不可改网关/点表注册 |
| viewer | viewer / view123 | 看板与只读；写接口网关 403 |

- 登录：`POST /api/auth/login`（asset）→ JWT  
- 网关：`Authorization: Bearer` 必填；注入 `X-Tenant-Id` / `X-User-*` / `X-Roles`  
- 前端：`/login`，菜单按角色过滤  

---

## 1. 目标与阶段结论

### 1.1 产品目标

在 L2（点表 / 组态读写）之上补一层 **薄执行壳**：

> 生产订单选配方 → 占用工位 → Profile 写参数/启停 → 等完成位 → 跑配方工步 → 设备态门禁 / 复位。

### 1.2 本阶段已定模型（重要）

```text
订单 → 工位 Station
         └─ device_bindings[]（只绑设备，不直接绑 Profile）
              └─ EquipmentDevice（gatewayId + deviceId）
                   └─ profileId → EquipmentProfile
                        ├─ params.*      业务键 → 点
                        ├─ commands.*    start / stop / reset
                        └─ status.*      running / ready / done / error
```

| 对象 | 职责 |
| --- | --- |
| Station | 资源锁粒度；绑一台或多台执行设备 |
| EquipmentDevice | 边端 PLC 在执行层的登记；**必须绑已发布 Profile** |
| EquipmentProfile | 机型对接配置（命令 / 状态 / 参数映射） |
| Recipe | 绑 `execDeviceId`；`paramsJson` 对应 Profile.params 业务键 |
| BatchOrder / BatchRun | 订单生命周期 + 引擎逐步读写边端 |

### 1.3 已验证（实验室）

- 工位 / 执行设备 / Profile / 配方 / 订单 / Run / 锁 页面可走通
- 下发：写 Profile 参数 + `commands.start`，再等 `status.done`，再跑配方工步（如 DELAY）
- 配方编辑：选执行设备 → 按 Profile.params 编辑变量 → 选点写工步
- 设备态：周期读 `status.running/ready/error` 刷新工位态；复位写 `commands.reset`
- HMI 运行态写值走 `tryLock`（与工位锁共用）

---

## 2. 架构（当前）

```text
浏览器 linenexus-web
  ├─ /exec/stations|devices|profiles|recipes|orders|runs|locks
  └─ /hmi/...（组态，写值可占工位锁）
           │
           │ HTTP  /api/execution/**  /api/assets/**
           ▼
API Gateway :18080
           │
     ┌─────┴──────┐
     ▼            ▼
execution:18082  asset:18081
  工位/配方/订单     网关代理 + HMI
  Profile/设备态     read/write → 边端
  Engine tick
  State sync tick
           │
           ▼
linenexus-edge :8081   设备/点权威 + OPC UA/Modbus/MQTT
```

服务要点：

| 组件 | 端口 / 配置 | 说明 |
| --- | --- | --- |
| `linenexus-execution` | `:18082` | MySQL `linenexus_execution`；Nacos 注册 |
| Engine | `engine-tick-ms: 2000` | 排队起跑 + 推进 RUNNING 工步 |
| 设备态同步 | `state-sync-enabled` / `state-sync-tick-ms: 3000` | 读 Profile status.* 刷新 `equipment_state` |
| 边端 IO | `asset-base-url` | 经 asset 代理写/读点 |

---

## 3. Profile 必填映射

发布前必须配置且 `pointId` 非空：

| 段 | 键 | 用途 |
| --- | --- | --- |
| `commands` | `start` | 下发启机 |
| `commands` | `stop` | 预留停机（UI 已有，业务写点待接） |
| `commands` | `reset` | 工位「复位」写 PLC |
| `status` | `running` | 同步 → RUNNING |
| `status` | `ready` | 同步 → IDLE |
| `status` | `done` | 启机后引擎等待完成 |
| `status` | `error` | 同步 → FAULT |

`params` 仍为可选扩展（如 `productTime` / `recipeTime`）；配方页按设备 Profile 动态出变量表。

默认模板点名（可改）：`m0/m1/m2`、`y0/y1/m4/y2`、`t0`。

---

## 4. 设备态推导（边端同步）

优先级（周期约 3s）：

1. 工位为 **MAINTENANCE** → 不覆盖  
2. **HOLD** 且无 `error` → 保持 HOLD  
3. `status.error` = true → **FAULT**（`fault_code=status.error`）  
4. `status.running` = true → **RUNNING**  
5. `status.ready` = true → **IDLE**  

复位 API `POST /stations/{id}/equipment-state/reset`：

1. 仅允许 FAULT / HOLD / MAINTENANCE  
2. 写 `commands.reset`  
3. 库内态清为 IDLE  

说明：执行引擎仍会在起跑/结束时写 RUNNING/IDLE；边端同步与引擎写入并存，以现场点为准纠偏故障/就绪。

---

## 5. 下发与工步顺序

```text
dispatch / tryAcquireAndStart
  → requireProfileReady（设备已绑已发布 Profile）
  → 占资源锁，设备态 RUNNING
  → ProfileApply：写 params（订单/配方参数）+ commands.start
  → 若有 status.done：current_step_seq=0，PROFILE_DONE_WAIT
  → done 到位后进入配方工步 seq≥1
  → WRITE / READ_ASSERT / DELAY / WAIT_CONDITION / OPERATOR_ACK
  → 完成 → 释锁，设备态 IDLE
```

配方必须绑 `execDeviceId`；发布前至少 1 个工步。

---

## 6. 前端页面

| 路径 | 作用 |
| --- | --- |
| `/exec/dashboard` | KPI、设备态/订单分布、近7日趋势、异常待办（ECharts） |
| `/exec/stations` | 工位 CRUD、绑设备、看设备态、维护/复位/置故障 |
| `/exec/devices` | 执行设备登记、绑 Profile |
| `/exec/profiles` + edit | 机型映射编辑（必填命令/状态） |
| `/exec/recipes` + edit | 选设备、编辑 params、工步 |
| `/exec/orders` | 生产订单、选配方/工位、下发 |
| `/exec/runs` | Run 监控、HOLD 确认、取消 |
| `/exec/locks` | 资源锁查看 |

---

## 7. 关键代码入口

| 改什么 | 看哪里 |
| --- | --- |
| Profile 校验 / 模板 | `profile/EquipmentProfileService`、`ProfileDocuments` |
| 下发写参/启机/读状态/写复位 | `profile/ProfileApplyService` |
| 设备态同步 | `station/EquipmentStateSyncScheduler`、`StationService.applyEdgeStatus` |
| 复位写点 | `StationService.reset` |
| 引擎调度 | `engine/ExecutionEngine`、`ExecutionTickService`、`StepAdvanceService` |
| 订单下发 | `batch/BatchService` |
| 配方替换工步 | `recipe/RecipeService` + `RecipeStepRepository`（删后须 flush，防 `uk_recipe_seq`） |
| 边端读写 | `edge/EdgeIoClient` → asset → edge |
| 前端 API | `web/src/api/execution.js` |
| 前端页 | `web/src/views/exec/*` |

---

## 8. 本阶段踩过的坑

1. **执行设备 deviceId 过期**：边端重建 OPC 设备后 UUID 变了，写点 404「设备不存在」→ 在「执行设备」改绑当前边端设备。  
2. **完成判据**：不能写死 `m4`；须等 Profile `status.done`。引擎 `seq=0` 专门轮询完成位，**不得**因配方无 seq=0 而误判「全部工步完成」。等待时先等完成位清除再等置位，避免上周期粘连。  
3. **订单选不了配方**：配方须 **published** 且有工步；`execDeviceId` 须与工位所绑设备一致。  
4. **发布 Bad Request**：空工步；错误信息需 `include-message` + 前端拦截。  
5. **编辑配方 500**：同事务内删工步再插同 `seq` 未 flush，撞 MySQL `uk_recipe_seq` → `deleteByRecipeId` 加 `@Modifying(flushAutomatically)` + `flush()`。  
6. **Station ↔ ProfileApply 循环依赖**：复位注入 `@Lazy ProfileApplyService`。

---

## 9. 明确未做 / 弱项

| 项 | 说明 |
| --- | --- |
| `commands.stop` | 映射必填，业务停机入口未挂完整 |
| 抢占 | `preemption-enabled: false`，仅排队 |
| 边端整包任务执行器 | 仍是云逐步 HTTP 读写 |
| 设备态与引擎竞态 | 已可用，细则/仲裁策略未产品化 |
| 锁页强制释放审计 | 页面有，审计链偏薄 |
| 多设备工位工步选设备 | 默认首台绑定设备 |
| 实时推送 | HMI/设备态仍轮询 |
| 认证 / 多租户 UI | 仍 `tenant-dev` 头 |
| 时序库 / 产量报工 | 不做（设计边界内） |
| 电子签名合规 | 仅 OPERATOR_ACK 浅放行 |

---

## 10. 联调检查清单（刷新）

1. 边端建点（含 reset / error 对应点），执行设备绑当前 `deviceId` + 已发布 Profile。  
2. Profile 填齐 7 个必填映射并发布。  
3. 工位绑该执行设备；配方绑同一 `execDeviceId`，填 params + 至少 1 工步，发布。  
4. 建订单 → 下发 → Run 出现 PROFILE_DONE_WAIT → done → DELAY 等 → COMPLETED。  
5. 模拟 `status.error` → 工位 FAULT，新单被拒；点复位 → 写 reset → IDLE。  
6. 同工位第二单 → QUEUED；HMI 写值占锁提示占用。

---

## 11. 缺口与推荐路线

### 11.1 还缺什么（按价值）

| 优先级 | 缺口 | 为什么重要 |
| --- | --- | --- |
| P0+ | **演示闭环固化** | 功能散点已有，但「排队→故障→复位→续跑」未当成一条金线验收 |
| P0+ | Profile `reset`/`error` 现场点真实可用 | 无点则同步/复位名存实亡 |
| P1 | `commands.stop` 入口 | 必填映射已在，缺业务按钮/引擎停机语义 |
| P1 | 锁强制释放 + 审计 | 现场卡死时运维刚需 |
| P1 | Run/ExecLog 可读 UI | 排障现在偏看库/接口 |
| P1 | 引擎态 vs 边端态冲突策略 | 避免「引擎刚 IDLE 又被 running 拉回」争议 |
| P2 | 优先级抢占 | 配置已有，默认关 |
| P2 | 边端整包任务执行器 | 降云边往返延迟 |
| P2 | HMI 显示当前批次/工位态 | 监视与执行合一 |
| 平台 | 认证、时序、Compose、WS | 产品化与演示稳定性 |

### 11.2 建议下一步（只选一刀）

**下一刀：把设计稿演示脚本「排队→故障→复位→队列续跑」一次打穿并写成可勾选清单。**

1. 确认 Profile 七项点在 PLC 真实存在并可读写  
2. 双单同工位：A 跑、B 排队  
3. 拉高 `status.error` → FAULT，新单拒  
4. 点复位 → 写 reset → IDLE → B 自动起  
5. 步骤与期望记进 README 或本文 §10  

完成后再开 `stop` 或锁审计；抢占与边端 task 放到再下一阶段。

---

## 12. 一句话交接

**执行层 P0 已通：Station→Device→Profile→订单下发→等 done→配方工步；设备态可读 status.*，复位可写 reset。新对话请先确认 Profile 七项必填已发布，再按 §11 选下一刀（演示闭环 / stop / 抢占 / 边端任务器）。**
