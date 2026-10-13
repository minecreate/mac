/**
 * HMI document schema (schemaVersion 1).
 * Kept as JSDoc typedefs so the Vue project stays JS without a TS build step.
 */

/**
 * @typedef {'text'|'color'|'visible'|'clickWrite'} BindingKind
 */

/**
 * @typedef {Object} HmiBinding
 * @property {string} gatewayId
 * @property {string} deviceId
 * @property {string} pointId
 * @property {BindingKind} kind
 * @property {Record<string, unknown>} [map]
 */

/**
 * @typedef {Object} HmiTransform
 * @property {number} x
 * @property {number} y
 * @property {number} w
 * @property {number} h
 * @property {number} [rotation]
 */

/**
 * @typedef {Object} HmiLayer
 * @property {string} id
 * @property {string} name
 * @property {boolean} visible
 * @property {boolean} locked
 */

/**
 * @typedef {'text'|'label'|'time'|'date'|'image'|'rect'|'circle'|'ellipse'|'line'|'triangle'|'symbol'|'button'|'switch'|'checkbox'|'led'|'statusBadge'|'input'|'numberInput'|'slider'|'progress'|'gauge'|'levelBar'|'alarmBar'|'pipe'} HmiElementType
 */

/**
 * @typedef {Object} HmiElement
 * @property {string} id
 * @property {HmiElementType} type
 * @property {string} layerId
 * @property {HmiTransform} transform
 * @property {Record<string, unknown>} [style]
 * @property {Record<string, unknown>} [props]
 * @property {HmiBinding} [binding]
 */

/**
 * @typedef {'main'|'popup'} HmiPageRole
 * @typedef {'center'|'topLeft'|'topRight'|'bottomLeft'|'bottomRight'} HmiPopupPlacement
 */

/**
 * @typedef {Object} HmiDocument
 * @property {number} schemaVersion
 * @property {{ width: number, height: number, background?: string }} canvas
 * @property {HmiLayer[]} layers
 * @property {HmiElement[]} elements
 * @property {{
 *   grid?: number,
 *   snap?: boolean,
 *   role?: HmiPageRole,
 *   popupPlacement?: HmiPopupPlacement,
 * }} [meta]
 */

export const DEFAULT_LAYER_ID = 'layer-default'
export const POPUP_DEFAULT_SIZE = { width: 640, height: 480 }

/**
 * @param {{ role?: HmiPageRole, width?: number, height?: number, popupPlacement?: HmiPopupPlacement }} [opts]
 * @returns {HmiDocument}
 */
export function createEmptyDocument(opts = {}) {
  const role = opts.role === 'popup' ? 'popup' : 'main'
  const isPopup = role === 'popup'
  return {
    schemaVersion: 1,
    canvas: {
      width: Number(opts.width) || (isPopup ? POPUP_DEFAULT_SIZE.width : 1920),
      height: Number(opts.height) || (isPopup ? POPUP_DEFAULT_SIZE.height : 1080),
      background: '#1a1d24',
    },
    layers: [{ id: DEFAULT_LAYER_ID, name: '默认', visible: true, locked: false }],
    elements: [],
    meta: {
      grid: 10,
      snap: false,
      role,
      popupPlacement: opts.popupPlacement || 'center',
    },
  }
}

/**
 * @param {unknown} raw
 * @returns {HmiDocument}
 */
export function parseDocument(raw) {
  if (raw == null) return createEmptyDocument()
  let obj = raw
  if (typeof raw === 'string') {
    try {
      obj = JSON.parse(raw)
    } catch {
      return createEmptyDocument()
    }
  }
  if (!obj || typeof obj !== 'object') return createEmptyDocument()
  const base = createEmptyDocument()
  return {
    schemaVersion: Number(obj.schemaVersion) || 1,
    canvas: {
      width: Number(obj.canvas?.width) || base.canvas.width,
      height: Number(obj.canvas?.height) || base.canvas.height,
      background: obj.canvas?.background || base.canvas.background,
    },
    layers:
      Array.isArray(obj.layers) && obj.layers.length
        ? obj.layers.map((l) => ({
            id: String(l.id || DEFAULT_LAYER_ID),
            name: String(l.name || '图层'),
            visible: l.visible !== false,
            locked: !!l.locked,
          }))
        : base.layers,
    elements: Array.isArray(obj.elements)
      ? obj.elements.map(normalizeElement).filter(Boolean)
      : [],
    meta: {
      grid: Number(obj.meta?.grid) || 10,
      snap: !!obj.meta?.snap,
      role: obj.meta?.role === 'popup' ? 'popup' : 'main',
      popupPlacement: normalizePopupPlacement(obj.meta?.popupPlacement),
    },
  }
}

/** @param {unknown} value */
export function normalizePopupPlacement(value) {
  const allowed = ['center', 'topLeft', 'topRight', 'bottomLeft', 'bottomRight']
  return allowed.includes(String(value)) ? String(value) : 'center'
}

