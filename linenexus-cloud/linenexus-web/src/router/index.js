import { createRouter, createWebHistory } from 'vue-router'
import EdgeGatewaysView from '../views/EdgeGatewaysView.vue'
import PlaceholderView from '../views/PlaceholderView.vue'
import { canAccessMenu, canManageAssets, canWrite, isLoggedIn, isViewerOnly } from '../auth/session'
import { isPlatformAdmin, isPlatformLoggedIn } from '../auth/platformSession'

const HOME = '/home'
const PLATFORM_HOME = '/platform/tenants'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/platform/login',
      name: 'platform-login',
      component: () => import('../views/platform/PlatformLoginView.vue'),
      meta: { public: true, platform: true },
    },
    {
      path: '/platform',
      component: () => import('../views/platform/PlatformLayout.vue'),
      meta: { platform: true },
      children: [
        { path: '', redirect: PLATFORM_HOME },
        {
          path: 'tenants',
          name: 'platform-tenants',
          component: () => import('../views/platform/TenantManageView.vue'),
          meta: { platform: true },
        },
      ],
    },
    { path: '/', redirect: HOME },
    {
      path: '/home',
      name: 'home',
      component: () => import('../views/HomeView.vue'),
      meta: { menu: 'home' },
    },
    {
      path: '/edge-gateways',
      name: 'edge-gateways',
      component: EdgeGatewaysView,
      meta: { menu: 'edge-gateways', adminOnly: true },
    },
    {
      path: '/edge-gateways/:id',
      name: 'edge-gateway-devices',
      component: EdgeGatewaysView,
      meta: { menu: 'edge-gateways', adminOnly: true },
    },
    {
      path: '/edge-gateways/:id/devices/:deviceId',
      name: 'device-detail',
      component: EdgeGatewaysView,
      meta: { menu: 'edge-gateways', adminOnly: true },
    },
    {
      path: '/hmi/screens',
      name: 'hmi-screens',
      component: () => import('../views/hmi/HmiScreenListView.vue'),
      meta: { menu: 'hmi-screens' },
    },
    {
      path: '/hmi/screens/:id/edit',
      name: 'hmi-editor',
      component: () => import('../views/hmi/HmiEditorView.vue'),
      meta: { menu: 'hmi-screens', write: true },
    },
    {
      path: '/hmi/screens/:id/runtime',
      name: 'hmi-runtime',
      component: () => import('../views/hmi/HmiRuntimeView.vue'),
      meta: { menu: 'hmi-screens' },
    },
    {
      path: '/monitor/realtime',
      redirect: '/hmi/screens',
    },
    {
      path: '/monitor/history',
      name: 'monitor-history',
      component: () => import('../views/monitor/HistoryTrendView.vue'),
      meta: { menu: 'monitor-history' },
    },
    {
      path: '/ops/alarms',
      name: 'ops-alarms',
      component: () => import('../views/ops/AlarmsView.vue'),
      meta: { menu: 'ops-alarms' },
    },
    {
      path: '/ops/jobs',
      name: 'ops-jobs',
      component: PlaceholderView,
      props: { title: '任务调度', summary: '周期采集、巡检与定时启停。' },
      meta: { menu: 'ops-jobs' },
    },
    {
      path: '/account/profile',
      name: 'account-profile',
      component: () => import('../views/account/ProfileView.vue'),
      meta: { menu: 'account-profile' },
    },
    {
      path: '/sys/users',
      redirect: '/account/profile',
    },
    {
      path: '/sys/roles',
      redirect: '/account/profile',
    },
    {
      path: '/sys/logs',
      name: 'sys-logs',
      component: () => import('../views/sys/SystemLogsView.vue'),
      meta: { menu: 'sys-logs', adminOnly: true },
    },
  ],
})

router.beforeEach((to) => {
  const isPlatformRoute = to.path.startsWith('/platform')

  if (isPlatformRoute) {
    if (to.path === '/platform/login') {
      if (isPlatformLoggedIn() && isPlatformAdmin()) {
        return { path: PLATFORM_HOME }
      }
      return true
    }
    if (!isPlatformLoggedIn() || !isPlatformAdmin()) {
      return { path: '/platform/login', query: { redirect: to.fullPath } }
    }
    return true
  }

  if (to.meta?.public) {
    if (to.path === '/login' && isLoggedIn()) {
      return { path: HOME }
    }
    return true
  }
  if (!isLoggedIn()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta?.adminOnly && !canManageAssets()) {
    return { path: HOME }
  }
  if (to.meta?.write && !canWrite()) {
    return { path: HOME }
  }
  if (to.meta?.menu && !canAccessMenu(to.meta.menu)) {
    return { path: HOME }
  }
  if (isViewerOnly() && (to.path.includes('/edit') || to.name === 'hmi-editor')) {
    return { path: HOME }
  }
  return true
})

export default router
