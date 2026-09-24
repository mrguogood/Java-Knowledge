<template>
  <AiChatBase :mode-config="modeConfig" />
</template>

<script setup>
import AiChatBase from './AiChatBase.vue'
import { sendChatWithCache } from '@/api/chat'

defineOptions({ name: 'CacheChat' })

const modeConfig = {
  title: '缓存问答',
  description: '优先从 Caffeine 本地缓存读取，命中即返回，提升响应速度',
  techTag: 'Caffeine 缓存',
  tagType: 'success',
  icon: 'Coin',
  apiFn: sendChatWithCache
}
</script>