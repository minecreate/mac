<script setup>
import { computed, h } from 'vue'
import { FileOutlined, FolderOpenOutlined, PlusOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  projectName: { type: String, default: '当前工程' },
  pages: { type: Array, default: () => [] },
  currentId: { type: String, default: '' },
  homeScreenId: { type: String, default: '' },
  /** 外层已有「页面树」Tab 时隐藏大标题，只留工程操作 */
  compactHeader: { type: Boolean, default: false },
})

const emit = defineEmits(['select', 'create'])

const treeData = computed(() => [
  {
    key: 'project',
    title: props.projectName || '当前工程',
    icon: () => h(FolderOpenOutlined),
    selectable: false,
    children: (props.pages || []).map((p) => {
      let title = p.name
      if (p.id === props.homeScreenId) title = `${p.name} · 主`
      else if (p.pageRole === 'popup') title = `${p.name} · 弹`
      return {
        key: p.id,
        title,
        isLeaf: true,
        icon: () => h(FileOutlined),
      }
    }),
  },
])

const selectedKeys = computed(() => (props.currentId ? [props.currentId] : []))

function onSelect(keys) {
  const id = keys?.[0]
  if (!id || id === 'project' || id === props.currentId) return
  emit('select', id)
}
</script>

<template>
  <aside class="ptp" :class="{ compact: compactHeader }">
    <header class="ptp-head">
      <span class="ptp-title">{{ compactHeader ? projectName || '当前工程' : '画面列表' }}</span>
      <a-button type="text" size="small" class="ptp-add" title="新建页面（本工程）" @click="emit('create')">
        <template #icon><PlusOutlined /></template>
      </a-button>
    </header>
    <div class="ptp-body">
      <a-tree
        v-if="pages.length"
        block-node
        show-icon
        default-expand-all
        :tree-data="treeData"
        :selected-keys="selectedKeys"
        @select="onSelect"
      />
      <div v-else class="ptp-empty">暂无同工程画面</div>
    </div>
  </aside>
</template>

<style scoped>
.ptp {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  background: #12171e;
  color: #e7eef7;
}
.ptp.compact .ptp-title {
  text-transform: none;
  letter-spacing: 0;
  font-size: 13px;
  color: #e7eef7;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 160px;
}
.ptp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 10px 6px;
  flex-shrink: 0;
}
.ptp-title {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: #8b9bb0;
  text-transform: uppercase;
}
.ptp-add {
  color: #8b9bb0 !important;
}
.ptp-add:hover {
  color: #38bdf8 !important;
}
.ptp-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0 4px 8px;
}
.ptp-empty {
  padding: 12px;
  font-size: 12px;
  color: #8b9bb0;
}
.ptp :deep(.ant-tree) {
  background: transparent;
  color: #e7eef7;
}
.ptp :deep(.ant-tree-node-content-wrapper:hover) {
  background: rgba(56, 189, 248, 0.12);
}
.ptp :deep(.ant-tree-node-selected .ant-tree-node-content-wrapper),
.ptp :deep(.ant-tree-node-content-wrapper.ant-tree-node-selected) {
  background: rgba(56, 189, 248, 0.22) !important;
}
</style>
