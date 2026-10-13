<script setup>
import { computed, h, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter, RouterView } from 'vue-router'
import {
  ApiOutlined,
  AlertOutlined,
  ApartmentOutlined,
  AuditOutlined,
  ClockCircleOutlined,
  ClusterOutlined,
  ControlOutlined,
  DashboardOutlined,
  HomeOutlined,
  LineChartOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SettingOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { clearSession, canAccessMenu, getUser, isLoggedIn } from './auth/session'

const route = useRoute()
const router = useRouter()

const SIDER_KEY = 'linenexus.siderCollapsed'
const siderCollapsed = ref(localStorage.getItem(SIDER_KEY) === '1')
const userTick = ref(0)
const user = computed(() => {
  userTick.value
  return getUser()
})
const showShell = computed(() => {
  userTick.value
  // 平台运营面使用独立布局；租户控制台不展示 /platform
  if (route.path.startsWith('/platform') || route.path === '/login') {
    return false
  }
  return isLoggedIn()
})

watch(siderCollapsed, (v) => {
  localStorage.setItem(SIDER_KEY, v ? '1' : '0')
})

watch(
  () => route.path,
  () => {
    userTick.value += 1
  },
)

function refreshUser() {
  userTick.value += 1
}

onMounted(() => {
  window.addEventListener('linenexus:session', refreshUser)
})
onUnmounted(() => {
  window.removeEventListener('linenexus:session', refreshUser)
})

const pathByKey = {
  home: '/home',
  'edge-gateways': '/edge-gateways',
  'hmi-screens': '/hmi/screens',
  'monitor-history': '/monitor/history',
  'ops-alarms': '/ops/alarms',
  'ops-jobs': '/ops/jobs',
  'sys-logs': '/sys/logs',
}

const selectedKeys = computed(() => {
  if (route.path === '/home' || route.path.startsWith('/home/')) return ['home']
  if (route.path.startsWith('/edge-gateways')) return ['edge-gateways']
  if (route.path.startsWith('/hmi') || route.path.startsWith('/monitor/realtime')) return ['hmi-screens']
  if (route.path.startsWith('/monitor/history')) return ['monitor-history']
  if (route.path.startsWith('/ops/alarms')) return ['ops-alarms']
  if (route.path.startsWith('/ops/jobs')) return ['ops-jobs']
  if (route.path.startsWith('/sys/logs')) return ['sys-logs']
  return []
})

const openKeys = ref(['monitor'])

watch(
  () => route.path,
  (path) => {
    if (siderCollapsed.value) return
    if (path === '/home' || path.startsWith('/home/')) openKeys.value = []
    else if (path.startsWith('/monitor') || path.startsWith('/hmi')) openKeys.value = ['monitor']
    else if (path.startsWith('/ops')) openKeys.value = ['ops']
    else if (path.startsWith('/sys')) openKeys.value = ['system']
    else openKeys.value = ['access']
  },
  { immediate: true },
)

function filterChildren(children) {
  return (children || []).filter((c) => c.disabled || canAccessMenu(c.key))
}

const menuItems = computed(() => {
  userTick.value
  const all = [
    {
      key: 'home',
      icon: () => h(HomeOutlined),
      label: '运维总览',
    },
    {
      key: 'access',
      icon: () => h(ApartmentOutlined),
      label: '接入',
      children: [{ key: 'edge-gateways', icon: () => h(ClusterOutlined), label: '设备与点表' }],
    },
    {
      key: 'monitor',
      icon: () => h(DashboardOutlined),
      label: '监控',
      children: [
        { key: 'hmi-screens', icon: () => h(ApiOutlined), label: '组态画面' },
        { key: 'monitor-history', icon: () => h(LineChartOutlined), label: '历史曲线' },
      ],
    },
    {
      key: 'ops',
      icon: () => h(AlertOutlined),
      label: '运维',
      children: [
        { key: 'ops-alarms', icon: () => h(AlertOutlined), label: '告警' },
        { key: 'ops-jobs', icon: () => h(ClockCircleOutlined), label: '任务调度（即将开放）', disabled: true },
      ],
    },
    {
      key: 'system',
      icon: () => h(ControlOutlined),
      label: '系统管理',
      children: [{ key: 'sys-logs', icon: () => h(AuditOutlined), label: '系统日志' }],
    },
  ]
  return all
    .map((g) => {
      if (!g.children) return canAccessMenu(g.key) ? g : null
      const children = filterChildren(g.children)
      return children.length ? { ...g, children } : null
    })
    .filter(Boolean)
})

const roleLabel = computed(() => (user.value?.roles || []).join(',') || '—')

function onMenuClick({ key }) {
  const path = pathByKey[key]
  if (path) router.push(path)
}

function toggleSider() {
  siderCollapsed.value = !siderCollapsed.value
}

function onLogout() {
  clearSession()
  userTick.value += 1
  router.replace('/login')
}

function goProfile() {
  router.push('/account/profile')
}
</script>

<template>
  <RouterView v-if="!showShell" />
  <a-layout v-else class="app-layout">
    <a-layout-header class="app-header">
      <div class="brand">
        <a-button type="text" class="sider-toggle" @click="toggleSider">
          <template #icon>
            <MenuUnfoldOutlined v-if="siderCollapsed" />
            <MenuFoldOutlined v-else />
          </template>
        </a-button>
        <span class="brand-mark" />
        <div v-show="!siderCollapsed" class="brand-text">
          <div class="brand-name">LineNexus</div>
          <div class="brand-sub">云平台</div>
        </div>
        <div v-show="siderCollapsed" class="brand-name brand-name-compact">LN</div>
      </div>
      <div class="header-right">
        <span class="header-meta" :title="`租户 ${user?.tenantId || 'tenant-dev'}`">
          {{ user?.tenantId || 'tenant-dev' }}
        </span>
        <span class="header-meta" :title="`角色 ${roleLabel}`">{{ roleLabel }}</span>
        <span class="header-divider" aria-hidden="true" />
        <a-dropdown>
          <a class="user-entry" @click.prevent>
            <a-avatar v-if="user?.avatar" :size="24" :src="user.avatar" />
            <UserOutlined v-else />
            <span>{{ user?.displayName || user?.username || '用户' }}</span>
          </a>
          <template #overlay>
            <a-menu>
              <a-menu-item key="profile" @click="goProfile">
                <SettingOutlined />
                个人资料
              </a-menu-item>
              <a-menu-item key="logout" @click="onLogout">
                <LogoutOutlined />
                退出登录
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </a-layout-header>

    <a-layout>
      <a-layout-sider
        theme="light"
        class="app-sider"
        collapsible
        :collapsed="siderCollapsed"
        :collapsed-width="64"
        :width="220"
        :trigger="null"
        @update:collapsed="(v) => (siderCollapsed = v)"
      >
        <a-menu
          v-model:open-keys="openKeys"
          mode="inline"
          :inline-collapsed="siderCollapsed"
          :selected-keys="selectedKeys"
          :items="menuItems"
          @click="onMenuClick"
        />
      </a-layout-sider>
      <a-layout-content class="app-content">
        <RouterView />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>
