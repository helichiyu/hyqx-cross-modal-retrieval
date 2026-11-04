<template>
  <div class="page-card">
    <el-card>
      <div class="toolbar">
        <el-select v-model="typeFilter" placeholder="任务类型" clearable style="width: 150px">
          <el-option v-for="d in typeDict" :key="d.alias" :label="d.label" :value="d.alias" />
        </el-select>
        <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 140px">
          <el-option v-for="d in statusDict" :key="d.alias" :label="d.label" :value="d.alias" />
        </el-select>
        <el-select v-model="disposalFilter" placeholder="失败处置" clearable style="width: 140px">
          <el-option v-for="d in disposalDict" :key="d.alias" :label="d.label" :value="d.alias" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="编号/错误关键字"
          clearable
          style="width: 200px"
          @keyup.enter="reload"
        />
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="timer = !timer">
          {{ timer ? '停止轮询' : '开启轮询(5s)' }}
        </el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column label="任务编号" width="180">
          <template #default="{ row }"><BizNoTag :value="row.bizNo" /></template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ dictLabel(typeDict, row.type) }}</template>
        </el-table-column>
        <el-table-column label="关联对象" width="180">
          <template #default="{ row }"><BizNoTag :value="row.refBizNo" /></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.status" :dict="statusDict" />
          </template>
        </el-table-column>
        <el-table-column prop="currentStep" label="当前步骤" width="150" />
        <el-table-column label="失败侧" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.failType === 'engine'" size="small" type="warning">引擎侧</el-tag>
            <el-tag v-else-if="row.failType === 'data'" size="small" type="danger">数据侧</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="处置" width="100">
          <template #default="{ row }">
            <StatusTag v-if="row.failDisposal" :value="row.failDisposal" :dict="disposalDict" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试" width="60" align="center" />
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.finishedTime || row.startedTime || row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <router-link :to="`/tasks/${row.id}`">
              <el-button link type="primary">详情</el-button>
            </router-link>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next, sizes"
        style="margin-top: 12px; justify-content: flex-end"
        @change="reload"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageTasks } from '../../api'
import type { DictItem, TaskVO } from '../../api/types'
import BizNoTag from '../../components/BizNoTag.vue'
import StatusTag from '../../components/StatusTag.vue'
import { useDictStore } from '../../stores/dict'
import { fmtTime } from '../../utils/format'

const dictStore = useDictStore()

const rows = ref<TaskVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const typeFilter = ref('')
const statusFilter = ref('')
const disposalFilter = ref('')
const keyword = ref('')
const loading = ref(false)

const typeDict = ref<DictItem[]>([])
const statusDict = ref<DictItem[]>([])
const disposalDict = ref<DictItem[]>([])

const dictLabel = (dict: DictItem[], alias?: string | null) =>
  dict.find((d) => d.alias === alias)?.label ?? alias ?? '-'

async function reload(silent = false) {
  if (!silent) {
    loading.value = true
  }
  try {
    const page = await pageTasks({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      type: typeFilter.value,
      status: statusFilter.value,
      failDisposal: disposalFilter.value,
      keyword: keyword.value,
    })
    rows.value = page.list
    total.value = page.total
  } catch (e) {
    if (!silent) {
      ElMessage.error((e as Error).message)
    }
  } finally {
    loading.value = false
  }
}

const timer = ref(false)
let interval: ReturnType<typeof setInterval> | undefined

function watchTimer(on: boolean) {
  clearInterval(interval)
  if (on) {
    interval = setInterval(() => reload(true), 5000)
  }
}

onMounted(async () => {
  typeDict.value = await dictStore.ensure('cmr_task_type')
  statusDict.value = await dictStore.ensure('cmr_task_status')
  disposalDict.value = await dictStore.ensure('cmr_fail_disposal')
  await reload()
})

onBeforeUnmount(() => watchTimer(false))
</script>
