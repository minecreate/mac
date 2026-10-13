import { platformHttp } from './platformHttp'
import { setPlatformSession } from '../auth/platformSession'

export function platformLogin(body) {
  return platformHttp('/api/auth/platform/login', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function platformLoginAndStore({ username, password }) {
  const res = await platformLogin({ username, password })
  setPlatformSession(res.token, res.user)
  return res
}

export function listTenants() {
  return platformHttp('/api/platform/tenants')
}

export function createTenant(body) {
  return platformHttp('/api/platform/tenants', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function updateTenant(id, body) {
  return platformHttp(`/api/platform/tenants/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}