/**
 * 运行态弹窗定位样式（相对视口）。
 * @param {HmiPopupPlacement} [placement]
 * @param {{ width: number, height: number }} size
 */
export function popupPlacementStyle(placement, size) {
  const w = Math.max(200, Number(size?.width) || POPUP_DEFAULT_SIZE.width)
  const h = Math.max(160, Number(size?.height) || POPUP_DEFAULT_SIZE.height)
  const pad = 24
  const base = {
    position: 'fixed',
    width: `${w + 48}px`,
    maxWidth: 'calc(100vw - 32px)',
    paddingBottom: 0,
    margin: 0,
  }
  switch (placement) {
    case 'topLeft':
      return { ...base, top: `${pad}px`, left: `${pad}px`, transform: 'none' }
    case 'topRight':
      return { ...base, top: `${pad}px`, right: `${pad}px`, left: 'auto', transform: 'none' }
    case 'bottomLeft':
      return { ...base, bottom: `${pad}px`, left: `${pad}px`, top: 'auto', transform: 'none' }
    case 'bottomRight':
      return {
        ...base,
        bottom: `${pad}px`,
        right: `${pad}px`,
        top: 'auto',
        left: 'auto',
        transform: 'none',
      }
    case 'center':
    default:
      return {
        ...base,
        top: '50%',
        left: '50%',
        transform: 'translate(-50%, -50%)',
        // 高度过大时仍可滚动
        maxHeight: `min(${h + 120}px, calc(100vh - 32px))`,
      }
  }
}

/**
 * @param {any} el
 * @returns {HmiElement|null}
 */
function normalizeElement(el) {
  if (!el || !el.id || !el.type) return null
  return {
    id: String(el.id),
    type: el.type,
    layerId: String(el.layerId || DEFAULT_LAYER_ID),
    transform: {
      x: Number(el.transform?.x) || 0,
      y: Number(el.transform?.y) || 0,
      w: Math.max(8, Number(el.transform?.w) || 80),
      h: Math.max(8, Number(el.transform?.h) || 40),
      rotation: Number(el.transform?.rotation) || 0,
    },
    style: el.style && typeof el.style === 'object' ? { ...el.style } : {},
    props: el.props && typeof el.props === 'object' ? { ...el.props } : {},
    binding: el.binding
      ? {
          gatewayId: String(el.binding.gatewayId || ''),
          deviceId: String(el.binding.deviceId || ''),
          pointId: String(el.binding.pointId || ''),
          kind: el.binding.kind || 'text',
          map: el.binding.map && typeof el.binding.map === 'object' ? { ...el.binding.map } : {},
        }
      : undefined,
  }
}

/**
 * @param {HmiDocument} doc
 * @returns {string}
 */
export function serializeDocument(doc) {
  return JSON.stringify(doc)
}

/**
 * Collect unique point keys used by bindings on this screen.
 * @param {HmiDocument} doc
 * @returns {{ gatewayId: string, deviceId: string, pointId: string, key: string }[]}
 */
export function collectPointKeys(doc) {
  const map = new Map()
  for (const el of doc.elements || []) {
    const b = el.binding
    if (!b?.gatewayId || !b?.deviceId || !b?.pointId) continue
    const key = `${b.gatewayId}|${b.deviceId}|${b.pointId}`
    if (!map.has(key)) {
      map.set(key, {
        gatewayId: b.gatewayId,
        deviceId: b.deviceId,
        pointId: b.pointId,
        key,
      })
    }
  }
  return [...map.values()]
}

/**
 * @param {string} type
 * @param {Partial<HmiTransform>} [at]
 * @returns {HmiElement}
 */
export function createElement(type, at = {}) {
  const id = `el-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`
  const defaults = elementDefaults(type)
  return {
    id,
    type,
    layerId: DEFAULT_LAYER_ID,
    transform: {
      x: at.x ?? 80,
      y: at.y ?? 80,
      w: at.w ?? defaults.w,
      h: at.h ?? defaults.h,
      rotation: 0,
    },
    style: { ...defaults.style },
    props: { ...defaults.props },
  }
}

