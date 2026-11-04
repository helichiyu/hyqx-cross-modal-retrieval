<template>
  <div class="page-card">
    <el-card>
      <el-tabs v-model="tab">
        <!-- 评测集 -->
        <el-tab-pane label="评测集" name="sets">
          <div class="toolbar">
            <el-select v-model="setStatusFilter" placeholder="状态" clearable style="width: 150px">
              <el-option v-for="d in setStatusDict" :key="d.alias" :label="d.label" :value="d.alias" />
            </el-select>
            <el-button type="primary" @click="loadSets">查询</el-button>
            <div class="grow" />
            <el-button type="primary" :icon="'Plus'" @click="setCreateVisible = true">
              从检索历史创建
            </el-button>
          </div>
          <el-table :data="sets" v-loading="setsLoading" border stripe>
            <el-table-column label="编号" width="180">
              <template #default="{ row }"><BizNoTag :value="row.bizNo" /></template>
            </el-table-column>
            <el-table-column prop="name" label="名称" min-width="140" />
            <el-table-column label="筛选条件" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="mono">{{ row.filtersJson }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <StatusTag :value="row.status" :dict="setStatusDict" />
              </template>
            </el-table-column>
            <el-table-column label="标注进度" width="120" align="center">
              <template #default="{ row }">{{ row.labeledCount ?? 0 }} / {{ row.queryCount }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openLabeling(row)">标注</el-button>
                <el-button
                  v-if="row.status === 'labeling'"
                  link
                  type="warning"
                  @click="completeSet(row)"
                >
                  完成标注
                </el-button>
                <el-button
                  v-if="row.status === 'done'"
                  link
                  type="success"
                  @click="openRun(row)"
                >
                  发起评测
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 评测记录 -->
        <el-tab-pane label="评测记录" name="runs">
          <div class="toolbar">
            <el-button type="primary" @click="loadRuns">刷新</el-button>
          </div>
          <el-table :data="runs" v-loading="runsLoading" border stripe>
            <el-table-column label="编号" width="180">
              <template #default="{ row }"><BizNoTag :value="row.bizNo" /></template>
            </el-table-column>
            <el-table-column label="评测集" width="180">
              <template #default="{ row }">
                <BizNoTag :value="sets.find((s) => s.id === row.evalSetId)?.bizNo" />
              </template>
            </el-table-column>
            <el-table-column label="任务" width="170">
              <template #default="{ row }"><BizNoTag :value="row.taskBizNo" /></template>
            </el-table-column>
            <el-table-column label="结论" width="110">
              <template #default="{ row }">
                <el-tag v-if="row.conclusion === 'pass'" type="success" size="small">达标</el-tag>
                <el-tag v-else-if="row.conclusion" type="danger" size="small">未达标</el-tag>
                <span v-else class="text-secondary">进行中</span>
              </template>
            </el-table-column>
            <el-table-column label="指标" min-width="200">
              <template #default="{ row }">
                <span class="mono">{{ metricsText(row.metricsJson) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRecord(row)">报告</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 创建评测集 -->
    <el-dialog v-model="setCreateVisible" title="从检索历史筛选创建评测集" width="540px">
      <el-form label-width="120px">
        <el-form-item label="名称">
          <el-input v-model="setForm.name" placeholder="如：11月零结果专项" />
        </el-form-item>
        <el-form-item label="筛选条件">
          <el-checkbox-group v-model="setForm.filters">
            <el-checkbox value="zeroResult">零结果检索</el-checkbox>
            <el-checkbox value="noClick">有结果无点击</el-checkbox>
            <el-checkbox value="reSearch">复搜（同查询多次）</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="setCreateVisible = false">取消</el-button>
        <el-button type="primary" :loading="setCreating" @click="submitSet">创建（进入标注）</el-button>
      </template>
    </el-dialog>

    <!-- 标注抽屉 -->
    <el-drawer v-model="labelVisible" size="56%" :title="`标注：${labelSet?.name ?? ''}`">
      <template v-if="labelItems.length">
        <div class="text-secondary" style="margin-bottom: 8px">
          标注该查询的正确答案条目（itemId，可多选），全部标注后才能完成。
          第 {{ labelIndex + 1 }} / {{ labelItems.length }} 条
        </div>
        <el-card v-for="(item, idx) in labelItems" :key="item.id" shadow="never" class="label-card">
          <div class="toolbar" style="margin-bottom: 8px">
            <el-tag size="small">#{{ idx + 1 }}</el-tag>
            <b>{{ item.query }}</b>
            <el-tag v-if="item.labeled" size="small" type="success">已标注</el-tag>
            <div class="grow" />
          </div>
          <el-input
            :model-value="answersOf(item)"
            placeholder="正确答案 itemId，逗号分隔，如：item_001,item_017（无正确答案留空）"
            class="mono"
            @change="(v: string) => labelItem(item, v)"
          >
            <template #append>
              <el-button @click="labelItem(item, answersOf(item))">保存</el-button>
            </template>
          </el-input>
        </el-card>
      </template>
      <el-empty v-else description="无待标注条目" />
    </el-drawer>

    <!-- 发起评测 -->
    <el-dialog v-model="runVisible" title="发起评测（409，评测对象=策略）" width="500px">
      <el-form label-width="120px">
        <el-form-item label="评测集">
          <el-input :model-value="runSet?.name" disabled />
        </el-form-item>
        <el-form-item label="目标策略版本">
          <el-select
            v-model="runVersionId"
            placeholder="默认：当前生效策略版本"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="v in strategyVersionList"
              :key="v.id"
              :label="`策略${v.strategyId} v${v.versionNo}${v.status === 'published' ? '' : '（草稿）'}`"
              :value="v.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="runVisible = false">取消</el-button>
        <el-button type="primary" :loading="runSubmitting" @click="submitRun">执行评测</el-button>
      </template>
    </el-dialog>

    <!-- 报告弹窗 -->
    <el-dialog v-model="recordVisible" title="评测报告" width="680px">
      <template v-if="recordData">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="结论">
            <el-tag v-if="recordData.conclusion === 'pass'" type="success">达标</el-tag>
            <el-tag v-else-if="recordData.conclusion" type="danger">未达标</el-tag>
            <span v-else>进行中</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ fmtTime(recordData.createTime) }}
          </el-descriptions-item>
        </el-descriptions>
        <h4>指标 vs 线值</h4>
        <pre class="mono report">{{ pretty(recordData.metricsJson, recordData.linesJson) }}</pre>
        <h4>未达标项</h4>
        <pre class="mono report">{{ recordData.failedLinesJson || '（无）' }}</pre>
        <h4 v-if="recordData.comparisonJson">与上次对比</h4>
        <pre v-if="recordData.comparisonJson" class="mono report">{{ recordData.comparisonJson }}</pre>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  completeEvalSet,
  createEvalSet,
  evalSetDetail,
  evalRunDetail,
  labelEvalItem,
  pageEvalRuns,
  pageEvalSets,
  pageStrategies,
  runEval,
  strategyVersions,
} from '../../api'
import type {
  DictItem,
  EvalItemVO,
  EvalRecordVO,
  EvalSetVO,
  StrategyVO,
  StrategyVersionVO,
} from '../../api/types'
import BizNoTag from '../../components/BizNoTag.vue'
import StatusTag from '../../components/StatusTag.vue'
import { useDictStore } from '../../stores/dict'
import { fmtTime, parseJson } from '../../utils/format'

