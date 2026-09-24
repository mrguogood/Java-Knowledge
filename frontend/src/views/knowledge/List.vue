<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-header">
        <h2 class="page-title">知识列表</h2>
        <el-button type="primary" :icon="Plus" @click="handleAdd">新建知识</el-button>
      </div>

      <div class="search-bar">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索标题、作者..."
          :prefix-icon="Search"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select
          v-model="searchForm.category"
          placeholder="全部分类"
          clearable
          class="search-select"
          @change="handleSearch"
        >
          <el-option
            v-for="cat in categoryOptions"
            :key="cat"
            :label="cat"
            :value="cat"
          />
        </el-select>
        <el-button @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table
        :data="knowledgeStore.knowledgeList"
        v-loading="knowledgeStore.loading"
        stripe
        border
        style="width: 100%"
        :header-cell-style="{ background: 'var(--color-bg-light)', color: 'var(--color-text-primary)', fontWeight: 600 }"
      >
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="title" label="知识标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="handleView(row)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="categoryTagType(row.category)">
              {{ row.category }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="author" label="作者" width="100" align="center" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.status === '已发布' ? 'success' : 'info'"
              effect="plain"
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="viewCount" label="浏览量" width="100" align="center" sortable />
        <el-table-column prop="createTime" label="创建时间" width="130" align="center" sortable />
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="View" @click="handleView(row)">
              查看
            </el-button>
            <el-button link type="primary" size="small" :icon="Edit" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-popconfirm
              title="确定要删除该知识条目吗？"
              confirm-button-text="确定"
              confirm-button-type="danger"
              cancel-button-text="取消"
              @confirm="handleDelete(row)"
            >
              <template #reference>
                <el-button link type="danger" size="small" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无知识数据" />
        </template>
      </el-table>

      <div class="pagination-wrapper" v-if="knowledgeStore.total > 0">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[5, 10, 20, 50]"
          :total="knowledgeStore.total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSearch"
          @current-change="handleSearch"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Plus, View, Edit, Delete } from '@element-plus/icons-vue'
import { useKnowledgeStore } from '@/store/knowledge'

defineOptions({ name: 'KnowledgeList' })

const knowledgeStore = useKnowledgeStore()

const searchForm = reactive({
  keyword: '',
  category: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const categoryOptions = reactive(['Java', '框架', '数据库', '中间件', '前端', '运维', '其他'])

const categoryTagType = (category) => {
  const typeMap = {
    'Java': '',
    '框架': 'success',
    '数据库': 'warning',
    '中间件': 'danger',
    '前端': 'info',
    '运维': '',
    '其他': 'info'
  }
  return typeMap[category] || ''
}

const handleSearch = () => {
  knowledgeStore.fetchKnowledgeList({
    page: pagination.page,
    pageSize: pagination.pageSize,
    keyword: searchForm.keyword,
    category: searchForm.category
  })
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.category = ''
  pagination.page = 1
  handleSearch()
}

const handleAdd = () => {
  ElMessage.info('新建知识（功能开发中）')
}

const handleView = (row) => {
  ElMessage.info(`查看知识：${row.title}`)
}

const handleEdit = (row) => {
  ElMessage.info(`编辑知识：${row.title}`)
}

const handleDelete = (row) => {
  ElMessage.success(`已删除：${row.title}`)
}

onMounted(() => {
  handleSearch()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-lg);
}

.page-title {
  font-size: var(--font-size-page-title);
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.search-bar {
  display: flex;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
  flex-wrap: wrap;
}

.search-input {
  width: 240px;
}

.search-select {
  width: 150px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--spacing-lg);
}

:deep(.el-table th) {
  font-size: 13px;
}

:deep(.el-table td) {
  font-size: 13px;
}
</style>