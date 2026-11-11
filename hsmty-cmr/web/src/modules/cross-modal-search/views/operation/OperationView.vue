<template>
  <div class="page-card">
    <header class="cm-page-header">
      <div>
        <h1 class="cm-page-title">结果运营</h1>
        <p class="cm-page-subtitle">
          两个入口（标记不匹配 / 零结果原因分析）→ 一次判断 → 三个去向（屏蔽数据 / 调策略 / 补数据）。
        </p>
      </div>
      <el-button @click="load">刷新</el-button>
    </header>

    <div class="stat-row" v-if="stats">
      <div class="stat-card"><span class="stat-k">待处理</span><span class="stat-v">{{ stats.pending }}</span></div>
      <div class="stat-card"><span class="stat-k">已处理</span><span class="stat-v">{{ stats.handled }}</span></div>
      <div class="stat-card"><span class="stat-k">屏蔽数据</span><span class="stat-v">{{ stats.blocked }}</span></div>
      <div class="stat-card"><span class="stat-k">调策略</span><span class="stat-v">{{ stats.strategy }}</span></div>
      <div class="stat-card"><span class="stat-k">补数据</span><span class="stat-v">{{ stats.supplement }}</span></div>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="cm-flex-between">
          <span>反馈单</span>
          <span class="text-secondary">待处理 → 已处理，处置后不可重复操作</span>
        </div>
      </template>
      <div class="toolbar">
        <el-select v-model="filters.entryType" clearable placeholder="入口类型" style="width: 150px" @change="reload">
          <el-option label="标记不匹配" value="mismatch" />
          <el-option label="零结果分析" value="zero" />
        </el-select>
        <el-select v-model="filters.status" clearable placeholder="状态" style="width: 130px" @change="reload">
          <el-option label="待处理" value="pending" />
          <el-option label="已处理" value="handled" />
        </el-select>
        <el-input v-model="filters.sourceTaskNo" placeholder="来源任务号" clearable style="width: 190px" @keyup.enter="reload" />
        <el-button type="primary" @click="reload">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" size="small" empty-text="暂无反馈">
        <el-table-column label="反馈单" width="150">
          <template #default="{ row }">
            <div class="mono">{{ row.bizNo }}</div>
            <div class="text-secondary">{{ row.createTime }}</div>
          </template>
        </el-table-column>
        <el-table-column label="入口" width="110">
          <template #default="{ row }">
            <el-tag :type="row.entryType === 'mismatch' ? 'warning' : 'info'" size="small" effect="plain">
              {{ row.entryType === 'mismatch' ? '标记不匹配' : '零结果分析' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" min-width="190">
          <template #default="{ row }">
            <el-button v-if="row.sourceTaskNo" link type="primary" class="mono" @click="openSummary(row.sourceTaskNo)">
              {{ row.sourceTaskNo }}
            </el-button>
            <div class="text-secondary">{{ row.sourceQuery || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="条目" min-width="180">
          <template #default="{ row }">
            <span v-if="!row.items?.length" class="text-secondary">—</span>
            <span v-else>{{ row.items.length }} 条<el-tooltip :content="itemNames(row.items)"><span class="more">明细</span></el-tooltip></span>
            <div v-if="row.description" class="text-secondary">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'handled' ? 'success' : 'warning'" size="small" effect="light">
              {{ row.status === 'handled' ? '已处理' : '待处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="判断 / 去向" min-width="200">
          <template #default="{ row }">
            <template v-if="row.status === 'handled'">
              <span>{{ judgmentLabel(row.judgment) }}</span>
              <span class="text-secondary"> → </span>
              <span class="disposition">{{ dispositionLabel(row.disposition) }}</span>
              <el-tag v-if="row.blocked" type="danger" size="small" effect="plain" class="ml4">已屏蔽</el-tag>
            </template>
            <span v-else class="text-secondary">待处置</span>
            <div v-if="row.resultNote" class="text-secondary">{{ row.resultNote }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'handled'" link type="primary" @click="openHandle(row)">处置</el-button>
            <span v-else class="text-secondary">已完成</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="cm-pagination"
        layout="total, prev, pager, next"
        :total="total"
        :current-page="pageNum"
        :page-size="20"
        @current-change="changePage"
      />
    </el-card>

    <el-dialog v-model="handleVisible" title="处置反馈" width="500px">
      <div v-if="current" class="handle-head">
        <div class="mono">{{ current.bizNo }}</div>
        <div class="text-secondary">来源 {{ current.sourceTaskNo || '—' }} · {{ current.sourceQuery || '' }}</div>
        <div v-if="current.items?.length" class="items">
          <div v-for="item in current.items" :key="item.itemId" class="item">
            <span>{{ item.name || item.itemId }}</span>
            <span class="text-secondary">{{ item.source }}</span>
          </div>
        </div>
      </div>
      <el-form label-width="90px">
        <el-form-item label="判断" required>
          <el-radio-group v-model="handleForm.judgment">
            <el-radio value="data_bad">数据问题</el-radio>
            <el-radio value="match_bad">匹配问题</el-radio>
            <el-radio value="need_data">缺数据</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="去向" required>
          <el-radio-group v-model="handleForm.disposition">
            <el-radio value="block">屏蔽数据</el-radio>
            <el-radio value="strategy">调策略</el-radio>
            <el-radio value="supplement">补数据</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="handleForm.resultNote" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <p v-if="handleForm.disposition === 'block'" class="text-secondary">
        选择「屏蔽数据」会把这些条目写入屏蔽列表，检索端不再下发。
      </p>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="handling" @click="submitHandle">确认处置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { feedbackStats, handleFeedback, pageFeedbacks } from '../../api'
import type { FeedbackStatsVO, FeedbackVO, MismatchItem } from '../../api/types'

const router = useRouter()

const loading = ref(false)
const rows = ref<FeedbackVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const stats = ref<FeedbackStatsVO | null>(null)
const filters = ref({ entryType: '', status: '', sourceTaskNo: '' })

const handleVisible = ref(false)
const handling = ref(false)
const current = ref<FeedbackVO | null>(null)
const handleForm = ref({ judgment: 'data_bad', disposition: 'block', resultNote: '' })

function judgmentLabel(judgment: string | null) {
  return { data_bad: '数据问题', match_bad: '匹配问题', need_data: '缺数据' }[judgment ?? ''] ?? '—'
}

function dispositionLabel(disposition: string | null) {
  return { block: '屏蔽数据', strategy: '调策略', supplement: '补数据' }[disposition ?? ''] ?? '—'
}

function itemNames(items: MismatchItem[]) {
  return items.map((item) => item.name || item.itemId).join('、')
}

async function load() {
  loading.value = true
  try {
    const [page, statsVo] = await Promise.all([
      pageFeedbacks({
        pageNum: pageNum.value,
        pageSize: 20,
        entryType: filters.value.entryType,
        status: filters.value.status,
        sourceTaskNo: filters.value.sourceTaskNo,
      }),
      feedbackStats(),
    ])
    rows.value = page.records
    total.value = page.total
    stats.value = statsVo
  } finally {
    loading.value = false
  }
}

function reload() {
  pageNum.value = 1
  load()
}

function changePage(page: number) {
  pageNum.value = page
  load()
}

function openSummary(taskNo: string) {
  router.push({ name: 'search-summary', params: { taskNo } })
}

function openHandle(row: FeedbackVO) {
  current.value = row
  handleForm.value = { judgment: 'data_bad', disposition: row.items?.length ? 'block' : 'strategy', resultNote: '' }
  handleVisible.value = true
}

async function submitHandle() {
  if (!current.value) {
    return
  }
  handling.value = true
  try {
    await handleFeedback(current.value.id, { ...handleForm.value })
    ElMessage.success('处置完成')
    handleVisible.value = false
    load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    handling.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.stat-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; }
.stat-card {
  display: flex; flex-direction: column; gap: 4px; padding: 14px 16px;
  border: 1px solid var(--content-line); border-radius: var(--radius-lg); background: #fff;
}
.stat-k { color: var(--muted); font-size: 12px; }
.stat-v { font-size: 22px; font-weight: 650; color: var(--text-primary); }
.more { margin-left: 6px; color: var(--system-blue); cursor: help; font-size: 12px; }
.ml4 { margin-left: 4px; }
.disposition { font-weight: 600; color: var(--system-blue); }
.handle-head { margin-bottom: 14px; }
.items { margin-top: 8px; display: flex; flex-direction: column; gap: 4px; font-size: 12px; }
.item { display: flex; justify-content: space-between; gap: 12px; padding: 4px 8px; background: var(--content-soft); border-radius: 6px; }
</style>
