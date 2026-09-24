<template>
  <AiChatBase :mode-config="modeConfig" />
</template>

<script setup>
import AiChatBase from './AiChatBase.vue'
import { sendChatWithConcurrency } from '@/api/chat'

defineOptions({ name: 'ConcurrencyChat' })

const modeConfig = {
  title: '并发控制',
  description: 'Semaphore 信号量限制并发大模型调用数，保护后端稳定性',
  techTag: 'Semaphore 信号量',
  tagType: 'warning',
  icon: 'Connection',
  apiFn: sendChatWithConcurrency
}
</script>