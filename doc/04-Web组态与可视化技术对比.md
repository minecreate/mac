# LineNexus — Web / 云组态与 IIoT 可视化技术对比

> 状态：选型参考 / 评审材料  
> 更新：2026-09-29  
> 范围：Web 组态、云 HMI、IIoT 可视化相关技术与产品（与 LineNexus 平台内嵌组态更相关）  
> 关联：`01-总体架构设计.md`；平台内 HMI 当前底座为 Konva（见实现与进展）

---

## 1. 说明

本文对比的是 **Web / 云组态、IIoT 可视化** 一类技术，不是传统工控机上的 WinCC / 组态王完整替代清单。

分层提醒：

| 层 | 常见技术 | 说明 |
| --- | --- | --- |
| 产线主操作站 / 验收级 SCADA | Ignition、WinCC 等商业栈 | 常与自研平台**对接**，不宜当内嵌库 |
| 平台内嵌组态画面 | Konva、Fabric、Meta2d、X6 等引擎 | LineNexus 更贴近这一层 |
| 边云实时通道 | MQTT、WebSocket 等 | 与画布引擎正交，另文讨论 |

---

## 2. 总对比表

| 技术 / 产品 | 类型 | 开源 | 擅长 | 不擅长 | 和 LineNexus 的契合 | 业内常见用法 |
| --- | --- | --- | --- | --- | --- | --- |
| **Konva / vue-konva** | 2D 画布引擎 | MIT | 绝对定位、拖拽缩放、自研编辑器 | 无现成工业图元/组态产品 | **高**（当前底座） | 自研 Web HMI 画面层 |
| **Fabric.js** | 2D 画布引擎 | MIT | 对象模型、图像/文本编辑 | 同上，图编辑生态略老 | 高 | 同 Konva，可选替代 |
| **AntV X6** | 图编辑引擎 | MIT | 节点/边、锚点、连线、布局 | 不像经典「拖控件到工艺画面」 | 中（适合管线/拓扑子域） | 流程、管网、低代码连线 |
| **LogicFlow** | 图编辑引擎 | Apache | 流程图、可扩展节点 | 工业 P&ID 弱 | 中 | 审批流/业务流更多 |
| **JointJS** | 图编辑引擎 | 开源+商业 | 连线/图示成熟 | 完整商业功能收费 | 中 | diagram 类产品 |
| **Meta2d.js** | 组态向 2D 引擎 | 核心 MIT | 数据驱动、动画、偏组态 | 企业图元/完整编辑器常收费 | 中（能力近，品牌边界需分清） | 国内 Web 组态项目 |
| **GoView** | 大屏低代码 | 开源 | 图表看板、拖拽大屏 | 工艺组态/写点弱 | 低（偏运营大屏） | 数据可视化大屏 |
| **FUXA** | 整包 Web SCADA/HMI | 开源(+Pro) | 编辑器+协议+运行态一体 | 难嵌进自有云边平台 | 低（并列系统） | 中小项目/演示 |
| **ThingsBoard** | IoT 平台+Dashboard | 开源+商业 | 设备、规则、租户仪表盘 | 不是经典工艺组态 | 低（功能重叠大） | IIoT 监控平台 |
| **Ignition Perspective** | 商业 Web SCADA/HMI | 商业 | 生产级画面、告警、部署 | 贵，难当「内嵌库」 | 对接用，不当底座 | 厂级 Web HMI 主流之一 |
| **自研 SVG/Canvas** | 自研 | — | 完全可控 | 工期最长 | 视团队而定 | 大厂/强定制平台 |

---

## 3. 按目标选型（短表）

| 目标 | 更合适 | 不太合适 |
| --- | --- | --- |
| 平台**内嵌**组态，自己管点表/权限 | Konva、Fabric、Meta2d 核心、X6（局部） | Ignition、FUXA 整包、ThingsBoard |
| **工艺画面**拖拽摆放（泵阀灯数显） | **Konva / Fabric / Meta2d** | X6、LogicFlow（除非强连线） |
| **拓扑/管线**自动连线 | **X6 / LogicFlow / JointJS** | 纯 Konva（能做但费劲） |
| **运营大屏**图表 | GoView、ECharts、ThingsBoard | Konva 主做组态时不必硬上 |
| **产线验收级**主操作站 | Ignition / WinCC 等 | 开源引擎单打 |
| **快速有个能用的 Web HMI** | FUXA | 从零 Konva（要自己写编辑器） |

---

## 4. 开源可嵌入引擎（评审短表）

仅列「可嵌进自有前端、非整包替换平台」的选项：

| 引擎 | 许可 | 模型 | 更适合 | 备注 |
| --- | --- | --- | --- | --- |
| Konva | MIT | 自由画布 / Shape | 工艺组态主画面 | LineNexus 当前选择 |
| Fabric.js | MIT | 自由画布 / Object | 同 Konva | 可作备选 |
| Meta2d.js | 核心 MIT | 偏组态图元 | 组态向能力多 | 企业图元/成品编辑器常另收费 |
| AntV X6 | MIT | Node / Edge 图 | 拓扑、管线子域 | 可不换主底座，局部引入 |
| LogicFlow | Apache | 流程图 | 业务流/简单拓扑 | 工业图元弱 |
| JointJS | 开源+商业 | 图 | 专业 diagram | 注意商业版边界 |

---

## 5. 与 LineNexus 现状的落点

| 项 | 现状 / 建议 |
| --- | --- |
| 画面底座 | **Konva + vue-konva**（合理，贴近 Web 自研组态常见做法） |
| 复杂管线 | 主画布保持 Konva；自动布线需求高时再**局部**引入 X6 |
| 边云实时 | MQTT（边→云）+ 云侧 WebSocket 推浏览器（与画布正交） |
| 产线主 HMI | 可对外接 Ignition 等；平台内继续自研轻量组态 |

**结论（选型口径）：**

- 做「云边平台内的 Web 组态」→ 自由画布类（Konva）是主流路径之一。  
- X6 是图/拓扑引擎，不是 SCADA 产品；适合补强连线，不宜默认整页替换。  
- Meta2d / FUXA / Ignition 各有定位：引擎加速、整包试用、生产级对接——不要混为一谈。

---

## 6. 参考链接

| 名称 | 链接 |
| --- | --- |
| Konva | https://konvajs.org/ |
| vue-konva | https://github.com/konvajs/vue-konva |
| AntV X6 | https://x6.antv.antgroup.com/ |
| LogicFlow | https://github.com/didi/LogicFlow |
| Meta2d.js | https://github.com/le5le-com/meta2d.js |
| GoView | https://github.com/dromara/go-view |
| FUXA | https://github.com/frangoteam/FUXA |
| ThingsBoard | https://github.com/thingsboard/thingsboard |
| Ignition Perspective | https://inductiveautomation.com/ignition/modules/perspective |
