<script setup>
import { computed, ref } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { PALETTE_CATEGORIES, defaultOpenKeys, countPaletteItems } from './palette'

defineProps({
  /** 外层已有「图元库」Tab 时弱化标题区 */
  embed: { type: Boolean, default: false },
})

const emit = defineEmits(['add'])

const keyword = ref('')
const openKeys = ref(defaultOpenKeys())
const total = countPaletteItems()

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return PALETTE_CATEGORIES.map((cat) => {
    const items = q
      ? cat.items.filter(
          (it) =>
            it.label.toLowerCase().includes(q) ||
            it.type.toLowerCase().includes(q) ||
            (it.hint || '').toLowerCase().includes(q) ||
            (it.symbolId || '').toLowerCase().includes(q),
        )
      : cat.items
    return { ...cat, items }
  }).filter((cat) => cat.items.length > 0)
})

function itemKey(item, idx) {
  return `${item.type}-${item.symbolId || item.label}-${idx}`
}

function onAdd(item) {
  emit('add', item)
}

function toggle(key) {
  const i = openKeys.value.indexOf(key)
  if (i >= 0) openKeys.value = openKeys.value.filter((k) => k !== key)
  else openKeys.value = [...openKeys.value, key]
}

function isOpen(key) {
  return openKeys.value.includes(key)
}
</script>

