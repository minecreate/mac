<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { ArrowLeftOutlined, SaveOutlined, CloudUploadOutlined } from '@ant-design/icons-vue'
import StageHost from '../../hmi/canvas/StageHost.vue'
import PalettePanel from '../../hmi/symbols/PalettePanel.vue'
import PageTreePanel from '../../hmi/editor/PageTreePanel.vue'
import { createCommandHistory } from '../../hmi/editor/commands'
import {
  createElement,
  createEmptyDocument,
  parseDocument,
  serializeDocument,
  POPUP_DEFAULT_SIZE,
} from '../../hmi/model/types'
import { listSymbols } from '../../hmi/symbols/registry'
import {
  getHmiScreen,
  updateHmiScreen,
  publishHmiScreen,
  listHmiScreens,
  listHmiProjects,
  createHmiScreen,
  setHmiProjectHomeScreen,
} from '../../api/hmi'
import { listEdgeGateways, listDevices, listDevicePoints } from '../../api/asset'

const DEFAULT_PROJECT_ID = 'hmi-proj-default'

const route = useRoute()
const router = useRouter()
const history = createCommandHistory()

const loading = ref(false)
const saving = ref(false)
const dirty = ref(false)
const screenMeta = ref(null)
const doc = ref(createEmptyDocument())
const selectedId = ref(null)
const screenName = ref('')
const projectPages = ref([])
const projectName = ref('当前工程')
const homeScreenId = ref('')
const createPageOpen = ref(false)
const newPageName = ref('')
const newPageRole = ref('main')
/** 左侧栏：pages=页面树，palette=图元库 */
const leftTab = ref('pages')

const isHomeScreen = computed(
  () => !!(homeScreenId.value && String(route.params.id) === homeScreenId.value),
)
const isPopupPage = computed(() => doc.value?.meta?.role === 'popup')

const gateways = ref([])
const devices = ref([])
const points = ref([])

const symbols = listSymbols()

const selected = computed(() => doc.value.elements.find((e) => e.id === selectedId.value) || null)

const bindForm = reactive({
  gatewayId: '',
  deviceId: '',
  pointId: '',
  kind: 'text',
  writeValue: '',
})

const bindingReady = computed(
  () => !!(bindForm.gatewayId && bindForm.deviceId && bindForm.pointId),
)

/** 仅在切换选中图元时回填表单，避免改属性/半成品绑点时被 watch 清空 */
watch(
  selectedId,
  async (id) => {
    const el = doc.value.elements.find((e) => e.id === id)
    if (!el) {
      bindForm.gatewayId = ''
      bindForm.deviceId = ''
      bindForm.pointId = ''
      bindForm.kind = 'text'
      bindForm.writeValue = ''
      devices.value = []
      points.value = []
      return
    }
    bindForm.kind =
      el.binding?.kind ||
      (el.type === 'button' ||
      el.type === 'switch' ||
      el.type === 'checkbox' ||
      el.type === 'input' ||
      el.type === 'numberInput' ||
      el.type === 'slider'
        ? 'clickWrite'
        : 'text')
    bindForm.writeValue = el.props?.writeValue ?? ''
    bindForm.gatewayId = el.binding?.gatewayId || ''
    bindForm.deviceId = el.binding?.deviceId || ''
    bindForm.pointId = el.binding?.pointId || ''
    devices.value = []
    points.value = []
    if (bindForm.gatewayId) await loadDevices(bindForm.gatewayId)
    if (bindForm.gatewayId && bindForm.deviceId) {
      await loadPoints(bindForm.gatewayId, bindForm.deviceId)
    }
  },
)

async function loadProjectPages(projectId) {
  const pid = projectId || DEFAULT_PROJECT_ID
  try {
    const [pages, projects] = await Promise.all([
      listHmiScreens({ projectId: pid }),
      listHmiProjects(),
    ])
    projectPages.value = pages.map((p) => ({
      ...p,
      pageRole: p.document?.meta?.role === 'popup' ? 'popup' : 'main',
    }))
    const project = projects.find((p) => p.id === pid)
    projectName.value = project?.name || '当前工程'
    homeScreenId.value = project?.homeScreenId || ''
  } catch {
    projectPages.value = []
  }
}

async function onSetAsHome() {
  const projectId = screenMeta.value?.projectId || DEFAULT_PROJECT_ID
  const screenId = String(route.params.id || '')
  if (!screenId) return
  try {
    const project = await setHmiProjectHomeScreen(projectId, screenId)
    homeScreenId.value = project.homeScreenId || screenId
    message.success('已设为工程启动首页')
  } catch (e) {
    message.error(e.message || '设置失败')
  }
}

