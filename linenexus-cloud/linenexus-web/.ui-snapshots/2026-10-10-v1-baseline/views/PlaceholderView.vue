<script setup>
defineProps({
  title: { type: String, required: true },
  summary: { type: String, default: '' },
})
</script>

<template>
  <a-card>
    <template #title>{{ title }}</template>
    <template #extra><a-tag>即将开放</a-tag></template>
    <p>{{ summary || '功能建设中，当前仅预留导航入口。' }}</p>
    <a-empty description="一期先打通网关 → 设备 → 点表读写" />
  </a-card>
</template>
