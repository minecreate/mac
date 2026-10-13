/**
 * 图元库分类（国产组态常见分法）— 编辑器左侧唯一数据源。
 * 新增：types 默认值 → StageHost 渲染 → 挂到此处（可带 style/props/transform 预设）。
 */

/**
 * @typedef {Object} PaletteItem
 * @property {string} type
 * @property {string} label
 * @property {string} [symbolId]
 * @property {string} [hint]
 * @property {string} [icon]          缩略图标名，见 PalettePanel
 * @property {Record<string, unknown>} [style]
 * @property {Record<string, unknown>} [props]
 * @property {{ w?: number, h?: number }} [transform]
 */

/**
 * @typedef {Object} PaletteCategory
 * @property {string} key
 * @property {string} label
 * @property {string} [blurb]
 * @property {boolean} [defaultOpen]
 * @property {PaletteItem[]} items
 */

/** @type {PaletteCategory[]} */
export const PALETTE_CATEGORIES = [
  {
    key: 'display',
    label: '显示',
    blurb: '数值与状态呈现',
    defaultOpen: true,
    items: [
      { type: 'text', label: '数显', icon: 'num', hint: '绑定点值文本刷新' },
      {
        type: 'text',
        label: '大数显',
        icon: 'numLg',
        hint: '大号数值',
        style: { fill: '#f8fafc', fontSize: 28 },
        transform: { w: 160, h: 48 },
        props: { text: '0.0', align: 'center' },
      },
      {
        type: 'text',
        label: '单位数显',
        icon: 'numUnit',
        hint: '数值+单位文案',
        props: { text: '25.0 ℃', align: 'left' },
        transform: { w: 140, h: 32 },
      },
      { type: 'label', label: '标签', icon: 'label', hint: '静态文案' },
      {
        type: 'time',
        label: '时间',
        icon: 'time',
        hint: '实时时钟，格式在属性中改',
        props: { format: 'HH:mm:ss', align: 'center' },
      },
      {
        type: 'date',
        label: '日期',
        icon: 'date',
        hint: '当前日期，格式在属性中改',
        props: { format: 'YYYY-MM-DD', align: 'center' },
      },
      {
        type: 'image',
        label: '图片',
        icon: 'image',
        hint: '贴图/Logo，属性里填 URL 或本地上传',
        props: { src: '' },
        transform: { w: 160, h: 100 },
      },
      {
        type: 'progress',
        label: '进度条',
        icon: 'progress',
        hint: '0–100% 条形',
        props: { value: 60, max: 100 },
      },
      {
        type: 'gauge',
        label: '仪表',
        icon: 'gauge',
        hint: '半圆仪表盘',
        props: { value: 45, min: 0, max: 100 },
      },
      {
        type: 'levelBar',
        label: '液位',
        icon: 'level',
        hint: '立式液位柱',
        props: { value: 55, max: 100 },
      },
      {
        type: 'alarmBar',
        label: '报警条',
        icon: 'alarm',
        hint: '报警/状态横条',
        props: { text: '报警' },
        style: { fill: '#7f1d1d', stroke: '#ef4444', textColor: '#fecaca', fontSize: 13 },
      },
      {
        type: 'statusBadge',
        label: '状态徽章',
        icon: 'badge',
        hint: '圆角状态标签，文案/颜色在属性中改',
        props: { text: '运行' },
      },
    ],
  },
  {
    key: 'input',
    label: '输入',
    blurb: '标准控件；文案/颜色/形状/写值在属性里改',
    defaultOpen: true,
    items: [
      {
        type: 'button',
        label: '按钮',
        icon: 'btn',
        hint: '标准按钮，选中后改文案/颜色/形状/写值',
        props: { text: '按钮', writeValue: 1, shape: 'rect', clickAction: 'write' },
      },
      {
        type: 'button',
        label: '弹窗按钮',
        icon: 'btnPopup',
        hint: '点击打开弹窗页（在属性里选目标画面）',
        props: { text: '详情', writeValue: 1, shape: 'rect', clickAction: 'popup', targetScreenId: '' },
        style: { fill: '#0f766e', stroke: '#115e59', textColor: '#fff', fontSize: 14 },
      },
      {
        type: 'switch',
        label: '拨开关',
        icon: 'switch',
        hint: '拨动开关，开/关颜色与写值在属性中改',
      },
      {
        type: 'checkbox',
        label: '复选框',
        icon: 'checkbox',
        hint: '勾选翻转写值',
      },
      { type: 'input', label: '文本框', icon: 'input', hint: '弹窗输入文本写入' },
      { type: 'numberInput', label: '数值框', icon: 'numInput', hint: '弹窗输入数值写入' },
      {
        type: 'slider',
        label: '滑动条',
        icon: 'slider',
        hint: '方向与量程在属性中改',
        props: { value: 40, min: 0, max: 100, orient: 'h' },
      },
    ],
  },
  {
    key: 'output',
    label: '输出 / 指示',
    blurb: '标准指示件；颜色/形状/大小在属性里改',
    defaultOpen: true,
    items: [
      {
        type: 'led',
        label: '指示灯',
        icon: 'led',
        hint: '标准指示灯，选中后改颜色/形状/尺寸',
        props: { shape: 'circle' },
        style: { fill: '#22c55e', stroke: '#86efac', strokeWidth: 2 },
      },
      {
        type: 'statusBadge',
        label: '状态徽章',
        icon: 'badge',
        hint: '文案与颜色可在属性中修改',
        props: { text: '运行' },
      },
      {
        type: 'alarmBar',
        label: '报警条',
        icon: 'alarm',
        hint: '文案与颜色可在属性中修改',
        props: { text: '报警' },
      },
    ],
  },
  {
    key: 'pipe',
    label: '管道 / 连接',
    blurb: '管段与接头',
    defaultOpen: false,
    items: [
      {
        type: 'pipe',
        label: '水平管',
        icon: 'pipeH',
        props: { shape: 'h', points: [0, 4, 160, 4] },
        transform: { w: 160, h: 8 },
      },
      {
        type: 'pipe',
        label: '垂直管',
        icon: 'pipeV',
        props: { shape: 'v', points: [4, 0, 4, 160] },
        transform: { w: 8, h: 160 },
      },
      {
        type: 'pipe',
        label: '弯头',
        icon: 'pipeL',
        props: { shape: 'elbow', points: [0, 4, 48, 4, 48, 52] },
        transform: { w: 56, h: 56 },
        style: { stroke: '#60a5fa', strokeWidth: 6 },
      },
      {
        type: 'pipe',
        label: '三通',
        icon: 'pipeT',
        props: { shape: 'tee', points: [0, 28, 80, 28, 40, 28, 40, 0] },
        transform: { w: 80, h: 56 },
        style: { stroke: '#60a5fa', strokeWidth: 6 },
      },
    ],
  },
  {
    key: 'device',
    label: '设备符号',
    blurb: '工艺设备',
    defaultOpen: false,
    items: [
      { type: 'symbol', label: '泵', symbolId: 'pump', icon: 'pump' },
      { type: 'symbol', label: '阀', symbolId: 'valve', icon: 'valve' },
      { type: 'symbol', label: '蝶阀', symbolId: 'butterflyValve', icon: 'bvalve' },
      { type: 'symbol', label: '止回阀', symbolId: 'checkValve', icon: 'cvalve' },
      { type: 'symbol', label: '罐', symbolId: 'tank', icon: 'tank' },
      { type: 'symbol', label: '电机', symbolId: 'motor', icon: 'motor' },
      { type: 'symbol', label: '风机', symbolId: 'fan', icon: 'fan' },
      { type: 'symbol', label: '压缩机', symbolId: 'compressor', icon: 'comp' },
      { type: 'symbol', label: '换热器', symbolId: 'heatExchanger', icon: 'hx' },
      { type: 'symbol', label: '传感器', symbolId: 'sensor', icon: 'sensor' },
      { type: 'symbol', label: '传送带', symbolId: 'conveyor', icon: 'belt' },
      { type: 'symbol', label: '搅拌器', symbolId: 'agitator', icon: 'agit' },
    ],
  },
  {
    key: 'basic',
    label: '基础图形',
    blurb: '几何底板',
    defaultOpen: false,
    items: [
      { type: 'rect', label: '矩形', icon: 'rect' },
      {
        type: 'ellipse',
        label: '椭圆',
        icon: 'ellipse',
        transform: { w: 96, h: 56 },
      },
      {
        type: 'line',
        label: '直线',
        icon: 'line',
        props: { points: [0, 0, 120, 0] },
        transform: { w: 120, h: 4 },
        style: { stroke: '#94a3b8', strokeWidth: 2 },
      },
      {
        type: 'triangle',
        label: '三角',
        icon: 'tri',
        transform: { w: 64, h: 56 },
      },
      {
        type: 'circle',
        label: '圆',
        icon: 'circle',
        style: { fill: 'transparent', stroke: '#94a3b8', strokeWidth: 2 },
        transform: { w: 56, h: 56 },
      },
    ],
  },
]

export function defaultOpenKeys() {
  return PALETTE_CATEGORIES.filter((c) => c.defaultOpen).map((c) => c.key)
}

export function listPaletteItems() {
  return PALETTE_CATEGORIES.flatMap((c) => c.items)
}

export function countPaletteItems() {
  return listPaletteItems().length
}
