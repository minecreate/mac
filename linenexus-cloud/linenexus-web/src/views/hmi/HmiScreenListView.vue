<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  PlusOutlined,
  EditOutlined,
  PlayCircleOutlined,
  DeleteOutlined,
} from '@ant-design/icons-vue'
import {
  listHmiScreens,
  listHmiProjects,
  createHmiProject,
  updateHmiProject,
  deleteHmiProject,
} from '../../api/hmi'

const DEFAULT_PROJECT_ID = 'hmi-proj-default'

const router = useRouter()
const loading = ref(false)
const screens = ref([])
const projects = ref([])
const createProjectOpen = ref(false)
const renameProjectOpen = ref(false)
const newProjectName = ref('')
const renameProjectName = ref('')
const renamingProject = ref(null)

const columns = [
  { title: '工程名称', dataIndex: 'name', key: 'name' },
  { title: '画面数', key: 'pageCount', width: 100 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 200 },
  { title: '操作', key: 'actions', width: 320 },
]

const rows = computed(() =>
  projects.value.map((p) => ({
    ...p,
    pageCount: screens.value.filter((s) => (s.projectId || DEFAULT_PROJECT_ID) === p.id).length,
  })),
)

async function refresh() {
  loading.value = true
  try {
    const [projRows, screenRows] = await Promise.all([listHmiProjects(), listHmiScreens()])
    projects.value = projRows
    screens.value = screenRows
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(refresh)

function homeIdOf(project) {
  return project?.homeScreenId || null
}

function goEdit(project) {
  const homeId = homeIdOf(project)
  if (!homeId) {
    message.warning('工程尚未生成主画面，请刷新后重试')
    return
  }
  router.push(`/hmi/screens/${homeId}/edit`)
}

function goRuntime(project) {
  const homeId = homeIdOf(project)
  if (!homeId) {
    message.warning('工程尚未生成主画面，请刷新后重试')
    return
  }
  router.push(`/hmi/screens/${homeId}/runtime`)
}

async function onCreateProject() {
  const name = newProjectName.value.trim()
  if (!name) {
    message.warning('请输入工程名称')
    return
  }
  try {
    const row = await createHmiProject({ name })
    createProjectOpen.value = false
    newProjectName.value = ''
    message.success('工程已创建，已进入主画面编辑')
    router.push(`/hmi/screens/${row.homeScreenId}/edit`)
  } catch (e) {
    message.error(e.message || '创建工程失败')
  }
}

function openRename(project) {
  renamingProject.value = project
  renameProjectName.value = project.name
  renameProjectOpen.value = true
}

async function onRenameProject() {
  const name = renameProjectName.value.trim()
  if (!name || !renamingProject.value) return
  try {
    await updateHmiProject(renamingProject.value.id, { name })
    renameProjectOpen.value = false
    renamingProject.value = null
    message.success('已重命名')
    await refresh()
  } catch (e) {
    message.error(e.message || '重命名失败')
  }
}

function onDeleteProject(project) {
  if (project.id === DEFAULT_PROJECT_ID) {
    message.warning('默认工程不可删除')
    return
  }
  Modal.confirm({
    title: `删除工程「${project.name}」？`,
    content: '工程下的画面会移到「默认工程」，画面本身不会删除。',
    okType: 'danger',
    async onOk() {
      await deleteHmiProject(project.id)
      message.success('工程已删除')
      await refresh()
    },
  })
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="title">组态工程</h2>
        <p class="sub">一个工程一个入口；进入后编辑主画面，其它页面在编辑器内新建与切换。</p>
      </div>
      <a-button type="primary" @click="createProjectOpen = true">
        <template #icon><PlusOutlined /></template>
        新建工程
      </a-button>
    </div>

    <a-table
      row-key="id"
      :loading="loading"
      :columns="columns"
      :data-source="rows"
      :pagination="{ pageSize: 10 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'pageCount'">
          {{ record.pageCount }} 页
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a-button size="small" type="primary" @click="goEdit(record)">
              <template #icon><EditOutlined /></template>
              编辑
            </a-button>
            <a-button size="small" type="primary" ghost @click="goRuntime(record)">
              <template #icon><PlayCircleOutlined /></template>
              运行
            </a-button>
            <a-button size="small" @click="openRename(record)">重命名</a-button>
            <a-button
              size="small"
              danger
              :disabled="record.id === DEFAULT_PROJECT_ID"
              @click="onDeleteProject(record)"
            >
              <template #icon><DeleteOutlined /></template>
              删除
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="createProjectOpen" title="新建工程" ok-text="创建" @ok="onCreateProject">
      <p class="modal-hint">创建后自动生成「主画面」作为启动首页，并进入编辑。</p>
      <a-input
        v-model:value="newProjectName"
        placeholder="工程名称，如：一号线 / 实验室A"
        @pressEnter="onCreateProject"
      />
    </a-modal>

    <a-modal v-model:open="renameProjectOpen" title="重命名工程" ok-text="保存" @ok="onRenameProject">
      <a-input v-model:value="renameProjectName" @pressEnter="onRenameProject" />
    </a-modal>
  </div>
</template>

<style scoped>
.page {
  padding: 20px 24px;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  gap: 16px;
}
.title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.sub {
  margin: 6px 0 0;
  color: rgba(0, 0, 0, 0.45);
}
.modal-hint {
  margin: 0 0 10px;
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}
</style>
