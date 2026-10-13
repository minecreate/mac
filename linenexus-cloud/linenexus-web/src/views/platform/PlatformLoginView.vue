<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { platformLoginAndStore } from '../../api/platform'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const prevTitle = document.title

onMounted(() => {
  document.title = 'LineNexus · 平台运营'
})
onUnmounted(() => {
  document.title = prevTitle || 'LineNexus'
})
const form = reactive({
  username: 'platform',
  password: 'platform123',
})

async function onSubmit() {
  if (!form.username || !form.password) {
    message.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await platformLoginAndStore({
      username: form.username.trim(),
      password: form.password,
    })
    message.success(`欢迎，${res.user.displayName || res.user.username}`)
    const redirect =
      typeof route.query.redirect === 'string' ? route.query.redirect : '/platform/tenants'
    router.replace(redirect.startsWith('/platform') ? redirect : '/platform/tenants')
  } catch (e) {
    message.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="plat">
    <div class="atmosphere" aria-hidden="true">
      <div class="mesh" />
      <div class="horizon" />
      <div class="beam" />
      <div class="vignette" />
    </div>

    <aside class="rail" aria-hidden="true">
      <span class="rail-mark" />
      <span class="rail-text">RESTRICTED</span>
      <span class="rail-id">OPS-01</span>
    </aside>

    <main class="frame">
      <section class="hero" aria-label="品牌">
        <p class="eyebrow">PLATFORM CONSOLE</p>
        <h1 class="brand">LineNexus</h1>
        <p class="lede">平台运营管理面</p>
        <p class="support">租户开通、启停与系统设置。此入口不在客户控制台中展示。</p>
        <div class="meta">
          <span class="chip">独立会话</span>
          <span class="chip">platform_admin</span>
          <span class="chip dim">/platform</span>
        </div>
      </section>

      <section class="panel" aria-label="运营登录">
        <header class="panel-head">
          <div class="status">
            <span class="pulse" />
            <span>SECURE ACCESS</span>
          </div>
          <h2>运营登录</h2>
          <p>使用平台超管账号进入管理面</p>
        </header>

        <form class="form" @submit.prevent="onSubmit">
          <label class="field">
            <span>用户名</span>
            <input
              v-model="form.username"
              type="text"
              name="username"
              autocomplete="username"
              required
              placeholder="platform"
            />
          </label>
          <label class="field">
            <span>密码</span>
            <input
              v-model="form.password"
              type="password"
              name="password"
              autocomplete="current-password"
              required
              placeholder="••••••••"
              @keyup.enter="onSubmit"
            />
          </label>
          <button class="submit" type="submit" :disabled="loading">
            <span>{{ loading ? '校验中…' : '进入管理面' }}</span>
            <span class="submit-arrow" aria-hidden="true">→</span>
          </button>
        </form>

        <p class="panel-foot">演示账号 platform / platform123</p>
      </section>
    </main>
  </div>
</template>

<style scoped>
.plat {
  --bg-0: #070b10;
  --bg-1: #0e1620;
  --steel: #1a2736;
  --signal: #4aa3c7;
  --signal-dim: rgba(74, 163, 199, 0.28);
  --frost: #e6eef5;
  --muted: #8a9bab;
  --ink: #0a1018;
  --panel: rgba(236, 242, 247, 0.96);
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background: linear-gradient(145deg, var(--bg-0) 0%, var(--bg-1) 42%, #0a121a 100%);
  color: var(--frost);
  font-family: 'Noto Sans SC', 'Space Grotesk', sans-serif;
}

.atmosphere {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.mesh {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(74, 163, 199, 0.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(74, 163, 199, 0.055) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(ellipse 70% 60% at 32% 48%, #000 10%, transparent 72%);
  animation: mesh-drift 36s linear infinite;
}

.horizon {
  position: absolute;
  left: -10%;
  right: -10%;
  top: 58%;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(74, 163, 199, 0.35),
    transparent
  );
  box-shadow: 0 0 40px 2px rgba(74, 163, 199, 0.12);
}

.beam {
  position: absolute;
  left: 18%;
  top: -20%;
  width: 42%;
  height: 140%;
  background: linear-gradient(
    180deg,
    transparent 0%,
    rgba(74, 163, 199, 0.05) 40%,
    transparent 70%
  );
  transform: rotate(-18deg);
  animation: beam-sweep 9s ease-in-out infinite;
}

.vignette {
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at center, transparent 45%, rgba(0, 0, 0, 0.45) 100%);
}

.rail {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 44px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  padding: 28px 0;
  border-right: 1px solid rgba(74, 163, 199, 0.18);
  background: rgba(8, 12, 18, 0.55);
  z-index: 2;
}

.rail-mark {
  width: 8px;
  height: 8px;
  background: var(--signal);
  box-shadow: 0 0 12px var(--signal-dim);
}

.rail-text {
  writing-mode: vertical-rl;
  transform: rotate(180deg);
  font-family: 'IBM Plex Mono', ui-monospace, monospace;
  font-size: 11px;
  letter-spacing: 0.28em;
  color: rgba(230, 238, 245, 0.45);
}

.rail-id {
  font-family: 'IBM Plex Mono', ui-monospace, monospace;
  font-size: 10px;
  letter-spacing: 0.08em;
  color: rgba(74, 163, 199, 0.7);
}

.frame {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: clamp(28px, 5vw, 64px);
  padding: 56px clamp(56px, 9vw, 120px) 56px clamp(72px, 8vw, 120px);
  box-sizing: border-box;
}

.hero {
  flex: 1 1 auto;
  max-width: 540px;
  animation: rise 0.75s ease-out both;
}

.eyebrow {
  margin: 0 0 18px;
  font-family: 'IBM Plex Mono', ui-monospace, monospace;
  font-size: 12px;
  letter-spacing: 0.18em;
  color: var(--signal);
}

.brand {
  margin: 0;
  font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
  font-size: clamp(48px, 7.5vw, 80px);
  font-weight: 700;
  letter-spacing: -0.035em;
  line-height: 0.92;
  color: #f2f7fb;
}

.lede {
  margin: 22px 0 0;
  font-size: clamp(18px, 2.2vw, 22px);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: rgba(230, 238, 245, 0.92);
}

.support {
  margin: 12px 0 0;
  max-width: 22em;
  font-size: 14px;
  line-height: 1.65;
  color: rgba(230, 238, 245, 0.58);
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 32px;
}

.chip {
  font-family: 'IBM Plex Mono', ui-monospace, monospace;
  font-size: 11px;
  letter-spacing: 0.04em;
  padding: 5px 10px;
  border: 1px solid rgba(74, 163, 199, 0.35);
  color: rgba(230, 238, 245, 0.78);
  background: rgba(74, 163, 199, 0.08);
}

.chip.dim {
  border-color: rgba(230, 238, 245, 0.14);
  background: transparent;
  color: rgba(230, 238, 245, 0.42);
}

.panel {
  flex: 0 0 auto;
  width: min(100%, 368px);
  padding: 28px 26px 22px;
  border-radius: 4px;
  background: var(--panel);
  border: 1px solid rgba(255, 255, 255, 0.22);
  box-shadow:
    0 24px 56px rgba(2, 8, 14, 0.45),
    0 0 0 1px rgba(74, 163, 199, 0.12);
  color: var(--ink);
  animation: rise 0.75s ease-out 0.1s both;
}

.panel-head .status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  font-family: 'IBM Plex Mono', ui-monospace, monospace;
  font-size: 10px;
  letter-spacing: 0.14em;
  color: #3a7a96;
}

.pulse {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--signal);
  box-shadow: 0 0 0 0 var(--signal-dim);
  animation: pulse 2.4s ease-out infinite;
}

.panel-head h2 {
  margin: 0;
  font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
  font-size: 22px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--ink);
}

.panel-head p {
  margin: 6px 0 0;
  font-size: 13px;
  color: #5f7386;
}

.form {
  margin-top: 22px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field span {
  font-family: 'IBM Plex Mono', ui-monospace, monospace;
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #6a7e91;
}

.field input {
  height: 42px;
  border: 1px solid #c5d0db;
  border-radius: 3px;
  padding: 0 12px;
  font: inherit;
  font-size: 14px;
  color: var(--ink);
  background: #fff;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.field input:focus {
  border-color: #3a7a96;
  box-shadow: 0 0 0 3px rgba(74, 163, 199, 0.22);
}

.field input::placeholder {
  color: #a3b2c0;
}

.submit {
  margin-top: 8px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border: none;
  border-radius: 3px;
  background: var(--ink);
  color: #f2f7fb;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, transform 0.12s;
}

.submit:hover:not(:disabled) {
  background: #152030;
}

.submit:active:not(:disabled) {
  transform: translateY(1px);
}

.submit:disabled {
  opacity: 0.65;
  cursor: wait;
}

.submit-arrow {
  font-family: 'IBM Plex Mono', ui-monospace, monospace;
  opacity: 0.7;
  transition: transform 0.15s, opacity 0.15s;
}

.submit:hover:not(:disabled) .submit-arrow {
  transform: translateX(3px);
  opacity: 1;
}

.panel-foot {
  margin: 16px 0 0;
  padding-top: 12px;
  border-top: 1px solid #d5dee7;
  font-family: 'IBM Plex Mono', ui-monospace, monospace;
  font-size: 11px;
  color: #7a8d9e;
  letter-spacing: 0.02em;
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes mesh-drift {
  from {
    background-position: 0 0;
  }
  to {
    background-position: 56px 56px;
  }
}

@keyframes beam-sweep {
  0%,
  100% {
    opacity: 0.35;
    transform: rotate(-18deg) translateX(0);
  }
  50% {
    opacity: 0.7;
    transform: rotate(-18deg) translateX(18px);
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

@media (max-width: 900px) {
  .rail {
    display: none;
  }
  .frame {
    flex-direction: column;
    align-items: stretch;
    justify-content: flex-start;
    gap: 36px;
    padding: 40px 20px 48px;
  }
  .brand {
    font-size: 44px;
  }
  .panel {
    width: 100%;
    max-width: 400px;
    align-self: center;
  }
}

@media (prefers-reduced-motion: reduce) {
  .mesh,
  .beam,
  .pulse,
  .hero,
  .panel {
    animation: none;
  }
}
</style>