function elementDefaults(type) {
  switch (type) {
    case 'text':
      return {
        w: 120,
        h: 32,
        style: { fill: '#e8eaed', fontSize: 16 },
        props: { text: '0.0', align: 'left' },
      }
    case 'label':
      return {
        w: 100,
        h: 28,
        style: { fill: '#9ca3af', fontSize: 14 },
        props: { text: '标签', align: 'left' },
      }
    case 'time':
      return {
        w: 120,
        h: 32,
        style: { fill: '#e8eaed', fontSize: 18 },
        /** format: HH:mm:ss | HH:mm */
        props: { format: 'HH:mm:ss', align: 'center' },
      }
    case 'date':
      return {
        w: 140,
        h: 32,
        style: { fill: '#e8eaed', fontSize: 16 },
        /** format: YYYY-MM-DD | YYYY/MM/DD | MM-DD */
        props: { format: 'YYYY-MM-DD', align: 'center' },
      }
    case 'image':
      return {
        w: 160,
        h: 100,
        style: { opacity: 1, stroke: '', strokeWidth: 0 },
        /** src: http(s)/dataURL；本地上传会写入 dataURL */
        props: { src: '' },
      }
    case 'rect':
      return {
        w: 100,
        h: 60,
        style: { fill: '#3a3f4b', stroke: '#6b7280', strokeWidth: 1 },
        props: {},
      }
    case 'ellipse':
      return {
        w: 96,
        h: 56,
        style: { fill: '#3a3f4b', stroke: '#6b7280', strokeWidth: 1 },
        props: {},
      }
    case 'line':
      return {
        w: 120,
        h: 4,
        style: { stroke: '#94a3b8', strokeWidth: 2 },
        props: { points: [0, 2, 120, 2] },
      }
    case 'triangle':
      return {
        w: 64,
        h: 56,
        style: { fill: '#3a3f4b', stroke: '#6b7280', strokeWidth: 1 },
        props: {},
      }
    case 'circle':
      return {
        w: 48,
        h: 48,
        style: { fill: '#52c41a', stroke: '#389e0d', strokeWidth: 1 },
        props: {},
      }
    case 'symbol':
      return {
        w: 64,
        h: 64,
        style: { fill: '#4b5563', stroke: '#9ca3af', strokeWidth: 2 },
        props: { symbolId: 'pump' },
      }
    case 'button':
      return {
        w: 100,
        h: 36,
        style: { fill: '#1677ff', stroke: '#0958d9', strokeWidth: 1, textColor: '#fff', fontSize: 14 },
        /** shape: rect | round | rounded；clickAction: write | navigate | popup */
        props: {
          text: '按钮',
          writeValue: 1,
          shape: 'rect',
          clickAction: 'write',
          targetScreenId: '',
        },
      }
    case 'switch':
      return {
        w: 72,
        h: 36,
        style: { fillOn: '#52c41a', fillOff: '#6b7280' },
        props: { onValue: 1, offValue: 0 },
      }
    case 'checkbox':
      return {
        w: 28,
        h: 28,
        style: { fillOn: '#1677ff', fillOff: '#1f2937', stroke: '#94a3b8', checkColor: '#fff' },
        props: { onValue: 1, offValue: 0 },
      }
    case 'led':
      return {
        w: 36,
        h: 36,
        style: { fill: '#22c55e', stroke: '#86efac', strokeWidth: 2 },
        /** shape: circle | square | rounded */
        props: { shape: 'circle' },
      }
    case 'statusBadge':
      return {
        w: 96,
        h: 28,
        style: { fill: '#14532d', stroke: '#22c55e', strokeWidth: 1, textColor: '#bbf7d0', fontSize: 13 },
        props: { text: '运行' },
      }
    case 'input':
      return {
        w: 140,
        h: 32,
        style: {
          fill: '#111827',
          stroke: '#4b5563',
          strokeWidth: 1,
          textColor: '#e5e7eb',
          fontSize: 14,
        },
        props: { text: '', placeholder: '输入…', align: 'left' },
      }
    case 'numberInput':
      return {
        w: 100,
        h: 32,
        style: {
          fill: '#111827',
          stroke: '#4b5563',
          strokeWidth: 1,
          textColor: '#e5e7eb',
          fontSize: 14,
        },
        props: { text: '0', placeholder: '0.0', align: 'right', inputMode: 'number' },
      }
    case 'slider':
      return {
        w: 160,
        h: 28,
        style: { track: '#374151', fill: '#38bdf8', thumb: '#e5e7eb' },
        props: { value: 40, min: 0, max: 100, orient: 'h' },
      }
    case 'progress':
      return {
        w: 160,
        h: 20,
        style: { track: '#1f2937', fill: '#22c55e', stroke: '#4b5563', strokeWidth: 1 },
        props: { value: 60, max: 100 },
      }
    case 'gauge':
      return {
        w: 100,
        h: 72,
        style: { track: '#374151', fill: '#38bdf8', textColor: '#e5e7eb', fontSize: 12 },
        props: { value: 45, min: 0, max: 100 },
      }
    case 'levelBar':
      return {
        w: 40,
        h: 120,
        style: { track: '#1f2937', fill: '#38bdf8', stroke: '#64748b', strokeWidth: 2 },
        props: { value: 55, max: 100 },
      }
    case 'alarmBar':
      return {
        w: 140,
        h: 28,
        style: { fill: '#7f1d1d', stroke: '#ef4444', strokeWidth: 1, textColor: '#fecaca', fontSize: 13 },
        props: { text: '报警' },
      }
    case 'pipe':
      return {
        w: 160,
        h: 8,
        style: { stroke: '#60a5fa', strokeWidth: 6 },
        props: { shape: 'h', points: [0, 4, 160, 4] },
      }
    default:
      return { w: 80, h: 40, style: {}, props: {} }
  }
}
