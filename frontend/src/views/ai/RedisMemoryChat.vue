<template>
  <MemoryChatBase kind="redis" />
</template>

<script setup>
import MemoryChatBase from './MemoryChatBase.vue'

defineOptions({ name: 'RedisMemoryChat' })
</script>