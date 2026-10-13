<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { loginAndStore } from '../api/auth'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const form = reactive({
  username: 'admin',
  password: 'admin123',
  tenantId: 'tenant-dev',
})

const demos = [
  { user: 'admin', pass: 'admin123', role: '管理员', desc: '全部权限' },
  { user: 'operator', pass: 'op123', role: '操作员', desc: '执行与写值' },
  { user: 'viewer', pass: 'view123', role: '观察员', desc: '只读看板' },
]

async function onSubmit() {
  if (!form.username || !form.password) {
    message.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await loginAndStore({
      username: form.username.trim(),
      password: form.password,
      tenantId: form.tenantId.trim() || 'tenant-dev',
    })
    message.success(`欢迎，${res.user.displayName || res.user.username}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
    router.replace(redirect || '/home')
  } catch (e) {
    message.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}

function fill(d) {
  form.username = d.user
  form.password = d.pass
}
</script>

<template>
  <div class="login">
    <div class="stage" aria-hidden="true">
      <div class="hero-grid" />
      <div class="hero-scan" />
      <div class="hero-glow" />
    </div>

    <div class="content">
      <section class="brand-block" aria-label="品牌">
        <p class="eyebrow">CLOUD · MULTI-TENANT · HMI</p>
        <h1 class="brand">LineNexus</h1>
        <p class="tagline">多租户工业云平台：接入、组态与运维</p>
        <ul class="signals">
          <li><span class="dot" />网关与点表</li>
          <li><span class="dot live" />组态监控</li>
          <li><span class="dot" />系统管理</li>
        </ul>
        <p class="hero-foot">tenant-dev · cloud console</p>
      </section>

      <section class="panel" aria-label="登录">
        <header class="panel-head">
          <h2>登录</h2>
          <p>使用租户账号进入控制台</p>
        </header>

        <form class="form" @submit.prevent="onSubmit">
          <label class="field">
            <span>租户</span>
            <input v-model="form.tenantId" type="text" placeholder="tenant-dev" autocomplete="organization" />
          </label>
          <label class="field">
            <span>用户名</span>
            <input
              v-model="form.username"
              type="text"
              autocomplete="username"
              required
            />
          </label>
          <label class="field">
            <span>密码</span>
            <input
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              required
              @keyup.enter="onSubmit"
            />
          </label>
          <button class="submit" type="submit" :disabled="loading">
            {{ loading ? '登录中…' : '进入控制台' }}
          </button>
        </form>

        <div class="demos">
          <span class="demos-label">演示</span>
          <div class="demo-row">
            <button
              v-for="d in demos"
              :key="d.user"
              type="button"
              class="demo-chip"
              :title="`${d.user} · ${d.desc}`"
              @click="fill(d)"
            >
              {{ d.role }}
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.login {
  --ink: var(--ln-ink, #0b1f2a);
  --ink-2: var(--ln-ink-2, #143445);
  --signal: var(--ln-signal, #2bb89a);
  --signal-dim: var(--ln-signal-dim, rgba(43, 184, 154, 0.35));
  --text: var(--ln-text, #1a2b34);
  --muted: var(--ln-muted, #5c7380);
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background: linear-gradient(160deg, #0b1f2a 0%, #123347 48%, #0e2836 100%);
  color: #e8f1f4;
}

.stage {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.hero-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(62, 207, 178, 0.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(62, 207, 178, 0.07) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse at 28% 42%, #000 18%, transparent 72%);
  animation: grid-drift 28s linear infinite;
}

.hero-scan {
  position: absolute;
  left: 0;
  right: 0;
  height: 120px;
  background: linear-gradient(
    180deg,
    transparent,
    rgba(62, 207, 178, 0.08),
    transparent
  );
  animation: scan 7s ease-in-out infinite;
}

.hero-glow {
  position: absolute;
  right: 8%;
  top: 18%;
  width: min(42vw, 480px);
  height: min(42vw, 480px);
  border-radius: 50%;
  background: radial-gradient(circle, rgba(62, 207, 178, 0.14), transparent 68%);
  filter: blur(8px);
}

.content {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 40px;
  /* 右侧留白更大，卡片整体偏左 */
  padding: 56px clamp(96px, 14vw, 180px) 56px clamp(28px, 6vw, 88px);
  box-sizing: border-box;
}

.brand-block {
  flex: 1 1 auto;
  max-width: 520px;
  animation: rise 0.7s ease-out both;
}

.eyebrow {
  margin: 0 0 18px;
  font-family: 'IBM Plex Mono', ui-monospace, monospace;
  font-size: 12px;
  letter-spacing: 0.16em;
  color: var(--signal);
}

.brand {
  margin: 0;
  font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
  font-size: clamp(44px, 7vw, 76px);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 0.95;
}

.tagline {
  margin: 22px 0 0;
  font-size: 18px;
  line-height: 1.55;
  color: rgba(232, 241, 244, 0.78);
  max-width: 18em;
}

.signals {
  list-style: none;
  margin: 36px 0 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 10px 18px;
}

.signals li {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: rgba(232, 241, 244, 0.7);
  font-family: 'IBM Plex Mono', ui-monospace, monospace;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: rgba(232, 241, 244, 0.35);
}

.dot.live {
  background: var(--signal);
  box-shadow: 0 0 0 0 var(--signal-dim);
  animation: pulse 2.2s ease-out infinite;
}

.hero-foot {
  margin: 40px 0 0;
  font-family: 'IBM Plex Mono', ui-monospace, monospace;
  font-size: 11px;
  letter-spacing: 0.08em;
  color: rgba(232, 241, 244, 0.4);
}

.panel {
  flex: 0 0 auto;
  width: min(100%, 360px);
  padding: 28px 26px 24px;
  border-radius: 14px;
  background: rgba(243, 246, 248, 0.94);
  border: 1px solid rgba(255, 255, 255, 0.28);
  box-shadow:
    0 18px 48px rgba(4, 16, 24, 0.28),
    0 1px 0 rgba(255, 255, 255, 0.35) inset;
  backdrop-filter: blur(14px);
  color: var(--text);
  animation: rise 0.7s ease-out 0.08s both;
}

.panel-head h2 {
  margin: 0;
  font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
  font-size: 22px;
  font-weight: 600;
  color: var(--text);
  letter-spacing: -0.02em;
}

.panel-head p {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.form {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field span {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--muted);
}

.field input {
  height: 40px;
  border: 1px solid #d5dee5;
  border-radius: 8px;
  padding: 0 12px;
  font: inherit;
  font-size: 14px;
  color: var(--text);
  background: #fff;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.field input:focus {
  border-color: #2a6f7a;
  box-shadow: 0 0 0 3px rgba(62, 207, 178, 0.2);
}

.submit {
  margin-top: 6px;
  height: 42px;
  border: none;
  border-radius: 8px;
  background: var(--ink);
  color: #fff;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, transform 0.15s;
}

.submit:hover:not(:disabled) {
  background: var(--ink-2);
}

.submit:active:not(:disabled) {
  transform: translateY(1px);
}

.submit:disabled {
  opacity: 0.65;
  cursor: wait;
}

.demos {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #d5dee5;
  display: flex;
  align-items: center;
  gap: 10px;
}

.demos-label {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--muted);
  letter-spacing: 0.04em;
}

.demo-row {
  display: flex;
  flex: 1 1 auto;
  gap: 6px;
  min-width: 0;
}

.demo-chip {
  flex: 1 1 0;
  height: 28px;
  border: 1px solid #d5dee5;
  background: #fff;
  border-radius: 6px;
  padding: 0 8px;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  color: var(--text);
  white-space: nowrap;
  transition: border-color 0.15s, background 0.15s, color 0.15s;
}

.demo-chip:hover {
  border-color: #2a6f7a;
  background: #f0f8f7;
  color: #1a4d55;
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes grid-drift {
  from {
    background-position: 0 0;
  }
  to {
    background-position: 48px 48px;
  }
}

@keyframes scan {
  0%,
  100% {
    top: -10%;
    opacity: 0;
  }
  15% {
    opacity: 1;
  }
  50% {
    top: 80%;
    opacity: 0.5;
  }
  85% {
    opacity: 0;
  }
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 var(--signal-dim);
  }
  70% {
    box-shadow: 0 0 0 8px transparent;
  }
  100% {
    box-shadow: 0 0 0 0 transparent;
  }
}

@media (max-width: 860px) {
  .content {
    flex-direction: column;
    align-items: stretch;
    justify-content: flex-start;
    gap: 28px;
    padding: 36px 20px 40px;
  }
  .brand {
    font-size: 40px;
  }
  .tagline {
    font-size: 16px;
  }
  .hero-foot {
    margin-top: 24px;
  }
  .panel {
    width: 100%;
    max-width: 400px;
    align-self: center;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-grid,
  .hero-scan,
  .dot.live,
  .brand-block,
  .panel {
    animation: none;
  }
}
</style>
