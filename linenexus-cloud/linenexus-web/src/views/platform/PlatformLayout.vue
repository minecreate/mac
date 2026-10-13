<script setup>
import { computed, h, ref, watch } from 'vue'
import { useRoute, useRouter, RouterView } from 'vue-router'
import {
  BankOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import {
  clearPlatformSession,
  getPlatformUser,
  isPlatformLoggedIn,
} from '../../auth/platformSession'

const route = useRoute()
const router = useRouter()
const SIDER_KEY = 'linenexus.platform.siderCollapsed'
const siderCollapsed = ref(localStorage.getItem(SIDER_KEY) === '1')
const tick = ref(0)

const user = computed(() => {
  tick.value
  return getPlatformUser()
})

watch(siderCollapsed, (v) => {
  localStorage.setItem(SIDER_KEY, v ? '1' : '0')
})

watch(
  () => route.path,
  () => {
    tick.value += 1
  },
)

const selectedKeys = computed(() => {
  if (route.path.startsWith('/platform/tenants')) return ['tenants']
  return []
})

const menuItems = [
  {
    key: 'tenants',
    icon: () => h(BankOutlined),
    label: '租户管理',
  },
]

function onMenuClick({ key }) {
  if (key === 'tenants') router.push('/platform/tenants')
}

function onLogout() {
  clearPlatformSession()
  tick.value += 1
  router.replace('/platform/login')
}

if (!isPlatformLoggedIn()) {
  router.replace({ path: '/platform/login', query: { redirect: route.fullPath } })
}
</script>

<template>
  <a-layout class="plat-layout">
    <a-layout-header class="plat-header">
      <div class="brand">
        <a-button type="text" class="toggle" @click="siderCollapsed = !siderCollapsed">
          <template #icon>
            <MenuUnfoldOutlined v-if="siderCollapsed" />
            <MenuFoldOutlined v-else />
          </template>
        </a-button>
        <span class="mark" aria-hidden="true" />
        <div class="brand-text">
          <div class="name">LineNexus</div>
          <div class="sub">PLATFORM OPS</div>
        </div>
      </div>
      <div class="right">
        <span class="badge">platform</span>
        <a-dropdown>
          <a class="user" @click.prevent>
            <UserOutlined />
            <span>{{ user?.displayName || user?.username || '运营' }}</span>
          </a>
          <template #overlay>
            <a-menu>
              <a-menu-item key="logout" @click="onLogout">
                <LogoutOutlined />
                退出
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </a-layout-header>
    <a-layout>
      <a-layout-sider
        theme="light"
        class="plat-sider"
        collapsible
        :collapsed="siderCollapsed"
        :collapsed-width="64"
        :width="200"
        :trigger="null"
      >
        <a-menu
          mode="inline"
          :inline-collapsed="siderCollapsed"
          :selected-keys="selectedKeys"
          :items="menuItems"
          @click="onMenuClick"
        />
      </a-layout-sider>
      <a-layout-content class="plat-content">
        <RouterView />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.plat-layout {
  min-height: 100vh;
  background: var(--ln-surface, #eef3f4);
}
.plat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 0 8px;
  background: linear-gradient(180deg, #0f2834 0%, #0b1f2a 100%);
  color: #e8f1f4;
  height: 56px;
  line-height: normal;
  border-bottom: 1px solid rgba(43, 184, 154, 0.28);
  position: relative;
}
.plat-header::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: #c47a1a;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.toggle {
  color: rgba(232, 241, 244, 0.85) !important;
}
.mark {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: linear-gradient(145deg, #c47a1a, #8a5410);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.18);
  position: relative;
}
.mark::before {
  content: '';
  position: absolute;
  left: 8px;
  top: 8px;
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255, 255, 255, 0.9);
  border-radius: 2px;
}
.brand-text .name {
  font-family: var(--font-display, 'Space Grotesk', sans-serif);
  font-weight: 650;
  font-size: 15px;
  line-height: 1.1;
  letter-spacing: -0.02em;
}
.brand-text .sub {
  font-size: 10px;
  opacity: 0.55;
  line-height: 1.2;
  font-family: var(--font-mono, monospace);
  letter-spacing: 0.08em;
}
.right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.badge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 4px;
  border: 1px solid rgba(196, 122, 26, 0.45);
  background: rgba(196, 122, 26, 0.16);
  color: #f0c788;
  font-size: 11px;
  font-family: var(--font-mono, monospace);
  letter-spacing: 0.04em;
}
.user {
  color: #e8f1f4;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.plat-sider {
  background: #f7fafb !important;
  border-right: 1px solid var(--ln-line, #d5e0e4);
}
.plat-sider :deep(.ant-menu-item-selected) {
  background: rgba(196, 122, 26, 0.12) !important;
  color: #8a5410 !important;
}
.plat-content {
  margin: 16px;
  padding: 20px;
  background: #fff;
  border: 1px solid var(--ln-line, #d5e0e4);
  border-radius: var(--ln-radius, 8px);
  min-height: calc(100vh - 88px);
  box-shadow: var(--ln-shadow, none);
}
</style>
