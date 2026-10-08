<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-header">
        <h2 class="page-title">知识库管理</h2>
        <el-button type="primary" :icon="Plus" @click="openUpload">上传知识</el-button>
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
        <el-button type="primary" plain :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
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
        <el-table-column prop="author" label="作者" width="110" align="center" />
        <el-table-column label="文件类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="fileTypeTagType(row.fileType)" effect="plain">
              {{ fileTypeLabel(row.fileType) }}
            </el-tag>
          </template>
        </el-table-column>
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
        <el-table-column prop="viewCount" label="浏览量" width="90" align="center" sortable />
        <el-table-column prop="createTime" label="创建时间" width="150" align="center" sortable>
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="View" @click="handleView(row)">
              查看
            </el-button>
            <el-button link type="primary" size="small" :icon="Download" @click="handleDownload(row)">
              下载
            </el-button>
            <el-button link type="primary" size="small" :icon="Edit" @click="openEdit(row)">
              编辑
            </el-button>
            <el-popconfirm
              title="确定要删除该知识条目吗？将同时删除磁盘文件"
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

    <!-- 上传对话框 -->
    <el-dialog
      v-model="dialog.uploadVisible"
      :title="dialog.isEdit ? '编辑知识' : '上传知识'"
      width="520px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="90px"
        label-position="left"
      >
        <el-form-item label="知识标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入标题" maxlength="100" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择分类" clearable style="width: 100%">
            <el-option v-for="cat in categoryOptions" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-form-item>
        <el-form-item label="上传人" prop="author">
          <el-input v-model="form.author" placeholder="请输入上传人" maxlength="50" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="已发布">已发布</el-radio>
            <el-radio value="草稿">草稿</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="dialog.isEdit ? '替换文件' : '知识文件'" prop="file">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".pdf,.doc,.docx,.xls,.xlsx"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
          >
            <el-button :icon="Upload">选择文件</el-button>
            <template #tip>
              <div class="upload-tip">支持 pdf / word / excel，大小不限</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.submitting" @click="handleSubmit">
          {{ dialog.isEdit ? '保存' : '上传' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 查看详情对话框 -->
    <el-dialog v-model="dialog.detailVisible" title="知识详情" width="560px" destroy-on-close>
      <div v-if="detail" class="detail-content">
        <h2 class="detail-title">{{ detail.title }}</h2>
        <div class="detail-meta">
          <el-tag size="small" :type="categoryTagType(detail.category)">{{ detail.category }}</el-tag>
          <el-tag size="small" v-if="detail.status" effect="plain" :type="detail.status === '已发布' ? 'success' : 'info'">
            {{ detail.status }}
          </el-tag>
        </div>
        <el-descriptions :column="2" border class="detail-descriptions">
          <el-descriptions-item label="作者">{{ detail.author || '-' }}</el-descriptions-item>
          <el-descriptions-item label="文件类型">{{ fileTypeLabel(detail.fileType) }}</el-descriptions-item>
          <el-descriptions-item label="文件大小">{{ formatSize(detail.fileSize) }}</el-descriptions-item>
          <el-descriptions-item label="浏览量">{{ detail.viewCount }}</el-descriptions-item>
          <el-descriptions-item label="文件名">{{ detail.fileName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-actions">
          <el-button type="primary" :icon="Download" @click="handleDownload(detail)">下载文件</el-button>
          <el-button :icon="Edit" @click="fromDetailToEdit">编辑</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Search, Plus, View, Edit, Delete, Download, Upload, Refresh
} from '@element-plus/icons-vue'
import { useKnowledgeStore } from '@/store/knowledge'
import {
  uploadKnowledge, updateKnowledge, deleteKnowledge,
  fetchKnowledgeDetail, downloadKnowledge
} from '@/api/knowledge'

defineOptions({ name: 'KnowledgeList' })

const knowledgeStore = useKnowledgeStore()

const searchForm = reactive({ keyword: '', category: '' })
const pagination = reactive({ page: 1, pageSize: 10 })
const categoryOptions = reactive(['Java', '框架', '数据库', '中间件', '前端', '运维', '其他'])

/* ---------- 上传 / 编辑对话框 ---------- */
const formRef = ref(null)
const uploadRef = ref(null)
const dialog = reactive({
  uploadVisible: false,
  detailVisible: false,
  isEdit: false,
  submitting: false,
  editingId: null
})

const form = reactive({
  title: '',
  category: '',
  author: '',
  status: '已发布',
  file: null
})

const formRules = {
  title: [{ required: true, message: '请输入知识标题', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  author: [{ required: true, message: '请输入上传人', trigger: 'blur' }]
}

const resetForm = () => {
  form.title = ''
  form.category = ''
  form.author = ''
  form.status = '已发布'
  form.file = null
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
}

const openUpload = () => {
  dialog.isEdit = false
  dialog.editingId = null
  resetForm()
  dialog.uploadVisible = true
}

const openEdit = (row) => {
  dialog.isEdit = true
  dialog.editingId = row.id
  form.title = row.title
  form.category = row.category
  form.author = row.author
  form.status = row.status || '已发布'
  form.file = null
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
  dialog.uploadVisible = true
}

const handleFileChange = (file) => {
  form.file = file.raw
}

const handleFileRemove = () => {
  form.file = null
}

const handleSubmit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  // 上传和编辑都必须有文件（编辑时若未换文件则不能提交新文件逻辑，仅更新元信息）
  if (!dialog.isEdit && !form.file) {
    ElMessage.warning('请先选择要上传的文件')
    return
  }

  const formData = new FormData()
  if (dialog.isEdit && dialog.editingId) {
    formData.append('id', dialog.editingId)
  }
  formData.append('title', form.title)
  formData.append('category', form.category)
  formData.append('author', form.author)
  formData.append('status', form.status)
  if (form.file) {
    formData.append('file', form.file)
  }

  dialog.submitting = true
  try {
    if (dialog.isEdit) {
      const res = await updateKnowledge(formData)
      ElMessage.success(res?.msg || '更新成功')
    } else {
      const res = await uploadKnowledge(formData)
      ElMessage.success(res?.msg || '上传成功')
    }
    dialog.uploadVisible = false
    pagination.page = 1
    handleSearch()
  } catch (error) {
    ElMessage.error('提交失败：' + (error.message || '未知错误'))
  } finally {
    dialog.submitting = false
  }
}

/* ---------- 详情 ---------- */
const detail = ref(null)

const openDetail = async (row) => {
  try {
    const res = await fetchKnowledgeDetail(row.id)
    detail.value = res?.data || row
    dialog.detailVisible = true
  } catch (error) {
    ElMessage.error('加载详情失败：' + (error.message || '未知错误'))
  }
}

const handleView = (row) => openDetail(row)

const fromDetailToEdit = () => {
  dialog.detailVisible = false
  if (detail.value) {
    openEdit(detail.value)
  }
}

/* ---------- 下载 ---------- */
const triggerDownload = (blob, filename) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

async function handleDownload(row) {
  try {
    const res = await downloadKnowledge(row.id)
    if (!res) {
      ElMessage.warning('文件不存在或已删除')
      return
    }
    const filename = row.fileName || `知识_${row.id}`
    triggerDownload(res, filename)
    ElMessage.success('下载成功')
  } catch (error) {
    ElMessage.error('下载失败：' + (error.message || '未知错误'))
  }
}

/* ---------- 删除 ---------- */
const handleDelete = async (row) => {
  try {
    const res = await deleteKnowledge(row.id)
    ElMessage.success(res?.msg || '删除成功')
    // 删除当前页最后一条时回退一页
    if (knowledgeStore.knowledgeList.length === 1 && pagination.page > 1) {
      pagination.page -= 1
    }
    handleSearch()
  } catch (error) {
    ElMessage.error('删除失败：' + (error.message || '未知错误'))
  }
}

/* ---------- 检索 ---------- */
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

/* ---------- 工具函数 ---------- */
const categoryTagType = (category) => {
  const typeMap = {
    'Java': '',
    '框架': 'success',
    '数据库': 'warning',
    '中间件': 'danger',
    '前端': 'info',
    '运维': 'primary',
    '其他': 'info'
  }
  return typeMap[category] || ''
}

const fileTypeTagType = (fileType) => {
  const typeMap = { pdf: 'danger', word: 'primary', excel: 'success' }
  return typeMap[fileType] || 'info'
}

const fileTypeLabel = (fileType) => {
  const typeMap = { pdf: 'PDF', word: 'Word', excel: 'Excel' }
  return typeMap[fileType] || (fileType || '-')
}

const formatSize = (bytes) => {
  if (!bytes && bytes !== 0) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

const formatTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  if (isNaN(date.getTime())) return time
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
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

.upload-tip {
  font-size: var(--font-size-caption);
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.detail-title {
  font-size: var(--font-size-section-title);
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 var(--spacing-sm);
}

.detail-meta {
  display: flex;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-lg);
}

.detail-descriptions {
  margin-bottom: var(--spacing-lg);
}

.detail-actions {
  display: flex;
  gap: var(--spacing-md);
  justify-content: flex-end;
}
</style>