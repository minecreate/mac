import { http } from './http'

async function request(path, options = {}) {
  return http(path, options)
}

/** HMI 写值前抢工位锁；execution 未启动时由调用方按 404 软放行 */
export function tryLock(body) {
  return request('/api/execution/locks/try', { method: 'POST', body: JSON.stringify(body) })
}

export function releaseLock(body) {
  return request('/api/execution/locks/release', { method: 'POST', body: JSON.stringify(body) })
}
