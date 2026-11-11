<template>
  <div class="page-card">
    <header class="cm-page-header">
      <div>
        <h1 class="cm-page-title">搜索行为</h1>
        <p class="cm-page-subtitle">
          每次检索一条流水：谁、搜了什么、用哪版策略、返回多少条、被赞踩多少；零结果与失败单独列出。
        </p>
      </div>
      <div class="cm-flex">
        <el-button @click="reload">刷新</el-button>
      </div>
    </header>

    <el-card shadow="never">
      <template #header>
        <div class="cm-flex-between">
          <span>行为流水</span>
          <span class="text-secondary">共 {{ total }} 条 · 30 天内可回看摘要</span>
        </div>
      </template>
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索词" clearable style="width: 200px" @keyup.enter="reload" />
        <el-input v-model="query.operatorName" placeholder="操作人" clearable style="width: 150px" @keyup.enter="reload" />
        <el-checkbox v-model="query.zeroResult" label="只看零结果" />
        <el-date-picker
          v-model="range"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 340px"
        />
        <el-button type="primary" @click="reload">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" size="small" empty-text="暂无检索行为">
        <el-table-column label="任务号" width="150">
          <template #default="{ row }">
            <el-button link type="primary" class="mono" @click="openSummary(row)">{{ row.taskNo }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="搜索词" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.queryText || '（文件检索）' }}</template>
        </el-table-column>
        <el-table-column label="模态" width="110">
          <template #default="{ row }">{{ row.queryModality }} → {{ row.targetModality || '自动' }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="100">
          <template #default="{ row }">{{ row.operatorName || '—' }}</template>
        </el-table-column>
        <el-table-column label="策略" width="150">
          <template #default="{ row }">
            <span class="mono">{{ row.strategyNo || '默认' }}</span>
            <span v-if="row.strategyVersion" class="text-secondary"> v{{ row.strategyVersion }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.zeroResult" type="warning" size="small" effect="plain">零结果</el-tag>
            <span v-else>{{ row.itemCount }} 条</span>
          </template>
        </el-table-column>
        <el-table-column label="赞/踩" width="90">
          <template #default="{ row }">
            <span class="vote-up">▲{{ row.upCount }}</span>
            <span class="vote-down">▼{{ row.downCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.taskStatus === 'done' ? 'success' : 'danger'" size="small" effect="plain">
              {{ row.taskStatus === 'done' ? '完成' : '失败' }}
            </el-tag>
            <el-tag v-if="row.sealed" type="info" size="small" effect="plain" class="ml4">已封存</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSummary(row)">摘要</el-button>
            <el-button v-if="!row.sealed" link @click="seal(row)">封存</el-button>
            <el-button v-if="row.zeroResult" link type="warning" @click="openZeroFeedback(row)">原因分析</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="cm-pagination"
        layout="total, prev, pager, next"
        :total="total"
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        @current-change="changePage"
      />
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="cm-flex-between">
          <span>屏蔽条目</span>
          <span class="text-secondary">结果运营「屏蔽数据」写入，检索端不再下发</span>
        </div>
      </template>
      <el-table :data="blocked" size="small" empty-text="暂无屏蔽条目">
        <el-table-column prop="itemId" label="条目 ID" min-width="160" />
        <el-table-column prop="itemName" label="名称" min-width="160" />
        <el-table-column prop="source" label="来源" min-width="140" />
        <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
        <el-table-column prop="createTime" label="屏蔽时间" width="170" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="danger" @click="unblock(row)">解除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="zeroVisible" title="零结果 · 发起原因分析" width="480px">
      <el-form label-width="90px">
        <el-form-item label="任务号">
          <span class="mono">{{ zeroForm.taskNo }}</span>
        </el-form-item>
        <el-form-item label="搜索词">
          <span>{{ zeroForm.query }}</span>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="zeroForm.description" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="zeroVisible = false">取消</el-button>
        <el-button type="primary" :loading="zeroSubmitting" @click="submitZero">提交反馈</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createFeedback,
  listBlockedItems,
  pageBehaviors,
  sealTask,
  unblockItem,
} from '../../api'
import type { BehaviorVO, BlockedItemVO } from '../../api/types'

const router = useRouter()

const loading = ref(false)
const rows = ref<BehaviorVO[]>([])
const blocked = ref<BlockedItemVO[]>([])
const total = ref(0)
const range = ref<[string, string] | null>(null)
const query = ref({ pageNum: 1, pageSize: 20, keyword: '', operatorName: '', zeroResult: false })

const zeroVisible = ref(false)
const zeroSubmitting = ref(false)
const zeroForm = ref({ taskNo: '', query: '', description: '' })

async function load() {
  loading.value = true
  try {
    const page = await pageBehaviors({
      pageNum: query.value.pageNum,
      pageSize: query.value.pageSize,
      keyword: query.value.keyword,
      operatorName: query.value.operatorName,
      zeroResult: query.value.zeroResult || undefined,
      from: range.value?.[0],
      to: range.value?.[1],
    })
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function reload() {
  query.value.pageNum = 1
  load()
}

function changePage(pageNum: number) {
  query.value.pageNum = pageNum
  load()
}

function openSummary(row: BehaviorVO) {
  router.push({ name: 'search-summary', params: { taskNo: row.taskNo } })
}

async function seal(row: BehaviorVO) {
  await ElMessageBox.confirm('封存后该任务的赞/踩将被锁定，确认封存？', '封存任务', { type: 'warning' })
  await sealTask(row.taskNo)
  ElMessage.success('已封存')
  load()
}

function openZeroFeedback(row: BehaviorVO) {
  zeroForm.value = { taskNo: row.taskNo, query: row.queryText ?? '', description: '' }
  zeroVisible.value = true
}

async function submitZero() {
  zeroSubmitting.value = true
  try {
    await createFeedback({
      entryType: 'zero',
      sourceTaskNo: zeroForm.value.taskNo,
      sourceQuery: zeroForm.value.query,
      description: zeroForm.value.description,
    })
    ElMessage.success('已登记零结果反馈，可在结果运营处置')
    zeroVisible.value = false
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    zeroSubmitting.value = false
  }
}

async function unblock(row: BlockedItemVO) {
  await ElMessageBox.confirm(`确认解除条目「${row.itemName || row.itemId}」的屏蔽？`, '解除屏蔽', { type: 'warning' })
  await unblockItem(row.id)
  blocked.value = await listBlockedItems()
  ElMessage.success('已解除')
}

onMounted(async () => {
  await load()
  blocked.value = await listBlockedItems()
})
</script>

<style scoped>
.vote-up { color: var(--success); margin-right: 6px; font-size: 12px; }
.vote-down { color: var(--error); font-size: 12px; }
.ml4 { margin-left: 4px; }
</style>