async function load() {
  loading.value = true
  try {
    const id = route.params.id
    const row = await getHmiScreen(id)
    screenMeta.value = row
    screenName.value = row.name
    doc.value = parseDocument(row.document)
    history.clear()
    dirty.value = false
    selectedId.value = null
    gateways.value = await listEdgeGateways()
    await loadProjectPages(row.projectId)
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

watch(
  () => route.params.id,
  (id, prev) => {
    if (id && id !== prev) load()
  },
)

function dispatch(cmd) {
  doc.value = history.push(doc.value, cmd)
  dirty.value = true
}

function switchToPage(id) {
  if (!id || id === route.params.id) return
  if (!dirty.value) {
    router.push(`/hmi/screens/${id}/edit`)
    return
  }
  Modal.confirm({
    title: '切换页面',
    content: '当前页面有未保存修改，是否先保存？',
    okText: '保存并切换',
    cancelText: '不保存',
    onOk: async () => {
      await onSave()
      router.push(`/hmi/screens/${id}/edit`)
    },
    onCancel: () => {
      dirty.value = false
      router.push(`/hmi/screens/${id}/edit`)
    },
  })
}

async function onCreatePageInProject() {
  const name = newPageName.value.trim()
  if (!name) {
    message.warning('请输入页面名称')
    return
  }
  try {
    if (dirty.value) await onSave()
    const role = newPageRole.value === 'popup' ? 'popup' : 'main'
    const empty =
      role === 'popup'
        ? createEmptyDocument({
            role: 'popup',
            width: POPUP_DEFAULT_SIZE.width,
            height: POPUP_DEFAULT_SIZE.height,
            popupPlacement: 'center',
          })
        : createEmptyDocument({ role: 'main' })
    const row = await createHmiScreen({
      name,
      projectId: screenMeta.value?.projectId || DEFAULT_PROJECT_ID,
      documentJson: serializeDocument(empty),
    })
    createPageOpen.value = false
    newPageName.value = ''
    newPageRole.value = 'main'
    message.success(role === 'popup' ? '已新建弹窗页' : '已新建页面')
    router.push(`/hmi/screens/${row.id}/edit`)
  } catch (e) {
    message.error(e.message || '新建失败')
  }
}

function updateCanvas(key, value) {
  const n = Number(value)
  if (Number.isNaN(n) || n < 80) return
  dirty.value = true
  doc.value = {
    ...doc.value,
    canvas: { ...doc.value.canvas, [key]: Math.round(n) },
  }
}

function updatePageMeta(key, value) {
  dirty.value = true
  const meta = { ...doc.value.meta, [key]: value }
  if (key === 'role' && value === 'popup') {
    const w = doc.value.canvas?.width || 1920
    const h = doc.value.canvas?.height || 1080
    // 从主画面切到弹窗时，若仍是全屏分辨率则收缩为默认弹窗尺寸
    const canvas =
      w >= 1280 || h >= 800
        ? { ...doc.value.canvas, width: POPUP_DEFAULT_SIZE.width, height: POPUP_DEFAULT_SIZE.height }
        : { ...doc.value.canvas }
    doc.value = { ...doc.value, canvas, meta }
    return
  }
  doc.value = { ...doc.value, meta }
}

function addElement(item) {
  const type = typeof item === 'string' ? item : item.type
  const preset = typeof item === 'object' && item ? item : {}
  const el = createElement(type, {
    x: 100 + doc.value.elements.length * 12,
    y: 100 + doc.value.elements.length * 8,
    ...(preset.transform || {}),
  })
  if (preset.symbolId) el.props.symbolId = preset.symbolId
  if (preset.props && typeof preset.props === 'object') {
    el.props = { ...el.props, ...preset.props }
  }
  if (preset.style && typeof preset.style === 'object') {
    el.style = { ...el.style, ...preset.style }
  }
  dispatch({ type: 'add', payload: el })
  selectedId.value = el.id
}

function onSelect(id) {
  selectedId.value = id
}

function onStageClick() {
  selectedId.value = null
}

function onChangeTransform({ id, transform }) {
  const el = doc.value.elements.find((e) => e.id === id)
  if (!el) return
  dispatch({
    type: 'update',
    payload: { ...el, transform: { ...el.transform, ...transform } },
  })
}

function updateSelected(patch) {
  if (!selected.value) return
  dispatch({ type: 'update', payload: { ...selected.value, ...patch } })
}

function normalizeHex(value) {
  const s = String(value || '').trim()
  if (/^#[0-9a-fA-F]{6}$/.test(s)) return s
  if (/^#[0-9a-fA-F]{3}$/.test(s)) {
    const r = s[1]
    const g = s[2]
    const b = s[3]
    return `#${r}${r}${g}${g}${b}${b}`
  }
  return ''
}

function updateSelectedStyle(key, value) {
  if (!selected.value) return
  updateSelected({ style: { ...selected.value.style, [key]: value } })
}

function updateSelectedProps(key, value) {
  if (!selected.value) return
  updateSelected({ props: { ...selected.value.props, [key]: value } })
}

function coerceWriteValue(raw) {
  const s = String(raw ?? '').trim()
  if (s === '') return ''
  const n = Number(s)
  return Number.isNaN(n) ? s : n
}

function onSliderOrientChange(orient) {
  if (!selected.value || selected.value.type !== 'slider') return
  const t = selected.value.transform
  const next = { ...selected.value.props, orient }
  // 横竖切换时交换宽高，避免细条方向不对
  const swap = (orient === 'v' && t.w > t.h) || (orient === 'h' && t.h > t.w)
  if (swap) {
    updateSelected({
      props: next,
      transform: { ...t, w: t.h, h: t.w },
    })
    return
  }
  updateSelected({ props: next })
}

function onImageFileChange(e) {
  const file = e?.target?.files?.[0]
  if (!file || !selected.value || selected.value.type !== 'image') return
  if (file.size > 800 * 1024) {
    message.warning('图片过大，请压缩到 800KB 以内')
    e.target.value = ''
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    updateSelectedProps('src', String(reader.result || ''))
    message.success('图片已载入')
  }
  reader.onerror = () => message.error('读取图片失败')
  reader.readAsDataURL(file)
  e.target.value = ''
}

function updateSelectedTransform(key, value) {
  if (!selected.value) return
  const n = Number(value)
  if (Number.isNaN(n)) return
  updateSelected({ transform: { ...selected.value.transform, [key]: n } })
}

function removeSelected() {
  if (!selectedId.value) return
  dispatch({ type: 'remove', payload: { id: selectedId.value } })
  selectedId.value = null
}

/** 数组靠后 = 越靠上（置顶） */
const selectedZIndex = computed(() =>
  doc.value.elements.findIndex((e) => e.id === selectedId.value),
)
const canBringForward = computed(
  () => selectedZIndex.value >= 0 && selectedZIndex.value < doc.value.elements.length - 1,
)
const canSendBackward = computed(() => selectedZIndex.value > 0)

function reorderSelected(action) {
  const id = selectedId.value
  if (!id) return
  const ids = doc.value.elements.map((e) => e.id)
  const i = ids.indexOf(id)
  if (i < 0) return
  let next = ids.slice()
  if (action === 'front') {
    next.splice(i, 1)
    next.push(id)
  } else if (action === 'back') {
    next.splice(i, 1)
    next.unshift(id)
  } else if (action === 'forward') {
    if (i >= next.length - 1) return
    ;[next[i], next[i + 1]] = [next[i + 1], next[i]]
  } else if (action === 'backward') {
    if (i <= 0) return
    ;[next[i - 1], next[i]] = [next[i], next[i - 1]]
  } else {
    return
  }
  dispatch({ type: 'reorder', payload: { ids: next } })
}

async function loadDevices(gwId) {
  try {
    devices.value = await listDevices(gwId)
  } catch {
    devices.value = []
  }
}

async function loadPoints(gwId, devId) {
  try {
    points.value = await listDevicePoints(gwId, devId)
  } catch {
    points.value = []
  }
}

async function onGatewayChange(gwId) {
  if (!gwId) {
    clearBinding()
    return
  }
  bindForm.gatewayId = gwId
  bindForm.deviceId = ''
  bindForm.pointId = ''
  points.value = []
  await loadDevices(gwId)
  // 三级未选齐前不写 binding，避免半成品触发回填把表单清空
}

async function onDeviceChange(devId) {
  if (!devId) {
    bindForm.deviceId = ''
    bindForm.pointId = ''
    points.value = []
    // 设备清空后绑定不完整：去掉已保存的 binding
    if (selected.value?.binding) updateSelected({ binding: undefined })
    return
  }
  bindForm.deviceId = devId
  bindForm.pointId = ''
  await loadPoints(bindForm.gatewayId, devId)
}

function onPointChange(pointId) {
  if (!pointId) {
    bindForm.pointId = ''
    if (selected.value?.binding) updateSelected({ binding: undefined })
    return
  }
  bindForm.pointId = pointId
  applyBindingFromForm()
}

function onKindChange(kind) {
  bindForm.kind = kind
  if (bindingReady.value) applyBindingFromForm()
}

function applyBindingFromForm() {
  if (!selected.value) return
  if (!bindForm.gatewayId || !bindForm.deviceId || !bindForm.pointId) {
    return
  }
  const props = { ...selected.value.props }
  if (bindForm.writeValue !== '' && bindForm.writeValue != null) {
    const n = Number(bindForm.writeValue)
    props.writeValue = Number.isNaN(n) ? bindForm.writeValue : n
  }
  updateSelected({
    props,
    binding: {
      gatewayId: bindForm.gatewayId,
      deviceId: bindForm.deviceId,
      pointId: bindForm.pointId,
      kind: bindForm.kind || 'text',
      map: {},
    },
  })
  message.success('已绑定点位（记得保存画面）', 1.5)
}

function clearBinding() {
  bindForm.gatewayId = ''
  bindForm.deviceId = ''
  bindForm.pointId = ''
  devices.value = []
  points.value = []
  if (selected.value?.binding) updateSelected({ binding: undefined })
}

async function onSave() {
  saving.value = true
  try {
    const row = await updateHmiScreen(route.params.id, {
      name: screenName.value,
      documentJson: serializeDocument(doc.value),
    })
    screenMeta.value = row
    dirty.value = false
    message.success('已保存草稿')
    await loadProjectPages(screenMeta.value?.projectId)
  } catch (e) {
    message.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onPublish() {
  await onSave()
  try {
    const row = await publishHmiScreen(route.params.id)
    screenMeta.value = row
    message.success('已发布')
  } catch (e) {
    message.error(e.message || '发布失败')
  }
}

function goBack() {
  router.push('/hmi/screens')
}

function goRuntime() {
  router.push(`/hmi/screens/${route.params.id}/runtime`)
}
</script>

<template>
  <div class="editor-page">
    <div class="toolbar">
      <a-space>
        <a-button @click="goBack">
          <template #icon><ArrowLeftOutlined /></template>
          工程列表
        </a-button>
        <span class="proj-label">{{ projectName }}</span>
        <a-input v-model:value="screenName" style="width: 200px" />
        <a-tag v-if="isHomeScreen" color="blue">启动首页</a-tag>
        <a-tag v-if="isPopupPage" color="purple">弹窗页</a-tag>
        <a-tag v-if="screenMeta">v{{ screenMeta.version }} · {{ screenMeta.status }}</a-tag>
      </a-space>
      <a-space>
        <a-button v-if="!isHomeScreen" @click="onSetAsHome">设为启动首页</a-button>
        <a-button @click="goRuntime">预览运行</a-button>
        <a-button type="primary" :loading="saving" @click="onSave">
          <template #icon><SaveOutlined /></template>
          保存
        </a-button>
        <a-button @click="onPublish">
          <template #icon><CloudUploadOutlined /></template>
          发布
        </a-button>
      </a-space>
    </div>

    <div class="workspace">
      <div class="left-rail">
        <div class="left-tabs" role="tablist">
          <button
            type="button"
            class="left-tab"
            role="tab"
            :aria-selected="leftTab === 'pages'"
            :class="{ active: leftTab === 'pages' }"
            @click="leftTab = 'pages'"
          >
            画面列表
          </button>
          <button
            type="button"
            class="left-tab"
            role="tab"
            :aria-selected="leftTab === 'palette'"
            :class="{ active: leftTab === 'palette' }"
            @click="leftTab = 'palette'"
          >
            图元库
          </button>
        </div>
        <div class="left-panel">
          <PageTreePanel
            v-show="leftTab === 'pages'"
            class="page-tree"
            :project-name="projectName"
            :pages="projectPages"
            :current-id="String(route.params.id || '')"
            :home-screen-id="homeScreenId"
            :compact-header="true"
            @select="switchToPage"
            @create="createPageOpen = true"
          />
          <PalettePanel v-show="leftTab === 'palette'" class="palette" :embed="true" @add="addElement" />
        </div>
      </div>

      <main class="canvas-wrap">
        <a-spin :spinning="loading" class="canvas-spin">
          <StageHost
            :document="doc"
            mode="edit"
            :selected-id="selectedId"
            @select="onSelect"
            @stage-click="onStageClick"
            @change-transform="onChangeTransform"
          />
        </a-spin>
      </main>

      <aside class="props">
        <div class="aside-title">属性</div>
        <template v-if="!selected">
          <a-form layout="vertical" size="small">
            <a-form-item label="页面用途">
              <a-select
                :value="doc.meta?.role || 'main'"
                style="width: 100%"
                :options="[
                  { value: 'main', label: '主画面（全屏）' },
                  { value: 'popup', label: '弹窗页' },
                ]"
                @change="(v) => updatePageMeta('role', v)"
              />
            </a-form-item>
            <a-form-item label="画布宽度">
              <a-input-number
                :value="doc.canvas.width"
                :min="160"
                :max="3840"
                style="width: 100%"
                @change="(v) => updateCanvas('width', v)"
              />
            </a-form-item>
            <a-form-item label="画布高度">
              <a-input-number
                :value="doc.canvas.height"
                :min="120"
                :max="2160"
                style="width: 100%"
                @change="(v) => updateCanvas('height', v)"
              />
            </a-form-item>
            <a-form-item v-if="isPopupPage" label="弹出位置">
              <a-select
                :value="doc.meta?.popupPlacement || 'center'"
                style="width: 100%"
                :options="[
                  { value: 'center', label: '居中' },
                  { value: 'topLeft', label: '左上' },
                  { value: 'topRight', label: '右上' },
                  { value: 'bottomLeft', label: '左下' },
                  { value: 'bottomRight', label: '右下' },
                ]"
                @change="(v) => updatePageMeta('popupPlacement', v)"
              />
            </a-form-item>
            <a-alert
              v-if="isPopupPage"
              type="info"
              show-icon
              message="弹窗页按上方宽高弹出；主画面按钮「弹窗打开」可指向本页"
            />
          </a-form>
        </template>
        <template v-else-if="selected">
          <a-form layout="vertical" size="small">
            <a-form-item label="类型">
              <a-input :value="selected.type" disabled />
            </a-form-item>
            <a-form-item label="位置 / 尺寸">
              <a-space wrap>
                <a-input-number
                  :value="selected.transform.x"
                  :step="1"
                  addon-before="X"
                  @change="(v) => updateSelectedTransform('x', v)"
                />
                <a-input-number
                  :value="selected.transform.y"
                  :step="1"
                  addon-before="Y"
                  @change="(v) => updateSelectedTransform('y', v)"
                />
                <a-input-number
                  :value="selected.transform.w"
                  :min="8"
                  addon-before="W"
                  @change="(v) => updateSelectedTransform('w', v)"
                />
                <a-input-number
                  :value="selected.transform.h"
                  :min="8"
                  addon-before="H"
                  @change="(v) => updateSelectedTransform('h', v)"
                />
              </a-space>
            </a-form-item>

            <a-form-item label="图层顺序">
              <div class="z-grid">
                <a-button size="small" :disabled="!canBringForward" @click="reorderSelected('forward')">
                  上一层
                </a-button>
                <a-button size="small" :disabled="!canSendBackward" @click="reorderSelected('backward')">
                  下一层
                </a-button>
                <a-button size="small" :disabled="!canBringForward" @click="reorderSelected('front')">
                  置顶
                </a-button>
                <a-button size="small" :disabled="!canSendBackward" @click="reorderSelected('back')">
                  置底
                </a-button>
              </div>
            </a-form-item>

            <a-form-item
              v-if="
                selected.type === 'text' ||
                selected.type === 'label' ||
                selected.type === 'button' ||
                selected.type === 'input' ||
                selected.type === 'numberInput' ||
                selected.type === 'alarmBar' ||
                selected.type === 'statusBadge'
              "
              :label="selected.type === 'input' || selected.type === 'numberInput' ? '当前显示' : '文本'"
            >
              <a-input
                :value="selected.props?.text"
                @change="(e) => updateSelectedProps('text', e.target.value)"
              />
            </a-form-item>

            <template v-if="selected.type === 'time'">
              <a-form-item label="时间格式">
                <a-select
                  :value="selected.props?.format || 'HH:mm:ss'"
                  style="width: 100%"
                  :options="[
                    { value: 'HH:mm:ss', label: '时:分:秒' },
                    { value: 'HH:mm', label: '时:分' },
                  ]"
                  @change="(v) => updateSelectedProps('format', v)"
                />
              </a-form-item>
              <a-form-item label="对齐">
                <a-select
                  :value="selected.props?.align || 'center'"
                  style="width: 100%"
                  :options="[
                    { value: 'left', label: '左' },
                    { value: 'center', label: '中' },
                    { value: 'right', label: '右' },
                  ]"
                  @change="(v) => updateSelectedProps('align', v)"
                />
              </a-form-item>
              <a-form-item label="字号">
                <a-input-number
                  :value="selected.style?.fontSize || 18"
                  :min="10"
                  :max="72"
                  style="width: 100%"
                  @change="(v) => updateSelectedStyle('fontSize', v)"
                />
              </a-form-item>
            </template>

            <template v-if="selected.type === 'date'">
              <a-form-item label="日期格式">
                <a-select
                  :value="selected.props?.format || 'YYYY-MM-DD'"
                  style="width: 100%"
                  :options="[
                    { value: 'YYYY-MM-DD', label: '年-月-日' },
                    { value: 'YYYY/MM/DD', label: '年/月/日' },
                    { value: 'MM-DD', label: '月-日' },
                  ]"
                  @change="(v) => updateSelectedProps('format', v)"
                />
              </a-form-item>
              <a-form-item label="对齐">
                <a-select
                  :value="selected.props?.align || 'center'"
                  style="width: 100%"
                  :options="[
                    { value: 'left', label: '左' },
                    { value: 'center', label: '中' },
                    { value: 'right', label: '右' },
                  ]"
                  @change="(v) => updateSelectedProps('align', v)"
                />
              </a-form-item>
              <a-form-item label="字号">
                <a-input-number
                  :value="selected.style?.fontSize || 16"
                  :min="10"
                  :max="72"
                  style="width: 100%"
                  @change="(v) => updateSelectedStyle('fontSize', v)"
                />
              </a-form-item>
            </template>

            <template v-if="selected.type === 'image'">
              <a-form-item label="图片地址">
                <a-input
                  :value="selected.props?.src || ''"
                  placeholder="https://… 或本地上传"
                  @change="(e) => updateSelectedProps('src', e.target.value)"
                />
              </a-form-item>
              <a-form-item label="本地上传">
                <input type="file" accept="image/*" @change="onImageFileChange" />
                <div class="field-hint">建议 &lt; 500KB，过大可能影响画面保存</div>
              </a-form-item>
              <a-form-item v-if="selected.props?.src" label="预览">
                <img class="img-preview" :src="selected.props.src" alt="preview" />
              </a-form-item>
              <a-form-item label="不透明度">
                <a-slider
                  :value="Number(selected.style?.opacity ?? 1)"
                  :min="0.1"
                  :max="1"
                  :step="0.05"
                  @change="(v) => updateSelectedStyle('opacity', v)"
                />
              </a-form-item>
              <a-button
                v-if="selected.props?.src"
                block
                style="margin-bottom: 8px"
                @click="updateSelectedProps('src', '')"
              >
                清除图片
              </a-button>
            </template>

            <template v-if="selected.type === 'button'">
              <a-form-item label="形状">
                <a-select
                  :value="selected.props?.shape || 'rect'"
                  style="width: 100%"
                  :options="[
                    { value: 'rect', label: '圆角矩形' },
                    { value: 'rounded', label: '胶囊形' },
                    { value: 'round', label: '圆形' },
                  ]"
                  @change="(v) => updateSelectedProps('shape', v)"
                />
              </a-form-item>
              <a-form-item label="字号">
                <a-input-number
                  :value="selected.style?.fontSize || 14"
                  :min="10"
                  :max="48"
                  style="width: 100%"
                  @change="(v) => updateSelectedStyle('fontSize', v)"
                />
              </a-form-item>
              <a-form-item label="点击动作">
                <a-select
                  :value="selected.props?.clickAction || 'write'"
                  style="width: 100%"
                  :options="[
                    { value: 'write', label: '写值（绑定点位）' },
                    { value: 'navigate', label: '跳转同工程画面' },
                    { value: 'popup', label: '弹窗打开画面' },
                  ]"
                  @change="(v) => updateSelectedProps('clickAction', v)"
                />
              </a-form-item>
              <a-form-item
                v-if="!selected.props?.clickAction || selected.props?.clickAction === 'write'"
                label="写入值"
              >
                <a-input
                  :value="String(selected.props?.writeValue ?? '')"
                  placeholder="例如 1 / 0"
                  @change="(e) => updateSelectedProps('writeValue', coerceWriteValue(e.target.value))"
                />
              </a-form-item>
              <a-form-item
                v-if="
                  selected.props?.clickAction === 'navigate' ||
                  selected.props?.clickAction === 'popup'
                "
                label="目标画面"
              >
                <a-select
                  :value="selected.props?.targetScreenId || undefined"
                  allow-clear
                  show-search
                  option-filter-prop="label"
                  placeholder="选择同工程画面"
                  style="width: 100%"
                  :options="
                    projectPages
                      .filter((p) => p.id !== route.params.id)
                      .map((p) => ({
                        value: p.id,
                        label: p.pageRole === 'popup' ? `${p.name}（弹窗）` : p.name,
                      }))
                  "
                  @change="(v) => updateSelectedProps('targetScreenId', v || '')"
                />
              </a-form-item>
            </template>

            <template v-if="selected.type === 'switch' || selected.type === 'checkbox'">
              <a-form-item label="开写入值">
                <a-input-number
                  :value="selected.props?.onValue ?? 1"
                  style="width: 100%"
                  @change="(v) => updateSelectedProps('onValue', v)"
                />
              </a-form-item>
              <a-form-item label="关写入值">
                <a-input-number
                  :value="selected.props?.offValue ?? 0"
                  style="width: 100%"
                  @change="(v) => updateSelectedProps('offValue', v)"
                />
              </a-form-item>
              <a-form-item label="开颜色">
                <div class="color-row">
                  <input
                    class="color-picker"
                    type="color"
                    :value="normalizeHex(selected.style?.fillOn) || '#52c41a'"
                    @input="(e) => updateSelectedStyle('fillOn', e.target.value)"
                  />
                  <a-input
                    :value="selected.style?.fillOn || ''"
                    placeholder="#52c41a"
                    @change="(e) => updateSelectedStyle('fillOn', e.target.value)"
                  />
                </div>
              </a-form-item>
              <a-form-item label="关颜色">
                <div class="color-row">
                  <input
                    class="color-picker"
                    type="color"
                    :value="normalizeHex(selected.style?.fillOff) || '#6b7280'"
                    @input="(e) => updateSelectedStyle('fillOff', e.target.value)"
                  />
                  <a-input
                    :value="selected.style?.fillOff || ''"
                    placeholder="#6b7280"
                    @change="(e) => updateSelectedStyle('fillOff', e.target.value)"
                  />
                </div>
              </a-form-item>
            </template>

            <a-form-item
              v-if="
                selected.type === 'progress' ||
                selected.type === 'gauge' ||
                selected.type === 'levelBar' ||
                selected.type === 'slider'
              "
              label="当前值"
            >
              <a-input-number
                :value="selected.props?.value"
                style="width: 100%"
                @change="(v) => updateSelectedProps('value', v)"
              />
            </a-form-item>

            <template v-if="selected.type === 'slider'">
              <a-form-item label="方向">
                <a-select
                  :value="selected.props?.orient || 'h'"
                  style="width: 100%"
                  :options="[
                    { value: 'h', label: '水平' },
                    { value: 'v', label: '垂直' },
                  ]"
                  @change="onSliderOrientChange"
                />
              </a-form-item>
              <a-form-item label="最小值">
                <a-input-number
                  :value="selected.props?.min ?? 0"
                  style="width: 100%"
                  @change="(v) => updateSelectedProps('min', v)"
                />
              </a-form-item>
              <a-form-item label="最大值">
                <a-input-number
                  :value="selected.props?.max ?? 100"
                  style="width: 100%"
                  @change="(v) => updateSelectedProps('max', v)"
                />
              </a-form-item>
            </template>

            <a-form-item
              v-if="selected.type === 'input' || selected.type === 'numberInput'"
              label="占位提示"
            >
              <a-input
                :value="selected.props?.placeholder"
                @change="(e) => updateSelectedProps('placeholder', e.target.value)"
              />
            </a-form-item>

            <a-form-item v-if="selected.type === 'symbol'" label="符号">
              <a-select
                :value="selected.props?.symbolId || 'pump'"
                style="width: 100%"
                :options="symbols.map((s) => ({ value: s.id, label: s.label }))"
                @change="(v) => updateSelectedProps('symbolId', v)"
              />
            </a-form-item>

            <template v-if="selected.type === 'led'">
              <a-form-item label="形状">
                <a-select
                  :value="selected.props?.shape || 'circle'"
                  style="width: 100%"
                  :options="[
                    { value: 'circle', label: '圆形' },
                    { value: 'square', label: '方形' },
                    { value: 'rounded', label: '圆角方' },
                  ]"
                  @change="(v) => updateSelectedProps('shape', v)"
                />
              </a-form-item>
            </template>

            <template
              v-if="
                selected.type !== 'switch' &&
                selected.type !== 'checkbox' &&
                selected.type !== 'image'
              "
            >
              <a-form-item
                :label="
                  selected.type === 'text' ||
                  selected.type === 'label' ||
                  selected.type === 'time' ||
                  selected.type === 'date'
                    ? '文字色'
                    : '填充色'
                "
              >
                <div class="color-row">
                  <input
                    class="color-picker"
                    type="color"
                    :value="normalizeHex(selected.style?.fill) || '#1677ff'"
                    @input="(e) => updateSelectedStyle('fill', e.target.value)"
                  />
                  <a-input
                    :value="selected.style?.fill || ''"
                    placeholder="#1677ff"
                    @change="(e) => updateSelectedStyle('fill', e.target.value)"
                  />
                </div>
              </a-form-item>
              <a-form-item
                v-if="
                  selected.type !== 'text' &&
                  selected.type !== 'label' &&
                  selected.type !== 'time' &&
                  selected.type !== 'date'
                "
                label="描边色"
              >
                <div class="color-row">
                  <input
                    class="color-picker"
                    type="color"
                    :value="normalizeHex(selected.style?.stroke) || '#0958d9'"
                    @input="(e) => updateSelectedStyle('stroke', e.target.value)"
                  />
                  <a-input
                    :value="selected.style?.stroke || ''"
                    placeholder="#0958d9"
                    @change="(e) => updateSelectedStyle('stroke', e.target.value)"
                  />
                </div>
              </a-form-item>
            </template>
            <a-form-item
              v-if="
                selected.type === 'button' ||
                selected.type === 'statusBadge' ||
                selected.type === 'alarmBar' ||
                selected.type === 'input' ||
                selected.type === 'numberInput'
              "
              label="文字色"
            >
              <div class="color-row">
                <input
                  class="color-picker"
                  type="color"
                  :value="normalizeHex(selected.style?.textColor) || '#ffffff'"
                  @input="(e) => updateSelectedStyle('textColor', e.target.value)"
                />
                <a-input
                  :value="selected.style?.textColor || ''"
                  placeholder="#ffffff"
                  @change="(e) => updateSelectedStyle('textColor', e.target.value)"
                />
              </div>
            </a-form-item>

            <template
              v-if="
                selected.type !== 'time' &&
                selected.type !== 'date' &&
                selected.type !== 'image' &&
                !(
                  selected.type === 'button' &&
                  (selected.props?.clickAction === 'navigate' ||
                    selected.props?.clickAction === 'popup')
                )
              "
            >
              <a-divider>点位绑定</a-divider>
              <a-alert
                v-if="!gateways.length"
                type="warning"
                show-icon
                style="margin-bottom: 12px"
                message="暂无网关，请先在「设备与点表」登记边端"
              />
              <a-tag :color="bindingReady || selected.binding ? 'success' : 'default'" style="margin-bottom: 8px">
                {{
                  selected.binding
                    ? `已绑：${selected.binding.pointId}`
                    : bindingReady
                      ? '待写入绑定'
                      : '请依次选择 网关 → 设备 → 点'
                }}
              </a-tag>

              <a-form-item label="网关">
                <a-select
                  :value="bindForm.gatewayId || undefined"
                  allow-clear
                  show-search
                  option-filter-prop="label"
                  placeholder="选择网关"
                  style="width: 100%"
                  :options="gateways.map((g) => ({ value: g.id, label: `${g.name || g.id} (${g.id})` }))"
                  @change="onGatewayChange"
                />
              </a-form-item>
              <a-form-item label="设备">
                <a-select
                  :value="bindForm.deviceId || undefined"
                  allow-clear
                  show-search
                  option-filter-prop="label"
                  placeholder="先选网关"
                  style="width: 100%"
                  :disabled="!bindForm.gatewayId"
                  :options="devices.map((d) => ({ value: d.id, label: d.name || d.id }))"
                  @change="onDeviceChange"
                />
              </a-form-item>
              <a-form-item label="点位">
                <a-select
                  :value="bindForm.pointId || undefined"
                  allow-clear
                  show-search
                  option-filter-prop="label"
                  placeholder="先选设备"
                  style="width: 100%"
                  :disabled="!bindForm.deviceId"
                  :options="
                    points.map((p) => ({
                      value: p.id,
                      label: `${p.name || p.id}${p.dataType ? ' · ' + p.dataType : ''}`,
                    }))
                  "
                  @change="onPointChange"
                />
              </a-form-item>
              <a-form-item label="绑定类型">
                <a-select
                  :value="bindForm.kind"
                  style="width: 100%"
                  :options="[
                    { value: 'text', label: '数值显示' },
                    { value: 'color', label: '颜色' },
                    { value: 'visible', label: '可见' },
                    { value: 'clickWrite', label: '点击写值' },
                  ]"
                  @change="onKindChange"
                />
              </a-form-item>
              <a-form-item v-if="bindForm.kind === 'clickWrite' && selected.type === 'button'" label="写入值">
                <a-input
                  v-model:value="bindForm.writeValue"
                  placeholder="例如 1"
                  @blur="() => bindingReady && applyBindingFromForm()"
                />
              </a-form-item>
              <a-button
                v-if="bindingReady && !selected.binding"
                type="primary"
                block
                style="margin-bottom: 8px"
                @click="applyBindingFromForm"
              >
                确认绑定
              </a-button>
              <a-button v-if="selected.binding" block style="margin-bottom: 8px" @click="clearBinding">
                清除绑定
              </a-button>
            </template>

            <a-button danger block @click="removeSelected">删除图元</a-button>
          </a-form>
        </template>
      </aside>
    </div>

    <a-modal
      v-model:open="createPageOpen"
      title="新建页面"
      ok-text="创建"
      @ok="onCreatePageInProject"
    >
      <p class="modal-hint">所属工程：{{ projectName }}</p>
      <a-form layout="vertical">
        <a-form-item label="页面名称">
          <a-input
            v-model:value="newPageName"
            placeholder="如：工艺段 / 报警详情"
            @pressEnter="onCreatePageInProject"
          />
        </a-form-item>
        <a-form-item label="页面用途">
          <a-radio-group v-model:value="newPageRole">
            <a-radio value="main">主画面</a-radio>
            <a-radio value="popup">弹窗页</a-radio>
          </a-radio-group>
        </a-form-item>
        <p v-if="newPageRole === 'popup'" class="modal-hint">
          弹窗页默认 {{ POPUP_DEFAULT_SIZE.width }}×{{ POPUP_DEFAULT_SIZE.height }}，可在属性里改尺寸与弹出位置。
        </p>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.editor-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 64px);
  min-height: 560px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
}
.proj-label {
  font-weight: 600;
  color: rgba(0, 0, 0, 0.65);
}
.workspace {
  flex: 1;
  display: grid;
  grid-template-columns: 248px 1fr 300px;
  min-height: 0;
}
.left-rail {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid #2a3444;
  background: #12171e;
}
.left-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  flex-shrink: 0;
  border-bottom: 1px solid #2a3444;
  background: #151b24;
}
.left-tab {
  appearance: none;
  border: 0;
  background: transparent;
  color: #8b9bb0;
  font-size: 13px;
  font-weight: 600;
  padding: 10px 8px;
  cursor: pointer;
  position: relative;
}
.left-tab:hover {
  color: #e7eef7;
  background: rgba(56, 189, 248, 0.06);
}
.left-tab.active {
  color: #e7eef7;
}
.left-tab.active::after {
  content: '';
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 0;
  height: 2px;
  border-radius: 2px 2px 0 0;
  background: #38bdf8;
}
.left-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.page-tree,
.palette {
  flex: 1;
  min-height: 0;
  height: 100%;
  border-right: none !important;
}
.modal-hint {
  margin: 0 0 10px;
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}
.field-hint {
  margin-top: 6px;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}
.img-preview {
  display: block;
  max-width: 100%;
  max-height: 120px;
  object-fit: contain;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fafafa;
}
.z-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}
.props {
  background: #fff;
  border-left: 1px solid #f0f0f0;
  padding: 12px;
  overflow: auto;
}
.color-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.color-picker {
  width: 36px;
  height: 32px;
  padding: 0;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  flex-shrink: 0;
}
.aside-title {
  font-weight: 600;
  margin-bottom: 10px;
}
.canvas-wrap {
  min-width: 0;
  min-height: 0;
  padding: 12px;
  background: #f5f6f8;
}
.canvas-spin {
  display: block;
  height: 100%;
}
.canvas-spin :deep(.ant-spin-container) {
  height: 100%;
}
</style>
