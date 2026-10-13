<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { getSymbol } from '../symbols/registry'
import HmiImageShape from './HmiImageShape.vue'

const props = defineProps({
  document: { type: Object, required: true },
  mode: { type: String, default: 'edit' },
  selectedId: { type: String, default: null },
  visualElements: { type: Array, default: null },
})

const emit = defineEmits(['select', 'change-transform', 'element-click', 'stage-click'])

const stageRef = ref(null)
const transformerRef = ref(null)
const containerRef = ref(null)
const stageSize = ref({ width: 800, height: 480 })
/** 驱动时间/日期控件刷新 */
const clockNow = ref(Date.now())
let clockTimer = null

const isEdit = computed(() => props.mode === 'edit')

const canvas = computed(() => props.document?.canvas || { width: 1920, height: 1080, background: '#1a1d24' })

const layerVisibility = computed(() => {
  const map = new Map()
  for (const l of props.document?.layers || []) {
    map.set(l.id, l.visible !== false)
  }
  return map
})

const elements = computed(() => {
  const source = props.visualElements || props.document?.elements || []
  return source.filter((el) => layerVisibility.value.get(el.layerId) !== false && el.props?.runtimeVisible !== false)
})

const hasClockWidgets = computed(() =>
  elements.value.some((el) => el.type === 'time' || el.type === 'date'),
)

function fitStage() {
  const el = containerRef.value
  if (!el) return
  stageSize.value = { width: el.clientWidth || 800, height: el.clientHeight || 480 }
}

function startClock() {
  stopClock()
  clockNow.value = Date.now()
  clockTimer = setInterval(() => {
    clockNow.value = Date.now()
  }, 1000)
}

function stopClock() {
  if (clockTimer) {
    clearInterval(clockTimer)
    clockTimer = null
  }
}

onMounted(() => {
  fitStage()
  window.addEventListener('resize', fitStage)
  if (hasClockWidgets.value) startClock()
})

onUnmounted(() => {
  window.removeEventListener('resize', fitStage)
  stopClock()
})

watch(hasClockWidgets, (need) => {
  if (need) startClock()
  else stopClock()
})

watch(
  () => props.selectedId,
  async () => {
    if (!isEdit.value) return
    await nextTick()
    syncTransformer()
  },
)

watch(
  () => elements.value.map((e) => e.id).join(','),
  async () => {
    await nextTick()
    syncTransformer()
  },
)

function syncTransformer() {
  const tr = transformerRef.value?.getNode?.()
  const stage = stageRef.value?.getNode?.()
  if (!tr || !stage) return
  if (!props.selectedId || !isEdit.value) {
    tr.nodes([])
    tr.getLayer()?.batchDraw()
    return
  }
  const node = stage.findOne(`#node-${props.selectedId}`)
  if (node) {
    tr.nodes([node])
    tr.getLayer()?.batchDraw()
  } else {
    tr.nodes([])
    tr.getLayer()?.batchDraw()
  }
}

function onStageClick(e) {
  if (e.target === e.target.getStage()) {
    emit('stage-click')
    emit('select', null)
  }
}

function onElementClick(el, e) {
  e.cancelBubble = true
  if (isEdit.value) emit('select', el.id)
  else emit('element-click', el)
}

function onDragEnd(el, e) {
  if (!isEdit.value) return
  const node = e.target
  emit('change-transform', {
    id: el.id,
    transform: { ...el.transform, x: node.x(), y: node.y() },
  })
}

function onTransformEnd(el, e) {
  if (!isEdit.value) return
  const node = e.target
  const scaleX = node.scaleX()
  const scaleY = node.scaleY()
  node.scaleX(1)
  node.scaleY(1)
  emit('change-transform', {
    id: el.id,
    transform: {
      x: node.x(),
      y: node.y(),
      w: Math.max(8, (el.transform.w || 40) * scaleX),
      h: Math.max(8, (el.transform.h || 40) * scaleY),
      rotation: node.rotation(),
    },
  })
}

function groupConfig(el) {
  const t = el.transform || {}
  return {
    id: `node-${el.id}`,
    x: t.x || 0,
    y: t.y || 0,
    width: t.w || 40,
    height: t.h || 40,
    rotation: t.rotation || 0,
    draggable: isEdit.value,
  }
}

