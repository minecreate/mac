const TOKEN_KEY = 'linenexus.platform.auth.token'
const USER_KEY = 'linenexus.platform.auth.user'

export function getPlatformToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getPlatformUser() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function setPlatformSession(token, user) {
  localStorage.setItem(TOKEN_KEY, token || '')
  localStorage.setItem(USER_KEY, JSON.stringify(user || null))
}

export function clearPlatformSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function isPlatformLoggedIn() {
  return Boolean(getPlatformToken())
}

export function isPlatformAdmin(user = getPlatformUser()) {
  return Array.isArray(user?.roles) && user.roles.includes('platform_admin')
}
