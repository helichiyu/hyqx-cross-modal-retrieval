<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { downloadFile, pageFiles, removeFiles, uploadFile, type FileObject } from '../api/file'

const loading = ref(false)
const uploading = ref(false)
const rows = ref<FileObject[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  limit: 10,
  keyword: '',
})

async function load() {
  loading.value = true
  try {
    const page = await pageFiles(query)
    rows.value = page.rows
    total.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '查询失败')
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

async function handleUpload(options: UploadRequestOptions) {
  uploading.value = true
  try {
    await uploadFile(options.file, 'cmr')
    ElMessage.success('上传成功')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '上传失败')
  } finally {
    uploading.value = false
  }
}

async function handleDownload(row: FileObject) {
  try {
    const { blob, filename } = await downloadFile(row.id)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename || row.originalName
    link.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '下载失败')
  }
}

async function handleRemove(row: FileObject) {
  try {
    await ElMessageBox.confirm(`确认删除文件「${row.originalName}」？`, '提示', { type: 'warning' })
    await removeFiles([row.id])
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e instanceof Error ? e.message : '删除失败')
    }
  }
}

function formatSize(size?: number) {
  if (!size) {
    return '-'
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

onMounted(load)
</script>

<template>
  <div>
    <h2 class="cms-page-title">
      文件管理
    </h2>

    <el-card shadow="never">
      <div class="cms-toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="按文件名搜索"
          clearable
          style="width: 240px"
          @keyup.enter="search"
        />
        <el-button
          type="primary"
          @click="search"
        >
          查询
        </el-button>
        <el-upload
          :show-file-list="false"
          :http-request="handleUpload"
          :disabled="uploading"
        >
          <el-button
            type="success"
            :loading="uploading"
          >
            上传文件
          </el-button>
        </el-upload>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        border
      >
        <el-table-column
          prop="originalName"
          label="文件名"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column
          prop="bizType"
          label="业务类型"
          width="120"
        />
        <el-table-column
          label="大小"
          width="110"
        >
          <template #default="{ row }">
            {{ formatSize(row.size) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="storeType"
          label="存储"
          width="100"
        />
        <el-table-column
          prop="createTime"
          label="上传时间"
          width="180"
        />
        <el-table-column
          label="操作"
          width="160"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="handleDownload(row)"
            >
              下载
            </el-button>
            <el-button
              link
              type="danger"
              @click="handleRemove(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.limit"
        class="cms-pagination"
        layout="total, prev, pager, next, sizes"
        :total="total"
        :page-sizes="[10, 20, 50]"
        @current-change="load"
        @size-change="search"
      />
    </el-card>
  </div>
</template>

<style scoped>
.cms-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.cms-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
