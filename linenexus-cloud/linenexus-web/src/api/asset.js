import { clearSession, getToken, getUser } from '../auth/session'

/** 进行中的设备 read，切设备/重复读时取消，避免占满浏览器同源连接 */
let activeReadAbort = null

/**
 * @param {string} path
 * @param {RequestInit & { timeoutMs?: number }} [options]
 */
async function request(path, options = {}) {
  const { timeoutMs = 15000, signal: outerSignal, ...fetchOpts } = options
  const controller = new AbortController()
  const onOuterAbort = () => controller.abort()
  if (outerSignal) {
    if (outerSignal.aborted) controller.abort()
    else outerSignal.addEventListener('abort', onOuterAbort, { once: true })
  }
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  const user = getUser()
  const tenant = user?.tenantId || 'tenant-dev'
  const token = getToken()
  try {
    const response = await fetch(path, {
      ...fetchOpts,
      signal: controller.signal,
      headers: {
        'X-Tenant-Id': tenant,
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(fetchOpts.body ? { 'Content-Type': 'application/json' } : {}),
        ...fetchOpts.headers,
      },
    })
    if (response.status === 204) {
      return null
    }
    const text = await response.text()
    let data = null
    if (text) {
      try {
        data = JSON.parse(text)
      } catch {
        data = text
      }
    }
    if (!response.ok) {
      if (response.status === 401) {
        clearSession()
        if (!window.location.pathname.startsWith('/login')) {
          window.location.assign('/login')
        }
      }
      const msg =
        (data && (data.message || data.error)) ||
        (typeof data === 'string' ? data : `请求失败（${response.status}）`)
      throw new Error(msg)
    }
    return data
  } catch (e) {
    if (e?.name === 'AbortError') {
      throw new Error(`请求超时（${Math.round(timeoutMs / 1000)}s）`)
    }
    throw e
  } finally {
    clearTimeout(timer)
    if (outerSignal) outerSignal.removeEventListener('abort', onOuterAbort)
  }
}

export async function listEdgeGateways() {
  return request('/api/assets/edge-gateways')
}

export async function getEdgeGateway(id) {
  return request(`/api/assets/edge-gateways/${encodeURIComponent(id)}`)
}

/** 登记/创建边端网关根节点（也可由 edge 心跳注册） */
export async function registerEdgeGateway(body) {
  return request('/api/assets/edge-gateways/register', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function deleteEdgeGateway(id) {
  return request(`/api/assets/edge-gateways/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  })
}

export async function listDevices(gatewayId) {
  const data = await request(`/api/assets/edge-gateways/${encodeURIComponent(gatewayId)}/devices`)
  return data?.items || []
}

export async function createDevice(gatewayId, body) {
  return request(`/api/assets/edge-gateways/${encodeURIComponent(gatewayId)}/devices`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function getDevice(gatewayId, deviceId) {
  return request(
    `/api/assets/edge-gateways/${encodeURIComponent(gatewayId)}/devices/${encodeURIComponent(deviceId)}`,
  )
}

export async function deleteDevice(gatewayId, deviceId) {
  return request(
    `/api/assets/edge-gateways/${encodeURIComponent(gatewayId)}/devices/${encodeURIComponent(deviceId)}`,
    { method: 'DELETE' },
  )
}

export async function listDevicePoints(gatewayId, deviceId) {
  const data = await request(
    `/api/assets/edge-gateways/${encodeURIComponent(gatewayId)}/devices/${encodeURIComponent(deviceId)}/points`,
  )
  return data?.items || []
}

export async function createPoint(gatewayId, deviceId, body) {
  return request(
    `/api/assets/edge-gateways/${encodeURIComponent(gatewayId)}/devices/${encodeURIComponent(deviceId)}/points`,
    {
      method: 'POST',
      body: JSON.stringify(body),
    },
  )
}

export async function updatePoint(gatewayId, pointId, body) {
  return request(
    `/api/assets/edge-gateways/${encodeURIComponent(gatewayId)}/points/${encodeURIComponent(pointId)}`,
    {
      method: 'PUT',
      body: JSON.stringify(body),
    },
  )
}

export async function deletePoint(gatewayId, pointId) {
  return request(
    `/api/assets/edge-gateways/${encodeURIComponent(gatewayId)}/points/${encodeURIComponent(pointId)}`,
    { method: 'DELETE' },
  )
}

/** 南向读值：短超时 + 取消上一次，避免离线设备堵死页面 */
export async function readDevice(gatewayId, deviceId) {
  if (activeReadAbort) activeReadAbort.abort()
  activeReadAbort = new AbortController()
  const signal = activeReadAbort.signal
  try {
    const data = await request(
      `/api/assets/edge-gateways/${encodeURIComponent(gatewayId)}/devices/${encodeURIComponent(deviceId)}/read`,
      { method: 'POST', timeoutMs: 5000, signal },
    )
    return data?.items || []
  } finally {
    if (activeReadAbort?.signal === signal) activeReadAbort = null
  }
}

export async function writePoint(gatewayId, deviceId, pointId, value) {
  return request(
    `/api/assets/edge-gateways/${encodeURIComponent(gatewayId)}/devices/${encodeURIComponent(deviceId)}/write`,
    {
      method: 'POST',
      body: JSON.stringify({ deviceId, pointId, value }),
      timeoutMs: 8000,
    },
  )
}
