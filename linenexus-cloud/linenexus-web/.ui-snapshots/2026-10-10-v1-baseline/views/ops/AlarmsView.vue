<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  ackAlarmEvent,
  createAlarmRule,
  deleteAlarmRule,
  listAlarmEvents,
  listAlarmRules,
  updateAlarmRule,
} from '../../api/monitor'
import { canWrite } from '../../auth/session'

const writable = computed(() => canWrite())
const loadingEvents = ref(false)
const loadingRules = ref(false)
const events = ref([])
const rules = ref([])
const eventStatus = ref('active')

const ruleModalOpen = ref(false)
const ruleForm = reactive({
  id: '',
  name: '',
  ruleType: 'threshold',
  enabled: true,
  gatewayId: '',
  deviceId: '',
  pointId: '',
  operator: 'gt',
  threshold: 100,
  offlineSec: 60,
  severity: 'warning',
})

const eventColumns = [
  { title: '时间', dataIndex: 'triggeredAt', key: 'triggeredAt', width: 200 },
  { title: '级别', dataIndex: 'severity', key: 'severity', width: 90 },
  { title: '规则', dataIndex: 'ruleName', key: 'ruleName', width: 140 },
  { title: '类型', dataIndex: 'ruleType', key: 'ruleType', width: 100 },
  { title: '内容', dataIndex: 'message', key: 'message' },
  { title: '状态', dataIndex: 'status', key: 'status', width: 90 },
  { title: '操作', key: 'action', width: 100 },
]

const ruleColumns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '类型', dataIndex: 'ruleType', key: 'ruleType', width: 100 },
  { title: '启用', dataIndex: 'enabled', key: 'enabled', width: 80 },
  { title: '范围', key: 'scope' },
  { title: '条件', key: 'cond' },
  { title: '级别', dataIndex: 'severity', key: 'severity', width: 90 },
  { title: '操作', key: 'action', width: 140 },
]

onMounted(() => {
  refreshEvents()
  refreshRules()
})

async function refreshEvents() {
  loadingEvents.value = true
  try {
    events.value = (await listAlarmEvents(eventStatus.value || undefined)) || []
  } catch (e) {
    message.error(e.message || '加载告警失败')
  } finally {
    loadingEvents.value = false
  }
}

async function refreshRules() {
  loadingRules.value = true
  try {
    rules.value = (await listAlarmRules()) || []
  } catch (e) {
    message.error(e.message || '加载规则失败')
  } finally {
    loadingRules.value = false
  }
}

async function onAck(row) {
  try {
    await ackAlarmEvent(row.id)
    message.success('已确认')
    refreshEvents()
  } catch (e) {
    message.error(e.message || '确认失败')
  }
}

function openCreateRule() {
  Object.assign(ruleForm, {
    id: '',
    name: '',
    ruleType: 'threshold',
    enabled: true,
    gatewayId: '',
    deviceId: '',
    pointId: '',
    operator: 'gt',
    threshold: 100,
    offlineSec: 60,
    severity: 'warning',
  })
  ruleModalOpen.value = true
}

function openEditRule(row) {
  Object.assign(ruleForm, {
    id: row.id,
    name: row.name,
    ruleType: row.ruleType,
    enabled: row.enabled,
    gatewayId: row.gatewayId || '',
    deviceId: row.deviceId || '',
    pointId: row.pointId || '',
    operator: row.operator || 'gt',
    threshold: row.threshold ?? 100,
    offlineSec: row.offlineSec ?? 60,
    severity: row.severity || 'warning',
  })
  ruleModalOpen.value = true
}

async function saveRule() {
  const body = {
    name: ruleForm.name,
    ruleType: ruleForm.ruleType,
    enabled: ruleForm.enabled,
    gatewayId: ruleForm.gatewayId || null,
    deviceId: ruleForm.deviceId || null,
    pointId: ruleForm.pointId || null,
    operator: ruleForm.operator,
    threshold: ruleForm.ruleType === 'threshold' ? Number(ruleForm.threshold) : null,
    offlineSec: ruleForm.ruleType === 'offline' ? Number(ruleForm.offlineSec) : null,
    severity: ruleForm.severity,
  }
  try {
    if (ruleForm.id) {
      await updateAlarmRule(ruleForm.id, body)
      message.success('已更新')
    } else {
      await createAlarmRule(body)
      message.success('已创建')
    }
    ruleModalOpen.value = false
    refreshRules()
  } catch (e) {
    message.error(e.message || '保存失败')
  }
}

function onDeleteRule(row) {
  Modal.confirm({
    title: '删除规则？',
    content: row.name,
    onOk: async () => {
      await deleteAlarmRule(row.id)
      message.success('已删除')
      refreshRules()
    },
  })
}

