import { http } from './http'

async function request(path, options = {}) {
  return http(path, options)
}

function normalizeScreen(row) {
  if (!row) return null
  let document = row.documentJson
  if (typeof document === 'string') {
    try {
      document = JSON.parse(document)
    } catch {
      document = null
    }
  }
  return {
    id: row.id,
    tenantId: row.tenantId,
    projectId: row.projectId || null,
    name: row.name,
    version: row.version,
    status: row.status,
    document,
    documentJson: row.documentJson,
    updatedBy: row.updatedBy,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
  }
}

function normalizeProject(row) {
  if (!row) return null
  return {
    id: row.id,
    tenantId: row.tenantId,
    name: row.name,
    sortOrder: row.sortOrder ?? 0,
    homeScreenId: row.homeScreenId || null,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
  }
}

export async function listHmiProjects() {
  const rows = await request('/api/assets/hmi/projects')
  return (rows || []).map(normalizeProject)
}

export async function getHmiProject(id) {
  return normalizeProject(await request(`/api/assets/hmi/projects/${encodeURIComponent(id)}`))
}

export async function createHmiProject({ name, sortOrder }) {
  return normalizeProject(
    await request('/api/assets/hmi/projects', {
      method: 'POST',
      body: JSON.stringify({ name, sortOrder }),
    }),
  )
}

export async function updateHmiProject(id, { name, sortOrder, homeScreenId }) {
  return normalizeProject(
    await request(`/api/assets/hmi/projects/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify({ name, sortOrder, homeScreenId }),
    }),
  )
}

export async function setHmiProjectHomeScreen(projectId, screenId) {
  return normalizeProject(
    await request(`/api/assets/hmi/projects/${encodeURIComponent(projectId)}/home-screen`, {
      method: 'POST',
      body: JSON.stringify({ screenId }),
    }),
  )
}

export async function deleteHmiProject(id) {
  return request(`/api/assets/hmi/projects/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export async function listHmiScreens({ status, projectId } = {}) {
  const q = new URLSearchParams()
  if (status) q.set('status', status)
  if (projectId) q.set('projectId', projectId)
  const suffix = q.toString() ? `?${q}` : ''
  const rows = await request(`/api/assets/hmi/screens${suffix}`)
  return (rows || []).map(normalizeScreen)
}

export async function getHmiScreen(id) {
  return normalizeScreen(await request(`/api/assets/hmi/screens/${encodeURIComponent(id)}`))
}

export async function createHmiScreen({ name, documentJson, projectId }) {
  return normalizeScreen(
    await request('/api/assets/hmi/screens', {
      method: 'POST',
      body: JSON.stringify({
        name,
        projectId,
        documentJson: typeof documentJson === 'string' ? documentJson : JSON.stringify(documentJson),
        updatedBy: 'console',
      }),
    }),
  )
}

export async function updateHmiScreen(id, { name, documentJson, projectId }) {
  return normalizeScreen(
    await request(`/api/assets/hmi/screens/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify({
        name,
        projectId,
        documentJson: typeof documentJson === 'string' ? documentJson : JSON.stringify(documentJson),
        updatedBy: 'console',
      }),
    }),
  )
}

export async function publishHmiScreen(id) {
  return normalizeScreen(
    await request(`/api/assets/hmi/screens/${encodeURIComponent(id)}/publish`, {
      method: 'POST',
      body: JSON.stringify({ updatedBy: 'console' }),
    }),
  )
}

export async function deleteHmiScreen(id) {
  return request(`/api/assets/hmi/screens/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
