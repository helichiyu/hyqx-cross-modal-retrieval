<template>
  <div class="page-card">
    <header class="cm-page-header">
      <h1 class="cm-page-title">效果评测</h1>
      <div class="cm-flex">
        <el-button type="primary" @click="openCreate">＋ 发起评测</el-button>
      </div>
    </header>

    <!-- 评测列表：表格 + 按 Query 搜索 + 结论筛选 + 每页 10 条 -->
    <section class="cm-panel eval-panel" aria-labelledby="evalListTitle">
      <div class="eval-head">
        <h2 id="evalListTitle">评测列表</h2>
      </div>
      <div class="eval-tools">
        <el-input
          v-model="keyword"
          class="eval-search"
          placeholder="按 Query 搜索"
          clearable
          @input="onKeywordInput"
          @clear="onKeywordInput"
        />
        <div class="v-filter" role="group" aria-label="按结论筛选">
          <button
            v-for="chip in verdictChips"
            :key="chip.key"
            type="button"
            class="v-chip"
            :class="[chip.cls, { 'is-on': verdict === chip.key }]"
            :aria-pressed="verdict === chip.key"
            @click="setVerdict(chip.key)"
          >
            {{ chip.label }} <b>{{ chip.count }}</b>
          </button>
        </div>
        <span class="eval-hint">劣化阈值：踩数上升 / 踩率 +5% / 结果数 −10%</span>
      </div>

      <el-table
        v-loading="loading"
        :data="pageRows"
        class="eval-table"
        size="small"
        :row-class-name="rowClassName"
        :empty-text="emptyText"
      >
        <el-table-column prop="bizNo" label="评测编号" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.bizNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="搜索词" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="ev-q">{{ row.query || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="基准任务" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.baseTaskNo || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="基准时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.baseTime) }}</template>
        </el-table-column>
        <el-table-column label="基准结果数" width="104" align="center">
          <template #default="{ row }">{{ row.baseN ?? 0 }} 条</template>
        </el-table-column>
        <el-table-column label="踩数" width="74" align="center">
          <template #default="{ row }">{{ row.baseDown ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="重放次数" width="90" align="center">
          <template #default="{ row }">{{ row.replayCount }} 次</template>
        </el-table-column>
        <el-table-column label="结论" width="96" align="center">
          <template #default="{ row }">
            <el-tooltip :content="rowTip(row)" placement="top">
              <span class="badge" :class="badgeClass(row)">{{ badgeLabel(row) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="最近重放" width="160">
          <template #default="{ row }">{{ row.latestTime ? fmtTime(row.latestTime) : '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="right" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openCompare(row)">查看对比</el-button>
            <el-button
              link
              :disabled="!canReplay(row) || !!replayingId"
              :loading="replayingId === row.id"
              @click="doReplay(row)"
            >
              发起重放
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="eval-pager">
        <el-pagination
          class="cm-pagination"
          layout="total, prev, pager, next"
          :total="filteredRows.length"
          :page-size="PAGE_SIZE"
          :current-page="pageNum"
          @current-change="onPageChange"
        />
      </div>
    </section>

    <!-- 难例样本集：从结果页点踩自动汇总 -->
    <section class="cm-panel sample-block" aria-labelledby="sampleTitle">
      <div class="sb-head">
        <h2 id="sampleTitle">难例样本集</h2>
        <button
          class="sb-q"
          type="button"
          :aria-expanded="sampleHelpOpen"
          aria-controls="sampleHelp"
          title="难例样本集说明"
          @click="sampleHelpOpen = !sampleHelpOpen"
        >
          ?
        </button>
      </div>
      <div v-if="sampleHelpOpen" id="sampleHelp" class="sb-help">
        <p><b>定位：</b>难例样本集 = 从结果页单条点踩自动汇总的<b>难例</b>，用于专测薄弱点。</p>
        <p><b>列表：</b>一行 = 一个被点踩的任务；被踩条目数按条目去重，不统计点踩次数。</p>
        <p>
          <b>计算方式：</b>
          <code>COUNT(DISTINCT item_id) WHERE task ∈ 该 query 的所有任务 AND vote = 'down'</code>
        </p>
        <p><b>数据来源：</b><code>cm_item_votes</code>（点踩）+ 任务元数据 / 结果摘要；无数据时显示空态。</p>
      </div>

      <el-table
        v-loading="hardLoading"
        :data="hardRows"
        class="sample-table"
        size="small"
        empty-text="暂无被踩的数据"
      >
        <el-table-column label="任务号" width="170">
          <template #default="{ row }">
            <span class="mono">{{ row.taskNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="搜索词" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.query || '—' }}</template>
        </el-table-column>
        <el-table-column label="踩数" width="90" align="center">
          <template #default="{ row }">
            <span class="sb-count">{{ row.downCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="right" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="isEvaluated(row.query) || creatingTaskNo === row.taskNo"
              :loading="creatingTaskNo === row.taskNo"
              @click="createFromHardCase(row)"
            >
              {{ isEvaluated(row.query) ? '已在评测' : '发起评测' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="sb-pager">
        <el-pagination
          class="cm-pagination"
          layout="total, prev, pager, next"
          :total="hardTotal"
          :page-size="HARD_PAGE_SIZE"
          :current-page="hardPage"
          @current-change="onHardPageChange"
        />
      </div>
    </section>

    <!-- 发起评测：从搜索行为记录里选一条设为基准 -->
    <el-dialog v-model="createOpen" title="发起评测" width="620px">
      <div class="mb-step">搜索行为记录</div>
      <el-input
        v-model="pickKeyword"
        class="hist-search"
        placeholder="搜索查询或时间"
        clearable
        @input="onPickKeyword"
        @clear="onPickKeyword"
      />
      <div v-loading="pickLoading" class="pick-list">
        <div class="pick-head">
          <span aria-hidden="true"></span>
          <span>时间</span>
          <span>查询</span>
          <span class="ta-r">踩数</span>
        </div>
        <div v-if="!pickRows.length" class="pick-empty">没有匹配的搜索记录</div>
        <label
          v-for="row in pickRows"
          :key="row.taskNo"
          class="pick-row"
          :class="{ 'is-pick': pickedTaskNo === row.taskNo }"
        >
          <input
            v-model="pickedTaskNo"
            type="radio"
            name="evalPick"
            :value="row.taskNo"
            :disabled="isEvaluated(row.queryText)"
            :aria-label="`选择 ${row.queryText || row.taskNo}`"
          />
          <span class="pk-time">{{ fmtTime(row.createTime) }}</span>
          <span class="pk-q" :title="row.queryText || ''">
            {{ row.queryText || '—' }}
            <span class="pk-task">{{ row.taskNo }}</span>
            <span v-if="isEvaluated(row.queryText)" class="pk-flag">已在评测中</span>
          </span>
          <span class="pk-d">{{ row.downCount }}</span>
        </label>
      </div>
      <el-pagination
        class="cm-pagination pick-pager"
        layout="total, prev, pager, next"
        :total="pickTotal"
        :page-size="PICK_PAGE_SIZE"
        :current-page="pickPage"
        @current-change="onPickPageChange"
      />
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button type="primary" :disabled="!pickedTaskNo" :loading="creating" @click="submitCreate">
          设为基准
        </el-button>
      </template>
    </el-dialog>

    <!-- 对比视图：760px 弹窗，行 = 指标，列 = 基准 + 最近 3 次重放 + 变化 -->
    <el-dialog v-model="compareOpen" :title="compareTitle" width="760px" destroy-on-close>
      <div v-loading="compareLoading" class="cmp-body">
        <template v-if="detail">
          <p v-if="replayTotal" class="cmp-note">
            共 {{ replayTotal }} 次重放{{ replayTrimmed ? ' · 显示最近 3 次' : '' }}
          </p>
          <div class="cmp-wrap">
            <table class="cmp-tbl">
              <thead>
                <tr>
                  <th scope="col">指标</th>
                  <th scope="col" class="num is-basecol">基准</th>
                  <th
                    v-for="(item, index) in shownReplays"
                    :key="index"
                    scope="col"
                    class="num"
                    :title="replayTip(item)"
                  >
                    重放{{ replayNo(index) }}
                  </th>
                  <th scope="col" class="num">
                    变化<i class="th-sub">最新 vs 基准</i>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in metricRows" :key="row.key" :class="{ 'row-bad': row.bad }">
                  <td class="m-name">{{ row.label }}</td>
                  <td
                    v-for="(value, index) in row.values"
                    :key="index"
                    class="num"
                    :class="{ 'is-basecol': index === 0 }"
                  >
                    {{ value }}
                  </td>
                  <td class="num chg-cell">
                    <span class="chg" :class="row.changeClass">{{ row.changeText }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="cmp-demo">无摘要的指标显示「—」；真实实现由检索引擎按 Top-20 摘要计算。</p>

          <div
            v-if="verdictState !== 'unevaluated'"
            class="regress"
            :class="verdictState === 'degraded' ? 'bad' : 'ok'"
            role="status"
          >
            <div class="regress-line">
              <span class="rg-ico" aria-hidden="true">{{ verdictState === 'degraded' ? '⚠' : '✓' }}</span>
              {{ verdictText }}
            </div>
            <div v-if="verdictState === 'degraded'" class="regress-tip">
              建议：不发布当前策略，先排查召回或排序问题
            </div>
          </div>

          <div v-if="checks.length" class="chk-block">
            <div class="cmp-sec-title">评测数据检查</div>
            <div class="chk-sum" :class="checkBadCount ? 'warn' : 'ok'">
              {{ checkBadCount ? `✕ 发现 ${checkBadCount} 项问题` : '✓ 评测数据检查通过' }}
            </div>
            <div class="chk-list">
              <div v-for="(check, index) in checks" :key="check.key || index" class="chk-item">
                <span class="chk-ico" :class="check.ok ? 'ok' : 'warn'" aria-hidden="true">
                  {{ check.ok ? '✓' : '✕' }}
                </span>
                <div class="chk-main">
                  <div class="chk-t">{{ index + 1 }}. {{ check.label }}</div>
                  <div v-if="check.detail" class="chk-d">{{ check.detail }}</div>
                </div>
              </div>
            </div>
          </div>

          <div class="conclusion" :class="conclusion.cls">{{ conclusion.text }}</div>
        </template>
      </div>
      <template #footer>
        <el-button @click="compareOpen = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createEvaluation,
  evaluationDetail,
  hardCases,
  pageBehaviors,
  pageEvaluations,
  replayEvaluation,
} from '../../api'
import type { BehaviorVO, EvaluationDetailVO, EvaluationRowVO, HardCaseVO } from '../../api/types'
import { fmtTime } from '../../utils/format'

type ConclusionKey = 'degraded' | 'normal' | 'unevaluated'
type VerdictFilter = 'all' | ConclusionKey

interface Metrics {
  taskNo: string | null
  time: string | null
  items: number | null
  down: number | null
  downRate: number | null
  top20AvgSim: number | null
  top1Sim: number | null
  top20Overlap: number | null
  avgSim: number | null
  elapsedMs: number | null
}

interface MetricChange {
  text: string
  cls: 'good' | 'bad' | 'flat'
  bad: boolean
}

interface MetricRow {
  key: string
  label: string
  values: string[]
  changeText: string
  changeClass: 'good' | 'bad' | 'flat'
  bad: boolean
}

const PAGE_SIZE = 10
const HARD_PAGE_SIZE = 10
const PICK_PAGE_SIZE = 8
const FETCH_SIZE = 200

// ---------- 评测列表 ----------
const loading = ref(false)
const keyword = ref('')
const verdict = ref<VerdictFilter>('all')
const pageNum = ref(1)
const allRows = ref<EvaluationRowVO[]>([])

const filteredRows = computed(() => {
  if (verdict.value === 'all') {
    return allRows.value
  }
  return allRows.value.filter((row) => conclusionKey(row.conclusion) === verdict.value)
})

const pageRows = computed(() => {
  const start = (pageNum.value - 1) * PAGE_SIZE
  return filteredRows.value.slice(start, start + PAGE_SIZE)
})

const verdictCounts = computed(() => {
  const counts: Record<ConclusionKey, number> = { degraded: 0, normal: 0, unevaluated: 0 }
  for (const row of allRows.value) {
    counts[conclusionKey(row.conclusion)] += 1
  }
  return counts
})

const verdictChips = computed(() => [
  { key: 'all' as VerdictFilter, label: '全部', cls: '', count: allRows.value.length },
  { key: 'degraded' as VerdictFilter, label: '已劣化', cls: 'bad', count: verdictCounts.value.degraded },
  { key: 'normal' as VerdictFilter, label: '正常', cls: 'ok', count: verdictCounts.value.normal },
  { key: 'unevaluated' as VerdictFilter, label: '未评测', cls: 'none', count: verdictCounts.value.unevaluated },
])

const emptyText = computed(() => (allRows.value.length ? '没有匹配的评测' : '暂无评测'))

const evaluatedQueries = computed(() => {
  const set = new Set<string>()
  for (const row of allRows.value) {
    if (row.query) {
      set.add(row.query.trim())
    }
  }
  return set
})

let keywordTimer: ReturnType<typeof setTimeout> | null = null

function conclusionKey(value?: string | null): ConclusionKey {
  return value === 'degraded' || value === 'normal' ? value : 'unevaluated'
}

function badgeLabel(row: EvaluationRowVO): string {
  const key = conclusionKey(row.conclusion)
  return key === 'degraded' ? '已劣化' : key === 'normal' ? '正常' : '未评测'
}

function badgeClass(row: EvaluationRowVO): string {
  const key = conclusionKey(row.conclusion)
  return key === 'degraded' ? 'fail' : key === 'normal' ? 'ok' : 'gray'
}

function rowTip(row: EvaluationRowVO): string {
  const key = conclusionKey(row.conclusion)
  if (key === 'unevaluated') {
    return '尚无重放，暂不判定'
  }
  if (key === 'normal') {
    return '最新重放未触发劣化阈值'
  }
  return `劣化原因：${(row.reasons ?? []).join('、') || '最新重放触发劣化阈值'}`
}

function rowClassName({ row }: { row: EvaluationRowVO }): string {
  return conclusionKey(row.conclusion) === 'degraded' ? 'row-bad' : ''
}

async function fetchEvaluations(): Promise<EvaluationRowVO[]> {
  const kw = keyword.value.trim()
  const first = await pageEvaluations({ pageNum: 1, pageSize: FETCH_SIZE, keyword: kw })
  const records = [...first.records]
  // 不截断：结论/“已在评测”判断需要完整集合
  const pages = Math.ceil(first.total / FETCH_SIZE)
  for (let page = 2; page <= pages; page += 1) {
    const next = await pageEvaluations({ pageNum: page, pageSize: FETCH_SIZE, keyword: kw })
    records.push(...next.records)
  }
  return records
}

async function load(resetPage = false) {
  loading.value = true
  try {
    allRows.value = await fetchEvaluations()
    if (resetPage) {
      pageNum.value = 1
    }
    clampPage()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

function clampPage() {
  const pages = Math.max(1, Math.ceil(filteredRows.value.length / PAGE_SIZE))
  if (pageNum.value > pages) {
    pageNum.value = pages
  }
}

function onKeywordInput() {
  if (keywordTimer) {
    clearTimeout(keywordTimer)
  }
  keywordTimer = setTimeout(() => {
    void load(true)
  }, 250)
}

function setVerdict(key: VerdictFilter) {
  verdict.value = key
  pageNum.value = 1
  clampPage()
}

function onPageChange(page: number) {
  pageNum.value = page
}

// ---------- 难例样本集 & 新建评测候选 ----------
const hardLoading = ref(false)
const hardRows = ref<HardCaseVO[]>([])
const hardTotal = ref(0)
const hardPage = ref(1)
const creatingTaskNo = ref('')

async function loadHardCases() {
  hardLoading.value = true
  try {
    // 后端已按「被踩条目数倒序、同数量按时间倒序」排序，直接服务端分页，避免截断
    const page = await hardCases({ pageNum: hardPage.value, pageSize: HARD_PAGE_SIZE })
    hardRows.value = page.records
    hardTotal.value = page.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    hardLoading.value = false
  }
}

function onHardPageChange(page: number) {
  hardPage.value = page
  void loadHardCases()
}

function isEvaluated(query?: string | null): boolean {
  return Boolean(query && evaluatedQueries.value.has(query.trim()))
}

function canReplay(row: EvaluationRowVO): boolean {
  return Boolean(row.baseTaskNo)
}

// 新建评测（候选记录列表）
const createOpen = ref(false)
const creating = ref(false)
const pickedTaskNo = ref('')
const pickKeyword = ref('')
const pickPage = ref(1)

// 候选 = 全部搜索行为记录（后端分页 + 关键词），不限“有点踩”的难例
const candidates = ref<BehaviorVO[]>([])
const pickTotal = ref(0)
const pickLoading = ref(false)
let pickTimer: ReturnType<typeof setTimeout> | null = null

const pickRows = computed(() => candidates.value)

async function loadCandidates() {
  pickLoading.value = true
  try {
    const page = await pageBehaviors({
      pageNum: pickPage.value,
      pageSize: PICK_PAGE_SIZE,
      keyword: pickKeyword.value.trim() || undefined,
    })
    candidates.value = page.records
    pickTotal.value = page.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    pickLoading.value = false
  }
}

function openCreate() {
  pickedTaskNo.value = ''
  pickKeyword.value = ''
  pickPage.value = 1
  createOpen.value = true
  void loadCandidates()
}

function onPickKeyword() {
  pickPage.value = 1
  if (pickTimer) {
    clearTimeout(pickTimer)
  }
  pickTimer = setTimeout(() => void loadCandidates(), 300)
}

function onPickPageChange(page: number) {
  pickPage.value = page
  void loadCandidates()
}

async function submitCreate() {
  const target = candidates.value.find((row) => row.taskNo === pickedTaskNo.value)
  if (!target) {
    ElMessage.warning('请先选择一条搜索记录')
    return
  }
  if (isEvaluated(target.queryText)) {
    ElMessage.error('该搜索已在评测中')
    return
  }
  creating.value = true
  try {
    await createEvaluation(target.taskNo)
    ElMessage.success('已加入评测')
    createOpen.value = false
    verdict.value = 'all'
    await Promise.all([load(true), loadHardCases()])
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    creating.value = false
  }
}

async function createFromHardCase(row: HardCaseVO) {
  if (!row.taskNo || isEvaluated(row.query)) {
    return
  }
  creatingTaskNo.value = row.taskNo
  try {
    await createEvaluation(row.taskNo)
    ElMessage.success('已加入评测')
    verdict.value = 'all'
    await Promise.all([load(true), loadHardCases()])
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    creatingTaskNo.value = ''
  }
}

// ---------- 发起重放 ----------
const replayingId = ref('')

async function doReplay(row: EvaluationRowVO) {
  if (!canReplay(row) || replayingId.value) {
    return
  }
  replayingId.value = row.id
  try {
    await replayEvaluation(row.id)
    ElMessage.success('重放完成，已刷新结论')
    await Promise.all([load(), loadHardCases()])
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    replayingId.value = ''
  }
}

// ---------- 对比视图 ----------
const compareOpen = ref(false)
const compareLoading = ref(false)
const compareRow = ref<EvaluationRowVO | null>(null)
const detail = ref<EvaluationDetailVO | null>(null)

const compareTitle = computed(() => `效果对比 · ${compareRow.value?.query ?? ''}`)

async function openCompare(row: EvaluationRowVO) {
  compareRow.value = row
  detail.value = null
  compareOpen.value = true
  compareLoading.value = true
  try {
    detail.value = await evaluationDetail(row.id)
  } catch (error) {
    ElMessage.error((error as Error).message)
    compareOpen.value = false
  } finally {
    compareLoading.value = false
  }
}

function valueOf(src: Record<string, unknown> | null | undefined, keys: string[]): unknown {
  if (!src) {
    return null
  }
  for (const key of keys) {
    const value = src[key]
    if (value !== null && value !== undefined && value !== '') {
      return value
    }
  }
  return null
}

function numOf(src: Record<string, unknown> | null | undefined, keys: string[]): number | null {
  const raw = valueOf(src, keys)
  if (raw === null) {
    return null
  }
  const value = Number(raw)
  return Number.isFinite(value) ? value : null
}

function textOf(src: Record<string, unknown> | null | undefined, keys: string[]): string | null {
  const raw = valueOf(src, keys)
  return raw === null ? null : String(raw)
}

function toMetrics(src?: Record<string, unknown> | null): Metrics {
  const items = numOf(src, ['items', 'itemCount'])
  const down = numOf(src, ['down', 'downCount'])
  let downRate = numOf(src, ['downRate'])
  if (downRate === null && items !== null && items > 0 && down !== null && down > 0) {
    downRate = down / items
  }
  return {
    taskNo: textOf(src, ['taskNo', 'searchId']),
    time: textOf(src, ['time', 'createTime']),
    items,
    down,
    downRate,
    top20AvgSim: numOf(src, ['top20AvgSim']),
    top1Sim: numOf(src, ['top1Sim']),
    top20Overlap: numOf(src, ['top20Overlap']),
    avgSim: numOf(src, ['avgSim']),
    elapsedMs: numOf(src, ['elapsedMs']),
  }
}

const baseMetrics = computed(() => toMetrics(detail.value?.base))

const replayMetrics = computed(() =>
  (detail.value?.replays ?? [])
    .map((item) => toMetrics(item))
    .sort((a, b) => String(a.time ?? '').localeCompare(String(b.time ?? ''))),
)

const replayTotal = computed(() => replayMetrics.value.length)
const replayTrimmed = computed(() => replayTotal.value > 3)
const shownReplays = computed(() => replayMetrics.value.slice(-3))
const latestMetrics = computed(() =>
  replayTotal.value ? replayMetrics.value[replayTotal.value - 1] : null,
)

function replayNo(index: number): number {
  return replayTotal.value - shownReplays.value.length + index + 1
}

function replayTip(metrics: Metrics): string {
  const time = metrics.time ? fmtTime(metrics.time) : ''
  return [time, metrics.taskNo].filter(Boolean).join(' · ')
}

function formatInt(value: number | null): string {
  return value === null ? '—' : String(value)
}

function formatSim(value: number | null): string {
  return value === null ? '—' : Number(value).toFixed(2)
}

function formatMs(value: number | null): string {
  return value === null ? '—' : `${value} ms`
}

function effectiveRate(metrics: Metrics): number | null {
  if (metrics.downRate !== null) {
    return metrics.downRate
  }
  if (metrics.items !== null && metrics.items > 0 && metrics.down !== null && metrics.down > 0) {
    return metrics.down / metrics.items
  }
  return null
}

function formatPct(ratio: number): string {
  const percent = ratio * 100
  return `${Math.abs(percent - Math.round(percent)) < 0.05 ? Math.round(percent) : percent.toFixed(1)}%`
}

function formatPoints(ratio: number): string {
  const points = Math.abs(ratio * 100)
  return `${Math.abs(points - Math.round(points)) < 0.05 ? Math.round(points) : points.toFixed(1)}pt`
}

function formatRate(metrics: Metrics): string {
  const rate = effectiveRate(metrics)
  return rate === null ? '—' : formatPct(rate)
}

function makeRow(key: string, label: string, values: string[], change: MetricChange): MetricRow {
  return {
    key,
    label,
    values,
    changeText: change.text,
    changeClass: change.cls,
    bad: change.bad,
  }
}

const metricRows = computed<MetricRow[]>(() => {
  const base = baseMetrics.value
  const latest = latestMetrics.value
  const columns = [base, ...shownReplays.value]

  const rows: MetricRow[] = []

  // 结果数：仅作参考，跌破基准 10% 时标红
  let resultChange: MetricChange = { text: '—', cls: 'flat', bad: false }
  if (latest && base.items !== null && latest.items !== null) {
    const delta = latest.items - base.items
    const text = delta === 0 ? '±0' : delta > 0 ? `↑${delta}` : `↓${-delta}`
    const drop = base.items > 0 ? (base.items - latest.items) / base.items : 0
    const bad = drop >= 0.1 - 1e-9
    resultChange = { text, cls: bad ? 'bad' : 'flat', bad }
  }
  rows.push(makeRow('items', '结果数', columns.map((item) => formatInt(item.items)), resultChange))

  // 踩数：上升为红（>= 1 即劣化），下降为绿
  let downChange: MetricChange = { text: '—', cls: 'flat', bad: false }
  if (latest && base.down !== null && latest.down !== null) {
    const delta = latest.down - base.down
    const text = delta === 0 ? '±0' : delta > 0 ? `↑${delta}` : `↓${-delta}`
    const bad = delta >= 1
    const good = delta < 0
    downChange = { text, cls: bad ? 'bad' : good ? 'good' : 'flat', bad }
  }
  rows.push(makeRow('down', '踩数', columns.map((item) => formatInt(item.down)), downChange))

  // 踩率：上升 >= 5 个百分点即劣化，下降为绿
  let rateChange: MetricChange = { text: '—', cls: 'flat', bad: false }
  if (latest) {
    const baseRate = effectiveRate(base)
    const latestRate = effectiveRate(latest)
    if (baseRate !== null && latestRate !== null) {
      const delta = latestRate - baseRate
      const text = Math.abs(delta) < 1e-9 ? '±0pt' : delta > 0 ? `↑${formatPoints(delta)}` : `↓${formatPoints(delta)}`
      const bad = delta >= 0.05 - 1e-9
      const good = delta < -0.0005
      rateChange = { text, cls: bad ? 'bad' : good ? 'good' : 'flat', bad }
    }
  }
  rows.push(makeRow('downRate', '踩率', columns.map((item) => formatRate(item)), rateChange))

  function simRow(key: string, label: string, pick: (metrics: Metrics) => number | null) {
    let change: MetricChange = { text: '—', cls: 'flat', bad: false }
    if (latest) {
      const baseValue = pick(base)
      const latestValue = pick(latest)
      if (baseValue !== null && latestValue !== null) {
        const delta = latestValue - baseValue
        const text =
          Math.abs(delta) < 0.005
            ? '±0.00'
            : delta > 0
              ? `↑${Math.abs(delta).toFixed(2)}`
              : `↓${Math.abs(delta).toFixed(2)}`
        const bad = delta < -0.005
        const good = delta > 0.005
        change = { text, cls: bad ? 'bad' : good ? 'good' : 'flat', bad }
      }
    }
    rows.push(makeRow(key, label, columns.map((item) => formatSim(pick(item))), change))
  }

  simRow('top20AvgSim', 'Top-20 平均相似度', (item) => item.top20AvgSim)
  simRow('top1Sim', 'Top-1 相似度', (item) => item.top1Sim)

  // Top-20 重叠：基准为 —，不参与好坏判定
  rows.push(
    makeRow(
      'top20Overlap',
      'Top-20 重叠',
      columns.map((item, index) =>
        index === 0 || item.top20Overlap === null
          ? '—'
          : `${Math.round(Math.min(1, Math.max(0, item.top20Overlap)) * 100)}%`,
      ),
      { text: '—', cls: 'flat', bad: false },
    ),
  )

  simRow('avgSim', '平均相似度', (item) => item.avgSim)

  // 耗时：仅展示变化，不做好坏判定
  let elapsedChange: MetricChange = { text: '—', cls: 'flat', bad: false }
  if (latest && base.elapsedMs !== null && latest.elapsedMs !== null) {
    const delta = latest.elapsedMs - base.elapsedMs
    elapsedChange = {
      text: delta === 0 ? '±0 ms' : delta > 0 ? `+${delta} ms` : `${delta} ms`,
      cls: 'flat',
      bad: false,
    }
  }
  rows.push(makeRow('elapsedMs', '耗时', columns.map((item) => formatMs(item.elapsedMs)), elapsedChange))

  return rows
})

const verdictState = computed<ConclusionKey>(() =>
  latestMetrics.value ? conclusionKey(detail.value?.conclusion) : 'unevaluated',
)

const verdictText = computed(() => {
  if (verdictState.value === 'degraded') {
    const reasons = (detail.value?.reasons ?? []).join('、')
    return `本次重放相较基准已劣化：${reasons || '触发劣化阈值'}`
  }
  return '本次重放未发现劣化'
})

const checks = computed(() => detail.value?.checks ?? [])
const checkBadCount = computed(() => checks.value.filter((check) => !check.ok).length)

const conclusion = computed(() => {
  const base = baseMetrics.value
  const latest = latestMetrics.value
  if (!latest) {
    return { cls: 'flat', text: '结论：尚无重放，暂无结论' }
  }
  const baseAvg = base.avgSim ?? base.top20AvgSim
  const latestAvg = latest.avgSim ?? latest.top20AvgSim
  const avgText = `${formatSim(baseAvg)} → ${formatSim(latestAvg)}`
  const baseDown = base.down ?? 0
  const latestDown = latest.down ?? 0

  if (latestDown < baseDown) {
    return { cls: 'good', text: `结论：治理有效（踩从 ${baseDown} 降到 ${latestDown}，平均相似度 ${avgText}）` }
  }
  if (latestDown > baseDown) {
    return { cls: 'bad', text: `结论：效果变差（踩从 ${baseDown} 升到 ${latestDown}，平均相似度 ${avgText}）` }
  }
  if (baseAvg !== null && latestAvg !== null && latestAvg - baseAvg > 0.005) {
    return { cls: 'good', text: `结论：效果改善（踩仍为 ${latestDown}，平均相似度 ${avgText}）` }
  }
  if (baseAvg !== null && latestAvg !== null && baseAvg - latestAvg > 0.005) {
    return { cls: 'bad', text: `结论：效果退步（踩仍为 ${latestDown}，平均相似度 ${avgText}）` }
  }
  return { cls: 'flat', text: `结论：无明显变化（踩仍为 ${latestDown}，平均相似度 ${avgText}）` }
})

const sampleHelpOpen = ref(false)

onMounted(() => {
  void load()
  void loadHardCases()
})

onBeforeUnmount(() => {
  if (keywordTimer) {
    clearTimeout(keywordTimer)
  }
})
</script>

<style scoped>
/* ---------- 评测列表面板 ---------- */
.eval-panel {
  overflow: hidden;
}

.eval-head {
  display: flex;
  align-items: center;
  min-height: 48px;
  padding: 13px 18px;
  border-bottom: 1px solid var(--content-line-soft);
}

.eval-head h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.4;
  color: #1f2a44;
}

.eval-head h2::before {
  content: '';
  flex: 0 0 auto;
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: var(--system-blue);
}

.eval-tools {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--content-line-soft);
}

.eval-search {
  width: 260px;
  max-width: 100%;
}

.eval-hint {
  margin-left: auto;
  color: #98a2b3;
  font-size: 11.5px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/* ---------- 结论筛选 chips ---------- */
.v-filter {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.v-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 26px;
  padding: 0 10px;
  border: 1px solid #dde4f0;
  border-radius: 999px;
  background: #fff;
  color: #5b6577;
  font-size: 12px;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.15s;
}

.v-chip b {
  color: #8a96ad;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.v-chip:hover {
  border-color: #b9c9ff;
  color: var(--system-blue);
}

.v-chip.is-on {
  border-color: #b9c9ff;
  background: var(--system-blue-weak);
  color: var(--system-blue);
  font-weight: 600;
}

.v-chip.is-on b {
  color: var(--system-blue);
}

.v-chip.bad.is-on {
  border-color: #f3c8c1;
  background: #fdf1ef;
  color: #b3261e;
}

.v-chip.bad.is-on b {
  color: #b3261e;
}

.v-chip.ok.is-on {
  border-color: #c6e6d3;
  background: #eef8f2;
  color: #177a3f;
}

.v-chip.ok.is-on b {
  color: #177a3f;
}

.v-chip.none.is-on {
  border-color: #dfe3ea;
  background: #f4f5f8;
  color: #5b6577;
}

/* ---------- 表格 ---------- */
.ev-q {
  font-weight: 600;
  color: #273244;
}

.eval-table :deep(tr.row-bad > td.el-table__cell) {
  background: #fdf6f5;
}

.eval-table :deep(tr.row-bad:hover > td.el-table__cell) {
  background: #fbeeec;
}

.eval-pager {
  padding: 4px 14px 6px;
}

/* ---------- 难例样本集 ---------- */
.sample-block {
  position: relative;
}

.sb-head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 48px;
  padding: 13px 18px;
  border-bottom: 1px solid var(--content-line-soft);
}

.sb-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.4;
  color: #1f2a44;
}

.sb-q {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
  margin-left: auto;
  padding: 0;
  border: 1px solid #d7e0f0;
  border-radius: 50%;
  background: #fff;
  color: #8c98ad;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
  transition: all 0.15s;
}

.sb-q:hover,
.sb-q[aria-expanded='true'] {
  border-color: #b9c9ff;
  background: var(--system-blue-weak);
  color: var(--system-blue);
}

.sb-help {
  position: absolute;
  top: 52px;
  left: 18px;
  z-index: 6;
  width: 440px;
  max-width: calc(100% - 36px);
  padding: 12px 14px;
  border: 1px solid #dbe4f5;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 30px rgba(20, 32, 70, 0.14);
}

.sb-help p {
  margin: 0;
  color: #5b6577;
  font-size: 12px;
  line-height: 1.7;
}

.sb-help p + p {
  margin-top: 6px;
}

.sb-help b {
  color: #344054;
}

.sb-help code {
  padding: 1px 5px;
  border-radius: 4px;
  background: #f2f5fa;
  color: #475467;
  font-family: var(--font-mono);
  font-size: 10.5px;
}

.sb-count {
  color: #a93226;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.sb-pager {
  padding: 4px 14px 6px;
}

/* ---------- 发起评测：候选记录单选列表 ---------- */
.mb-step {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 2px 0 8px;
  color: #475467;
  font-size: 12px;
  font-weight: 700;
}

.mb-step::before {
  content: '';
  width: 3px;
  height: 12px;
  border-radius: 2px;
  background: var(--system-blue);
}

.hist-search {
  margin-bottom: 8px;
}

.pick-list {
  display: flex;
  flex-direction: column;
  max-height: min(52vh, 420px);
  overflow: hidden auto;
  border: 1px solid var(--content-line);
  border-radius: 8px;
  background: #fff;
}

.pick-head,
.pick-row {
  display: grid;
  grid-template-columns: 18px 118px minmax(0, 1fr) 70px;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
}

.pick-head {
  position: sticky;
  top: 0;
  z-index: 1;
  border-bottom: 1px solid var(--content-line);
  background: #f6f8fc;
  color: #8c98ad;
  font-size: 11.5px;
  font-weight: 600;
}

.pick-row {
  min-width: 0;
  border-bottom: 1px solid var(--content-line-soft);
  cursor: pointer;
}

.pick-row:last-child {
  border-bottom: 0;
}

.pick-row:hover {
  background: #f8fbff;
}

.pick-row.is-pick {
  background: #f4f8ff;
}

.pick-row input {
  width: 14px;
  height: 14px;
  margin: 0;
  accent-color: var(--system-blue);
  cursor: pointer;
}

.pick-row input:disabled {
  cursor: not-allowed;
}

.pk-time {
  color: #667085;
  font-family: var(--font-mono);
  font-size: 11.5px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.pk-q {
  min-width: 0;
  overflow: hidden;
  color: #273244;
  font-size: 12.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pk-task {
  margin-left: 6px;
  color: #98a2b3;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 400;
}

.pk-flag {
  display: inline-block;
  margin-left: 6px;
  padding: 0 5px;
  border-radius: 4px;
  background: #f0f2f7;
  color: #8c98ad;
  font-size: 10px;
  font-weight: 600;
  vertical-align: 1px;
}

.pk-d {
  color: #475467;
  font-size: 12px;
  text-align: right;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.pick-empty {
  padding: 26px 12px;
  background: #fff;
  color: #9aa6bd;
  font-size: 12px;
  text-align: center;
}

.ta-r {
  text-align: right;
}

.pick-pager {
  margin-top: 8px;
}

/* ---------- 对比视图 ---------- */
.cmp-note {
  margin: 0 0 7px;
  color: #98a2b3;
  font-size: 11.5px;
  line-height: 1.6;
  font-variant-numeric: tabular-nums;
}

.cmp-wrap {
  overflow-x: auto;
  border: 1px solid var(--content-line);
  border-radius: 8px;
}

.cmp-tbl {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
}

.cmp-tbl th {
  padding: 9px 12px;
  border-bottom: 1px solid var(--content-line);
  background: #f6f8fc;
  color: #8c98ad;
  font-size: 11.5px;
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
}

.cmp-tbl th.num,
.cmp-tbl td.num {
  text-align: right;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.cmp-tbl th.is-basecol {
  background: #eef4ff;
  color: #5f73a8;
}

.cmp-tbl td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--content-line-soft);
  color: #344054;
  vertical-align: middle;
}

.cmp-tbl tbody tr:last-child td {
  border-bottom: 0;
}

.cmp-tbl td.is-basecol {
  background: #f8fbff;
  color: #1f2a44;
  font-weight: 700;
}

.cmp-tbl td.m-name {
  color: #273244;
  font-weight: 600;
  white-space: nowrap;
}

.cmp-tbl td.chg-cell {
  width: 1%;
}

.cmp-tbl tr.row-bad td {
  background: #fdf6f5;
}

.cmp-tbl tr.row-bad td.is-basecol {
  background: #fbf3f1;
}

.th-sub {
  display: block;
  color: #a4aec2;
  font-size: 10px;
  font-style: normal;
  font-weight: 400;
  letter-spacing: 0;
}

.cmp-demo {
  margin: 7px 0 0;
  color: #98a2b3;
  font-size: 11px;
  line-height: 1.6;
}

.cmp-sec-title {
  margin: 14px 0 7px;
  color: #1f2a44;
  font-size: 13px;
  font-weight: 700;
}

.chg {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.chg.good {
  color: var(--success);
}

.chg.bad {
  color: var(--error);
}

.chg.flat {
  color: #98a2b3;
}

/* ---------- 回归阻断提示 ---------- */
.regress {
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 12.5px;
  line-height: 1.6;
}

.regress.bad {
  border: 1px solid #f6d8d4;
  background: #fdf4f3;
  color: #a93226;
}

.regress.ok {
  border: 1px solid #d5eedf;
  background: #f1faf4;
  color: #177a3f;
}

.regress-line {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.regress-line .rg-ico {
  margin-right: 5px;
}

.regress-tip {
  margin-top: 3px;
  color: #8a3a31;
  font-size: 12px;
  font-weight: 400;
}

/* ---------- 评测数据检查（详情内 4 项只读） ---------- */
.chk-sum {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 8px;
  padding: 9px 12px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.5;
}

.chk-sum.ok {
  border: 1px solid #d5eedf;
  background: #f1faf4;
  color: #177a3f;
}

.chk-sum.warn {
  border: 1px solid #f5e0c8;
  background: #fdf6ef;
  color: #b25e09;
}

.chk-list {
  display: flex;
  flex-direction: column;
}

.chk-item {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 10px 2px;
  border-bottom: 1px solid #f2f5fa;
}

.chk-item:last-child {
  border-bottom: 0;
}

.chk-ico {
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
  margin-top: 2px;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 700;
  line-height: 16px;
  text-align: center;
}

.chk-ico.ok {
  border: 1px solid #cfe9da;
  background: #eaf7ef;
  color: #177a3f;
}

.chk-ico.warn {
  border: 1px solid #f6dfc2;
  background: #fdf3e6;
  color: #b25e09;
}

.chk-main {
  flex: 1;
  min-width: 0;
}

.chk-t {
  color: #273244;
  font-size: 13px;
  font-weight: 600;
}

.chk-d {
  margin-top: 3px;
  color: #8a5a1f;
  font-size: 12px;
  line-height: 1.65;
  word-break: break-all;
}

/* ---------- 结论句 ---------- */
.conclusion {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 11px 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.5;
}

.conclusion.good {
  border: 1px solid #d5eedf;
  background: #f1faf4;
  color: #177a3f;
}

.conclusion.bad {
  border: 1px solid #f6d8d4;
  background: #fdf4f3;
  color: #a93226;
}

.conclusion.flat {
  border: 1px solid #e6e9f0;
  background: #f7f8fb;
  color: #5b6577;
}

@media (max-width: 760px) {
  .eval-hint {
    margin-left: 0;
    width: 100%;
    white-space: normal;
  }

  .eval-search {
    width: 100%;
  }

  .sb-help {
    right: 12px;
    left: 12px;
    width: auto;
    max-width: none;
  }
}

</style>
