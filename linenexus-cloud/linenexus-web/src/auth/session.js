const TOKEN_KEY = 'linenexus.auth.token'
const USER_KEY = 'linenexus.auth.user'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getUser() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function setSession(token, user) {
  localStorage.setItem(TOKEN_KEY, token || '')
  localStorage.setItem(USER_KEY, JSON.stringify(user || null))
  window.dispatchEvent(new CustomEvent('linenexus:session'))
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  window.dispatchEvent(new CustomEvent('linenexus:session'))
}

export function isLoggedIn() {
  return Boolean(getToken())
}

export function rolesOf(user = getUser()) {
  return Array.isArray(user?.roles) ? user.roles : []
}

export function hasRole(role, user = getUser()) {
  return rolesOf(user).includes(role)
}

export function isAdmin(user = getUser()) {
  return hasRole('admin', user)
}

export function isOperator(user = getUser()) {
  return isAdmin(user) || hasRole('operator', user)
}

export function isViewerOnly(user = getUser()) {
  return hasRole('viewer', user) && !isOperator(user)
}

/** 菜单/路由权限键 */
export function canAccessMenu(key, user = getUser()) {
  if (!user) return false
  if (key === 'account-profile' || key === 'home') return true
  if (isAdmin(user)) return true
  const monitorKeys = ['home', 'hmi-screens', 'monitor-history', 'ops-alarms']
  if (isViewerOnly(user)) {
    return monitorKeys.includes(key)
  }
  // operator：组态/监控/告警；不含点表注册与系统管理
  return monitorKeys.includes(key)
}

export function canWrite(user = getUser()) {
  return isOperator(user)
}

export function canManageAssets(user = getUser()) {
  return isAdmin(user)
}
