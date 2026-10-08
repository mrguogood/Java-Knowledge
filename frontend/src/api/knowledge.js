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
 * 分页查询知识列表
 * @param {object} params { page, pageSize, keyword, category }
 */
export function fetchKnowledgeList(params) {
  return apiClient.post('/knowledge/list', params)
}

/**
 * 上传知识文件（multipart/form-data）
 */
export function uploadKnowledge(formData) {
  return apiClient.post('/knowledge/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 查询知识详情（浏览量 +1）
 */
export function fetchKnowledgeDetail(id) {
  return apiClient.post('/knowledge/detail', { id })
}

/**
 * 下载/预览知识文件
 */
export function downloadKnowledge(id) {
  return apiClient.post('/knowledge/download', { id }, { responseType: 'blob' })
}

/**
 * 更新知识（可传新文件覆盖）
 */
export function updateKnowledge(formData) {
  return apiClient.post('/knowledge/update', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 删除知识记录 + 物理文件
 */
export function deleteKnowledge(id) {
  return apiClient.post('/knowledge/delete', { id })
}