<script setup>
import { onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { createTenant, listTenants, updateTenant } from '../../api/platform'

const loading = ref(false)
const rows = ref([])
const createOpen = ref(false)
const creating = ref(false)
const form = reactive({
  id: '',
  name: '',
  remark: '',
  adminUsername: 'admin',
  adminPassword: '',
  adminDisplayName: '管理员',
})

const columns = [
  { title: '租户 ID', dataIndex: 'id', key: 'id', width: 160 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 190 },
  { title: '操作', key: 'actions', width: 140 },
]

async function load() {
  loading.value = true
  try {
    rows.value = (await listTenants()) || []
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.id = ''
  form.name = ''
  form.remark = ''
  form.adminUsername = 'admin'
  form.adminPassword = ''
  form.adminDisplayName = '管理员'
  createOpen.value = true
}

async function onCreate() {
  if (!form.id.trim() || !form.name.trim()) {
    message.warning('请填写租户 ID 与名称')
    return
  }
  creating.value = true
  try {
    const body = {
      id: form.id.trim(),
      name: form.name.trim(),
      remark: form.remark.trim() || null,
    }
    if (form.adminUsername.trim()) {
      if (!form.adminPassword) {
        message.warning('填写了管理员用户名时须设置初始密码')
        creating.value = false
        return
      }
      body.adminUsername = form.adminUsername.trim()
      body.adminPassword = form.adminPassword
      body.adminDisplayName = form.adminDisplayName.trim() || form.adminUsername.trim()
    }
    await createTenant(body)
    message.success('租户已开通')
    createOpen.value = false
    await load()
  } catch (e) {
    message.error(e.message || '开通失败')
  } finally {
    creating.value = false
  }
}

function toggleStatus(row) {
  const next = row.status === 'active' ? 'disabled' : 'active'
  const label = next === 'active' ? '启用' : '停用'
  Modal.confirm({
    title: `${label}租户「${row.name}」？`,
    content: next === 'disabled' ? '停用后该租户账号仍可存在，但建议业务侧拒绝其访问。' : '',
    async onOk() {
      try {
        await updateTenant(row.id, { status: next })
        message.success(`已${label}`)
        await load()
      } catch (e) {
        message.error(e.message || '操作失败')
        throw e
      }
    },
  })
}

function fmtTime(v) {
  if (!v) return '—'
  try {
    return new Date(v).toLocaleString()
  } catch {
    return String(v)
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="head">
      <div>
        <h2>租户管理</h2>
        <p>开通、启停租户；可选同时创建首个租户管理员。</p>
      </div>
      <a-button type="primary" @click="openCreate">开通租户</a-button>
    </div>

    <a-table
      row-key="id"
      :loading="loading"
      :columns="columns"
      :data-source="rows"
      :pagination="{ pageSize: 20 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 'active' ? 'green' : 'default'">
            {{ record.status === 'active' ? '启用' : '停用' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'createdAt'">
          {{ fmtTime(record.createdAt) }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-button type="link" size="small" @click="toggleStatus(record)">
            {{ record.status === 'active' ? '停用' : '启用' }}
          </a-button>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="createOpen"
      title="开通租户"
      :confirm-loading="creating"
      ok-text="开通"
      @ok="onCreate"
    >
      <a-form layout="vertical" class="create-form">
        <a-form-item label="租户 ID" required>
          <a-input v-model:value="form.id" placeholder="如 acme-plant" />
        </a-form-item>
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" placeholder="显示名称" />
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="form.remark" placeholder="可选" />
        </a-form-item>
        <a-divider>首个管理员（可选）</a-divider>
        <a-form-item label="用户名">
          <a-input v-model:value="form.adminUsername" placeholder="留空则不创建" />
        </a-form-item>
        <a-form-item label="初始密码">
          <a-input-password v-model:value="form.adminPassword" />
        </a-form-item>
        <a-form-item label="显示名">
          <a-input v-model:value="form.adminDisplayName" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.page h2 {
  margin: 0;
  font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
  font-size: 22px;
  font-weight: 650;
  color: #1a1210;
}
.page p {
  margin: 6px 0 0;
  color: #6b5a52;
  font-size: 13px;
}
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}
.create-form {
  margin-top: 8px;
}
</style>
