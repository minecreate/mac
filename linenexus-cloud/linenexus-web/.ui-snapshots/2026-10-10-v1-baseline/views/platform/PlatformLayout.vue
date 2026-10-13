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
        <span class="mark" />
        <div class="brand-text">
          <div class="name">LineNexus</div>
          <div class="sub">平台运营</div>
        </div>
      </div>
      <div class="right">
        <a-tag color="orange">platform</a-tag>
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
  background: #f3eee8;
}
.plat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 0 8px;
  background: #1a1210;
  color: #f4ebe3;
  height: 56px;
  line-height: 56px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
}
.toggle {
  color: #f4ebe3 !important;
}
.mark {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  background: #c4783a;
}
.brand-text .name {
  font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
  font-weight: 650;
  font-size: 15px;
  line-height: 1.1;
}
.brand-text .sub {
  font-size: 11px;
  opacity: 0.65;
  line-height: 1.1;
}
.right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user {
  color: #f4ebe3;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.plat-sider {
  background: #faf6f1 !important;
  border-right: 1px solid #e5dcd2;
}
.plat-content {
  margin: 16px;
  padding: 20px;
  background: #fff;
  border: 1px solid #e8dfd6;
  border-radius: 10px;
  min-height: calc(100vh - 88px);
}
</style>
