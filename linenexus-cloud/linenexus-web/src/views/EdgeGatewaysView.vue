<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  ClusterOutlined,
  DeleteOutlined,
  HddOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import {
  createPoint,
  createDevice,
  deleteDevice,
  deleteEdgeGateway,
  deletePoint,
  listDevicePoints,
  listDevices,
  listEdgeGateways,
  readDevice,
  registerEdgeGateway,
  updatePoint,
  writePoint,
} from '../api/asset'

const route = useRoute()
const router = useRouter()

const gateways = ref([])
const devicesByGw = reactive({})
const points = ref([])
const readings = ref([])
const writeDrafts = reactive({})

const expandedKeys = ref([])
const selectedKeys = ref([])
const selectedGwId = ref('')
const selectedDevId = ref('')
const syncingRoute = ref(false)

const loadingTree = ref(false) // 仅网关列表
const loadingDevices = ref(false) // 当前网关下设备
const loadingPts = ref(false) // 点表
const loadingRead = ref(false) // 南向读值
const busy = ref(false) // 弹窗/写点等表单操作
/** 点表请求序号，避免慢请求覆盖新选中设备 */
let pointsReqSeq = 0
let devicesReqSeq = 0

const TREE_WIDTH_KEY = 'linenexus.treeWidth'
const treeWidth = ref(Number(localStorage.getItem(TREE_WIDTH_KEY)) || 300)
const treeResizing = ref(false)

function clampTreeWidth(w) {
  const min = 200
  const max = Math.max(280, Math.floor(window.innerWidth * 0.55))
  return Math.min(max, Math.max(min, Math.round(w)))
}

