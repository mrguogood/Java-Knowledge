<template>
  <div class="breadcrumb">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item
        v-for="(item, index) in breadcrumbs"
        :key="index"
        :to="item.path ? { path: item.path } : undefined"
      >
        {{ item.name }}
      </el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/store/app'

const route = useRoute()
const appStore = useAppStore()

/**
 * 从当前路由 meta.breadcrumb 读取面包屑
 * 若 meta 未配置则回退到 store 中的默认值
 */
const breadcrumbs = computed(() => {
  const metaBreadcrumb = route.meta?.breadcrumb
  if (metaBreadcrumb && metaBreadcrumb.length > 0) {
    appStore.setBreadcrumbs(metaBreadcrumb)
    return metaBreadcrumb
  }
  return appStore.breadcrumbs
})
</script>

<style scoped>
.breadcrumb {
  padding: 0 var(--spacing-xs);
}

.breadcrumb :deep(.el-breadcrumb__inner) {
  font-size: var(--font-size-body);
  color: var(--color-text-regular);
}

.breadcrumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--color-text-primary);
  font-weight: 500;
}
</style>