function textOf(el) {
  return el.props?.text != null ? String(el.props.text) : ''
}

function pad2(n) {
  return String(n).padStart(2, '0')
}

/** 简易格式：YYYY MM DD HH mm ss */
function formatClock(el) {
  // 依赖 clockNow 以触发重渲染
  const d = new Date(clockNow.value)
  const map = {
    YYYY: String(d.getFullYear()),
    MM: pad2(d.getMonth() + 1),
    DD: pad2(d.getDate()),
    HH: pad2(d.getHours()),
    mm: pad2(d.getMinutes()),
    ss: pad2(d.getSeconds()),
  }
  const fallback = el.type === 'date' ? 'YYYY-MM-DD' : 'HH:mm:ss'
  const fmt = String(el.props?.format || fallback)
  return fmt.replace(/YYYY|MM|DD|HH|mm|ss/g, (token) => map[token] || token)
}

function symbolKind(el) {
  return getSymbol(el.props?.symbolId || 'pump').kind
}

function ratioOf(el) {
  const max = Number(el.props?.max ?? 100) || 100
  const min = Number(el.props?.min ?? 0) || 0
  const v = Number(el.props?.value ?? 0)
  if (Number.isNaN(v)) return 0
  return Math.max(0, Math.min(1, (v - min) / (max - min || 1)))
}

function progressWidth(el) {
  return Math.max(0, (el.transform.w || 0) * ratioOf(el))
}

function levelFillH(el) {
  return Math.max(0, (el.transform.h || 0) * ratioOf(el))
}

function levelFillY(el) {
  return (el.transform.h || 0) - levelFillH(el)
}

function sliderThumbX(el) {
  const r = ratioOf(el)
  const w = el.transform.w || 0
  const pad = 8
  return pad + r * Math.max(0, w - pad * 2)
}

function sliderThumbY(el) {
  const r = ratioOf(el)
  const h = el.transform.h || 0
  const pad = 8
  return h - pad - r * Math.max(0, h - pad * 2)
}

function isRoundButton(el) {
  return el.props?.shape === 'round' || el.props?.shape === 'circle'
}

function isVerticalSlider(el) {
  return el.props?.orient === 'v' || el.props?.orient === 'vertical'
}

/** 半圆仪表弧线点 */
function gaugeArcPoints(el, t) {
  const w = el.transform.w || 100
  const h = el.transform.h || 72
  const cx = w / 2
  const cy = h * 0.85
  const r = Math.min(w, h) * 0.42
  const start = Math.PI
  const end = Math.PI + Math.PI * t
  const pts = []
  const steps = 24
  for (let i = 0; i <= steps; i++) {
    const a = start + ((end - start) * i) / steps
    pts.push(cx + Math.cos(a) * r, cy + Math.sin(a) * r)
  }
  return pts
}

function gaugeNeedle(el) {
  const w = el.transform.w || 100
  const h = el.transform.h || 72
  const cx = w / 2
  const cy = h * 0.85
  const r = Math.min(w, h) * 0.38
  const a = Math.PI + Math.PI * ratioOf(el)
  return [cx, cy, cx + Math.cos(a) * r, cy + Math.sin(a) * r]
}

function trianglePoints(el) {
  const w = el.transform.w || 64
  const h = el.transform.h || 56
  return [w / 2, 2, w - 2, h - 2, 2, h - 2]
}

function pipePoints(el) {
  if (Array.isArray(el.props?.points) && el.props.points.length >= 4) return el.props.points
  const w = el.transform.w || 160
  const h = el.transform.h || 8
  const shape = el.props?.shape || 'h'
  if (shape === 'v') return [w / 2, 0, w / 2, h]
  if (shape === 'elbow') return [0, 4, Math.min(w - 4, 48), 4, Math.min(w - 4, 48), Math.max(h - 4, 8)]
  if (shape === 'tee') {
    const midY = h / 2
    const midX = w / 2
    return [0, midY, midX, midY, midX, 0, midX, midY, w, midY]
  }
  return [0, h / 2, w, h / 2]
}
</script>

