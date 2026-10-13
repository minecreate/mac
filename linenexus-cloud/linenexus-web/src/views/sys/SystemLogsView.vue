<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { getAuditLog, listAuditLogs } from '../../api/audit'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const detailOpen = ref(false)
const detail = ref(null)
const detailLoading = ref(false)

const filters = reactive({
  category: undefined,
  action: undefined,
  result: undefined,
  actor: '',
  keyword: '',
  range: [],
})

const pager = reactive({
  page: 1,
  size: 20,
})

const categoryOptions = [
  { value: 'auth', label: '安全认证' },
  { value: 'control', label: '控制写点' },
  { value: 'alarm', label: '告警' },
  { value: 'config', label: '配置变更' },
]

const actionOptions = [
  { value: 'login', label: '登录成功' },
  { value: 'login_fail', label: '登录失败' },
  { value: 'password_change', label: '改密码' },
  { value: 'profile_update', label: '改资料' },
  { value: 'point_write', label: '写点' },
  { value: 'alarm_ack', label: '告警确认' },
  { value: 'hmi_publish', label: '组态发布' },
  { value: 'gateway_register', label: '网关登记' },
  { value: 'gateway_delete', label: '网关删除' },
]

const columns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 190 },
  { title: '类别', dataIndex: 'category', key: 'category', width: 100 },
  { title: '动作', dataIndex: 'action', key: 'action', width: 120 },
  { title: '结果', dataIndex: 'result', key: 'result', width: 90 },
  { title: '操作者', dataIndex: 'actorName', key: 'actorName', width: 120 },
  { title: '对象', dataIndex: 'target', key: 'target', width: 180, ellipsis: true },
  { title: '摘要', dataIndex: 'summary', key: 'summary', ellipsis: true },
  { title: 'IP', dataIndex: 'clientIp', key: 'clientIp', width: 130 },
  { title: '操作', key: 'ops', width: 90 },
]

const categoryLabel = computed(() => Object.fromEntries(categoryOptions.map((o) => [o.value, o.label])))
const actionLabel = computed(() => Object.fromEntries(actionOptions.map((o) => [o.value, o.label])))

onMounted(() => refresh())

