<template>
  <div class="not-found">
    <div class="nf-content">
      <h1 class="nf-code">404</h1>
      <p class="nf-text">抱歉，您访问的页面不存在</p>
      <el-button type="primary" @click="$router.push('/home')">返回首页</el-button>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.not-found {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-page);
}

.nf-content {
  text-align: center;
}

.nf-code {
  font-size: 72px;
  font-weight: 700;
  color: var(--color-primary);
  line-height: 1;
  margin-bottom: var(--spacing-base);
}

.nf-text {
  font-size: var(--font-size-section-title);
  color: var(--color-text-secondary);
  margin-bottom: var(--spacing-xl);
}
</style>