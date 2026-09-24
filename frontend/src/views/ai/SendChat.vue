<template>
  <AiChatBase :mode-config="modeConfig" />
</template>

<script setup>
import AiChatBase from './AiChatBase.vue'
import { sendChatMessage } from '@/api/chat'

defineOptions({ name: 'SendChat' })

const modeConfig = {
  title: '单轮对话',
  description: '标准问答模式，每次独立调用通义千问大模型',
  techTag: 'Sentinel 限流熔断',
  tagType: 'primary',
  icon: 'ChatDotRound',
  apiFn: sendChatMessage
}
</script>