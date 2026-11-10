<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { pageFiles } from '../api/file'

const total = ref(0)
const loading = ref(false)
const error = ref('')

onMounted(async () => {
  loading.value = true
  try {
    const page = await pageFiles({ page: 1, limit: 1 })
    total.value = page.total
  } catch (e) {
    error.value = e instanceof Error ? e.message : '统计加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div>
    <h2 class="cms-page-title">
      跨模态检索概览
    </h2>
    <el-alert
      v-if="error"
      :title="error"
      type="warning"
      show-icon
      :closable="false"
      class="cms-mb"
    />
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card
          v-loading="loading"
          shadow="never"
        >
          <div class="cms-stat-label">
            文件总数
          </div>
          <div class="cms-stat-value">
            {{ total }}
          </div>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="never">
          <div class="cms-stat-label">
            能力说明
          </div>
          <p class="cms-placeholder">
            检索算子（共9个，单容器6300端口）尚未迁入本工程，检索页当前为占位实现；
            文件存储与元数据服务已按 HSMTY 平台规范完成对接。
          </p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.cms-mb {
  margin-bottom: 16px;
}

.cms-stat-label {
  color: #909399;
  font-size: 13px;
}

.cms-stat-value {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 600;
}
</style>
