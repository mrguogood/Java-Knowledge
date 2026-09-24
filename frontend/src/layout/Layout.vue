<template>
  <div class="layout-container">
    <Sidebar />

    <div class="layout-right" :class="{ expanded: appStore.collapsed }">
      <Topbar />
      <TabsBar />

      <div class="layout-main">
        <router-view v-slot="{ Component }">
          <keep-alive :include="appStore.cachedNames">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup>
import { watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/store/app'
import Sidebar from './Sidebar.vue'
import Topbar from './Topbar.vue'
import TabsBar from './TabsBar.vue'

const appStore = useAppStore()
const route = useRoute()

watch(
  () => route.path,
  (path) => {
    if (route.meta?.title) {
      appStore.addTab({
        path,
        title: route.meta.title
      })
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.layout-container {
  height: 100%;
  display: flex;
  background: var(--color-bg-page);
}

.layout-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.layout-main {
  flex: 1;
  padding: var(--spacing-base);
  overflow-y: auto;
  background: var(--color-bg-page);
}
</style>