<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  AlertOutlined,
  ApiOutlined,
  ClusterOutlined,
  CopyOutlined,
  DesktopOutlined,
  EnvironmentOutlined,
  ExpandOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import { listEdgeGateways, listDevices } from '../api/asset'
import { listAlarmEvents } from '../api/monitor'
import { listHmiProjects, listHmiScreens } from '../api/hmi'
import { canManageAssets } from '../auth/session'
import ChinaDeviceMap from '../components/ChinaDeviceMap.vue'

const DEFAULT_PROJECT_ID = 'hmi-proj-default'
const REFRESH_MS = 30000

const router = useRouter()
const loading = ref(false)
const gateways = ref([])
const deviceCount = ref(0)
const activeAlarms = ref([])
const projects = ref([])
const screens = ref([])
let timer = null

const onlineGateways = computed(() =>
  gateways.value.filter((g) => String(g.status || '').toLowerCase() === 'online'),
)
const offlineGateways = computed(() =>
  gateways.value.filter((g) => String(g.status || '').toLowerCase() !== 'online'),
)

const assetHome = computed(() => (canManageAssets() ? '/edge-gateways' : ''))

const kpiItems = computed(() => [
  {
    key: 'alarms',
    label: '活动告警',
    value: activeAlarms.value.length,
    tone: activeAlarms.value.length > 0 ? 'danger' : 'ok',
    icon: AlertOutlined,
    to: '/ops/alarms',
  },
  {
    key: 'offline',
    label: '离线网关',
    value: offlineGateways.value.length,
    tone: offlineGateways.value.length > 0 ? 'warn' : 'ok',
    icon: ClusterOutlined,
    to: assetHome.value,
  },
  {
    key: 'online',
    label: '在线网关',
    value: onlineGateways.value.length,
    tone: 'info',
    icon: DesktopOutlined,
    to: assetHome.value,
  },
  {
    key: 'devices',
    label: '接入设备',
    value: deviceCount.value,
    tone: 'neutral',
    icon: ApiOutlined,
    to: assetHome.value,
  },
])

const boardRows = computed(() =>
  projects.value.map((p) => ({
    ...p,
    pageCount: screens.value.filter((s) => (s.projectId || DEFAULT_PROJECT_ID) === p.id).length,
    homeScreenId: p.homeScreenId || null,
  })),
)

async function loadSafe(label, fn) {
  try {
    return await fn()
  } catch (e) {
    console.warn(`[home] ${label} failed`, e)
    return null
  }
}

async function refresh() {
  loading.value = true
  try {
    const [gwRows, alarmRows, projRows, screenRows] = await Promise.all([
      loadSafe('gateways', listEdgeGateways),
      loadSafe('alarms', () => listAlarmEvents('active')),
      loadSafe('projects', listHmiProjects),
      loadSafe('screens', listHmiScreens),
    ])
    gateways.value = Array.isArray(gwRows) ? gwRows : []
    activeAlarms.value = Array.isArray(alarmRows) ? alarmRows : []
    projects.value = Array.isArray(projRows) ? projRows : []
    screens.value = Array.isArray(screenRows) ? screenRows : []

    if (!gateways.value.length && gwRows == null) {
      message.warning('网关列表加载失败，地图暂无数据')
    }

    const counts = await Promise.all(
      gateways.value.map(async (g) => {
        try {
          const items = await listDevices(g.id)
          return Array.isArray(items) ? items.length : 0
        } catch {
          return 0
        }
      }),
    )
    deviceCount.value = counts.reduce((a, b) => a + b, 0)
  } catch (e) {
    message.error(e.message || '加载总览失败')
  } finally {
    loading.value = false
  }
}

function goKpi(item) {
  if (item?.to) router.push(item.to)
}

function goGateway(id) {
  if (!canManageAssets()) return
  router.push(`/edge-gateways/${encodeURIComponent(id)}`)
}

function runtimePath(board) {
  if (!board?.homeScreenId) return ''
  return `/hmi/screens/${board.homeScreenId}/runtime`
}

function openBoard(board) {
  const path = runtimePath(board)
  if (!path) {
    message.warning('该工程尚未生成主画面')
    return
  }
  router.push({ path, query: { from: 'home' } })
}

function openBoardFullscreen(board) {
  const path = runtimePath(board)
  if (!path) {
    message.warning('该工程尚未生成主画面')
    return
  }
  router.push({ path, query: { from: 'home', fullscreen: '1' } })
}

