<template>
  <div class="page-container ai-chat-page">
    <div class="page-card mode-banner">
      <div class="mode-banner-left">
        <el-icon :size="24"><component :is="modeConfig.icon" /></el-icon>
        <div>
          <h3 class="mode-banner-title">{{ modeConfig.title }}</h3>
          <p class="mode-banner-desc">{{ modeConfig.description }}</p>
        </div>
      </div>
      <el-tag :type="modeConfig.tagType" size="small" effect="plain">{{ modeConfig.techTag }}</el-tag>
    </div>

    <div class="chat-card">
      <div class="chat-messages" ref="messagesContainer">
        <div v-if="messages.length === 0" class="empty-state">
          <el-icon :size="48" color="var(--color-text-placeholder)"><ChatDotRound /></el-icon>
          <p>开始一段对话吧</p>
        </div>

        <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['message', msg.role]"
        >
          <div class="message-bubble" :class="{ 'markdown-body': msg.role === 'assistant' }">
            <div class="message-role">
              {{ msg.role === 'user' ? '👤 你' : '🤖 AI 助手' }}
            </div>
            <div
              v-if="msg.role === 'assistant'"
              class="message-text"
              v-html="renderMarkdown(msg.content)"
            ></div>
            <div v-else class="message-text">{{ msg.content }}</div>
          </div>
        </div>

        <div v-if="loading" class="message assistant">
          <div class="message-bubble">
            <div class="message-role">🤖 AI 助手</div>
            <div class="typing-indicator">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-footer">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="1"
          :autosize="{ minRows: 3, maxRows: 6 }"
          placeholder="输入你的问题，按 Enter 发送..."
          :disabled="loading"
          @keydown.enter.exact.prevent="sendMessage"
        />
        <div class="chat-footer-actions">
          <el-button
            type="primary"
            :loading="loading"
            :disabled="!inputText.trim()"
            @click="sendMessage"
          >
            发送
          </el-button>
          <el-button @click="clearMessages" :disabled="loading">清空</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { ChatDotRound } from '@element-plus/icons-vue'
import { renderMarkdown } from '@/utils/markdown'
import 'highlight.js/styles/github-dark.css'

const props = defineProps({
  modeConfig: {
    type: Object,
    required: true
  }
})

const messages = ref([])
const inputText = ref('')
const loading = ref(false)
const messagesContainer = ref(null)

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  loading.value = true
  await scrollToBottom()

  try {
    const response = await props.modeConfig.apiFn(text)
    messages.value.push({
      role: 'assistant',
      content: response.data || response
    })
  } catch (error) {
    messages.value.push({
      role: 'assistant',
      content: '抱歉，请求失败：' + (error.message || '未知错误')
    })
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

const clearMessages = () => {
  messages.value = []
}

onMounted(() => {
  messages.value = [{
    role: 'assistant',
    content: `你好！当前使用的是「${props.modeConfig.title}」模式。${props.modeConfig.description}，请输入你的问题。`
  }]
})
</script>

<style scoped>
.ai-chat-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.mode-banner {
  padding: var(--spacing-md) var(--spacing-lg);
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
}

.mode-banner-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.mode-banner-title {
  font-size: var(--font-size-section-title);
  font-weight: 600;
  color: var(--color-text-primary);
}

.mode-banner-desc {
  font-size: var(--font-size-caption);
  color: var(--color-text-secondary);
  margin-top: 2px;
}

.chat-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-placeholder);
}

.empty-state p {
  margin-top: var(--spacing-md);
  font-size: var(--font-size-body);
}

.message {
  display: flex;
}

.message.user {
  justify-content: flex-end;
}

.message.user .message-bubble {
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-light));
  color: #fff;
  border-radius: var(--radius-lg) var(--radius-lg) var(--radius-sm) var(--radius-lg);
  max-width: 70%;
}

.message.assistant .message-bubble {
  background: var(--color-bg-light);
  color: var(--color-text-primary);
  border-radius: var(--radius-lg) var(--radius-lg) var(--radius-lg) var(--radius-sm);
  max-width: 85%;
}

.message-bubble {
  padding: var(--spacing-md) var(--spacing-base);
}

.message-role {
  font-size: var(--font-size-mini);
  font-weight: 600;
  margin-bottom: var(--spacing-xs);
  opacity: 0.7;
}

.message-text {
  font-size: var(--font-size-body);
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.typing-indicator {
  display: flex;
  gap: var(--spacing-xs);
  padding: var(--spacing-xs) 0;
}

.typing-indicator span {
  width: 7px;
  height: 7px;
  background: var(--color-text-secondary);
  border-radius: 50%;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-5px); opacity: 1; }
}