const dictStore = useDictStore()

const tab = ref('sets')
const sets = ref<EvalSetVO[]>([])
const runs = ref<EvalRecordVO[]>([])
const setsLoading = ref(false)
const runsLoading = ref(false)
const setStatusFilter = ref('')
const setStatusDict = ref<DictItem[]>([])

const metricsText = (raw?: string | null) => {
  const m = parseJson<Record<string, number>>(raw, {})
  const parts = Object.entries(m).map(([k, v]) => `${k}=${Number(v).toFixed(3)}`)
  return parts.length ? parts.join('  ') : '-'
}

const pretty = (metrics?: string | null, lines?: string | null) => {
  const m = parseJson<Record<string, number>>(metrics, {})
  const l = parseJson<Record<string, number>>(lines, {})
  const out: string[] = []
  for (const [k, v] of Object.entries(m)) {
    out.push(`${k.padEnd(14)} 实测 ${Number(v).toFixed(4)}   线值 ${l[k] ?? '-'}`)
  }
  return out.join('\n') || '（暂无指标，任务进行中）'
}

async function loadSets() {
  setsLoading.value = true
  try {
    const page = await pageEvalSets({ status: setStatusFilter.value })
    sets.value = page.list
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    setsLoading.value = false
  }
}

async function loadRuns() {
  runsLoading.value = true
  try {
    const page = await pageEvalRuns()
    runs.value = page.list
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    runsLoading.value = false
  }
}