async function copyShareLink(board) {
  const path = runtimePath(board)
  if (!path) {
    message.warning('该工程尚未生成主画面')
    return
  }
  const url = `${window.location.origin}${path}`
  try {
    await navigator.clipboard.writeText(url)
    message.success('运行链接已复制')
  } catch {
    message.info(url)
  }
}

function formatTime(v) {
  if (!v) return '—'
  try {
    return new Date(v).toLocaleString()
  } catch {
    return String(v)
  }
}

onMounted(() => {
  refresh()
  timer = setInterval(refresh, REFRESH_MS)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="home ln-page">
    <div class="ln-page-head">
      <div>
        <p class="ln-page-kicker">Ops Overview</p>
        <h1 class="ln-page-title">运维总览</h1>
        <p class="ln-page-desc">告警与接入状态一目了然；点击看板进入运行画面，可全屏或复制分享链接。</p>
      </div>
      <a-button :loading="loading" @click="refresh">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </div>

    <div class="status-rail" role="list">
      <button
        v-for="item in kpiItems"
        :key="item.key"
        type="button"
        class="status-cell"
        :class="`status-cell--${item.tone}`"
        role="listitem"
        @click="goKpi(item)"
      >
        <span class="status-accent" aria-hidden="true" />
        <span class="status-label">
          <component :is="item.icon" class="status-ico" />
          {{ item.label }}
        </span>
        <span class="status-value">{{ item.value }}</span>
      </button>
    </div>

    <div class="main-grid">
      <section class="panel boards-panel">
        <div class="panel-head">
          <div>
            <p class="panel-kicker">HMI Boards</p>
            <h2>组态看板</h2>
          </div>
          <a-button type="link" @click="router.push('/hmi/screens')">管理工程</a-button>
        </div>

        <a-spin :spinning="loading">
          <div v-if="boardRows.length" class="board-grid">
            <article v-for="board in boardRows" :key="board.id" class="board-card">
              <button type="button" class="board-main" @click="openBoard(board)">
                <div class="board-visual" aria-hidden="true">
                  <span class="board-grid-bg" />
                  <ApiOutlined />
                </div>
                <div class="board-info">
                  <div class="board-name">{{ board.name }}</div>
                  <div class="board-meta">
                    {{ board.pageCount }} 页 · 更新 {{ formatTime(board.updatedAt) }}
                  </div>
                </div>
              </button>
              <div class="board-actions">
                <a-button size="small" type="primary" ghost @click="openBoardFullscreen(board)">
                  <template #icon><ExpandOutlined /></template>
                  全屏
                </a-button>
                <a-button size="small" @click="copyShareLink(board)">
                  <template #icon><CopyOutlined /></template>
                  分享
                </a-button>
              </div>
            </article>
          </div>
          <a-empty v-else description="暂无组态工程">
            <a-button type="primary" @click="router.push('/hmi/screens')">去创建工程</a-button>
          </a-empty>
        </a-spin>
      </section>

      <section class="panel map-panel">
        <div class="panel-head">
          <div>
            <p class="panel-kicker">Geo</p>
            <h2>
              <EnvironmentOutlined />
              设备分布
            </h2>
          </div>
          <span class="map-hint">中国地图 · 可缩放</span>
        </div>
        <ChinaDeviceMap :gateways="gateways" :height="360" @select="goGateway" />
        <ul class="map-legend">
          <li><span class="dot online" />在线 {{ onlineGateways.length }}</li>
          <li><span class="dot offline" />离线 {{ offlineGateways.length }}</li>
          <li class="muted">未配置经纬度时按城市示意落点</li>
        </ul>
      </section>
    </div>

    <section v-if="activeAlarms.length" class="panel alarm-panel">
      <div class="panel-head">
        <div>
          <p class="panel-kicker">Active Alarms</p>
          <h2>最近活动告警</h2>
        </div>
        <a-button type="link" @click="router.push('/ops/alarms')">全部告警</a-button>
      </div>
      <a-table
        size="small"
        row-key="id"
        :pagination="false"
        :data-source="activeAlarms.slice(0, 5)"
        :columns="[
          { title: '时间', dataIndex: 'triggeredAt', key: 'triggeredAt', width: 180 },
          { title: '级别', dataIndex: 'severity', key: 'severity', width: 90 },
          { title: '内容', dataIndex: 'message', key: 'message', ellipsis: true },
        ]"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'triggeredAt'">
            <span class="mono-time">{{ formatTime(record.triggeredAt) }}</span>
          </template>
          <template v-else-if="column.key === 'severity'">
            <a-tag :color="record.severity === 'critical' ? 'error' : 'warning'">
              {{ record.severity || '—' }}
            </a-tag>
          </template>
        </template>
      </a-table>
    </section>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.status-rail {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1px;
  background: var(--ln-line);
  border: 1px solid var(--ln-line);
  border-radius: var(--ln-radius);
  overflow: hidden;
  box-shadow: var(--ln-shadow);
}

.status-cell {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 16px 18px 14px;
  border: 0;
  background: var(--ln-panel);
  text-align: left;
  cursor: pointer;
  transition: background 0.15s;
  min-height: 96px;
}

.status-cell:hover {
  background: #f7fbfb;
}

.status-accent {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--ln-line-strong);
}

