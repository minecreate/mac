import { http } from './http'

/**
 * @param {{
 *   category?: string,
 *   action?: string,
 *   result?: string,
 *   actor?: string,
 *   keyword?: string,
 *   from?: string,
 *   to?: string,
 *   page?: number,
 *   size?: number,
 * }} [q]
 */
export function listAuditLogs(q = {}) {
  const params = new URLSearchParams()
  Object.entries(q).forEach(([k, v]) => {
    if (v === undefined || v === null || v === '') return
    params.set(k, String(v))
  })
  const qs = params.toString()
  return http(`/api/assets/audit-logs${qs ? `?${qs}` : ''}`)
}

export function getAuditLog(id) {
  return http(`/api/assets/audit-logs/${encodeURIComponent(id)}`)
}