function scopeText(row) {
  const parts = []
  if (row.gatewayId) parts.push(`网关:${row.gatewayId}`)
  if (row.deviceId) parts.push(`设备:${row.deviceId}`)
  if (row.pointId) parts.push(`点:${row.pointId}`)
  return parts.length ? parts.join(' / ') : '租户内全部'
}

function condText(row) {
  if (row.ruleType === 'offline') {
    return `心跳超时 ≥ ${row.offlineSec || 60}s`
  }
  return `${row.operator || 'gt'} ${row.threshold}`
}
</script>

<template>
  <a-space direction="vertical" style="width: 100%" :size="16">
    <a-card title="活动告警">
      <template #extra>
        <a-space>
          <a-select
            v-model:value="eventStatus"
            style="width: 120px"
            :options="[
              { value: 'active', label: '活动中' },
              { value: 'acked', label: '已确认' },
              { value: '', label: '全部' },
            ]"
            @change="refreshEvents"
          />
          <a-button @click="refreshEvents">刷新</a-button>
        </a-space>
      </template>
      <a-table
        :loading="loadingEvents"
        :columns="eventColumns"
        :data-source="events"
        row-key="id"
        size="middle"
        :pagination="{ pageSize: 10 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-button
              v-if="record.status === 'active' && writable"
              type="link"
              size="small"
              @click="onAck(record)"
            >
              确认
            </a-button>
            <span v-else>—</span>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-card title="告警规则">
      <template #extra>
        <a-space>
          <a-button v-if="writable" type="primary" @click="openCreateRule">新建规则</a-button>
          <a-button @click="refreshRules">刷新</a-button>
        </a-space>
      </template>
      <a-table
        :loading="loadingRules"
        :columns="ruleColumns"
        :data-source="rules"
        row-key="id"
        size="middle"
        :pagination="{ pageSize: 8 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'default'">
              {{ record.enabled ? '开' : '关' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'scope'">{{ scopeText(record) }}</template>
          <template v-else-if="column.key === 'cond'">{{ condText(record) }}</template>
          <template v-else-if="column.key === 'action'">
            <a-space v-if="writable">
              <a-button type="link" size="small" @click="openEditRule(record)">编辑</a-button>
              <a-button type="link" size="small" danger @click="onDeleteRule(record)">删除</a-button>
            </a-space>
            <span v-else>—</span>
          </template>
        </template>
      </a-table>
    </a-card>
  </a-space>

  <a-modal
    v-model:open="ruleModalOpen"
    :title="ruleForm.id ? '编辑规则' : '新建规则'"
    ok-text="保存"
    @ok="saveRule"
  >
    <a-form layout="vertical">
      <a-form-item label="名称" required>
        <a-input v-model:value="ruleForm.name" />
      </a-form-item>
      <a-form-item label="类型">
        <a-radio-group v-model:value="ruleForm.ruleType">
          <a-radio value="threshold">阈值</a-radio>
          <a-radio value="offline">离线</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="启用">
        <a-switch v-model:checked="ruleForm.enabled" />
      </a-form-item>
      <a-form-item label="网关 ID（可空）">
        <a-input v-model:value="ruleForm.gatewayId" placeholder="留空=租户内全部网关" />
      </a-form-item>
      <template v-if="ruleForm.ruleType === 'threshold'">
        <a-form-item label="设备 ID（可空）">
          <a-input v-model:value="ruleForm.deviceId" />
        </a-form-item>
        <a-form-item label="点 ID" required>
          <a-input v-model:value="ruleForm.pointId" />
        </a-form-item>
        <a-form-item label="比较">
          <a-select
            v-model:value="ruleForm.operator"
            :options="[
              { value: 'gt', label: '>' },
              { value: 'gte', label: '≥' },
              { value: 'lt', label: '<' },
              { value: 'lte', label: '≤' },
              { value: 'eq', label: '=' },
            ]"
          />
        </a-form-item>
        <a-form-item label="阈值">
          <a-input-number v-model:value="ruleForm.threshold" style="width: 100%" />
        </a-form-item>
      </template>
      <template v-else>
        <a-form-item label="离线超时（秒）">
          <a-input-number v-model:value="ruleForm.offlineSec" :min="1" style="width: 100%" />
        </a-form-item>
      </template>
      <a-form-item label="级别">
        <a-select
          v-model:value="ruleForm.severity"
          :options="[
            { value: 'info', label: 'info' },
            { value: 'warning', label: 'warning' },
            { value: 'critical', label: 'critical' },
          ]"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>
