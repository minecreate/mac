import { http } from './http'
import { setSession } from '../auth/session'

export function login(body) {
  return http('/api/auth/login', { method: 'POST', body: JSON.stringify(body) })
}

export function fetchMe() {
  return http('/api/auth/me')
}

export function updateProfile(body) {
  return http('/api/auth/me', { method: 'PUT', body: JSON.stringify(body) })
}

export function changePassword(body) {
  return http('/api/auth/password', { method: 'POST', body: JSON.stringify(body) })
}

export function fetchRoleHints() {
  return http('/api/auth/roles')
}

export async function loginAndStore({ username, password, tenantId }) {
  const res = await login({ username, password, tenantId })
  const token = res.token
  const user = res.user
  setSession(token, user)
  return res
}
