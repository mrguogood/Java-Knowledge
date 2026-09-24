<template>
  <div class="tabs-bar" v-if="appStore.tabs.length > 0">
    <div class="tabs-scroll">
      <div
        v-for="tab in appStore.tabs"
        :key="tab.path"
        :class="['tab-item', { active: tab.path === currentPath }]"
        @click="handleClick(tab)"
      >
        <span class="tab-title">{{ tab.title }}</span>
        <el-icon
          v-if="tab.closable"
          class="tab-close"
          :size="12"
          @click.stop="handleClose(tab.path)"
        >
          <Close />
        </el-icon>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Close } from '@element-plus/icons-vue'
import { useAppStore } from '@/store/app'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()

const currentPath = computed(() => route.path)

const handleClick = (tab) => {
  if (tab.path !== route.path) {
    router.push(tab.path)
  }
}

const handleClose = (path) => {
  const nextPath = appStore.removeTab(path)
  if (nextPath && route.path === path) {
    router.push(nextPath)
  }
}
</script>

<style scoped>
.tabs-bar {
  background: var(--color-bg-card);
  border-bottom: 1px solid var(--color-border);
  padding: 0 var(--spacing-base);
  height: 36px;
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.tabs-scroll {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  overflow-x: auto;
  height: 100%;
}

.tabs-scroll::-webkit-scrollbar {
  height: 0;
}

.tab-item {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: 0 var(--spacing-md);
  height: 28px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-card);
  color: var(--color-text-regular);
  font-size: var(--font-size-caption);
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s;
  flex-shrink: 0;
}

.tab-item:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.tab-item.active {
  background: var(--color-primary);
  color: #fff;
  border-color: var(--color-primary);
}

.tab-item.active .tab-close:hover {
  background: rgba(255, 255, 255, 0.2);
}

.tab-close {
  border-radius: 50%;
  transition: background 0.15s;
}

.tab-close:hover {
  background: var(--color-bg-light);
}
</style>