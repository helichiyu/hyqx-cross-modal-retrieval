<template>
  <span class="biz-no" :title="value ?? undefined">
    {{ value }}
    <el-icon v-if="value" class="copy-icon" title="点击复制" @click.stop="copy">
      <CopyDocument />
    </el-icon>
  </span>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'

const props = defineProps<{ value?: string | null }>()

async function copy() {
  if (!props.value) {
    return
  }
  try {
    await navigator.clipboard.writeText(props.value)
    ElMessage.success('已复制')
  } catch {
    // 非 https 环境 clipboard 不可用时降级
    const ta = document.createElement('textarea')
    ta.value = props.value
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('已复制')
  }
}
</script>

<style scoped>
.copy-icon {
  cursor: pointer;
  color: #909399;
  margin-left: 2px;
  vertical-align: middle;
}
.copy-icon:hover {
  color: #409eff;
}
</style>
