import axios from 'axios'

const apiClient = axios.create({
  baseURL: '/api',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json'
  }
})

apiClient.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '请求失败'
    console.error('API Error:', message)
    return Promise.reject(new Error(message))
  }
)

/**
 * 单轮对话（Sentinel 限流/熔断保护）
 */
export function sendChatMessage(message) {
  return apiClient.post('/chat/send', { message })
}

/**
 * 带缓存的问答（Caffeine 本地缓存）
 */
export function sendChatWithCache(message) {
  return apiClient.post('/chat/cache', { message })
}

/**
 * 带并发控制的问答（Semaphore 信号量）
 */
export function sendChatWithConcurrency(message) {
  return apiClient.post('/chat/concurrency', { message })
}

export default apiClient