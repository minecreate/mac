<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  gateways: { type: Array, default: () => [] },
  height: { type: [Number, String], default: 360 },
})

const emit = defineEmits(['select'])

const chartEl = ref(null)
const ready = ref(false)
const loadError = ref('')
let chart = null
let mapRegistered = false

/** 无真实经纬度时，按网关 id 落到国内主要城市附近（ECharts 坐标为 [经度, 纬度]） */
const FALLBACK_CITIES = [
  [116.4074, 39.9042], // 北京
  [121.4737, 31.2304], // 上海
  [113.2644, 23.1291], // 广州
  [114.0579, 22.5431], // 深圳
  [104.0665, 30.5723], // 成都
  [106.5516, 29.563], // 重庆
  [118.7969, 32.0603], // 南京
  [120.1551, 30.2741], // 杭州
  [108.9398, 34.3416], // 西安
  [117.2008, 39.0842], // 天津
  [114.3055, 30.5928], // 武汉
  [113.6254, 34.7466], // 郑州
  [117.0, 36.65], // 济南
  [112.9823, 28.1941], // 长沙
  [126.535, 45.802], // 哈尔滨
  [123.4315, 41.8057], // 沈阳
  [102.8329, 24.8801], // 昆明
  [106.63, 26.65], // 贵阳
  [110.35, 20.02], // 海口
  [87.6168, 43.8256], // 乌鲁木齐
]

const points = computed(() =>
  (props.gateways || []).map((g, i) => {
    const online = String(g.status || '').toLowerCase() === 'online'
    const coord = resolveCoord(g, i)
    return {
      name: g.name || g.id,
      value: [...coord, online ? 1 : 0],
      gatewayId: g.id,
      online,
      status: g.status || '—',
    }
  }),
)

function resolveCoord(g, i) {
  const lng = Number(g.longitude ?? g.lng)
  const lat = Number(g.latitude ?? g.lat)
  if (Number.isFinite(lng) && Number.isFinite(lat) && Math.abs(lng) <= 180 && Math.abs(lat) <= 90) {
    return [lng, lat]
  }
  const h = hashStr(String(g.id || i))
  return FALLBACK_CITIES[h % FALLBACK_CITIES.length]
}

function hashStr(s) {
  let h = 2166136261
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

async function ensureChinaMap() {
  if (mapRegistered) return
  const res = await fetch('/geo/china.json')
  if (!res.ok) throw new Error(`加载中国地图失败（${res.status}）`)
  const geo = await res.json()
  echarts.registerMap('china', geo)
  mapRegistered = true
}

function buildOption() {
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      formatter(p) {
        if (!p?.data) return ''
        const d = p.data
        return `${d.name}<br/>状态：${d.online ? '在线' : '离线'}<br/>点击进入网关`
      },
    },
    geo: {
      map: 'china',
      roam: true,
      scaleLimit: { min: 0.8, max: 12 },
      zoom: 1.15,
      center: [104.5, 35.5],
      label: { show: false },
      itemStyle: {
        areaColor: '#e8f2ff',
        borderColor: '#8bb7e0',
        borderWidth: 1,
      },
      emphasis: {
        itemStyle: { areaColor: '#d3e7fb' },
        label: { show: false },
      },
      regions: [
        {
          name: '南海诸岛',
          itemStyle: { areaColor: '#e8f2ff', borderColor: '#8bb7e0' },
        },
      ],
    },
    series: [
      {
        name: '网关',
        type: 'scatter',
        coordinateSystem: 'geo',
        symbol: 'pin',
        symbolSize: 36,
        label: {
          show: true,
          formatter: '{b}',
          position: 'right',
          color: 'rgba(0,0,0,0.75)',
          fontSize: 11,
          backgroundColor: 'rgba(255,255,255,0.9)',
          padding: [2, 6],
          borderRadius: 3,
          borderColor: '#e8e8e8',
          borderWidth: 1,
        },
        itemStyle: {
          color: (params) => (params.data?.online ? '#52c41a' : '#ff4d4f'),
          shadowBlur: 6,
          shadowColor: 'rgba(0,0,0,0.25)',
        },
        emphasis: {
          scale: 1.15,
          label: { show: true },
        },
        data: points.value,
      },
    ],
  }
}

function render() {
  if (!chart || !ready.value) return
  chart.setOption(buildOption(), true)
}

function onResize() {
  chart?.resize()
}

function onClick(params) {
  const id = params?.data?.gatewayId
  if (id) emit('select', id)
}

onMounted(async () => {
  try {
    await ensureChinaMap()
    if (!chartEl.value) return
    chart = echarts.init(chartEl.value)
    chart.on('click', onClick)
    ready.value = true
    render()
    window.addEventListener('resize', onResize)
  } catch (e) {
    loadError.value = e.message || '地图初始化失败'
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  chart?.off('click', onClick)
  chart?.dispose()
  chart = null
})

watch(
  () => props.gateways,
  () => render(),
  { deep: true },
)
</script>

<template>
  <div class="china-map">
    <div
      ref="chartEl"
      class="china-map-canvas"
      :style="{ height: typeof height === 'number' ? height + 'px' : height }"
    />
    <div v-if="loadError" class="china-map-error">{{ loadError }}</div>
    <div v-else-if="!gateways.length && ready" class="china-map-empty">暂无网关，接入后显示在地图上</div>
    <div class="china-map-tip">滚轮缩放 · 拖拽平移 · 点击标记进入网关</div>
  </div>
</template>

<style scoped>
.china-map {
  position: relative;
  width: 100%;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #dbe7f3;
  background: #f4f8fc;
}
.china-map-canvas {
  width: 100%;
}
.china-map-error,
.china-map-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
  pointer-events: none;
  background: rgba(244, 248, 252, 0.55);
}
.china-map-tip {
  position: absolute;
  left: 10px;
  bottom: 8px;
  z-index: 2;
  font-size: 11px;
  color: rgba(0, 0, 0, 0.4);
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  padding: 2px 8px;
  pointer-events: none;
}
</style>
