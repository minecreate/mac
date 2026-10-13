<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import * as echarts from 'echarts'
import { listDevicePoints, listDevices, listEdgeGateways } from '../../api/asset'
import { queryHistory } from '../../api/monitor'

const loading = ref(false)
const gateways = ref([])
const devices = ref([])
const points = ref([])

const gatewayId = ref('')
const deviceId = ref('')
const selectedPointIds = ref([])
const rangeHours = ref(1)
const intervalSec = ref(0)
const chartEl = ref(null)
let chart = null

const gatewayOptions = computed(() =>
  gateways.value.map((g) => ({ value: g.id, label: `${g.name || g.id} (${g.id})` })),
)
const deviceOptions = computed(() =>
  devices.value.map((d) => ({ value: d.id, label: `${d.name || d.id}` })),
)
const pointOptions = computed(() =>
  points.value.map((p) => ({ value: p.id, label: `${p.name || p.id}` })),
)

onMounted(async () => {
  try {
    gateways.value = (await listEdgeGateways()) || []
    if (gateways.value.length && !gatewayId.value) {
      gatewayId.value = gateways.value[0].id
    }
  } catch (e) {
    message.error(e.message || '加载网关失败')
  }
  chart = echarts.init(chartEl.value)
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})

function onResize() {
  chart?.resize()
}

watch(gatewayId, async (id) => {
  deviceId.value = ''
  selectedPointIds.value = []
  devices.value = []
  points.value = []
  if (!id) return
  try {
    devices.value = await listDevices(id)
    if (devices.value.length) deviceId.value = devices.value[0].id
  } catch (e) {
    message.error(e.message || '加载设备失败')
  }
})

watch(deviceId, async (id) => {
  selectedPointIds.value = []
  points.value = []
  if (!id || !gatewayId.value) return
  try {
    points.value = await listDevicePoints(gatewayId.value, id)
    if (points.value.length) {
      selectedPointIds.value = [points.value[0].id]
    }
  } catch (e) {
    message.error(e.message || '加载点表失败')
  }
})

async function loadTrend() {
  if (!gatewayId.value || !deviceId.value || !selectedPointIds.value.length) {
    message.warning('请选择网关、设备与至少一个点')
    return
  }
  loading.value = true
  try {
    const to = new Date()
    const from = new Date(to.getTime() - rangeHours.value * 3600 * 1000)
    const data = await queryHistory({
      gatewayId: gatewayId.value,
      deviceId: deviceId.value,
      pointIds: selectedPointIds.value,
      from: from.toISOString(),
      to: to.toISOString(),
      interval: intervalSec.value > 0 ? intervalSec.value : undefined,
    })
    const series = (data?.series || []).map((s, i) => {
      const pts = (s.points || []).map((p) => {
        const v = p.vDouble != null ? p.vDouble : Number(p.value)
        return [p.ts, Number.isFinite(v) ? v : null]
      })
      const name =
        points.value.find((p) => p.id === s.pointId)?.name || s.pointId
      return {
        name,
        type: 'line',
        showSymbol: pts.length < 80,
        data: pts,
        color: ['#1677ff', '#52c41a', '#fa8c16', '#eb2f96', '#13c2c2'][i % 5],
      }
    })
    chart?.setOption(
      {
        tooltip: { trigger: 'axis' },
        legend: { top: 0 },
        grid: { left: 48, right: 24, top: 40, bottom: 48 },
        xAxis: { type: 'time' },
        yAxis: { type: 'value', scale: true },
        dataZoom: [{ type: 'inside' }, { type: 'slider', height: 18 }],
        series,
      },
      true,
    )
    if (!series.some((s) => s.data.length)) {
      message.info('该时间范围内暂无时序数据（请确认边端 MQTT 已上报或使用 ingest 调试接口）')
    }
  } catch (e) {
    message.error(e.message || '查询失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <a-card title="历史曲线">
    <a-space wrap style="margin-bottom: 16px">
      <a-select
        v-model:value="gatewayId"
        :options="gatewayOptions"
        placeholder="网关"
        style="width: 220px"
        show-search
        option-filter-prop="label"
      />
      <a-select
        v-model:value="deviceId"
        :options="deviceOptions"
        placeholder="设备"
        style="width: 200px"
        show-search
        option-filter-prop="label"
      />
      <a-select
        v-model:value="selectedPointIds"
        :options="pointOptions"
        mode="multiple"
        placeholder="测点"
        style="min-width: 240px"
        :max-tag-count="2"
      />
      <a-select
        v-model:value="rangeHours"
        :options="[
          { value: 1, label: '近 1 小时' },
          { value: 6, label: '近 6 小时' },
          { value: 24, label: '近 24 小时' },
          { value: 72, label: '近 3 天' },
        ]"
        style="width: 130px"
      />
      <a-select
        v-model:value="intervalSec"
        :options="[
          { value: 0, label: '原始采样' },
          { value: 10, label: '10s 聚合' },
          { value: 60, label: '1min 聚合' },
          { value: 300, label: '5min 聚合' },
        ]"
        style="width: 130px"
      />
      <a-button type="primary" :loading="loading" @click="loadTrend">查询</a-button>
    </a-space>
    <div ref="chartEl" class="trend-chart" />
  </a-card>
</template>

<style scoped>
.trend-chart {
  width: 100%;
  height: 480px;
  background: #fafafa;
  border-radius: 6px;
}
</style>
