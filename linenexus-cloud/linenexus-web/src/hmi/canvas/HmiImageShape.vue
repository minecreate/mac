<script setup>
import { onUnmounted, ref, watch } from 'vue'

const props = defineProps({
  src: { type: String, default: '' },
  width: { type: Number, default: 160 },
  height: { type: Number, default: 100 },
  opacity: { type: Number, default: 1 },
  stroke: { type: String, default: '' },
  strokeWidth: { type: Number, default: 0 },
})

const image = ref(null)
const failed = ref(false)
let loadingToken = 0

function load(src) {
  const token = ++loadingToken
  image.value = null
  failed.value = false
  if (!src) return
  const img = new window.Image()
  // dataURL 不需要 crossOrigin；外链有时需要
  if (!String(src).startsWith('data:')) {
    img.crossOrigin = 'anonymous'
  }
  img.onload = () => {
    if (token !== loadingToken) return
    image.value = img
    failed.value = false
  }
  img.onerror = () => {
    if (token !== loadingToken) return
    image.value = null
    failed.value = true
  }
  img.src = src
}

watch(
  () => props.src,
  (src) => load(src),
  { immediate: true },
)

onUnmounted(() => {
  loadingToken += 1
})
</script>

<template>
  <v-image
    v-if="image"
    :config="{
      image,
      width,
      height,
      opacity: opacity ?? 1,
    }"
  />
  <template v-else>
    <v-rect
      :config="{
        width,
        height,
        fill: '#1f2937',
        stroke: stroke || '#64748b',
        strokeWidth: strokeWidth || 1,
        dash: [6, 4],
      }"
    />
    <v-text
      :config="{
        text: failed ? '加载失败' : src ? '加载中…' : '图片',
        width,
        height,
        fill: '#94a3b8',
        fontSize: 13,
        align: 'center',
        verticalAlign: 'middle',
      }"
    />
  </template>
  <v-rect
    v-if="image && (strokeWidth > 0 || stroke)"
    :config="{
      width,
      height,
      fill: 'transparent',
      stroke: stroke || '#64748b',
      strokeWidth: strokeWidth || 1,
      listening: false,
    }"
  />
</template>
