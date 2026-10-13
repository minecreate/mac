import {
  clearPlatformSession,
  getPlatformToken,
  getPlatformUser,
} from '../auth/platformSession'

export async function platformHttp(path, options = {}) {
  const user = getPlatformUser()
  const token = getPlatformToken()
  const headers = {
    'X-Tenant-Id': user?.tenantId || '__platform__',
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
    if (response.status === 401 && !String(path).includes('/api/auth/platform/login')) {
      clearPlatformSession()
      if (!window.location.pathname.startsWith('/platform/login')) {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.assign(`/platform/login?redirect=${redirect}`)
      }
    }
    const msg =
      (data && (data.message || data.detail || data.error || data.reason || data.title)) ||
      (typeof data === 'string' ? data : `请求失败（${response.status}）`)
    const err = new Error(msg)
    err.status = response.status
    err.data = data
    throw err
  }
  return data
}
