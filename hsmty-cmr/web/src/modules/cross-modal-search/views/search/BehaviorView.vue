<template>
  <div class="page-card">
    <header class="cm-page-header">
      <div>
        <h1 class="cm-page-title">搜索行为</h1>
        <p class="cm-page-subtitle">谁在什么时候搜了什么、结果多少、赞 / 踩多少。</p>
      </div>
      <button class="dev-entry" type="button" title="研发说明" @click="devOpen = true">
        <span class="de-q" aria-hidden="true">?</span>研发说明
      </button>
    </header>

    <section ref="listSection" class="cm-panel panel-pad" aria-label="搜索行为">
      <div class="cm-panel-head">
        <h3 class="cm-panel-title">搜索行为</h3>
      </div>

      <div class="chip-row">
        <button type="button" class="cm-chip" :class="{ 'is-active': chip === 'all' }" @click="setChip('all')">全部</button>
        <button type="button" class="cm-chip" :class="{ 'is-active': chip === 'down' }" @click="setChip('down')">有踩</button>
        <button type="button" class="cm-chip" :class="{ 'is-active': chip === 'none' }" @click="setChip('none')">无踩</button>
        <div class="chip-tools">
          <el-input
            v-model="query.keyword"
            class="op-search"
            placeholder="按操作人 / 输入内容 / 任务号搜索"
            clearable
            @input="onKeywordInput"
            @keyup.enter="applySearch"
          />
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" size="small" style="width: 100%">
        <el-table-column label="时间" width="140">
          <template #default="{ row }">
            <span class="time-cell">{{ fmtTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="任务号" width="170">
          <template #default="{ row }">
            <div class="task-cell">
              <div class="task-line">
                <span class="mono task-no" :title="`任务号 ${row.taskNo}`">{{ row.taskNo }}</span>
                <el-button
                  class="copy-btn"
                  link
                  type="primary"
                  title="复制任务号"
                  aria-label="复制任务号"
                  @click="copyTaskNo(row.taskNo)"
                >
                  <el-icon><component :is="'CopyDocument'" /></el-icon>
                </el-button>
              </div>
              <span
                v-if="row.replayOfTaskNo"
                class="replay-line"
                :title="`重放自 ${row.replayOfTaskNo}`"
              >⟳ 重放自 {{ row.replayOfTaskNo }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作人" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ row.operatorName || '—' }}</template>
        </el-table-column>
        <el-table-column label="模态对" width="110">
          <template #default="{ row }">{{ pairLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="输入内容" min-width="200">
          <template #default="{ row }">
            <span class="ellipsis" :title="inputText(row)">{{ inputText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结果数" width="80" align="right" header-align="right">
          <template #default="{ row }">
            <span class="num-cell">{{ hasItemCount(row) ? row.itemCount : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="赞 / 踩" width="80" align="center" header-align="center">
          <template #default="{ row }">
            <span
              v-if="hasItemCount(row)"
              class="votes"
              :class="{ zero: !row.upCount && !row.downCount }"
              :title="`本次结果中被点赞 ${row.upCount || 0} 条、被点踩 ${row.downCount || 0} 条`"
            >{{ row.upCount || 0 }} / {{ row.downCount || 0 }}</span>
            <span v-else class="votes zero" title="无结果数">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSummary(row)">详情</el-button>
            <el-button
              link
              type="primary"
              class="rerun-btn"
              :loading="rerunning === row.resultSetId"
              @click="rerun(row)"
            >重新执行</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="cm-empty table-empty">
            <div class="cm-empty-title">当前筛选条件下没有搜索记录</div>
            <el-button size="small" @click="clearFilters">清除筛选</el-button>
          </div>
        </template>
      </el-table>

      <el-pagination
        class="cm-pagination"
        layout="total, prev, pager, next"
        :total="total"
        :current-page="query.pageNum"
        :page-size="PAGE_SIZE"
        @current-change="changePage"
      />
    </section>

    <section ref="failSection" class="cm-panel panel-pad" aria-label="失败任务">
      <div class="cm-panel-head">
        <h3 class="cm-panel-title">失败任务</h3>
      </div>
      <p class="auto-note">失败由系统自动处置：引擎侧自动重试，数据侧自动转隔离，此处只读展示</p>

      <el-table v-loading="failLoading" :data="failRows" size="small" style="width: 100%">
        <el-table-column label="任务号" width="170">
          <template #default="{ row }">
            <span class="mono">{{ row.bizNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="输入内容" min-width="200">
          <template #default="{ row }">
            <span class="ellipsis" :title="row.queryText || ''">{{ row.queryText || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="180">
          <template #default="{ row }">
            <span class="ellipsis" :title="row.errorMsg || ''">{{ row.errorMsg || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="失败类型" width="100">
          <template #default="{ row }">
            <span class="badge" :class="failTypeBadge(row.failType)">{{ failTypeLabel(row.failType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="系统处理" width="170">
          <template #default="{ row }">
            <span class="mono">{{ disposalText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="badge" :class="handlingBadge(row.handling)">{{ handlingLabel(row.handling) }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <div class="cm-empty table-empty">
            <div class="cm-empty-title">暂无失败任务</div>
          </div>
        </template>
      </el-table>

      <el-pagination
        class="cm-pagination"
        layout="total, prev, pager, next"
        :total="failTotal"
        :current-page="failPage"
        :page-size="PAGE_SIZE"
        @current-change="changeFailPage"
      />
    </section>

    <DevNotesDrawer v-model="devOpen" title="研发需求说明">
      <p class="help-lead">
        本页是<b>用户搜索行为流水</b>：一条搜索一条记录，<b>不做跨记录的统计与聚合</b>；每条记录只留两个结果：<b>结果数</b>与<b>赞 / 踩</b>。更深的运营与评测在结果运营、效果评测。
      </p>

      <div>
        <div class="hb-t">表格 8 列（赞 / 踩 同格）</div>
        <table class="help-table">
          <thead>
            <tr><th>列</th><th>取值与说明</th></tr>
          </thead>
          <tbody>
            <tr><td class="fld">时间</td><td>搜索发起时间，YYYY-MM-DD HH:mm；第一列只放时间，固定按时间倒序（最新在上），不提供排序切换。</td></tr>
            <tr><td class="fld">任务号</td><td>本次搜索的任务编号（TASK…），紧接时间列独立一列；等宽字体、单行、可整体选中复制，不可点；查看详情走操作列「详情」。</td></tr>
            <tr><td class="fld">操作人</td><td>登录账户的昵称，<b>只显示昵称（张三）</b>；没有昵称时回退显示账户名；用于按账户追溯是谁搜的。</td></tr>
            <tr><td class="fld">模态对</td><td>文搜图 / 图搜图 / 文搜视频 / 图搜文 / 视频搜视频；真实搜索记录缺省按「文搜图」。</td></tr>
            <tr><td class="fld">输入内容</td><td>文搜类＝query 原文；图搜 / 视频搜＝所上传文件名（图片：发票扫描页_01.jpg）；单行省略，悬停看全量。</td></tr>
            <tr><td class="fld">结果数</td><td>该次搜索返回条数（130 / 87 / 42）；未搜到为「—」。</td></tr>
            <tr><td class="fld">赞 / 踩</td><td>同一格显示「赞数 / 踩数」（如 3 / 5），按任务号汇总该次结果中被点过赞 / 踩的<b>条目数</b>；两者都为 0 时灰色显示「0 / 0」，无结果数为「—」。</td></tr>
            <tr><td class="fld">操作</td><td>「详情」跳任务详情页；「重新执行」生成一条新任务记录并跳结果页；表格行本身不承接点击。</td></tr>
          </tbody>
        </table>
        <p class="help-note"><b>重放来源不是独立列：</b>有重放来源时，任务号下方追加一行小灰字「⟳ 重放自 TASK…」，纯文本标注原任务号，不可点。</p>
      </div>

      <p class="help-note"><b>记录边界：</b>本页只记用户搜索行为，一条行为一条记录；编辑策略、屏蔽数据等其他操作不记在本页。赞 / 踩落在数据条目上，本页只按任务号汇总条目数，不记单条明细。</p>

      <p class="help-note"><b>不设状态：</b>搜索记录不设成功 / 失败状态：结果数有值即搜到了，为「—」即未搜到。失败任务单独在页面「失败任务」表展示，其处置状态独立保留。</p>

      <p class="help-note"><b>赞 / 踩口径：</b>结果页按「任务号 + 条目号」写入评价；本页按行任务号统计 up / down 的<b>条目数</b>（同一条多次只算 1 条），不统计点击次数。新任务号默认 0 / 0。</p>

      <p class="help-note"><b>重新执行：</b>点「重新执行」按原记录的条件重跑，生成新任务号与新记录，并携带「⟳ 重放自 原任务号」标记跳转结果页；同一 query 重放几次就有几条记录，可据此看出被重放过几次、结果数与赞 / 踩如何变化。</p>

      <p class="help-note"><b>详情与快照：</b>「详情」→ 任务详情页（任务号带入）。详情只存 Top-20 摘要（ID + 名称 + 分数），不存图片本体，保留 30 天；本页不存结果快照，「重新执行」只存条件与新结果数，在结果页看「当时 → 现在」的对比。</p>

      <p class="help-note"><b>排序与筛选：</b>固定按时间倒序（最新在上）；「全部 / 有踩 / 无踩」chip（有踩＝踩数 &gt; 0；无踩＝踩数 = 0 或「—」）+ 搜索框（按操作人昵称 / 账户名、输入内容、任务号、重放来源，包含匹配、不区分大小写）叠加过滤；每页 10 条；无结果时给「清除筛选」。</p>

      <p class="help-note"><b>失败任务（只读）：</b>表格 6 列：任务号 / 输入内容 / 失败原因 / 失败类型 / 系统处理 / 状态；失败由系统自动处置——引擎侧失败自动重试（最多 3 次），数据侧失败自动转隔离区；状态取「重试中 / 已恢复 / 已转隔离」，本页不提供人工处置；每页 10 条。</p>

      <p class="help-note"><b>入口与跳转：</b>支持 <code>?task=</code> 定位一条搜索记录（自动把任务号填入搜索框并滚动到列表）、<code>?tab=fail</code> 定位失败任务表；上游来自结果页（写任务元数据与赞踩），下游去任务详情、结果页重放、结果运营与效果评测。图搜类输入取文件名；无结果数（「—」）时原结果数按 0 计。</p>
    </DevNotesDrawer>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { failedTasks, pageBehaviors, rerunSearchResults } from '../../api'
import type { BehaviorVO, FailedTaskVO } from '../../api/types'
import { fmtTime } from '../../utils/format'
import DevNotesDrawer from '../../components/DevNotesDrawer.vue'

const PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()

const devOpen = ref(false)
const loading = ref(false)
const rows = ref<BehaviorVO[]>([])
const total = ref(0)

const chip = ref<'all' | 'down' | 'none'>('all')
const query = ref({ pageNum: 1, pageSize: PAGE_SIZE, keyword: '' })
/** ?task= 精确定位时使用；用户继续输入或切换 chip 后解除 */
const pinnedTaskNo = ref('')
const rerunning = ref('')

const failLoading = ref(false)
const failRows = ref<FailedTaskVO[]>([])
const failTotal = ref(0)
const failPage = ref(1)

const listSection = ref<HTMLElement>()
const failSection = ref<HTMLElement>()

let keywordTimer: ReturnType<typeof setTimeout> | undefined

const MODALITY_SHORT: Record<string, string> = { text: '文', image: '图', audio: '音', video: '视频' }

function pairLabel(row: BehaviorVO) {
  const from = row.queryModality ? MODALITY_SHORT[row.queryModality] || row.queryModality : ''
  const to = row.targetModality ? MODALITY_SHORT[row.targetModality] || row.targetModality : ''
  if (from && to) {
    return `${from}搜${to}`
  }
  return '文搜图'
}

function hasItemCount(row: BehaviorVO) {
  return typeof row.itemCount === 'number' && row.itemCount > 0
}

function inputText(row: BehaviorVO) {
  const text = (row.queryText || '').trim()
  if (text) {
    return text
  }
  // 契约未返回 queryFile 时后端可能将文件名放入 queryText；两者都无则回退占位
  const file = (row as BehaviorVO & { queryFile?: string | null }).queryFile
  if (file) {
    const name = file.split(/[\\/]/).pop() || file
    const prefix = row.queryModality === 'image' ? '图片：' : row.queryModality === 'video' ? '视频：' : row.queryModality === 'audio' ? '音频：' : ''
    return `${prefix}${name}`
  }
  return '（文件检索）'
}

function failTypeLabel(type?: string | null) {
  if (type === 'engine') {
    return '引擎侧'
  }
  if (type === 'data') {
    return '数据侧'
  }
  return type || '—'
}

function failTypeBadge(type?: string | null) {
  if (type === 'engine') {
    return 'run'
  }
  if (type === 'data') {
    return 'fail'
  }
  return 'gray'
}

const HANDLING_LABEL: Record<string, string> = {
  retrying: '重试中',
  recovered: '已恢复',
  isolated: '已转隔离',
}
const HANDLING_BADGE: Record<string, string> = {
  retrying: 'run',
  recovered: 'ok',
  isolated: 'fail',
}

function handlingLabel(handling?: string | null) {
  return handling ? HANDLING_LABEL[handling] || handling : '—'
}

function handlingBadge(handling?: string | null) {
  return handling ? HANDLING_BADGE[handling] || 'gray' : 'gray'
}

function disposalText(row: FailedTaskVO) {
  if (row.handling === 'recovered') {
    return '已自动重试 · 已恢复'
  }
  if (row.handling === 'retrying') {
    return '系统自动重试中'
  }
  if (row.handling === 'isolated') {
    return '已转隔离'
  }
  if (row.failDisposal === 'retry') {
    return '自动重试'
  }
  if (row.failDisposal === 'isolate') {
    return '已转隔离'
  }
  return '系统自动处置'
}

async function load() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      pageNum: query.value.pageNum,
      pageSize: PAGE_SIZE,
    }
    if (pinnedTaskNo.value) {
      params.taskNo = pinnedTaskNo.value
    } else if (query.value.keyword.trim()) {
      params.keyword = query.value.keyword.trim()
    }
    if (chip.value === 'down') {
      params.downCount = true
    } else if (chip.value === 'none') {
      params.downCount = false
    }
    const page = await pageBehaviors(params)
    rows.value = page.records
    total.value = page.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function loadFailed() {
  failLoading.value = true
  try {
    const page = await failedTasks({ pageNum: failPage.value, pageSize: PAGE_SIZE })
    failRows.value = page.records
    failTotal.value = page.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    failLoading.value = false
  }
}

function onKeywordInput() {
  pinnedTaskNo.value = ''
  if (keywordTimer) {
    clearTimeout(keywordTimer)
  }
  keywordTimer = setTimeout(() => {
    query.value.pageNum = 1
    load()
  }, 300)
}

function applySearch() {
  if (keywordTimer) {
    clearTimeout(keywordTimer)
  }
  pinnedTaskNo.value = ''
  query.value.pageNum = 1
  load()
}

function setChip(value: 'all' | 'down' | 'none') {
  chip.value = value
  pinnedTaskNo.value = ''
  query.value.pageNum = 1
  load()
}

function clearFilters() {
  query.value.keyword = ''
  pinnedTaskNo.value = ''
  chip.value = 'all'
  query.value.pageNum = 1
  load()
}

function changePage(pageNum: number) {
  query.value.pageNum = pageNum
  load()
}

function changeFailPage(pageNum: number) {
  failPage.value = pageNum
  loadFailed()
}

function openSummary(row: BehaviorVO) {
  router.push({ name: 'search-summary', params: { taskNo: row.taskNo } })
}

async function rerun(row: BehaviorVO) {
  rerunning.value = row.resultSetId
  try {
    const result = await rerunSearchResults(row.resultSetId)
    router.push(`/search/results/${result.resultSetId}`)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    rerunning.value = ''
  }
}

async function copyTaskNo(taskNo: string) {
  try {
    await navigator.clipboard.writeText(taskNo)
    ElMessage.success('已复制任务号')
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = taskNo
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    try {
      document.execCommand('copy')
      ElMessage.success('已复制任务号')
    } catch {
      ElMessage.error('复制失败，请手动选择任务号')
    } finally {
      document.body.removeChild(textarea)
    }
  }
}

function focusSection(el?: HTMLElement) {
  if (!el) {
    return
  }
  el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  el.classList.add('sec-flash')
  setTimeout(() => el.classList.remove('sec-flash'), 1200)
}

onMounted(async () => {
  const task = typeof route.query.task === 'string' ? route.query.task : ''
  const tab = typeof route.query.tab === 'string' ? route.query.tab : ''
  if (task) {
    pinnedTaskNo.value = task
    query.value.keyword = task
  }
  await Promise.all([load(), loadFailed()])
  if (task) {
    const hit = rows.value.some((row) => row.taskNo === task)
    if (hit) {
      ElMessage.success(`已定位任务 ${task}`)
    } else {
      ElMessage.warning(`未找到任务 ${task}`)
    }
    await nextTick()
    focusSection(listSection.value)
  }
  if (tab === 'fail') {
    await nextTick()
    focusSection(failSection.value)
  }
})
</script>

<style scoped>
.panel-pad {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 18px;
}
.panel-pad.sec-flash {
  animation: secFlash 1.1s ease-out;
}
@keyframes secFlash {
  0% { box-shadow: 0 0 0 3px rgba(65, 105, 246, 0.2); }
  100% { box-shadow: 0 0 0 0 rgba(65, 105, 246, 0); }
}

.chip-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.chip-tools {
  margin-left: auto;
}
.op-search {
  width: 260px;
}

.task-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.task-line {
  display: flex;
  align-items: center;
  gap: 2px;
  min-width: 0;
}
.task-no {
  min-width: 0;
  overflow: hidden;
  color: #273244;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.copy-btn {
  flex: 0 0 auto;
  height: auto;
  padding: 0;
}
.rerun-btn {
  font-weight: 600;
}
.replay-line {
  overflow: hidden;
  color: #98a2b3;
  font-size: 12px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time-cell {
  color: #475467;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.ellipsis {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.num-cell,
.votes {
  color: #475467;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.votes.zero {
  color: #b3bccb;
}

.auto-note {
  margin: 0;
  padding: 9px 12px;
  border: 1px dashed #cdd9f5;
  border-radius: 8px;
  background: var(--system-blue-weak);
  color: #475467;
  font-size: 12px;
  line-height: 1.7;
}

.table-empty {
  border: 0;
  background: transparent;
  padding: 18px 12px;
}
</style>
