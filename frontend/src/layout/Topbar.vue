<template>
  <div class="topbar">
    <div class="topbar-left">
      <!-- 侧边栏折叠按钮 -->
      <el-icon class="collapse-btn" @click="appStore.toggleCollapse" :size="20">
        <Fold v-if="!appStore.collapsed" />
        <Expand v-else />
      </el-icon>

      <!-- 面包屑 -->
      <Breadcrumb />
    </div>

    <div class="topbar-right">
      <!-- 消息通知 -->
      <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="notice-badge">
        <el-icon class="topbar-icon" :size="20"><Bell /></el-icon>
      </el-badge>

      <!-- 用户信息下拉 -->
      <el-dropdown trigger="click">
        <div class="user-info">
          <el-avatar :size="32" :src="userStore.userInfo.avatar" :icon="UserFilled" />
          <span class="username">{{ userStore.userInfo.nickname }}</span>
          <el-icon class="arrow-icon"><ArrowDown /></el-icon>
        </div>

        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item>
              <el-icon><User /></el-icon>
              我的
            </el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Fold, Expand, Bell, ArrowDown, User, SwitchButton, UserFilled
} from '@element-plus/icons-vue'
import { useAppStore } from '@/store/app'
import { useUserStore } from '@/store/user'
import Breadcrumb from './Breadcrumb.vue'

const appStore = useAppStore()
const userStore = useUserStore()

const unreadCount = computed(() => {
  return userStore.notifications.filter(n => !n.read).length
})

// TODO: 退出登录 —— 对接真实 API 后清除 token + 跳转登录页
const handleLogout = () => {
  ElMessage.success('已退出登录')
}
</script>

<style scoped>
.topbar {
  height: var(--topbar-height);
  background: var(--color-bg-card);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-lg);
  box-shadow: var(--shadow-sm);
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-base);
}

.collapse-btn {
  cursor: pointer;
  color: var(--color-text-regular);
  flex-shrink: 0;
}

.collapse-btn:hover {
  color: var(--color-primary);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
}

.notice-badge {
  cursor: pointer;
}

.topbar-icon {
  color: var(--color-text-regular);
  cursor: pointer;
}

.topbar-icon:hover {
  color: var(--color-primary);
}

.user-info {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  cursor: pointer;
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--radius-sm);
  transition: background 0.2s;
}

.user-info:hover {
  background: var(--color-bg-light);
}

.username {
  font-size: var(--font-size-body);
  color: var(--color-text-primary);
}

.arrow-icon {
  color: var(--color-text-secondary);
  font-size: var(--font-size-caption);
}
</style>