<template>
  <div class="page-container">
    <div class="page-card home-page">
      <div class="welcome-section">
        <h1 class="welcome-title">👋 欢迎回来，{{ userStore.userInfo.nickname }}</h1>
        <p class="welcome-subtitle">企业知识库管理平台 — 集中管理团队技术知识资产</p>
      </div>

      <el-row :gutter="16" class="stats-row">
        <el-col :xs="24" :sm="12" :md="6" v-for="stat in stats" :key="stat.label">
          <div class="stat-card" :style="{ borderLeftColor: stat.color }">
            <div class="stat-icon">
              <el-icon :size="28" :color="stat.color"><component :is="stat.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stat.value }}</div>
              <div class="stat-label">{{ stat.label }}</div>
            </div>
          </div>
        </el-col>
      </el-row>

      <div class="quick-actions">
        <h3 class="section-title">快捷操作</h3>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="8" v-for="action in quickActions" :key="action.path">
            <div class="action-card" @click="$router.push(action.path)">
              <el-icon :size="22" :color="'var(--color-primary)'"><component :is="action.icon" /></el-icon>
              <span class="action-label">{{ action.label }}</span>
            </div>
          </el-col>
        </el-row>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import {
  Document, View, User, Collection, Reading, Plus
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

defineOptions({ name: 'Home' })

const userStore = useUserStore()

const stats = reactive([
  { label: '知识总数',  value: 128, icon: 'Document',  color: 'var(--color-primary)' },
  { label: '今日访问',  value: 56,  icon: 'View',      color: 'var(--color-success)' },
  { label: '本周新增',  value: 12,  icon: 'Collection', color: 'var(--color-warning)' },
  { label: '活跃用户',  value: 8,   icon: 'User',      color: 'var(--color-danger)' }
])

const quickActions = [
  { label: '知识列表',  path: '/knowledge/list', icon: 'Reading' },
  { label: '新建知识',  path: '/knowledge/list', icon: 'Plus' }
]
</script>

<style scoped>
.home-page {
  min-height: calc(100% - var(--spacing-xxl));
}

.welcome-section {
  margin-bottom: var(--spacing-xl);
}

.welcome-title {
  font-size: var(--font-size-welcome);
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-sm);
}

.welcome-subtitle {
  font-size: var(--font-size-body);
  color: var(--color-text-secondary);
}

.stats-row {
  margin-bottom: var(--spacing-xl);
}

.stat-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-left: 4px solid var(--color-primary);
  border-radius: var(--radius-sm);
  padding: var(--spacing-lg) var(--spacing-base);
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  transition: box-shadow 0.3s;
}

.stat-card:hover {
  box-shadow: var(--shadow-md);
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: var(--font-size-stat-value);
  font-weight: 700;
  color: var(--color-text-primary);
}

.stat-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-top: 2px;
}

.section-title {
  font-size: var(--font-size-section-title);
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-md);
  padding-bottom: var(--spacing-sm);
  border-bottom: 1px solid var(--color-border);
}

.action-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-base) var(--spacing-lg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.3s;
}

.action-card:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.action-label {
  font-size: var(--font-size-body);
  font-weight: 500;
}
</style>