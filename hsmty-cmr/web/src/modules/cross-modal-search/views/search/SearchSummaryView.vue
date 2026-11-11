<template>
  <div class="page-card">
    <header class="cm-page-header">
      <div>
        <h1 class="cm-page-title">搜索结果摘要</h1>
      </div>
      <button class="dev-entry" type="button" title="研发说明" @click="devOpen = true">
        <span class="de-q" aria-hidden="true">?</span>研发说明
      </button>
    </header>

    <section class="cm-panel task-card" aria-label="任务信息">
      <div class="tc-top">
        <span class="tc-crumb">
          <router-link :to="{ name: 'behaviors' }">搜索行为</router-link> / 搜索结果摘要
        </span>
      </div>

      <div v-if="replayOfTaskNo && mode !== 'failed'" class="replay-bar" :title="`重放自 ${replayOfTaskNo}`">
        <div class="rp-main">
          ⟳ 重放结果 · 当时 <b>{{ replayWasText }}</b> 条 → 现在 <b>{{ nowCount }}</b> 条
        </div>
      </div>

      <div class="sec-head"><span class="sec-title">任务信息</span></div>
      <div class="tc-grid">
        <div class="tc-field">
          <div class="k">任务号</div>
          <div class="v mono" :title="taskNo">{{ taskNo }}</div>
        </div>
        <div class="tc-field">
          <div class="k">搜索词</div>
          <div class="v" :title="queryText">{{ queryText }}</div>
        </div>
        <div class="tc-field">
          <div class="k">模态对</div>
          <div class="v">{{ pairText }}</div>
        </div>
        <div class="tc-field">
          <div class="k">检索策略</div>
          <div class="v" :title="strategyTitle">{{ strategyText }}</div>
        </div>
        <div class="tc-field">
          <div class="k">时间</div>
          <div class="v">{{ timeText }}</div>
        </div>
        <div class="tc-field">
          <div class="k">结果数（当时）</div>
          <div class="v">{{ countText }}</div>
        </div>
        <div class="tc-field">
          <div class="k">状态</div>
          <div class="v"><span class="badge" :class="statusBadgeCls">{{ statusText }}</span></div>
        </div>
        <div class="tc-field">
          <div class="k">记录方式</div>
          <div class="v">系统自动记录</div>
        </div>
      </div>
    </section>

    <section v-loading="loading" class="cm-panel sum-card" aria-label="结果摘要">
      <div class="sec-head"><span class="sec-title">{{ resultTitle }}</span></div>

      <template v-if="mode === 'ready' || mode === 'loading'">
        <el-table
          :data="rows"
          row-key="itemId"
          :row-class-name="rowClassName"
          style="width: 100%"
        >
          <el-table-column label="排名" width="56">
            <template #default="{ $index }">
              <span class="rank-cell">{{ $index + 1 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="条目 ID" width="162">
            <template #default="{ row }">
              <span class="mono item-id" :title="row.itemId">{{ row.itemId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="名称" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="item-name">{{ row.name || '已删除' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="相似度" width="82">
            <template #default="{ row }">
              <b class="sim">{{ simText(row.score) }}</b>
            </template>
          </el-table-column>
          <el-table-column label="勾选" width="56" align="center" header-align="center">
            <template #default="{ row }">
              <el-checkbox
                :model-value="isChecked(row)"
                :disabled="sealed"
                :aria-label="`勾选第 ${row.rank || ''} 条 ${row.name || row.itemId}`"
                @change="(value: string | number | boolean) => toggleRow(row, value)"
              />
            </template>
          </el-table-column>
          <template #empty>
            <span class="table-empty-text">本次检索无结果条目</span>
          </template>
        </el-table>

        <div class="sum-foot">
          <span class="sel-info">已选 <b>{{ selected.length }}</b> 条</span>
          <span v-if="sealed" class="sealed-tip">已提交，不可重复标记</span>
          <el-button
            class="mark-btn"
            :class="{ 'is-sealed': sealed }"
            type="primary"
            :disabled="sealed || selected.length === 0"
            :loading="submitting"
            @click="markMismatch"
          >标记不匹配</el-button>
        </div>
      </template>

      <div v-else-if="mode === 'failed'" class="sum-empty">
        <span><b>本次运行失败</b>，未生成结果摘要</span>
        <span class="fail-reason">{{ failure?.errorMsg || '执行超时或检索服务异常' }}</span>
        <span class="sum-links">
          <router-link class="st-link" :to="{ name: 'behaviors', query: { task: taskNo, tab: 'fail' } }">查看失败任务</router-link>
          <router-link class="st-link" :to="{ name: 'search' }">返回检索台</router-link>
        </span>
      </div>

      <div v-else-if="mode === 'expired'" class="sum-empty is-none">
        <span><b>摘要已过期</b></span>
        <span class="sum-sub">摘要仅保留 30 天，条目明细不再展示，仅保留条数与赞踩汇总</span>
        <span class="sum-sub">共 {{ summary?.itemCount ?? 0 }} 条 · 赞 {{ summary?.upCount ?? 0 }} · 踩 {{ summary?.downCount ?? 0 }}</span>
        <span class="sum-links">
          <router-link class="st-link" :to="{ name: 'search' }">返回检索台</router-link>
        </span>
      </div>

      <div v-else class="sum-empty is-none">
        <span><b>未找到该任务的摘要</b></span>
        <span class="sum-sub">摘要仅保留 30 天，或该任务已过期</span>
        <span class="sum-links">
          <router-link class="st-link" :to="{ name: 'search' }">返回检索台</router-link>
        </span>
      </div>
    </section>

    <DevNotesDrawer v-model="devOpen" title="研发需求说明">
      <p class="help-lead">
        本页是<b>搜索结果摘要</b>：任务信息（任务号、状态、当时条件、检索策略与结果数）+ 结果摘要（Top-20）。
        摘要只存条目 ID + 名称 + 分数，不存文件本体；保留 30 天，到期只留条数与赞踩汇总。
      </p>

      <div>
        <div class="hb-t">任务信息 8 项</div>
        <table class="help-table">
          <thead>
            <tr><th>字段</th><th>口径</th></tr>
          </thead>
          <tbody>
            <tr><td>任务号</td><td>地址栏 taskNo 带入，等宽字体、整段可选中复制；摘要按任务号回读，不回落其他任务。</td></tr>
            <tr><td>搜索词</td><td>本次检索的输入内容；文件检索无文本时显示「—」。</td></tr>
            <tr><td>模态对</td><td>文搜图 / 图搜图 / 文搜视频等；目标模态为空（全部）时显示「文搜全部」。</td></tr>
            <tr><td>检索策略</td><td>策略编号 + 版本号（如 STG… v3），取不到显示「—」；悬停可看本次相似度阈值。</td></tr>
            <tr><td>时间</td><td>检索发起时间，YYYY-MM-DD HH:mm:ss。</td></tr>
            <tr><td>结果数（当时）</td><td>本次实际下发条数，不是摘要表行数（摘要只取 Top-20）。</td></tr>
            <tr><td>状态</td><td>已封存 / 未封存；提交不匹配反馈即自动封存，封存后勾选与提交锁死。</td></tr>
            <tr><td>记录方式</td><td>固定「系统自动记录」：摘要由结果页写入，本页只读。</td></tr>
          </tbody>
        </table>
      </div>

      <div>
        <div class="hb-t">结果摘要（Top-20）</div>
        <table class="help-table">
          <thead>
            <tr><th>列</th><th>取值与说明</th></tr>
          </thead>
          <tbody>
            <tr><td>排名</td><td>按相似度降序的序号，从 1 起。</td></tr>
            <tr><td>条目 ID</td><td>ITM 编号，等宽字体、整段可选中复制。</td></tr>
            <tr><td>名称</td><td>超长单行省略，悬停看全量；条目已删除时显示「已删除」。</td></tr>
            <tr><td>相似度</td><td>保留两位小数（toFixed(2)），蓝色粗体。</td></tr>
            <tr><td>勾选</td><td>常显 checkbox，不再切换「选择模式」；封存后全部禁用。</td></tr>
          </tbody>
        </table>
      </div>

      <p class="help-note">
        <b>提交与封存：</b>已选 ≥1 条后「标记不匹配」→ feedbacks（entryType=mismatch，sourceTaskNo / sourceQuery + 勾选条目 {itemId, name, source}）→
        成功后 sealTask；提交即封存，同一任务不可重复提交。去向不在本页预填，留到结果运营处理。
      </p>
      <p class="help-note">
        <b>空态：</b>失败任务不生成摘要，只展示失败原因与「查看失败任务」；expired=true（超过 30 天）只保留条数与赞踩汇总；两者都给「返回检索台」。
        无摘要记录时显示「未找到该任务的摘要」，不复用其他任务数据，任务号仍显示地址栏里的真实任务号。
      </p>
      <p class="help-note">
        <b>重放：</b>任务带重放来源时顶部显示「⟳ 重放结果 · 当时 X 条 → 现在 Y 条」，摘要展示本次重放的 Top-20。
      </p>
    </DevNotesDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createFeedback, failedTasks, searchSummary, sealTask } from '../../api'
import type { FailedTaskVO, ResultItemVO, SearchSummaryVO } from '../../api/types'
import { fmtTime } from '../../utils/format'
import DevNotesDrawer from '../../components/DevNotesDrawer.vue'

type PageMode = 'loading' | 'ready' | 'failed' | 'expired' | 'notfound'

const MODALITY_SHORT: Record<string, string> = { text: '文', image: '图', audio: '音', video: '视频' }

const route = useRoute()
const taskNo = String(route.params.taskNo)

const devOpen = ref(false)
const loading = ref(false)
const mode = ref<PageMode>('loading')
const summary = ref<SearchSummaryVO | null>(null)
const failure = ref<FailedTaskVO | null>(null)
const selected = ref<ResultItemVO[]>([])
const submitting = ref(false)
const replayWasCount = ref<number | null>(null)

const rows = computed(() =>
  [...(summary.value?.items ?? [])].sort((a, b) => scoreOf(b) - scoreOf(a)),
)

const sealed = computed(() => summary.value?.sealed === true)

const replayOfTaskNo = computed(() => {
  const replayNo = summary.value?.replayOfTaskNo
  return replayNo ? String(replayNo) : ''
})

const nowCount = computed(() => summary.value?.itemCount ?? rows.value.length)
const replayWasText = computed(() => (replayWasCount.value == null ? '—' : String(replayWasCount.value)))

const queryText = computed(() => summary.value?.queryText || failure.value?.queryText || '—')

const pairText = computed(() => {
  const queryModality = summary.value?.queryModality
  if (!queryModality) {
    return '—'
  }
  const from = MODALITY_SHORT[queryModality] ?? queryModality
  const targetModality = summary.value?.targetModality
  const to = targetModality ? MODALITY_SHORT[targetModality] ?? targetModality : '全部'
  return `${from}搜${to}`
})

const strategyText = computed(() => {
  const strategyNo = summary.value?.strategyNo
  if (!strategyNo) {
    return '—'
  }
  const version = summary.value?.strategyVersion
  return version == null ? strategyNo : `${strategyNo} v${version}`
})

const strategyTitle = computed(() => {
  const threshold = summary.value?.threshold
  return threshold == null ? '' : `相似度阈值 ≥ ${Number(threshold).toFixed(2)}`
})

const timeText = computed(() => fmtTime(summary.value?.createTime ?? failure.value?.createTime))

const countText = computed(() => {
  if (mode.value === 'failed' || mode.value === 'notfound') {
    return '—'
  }
  return String(summary.value?.itemCount ?? rows.value.length)
})

const statusText = computed(() => {
  if (mode.value === 'failed' || mode.value === 'notfound' || mode.value === 'loading') {
    return '—'
  }
  return sealed.value ? '已封存' : '未封存'
})

const statusBadgeCls = computed(() => (sealed.value ? 'ok' : 'gray'))

const resultTitle = computed(() => {
  switch (mode.value) {
    case 'ready':
      return `结果摘要（共 ${rows.value.length} 条）`
    case 'failed':
      return '结果摘要（未生成）'
    case 'expired':
      return '结果摘要（已过期）'
    case 'notfound':
      return '结果摘要（未找到）'
    default:
      return '结果摘要'
  }
})

function scoreOf(item: ResultItemVO): number {
  const score = Number(item.score)
  return Number.isFinite(score) ? score : 0
}

function simText(value?: number | null): string {
  const score = Number(value)
  return Number.isFinite(score) ? score.toFixed(2) : '—'
}

function isChecked(row: ResultItemVO): boolean {
  return selected.value.some((item) => item.itemId === row.itemId)
}

function rowClassName({ row }: { row: ResultItemVO }): string {
  return isChecked(row) ? 'is-checked-row' : ''
}

function toggleRow(row: ResultItemVO, value: string | number | boolean): void {
  if (value === true) {
    if (!isChecked(row)) {
      selected.value = [...selected.value, row]
    }
  } else {
    selected.value = selected.value.filter((item) => item.itemId !== row.itemId)
  }
}

async function load(): Promise<void> {
  loading.value = true
  mode.value = 'loading'
  summary.value = null
  selected.value = []
  failure.value = null
  replayWasCount.value = null
  try {
    const data = await searchSummary(taskNo)
    summary.value = data
    // 失败任务同样落历史（结果数 0）不生成摘要：按失败任务只读口径展示空态
    const maybeFailed = data.taskStatus === 'failed' || (data.itemCount ?? 0) === 0
    const failed = maybeFailed ? await lookupFailure(taskNo) : null
    if (failed) {
      failure.value = failed
      mode.value = 'failed'
      return
    }
    mode.value = data.expired ? 'expired' : 'ready'
    if (data.replayOfTaskNo) {
      void loadReplayBase(String(data.replayOfTaskNo))
    }
  } catch {
    summary.value = null
    const failed = await lookupFailure(taskNo)
    if (failed) {
      failure.value = failed
      mode.value = 'failed'
    } else {
      mode.value = 'notfound'
    }
  } finally {
    loading.value = false
  }
}

/** 失败任务不生成摘要；接口未实现时静默回落「未找到」空态 */
async function lookupFailure(no: string): Promise<FailedTaskVO | null> {
  try {
    const page = await failedTasks({ keyword: no, pageNum: 1, pageSize: 20 })
    return page.records.find((item) => item.bizNo === no) ?? null
  } catch {
    return null
  }
}

/** 重放条「当时 N 条」：按原任务号回读一次，取不到显示「—」 */
async function loadReplayBase(no: string): Promise<void> {
  try {
    const base = await searchSummary(no)
    replayWasCount.value = base.itemCount ?? null
  } catch {
    replayWasCount.value = null
  }
}

async function markMismatch(): Promise<void> {
  const current = summary.value
  if (!current || sealed.value || selected.value.length === 0) {
    return
  }
  const count = selected.value.length
  submitting.value = true
  try {
    await createFeedback({
      entryType: 'mismatch',
      sourceTaskNo: taskNo,
      sourceQuery: current.queryText ?? undefined,
      items: selected.value.map((item) => ({
        itemId: item.itemId,
        name: item.name,
        source: item.source,
      })),
    })
    // 数据流：提交即自动封存
    try {
      await sealTask(taskNo)
    } catch (error) {
      const message = (error as Error).message || ''
      if (!message.includes('已封存')) {
        ElMessage.warning(`反馈已提交，封存同步失败：${message}`)
      }
    }
    current.sealed = true
    selected.value = []
    ElMessage.success(`已提交 ${count} 条不匹配记录`)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.task-card,
.sum-card {
  padding: 16px 18px;
}

.tc-top {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.tc-crumb { font-size: 12.5px; color: var(--muted); }
.tc-crumb a { color: var(--system-blue); text-decoration: none; }
.tc-crumb a:hover { text-decoration: underline; }

.replay-bar {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 12px;
  padding: 9px 12px;
  border: 1px solid #f0dfb2;
  border-radius: 7px;
  background: #fffbf0;
  font-size: 12px;
  line-height: 1.6;
  color: #8a6a1f;
}
.replay-bar .rp-main { font-weight: 700; color: #8a5a00; }
.replay-bar b { color: #8a5a00; font-weight: 700; font-variant-numeric: tabular-nums; }

.sec-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.sec-head::before {
  content: '';
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: var(--system-blue);
}
.sec-title { font-size: 15px; font-weight: 700; color: #1f2a44; }

.tc-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 18px;
  padding: 13px 14px;
  border: 1px solid #eaf0f9;
  border-radius: 8px;
  background: var(--content-soft);
}
.tc-field .k { margin-bottom: 3px; font-size: 11px; color: #9aa6bd; }
.tc-field .v {
  overflow: hidden;
  color: #1f2a44;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.tc-field .v.mono {
  font-family: var(--font-mono);
  font-size: 14px;
  letter-spacing: 0.3px;
  user-select: all;
}

.rank-cell { color: #8a96ad; font-variant-numeric: tabular-nums; }
.item-id { color: #1f2a44; letter-spacing: 0.2px; user-select: all; }
.item-name { color: #1f2a44; font-weight: 600; }
.sim {
  color: var(--system-blue);
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.sum-card :deep(.el-table__row.is-checked-row) { background: #f4f8ff; }

.sum-foot {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid #eef2f8;
}
.sel-info { font-size: 12.5px; color: var(--muted); }
.sel-info b { margin: 0 2px; color: #1f2a44; font-weight: 700; font-variant-numeric: tabular-nums; }
.sealed-tip { color: #98a2b3; font-size: 12px; white-space: nowrap; }
.mark-btn { margin-left: auto; }
.mark-btn.is-sealed,
.mark-btn.is-sealed:hover {
  background: #c2ccdd;
  border-color: #c2ccdd;
  color: #fff;
  box-shadow: none;
  opacity: 1;
}

.sum-empty {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 13px 15px;
  border: 1px solid #f3c4c6;
  border-radius: 8px;
  background: #fff7f7;
  color: #8c5560;
  font-size: 12.5px;
}
.sum-empty b { color: #b42318; font-weight: 700; }
.sum-empty.is-none { border-color: #e4edf8; background: #fbfdff; color: #475467; }
.sum-empty.is-none b { color: #1f2a44; }
.sum-sub { color: #98a2b3; font-size: 12px; }
.fail-reason { color: #8c5560; font-size: 12px; }
.sum-links { display: flex; align-items: center; gap: 14px; margin-left: auto; white-space: nowrap; }
.st-link {
  color: var(--system-blue);
  font-size: 12.5px;
  text-decoration: none;
  border-bottom: 1px dashed #aebdf5;
}
.st-link:hover { border-bottom-style: solid; }

.table-empty-text { display: block; padding: 14px 0; color: var(--muted); font-size: 12.5px; }

@media (max-width: 1100px) {
  .tc-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
