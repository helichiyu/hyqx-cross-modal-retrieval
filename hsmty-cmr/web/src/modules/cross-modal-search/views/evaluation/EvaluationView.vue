<template>
  <div class="page-card">
    <header class="cm-page-header">
      <div>
        <h1 class="cm-page-title">效果评测</h1>
        <p class="cm-page-subtitle">
          一条 query 一条评测：以历史检索为基准，重放对比踩数、踩率与结果数；命中任一退化规则即标红。
        </p>
      </div>
      <div class="cm-flex">
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="createVisible = true">新建评测</el-button>
      </div>
    </header>

    <el-card shadow="never">
      <template #header>
        <div class="cm-flex-between">
          <span>评测列表</span>
          <span class="text-secondary">退化规则：踩数上升 / 踩率 +5% / 结果数 −10%</span>
        </div>
      </template>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索 query" clearable style="width: 240px" @keyup.enter="reload" />
        <el-button type="primary" @click="reload">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" size="small" empty-text="暂无评测">
        <el-table-column prop="query" label="Query" min-width="200" show-overflow-tooltip />
        <el-table-column label="基准任务" width="150">
          <template #default="{ row }">
            <el-button link type="primary" class="mono" @click="openSummary(row.baseTaskNo)">
              {{ row.baseTaskNo }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="基准结果 / 踩" width="130">
          <template #default="{ row }">{{ row.baseN ?? 0 }} 条 / {{ row.baseDown ?? 0 }} 踩</template>
        </el-table-column>
        <el-table-column label="重放" width="80">
          <template #default="{ row }">{{ row.replayCount }} 次</template>
        </el-table-column>
        <el-table-column label="结论" width="110">
          <template #default="{ row }">
            <el-tag :type="conclusionType(row.conclusion)" size="small" effect="light">
              {{ conclusionLabel(row.conclusion) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="退化原因" min-width="200">
          <template #default="{ row }">
            <span v-if="row.reasons?.length" class="reason">{{ row.reasons.join('；') }}</span>
            <span v-else class="text-secondary">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="latestTime" label="最近重放" width="170" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">对比</el-button>
            <el-button link @click="openReplay(row)">重放</el-button>
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

    <el-dialog v-model="createVisible" title="新建评测" width="460px">
      <el-form label-width="100px">
        <el-form-item label="基准任务号" required>
          <el-input v-model="createForm.baseTaskNo" placeholder="如 SRH261110ABCDEF" />
        </el-form-item>
      </el-form>
      <p class="text-secondary">以该任务为基准；同一 query 只建立一条评测。</p>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">建立</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="replayVisible" title="重放评测" width="460px">
      <el-form label-width="100px">
        <el-form-item label="待比较策略">
          <el-select v-model="replayStrategyId" clearable placeholder="不选则用基准快照" style="width: 100%">
            <el-option
              v-for="option in strategyList"
              :key="option.id"
              :label="`${option.name}（${option.paramSummary}）`"
              :value="option.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="replayVisible = false">取消</el-button>
        <el-button type="primary" :loading="replaying" @click="submitReplay">执行重放</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="评测对比" size="560px">
      <div v-if="detail" class="detail">
        <div class="detail-query">{{ detail.query }}</div>
        <el-tag :type="conclusionType(detail.conclusion)" effect="light">
          {{ conclusionLabel(detail.conclusion) }}
        </el-tag>
        <ul v-if="detail.reasons?.length" class="reasons">
          <li v-for="reason in detail.reasons" :key="reason">{{ reason }}</li>
        </ul>

        <div class="cmp-grid">
          <div class="cmp-col">
            <div class="cmp-title">基准</div>
            <div v-for="(value, key) in detail.base" :key="key" class="cmp-row">
              <span class="cmp-k">{{ key }}</span><span class="mono">{{ value ?? '—' }}</span>
            </div>
          </div>
          <div class="cmp-col">
            <div class="cmp-title">最近重放</div>
            <div v-for="(value, key) in detail.latest || {}" :key="key" class="cmp-row">
              <span class="cmp-k">{{ key }}</span><span class="mono">{{ value ?? '—' }}</span>
            </div>
          </div>
        </div>

        <div class="text-secondary" v-if="detail.downRateDelta !== undefined">
          踩率变化 {{ detail.downRateDelta }}% · 结果数变化 {{ detail.resultDropRatio }}%
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  createEvaluation,
  evaluationDetail,
  pageEvaluations,
  replayEvaluation,
  strategyOptions,
} from '../../api'
import type { EvaluationDetailVO, EvaluationRowVO, StrategyOptionVO } from '../../api/types'

const router = useRouter()

const loading = ref(false)
const rows = ref<EvaluationRowVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const keyword = ref('')

const createVisible = ref(false)
const creating = ref(false)
const createForm = ref({ baseTaskNo: '' })

const replayVisible = ref(false)
const replaying = ref(false)
const replayStrategyId = ref<string | undefined>(undefined)
const replayTargetId = ref<string>('')
const strategyList = ref<StrategyOptionVO[]>([])

const detailVisible = ref(false)
const detail = ref<EvaluationDetailVO | null>(null)

function conclusionLabel(conclusion: string) {
  return { degraded: '退化', normal: '持平/变好', unevaluated: '未重放' }[conclusion] ?? conclusion
}

function conclusionType(conclusion: string) {
  return conclusion === 'degraded' ? 'danger' : conclusion === 'normal' ? 'success' : 'info'
}

async function load() {
  loading.value = true
  try {
    const page = await pageEvaluations({ pageNum: pageNum.value, pageSize: 20, keyword: keyword.value })
    rows.value = page.records
    total.value = page.total
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

async function submitCreate() {
  if (!createForm.value.baseTaskNo.trim()) {
    ElMessage.warning('请填写基准任务号')
    return
  }
  creating.value = true
  try {
    await createEvaluation(createForm.value.baseTaskNo.trim())
    ElMessage.success('评测已建立')
    createVisible.value = false
    createForm.value = { baseTaskNo: '' }
    load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    creating.value = false
  }
}

async function openReplay(row: EvaluationRowVO) {
  replayTargetId.value = row.id
  replayStrategyId.value = undefined
  if (!strategyList.value.length) {
    strategyList.value = await strategyOptions()
  }
  replayVisible.value = true
}

async function submitReplay() {
  replaying.value = true
  try {
    await replayEvaluation(replayTargetId.value, replayStrategyId.value)
    ElMessage.success('重放完成，已回到列表查看结论')
    replayVisible.value = false
    load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    replaying.value = false
  }
}

async function openDetail(row: EvaluationRowVO) {
  detail.value = await evaluationDetail(row.id)
  detailVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.reason { color: var(--error); font-size: 12px; }
.detail { display: flex; flex-direction: column; gap: 12px; }
.detail-query { font-size: 16px; font-weight: 650; color: var(--text-primary); }
.reasons { margin: 0; padding-left: 18px; color: var(--error); font-size: 13px; }
.cmp-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.cmp-col { border: 1px solid var(--content-line); border-radius: var(--radius); padding: 10px 12px; }
.cmp-title { font-weight: 600; margin-bottom: 6px; }
.cmp-row { display: flex; justify-content: space-between; font-size: 12px; padding: 2px 0; }
.cmp-k { color: var(--muted); }
</style>