<template>
  <aside class="pp" :class="{ embed }">
    <header class="pp-head">
      <div v-if="!embed" class="pp-title-row">
        <span class="pp-title">图元库</span>
        <span class="pp-count">{{ total }}</span>
      </div>
      <div class="pp-search">
        <SearchOutlined class="pp-search-ico" />
        <input v-model="keyword" class="pp-search-input" type="search" :placeholder="embed ? `搜索图元（${total}）` : '搜索图元…'" />
      </div>
    </header>

    <div class="pp-body">
      <section v-for="cat in filtered" :key="cat.key" class="pp-cat">
        <button type="button" class="pp-cat-hd" @click="toggle(cat.key)">
          <span class="pp-cat-chev" :class="{ open: isOpen(cat.key) }" />
          <span class="pp-cat-name">{{ cat.label }}</span>
          <span class="pp-cat-meta">{{ cat.items.length }}</span>
        </button>
        <div v-show="isOpen(cat.key) || keyword.trim()" class="pp-grid">
          <button
            v-for="(item, idx) in cat.items"
            :key="itemKey(item, idx)"
            type="button"
            class="pp-tile"
            :title="item.hint || item.label"
            @click="onAdd(item)"
          >
            <span class="pp-glyph" :data-icon="item.icon || item.type">
              <svg viewBox="0 0 32 32" aria-hidden="true">
                <template
                  v-if="
                    (item.icon || item.type) === 'num' ||
                    (item.icon || item.type) === 'numLg' ||
                    (item.icon || item.type) === 'numUnit'
                  "
                >
                  <rect x="4" y="8" width="24" height="16" rx="3" fill="none" stroke="currentColor" stroke-width="1.6" />
                  <text x="16" y="19.5" text-anchor="middle" font-size="9" fill="currentColor" font-family="ui-monospace, monospace">12.3</text>
                </template>
                <template v-else-if="(item.icon || item.type) === 'label' || (item.icon || item.type) === 'title'">
                  <text x="16" y="20" text-anchor="middle" font-size="11" fill="currentColor" font-family="system-ui">Aa</text>
                </template>
                <template v-else-if="(item.icon || item.type) === 'time'">
                  <circle cx="16" cy="16" r="10" fill="none" stroke="currentColor" stroke-width="1.6" />
                  <line x1="16" y1="16" x2="16" y2="10" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
                  <line x1="16" y1="16" x2="21" y2="16" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'date'">
                  <rect x="7" y="8" width="18" height="16" rx="2" fill="none" stroke="currentColor" stroke-width="1.5" />
                  <line x1="7" y1="13" x2="25" y2="13" stroke="currentColor" stroke-width="1.4" />
                  <line x1="12" y1="6" x2="12" y2="10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
                  <line x1="20" y1="6" x2="20" y2="10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'image'">
                  <rect x="6" y="8" width="20" height="16" rx="2" fill="none" stroke="currentColor" stroke-width="1.5" />
                  <circle cx="12" cy="13" r="2" fill="currentColor" opacity="0.85" />
                  <polyline points="8,22 14,16 18,19 22,14 26,22" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'progress' || (item.icon || item.type) === 'progressLoad'">
                  <rect x="3" y="13" width="26" height="6" rx="2" fill="none" stroke="currentColor" stroke-width="1.4" />
                  <rect x="3" y="13" width="16" height="6" rx="2" fill="currentColor" opacity="0.7" />
                </template>
                <template v-else-if="String(item.icon || item.type || '').startsWith('gauge')">
                  <path d="M6 22 A10 10 0 0 1 26 22" fill="none" stroke="currentColor" stroke-width="2" />
                  <line x1="16" y1="22" x2="22" y2="12" stroke="currentColor" stroke-width="1.6" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'level' || (item.icon || item.type) === 'levelMat'">
                  <rect x="11" y="4" width="10" height="24" rx="2" fill="none" stroke="currentColor" stroke-width="1.5" />
                  <rect x="11" y="14" width="10" height="14" rx="1" fill="currentColor" opacity="0.65" />
                </template>
                <template
                  v-else-if="
                    (item.icon || item.type) === 'alarm' ||
                    (item.icon || item.type) === 'warnBar' ||
                    (item.icon || item.type) === 'infoBar'
                  "
                >
                  <rect x="3" y="10" width="26" height="12" rx="2" fill="currentColor" opacity="0.35" stroke="currentColor" stroke-width="1.4" />
                  <text x="16" y="19" text-anchor="middle" font-size="7" fill="currentColor">!</text>
                </template>
                <template v-else-if="String(item.icon || '').startsWith('badge')">
                  <rect x="4" y="11" width="24" height="10" rx="5" fill="currentColor" opacity="0.35" stroke="currentColor" stroke-width="1.3" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'switch' || (item.icon || item.type) === 'switchSm'">
                  <rect x="5" y="11" width="22" height="10" rx="5" fill="none" stroke="currentColor" stroke-width="1.5" />
                  <circle cx="21" cy="16" r="3.5" fill="currentColor" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'checkbox'">
                  <rect x="8" y="8" width="16" height="16" rx="3" fill="none" stroke="currentColor" stroke-width="1.6" />
                  <polyline points="11,16 15,20 22,12" fill="none" stroke="currentColor" stroke-width="1.8" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'btnRound'">
                  <circle cx="16" cy="16" r="9" fill="currentColor" opacity="0.35" stroke="currentColor" stroke-width="1.5" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'estop'">
                  <circle cx="16" cy="16" r="9" fill="#dc2626" opacity="0.85" />
                  <circle cx="16" cy="16" r="5" fill="none" stroke="#fff" stroke-width="1.5" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'reset'">
                  <rect x="5" y="9" width="22" height="14" rx="3" fill="#ca8a04" opacity="0.8" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'btnPopup'">
                  <rect x="5" y="9" width="22" height="14" rx="3" fill="none" stroke="currentColor" stroke-width="1.5" />
                  <rect x="12" y="6" width="14" height="10" rx="2" fill="currentColor" opacity="0.35" />
                </template>
                <template
                  v-else-if="
                    (item.icon || item.type) === 'btn' ||
                    String(item.icon || '').startsWith('btn')
                  "
                >
                  <rect x="5" y="9" width="22" height="14" rx="3" fill="currentColor" opacity="0.25" stroke="currentColor" stroke-width="1.5" />
                </template>
                <template
                  v-else-if="
                    (item.icon || item.type) === 'input' ||
                    (item.icon || item.type) === 'numInput' ||
                    (item.icon || item.type) === 'sp'
                  "
                >
                  <rect x="3" y="10" width="26" height="12" rx="2" fill="none" stroke="currentColor" stroke-width="1.5" />
                  <line x1="7" y1="16" x2="14" y2="16" stroke="currentColor" stroke-width="1.4" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'slider'">
                  <line x1="4" y1="16" x2="28" y2="16" stroke="currentColor" stroke-width="2" />
                  <circle cx="18" cy="16" r="4" fill="currentColor" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'sliderV'">
                  <line x1="16" y1="4" x2="16" y2="28" stroke="currentColor" stroke-width="2" />
                  <circle cx="16" cy="14" r="4" fill="currentColor" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'led' || String(item.icon || '').startsWith('led')">
                  <circle cx="16" cy="16" r="8" :fill="item.style?.fill || '#22c55e'" opacity="0.95" />
                  <circle cx="13" cy="13" r="2" fill="#fff" opacity="0.5" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'pipeH'">
                  <line x1="3" y1="16" x2="29" y2="16" stroke="currentColor" stroke-width="4" stroke-linecap="round" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'pipeV'">
                  <line x1="16" y1="3" x2="16" y2="29" stroke="currentColor" stroke-width="4" stroke-linecap="round" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'pipeL'">
                  <polyline points="4,16 16,16 16,28" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'pipeT'">
                  <line x1="4" y1="18" x2="28" y2="18" stroke="currentColor" stroke-width="3.5" stroke-linecap="round" />
                  <line x1="16" y1="18" x2="16" y2="6" stroke="currentColor" stroke-width="3.5" stroke-linecap="round" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'pump'">
                  <circle cx="16" cy="16" r="9" fill="none" stroke="currentColor" stroke-width="1.6" />
                  <polygon points="12,21 16,10 20,21" fill="currentColor" opacity="0.7" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'valve' || (item.icon || item.type) === 'bvalve'">
                  <polygon points="6,16 16,7 26,16 16,25" fill="none" stroke="currentColor" stroke-width="1.6" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'cvalve'">
                  <polygon points="6,8 18,16 6,24" fill="none" stroke="currentColor" stroke-width="1.6" />
                  <line x1="22" y1="8" x2="22" y2="24" stroke="currentColor" stroke-width="2" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'tank'">
                  <rect x="8" y="5" width="16" height="22" rx="6" fill="none" stroke="currentColor" stroke-width="1.6" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'motor'">
                  <circle cx="16" cy="16" r="9" fill="none" stroke="currentColor" stroke-width="1.6" />
                  <text x="16" y="20" text-anchor="middle" font-size="10" fill="currentColor">M</text>
                </template>
                <template v-else-if="(item.icon || item.type) === 'fan'">
                  <circle cx="16" cy="16" r="9" fill="none" stroke="currentColor" stroke-width="1.6" />
                  <line x1="16" y1="7" x2="16" y2="25" stroke="currentColor" stroke-width="1.6" />
                  <line x1="7" y1="16" x2="25" y2="16" stroke="currentColor" stroke-width="1.6" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'comp'">
                  <rect x="5" y="10" width="22" height="12" rx="2" fill="none" stroke="currentColor" stroke-width="1.5" />
                  <circle cx="12" cy="16" r="3" fill="none" stroke="currentColor" stroke-width="1.3" />
                  <circle cx="20" cy="16" r="3" fill="none" stroke="currentColor" stroke-width="1.3" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'hx'">
                  <rect x="5" y="7" width="22" height="18" fill="none" stroke="currentColor" stroke-width="1.5" />
                  <polyline points="8,12 14,18 20,12 26,18" fill="none" stroke="currentColor" stroke-width="1.4" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'sensor'">
                  <polygon points="16,5 24,14 16,14 8,14" fill="currentColor" opacity="0.55" />
                  <line x1="16" y1="14" x2="16" y2="27" stroke="currentColor" stroke-width="1.6" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'belt'">
                  <rect x="3" y="12" width="26" height="8" rx="4" fill="none" stroke="currentColor" stroke-width="1.5" />
                  <circle cx="9" cy="16" r="2.2" fill="currentColor" />
                  <circle cx="23" cy="16" r="2.2" fill="currentColor" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'agit'">
                  <line x1="16" y1="5" x2="16" y2="18" stroke="currentColor" stroke-width="1.6" />
                  <line x1="9" y1="12" x2="23" y2="12" stroke="currentColor" stroke-width="1.6" />
                  <rect x="7" y="18" width="18" height="9" rx="2" fill="none" stroke="currentColor" stroke-width="1.5" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'rect'">
                  <rect x="6" y="8" width="20" height="16" rx="2" fill="none" stroke="currentColor" stroke-width="1.6" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'ellipse'">
                  <ellipse cx="16" cy="16" rx="11" ry="7" fill="none" stroke="currentColor" stroke-width="1.6" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'line'">
                  <line x1="5" y1="22" x2="27" y2="10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'tri'">
                  <polygon points="16,6 27,25 5,25" fill="none" stroke="currentColor" stroke-width="1.6" />
                </template>
                <template v-else-if="(item.icon || item.type) === 'circle'">
                  <circle cx="16" cy="16" r="9" fill="none" stroke="currentColor" stroke-width="1.6" />
                </template>
                <template v-else>
                  <rect x="7" y="7" width="18" height="18" rx="3" fill="none" stroke="currentColor" stroke-width="1.5" />
                </template>
              </svg>
            </span>
            <span class="pp-label">{{ item.label }}</span>
          </button>
        </div>
      </section>

      <div v-if="!filtered.length" class="pp-empty">无匹配图元</div>
    </div>
  </aside>
