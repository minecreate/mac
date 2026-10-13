<script setup>
import { computed, h, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Input, InputNumber, message, Modal } from 'ant-design-vue'
import { ArrowLeftOutlined, CopyOutlined, ExpandOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import StageHost from '../../hmi/canvas/StageHost.vue'
import { collectPointKeys, parseDocument, POPUP_DEFAULT_SIZE } from '../../hmi/model/types'
import {
  applyBindingToElement,
  indexReadings,
  resolveWriteValue,
} from '../../hmi/runtime/applyBindings'
import { getHmiScreen, listHmiScreens } from '../../api/hmi'
import { readDevice, writePoint } from '../../api/asset'
import { releaseLock, tryLock } from '../../api/execution'

const POLL_MS = 2000
const DEFAULT_PROJECT_ID = 'hmi-proj-default'
const HMI_OWNER_ID = `hmi-${Math.random().toString(36).slice(2, 8)}`

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const screenMeta = ref(null)
const doc = ref(null)
const projectPages = ref([])
const popupOpen = ref(false)
const popupTitle = ref('')
const popupDoc = ref(null)
const popupLoading = ref(false)
/** @type {import('vue').Ref<Map<string, any>>} */
const readingMap = ref(new Map())
let timer = null

const pageOptions = computed(() =>
  projectPages.value.map((p) => ({ value: p.id, label: p.name })),
)

const popupVisualElements = computed(() => {
  if (!popupDoc.value) return []
  return popupDoc.value.elements.map((el) => applyBindingToElement(el, readingMap.value))
})

const popupWidth = computed(() => {
  const w = Number(popupDoc.value?.canvas?.width) || POPUP_DEFAULT_SIZE.width
  return Math.min(Math.max(w + 48, 280), typeof window !== 'undefined' ? window.innerWidth - 32 : w + 48)
})

const popupPlacement = computed(() => popupDoc.value?.meta?.popupPlacement || 'center')

const popupWrapClass = computed(() => `hmi-popup-wrap hmi-popup--${popupPlacement.value}`)

const popupBodyStyle = computed(() => {
  const h = Number(popupDoc.value?.canvas?.height) || POPUP_DEFAULT_SIZE.height
  return {
    padding: '12px',
    maxHeight: `min(${h + 24}px, calc(100vh - 120px))`,
    overflow: 'auto',
  }
})

const popupCanvasStyle = computed(() => {
  const w = Number(popupDoc.value?.canvas?.width) || POPUP_DEFAULT_SIZE.width
  const h = Number(popupDoc.value?.canvas?.height) || POPUP_DEFAULT_SIZE.height
  return {
    width: `${w}px`,
    height: `${h}px`,
    maxWidth: '100%',
    margin: '0 auto',
  }
})

const visualElements = computed(() => {
  if (!doc.value) return []
  return doc.value.elements.map((el) => applyBindingToElement(el, readingMap.value))
})

const pointKeys = computed(() => (doc.value ? collectPointKeys(doc.value) : []))

async function loadProjectPages(projectId) {
  try {
    projectPages.value = await listHmiScreens({ projectId: projectId || DEFAULT_PROJECT_ID })
  } catch {
    projectPages.value = []
  }
}

async function loadScreen() {
  loading.value = true
  try {
    const row = await getHmiScreen(route.params.id)
    screenMeta.value = row
    doc.value = parseDocument(row.document)
    readingMap.value = new Map()
    await loadProjectPages(row.projectId)
  } catch (e) {
    message.error(e.message || '加载画面失败')
  } finally {
    loading.value = false
  }
}

function switchPage(id) {
  if (!id || id === route.params.id) return
  closePopup()
  router.push(`/hmi/screens/${id}/runtime`)
}

async function openPopupScreen(screenId) {
  if (!screenId) {
    message.warning('未配置目标画面')
    return
  }
  popupLoading.value = true
  popupOpen.value = true
  try {
    const row = await getHmiScreen(screenId)
    popupTitle.value = row.name || '弹窗画面'
    popupDoc.value = parseDocument(row.document)
  } catch (e) {
    popupOpen.value = false
    message.error(e.message || '加载弹窗画面失败')
  } finally {
    popupLoading.value = false
  }
}

function closePopup() {
  popupOpen.value = false
  popupDoc.value = null
  popupTitle.value = ''
}

async function pollReadings() {
  if (!doc.value) return
  const groups = new Map()
  for (const pk of pointKeys.value) {
    const gk = `${pk.gatewayId}|${pk.deviceId}`
    if (!groups.has(gk)) groups.set(gk, pk)
  }
  const next = new Map(readingMap.value)
  await Promise.all(
    [...groups.values()].map(async ({ gatewayId, deviceId }) => {
      try {
        const items = await readDevice(gatewayId, deviceId)
        const partial = indexReadings(gatewayId, deviceId, items)
        for (const [k, v] of partial) next.set(k, v)
      } catch {
        // keep last values on transient errors
      }
    }),
  )
  readingMap.value = next
}

function startPoll() {
  stopPoll()
  pollReadings()
  timer = setInterval(pollReadings, POLL_MS)
}

function stopPoll() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

onMounted(async () => {
  await loadScreen()
  startPoll()
})

watch(
  () => route.params.id,
  async (id, prev) => {
    if (!id || id === prev) return
    stopPoll()
    await loadScreen()
    startPoll()
  },
)

onUnmounted(stopPoll)

function currentValueFor(el) {
  const b = el.binding
  if (!b) return undefined
  return readingMap.value.get(`${b.gatewayId}|${b.deviceId}|${b.pointId}`)?.value
}

async function writeWithStationLock(gatewayId, deviceId, pointId, value) {
  let lockedKey = null
  try {
    const lockRes = await tryLock({
      gatewayId,
      deviceId,
      ownerType: 'hmi',
      ownerId: HMI_OWNER_ID,
      priority: 0,
      ttlSec: 15,
    })
    lockedKey = lockRes?.lock?.resourceKey || null
  } catch (e) {
    if (e.status === 409) {
      throw new Error(e.data?.reason || e.message || '工位被生产订单占用，暂不可写')
    }
    // 未绑定工位时放行写值，避免组态联调被卡住
    if (e.status !== 404) {
      // ignore other soft failures and still try write
    }
  }
  try {
    await writePoint(gatewayId, deviceId, pointId, value)
  } finally {
    if (lockedKey) {
      try {
        await releaseLock({ resourceKey: lockedKey, ownerType: 'hmi', ownerId: HMI_OWNER_ID })
      } catch {
        // ignore
      }
    }
  }
}

function onElementClick(el) {
  const action = el.props?.clickAction || 'write'
  if (el.type === 'button' && (action === 'navigate' || action === 'popup')) {
    const targetId = el.props?.targetScreenId
    if (!targetId) {
      message.warning('请先在编辑器配置目标画面')
      return
    }
    if (action === 'navigate') {
      switchPage(targetId)
      return
    }
    openPopupScreen(targetId)
    return
  }

  const b = el.binding
  if (!b?.gatewayId || !b?.deviceId || !b?.pointId) return
  const isInput = el.type === 'input' || el.type === 'numberInput' || el.type === 'slider'
  if (
    b.kind !== 'clickWrite' &&
    el.type !== 'button' &&
    el.type !== 'switch' &&
    el.type !== 'checkbox' &&
    !isInput
  )
    return

  const resolved = resolveWriteValue(el, currentValueFor(el))
  if (resolved === null) return

  if (resolved && typeof resolved === 'object' && resolved.prompt) {
    const draft = ref(
      resolved.mode === 'number' && resolved.seed !== '' && resolved.seed != null
        ? Number(resolved.seed)
        : String(resolved.seed ?? ''),
    )
    Modal.confirm({
      title: resolved.mode === 'number' ? '写入数值' : '写入文本',
      content: () =>
        h('div', { style: 'margin-top: 8px' }, [
          h('div', { style: 'margin-bottom: 8px; color: rgba(0,0,0,0.45)' }, `${b.deviceId} / ${b.pointId}`),
          resolved.mode === 'number'
            ? h(InputNumber, {
                value: draft.value,
                style: 'width: 100%',
                'onUpdate:value': (v) => {
                  draft.value = v
                },
              })
            : h(Input, {
                value: draft.value,
                'onUpdate:value': (v) => {
                  draft.value = v
                },
              }),
        ]),
      async onOk() {
        let writeValue = draft.value
        if (resolved.mode === 'number') {
          if (writeValue === null || writeValue === undefined || writeValue === '') {
            message.warning('请输入数值')
            return Promise.reject()
          }
          writeValue = Number(writeValue)
          if (Number.isNaN(writeValue)) {
            message.warning('数值无效')
            return Promise.reject()
          }
        } else {
          writeValue = String(writeValue ?? '')
        }
        try {
          await writeWithStationLock(b.gatewayId, b.deviceId, b.pointId, writeValue)
          message.success('写入已下发')
          await pollReadings()
        } catch (e) {
          message.error(e.message || '写值失败')
          throw e
        }
      },
    })
    return
  }

  Modal.confirm({
    title: '确认写值',
    content: `向 ${b.deviceId} / ${b.pointId} 写入：${resolved}`,
    async onOk() {
      try {
        await writeWithStationLock(b.gatewayId, b.deviceId, b.pointId, resolved)
        message.success('写入已下发')
        await pollReadings()
      } catch (e) {
        message.error(e.message || '写值失败')
        throw e
      }
    },
  })
}

const fromHome = computed(() => route.query.from === 'home')

function goBack() {
  router.push(fromHome.value ? '/home' : '/hmi/screens')
}

function goEdit() {
  router.push(`/hmi/screens/${route.params.id}/edit`)
}

async function copyShareLink() {
  const url = `${window.location.origin}/hmi/screens/${route.params.id}/runtime`
  try {
    await navigator.clipboard.writeText(url)
    message.success('运行链接已复制')
  } catch {
    message.info(url)
  }
}

async function enterBrowserFullscreen() {
  const el = document.querySelector('.runtime-page')
  if (!el?.requestFullscreen) {
    message.warning('当前浏览器不支持全屏')
    return
  }
  try {
    await el.requestFullscreen()
  } catch (e) {
    message.error(e?.message || '无法进入全屏')
  }
}

watch(
  () => route.query.fullscreen,
  async (v) => {
    if (v === '1') {
      await enterBrowserFullscreen()
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="runtime-page">
    <div class="toolbar">
      <a-space>
        <a-button @click="goBack">
          <template #icon><ArrowLeftOutlined /></template>
          {{ fromHome ? '返回总览' : '工程列表' }}
        </a-button>
        <span class="title">{{ screenMeta?.name || '运行态' }}</span>
        <a-select
          v-if="pageOptions.length > 1"
          :value="String(route.params.id)"
          style="width: 180px"
          :options="pageOptions"
          placeholder="切换画面"
          @change="switchPage"
        />
        <a-tag v-if="screenMeta" :color="screenMeta.status === 'published' ? 'success' : 'orange'">
          {{ screenMeta.status === 'published' ? '已发布' : '草稿预览' }}
        </a-tag>
        <a-tag>订阅点 {{ pointKeys.length }} · 轮询 {{ POLL_MS / 1000 }}s</a-tag>
      </a-space>
      <a-space>
        <a-button @click="pollReadings">
          <template #icon><ReloadOutlined /></template>
          立即刷新
        </a-button>
        <a-button @click="copyShareLink">
          <template #icon><CopyOutlined /></template>
          分享链接
        </a-button>
        <a-button @click="enterBrowserFullscreen">
          <template #icon><ExpandOutlined /></template>
          全屏
        </a-button>
        <a-button type="primary" ghost @click="goEdit">编辑画面</a-button>
      </a-space>
    </div>

    <div class="canvas-wrap" v-if="doc">
      <StageHost
        :document="doc"
        mode="runtime"
        :visual-elements="visualElements"
        @element-click="onElementClick"
      />
    </div>
    <a-spin v-else :spinning="loading" class="spin" />

    <a-modal
      :open="popupOpen"
      :title="popupTitle"
      :footer="null"
      :width="popupWidth"
      :centered="popupPlacement === 'center'"
      :wrap-class-name="popupWrapClass"
      :body-style="popupBodyStyle"
      destroy-on-close
      @cancel="closePopup"
    >
      <a-spin :spinning="popupLoading">
        <div v-if="popupDoc" class="popup-canvas" :style="popupCanvasStyle">
          <StageHost
            :document="popupDoc"
            mode="runtime"
            :visual-elements="popupVisualElements"
            @element-click="onElementClick"
          />
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<style>
/* Modal 挂到 body，需非 scoped */
.hmi-popup-wrap.hmi-popup--topLeft .ant-modal {
  top: 24px !important;
  left: 24px;
  margin: 0;
  padding-bottom: 0;
}
.hmi-popup-wrap.hmi-popup--topRight .ant-modal {
  top: 24px !important;
  right: 24px;
  left: auto;
  margin: 0 24px 0 auto;
  padding-bottom: 0;
}
.hmi-popup-wrap.hmi-popup--bottomLeft .ant-modal {
  top: auto !important;
  bottom: 24px;
  left: 24px;
  margin: 0;
  padding-bottom: 0;
}
.hmi-popup-wrap.hmi-popup--bottomRight .ant-modal {
  top: auto !important;
  bottom: 24px;
  right: 24px;
  left: auto;
  margin: 0 24px 0 auto;
  padding-bottom: 0;
}
.hmi-popup-wrap.hmi-popup--center .ant-modal {
  padding-bottom: 0;
}
</style>

<style scoped>
.runtime-page {
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
.title {
  font-weight: 600;
  font-size: 16px;
}
.canvas-wrap {
  flex: 1;
  min-height: 0;
  padding: 12px;
  background: #f5f6f8;
}
.spin {
  margin: 80px auto;
  display: block;
}
.popup-canvas {
  overflow: hidden;
  background: #0f141b;
  border-radius: 6px;
}
</style>
