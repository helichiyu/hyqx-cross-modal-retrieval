<template>
  <div class="page-card">
    <el-card v-if="detail">
      <div class="toolbar">
        <div class="summary-title">
          任务 <BizNoTag :value="detail.task.bizNo" />
          <StatusTag :value="detail.task.status" :dict="statusDict" />
          <el-tag size="small" effect="plain">{{ dictLabel(typeDict, detail.task.type) }}</el-tag>
        </div>
        <div class="grow" />
        <span v-if="detail.task.refBizNo" class="text-secondary">
          关联对象：<BizNoTag :value="detail.task.refBizNo" />
        </span>
        <el-button @click="$router.back()">返回</el-button>
      </div>

      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="当前步骤">
          {{ detail.task.currentStep || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="重试次数">
          {{ detail.task.retryCount ?? 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">
          {{ fmtTime(detail.task.startedTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="结束时间">
          {{ fmtTime(detail.task.finishedTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="失败侧">
          <el-tag v-if="detail.task.failType === 'engine'" size="small" type="warning">引擎侧（自动重试）</el-tag>
          <el-tag v-else-if="detail.task.failType === 'data'" size="small" type="danger">数据侧（转隔离）</el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="失败处置">
          <StatusTag v-if="detail.task.failDisposal" :value="detail.task.failDisposal" :dict="disposalDict" />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.task.errorMsg" label="错误信息" :span="3">
          <span class="mono" style="color: #f56c6c">{{ detail.task.errorMsg }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">步骤日志</el-divider>
      <el-timeline style="max-width: 720px; padding-left: 8px">
        <el-timeline-item
          v-for="log in detail.logs"
          :key="log.id"
          :timestamp="fmtTime(log.createTime)"
          :type="logType(log.status)"
          :hollow="log.status !== 'ok'"
        >
          <b>{{ log.step }}</b>
          <span class="text-secondary" style="margin-left: 8px">{{ statusText(log.status) }}</span>
          <div v-if="log.message" class="text-secondary mono" style="margin-top: 2px">
            {{ log.message }}
          </div>
        </el-timeline-item>
        <el-empty v-if="detail.logs.length === 0" description="暂无日志" :image-size="60" />
      </el-timeline>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { taskDetail } from '../../api'
import type { DictItem, TaskDetailVO } from '../../api/types'
import BizNoTag from '../../components/BizNoTag.vue'
import StatusTag from '../../components/StatusTag.vue'
import { useDictStore } from '../../stores/dict'
import { fmtTime } from '../../utils/format'

const route = useRoute()
const dictStore = useDictStore()

const detail = ref<TaskDetailVO | null>(null)
const typeDict = ref<DictItem[]>([])
const statusDict = ref<DictItem[]>([])
const disposalDict = ref<DictItem[]>([])

const taskId = computed(() => Number(route.params.id))

const dictLabel = (dict: DictItem[], alias?: string | null) =>
  dict.find((d) => d.alias === alias)?.label ?? alias ?? '-'

const logType = (s: string) => (s === 'ok' ? 'success' : s === 'fail' ? 'danger' : 'info')
const statusText = (s: string) =>
  s === 'ok' ? '成功' : s === 'fail' ? '失败' : s === 'start' ? '开始' : s

let interval: ReturnType<typeof setInterval> | undefined

async function load(silent = false) {
  try {
    detail.value = await taskDetail(taskId.value)
    // 运行中任务 5s 轮询直至终态
    clearInterval(interval)
    if (detail.value.task.status === 'running' || detail.value.task.status === 'ready') {
      interval = setInterval(() => load(true), 5000)
    }
  } catch (e) {
    if (!silent) {
      ElMessage.error((e as Error).message)
    }
  }
}

onMounted(async () => {
  typeDict.value = await dictStore.ensure('cmr_task_type')
  statusDict.value = await dictStore.ensure('cmr_task_status')
  disposalDict.value = await dictStore.ensure('cmr_fail_disposal')
  await load()
})

onBeforeUnmount(() => clearInterval(interval))
</script>

<style scoped>
.summary-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}
</style>