</template>

<style scoped>
.pp {
  --pp-bg: #12171e;
  --pp-panel: #171d26;
  --pp-elev: #1e2632;
  --pp-line: #2a3444;
  --pp-text: #e7eef7;
  --pp-muted: #8b9bb0;
  --pp-accent: #38bdf8;
  --pp-accent-dim: rgba(56, 189, 248, 0.14);

  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background:
    radial-gradient(120% 60% at 0% 0%, rgba(56, 189, 248, 0.08), transparent 55%),
    linear-gradient(180deg, #151b24 0%, var(--pp-bg) 40%);
  color: var(--pp-text);
  border-right: 1px solid var(--pp-line);
}

.pp.embed {
  border-right: none;
}

.pp.embed .pp-head {
  padding: 10px 12px;
}

.pp-head {
  flex-shrink: 0;
  padding: 14px 12px 10px;
  border-bottom: 1px solid var(--pp-line);
}

.pp-title-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 10px;
}

.pp-title {
  font-size: 13px;
  font-weight: 650;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--pp-text);
}

.pp-count {
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: var(--pp-muted);
  background: var(--pp-elev);
  border: 1px solid var(--pp-line);
  border-radius: 999px;
  padding: 1px 8px;
}

.pp-search {
  position: relative;
  display: flex;
  align-items: center;
}