function onTreeResizeStart(e) {
  e.preventDefault()
  treeResizing.value = true
  const startX = e.clientX
  const startW = treeWidth.value

  function onMove(ev) {
    treeWidth.value = clampTreeWidth(startW + (ev.clientX - startX))
  }
  function onUp() {
    treeResizing.value = false
    localStorage.setItem(TREE_WIDTH_KEY, String(treeWidth.value))
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

function onTreeResizeDblClick() {
  treeWidth.value = 300
  localStorage.setItem(TREE_WIDTH_KEY, '300')
}

const gwModalOpen = ref(false)
const deviceModalOpen = ref(false)
const pointModalOpen = ref(false)
const editingPointId = ref('')
const pointModalTitle = computed(() => (editingPointId.value ? '编辑点' : '新建点'))
const pointFilter = ref('')

const gwForm = reactive({
  id: '',
  name: '',
  managementUrl: 'http://127.0.0.1:8081',
  status: 'online',
})
const deviceForm = reactive({
  name: '',
  protocol: 'modbus',
  address: '',
  username: '',
  password: '',
})
const pointForm = reactive({
  name: '',
  dataType: 'int',
  rw: 'rw',
  scale: 1,
  // modbus
  mbFunc: 3,
  mbOffset: 0,
  mbType: 'int16',
  // opcua / mqtt / fallback
  opcNodeId: '',
  mqttTopic: '',
  addressJson: '{"register":0}',
})

const selectedGw = computed(() => gateways.value.find((g) => g.id === selectedGwId.value) || null)
const selectedDev = computed(() => {
  const list = devicesByGw[selectedGwId.value] || []
  return list.find((d) => d.id === selectedDevId.value) || null
})
const currentDevices = computed(() => devicesByGw[selectedGwId.value] || [])
const selectedProtocol = computed(() => String(selectedDev.value?.protocol || '').toLowerCase())

const modbusFuncOptions = [
  { value: 1, label: '1 · Coil（线圈）' },
  { value: 2, label: '2 · Discrete Input（离散输入）' },
  { value: 3, label: '3 · Holding Register（保持寄存器）' },
  { value: 4, label: '4 · Input Register（输入寄存器）' },
]
const modbusTypeOptions = [
  { value: 'int16', label: 'int16' },
  { value: 'uint16', label: 'uint16' },
  { value: 'int32', label: 'int32（2 寄存器）' },
  { value: 'uint32', label: 'uint32（2 寄存器）' },
  { value: 'float32', label: 'float32（2 寄存器）' },
  { value: 'bool', label: 'bool' },
]
const rwOptions = [
  { value: 'rw', label: '读写 (rw)' },
  { value: 'r', label: '只读 (r)' },
  { value: 'w', label: '只写 (w)' },
]

function resetPointForm() {
  const proto = selectedProtocol.value
  editingPointId.value = ''
  pointForm.name = ''
  pointForm.rw = 'rw'
  pointForm.scale = 1
  pointForm.mbFunc = 3
  pointForm.mbOffset = 0
  pointForm.mbType = 'int16'
  pointForm.opcNodeId = ''
  pointForm.mqttTopic = ''
  pointForm.addressJson = '{}'
  if (proto === 'modbus') {
    pointForm.dataType = 'int'
    pointForm.name = 'HR0'
  } else if (proto === 'opcua' || proto === 'codesys') {
    pointForm.dataType = 'float'
  } else if (proto === 'mqtt') {
    pointForm.dataType = 'float'
  } else {
    pointForm.dataType = 'float'
    pointForm.addressJson = '{"register":0}'
  }
}

function fillPointForm(point) {
  editingPointId.value = point.id
  pointForm.name = point.name || ''
  pointForm.dataType = point.dataType || 'float'
  pointForm.rw = point.rw || 'rw'
  pointForm.scale = point.scale ?? 1
  pointForm.mbFunc = 3
  pointForm.mbOffset = 0
  pointForm.mbType = 'int16'
  pointForm.opcNodeId = ''
  pointForm.mqttTopic = ''
  pointForm.addressJson = '{}'

  const addr = parseAddress(point.address)
  const proto = selectedProtocol.value
  if (proto === 'modbus' || addr.func != null || addr.offset != null) {
    pointForm.mbFunc = Number(addr.func ?? 3)
    pointForm.mbOffset = Number(addr.offset ?? 0)
    pointForm.mbType = String(addr.type || 'int16')
  } else if (proto === 'opcua' || proto === 'codesys' || addr.nodeId) {
    pointForm.opcNodeId = String(addr.nodeId || '')
  } else if (proto === 'mqtt' || addr.topic) {
    pointForm.mqttTopic = String(addr.topic || '')
  } else {
    pointForm.addressJson = JSON.stringify(addr || {})
  }
}

function openPointModal() {
  resetPointForm()
  pointModalOpen.value = true
}

function openEditPoint(point) {
  fillPointForm(point)
  pointModalOpen.value = true
}

function buildPointAddress() {
  const proto = selectedProtocol.value
  if (proto === 'modbus') {
    return {
      func: Number(pointForm.mbFunc),
      offset: Number(pointForm.mbOffset),
      type: pointForm.mbType,
    }
  }
  if (proto === 'opcua' || proto === 'codesys') {
    const nodeId = pointForm.opcNodeId.trim()
    if (!nodeId) throw new Error('请填写 NodeId')
    return { nodeId }
  }
  if (proto === 'mqtt') {
    const topic = pointForm.mqttTopic.trim()
    if (!topic) throw new Error('请填写 Topic')
    return { topic }
  }
  try {
    return JSON.parse(pointForm.addressJson)
  } catch {
    throw new Error('点地址须为合法 JSON')
  }
}

function onModbusTypeChange(typ) {
  if (typ === 'bool') pointForm.dataType = 'bool'
  else if (typ === 'float32') pointForm.dataType = 'float'
  else pointForm.dataType = 'int'
  if (Number(pointForm.mbFunc) === 1 || Number(pointForm.mbFunc) === 2) {
    pointForm.mbType = 'bool'
    pointForm.dataType = 'bool'
  }
}

function onModbusFuncChange(fn) {
  if (fn === 1 || fn === 2) {
    pointForm.mbType = 'bool'
    pointForm.dataType = 'bool'
    pointForm.rw = fn === 2 ? 'r' : 'rw'
  } else if (pointForm.mbType === 'bool') {
    pointForm.mbType = 'int16'
    pointForm.dataType = 'int'
  }
  if (fn === 4) pointForm.rw = 'r'
}

const treeData = computed(() =>
  gateways.value.map((gw) => {
    const kids = devicesByGw[gw.id]
    return {
      key: `gw:${gw.id}`,
      // title 仅作兜底；界面展示走 #title 插槽，避免和自定义标题叠成两份
      title: gw.name,
      isLeaf: false,
      selectable: true,
      gateway: gw,
      children:
        kids === undefined
          ? undefined
          : kids.map((d) => ({
              key: `dev:${gw.id}:${d.id}`,
              title: d.name,
              isLeaf: true,
              selectable: true,
              device: d,
              gatewayId: gw.id,
            })),
    }
  }),
)

const filteredPoints = computed(() => {
  const q = pointFilter.value.trim().toLowerCase()
  if (!q) return points.value
  return points.value.filter((p) => {
    const addr = typeof p.address === 'string' ? p.address : JSON.stringify(p.address)
    return `${p.name} ${p.dataType} ${p.rw} ${addr}`.toLowerCase().includes(q)
  })
})

const pointColumns = [
  { title: '点名', dataIndex: 'name', width: 120 },
  { title: '读写', dataIndex: 'rw', key: 'rw', width: 72 },
  { title: '点值类型', dataIndex: 'dataType', width: 88 },
  { title: '地址', key: 'address', width: 280 },
  { title: '当前值', key: 'value', width: 120 },
  { title: '写入', key: 'write', width: 180 },
  { title: '操作', key: 'action', width: 110 },
]

function gwKey(id) {
  return `gw:${id}`
}
function devKey(gwId, devId) {
  return `dev:${gwId}:${devId}`
}

function formatSeen(iso) {
  if (!iso) return '-'
  try {
    return new Date(iso).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return iso
  }
}

function parseAddress(address) {
  if (address == null) return {}
  if (typeof address === 'object') return address
  if (typeof address === 'string') {
    try {
      return JSON.parse(address)
    } catch {
      return { raw: address }
    }
  }
  return { raw: String(address) }
}

function addressText(address) {
  const obj = parseAddress(address)
  return Object.keys(obj).length ? JSON.stringify(obj) : '-'
}

const MODBUS_FUNC_LABEL = {
  1: 'Coil',
  2: 'Discrete',
  3: 'Holding',
  4: 'Input',
}

function addressDetail(address, protocol = selectedProtocol.value) {
  const obj = parseAddress(address)
  const proto = String(protocol || '').toLowerCase()

  if (proto === 'modbus' || obj.func != null || obj.offset != null) {
    const fn = Number(obj.func ?? 3)
    return {
      kind: 'modbus',
      rows: [
        { label: '功能码', value: `${fn} · ${MODBUS_FUNC_LABEL[fn] || '未知'}` },
        { label: '偏移', value: String(obj.offset ?? '-') },
        { label: '寄存器类型', value: String(obj.type || 'int16') },
      ],
      summary: `FC${fn} @${obj.offset ?? '-'} (${obj.type || 'int16'})`,
    }
  }
  if (proto === 'opcua' || proto === 'codesys' || obj.nodeId) {
    return {
      kind: 'opcua',
      rows: [{ label: 'NodeId', value: String(obj.nodeId || '-') }],
      summary: String(obj.nodeId || '-'),
    }
  }
  if (proto === 'mqtt' || obj.topic) {
    return {
      kind: 'mqtt',
      rows: [{ label: 'Topic', value: String(obj.topic || '-') }],
      summary: String(obj.topic || '-'),
    }
  }
  const text = addressText(obj)
  return {
    kind: 'raw',
    rows: [{ label: 'JSON', value: text }],
    summary: text,
  }
}

function rwLabel(rw) {
  if (rw === 'r') return '只读'
  if (rw === 'w') return '只写'
  return '读写'
}

function rwColor(rw) {
  if (rw === 'r') return 'default'
  if (rw === 'w') return 'orange'
  return 'blue'
}

function readingFor(pointId) {
  return readings.value.find((r) => r.pointId === pointId)
}

function nodeOf(slotProps) {
  // ant-design-vue Tree 的 title 插槽可能是 dataRef，也可能直接是节点
  return slotProps?.dataRef || slotProps || {}
}

async function syncRoute(replace = false) {
  let path = '/edge-gateways'
  if (selectedGwId.value) {
    path += `/${encodeURIComponent(selectedGwId.value)}`
    if (selectedDevId.value) {
      path += `/devices/${encodeURIComponent(selectedDevId.value)}`
    }
  }
  if (route.path === path) return
  syncingRoute.value = true
  try {
    await router[replace ? 'replace' : 'push'](path)
  } finally {
    await nextTick()
    syncingRoute.value = false
  }
}

/**
 * 分层加载：
 * 1) 网关列表 → 立刻结束 loadingTree，页面可交互
 * 2) 设备列表 → 只转资源树设备区
 * 3) 点表 → 只转右侧表格
 * 南向 read 永不自动触发，也不阻塞以上任一阶段
 */
async function loadGateways({ keepSelection = true } = {}) {
  loadingTree.value = true
  const prevGw = selectedGwId.value
  const prevDev = selectedDevId.value
  let nextGw = ''
  let nextDev = ''
  try {
    gateways.value = await listEdgeGateways()
    const gwFromRoute = String(route.params.id || '')
    if (keepSelection && prevGw && gateways.value.some((g) => g.id === prevGw)) nextGw = prevGw
    else if (gwFromRoute && gateways.value.some((g) => g.id === gwFromRoute)) nextGw = gwFromRoute
    else nextGw = gateways.value[0]?.id || ''

    if (!nextGw) {
      selectedGwId.value = ''
      selectedDevId.value = ''
      selectedKeys.value = []
      expandedKeys.value = []
      points.value = []
      readings.value = []
      await syncRoute(true)
      return
    }

    selectedGwId.value = nextGw
    if (!expandedKeys.value.includes(gwKey(nextGw))) {
      expandedKeys.value = [...expandedKeys.value, gwKey(nextGw)]
    }
    // 先按路由/记忆定选中，设备列表异步回来后再校正
    const devFromRoute = String(route.params.deviceId || '')
    if (keepSelection && prevDev) nextDev = prevDev
    else if (devFromRoute) nextDev = devFromRoute
    else nextDev = ''

    if (nextDev) {
      selectedDevId.value = nextDev
      selectedKeys.value = [devKey(nextGw, nextDev)]
    } else {
      selectedDevId.value = ''
      selectedKeys.value = [gwKey(nextGw)]
      points.value = []
      readings.value = []
    }
    await syncRoute(true)
  } catch (e) {
    message.error(e.message || '加载网关失败')
    gateways.value = []
  } finally {
    loadingTree.value = false
  }

  if (!nextGw) return
  // 设备 / 点表与页面骨架解耦
  void hydrateGatewayBranch(nextGw, nextDev || selectedDevId.value)
}

/** 加载某网关设备后校正选中，再异步拉点表（不阻塞页面） */
async function hydrateGatewayBranch(gwId, preferredDevId = '') {
  await loadDevices(gwId)
  if (selectedGwId.value !== gwId) return

  const devices = devicesByGw[gwId] || []
  let nextDev =
    preferredDevId && devices.some((d) => d.id === preferredDevId) ? preferredDevId : ''
  // 未指定设备时默认选第一台；指定了但不存在则停在网关节点
  if (!nextDev && !preferredDevId && devices.length) {
    nextDev = devices[0].id
  }

  if (nextDev) {
    if (selectedDevId.value !== nextDev) {
      selectedDevId.value = nextDev
      selectedKeys.value = [devKey(gwId, nextDev)]
      await syncRoute(true)
    }
    void loadPoints(gwId, nextDev)
    return
  }

  selectedDevId.value = ''
  selectedKeys.value = [gwKey(gwId)]
  points.value = []
  readings.value = []
  pointsReqSeq += 1
  loadingPts.value = false
  await syncRoute(true)
}

async function loadDevices(gwId) {
  if (!gwId) return
  const seq = ++devicesReqSeq
  loadingDevices.value = true
  try {
    const items = await listDevices(gwId)
    if (seq !== devicesReqSeq) return
    devicesByGw[gwId] = items
  } catch (e) {
    if (seq !== devicesReqSeq) return
    message.warning(e.message || '加载设备失败（请检查 managementUrl / 边端是否在线）')
    devicesByGw[gwId] = devicesByGw[gwId] || []
  } finally {
    if (seq === devicesReqSeq) loadingDevices.value = false
  }
}

async function loadPoints(gwId, devId) {
  if (!gwId || !devId) {
    points.value = []
    readings.value = []
    return
  }
  const seq = ++pointsReqSeq
  loadingPts.value = true
  readings.value = []
  try {
    const items = await listDevicePoints(gwId, devId)
    if (seq !== pointsReqSeq) return
    if (selectedGwId.value !== gwId || selectedDevId.value !== devId) return
    points.value = items
    for (const p of points.value) {
      if (writeDrafts[p.id] === undefined) writeDrafts[p.id] = ''
    }
  } catch (e) {
    if (seq !== pointsReqSeq) return
    message.error(e.message || '加载点位失败')
    points.value = []
  } finally {
    if (seq === pointsReqSeq) loadingPts.value = false
  }
}

async function refreshReadings({ silent = false } = {}) {
  if (!selectedGwId.value || !selectedDevId.value) return
  if (!points.value.length) {
    readings.value = []
    return
  }
  const online = selectedDev.value?.online
  if (online === false && !silent) {
    message.warning('设备 offline，跳过南向读取（避免长时间等待）')
    return
  }
  const gwId = selectedGwId.value
  const devId = selectedDevId.value
  if (!silent) loadingRead.value = true
  try {
    const items = await readDevice(gwId, devId)
    if (selectedGwId.value !== gwId || selectedDevId.value !== devId) return
    readings.value = items
    if (!silent) message.success('读取完成')
  } catch (e) {
    if (selectedGwId.value === gwId && selectedDevId.value === devId) {
      readings.value = []
    }
    if (!silent) message.error(e.message || '读取失败')
  } finally {
    if (!silent) loadingRead.value = false
  }
}

async function onExpand(keys) {
  const added = keys.filter((k) => !expandedKeys.value.includes(k))
  expandedKeys.value = keys
  for (const key of added) {
    if (key.startsWith('gw:')) {
      const gwId = key.slice(3)
      if (devicesByGw[gwId] === undefined) void loadDevices(gwId)
    }
  }
}

async function onSelect(keys) {
  if (!keys.length) return
  const key = keys[0]
  if (key.startsWith('gw:')) {
    await selectGatewayNode(key.slice(3))
    return
  }
  if (key.startsWith('dev:')) {
    const parts = key.split(':')
    await selectDeviceNode(parts[1], parts.slice(2).join(':'))
  }
}

async function onCreateGateway() {
  const id = gwForm.id.trim()
  if (!id) {
    message.error('网关 ID 不能为空')
    return
  }
  busy.value = true
  try {
    await registerEdgeGateway({
      id,
      name: gwForm.name.trim() || id,
      tenantId: 'tenant-dev',
      status: gwForm.status,
      managementUrl: gwForm.managementUrl.trim() || undefined,
    })
    message.success('网关已创建')
    gwModalOpen.value = false
    gwForm.id = ''
    gwForm.name = ''
    await loadGateways({ keepSelection: false })
    selectedGwId.value = id
    selectedDevId.value = ''
    selectedKeys.value = [gwKey(id)]
    expandedKeys.value = [...new Set([...expandedKeys.value, gwKey(id)])]
    await loadDevices(id)
    await syncRoute()
  } catch (e) {
    message.error(e.message || '创建网关失败')
  } finally {
    busy.value = false
  }
}

function confirmDeleteGateway(gw) {
  Modal.confirm({
    title: `删除网关「${gw.name}」？`,
    content: '仅移除云端登记，不会删除边端本地数据。',
    okType: 'danger',
    onOk: async () => {
      await deleteEdgeGateway(gw.id)
      delete devicesByGw[gw.id]
      message.success('已删除')
      await loadGateways({ keepSelection: false })
    },
  })
}

async function onCreateDevice() {
  if (!selectedGwId.value) {
    message.error('请先选择网关')
    return
  }
  busy.value = true
  try {
    const created = await createDevice(selectedGwId.value, {
      name: deviceForm.name || undefined,
      protocol: deviceForm.protocol,
      address: deviceForm.address,
      username: deviceForm.username || undefined,
      password: deviceForm.password || undefined,
    })
    message.success('设备已创建')
    deviceModalOpen.value = false
    deviceForm.name = ''
    deviceForm.address = ''
    deviceForm.username = ''
    deviceForm.password = ''
    expandedKeys.value = [...new Set([...expandedKeys.value, gwKey(selectedGwId.value)])]
    await loadDevices(selectedGwId.value)
    if (created?.id) {
      selectedDevId.value = created.id
      selectedKeys.value = [devKey(selectedGwId.value, created.id)]
      await loadPoints(selectedGwId.value, created.id)
      await syncRoute()
    }
  } catch (e) {
    message.error(e.message || '创建设备失败')
  } finally {
    busy.value = false
  }
}

function confirmDeleteDevice(device) {
  Modal.confirm({
    title: `删除设备「${device.name}」？`,
    okType: 'danger',
    onOk: async () => {
      await deleteDevice(selectedGwId.value, device.id)
      if (selectedDevId.value === device.id) {
        selectedDevId.value = ''
        points.value = []
        selectedKeys.value = [gwKey(selectedGwId.value)]
      }
      await loadDevices(selectedGwId.value)
      await syncRoute()
      message.success('已删除')
    },
  })
}

async function onSavePoint() {
  if (!selectedGwId.value || !selectedDevId.value) return
  if (!pointForm.name.trim()) {
    message.error('请填写点名')
    return
  }
  busy.value = true
  try {
    const address = buildPointAddress()
    const body = {
      name: pointForm.name.trim(),
      dataType: pointForm.dataType,
      rw: pointForm.rw,
      address,
      scale: Number(pointForm.scale) || 1,
    }
    if (editingPointId.value) {
      await updatePoint(selectedGwId.value, editingPointId.value, body)
      message.success('点位已更新')
    } else {
      await createPoint(selectedGwId.value, selectedDevId.value, body)
      message.success('点位已创建')
    }
    pointModalOpen.value = false
    editingPointId.value = ''
    await loadPoints(selectedGwId.value, selectedDevId.value)
    if (selectedDev.value?.online !== false) {
      void refreshReadings({ silent: true })
    }
  } catch (e) {
    message.error(e.message || (editingPointId.value ? '更新点失败' : '创建点失败'))
  } finally {
    busy.value = false
  }
}

function confirmDeletePoint(point) {
  Modal.confirm({
    title: `删除点「${point.name}」？`,
    okType: 'danger',
    onOk: async () => {
      await deletePoint(selectedGwId.value, point.id)
      await loadPoints(selectedGwId.value, selectedDevId.value)
      message.success('已删除')
    },
  })
}

async function onReadAll() {
  await refreshReadings({ silent: false })
}

async function onWrite(point) {
  const raw = writeDrafts[point.id]
  if (raw === undefined || raw === '') {
    message.warning('请先填写写入值')
    return
  }
  busy.value = true
  try {
    let value = raw
    if (point.dataType === 'int' || point.dataType === 'float') {
      const n = Number(raw)
      if (Number.isNaN(n)) throw new Error('数值格式不正确')
      value = n
    } else if (point.dataType === 'bool') {
      value = raw === 'true' || raw === '1'
    }
    await writePoint(selectedGwId.value, selectedDevId.value, point.id, value)
    message.success('写入成功')
    if (selectedDev.value?.online !== false) {
      void refreshReadings({ silent: true })
    }
  } catch (e) {
    message.error(e.message || '写入失败')
  } finally {
    busy.value = false
  }
}

async function selectGatewayNode(gwId) {
  selectedGwId.value = gwId
  selectedDevId.value = ''
  points.value = []
  readings.value = []
  pointsReqSeq += 1
  loadingPts.value = false
  selectedKeys.value = [gwKey(gwId)]
  if (!expandedKeys.value.includes(gwKey(gwId))) {
    expandedKeys.value = [...expandedKeys.value, gwKey(gwId)]
  }
  await syncRoute()
  if (devicesByGw[gwId] === undefined) void loadDevices(gwId)
}

async function selectDeviceNode(gwId, devId) {
  selectedGwId.value = gwId
  selectedDevId.value = devId
  selectedKeys.value = [devKey(gwId, devId)]
  if (!expandedKeys.value.includes(gwKey(gwId))) {
    expandedKeys.value = [...expandedKeys.value, gwKey(gwId)]
  }
  // 先更新路由与选中态，点表异步加载，不挡交互
  void syncRoute()
  if (devicesByGw[gwId] === undefined) void loadDevices(gwId)
  void loadPoints(gwId, devId)
}

watch(
  () => [route.params.id, route.params.deviceId],
  async ([gw, dev]) => {
    if (syncingRoute.value || loadingTree.value) return
    const nextGw = String(gw || '')
    const nextDev = String(dev || '')
    if (!gateways.value.length) return
    if (!nextGw) {
      selectedGwId.value = ''
      selectedDevId.value = ''
      selectedKeys.value = []
      points.value = []
      readings.value = []
      return
    }
    if (!gateways.value.some((g) => g.id === nextGw)) return
    if (nextGw !== selectedGwId.value) {
      selectedGwId.value = nextGw
      if (!expandedKeys.value.includes(gwKey(nextGw))) {
        expandedKeys.value = [...expandedKeys.value, gwKey(nextGw)]
      }
      void loadDevices(nextGw)
    }
    if (nextDev) {
      if (nextDev !== selectedDevId.value) {
        selectedDevId.value = nextDev
        selectedKeys.value = [devKey(nextGw, nextDev)]
        void loadPoints(nextGw, nextDev)
      }
    } else if (selectedDevId.value) {
      selectedDevId.value = ''
      selectedKeys.value = [gwKey(nextGw)]
      points.value = []
      readings.value = []
      pointsReqSeq += 1
      loadingPts.value = false
    }
  },
)

onMounted(() => loadGateways({ keepSelection: false }))
</script>

<template>
  <div class="page-card">
    <div class="page-toolbar">
      <div>
        <h1>设备与点表</h1>
        <p class="page-desc">左侧选节点，右侧看当前节点信息；选中设备后展示点表读写。</p>
      </div>
      <a-space>
        <a-button :loading="loadingTree" @click="loadGateways({ keepSelection: true })">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button type="primary" @click="gwModalOpen = true">
          <template #icon><PlusOutlined /></template>
          添加网关
        </a-button>
      </a-space>
    </div>

    <div class="workbench" :class="{ 'is-resizing': treeResizing }">
      <aside class="workbench-tree" :style="{ width: `${treeWidth}px` }">
        <a-card size="small" title="资源树" class="panel-card">
          <template #extra>
            <a-button
              type="link"
              size="small"
              :disabled="!selectedGwId"
              @click="deviceModalOpen = true"
            >
              + 设备
            </a-button>
          </template>

          <a-spin :spinning="loadingTree || loadingDevices">
            <a-empty v-if="!gateways.length && !loadingTree" description="暂无网关，请先添加根节点" />
            <a-tree
              v-else-if="gateways.length"
              block-node
              show-line
              :tree-data="treeData"
              :expanded-keys="expandedKeys"
              :selected-keys="selectedKeys"
              @expand="onExpand"
              @select="onSelect"
            >
              <template #title="slotProps">
                <template v-if="nodeOf(slotProps).gateway">
                  <span class="tree-title">
                    <ClusterOutlined style="color: #1677ff" />
                    <a-tag color="blue">网关</a-tag>
                    <span class="tree-title-name">{{ nodeOf(slotProps).gateway.name }}</span>
                    <span class="tree-title-id">{{ nodeOf(slotProps).gateway.id }}</span>
                  </span>
                </template>
                <template v-else-if="nodeOf(slotProps).device">
                  <span class="tree-title">
                    <HddOutlined style="color: #d46b08" />
                    <a-tag color="orange">设备</a-tag>
                    <span class="tree-title-name">{{ nodeOf(slotProps).device.name }}</span>
                    <span class="tree-title-id">
                      {{ nodeOf(slotProps).device.protocol }}
                      · {{ nodeOf(slotProps).device.online ? 'online' : 'offline' }}
                    </span>
                  </span>
                </template>
                <span v-else>{{ nodeOf(slotProps).title }}</span>
              </template>
            </a-tree>
          </a-spin>
        </a-card>
      </aside>

      <div
        class="workbench-resizer"
        title="拖动调整资源树宽度，双击恢复默认"
        @mousedown="onTreeResizeStart"
        @dblclick="onTreeResizeDblClick"
      />

      <section class="workbench-main">
        <!-- 设备节点：当前设备信息 + 点表 -->
        <a-card v-if="selectedDev" size="small" class="panel-card">
          <template #title>
            <div class="panel-title-row">
              <HddOutlined style="color: #d46b08" />
              <span>{{ selectedDev.name }}</span>
              <a-tag color="orange">设备</a-tag>
              <a-badge
                :status="selectedDev.online ? 'success' : 'default'"
                :text="selectedDev.online ? 'online' : 'offline'"
              />
            </div>
          </template>
          <template #extra>
            <a-space>
              <a-button
                :loading="loadingRead"
                :disabled="selectedDev.online === false"
                :title="selectedDev.online === false ? '设备 offline，请先恢复连接' : '南向读取当前点值'"
                @click="onReadAll"
              >
                读取全部
              </a-button>
              <a-button type="primary" @click="openPointModal">新建点</a-button>
              <a-button danger type="link" @click="confirmDeleteDevice(selectedDev)">
                <DeleteOutlined /> 删除设备
              </a-button>
            </a-space>
          </template>

          <a-descriptions size="small" :column="2" bordered class="node-desc">
            <a-descriptions-item label="所属网关">{{ selectedGw?.name || selectedGwId }}</a-descriptions-item>
            <a-descriptions-item label="协议">{{ selectedDev.protocol || '-' }}</a-descriptions-item>
            <a-descriptions-item label="端点" :span="2">
              <span class="mono">{{ selectedDev.endpoint || '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="设备 ID" :span="2">
              <span class="mono">{{ selectedDev.id }}</span>
            </a-descriptions-item>
          </a-descriptions>

          <div class="table-toolbar">
            <span class="section-label">点表</span>
            <a-input
              v-model:value="pointFilter"
              allow-clear
              placeholder="筛选点名 / 地址"
              style="max-width: 260px"
            />
            <span class="muted">共 {{ filteredPoints.length }} 点</span>
            <a-tag v-if="loadingPts" color="processing">加载点表…</a-tag>
            <a-tag v-else-if="loadingRead" color="processing">读取点值…</a-tag>
            <a-tag v-else-if="selectedDev.online === false" color="default">offline · 不自动读值</a-tag>
          </div>

          <a-table
            size="small"
            row-key="id"
            :loading="loadingPts"
            :columns="pointColumns"
            :data-source="filteredPoints"
            :pagination="{ pageSize: 20, showSizeChanger: true }"
            :scroll="{ x: 900 }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'rw'">
                <a-tag :color="rwColor(record.rw)" style="margin: 0">{{ rwLabel(record.rw) }}</a-tag>
              </template>
              <template v-else-if="column.key === 'address'">
                <div class="addr-detail" :title="addressDetail(record.address).summary">
                  <div
                    v-for="row in addressDetail(record.address).rows"
                    :key="row.label"
                    class="addr-detail-row"
                  >
                    <span class="addr-detail-label">{{ row.label }}</span>
                    <span class="addr-detail-value mono">{{ row.value }}</span>
                  </div>
                </div>
              </template>
              <template v-else-if="column.key === 'value'">
                <template v-if="readingFor(record.id)">
                  <span :style="{ color: readingFor(record.id).status === 'ok' ? undefined : '#ff4d4f' }">
                    {{ readingFor(record.id).value || readingFor(record.id).error || '-' }}
                  </span>
                </template>
                <span v-else class="muted">—</span>
              </template>
              <template v-else-if="column.key === 'write'">
                <a-space v-if="record.rw !== 'r'">
                  <a-input
                    v-model:value="writeDrafts[record.id]"
                    size="small"
                    placeholder="值"
                    style="width: 96px"
                  />
                  <a-button size="small" type="primary" :loading="busy" @click="onWrite(record)">
                    写
                  </a-button>
                </a-space>
                <span v-else class="muted">只读</span>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-space :size="0">
                  <a-button type="link" size="small" @click="openEditPoint(record)">编辑</a-button>
                  <a-button type="link" danger size="small" @click="confirmDeletePoint(record)">删</a-button>
                </a-space>
              </template>
            </template>
          </a-table>
        </a-card>

        <!-- 网关节点：当前网关信息（不重复列设备） -->
        <a-card v-else-if="selectedGw" size="small" class="panel-card">
          <template #title>
            <div class="panel-title-row">
              <ClusterOutlined style="color: #1677ff" />
              <span>{{ selectedGw.name }}</span>
              <a-tag color="blue">网关</a-tag>
              <a-badge
                :status="selectedGw.status === 'online' ? 'success' : 'default'"
                :text="selectedGw.status || '-'"
              />
            </div>
          </template>
          <template #extra>
            <a-space>
              <a-button type="primary" @click="deviceModalOpen = true">+ 设备</a-button>
              <a-button danger type="link" @click="confirmDeleteGateway(selectedGw)">删除网关</a-button>
            </a-space>
          </template>

          <a-alert
            v-if="!selectedGw.managementUrl"
            type="warning"
            show-icon
            style="margin-bottom: 12px"
            message="未配置 managementUrl，无法代理边端设备 API。"
          />

          <a-descriptions size="small" :column="1" bordered class="node-desc">
            <a-descriptions-item label="网关 ID">
              <span class="mono">{{ selectedGw.id }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="租户">{{ selectedGw.tenantId || '-' }}</a-descriptions-item>
            <a-descriptions-item label="心跳">{{ formatSeen(selectedGw.lastSeenAt) }}</a-descriptions-item>
            <a-descriptions-item label="managementUrl">
              <span class="mono">{{ selectedGw.managementUrl || '未配置' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="下属设备">
              {{ currentDevices.length }} 台
              <span class="muted">（在左侧树中展开并选择设备）</span>
            </a-descriptions-item>
          </a-descriptions>
        </a-card>

        <a-card v-else size="small" class="panel-card">
          <a-empty description="请在左侧选择网关或设备节点" />
        </a-card>
      </section>
    </div>

    <a-modal
      v-model:open="gwModalOpen"
      title="添加网关（根节点）"
      ok-text="创建"
      :confirm-loading="busy"
      @ok="onCreateGateway"
    >
      <a-form layout="vertical">
        <a-form-item label="网关 ID" required>
          <a-input v-model:value="gwForm.id" placeholder="如 gw-local-02" />
        </a-form-item>
        <a-form-item label="名称">
          <a-input v-model:value="gwForm.name" placeholder="可选" />
        </a-form-item>
        <a-form-item label="managementUrl">
          <a-input v-model:value="gwForm.managementUrl" placeholder="http://127.0.0.1:8081" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="deviceModalOpen"
      title="创建设备"
      ok-text="创建"
      :confirm-loading="busy"
      @ok="onCreateDevice"
    >
      <a-alert
        type="info"
        show-icon
        style="margin-bottom: 12px"
        :message="`所属网关：${selectedGw?.name || '未选择'}`"
      />
      <a-form layout="vertical">
        <a-form-item label="名称">
          <a-input v-model:value="deviceForm.name" placeholder="可选" />
        </a-form-item>
        <a-form-item label="协议" required>
          <a-select v-model:value="deviceForm.protocol">
            <a-select-option value="modbus">modbus</a-select-option>
            <a-select-option value="opcua">opcua</a-select-option>
            <a-select-option value="mqtt">mqtt</a-select-option>
            <a-select-option value="codesys">codesys</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="地址 / Endpoint" required>
          <a-input v-model:value="deviceForm.address" placeholder="如 127.0.0.1:502" />
        </a-form-item>
        <a-form-item label="用户名">
          <a-input v-model:value="deviceForm.username" autocomplete="off" />
        </a-form-item>
        <a-form-item label="密码">
          <a-input-password v-model:value="deviceForm.password" autocomplete="new-password" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="pointModalOpen"
      :title="pointModalTitle"
      :ok-text="editingPointId ? '保存' : '创建'"
      :confirm-loading="busy"
      :width="560"
      destroy-on-close
      centered
      wrap-class-name="point-modal"
      @ok="onSavePoint"
    >
      <div class="point-modal-body">
        <div class="point-modal-meta">
          <span class="point-modal-meta-label">所属设备</span>
          <span class="point-modal-meta-value">{{ selectedDev?.name || '-' }}</span>
          <a-tag v-if="selectedProtocol" color="blue">{{ selectedProtocol }}</a-tag>
        </div>

        <a-form layout="vertical" class="point-modal-form" :required-mark="false">
          <div class="point-form-section">
            <div class="point-form-section-title">基本信息</div>
            <a-row :gutter="[12, 0]">
              <a-col :span="14">
                <a-form-item label="点名" required>
                  <a-input v-model:value="pointForm.name" placeholder="如 HR0" allow-clear />
                </a-form-item>
              </a-col>
              <a-col :span="10">
                <a-form-item label="读写">
                  <a-select v-model:value="pointForm.rw" :options="rwOptions" />
                </a-form-item>
              </a-col>
              <a-col :span="14">
                <a-form-item label="点值类型">
                  <a-select v-model:value="pointForm.dataType">
                    <a-select-option value="float">float</a-select-option>
                    <a-select-option value="int">int</a-select-option>
                    <a-select-option value="bool">bool</a-select-option>
                    <a-select-option value="string">string</a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :span="10">
                <a-form-item label="缩放">
                  <a-input-number v-model:value="pointForm.scale" style="width: 100%" :min="0" />
                </a-form-item>
              </a-col>
            </a-row>
          </div>

          <div v-if="selectedProtocol === 'modbus'" class="point-form-section">
            <div class="point-form-section-title">Modbus 地址</div>
            <a-form-item label="功能码" required>
              <a-select
                v-model:value="pointForm.mbFunc"
                :options="modbusFuncOptions"
                @change="onModbusFuncChange"
              />
            </a-form-item>
            <a-row :gutter="[12, 0]">
              <a-col :span="12">
                <a-form-item label="寄存器偏移" required>
                  <a-input-number
                    v-model:value="pointForm.mbOffset"
                    :min="0"
                    :max="65535"
                    style="width: 100%"
                    placeholder="offset"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="寄存器类型" required>
                  <a-select
                    v-model:value="pointForm.mbType"
                    :options="modbusTypeOptions"
                    @change="onModbusTypeChange"
                  />
                </a-form-item>
              </a-col>
            </a-row>
            <div class="point-addr-preview">
              <span class="point-addr-preview-label">地址预览</span>
              <code class="mono">{{
                JSON.stringify({
                  func: pointForm.mbFunc,
                  offset: pointForm.mbOffset,
                  type: pointForm.mbType,
                })
              }}</code>
            </div>
          </div>

          <div
            v-else-if="selectedProtocol === 'opcua' || selectedProtocol === 'codesys'"
            class="point-form-section"
          >
            <div class="point-form-section-title">OPC UA 地址</div>
            <a-form-item label="NodeId" required>
              <a-input
                v-model:value="pointForm.opcNodeId"
                class="mono"
                placeholder="ns=4;s=Application.GVL.status"
                allow-clear
              />
            </a-form-item>
          </div>

          <div v-else-if="selectedProtocol === 'mqtt'" class="point-form-section">
            <div class="point-form-section-title">MQTT 地址</div>
            <a-form-item label="Topic" required>
              <a-input
                v-model:value="pointForm.mqttTopic"
                class="mono"
                placeholder="lab/line1/status"
                allow-clear
              />
            </a-form-item>
          </div>

          <div v-else class="point-form-section">
            <div class="point-form-section-title">地址</div>
            <a-form-item label="地址 JSON" required>
              <a-textarea
                v-model:value="pointForm.addressJson"
                class="mono"
                :rows="3"
                placeholder='{"key":"value"}'
              />
            </a-form-item>
          </div>
        </a-form>
      </div>
    </a-modal>
  </div>
</template>