.status-cell--danger .status-accent {
  background: var(--ln-danger);
}
.status-cell--warn .status-accent {
  background: var(--ln-warn);
}
.status-cell--info .status-accent,
.status-cell--ok .status-accent {
  background: var(--ln-signal);
}
.status-cell--neutral .status-accent {
  background: var(--ln-ink-3);
}

.status-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.04em;
  color: var(--ln-muted);
}

.status-ico {
  font-size: 12px;
}

.status-cell--danger .status-label {
  color: var(--ln-danger);
}
.status-cell--warn .status-label {
  color: var(--ln-warn);
}

.status-value {
  font-family: var(--font-display);
  font-size: 32px;
  font-weight: 650;
  line-height: 1;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
  color: var(--ln-ink);
}

.status-cell--danger .status-value {
  color: var(--ln-danger);
}

.main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(300px, 0.95fr);
  gap: 16px;
  align-items: stretch;
}

.map-panel {
  min-width: 0;
}

.panel {
  background: var(--ln-panel);
  border: 1px solid var(--ln-line);
  border-radius: var(--ln-radius);
  padding: 14px 16px 16px;
  min-width: 0;
  box-shadow: var(--ln-shadow);
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 14px;
}

.panel-kicker {
  margin: 0 0 2px;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--ln-primary);
}

.panel-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ln-ink);
  font-family: var(--font-display);
  letter-spacing: -0.01em;
}

.board-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.board-card {
  border: 1px solid var(--ln-line);
  border-radius: var(--ln-radius);
  overflow: hidden;
  background: #f7fafb;
  display: flex;
  flex-direction: column;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.board-card:hover {
  border-color: rgba(43, 184, 154, 0.45);
  box-shadow: 0 6px 18px rgba(11, 31, 42, 0.06);
}

.board-main {
  border: 0;
  background: transparent;
  padding: 0;
  text-align: left;
  cursor: pointer;
  color: inherit;
}

.board-visual {
  position: relative;
  height: 92px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  color: var(--ln-signal);
  background: linear-gradient(160deg, #0f2a35 0%, #163d4a 55%, #0f2834 100%);
  overflow: hidden;
}

.board-grid-bg {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(43, 184, 154, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(43, 184, 154, 0.08) 1px, transparent 1px);
  background-size: 18px 18px;
  mask-image: radial-gradient(ellipse at 50% 40%, #000 20%, transparent 75%);
  pointer-events: none;
}

.board-info {
  padding: 10px 12px 4px;
  background: #fff;
}

.board-name {
  font-weight: 650;
  font-size: 14px;
  color: var(--ln-ink);
}

.board-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ln-muted);
  font-family: var(--font-mono);
}

.board-actions {
  display: flex;
  gap: 8px;
  padding: 8px 12px 12px;
  background: #fff;
}

.map-hint {
  font-size: 11px;
  color: var(--ln-muted);
  font-family: var(--font-mono);
  margin-top: 4px;
}

.map-legend {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--ln-text-2);
}

.map-legend .dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}

.map-legend .dot.online {
  background: var(--ln-signal);
  box-shadow: 0 0 0 3px var(--ln-signal-soft);
}

.map-legend .dot.offline {
  background: var(--ln-danger);
}

.map-legend .muted {
  color: var(--ln-muted);
}

.alarm-panel {
  padding-bottom: 8px;
}

.mono-time {
  font-family: var(--font-mono);
  font-size: 12px;
}

@media (max-width: 960px) {
  .status-rail {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .main-grid {
    grid-template-columns: 1fr;
  }
  .map-panel {
    order: -1;
  }
}

@media (max-width: 560px) {
  .status-rail {
    grid-template-columns: 1fr;
  }
}
</style>
