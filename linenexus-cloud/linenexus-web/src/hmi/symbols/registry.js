/**
 * Symbol registry — maps symbolId to a simple Konva-friendly descriptor.
 * 图元库左侧分类入口见 palette.js。
 */

/**
 * @typedef {Object} SymbolDef
 * @property {string} id
 * @property {string} label
 * @property {string} kind
 * @property {string} [fill]
 * @property {string} [stroke]
 */

/** @type {Record<string, SymbolDef>} */
const REGISTRY = {
  pump: { id: 'pump', label: '泵', kind: 'pump', fill: '#4b5563', stroke: '#93c5fd' },
  valve: { id: 'valve', label: '阀', kind: 'valve', fill: '#4b5563', stroke: '#fbbf24' },
  butterflyValve: {
    id: 'butterflyValve',
    label: '蝶阀',
    kind: 'butterflyValve',
    fill: '#4b5563',
    stroke: '#fcd34d',
  },
  checkValve: {
    id: 'checkValve',
    label: '止回阀',
    kind: 'checkValve',
    fill: '#4b5563',
    stroke: '#fbbf24',
  },
  lamp: { id: 'lamp', label: '指示灯', kind: 'lamp', fill: '#6b7280', stroke: '#d1d5db' },
  tank: { id: 'tank', label: '罐', kind: 'tank', fill: '#374151', stroke: '#9ca3af' },
  motor: { id: 'motor', label: '电机', kind: 'motor', fill: '#4b5563', stroke: '#a78bfa' },
  fan: { id: 'fan', label: '风机', kind: 'fan', fill: '#4b5563', stroke: '#67e8f9' },
  compressor: {
    id: 'compressor',
    label: '压缩机',
    kind: 'compressor',
    fill: '#4b5563',
    stroke: '#93c5fd',
  },
  heatExchanger: {
    id: 'heatExchanger',
    label: '换热器',
    kind: 'heatExchanger',
    fill: '#374151',
    stroke: '#fb923c',
  },
  sensor: { id: 'sensor', label: '传感器', kind: 'sensor', fill: '#4b5563', stroke: '#34d399' },
  conveyor: {
    id: 'conveyor',
    label: '传送带',
    kind: 'conveyor',
    fill: '#374151',
    stroke: '#94a3b8',
  },
  agitator: {
    id: 'agitator',
    label: '搅拌器',
    kind: 'agitator',
    fill: '#4b5563',
    stroke: '#c4b5fd',
  },
}

/** @returns {SymbolDef[]} */
export function listSymbols() {
  return Object.values(REGISTRY)
}

/** @param {string} id */
export function getSymbol(id) {
  return REGISTRY[id] || REGISTRY.pump
}

/** @param {SymbolDef} def */
export function registerSymbol(def) {
  if (!def?.id) return
  REGISTRY[def.id] = def
}
