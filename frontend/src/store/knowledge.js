import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as knowledgeApi from '@/api/knowledge'

/**
 * 知识数据 Store —— 对接后端真实 API
 */
export const useKnowledgeStore = defineStore('knowledge', () => {
  const knowledgeList = ref([])
  const total = ref(0)
  const loading = ref(false)

  /**
   * 获取知识库列表（分页条件查询）
   * @param {object} params - 查询参数 { page, pageSize, keyword, category }
   */
  const fetchKnowledgeList = async (params = {}) => {
    const { page = 1, pageSize = 10, keyword = '', category = '' } = params

    loading.value = true
    try {
      const res = await knowledgeApi.fetchKnowledgeList({
        page,
        pageSize,
        keyword,
        category
      })
      // res 结构：{ code, message, data: { records, total, page, pageSize } }
      const data = res?.data || res
      knowledgeList.value = data?.records || []
      total.value = data?.total || 0
    } catch (error) {
      knowledgeList.value = []
      total.value = 0
      console.error('知识列表加载失败：', error.message)
    } finally {
      loading.value = false
    }
  }

  return {
    knowledgeList,
    total,
    loading,
    fetchKnowledgeList
  }
})