<template>
  <div ref="containerRef" class="stage-host">
    <v-stage
      ref="stageRef"
      :config="{ width: stageSize.width, height: stageSize.height }"
      @mousedown="onStageClick"
      @touchstart="onStageClick"
    >
      <v-layer>
        <v-rect
          :config="{
            x: 0,
            y: 0,
            width: canvas.width,
            height: canvas.height,
            fill: canvas.background || '#1a1d24',
            listening: false,
          }"
        />

        <v-group
          v-for="el in elements"
          :key="el.id"
          :config="groupConfig(el)"
          @click="(e) => onElementClick(el, e)"
          @tap="(e) => onElementClick(el, e)"
          @dragend="(e) => onDragEnd(el, e)"
          @transformend="(e) => onTransformEnd(el, e)"
        >
          <template v-if="el.type === 'text' || el.type === 'label'">
            <v-text
              :config="{
                text: textOf(el),
                width: el.transform.w,
                height: el.transform.h,
                fill: el.style?.fill || '#e8eaed',
                fontSize: el.style?.fontSize || 16,
                align: el.props?.align || 'left',
                verticalAlign: 'middle',
              }"
            />
          </template>

          <template v-else-if="el.type === 'time' || el.type === 'date'">
            <v-text
              :config="{
                text: formatClock(el),
                width: el.transform.w,
                height: el.transform.h,
                fill: el.style?.fill || '#e8eaed',
                fontSize: el.style?.fontSize || (el.type === 'time' ? 18 : 16),
                align: el.props?.align || 'center',
                verticalAlign: 'middle',
              }"
            />
          </template>

          <template v-else-if="el.type === 'image'">
            <HmiImageShape
              :src="String(el.props?.src || '')"
              :width="el.transform.w"
              :height="el.transform.h"
              :opacity="Number(el.style?.opacity ?? 1)"
              :stroke="el.style?.stroke || ''"
              :stroke-width="Number(el.style?.strokeWidth ?? 0)"
            />
          </template>

          <template v-else-if="el.type === 'rect'">
            <v-rect
              :config="{
                width: el.transform.w,
                height: el.transform.h,
                fill: el.style?.fill || '#3a3f4b',
                stroke: el.style?.stroke || '#6b7280',
                strokeWidth: el.style?.strokeWidth ?? 1,
                cornerRadius: 4,
              }"
            />
          </template>

          <template v-else-if="el.type === 'ellipse'">
            <v-ellipse
              :config="{
                x: el.transform.w / 2,
                y: el.transform.h / 2,
                radiusX: el.transform.w / 2,
                radiusY: el.transform.h / 2,
                fill: el.style?.fill || '#3a3f4b',
                stroke: el.style?.stroke || '#6b7280',
                strokeWidth: el.style?.strokeWidth ?? 1,
              }"
            />
          </template>

          <template v-else-if="el.type === 'line'">
            <v-line
              :config="{
                points: el.props?.points || [0, el.transform.h / 2, el.transform.w, el.transform.h / 2],
                stroke: el.style?.stroke || '#94a3b8',
                strokeWidth: el.style?.strokeWidth || 2,
                lineCap: 'round',
              }"
            />
          </template>

          <template v-else-if="el.type === 'triangle'">
            <v-line
              :config="{
                points: trianglePoints(el),
                closed: true,
                fill: el.style?.fill || '#3a3f4b',
                stroke: el.style?.stroke || '#6b7280',
                strokeWidth: el.style?.strokeWidth ?? 1,
              }"
            />
          </template>

          <template v-else-if="el.type === 'circle'">
            <v-circle
              :config="{
                x: el.transform.w / 2,
                y: el.transform.h / 2,
                radius: Math.min(el.transform.w, el.transform.h) / 2,
                fill: el.style?.fill === 'transparent' ? undefined : el.style?.fill || '#52c41a',
                stroke: el.style?.stroke || '#389e0d',
                strokeWidth: el.style?.strokeWidth ?? 1,
              }"
            />
          </template>

          <template v-else-if="el.type === 'button'">
            <v-circle
              v-if="isRoundButton(el)"
              :config="{
                x: el.transform.w / 2,
                y: el.transform.h / 2,
                radius: Math.min(el.transform.w, el.transform.h) / 2,
                fill: el.style?.fill || '#1677ff',
                stroke: el.style?.stroke || '#0958d9',
                strokeWidth: el.style?.strokeWidth ?? 1,
              }"
            />
            <v-rect
              v-else
              :config="{
                width: el.transform.w,
                height: el.transform.h,
                fill: el.style?.fill || '#1677ff',
                stroke: el.style?.stroke || '#0958d9',
                strokeWidth: el.style?.strokeWidth ?? 1,
                cornerRadius:
                  el.props?.shape === 'rounded'
                    ? Math.min(el.transform.w, el.transform.h) / 2
                    : 6,
              }"
            />
            <v-text
              :config="{
                text: textOf(el) || '按钮',
                width: el.transform.w,
                height: el.transform.h,
                fill: el.style?.textColor || '#fff',
                fontSize: el.style?.fontSize || 14,
                align: 'center',
                verticalAlign: 'middle',
              }"
            />
          </template>

          <template v-else-if="el.type === 'checkbox'">
            <v-rect
              :config="{
                width: el.transform.w,
                height: el.transform.h,
                fill: el.props?.runtimeOn
                  ? el.style?.fillOn || '#1677ff'
                  : el.style?.fillOff || '#1f2937',
                stroke: el.style?.stroke || '#94a3b8',
                strokeWidth: 1.5,
                cornerRadius: 4,
              }"
            />
            <v-text
              v-if="el.props?.runtimeOn"
              :config="{
                text: '✓',
                width: el.transform.w,
                height: el.transform.h,
                fill: el.style?.checkColor || '#fff',
                fontSize: Math.max(12, Math.min(el.transform.w, el.transform.h) * 0.7),
                align: 'center',
                verticalAlign: 'middle',
              }"
            />
          </template>

          <template v-else-if="el.type === 'led'">
            <v-circle
              v-if="(el.props?.shape || 'circle') === 'circle'"
              :config="{
                x: el.transform.w / 2,
                y: el.transform.h / 2,
                radius: Math.min(el.transform.w, el.transform.h) / 2,
                fill: el.style?.fill || '#22c55e',
                stroke: el.style?.stroke || '#86efac',
                strokeWidth: el.style?.strokeWidth ?? 2,
                shadowColor: el.style?.fill || '#22c55e',
                shadowBlur: 10,
                shadowOpacity: 0.55,
              }"
            />
            <v-rect
              v-else
              :config="{
                width: el.transform.w,
                height: el.transform.h,
                fill: el.style?.fill || '#22c55e',
                stroke: el.style?.stroke || '#86efac',
                strokeWidth: el.style?.strokeWidth ?? 2,
                cornerRadius: el.props?.shape === 'rounded' ? Math.min(el.transform.w, el.transform.h) * 0.22 : 2,
                shadowColor: el.style?.fill || '#22c55e',
                shadowBlur: 10,
                shadowOpacity: 0.55,
              }"
            />
            <v-circle
              :config="{
                x: el.transform.w * 0.38,
                y: el.transform.h * 0.38,
                radius: Math.min(el.transform.w, el.transform.h) * 0.12,
                fill: '#ffffff',
                opacity: 0.35,
                listening: false,
              }"
            />
          </template>

          <template v-else-if="el.type === 'input' || el.type === 'numberInput'">
            <v-rect
              :config="{
                width: el.transform.w,
                height: el.transform.h,
                fill: el.style?.fill || '#111827',
                stroke: el.style?.stroke || '#4b5563',
                strokeWidth: el.style?.strokeWidth ?? 1,
                cornerRadius: 4,
              }"
            />
            <v-text
              :config="{
                text: textOf(el) || el.props?.placeholder || '',
                width: el.transform.w - 12,
                height: el.transform.h,
                x: 6,
                fill: textOf(el) ? el.style?.textColor || '#e5e7eb' : '#6b7280',
                fontSize: el.style?.fontSize || 14,
                align: el.props?.align || (el.type === 'numberInput' ? 'right' : 'left'),
                verticalAlign: 'middle',
              }"
            />
          </template>

          <template v-else-if="el.type === 'switch'">
            <v-rect
              :config="{
                width: el.transform.w,
                height: el.transform.h,
                fill: el.props?.runtimeOn ? el.style?.fillOn || '#52c41a' : el.style?.fillOff || '#6b7280',
                cornerRadius: el.transform.h / 2,
              }"
            />
            <v-circle
              :config="{
                x: el.props?.runtimeOn ? el.transform.w - el.transform.h / 2 : el.transform.h / 2,
                y: el.transform.h / 2,
                radius: el.transform.h / 2 - 4,
                fill: '#fff',
              }"
            />
          </template>

          <template v-else-if="el.type === 'slider'">
            <template v-if="isVerticalSlider(el)">
              <v-rect
                :config="{
                  x: el.transform.w / 2 - 3,
                  width: 6,
                  height: el.transform.h,
                  fill: el.style?.track || '#374151',
                  cornerRadius: 3,
                }"
              />
              <v-rect
                :config="{
                  x: el.transform.w / 2 - 3,
                  y: sliderThumbY(el),
                  width: 6,
                  height: Math.max(0, (el.transform.h || 0) - sliderThumbY(el)),
                  fill: el.style?.fill || '#38bdf8',
                  cornerRadius: 3,
                }"
              />
              <v-circle
                :config="{
                  x: el.transform.w / 2,
                  y: sliderThumbY(el),
                  radius: 8,
                  fill: el.style?.thumb || '#e5e7eb',
                  stroke: el.style?.fill || '#38bdf8',
                  strokeWidth: 2,
                }"
              />
            </template>
            <template v-else>
              <v-rect
                :config="{
                  y: el.transform.h / 2 - 3,
                  width: el.transform.w,
                  height: 6,
                  fill: el.style?.track || '#374151',
                  cornerRadius: 3,
                }"
              />
              <v-rect
                :config="{
                  y: el.transform.h / 2 - 3,
                  width: Math.max(0, sliderThumbX(el)),
                  height: 6,
                  fill: el.style?.fill || '#38bdf8',
                  cornerRadius: 3,
                }"
              />
              <v-circle
                :config="{
                  x: sliderThumbX(el),
                  y: el.transform.h / 2,
                  radius: 8,
                  fill: el.style?.thumb || '#e5e7eb',
                  stroke: el.style?.fill || '#38bdf8',
                  strokeWidth: 2,
                }"
              />
            </template>
          </template>

          <template v-else-if="el.type === 'progress'">
            <v-rect
              :config="{
                width: el.transform.w,
                height: el.transform.h,
                fill: el.style?.track || '#1f2937',
                stroke: el.style?.stroke || '#4b5563',
                strokeWidth: el.style?.strokeWidth ?? 1,
                cornerRadius: 4,
              }"
            />
            <v-rect
              :config="{
                width: progressWidth(el),
                height: el.transform.h,
                fill: el.style?.fill || '#22c55e',
                cornerRadius: 4,
              }"
            />
          </template>

          <template v-else-if="el.type === 'levelBar'">
            <v-rect
              :config="{
                width: el.transform.w,
                height: el.transform.h,
                fill: el.style?.track || '#1f2937',
                stroke: el.style?.stroke || '#64748b',
                strokeWidth: el.style?.strokeWidth ?? 2,
                cornerRadius: 4,
              }"
            />
            <v-rect
              :config="{
                y: levelFillY(el),
                width: el.transform.w,
                height: levelFillH(el),
                fill: el.style?.fill || '#38bdf8',
                cornerRadius: [0, 0, 4, 4],
              }"
            />
          </template>

          <template v-else-if="el.type === 'gauge'">
            <v-line
              :config="{
                points: gaugeArcPoints(el, 1),
                stroke: el.style?.track || '#374151',
                strokeWidth: 8,
                lineCap: 'round',
              }"
            />
            <v-line
              :config="{
                points: gaugeArcPoints(el, ratioOf(el)),
                stroke: el.style?.fill || '#38bdf8',
                strokeWidth: 8,
                lineCap: 'round',
              }"
            />
            <v-line
              :config="{
                points: gaugeNeedle(el),
                stroke: '#f8fafc',
                strokeWidth: 2,
                lineCap: 'round',
              }"
            />
            <v-text
              :config="{
                text: String(el.props?.value ?? 0),
                y: el.transform.h * 0.45,
                width: el.transform.w,
                fill: el.style?.textColor || '#e5e7eb',
                fontSize: el.style?.fontSize || 12,
                align: 'center',
              }"
            />
          </template>

          <template v-else-if="el.type === 'alarmBar' || el.type === 'statusBadge'">
            <v-rect
              :config="{
                width: el.transform.w,
                height: el.transform.h,
                fill: el.style?.fill || (el.type === 'statusBadge' ? '#14532d' : '#7f1d1d'),
                stroke: el.style?.stroke || (el.type === 'statusBadge' ? '#22c55e' : '#ef4444'),
                strokeWidth: el.style?.strokeWidth ?? 1,
                cornerRadius: el.type === 'statusBadge' ? el.transform.h / 2 : 4,
              }"
            />
            <v-text
              :config="{
                text: textOf(el) || (el.type === 'statusBadge' ? '运行' : '报警'),
                width: el.transform.w,
                height: el.transform.h,
                fill: el.style?.textColor || (el.type === 'statusBadge' ? '#bbf7d0' : '#fecaca'),
                fontSize: el.style?.fontSize || 13,
                align: 'center',
                verticalAlign: 'middle',
              }"
            />
          </template>

          <template v-else-if="el.type === 'pipe'">
            <v-line
              :config="{
                points: pipePoints(el),
                stroke: el.style?.stroke || '#60a5fa',
                strokeWidth: el.style?.strokeWidth || 6,
                lineCap: 'round',
                lineJoin: 'round',
              }"
            />
          </template>

          <!-- symbols -->
          <template v-else-if="el.type === 'symbol'">
            <template v-if="symbolKind(el) === 'pump'">
              <v-circle
                :config="{
                  x: el.transform.w / 2,
                  y: el.transform.h / 2,
                  radius: Math.min(el.transform.w, el.transform.h) / 2 - 2,
                  fill: el.style?.fill || '#4b5563',
                  stroke: el.style?.stroke || '#93c5fd',
                  strokeWidth: 2,
                }"
              />
              <v-line
                :config="{
                  points: [
                    el.transform.w * 0.35,
                    el.transform.h * 0.65,
                    el.transform.w * 0.5,
                    el.transform.h * 0.3,
                    el.transform.w * 0.65,
                    el.transform.h * 0.65,
                  ],
                  closed: true,
                  fill: el.style?.stroke || '#93c5fd',
                }"
              />
            </template>
            <template v-else-if="symbolKind(el) === 'valve'">
              <v-line
                :config="{
                  points: [
                    4,
                    el.transform.h / 2,
                    el.transform.w / 2,
                    4,
                    el.transform.w - 4,
                    el.transform.h / 2,
                    el.transform.w / 2,
                    el.transform.h - 4,
                  ],
                  closed: true,
                  fill: el.style?.fill || '#4b5563',
                  stroke: el.style?.stroke || '#fbbf24',
                  strokeWidth: 2,
                }"
              />
            </template>
            <template v-else-if="symbolKind(el) === 'butterflyValve'">
              <v-circle
                :config="{
                  x: el.transform.w / 2,
                  y: el.transform.h / 2,
                  radius: Math.min(el.transform.w, el.transform.h) / 2 - 2,
                  fill: el.style?.fill || '#4b5563',
                  stroke: el.style?.stroke || '#fcd34d',
                  strokeWidth: 2,
                }"
              />
              <v-line
                :config="{
                  points: [el.transform.w * 0.2, el.transform.h / 2, el.transform.w * 0.8, el.transform.h / 2],
                  stroke: el.style?.stroke || '#fcd34d',
                  strokeWidth: 3,
                }"
              />
            </template>
            <template v-else-if="symbolKind(el) === 'checkValve'">
              <v-line
                :config="{
                  points: [
                    4,
                    el.transform.h * 0.2,
                    el.transform.w * 0.55,
                    el.transform.h / 2,
                    4,
                    el.transform.h * 0.8,
                  ],
                  closed: true,
                  fill: el.style?.fill || '#4b5563',
                  stroke: el.style?.stroke || '#fbbf24',
                  strokeWidth: 2,
                }"
              />
              <v-line
                :config="{
                  points: [el.transform.w * 0.7, 6, el.transform.w * 0.7, el.transform.h - 6],
                  stroke: el.style?.stroke || '#fbbf24',
                  strokeWidth: 3,
                }"
              />
            </template>
            <template v-else-if="symbolKind(el) === 'tank'">
              <v-rect
                :config="{
                  x: 4,
                  y: 4,
                  width: el.transform.w - 8,
                  height: el.transform.h - 8,
                  fill: el.style?.fill || '#374151',
                  stroke: el.style?.stroke || '#9ca3af',
                  strokeWidth: 2,
                  cornerRadius: [el.transform.w / 4, el.transform.w / 4, 4, 4],
                }"
              />
            </template>
            <template v-else-if="symbolKind(el) === 'lamp'">
              <v-circle
                :config="{
                  x: el.transform.w / 2,
                  y: el.transform.h / 2,
                  radius: Math.min(el.transform.w, el.transform.h) / 2 - 2,
                  fill: el.style?.fill || '#6b7280',
                  stroke: el.style?.stroke || '#d1d5db',
                  strokeWidth: 2,
                }"
              />
              <v-circle
                :config="{
                  x: el.transform.w * 0.38,
                  y: el.transform.h * 0.38,
                  radius: Math.min(el.transform.w, el.transform.h) * 0.12,
                  fill: 'rgba(255,255,255,0.35)',
                  listening: false,
                }"
              />
            </template>
            <template v-else-if="symbolKind(el) === 'fan'">
              <v-circle
                :config="{
                  x: el.transform.w / 2,
                  y: el.transform.h / 2,
                  radius: Math.min(el.transform.w, el.transform.h) / 2 - 2,
                  fill: el.style?.fill || '#4b5563',
                  stroke: el.style?.stroke || '#67e8f9',
                  strokeWidth: 2,
                }"
              />
              <v-line
                :config="{
                  points: [
                    el.transform.w / 2,
                    el.transform.h * 0.18,
                    el.transform.w / 2,
                    el.transform.h * 0.82,
                    el.transform.w * 0.18,
                    el.transform.h / 2,
                    el.transform.w * 0.82,
                    el.transform.h / 2,
                  ],
                  stroke: el.style?.stroke || '#67e8f9',
                  strokeWidth: 3,
                  lineCap: 'round',
                }"
              />
            </template>
            <template v-else-if="symbolKind(el) === 'compressor'">
              <v-rect
                :config="{
                  x: 6,
                  y: el.transform.h * 0.25,
                  width: el.transform.w - 12,
                  height: el.transform.h * 0.5,
                  fill: el.style?.fill || '#4b5563',
                  stroke: el.style?.stroke || '#93c5fd',
                  strokeWidth: 2,
                  cornerRadius: 4,
                }"
              />
              <v-circle
                :config="{
                  x: el.transform.w * 0.35,
                  y: el.transform.h / 2,
                  radius: el.transform.h * 0.18,
                  stroke: el.style?.stroke || '#93c5fd',
                  strokeWidth: 2,
                }"
              />
              <v-circle
                :config="{
                  x: el.transform.w * 0.65,
                  y: el.transform.h / 2,
                  radius: el.transform.h * 0.18,
                  stroke: el.style?.stroke || '#93c5fd',
                  strokeWidth: 2,
                }"
              />
            </template>
            <template v-else-if="symbolKind(el) === 'heatExchanger'">
              <v-rect
                :config="{
                  x: 4,
                  y: 8,
                  width: el.transform.w - 8,
                  height: el.transform.h - 16,
                  fill: el.style?.fill || '#374151',
                  stroke: el.style?.stroke || '#fb923c',
                  strokeWidth: 2,
                }"
              />
              <v-line
                :config="{
                  points: [
                    10,
                    el.transform.h * 0.35,
                    el.transform.w * 0.35,
                    el.transform.h * 0.55,
                    el.transform.w * 0.65,
                    el.transform.h * 0.35,
                    el.transform.w - 10,
                    el.transform.h * 0.55,
                  ],
                  stroke: el.style?.stroke || '#fb923c',
                  strokeWidth: 2,
                }"
              />
            </template>
            <template v-else-if="symbolKind(el) === 'sensor'">
              <v-line
                :config="{
                  points: [
                    el.transform.w / 2,
                    el.transform.h - 4,
                    el.transform.w / 2,
                    el.transform.h * 0.45,
                    el.transform.w * 0.2,
                    el.transform.h * 0.2,
                    el.transform.w * 0.8,
                    el.transform.h * 0.2,
                    el.transform.w / 2,
                    el.transform.h * 0.45,
                  ],
                  closed: false,
                  stroke: el.style?.stroke || '#34d399',
                  strokeWidth: 2,
                  fill: el.style?.fill || '#4b5563',
                }"
              />
              <v-line
                :config="{
                  points: [
                    el.transform.w * 0.2,
                    el.transform.h * 0.2,
                    el.transform.w / 2,
                    4,
                    el.transform.w * 0.8,
                    el.transform.h * 0.2,
                  ],
                  closed: true,
                  fill: el.style?.stroke || '#34d399',
                }"
              />
            </template>
            <template v-else-if="symbolKind(el) === 'conveyor'">
              <v-rect
                :config="{
                  x: 2,
                  y: el.transform.h * 0.35,
                  width: el.transform.w - 4,
                  height: el.transform.h * 0.3,
                  fill: el.style?.fill || '#374151',
                  stroke: el.style?.stroke || '#94a3b8',
                  strokeWidth: 2,
                  cornerRadius: el.transform.h * 0.15,
                }"
              />
              <v-circle
                :config="{
                  x: el.transform.w * 0.2,
                  y: el.transform.h * 0.5,
                  radius: el.transform.h * 0.12,
                  fill: el.style?.stroke || '#94a3b8',
                }"
              />
              <v-circle
                :config="{
                  x: el.transform.w * 0.8,
                  y: el.transform.h * 0.5,
                  radius: el.transform.h * 0.12,
                  fill: el.style?.stroke || '#94a3b8',
                }"
              />
            </template>
            <template v-else-if="symbolKind(el) === 'agitator'">
              <v-rect
                :config="{
                  x: el.transform.w * 0.15,
                  y: el.transform.h * 0.55,
                  width: el.transform.w * 0.7,
                  height: el.transform.h * 0.35,
                  fill: el.style?.fill || '#4b5563',
                  stroke: el.style?.stroke || '#c4b5fd',
                  strokeWidth: 2,
                  cornerRadius: 4,
                }"
              />
              <v-line
                :config="{
                  points: [el.transform.w / 2, 6, el.transform.w / 2, el.transform.h * 0.55],
                  stroke: el.style?.stroke || '#c4b5fd',
                  strokeWidth: 3,
                }"
              />
              <v-line
                :config="{
                  points: [
                    el.transform.w * 0.25,
                    el.transform.h * 0.35,
                    el.transform.w * 0.75,
                    el.transform.h * 0.35,
                  ],
                  stroke: el.style?.stroke || '#c4b5fd',
                  strokeWidth: 3,
                  lineCap: 'round',
                }"
              />
            </template>
            <template v-else>
              <v-circle
                :config="{
                  x: el.transform.w / 2,
                  y: el.transform.h / 2,
                  radius: Math.min(el.transform.w, el.transform.h) / 2 - 2,
                  fill: el.style?.fill || '#4b5563',
                  stroke: el.style?.stroke || '#a78bfa',
                  strokeWidth: 2,
                }"
              />
              <v-text
                :config="{
                  text: 'M',
                  width: el.transform.w,
                  height: el.transform.h,
                  align: 'center',
                  verticalAlign: 'middle',
                  fill: '#e5e7eb',
                  fontSize: Math.min(el.transform.w, el.transform.h) * 0.35,
                }"
              />
            </template>
          </template>
        </v-group>

        <v-transformer
          v-if="isEdit"
          ref="transformerRef"
          :config="{
            rotateEnabled: true,
            enabledAnchors: [
              'top-left',
              'top-right',
              'bottom-left',
              'bottom-right',
              'middle-left',
              'middle-right',
              'top-center',
              'bottom-center',
            ],
            boundBoxFunc: (oldBox, newBox) => (newBox.width < 8 || newBox.height < 8 ? oldBox : newBox),
          }"
        />
      </v-layer>
    </v-stage>
  </div>
</template>

<style scoped>
.stage-host {
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #0f1115;
  border: 1px solid #2a2f3a;
  border-radius: 8px;
}
</style>