.chat-footer {
  display: flex;
  gap: var(--spacing-sm);
  padding: var(--spacing-md) var(--spacing-lg);
  background: var(--color-bg-light);
  border-top: 1px solid var(--color-border);
  align-items: center;
  flex-shrink: 0;
}

.chat-footer :deep(.el-textarea__inner) {
  background: var(--color-bg-card);
  border-color: var(--color-border);
}

.chat-footer :deep(.el-textarea__inner:focus) {
  border-color: var(--color-primary);
}

.chat-footer-actions {
  display: flex;
  gap: var(--spacing-sm);
  flex-shrink: 0;
  padding-bottom: 1px;
}

.markdown-body .message-text {
  white-space: normal;
}

.markdown-body .message-text :deep(h1),
.markdown-body .message-text :deep(h2),
.markdown-body .message-text :deep(h3),
.markdown-body .message-text :deep(h4) {
  margin-top: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
  font-weight: 600;
  color: var(--color-text-primary);
}

.markdown-body .message-text :deep(h1) { font-size: 1.35em; }
.markdown-body .message-text :deep(h2) { font-size: 1.15em; border-bottom: 1px solid var(--color-border); padding-bottom: 3px; }
.markdown-body .message-text :deep(h3) { font-size: 1.05em; }
.markdown-body .message-text :deep(h4) { font-size: 1em; }

.markdown-body .message-text :deep(p) { margin-bottom: var(--spacing-sm); }
.markdown-body .message-text :deep(p:last-child) { margin-bottom: 0; }

.markdown-body .message-text :deep(ul),
.markdown-body .message-text :deep(ol) { padding-left: 18px; margin-bottom: var(--spacing-sm); }
.markdown-body .message-text :deep(li) { margin-bottom: 3px; }

.markdown-body .message-text :deep(.inline-code) {
  background: var(--color-primary-bg);
  padding: 1px 5px;
  border-radius: var(--radius-sm);
  font-family: 'Consolas', 'Courier New', monospace;
  font-size: 0.88em;
  color: var(--color-primary);
}

.markdown-body .message-text :deep(.code-block) {
  background: #1e1e2e;
  border-radius: var(--radius-md);
  margin: var(--spacing-sm) 0;
  overflow: hidden;
}

.markdown-body .message-text :deep(.code-block-header) {
  padding: var(--spacing-xs) var(--spacing-md);
  font-size: 11px;
  font-weight: 600;
  color: #89b4fa;
  background: #181825;
  letter-spacing: 0.5px;
  user-select: none;
}

.markdown-body .message-text :deep(.code-block pre) {
  margin: 0;
  padding: var(--spacing-md) 14px;
  background: transparent;
  overflow-x: auto;
}

.markdown-body .message-text :deep(.code-block pre code) {
  background: transparent;
  padding: 0;
  color: #cdd6f4;
  font-size: 0.82em;
  line-height: 1.6;
  font-family: 'Consolas', 'Courier New', monospace;
}

.markdown-body .message-text :deep(blockquote) {
  border-left: 3px solid var(--color-primary);
  padding: 5px var(--spacing-md);
  margin: var(--spacing-sm) 0;
  background: var(--color-primary-bg);
  color: var(--color-text-regular);
}

.markdown-body .message-text :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: var(--spacing-sm) 0;
  font-size: 0.88em;
}

.markdown-body .message-text :deep(th),
.markdown-body .message-text :deep(td) {
  border: 1px solid var(--color-border);
  padding: var(--spacing-sm) 10px;
  text-align: left;
}

.markdown-body .message-text :deep(th) { background: var(--color-bg-light); font-weight: 600; }
.markdown-body .message-text :deep(tr:nth-child(even)) { background: var(--color-bg-light); }
.markdown-body .message-text :deep(a) { color: var(--color-primary); text-decoration: none; }
.markdown-body .message-text :deep(a:hover) { text-decoration: underline; }
.markdown-body .message-text :deep(hr) { border: none; border-top: 1px solid var(--color-border); margin: 10px 0; }
.markdown-body .message-text :deep(strong) { font-weight: 600; color: var(--color-text-primary); }

.markdown-body .message-text :deep(.hljs-keyword)  { color: #c792ea; }
.markdown-body .message-text :deep(.hljs-string)   { color: #c3e88d; }
.markdown-body .message-text :deep(.hljs-number)   { color: #f78c6c; }
.markdown-body .message-text :deep(.hljs-comment)  { color: #676e95; font-style: italic; }
.markdown-body .message-text :deep(.hljs-function) { color: #82aaff; }
.markdown-body .message-text :deep(.hljs-title)    { color: #82aaff; }
.markdown-body .message-text :deep(.hljs-type)     { color: #ffcb6b; }
.markdown-body .message-text :deep(.hljs-built_in) { color: #f78c6c; }
</style>