import { http } from './http'

/** @param {{ gatewayId: string, deviceId: string, pointIds: string|string[], from: string, to: string, interval?: number }} q */
export async function queryHistory(q) {
  const pointIds = Array.isArray(q.pointIds) ? q.pointIds.join(',') : q.pointIds
  const params = new URLSearchParams({
    gatewayId: q.gatewayId,
    deviceId: q.deviceId,
    pointIds,
    from: q.from,
    to: q.to,
  })
  if (q.interval != null && q.interval > 0) {
    params.set('interval', String(q.interval))
  }
  return http(`/api/monitor/history?${params}`)
}

export async function listAlarmRules() {
  return http('/api/monitor/alarms/rules')
}

export async function createAlarmRule(body) {
  return http('/api/monitor/alarms/rules', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function updateAlarmRule(id, body) {
  return http(`/api/monitor/alarms/rules/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export async function deleteAlarmRule(id) {
  return http(`/api/monitor/alarms/rules/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  })
}

/** @param {string} [status] active | acked */
export async function listAlarmEvents(status) {
  const q = status ? `?status=${encodeURIComponent(status)}` : ''
  return http(`/api/monitor/alarms/events${q}`)
}

export async function ackAlarmEvent(id) {
  return http(`/api/monitor/alarms/events/${encodeURIComponent(id)}/ack`, {
    method: 'POST',
  })
}

/** 调试：HTTP 注入遥测批次 */
export async function ingestTelemetry(batch) {
  return http('/api/monitor/ingest/telemetry', {
    method: 'POST',
    body: JSON.stringify(batch),
  })
}