async function refresh() {
  loading.value = true
  try {
    const range = filters.range || []
    const data = await listAuditLogs({
      category: filters.category,
      action: filters.action,
      result: filters.result,
      actor: filters.actor.trim() || undefined,
      keyword: filters.keyword.trim() || undefined,
      from: range[0] ? dayjs(range[0]).toISOString() : undefined,
      to: range[1] ? dayjs(range[1]).toISOString() : undefined,
      page: pager.page - 1,
      size: pager.size,
    })
    rows.value = data?.items || []
    total.value = Number(data?.total || 0)
  } catch (e) {
    message.error(e.message || '加载系统日志失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pager.page = 1
  refresh()
}

function onReset() {
  filters.category = undefined
  filters.action = undefined
  filters.result = undefined
  filters.actor = ''
  filters.keyword = ''
  filters.range = []
  pager.page = 1
  refresh()
}

function onPageChange(page, size) {
  pager.page = page
  pager.size = size
  refresh()
}

async function openDetail(row) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = row
  try {
    detail.value = await getAuditLog(row.id)
  } catch (e) {
    message.error(e.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

function formatTime(v) {
  if (!v) return '—'
  try {
    return dayjs(v).format('YYYY-MM-DD HH:mm:ss')
  } catch {
    return String(v)
  }
}
</script>

<template>
  <div class="page ln-page">
    <div class="ln-page-head">
      <div>
        <p class="ln-page-kicker">Audit Trail</p>
        <h2 class="ln-page-title">系统日志</h2>
        <p class="ln-page-desc">记录登录、写点、告警确认与关键配置变更；保留 90 天，仅管理员可查。</p>
      </div>
      <a-button :loading="loading" @click="refresh">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </div>

    <a-card class="filter-card" :bordered="false">
      <a-form layout="inline" class="filters" @finish="onSearch">
        <a-form-item label="时间">
          <a-range-picker
            v-model:value="filters.range"
            show-time
            style="width: 360px"
            :placeholder="['开始', '结束']"
          />
        </a-form-item>
        <a-form-item label="类别">
          <a-select
            v-model:value="filters.category"
            allow-clear
            placeholder="全部"
            style="width: 130px"
            :options="categoryOptions"
          />
        </a-form-item>
        <a-form-item label="动作">
          <a-select
            v-model:value="filters.action"
            allow-clear
            placeholder="全部"
            style="width: 140px"
            :options="actionOptions"
          />
        </a-form-item>
        <a-form-item label="结果">
          <a-select
            v-model:value="filters.result"
            allow-clear
            placeholder="全部"
            style="width: 110px"
            :options="[
              { value: 'success', label: '成功' },
              { value: 'failure', label: '失败' },
            ]"
          />
        </a-form-item>
        <a-form-item label="操作者">
          <a-input v-model:value="filters.actor" allow-clear placeholder="用户名" style="width: 120px" />
        </a-form-item>
        <a-form-item label="关键字">
          <a-input
            v-model:value="filters.keyword"
            allow-clear
            placeholder="摘要 / 对象"
            style="width: 160px"
          />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" html-type="submit">
              <template #icon><SearchOutlined /></template>
              查询
            </a-button>
            <a-button @click="onReset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <a-table
      row-key="id"
      :loading="loading"
      :columns="columns"
      :data-source="rows"
      :pagination="{
        current: pager.page,
        pageSize: pager.size,
        total,
        showSizeChanger: true,
        showTotal: (t) => `共 ${t} 条`,
        onChange: onPageChange,
      }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'createdAt'">
          {{ formatTime(record.createdAt) }}
        </template>
        <template v-else-if="column.key === 'category'">
          {{ categoryLabel[record.category] || record.category }}
        </template>
        <template v-else-if="column.key === 'action'">
          {{ actionLabel[record.action] || record.action }}
        </template>
        <template v-else-if="column.key === 'result'">
          <a-tag :color="record.result === 'success' ? 'success' : 'error'">
            {{ record.result === 'success' ? '成功' : '失败' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'ops'">
          <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
        </template>
      </template>
    </a-table>

    <a-drawer v-model:open="detailOpen" title="日志详情" width="520" :destroy-on-close="true">
      <a-spin :spinning="detailLoading">
        <a-descriptions v-if="detail" :column="1" size="small" bordered>
          <a-descriptions-item label="时间">{{ formatTime(detail.createdAt) }}</a-descriptions-item>
          <a-descriptions-item label="类别">
            {{ categoryLabel[detail.category] || detail.category }}
          </a-descriptions-item>
          <a-descriptions-item label="动作">
            {{ actionLabel[detail.action] || detail.action }}
          </a-descriptions-item>
          <a-descriptions-item label="结果">{{ detail.result }}</a-descriptions-item>
          <a-descriptions-item label="操作者">
            {{ detail.actorName || '—' }}
            <span v-if="detail.actorId" class="muted">（{{ detail.actorId }}）</span>
          </a-descriptions-item>
          <a-descriptions-item label="对象">{{ detail.target || '—' }}</a-descriptions-item>
          <a-descriptions-item label="IP">{{ detail.clientIp || '—' }}</a-descriptions-item>
          <a-descriptions-item label="摘要">{{ detail.summary || '—' }}</a-descriptions-item>
          <a-descriptions-item label="详情">
            <pre class="detail-pre">{{ detail.detail || '—' }}</pre>
          </a-descriptions-item>
        </a-descriptions>
      </a-spin>
    </a-drawer>
  </div>
</template>

<style scoped>
.filter-card {
  margin-bottom: 12px;
  background: var(--ln-panel, #fff);
  border: 1px solid var(--ln-line, #f0f0f0) !important;
  border-radius: var(--ln-radius, 8px);
  box-shadow: var(--ln-shadow, none);
}
.filters {
  row-gap: 10px;
}
.muted {
  color: var(--ln-muted, rgba(0, 0, 0, 0.45));
  margin-left: 4px;
}
.detail-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: var(--font-mono, ui-monospace, Consolas, monospace);
  font-size: 12px;
  max-height: 240px;
  overflow: auto;
}
</style>
