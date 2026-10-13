import { clearSession, getToken, getUser } from '../auth/session'

export async function http(path, options = {}) {
  const user = getUser()
  const tenant = user?.tenantId || 'tenant-dev'
  const token = getToken()
  const headers = {
    'X-Tenant-Id': tenant,
    ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    ...options.headers,
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(path, { ...options, headers })
  if (response.status === 204) return null

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
    if (
      response.status === 401 &&
      !String(path).includes('/api/auth/login') &&
      !window.location.pathname.startsWith('/platform')
    ) {
      clearSession()
      if (!window.location.pathname.startsWith('/login')) {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.assign(`/login?redirect=${redirect}`)
      }
    }
    let msg =
      (data && (data.message || data.detail || data.error || data.reason || data.title)) ||
      (typeof data === 'string' ? data : `请求失败（${response.status}）`)
    if (msg === 'Bad Request' && data && (data.message || data.detail)) {
      msg = data.message || data.detail
    }
    if (response.status === 404 && String(path).includes('/api/execution')) {
      msg =
        '执行服务不可用（404）。请确认已启动 linenexus-execution(:18082)，并重启网关以加载 /api/execution 路由'
    } else if (response.status === 503 && String(path).includes('/api/execution')) {
      msg = '执行服务未在 Nacos 注册（503）。请启动 linenexus-execution 后重试'
    } else if (response.status === 503 && String(path).includes('/api/monitor')) {
      msg =
        '监视服务不可用（503）。请启动 linenexus-monitor(:18082)，并确认 TDengine（默认 192.168.3.45:6041）已运行'
    }
    const err = new Error(msg)
    err.status = response.status
    err.data = data
    throw err
  }
  return data
}
