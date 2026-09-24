import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 知识数据 Store —— 当前使用本地 Mock 数据
 *
 * TODO: 替换为真实 API 时，只需：
 *   1. 引入 axios
 *   2. 将 fetchKnowledgeList 中的 mock 替换为 axios 请求
 *   3. 在需要调用的组件的 onMounted 中 dispatch fetchKnowledgeList
 *
 * 示例：
 *   import axios from 'axios'
 *
 *   const fetchKnowledgeList = async (params) => {
 *     loading.value = true
 *     const res = await axios.get('/api/knowledge/list', { params })
 *     knowledgeList.value = res.data.records
 *     total.value = res.data.total
 *     loading.value = false
 *   }
 */
export const useKnowledgeStore = defineStore('knowledge', () => {
  const knowledgeList = ref([])
  const total = ref(0)
  const loading = ref(false)

  /**
   * Mock 数据 —— 12 条示例，覆盖各类知识条目
   */
  const mockData = [
    { id: 1,  title: 'Java 并发编程实战',            category: 'Java',    author: '张三', status: '已发布', viewCount: 1250, createTime: '2026-09-01' },
    { id: 2,  title: 'Spring Boot 微服务入门指南',    category: '框架',    author: '李四', status: '已发布', viewCount: 980,  createTime: '2026-09-02' },
    { id: 3,  title: 'MySQL 索引优化最佳实践',         category: '数据库',  author: '王五', status: '已发布', viewCount: 2340, createTime: '2026-09-03' },
    { id: 4,  title: 'Redis 缓存设计与常见问题',       category: '中间件',  author: '赵六', status: '已发布', viewCount: 1670, createTime: '2026-09-04' },
    { id: 5,  title: 'Vue 3 Composition API 深入解析', category: '前端',    author: '孙七', status: '已发布', viewCount: 890,  createTime: '2026-09-05' },
    { id: 6,  title: 'Docker 容器化部署实战',          category: '运维',    author: '周八', status: '已发布', viewCount: 1500, createTime: '2026-09-06' },
    { id: 7,  title: 'Sentinel 流量控制原理',          category: '中间件',  author: '吴九', status: '草稿',   viewCount: 0,    createTime: '2026-09-07' },
    { id: 8,  title: 'Kubernetes 集群运维手册',        category: '运维',    author: '郑十', status: '已发布', viewCount: 2100, createTime: '2026-09-08' },
    { id: 9,  title: '设计模式：观察者模式详解',         category: 'Java',    author: '张三', status: '已发布', viewCount: 720,  createTime: '2026-09-09' },
    { id: 10, title: 'Elasticsearch 搜索引擎入门',     category: '中间件',  author: '李四', status: '已发布', viewCount: 1080, createTime: '2026-09-10' },
    { id: 11, title: 'Nginx 反向代理与负载均衡配置',     category: '运维',    author: '王五', status: '草稿',   viewCount: 0,    createTime: '2026-09-11' },
    { id: 12, title: 'Python 数据分析工具 Pandas 速览', category: '其他',   author: '赵六', status: '已发布', viewCount: 630,  createTime: '2026-09-12' }
  ]

  /**
   * 获取知识列表
   * @param {object} params - 查询参数 { page, pageSize, keyword, category }
   * TODO: 对接真实 API 后去掉 mockData 的赋值
   */
  const fetchKnowledgeList = async (params = {}) => {
    const { page = 1, pageSize = 10, keyword = '', category = '' } = params

    loading.value = true

    // ---------- 模拟网络延迟 ----------
    await new Promise(resolve => setTimeout(resolve, 300))

    let filtered = [...mockData]
    if (keyword) {
      const kw = keyword.toLowerCase()
      filtered = filtered.filter(item =>
        item.title.toLowerCase().includes(kw) ||
        item.author.toLowerCase().includes(kw)
      )
    }
    if (category) {
      filtered = filtered.filter(item => item.category === category)
    }

    total.value = filtered.length
    const start = (page - 1) * pageSize
    knowledgeList.value = filtered.slice(start, start + pageSize)
    loading.value = false
  }

  return {
    knowledgeList,
    total,
    loading,
    fetchKnowledgeList
  }
})