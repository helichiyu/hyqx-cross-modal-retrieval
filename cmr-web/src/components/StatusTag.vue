<template>
  <el-tag :type="tagType" size="small" effect="light">{{ label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DictItem } from '../api/types'

const props = defineProps<{
  value: string | null | undefined
  dict: DictItem[]
}>()

const label = computed(
  () => props.dict.find((d) => d.alias === props.value)?.label ?? props.value ?? '-',
)

// 状态语义着色：绿=终态成功/可检索/生效，蓝=进行中，橙=卡住/挂起，红=失败/剔除/隔离
const GREEN = new Set([
  'stored', 'searchable', 'done', 'published', 'complete', 'closed', 'active', 'adopted', 'pass',
])
const BLUE = new Set([
  'ingest', 'scoring', 'indexing', 'running', 'ready', 'draft', 'labeling', 'processing', 'retrying',
])
const ORANGE = new Set(['stuck', 'pending', 'candidate', 'unadopted', 'suspend', 'confirm_production'])
const RED = new Set(['failed', 'rejected', 'isolated', 'discarded'])

const tagType = computed(() => {
  const v = props.value ?? ''
  if (GREEN.has(v)) {
    return 'success'
  }
  if (BLUE.has(v)) {
    return 'primary'
  }
  if (ORANGE.has(v)) {
    return 'warning'
  }
  if (RED.has(v)) {
    return 'danger'
  }
  return 'info'
})
</script>