// ---- 创建评测集 ----
const setCreateVisible = ref(false)
const setCreating = ref(false)
const setForm = reactive({ name: '', filters: ['zeroResult'] as string[] })

async function submitSet() {
  if (setForm.filters.length === 0) {
    ElMessage.warning('至少勾选一个筛选条件')
    return
  }
  setCreating.value = true
  try {
    // 后端 filters 为 Map<String, Boolean>：勾选即 true，未勾选即 false
    const filters: Record<string, boolean> = {
      zeroResult: setForm.filters.includes('zeroResult'),
      noClick: setForm.filters.includes('noClick'),
      reSearch: setForm.filters.includes('reSearch'),
    }
    await createEvalSet({ name: setForm.name || undefined, filters })
    ElMessage.success('评测集已创建，请进入标注')
    setCreateVisible.value = false
    await loadSets()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    setCreating.value = false
  }
}

// ---- 标注 ----
const labelVisible = ref(false)
const labelSet = ref<EvalSetVO | null>(null)
const labelItems = ref<EvalItemVO[]>([])
const labelIndex = ref(0)

const answersOf = (item: EvalItemVO) => parseJson<string[]>(item.answersJson, []).join(',')

async function openLabeling(row: EvalSetVO) {
  try {
    // 后端详情返回 { evalSet, items: PageResult }，标注一次拉一页（最多 200 条）
    const d = await evalSetDetail(row.id, { pageNum: 1, pageSize: 200 })
    labelSet.value = d.evalSet
    labelItems.value = d.items.list
    labelIndex.value = Math.max(
      0,
      d.items.list.findIndex((i) => !i.labeled),
    )
    labelVisible.value = true
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function labelItem(item: EvalItemVO, raw: string) {
  if (!labelSet.value) {
    return
  }
  const answers = raw
    .split(/[,，\s]+/)
    .map((s) => s.trim())
    .filter(Boolean)
  try {
    // 标注接口返回 Void：本地回填答案与已标注标记
    await labelEvalItem(labelSet.value.id, item.id, answers)
    const idx = labelItems.value.findIndex((i) => i.id === item.id)
    if (idx >= 0) {
      labelItems.value[idx] = {
        ...labelItems.value[idx],
        answersJson: JSON.stringify(answers),
        labeled: true,
      }
    }
    if (labelSet.value) {
      labelSet.value.labeledCount = labelItems.value.filter((i) => i.labeled).length
    }
    ElMessage.success('已保存')
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function completeSet(row: EvalSetVO) {
  try {
    await ElMessageBox.confirm('完成标注后评测集进入 done，可发起评测，继续？', '完成标注', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await completeEvalSet(row.id)
    ElMessage.success('标注完成')
    await loadSets()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

// ---- 发起评测 ----
const runVisible = ref(false)
const runSet = ref<EvalSetVO | null>(null)
const runSubmitting = ref(false)
const runVersionId = ref<number>()
const strategies = ref<StrategyVO[]>([])
const strategyVersionList = ref<StrategyVersionVO[]>([])

async function openRun(row: EvalSetVO) {
  runSet.value = row
  runVersionId.value = undefined
  try {
    const page = await pageStrategies()
    strategies.value = page.list
    const all: StrategyVersionVO[] = []
    for (const s of page.list) {
      try {
        all.push(...(await strategyVersions(s.id)))
      } catch {
        // 单策略失败不阻塞
      }
    }
    strategyVersionList.value = all
  } catch {
    strategyVersionList.value = []
  }
  runVisible.value = true
}

async function submitRun() {
  if (!runSet.value) {
    return
  }
  runSubmitting.value = true
  try {
    await runEval(runSet.value.id, runVersionId.value)
    ElMessage.success('评测任务已提交（409），可在评测记录页看报告')
    runVisible.value = false
    tab.value = 'runs'
    await loadRuns()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    runSubmitting.value = false
  }
}

// ---- 报告 ----
const recordVisible = ref(false)
const recordData = ref<EvalRecordVO | null>(null)

async function openRecord(row: EvalRecordVO) {
  try {
    recordData.value = await evalRunDetail(row.id)
    recordVisible.value = true
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(async () => {
  setStatusDict.value = await dictStore.ensure('cmr_eval_set_status')
  await loadSets()
  await loadRuns()
})
</script>

<style scoped>
.label-card {
  margin-bottom: 10px;
}
.report {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  max-height: 220px;
  overflow: auto;
  white-space: pre-wrap;
}
h4 {
  margin: 14px 0 6px;
}
</style>
