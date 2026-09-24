<template>
  <div class="sidebar" :class="{ collapsed: appStore.collapsed }">
    <!-- Logo 区域 -->
    <div class="sidebar-logo">
      <div class="logo-icon">
        <el-icon :size="24"><DataBoard /></el-icon>
      </div>
      <transition name="fade">
        <span v-show="!appStore.collapsed" class="logo-title">企业知识库管理平台</span>
      </transition>
    </div>

    <!-- 菜单区域 -->
    <div class="sidebar-menu">
      <el-menu
        :default-active="activeMenu"
        :collapse="appStore.collapsed"
        :collapse-transition="false"
        background-color="#001529"
        text-color="rgba(255,255,255,0.65)"
        active-text-color="#fff"
        router
      >
        <!-- 遍历菜单列表，支持二级菜单 -->
        <template v-for="menu in appStore.menuList" :key="menu.path">
          <!-- 有子菜单 -->
          <el-sub-menu v-if="menu.children && menu.children.length > 0" :index="menu.path">
            <template #title>
              <el-icon><component :is="menu.icon" /></el-icon>
              <span>{{ menu.name }}</span>
            </template>
            <el-menu-item
              v-for="child in menu.children"
              :key="child.path"
              :index="child.path"
            >
              <el-icon v-if="child.icon"><component :is="child.icon" /></el-icon>
              <span>{{ child.name }}</span>
            </el-menu-item>
          </el-sub-menu>

          <!-- 无子菜单 -->
          <el-menu-item v-else :index="menu.path">
            <el-icon><component :is="menu.icon" /></el-icon>
            <span>{{ menu.name }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/store/app'

const route = useRoute()
const appStore = useAppStore()

const activeMenu = computed(() => {
  return route.path
})
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  height: 100%;
  background-color: var(--color-sidebar-bg);
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
  overflow: hidden;
}

.sidebar.collapsed {
  width: var(--sidebar-collapsed-width);
}

.sidebar-logo {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 var(--spacing-base);
  gap: var(--spacing-sm);
  border-bottom: 1px solid var(--color-sidebar-border);
  flex-shrink: 0;
}

.logo-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  color: var(--color-primary);
}

.logo-title {
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
}

.sidebar-menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

.sidebar-menu :deep(.el-menu) {
  border-right: none;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background-color: var(--color-primary) !important;
  color: #fff !important;
}

.sidebar-menu :deep(.el-menu-item:hover),
.sidebar-menu :deep(.el-sub-menu__title:hover) {
  background-color: var(--color-sidebar-hover-bg) !important;
}

.sidebar-menu :deep(.el-sub-menu .el-menu) {
  background-color: var(--color-sidebar-submenu-bg) !important;
}

.sidebar-menu :deep(.el-sub-menu .el-menu .el-menu-item:hover) {
  background-color: rgba(255, 255, 255, 0.12) !important;
}

.sidebar-menu :deep(.el-sub-menu .el-menu .el-menu-item.is-active) {
  background-color: var(--color-primary) !important;
  color: #fff !important;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.sidebar-menu::-webkit-scrollbar {
  width: var(--spacing-xs);
}
.sidebar-menu::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.15);
  border-radius: 2px;
}
</style>