.pp-search-ico {
  position: absolute;
  left: 10px;
  color: var(--pp-muted);
  font-size: 12px;
  pointer-events: none;
}

.pp-search-input {
  width: 100%;
  height: 32px;
  padding: 0 10px 0 30px;
  border: 1px solid var(--pp-line);
  border-radius: 8px;
  background: var(--pp-elev);
  color: var(--pp-text);
  font-size: 12px;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.pp-search-input::placeholder {
  color: #6b7a8f;
}

.pp-search-input:focus {
  border-color: rgba(56, 189, 248, 0.55);
  box-shadow: 0 0 0 3px var(--pp-accent-dim);
}

.pp-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 8px 8px 16px;
  scrollbar-width: thin;
  scrollbar-color: #334155 transparent;
}

.pp-cat {
  margin-bottom: 6px;
}

.pp-cat-hd {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--pp-text);
  cursor: pointer;
  text-align: left;
  transition: background 0.12s ease;
}

.pp-cat-hd:hover {
  background: rgba(255, 255, 255, 0.04);
}

.pp-cat-chev {
  width: 0;
  height: 0;
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
  border-left: 5px solid var(--pp-muted);
  transition: transform 0.15s ease;
  flex-shrink: 0;
}

.pp-cat-chev.open {
  transform: rotate(90deg);
  border-left-color: var(--pp-accent);
}

.pp-cat-name {
  flex: 1;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.pp-cat-meta {
  font-size: 10px;
  color: var(--pp-muted);
  font-variant-numeric: tabular-nums;
}

.pp-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  padding: 2px 2px 8px;
}

.pp-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px 6px 8px;
  border: 1px solid var(--pp-line);
  border-radius: 10px;
  background: linear-gradient(180deg, #1c2430 0%, var(--pp-panel) 100%);
  color: var(--pp-text);
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    transform 0.12s ease,
    background 0.15s ease,
    box-shadow 0.15s ease;
}

.pp-tile:hover {
  border-color: rgba(56, 189, 248, 0.55);
  background: linear-gradient(180deg, #223041 0%, #1a2431 100%);
  box-shadow: 0 0 0 1px rgba(56, 189, 248, 0.12), 0 8px 18px rgba(0, 0, 0, 0.28);
  transform: translateY(-1px);
}

.pp-tile:active {
  transform: translateY(0);
}

.pp-glyph {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: rgba(56, 189, 248, 0.06);
  color: #9ec9e6;
}

.pp-glyph svg {
  width: 28px;
  height: 28px;
  display: block;
}

.pp-label {
  font-size: 11px;
  line-height: 1.2;
  color: #c5d0de;
  text-align: center;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pp-empty {
  padding: 24px 12px;
  text-align: center;
  color: var(--pp-muted);
  font-size: 12px;
}
</style>
