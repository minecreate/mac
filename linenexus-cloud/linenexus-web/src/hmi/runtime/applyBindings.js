/**
 * BindingAdapter — maps live point values onto element visual props,
 * and resolves click-write payloads. New bind kinds plug in here.
 */

/**
 * @typedef {Object} PointReading
 * @property {string} pointId
 * @property {unknown} value
 * @property {string} [status]
 * @property {string} [error]
 */

/**
 * Build a lookup: `${gatewayId}|${deviceId}|${pointId}` → reading
 * Readings from readDevice only have pointId; caller must scope by device.
 *
 * @param {string} gatewayId
 * @param {string} deviceId
 * @param {PointReading[]} items
 */
export function indexReadings(gatewayId, deviceId, items) {
  /** @type {Map<string, PointReading>} */
  const map = new Map()
  for (const item of items || []) {
    if (!item?.pointId) continue
    map.set(`${gatewayId}|${deviceId}|${item.pointId}`, item)
  }
  return map
}

/**
 * @param {import('../model/types').HmiElement} element
 * @param {Map<string, PointReading>} readingMap
 * @returns {import('../model/types').HmiElement}
 */
export function applyBindingToElement(element, readingMap) {
  const b = element.binding
  if (!b?.gatewayId || !b?.deviceId || !b?.pointId) return element
  const key = `${b.gatewayId}|${b.deviceId}|${b.pointId}`
  const reading = readingMap.get(key)
  if (!reading) return element

  const value = reading.value
  const kind = b.kind || 'text'
  const style = { ...(element.style || {}) }
  const props = { ...(element.props || {}) }

  switch (kind) {
    case 'text': {
      props.text = formatValue(value)
      if (
        element.type === 'progress' ||
        element.type === 'gauge' ||
        element.type === 'levelBar' ||
        element.type === 'slider'
      ) {
        const n = Number(value)
        if (!Number.isNaN(n)) props.value = n
      }
      if (element.type === 'alarmBar' || element.type === 'statusBadge') {
        props.text = formatValue(value)
      }
      break
    }
    case 'color': {
      const onColor = b.map?.onColor || style.fillOn || '#52c41a'
      const offColor = b.map?.offColor || style.fillOff || '#6b7280'
      const onVal = b.map?.onValue ?? props.onValue ?? 1
      style.fill = isTruthy(value, onVal) ? onColor : offColor
      if (element.type === 'led') {
        style.stroke = isTruthy(value, onVal) ? onColor : offColor
      }
      break
    }
    case 'visible': {
      const onVal = b.map?.onValue ?? 1
      props.runtimeVisible = isTruthy(value, onVal)
      break
    }
    case 'clickWrite': {
      if (element.type === 'switch' || element.type === 'checkbox') {
        const onVal = props.onValue ?? 1
        props.runtimeOn = isTruthy(value, onVal)
      } else if (element.type === 'button') {
        props.runtimeLabel = props.text
      } else if (element.type === 'input' || element.type === 'numberInput') {
        props.text = formatValue(value)
      } else if (element.type === 'slider') {
        const n = Number(value)
        if (!Number.isNaN(n)) props.value = n
      }
      if (element.type === 'text') props.text = formatValue(value)
      break
    }
    default:
      props.text = formatValue(value)
  }

  if ((element.type === 'input' || element.type === 'numberInput') && kind === 'text') {
    props.text = formatValue(value)
  }

  if ((element.type === 'circle' || element.type === 'symbol' || element.type === 'led') && kind === 'text') {
    const n = Number(value)
    if (!Number.isNaN(n)) {
      style.fill = n ? style.fill || '#52c41a' : '#6b7280'
      if (element.type === 'led' && !n) style.stroke = '#9ca3af'
    }
  }

  return { ...element, style, props }
}

/**
 * Resolve value to write when user clicks a writable element.
 * @param {import('../model/types').HmiElement} element
 * @param {unknown} [currentValue]
 */
/**
 * @returns {unknown|null} null = 不可写；{ prompt: true, mode, seed } = 需弹窗输入；其它 = 直接写入值
 */
export function resolveWriteValue(element, currentValue) {
  const b = element.binding
  const isInput =
    element.type === 'input' || element.type === 'numberInput' || element.type === 'slider'
  if (
    !b ||
    (b.kind !== 'clickWrite' &&
      element.type !== 'button' &&
      element.type !== 'switch' &&
      element.type !== 'checkbox' &&
      !isInput)
  ) {
    return null
  }
  if (element.type === 'switch' || element.type === 'checkbox') {
    const onVal = element.props?.onValue ?? 1
    const offVal = element.props?.offValue ?? 0
    const isOn = isTruthy(currentValue, onVal)
    return isOn ? offVal : onVal
  }
  if (isInput) {
    const seed =
      element.type === 'slider'
        ? currentValue != null
          ? currentValue
          : element.props?.value ?? 0
        : currentValue != null
          ? currentValue
          : element.props?.text ?? ''
    return {
      prompt: true,
      mode: element.type === 'input' ? 'text' : 'number',
      seed,
    }
  }
  if (element.props?.writeValue !== undefined && element.props?.writeValue !== '') {
    return coerceWrite(element.props.writeValue)
  }
  if (b.map?.writeValue !== undefined) {
    return coerceWrite(b.map.writeValue)
  }
  return 1
}

function formatValue(value) {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'number') {
    return Number.isInteger(value) ? String(value) : value.toFixed(2)
  }
  return String(value)
}

function isTruthy(value, onVal) {
  if (typeof value === 'boolean') return value
  if (value === onVal) return true
  const n = Number(value)
  const onN = Number(onVal)
  if (!Number.isNaN(n) && !Number.isNaN(onN)) return n === onN
  return Boolean(value) && value !== '0' && value !== 'false'
}

function coerceWrite(v) {
  if (typeof v === 'number' || typeof v === 'boolean') return v
  const s = String(v).trim()
  if (s === 'true') return true
  if (s === 'false') return false
  const n = Number(s)
  if (!Number.isNaN(n) && s !== '') return n
  return s
}
