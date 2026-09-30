<template>
  <div class="page-container ai-chat-page">
    <div class="page-card mode-banner">
      <div class="mode-banner-left">
        <el-icon :size="24"><component :is="iconComponent" /></el-icon>
        <div>
          <h3 class="mode-banner-title">{{ config.title }}</h3>
          <p class="mode-banner-desc">{{ config.description }}</p>
        </div>
      </div>
      <el-tag :type="config.tagType" size="small" effect="plain">{{ config.techTag }}</el-tag>
    </div>

    <div class="chat-card">
      <div class="chat-messages" ref="messagesContainer">
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

        <div v-if="streaming" class="message assistant">
          <div class="message-bubble markdown-body">
            <div class="message-role">🤖 AI 助手</div>
            <div class="message-text" v-html="streamingMarkdownHtml"></div>
          </div>
        </div>

        <div v-if="loading && !streaming" class="message assistant">
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
          :placeholder="placeholder"
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
import { ref, nextTick, computed, onMounted, onBeforeUnmount } from 'vue'
import { Memo, DataAnalysis } from '@element-plus/icons-vue'
import { renderMarkdown, renderStreamingMarkdown } from '@/utils/markdown'
import 'highlight.js/styles/github-dark.css'

const props = defineProps({
  kind: {
    type: String,
    required: true // 'local' | 'redis'
  }
})

const isLocal = computed(() => props.kind === 'local')

const config = computed(() =>
  isLocal.value
    ? {
        title: '本地内存记忆',
        description: 'ConcurrentHashMap 单机会话记忆，最多保留 20 条历史，多轮连续语境',
        techTag: '本地内存',
        tagType: 'success'
      }
    : {
        title: 'Redis 分布式记忆',
        description: 'Redis List 分布式会话记忆，集群共享 + 1 小时 TTL，跨实例连续语境',
        techTag: 'Redis',
        tagType: 'danger'
      }
)

const iconComponent = computed(() => (isLocal.value ? Memo : DataAnalysis))
const placeholder = computed(() =>
  isLocal.value ? '带着上下文提问，按 Enter 发送...' : '问问连续上下文的问题，按 Enter 发送...'
)

const messages = ref([])
const inputText = ref('')
const loading = ref(false)
const streaming = ref(false)
const streamingContent = ref('')
const messagesContainer = ref(null)
const sessionId = ref('')
let abortController = null
let scrollPending = false

const streamingMarkdownHtml = computed(() => {
  const html = renderStreamingMarkdown(streamingContent.value)
  const cursor = '<span class="cursor-blink">▌</span>'
  const lastClose = html.lastIndexOf('</')
  if (lastClose > 0) {
    return html.slice(0, lastClose) + cursor + html.slice(lastClose)
  }
  return html + cursor
})

const getStreamUrl = () =>
  isLocal.value ? '/api/chat/stream/local-memory' : '/api/chat/stream/redis-memory'

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

const scrollToBottomThrottled = () => {
  if (scrollPending) return
  scrollPending = true
  requestAnimationFrame(() => {
    scrollToBottom()
    scrollPending = false
  })
}

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  loading.value = true
  streaming.value = true
  streamingContent.value = ''
  await scrollToBottom()

  const controller = new AbortController()
  abortController = controller

  try {
    const url = getStreamUrl()
    const response = await fetch(url, {
      method: 'POST',
      signal: controller.signal,
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify({ message: text, sessionId: sessionId.value })
    })

    if (!response.ok) {
      throw new Error(`请求失败: HTTP ${response.status}`)
    }

    if (!response.body) {
      throw new Error('浏览器不支持 ReadableStream')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      while (buffer.includes('\n\n') || buffer.includes('\r\n\r\n')) {
        const eventEnd = buffer.includes('\r\n\r\n')
          ? buffer.indexOf('\r\n\r\n')
          : buffer.indexOf('\n\n')
        const eventBlock = buffer.slice(0, eventEnd)
        buffer = buffer.slice(eventEnd + (buffer.includes('\r\n\r\n') ? 4 : 2))

        let eventType = 'message'
        const dataLines = []

        for (const line of eventBlock.split('\n')) {
          const clean = line.replace(/\r$/, '')
          if (clean.startsWith('event:')) {
            eventType = clean.slice(6).trim()
          } else if (clean.startsWith('data:')) {
            let dataValue = clean.slice(5)
            if (dataValue.startsWith(' ')) {
              dataValue = dataValue.slice(1)
            }
            dataLines.push(dataValue)
          }
        }

        const data = dataLines.join('\n')

        if (eventType === 'message' && data) {
          streamingContent.value += data.replace(/__NL__/g, '\n')
          scrollToBottomThrottled()
        }
      }
    }

    if (streamingContent.value) {
      messages.value.push({
        role: 'assistant',
        content: streamingContent.value
      })
    }
    streaming.value = false
    streamingContent.value = ''
    loading.value = false
    await scrollToBottom()
  } catch (error) {
    if (error.name === 'AbortError') {
      streaming.value = false
      streamingContent.value = ''
      loading.value = false
      return
    }

    if (streamingContent.value) {
      messages.value.push({
        role: 'assistant',
        content: streamingContent.value
      })
    }
    if (!streamingContent.value) {
      messages.value.push({
        role: 'assistant',
        content: '抱歉，流式请求失败：' + (error.message || '未知错误')
      })
    }
    streaming.value = false
    streamingContent.value = ''
    loading.value = false
    await scrollToBottom()
  } finally {
    abortController = null
  }
}

const clearMessages = () => {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  messages.value = []
  streaming.value = false
  streamingContent.value = ''
  loading.value = false
  scrollPending = false
}

const generateSessionId = () => {
  if (crypto && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return 'session_' + Date.now() + '_' + Math.random().toString(36).slice(2, 10)
}

onMounted(() => {
  sessionId.value = generateSessionId()
  messages.value = [{
    role: 'assistant',
    content: '你好！当前使用的是「' + config.value.title + '」模式。' + config.value.techTag + ' 会记住本次会话的多轮上下文，请输入你的问题。'
  }]
})

onBeforeUnmount(() => {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
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
  position: relative;
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

.message-text :deep(.cursor-blink) {
  color: var(--color-primary);
  font-weight: 600;
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
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
</style>