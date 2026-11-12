<template>
  <div class="page-card">
    <header class="cm-page-header">
      <div>
        <h1 class="cm-page-title">结果运营</h1>
        <p class="cm-page-subtitle">检索结果的反馈登记与处理。</p>
      </div>
    </header>

    <div v-if="showBack" class="back-row">
      <a class="back-link" href="#" @click.prevent="backToResult">← 返回结果页</a>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="cm-flex">
          <span>反馈</span>
          <span v-if="zeroTaskNo" class="fb-src">来自空结果处置 · <b>{{ zeroTaskNo }}</b></span>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" empty-text="暂无反馈单">
        <el-table-column label="时间" width="150">
          <template #default="{ row }">
            <span class="fb-time">{{ fmtTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="反馈单" width="150">
          <template #default="{ row }">
            <span class="mono">{{ row.bizNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="搜索词" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sourceQuery || '—' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ entryLabel(row.entryType) }}</template>
        </el-table-column>
        <el-table-column label="条数" width="80" align="center">
          <template #default="{ row }">
            <span class="fb-cnt">{{ row.items?.length ?? 0 }} 条</span>
          </template>
        </el-table-column>
        <el-table-column label="去向" width="140">
          <template #default="{ row }">{{ dispositionText(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <span class="badge" :class="row.status === 'handled' ? 'ok' : 'wait'">
              {{ row.status === 'handled' ? '已处理' : '待处理' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="96" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'handled'" size="small" disabled>已处理</el-button>
            <el-button v-else size="small" @click="openHandle(row)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="cm-pagination"
        layout="total, prev, pager, next"
        :total="total"
        :current-page="pageNum"
        :page-size="pageSize"
        @current-change="changePage"
      />
    </el-card>

    <!-- 屏蔽清单：处置为「屏蔽数据」的条目，检索结果不再下发 -->
    <el-card shadow="never">
      <template #header>
        <div class="cm-flex">
          <span>屏蔽清单</span>
          <span class="fb-src">共 {{ blockedRows.length }} 条 · 被屏蔽条目不再出现在检索结果中，移除后恢复</span>
        </div>
      </template>
      <el-table v-loading="blockedLoading" :data="blockedRows" empty-text="暂无屏蔽条目">
        <el-table-column label="条目 ID" min-width="150">
          <template #default="{ row }">
            <span class="mono">{{ row.itemId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.itemName || '—' }}</template>
        </el-table-column>
        <el-table-column label="来源" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.source || '—' }}</template>
        </el-table-column>
        <el-table-column label="原因" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason || '—' }}</template>
        </el-table-column>
        <el-table-column label="屏蔽时间" width="150">
          <template #default="{ row }">
            <span class="fb-time">{{ row.createTime ? fmtTime(row.createTime) : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" :loading="unblockingId === row.id" @click="removeBlocked(row)">移除屏蔽</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 登记反馈：唯一入口为结果页「空结果处置 · 需求登记」带 ?zero= 进来 -->
    <el-dialog v-model="registerVisible" title="登记反馈" width="720px">
      <div v-if="zeroTaskNo" class="mb-src">来自空结果处置 · <b>{{ zeroTaskNo }}</b></div>

      <div class="fb-step">搜索行为</div>
      <input
        v-model="pickKeyword"
        class="hist-search"
        type="text"
        placeholder="搜索 query"
        autocomplete="off"
        aria-label="搜索 query"
        @input="onPickKeywordInput"
      >
      <div class="pick-list" v-loading="pickLoading">
        <div v-if="!pickLoading && !pickRows.length" class="pick-empty">没有匹配的检索记录</div>
        <label
          v-for="row in pickRows"
          v-else
          :key="row.id"
          class="pick-row"
          :class="{ 'is-pick': picked?.id === row.id }"
        >
          <input
            class="pick-ck"
            type="radio"
            name="fbPick"
            :value="row.id"
            :checked="picked?.id === row.id"
            :aria-label="`选择 ${row.queryText || '文件检索'}`"
            @change="choosePick(row)"
          >
          <span class="pick-main">
            <span class="pick-q">{{ row.queryText || '（文件检索）' }}</span>
            <span class="pick-sub">
              {{ fmtTime(row.createTime) }} · 结果
              <span :class="{ zero: pickCount(row) === 0 }">{{ pickCount(row) }} 条</span>
              · {{ row.taskNo }}
            </span>
          </span>
        </label>
      </div>
      <div class="pick-pager">
        <button class="pager-btn" type="button" :disabled="pickPage <= 1" @click="gotoPickPage(pickPage - 1)">上一页</button>
        <template v-for="(p, idx) in pickPageList" :key="idx">
          <span v-if="p === '...'" class="pager-gap">…</span>
          <button
            v-else
            class="pager-num"
            :class="{ 'is-cur': p === pickPage }"
            type="button"
            @click="gotoPickPage(Number(p))"
          >{{ p }}</button>
        </template>
        <button class="pager-btn" type="button" :disabled="pickPage >= pickPages" @click="gotoPickPage(pickPage + 1)">下一页</button>
        <span class="pg-info">第 {{ pickPage }} / {{ pickPages }} 页</span>
        <span class="pg-info">共 {{ pickTotal }} 条</span>
      </div>

      <div v-if="picked" class="fb-form-wrap">
        <div class="pick-chosen">
          已选 <b>{{ picked.queryText || '（文件检索）' }}</b><br>
          <span>{{ picked.resultSetId }} · 任务 {{ picked.taskNo }} · {{ pickCount(picked) }} 条</span>
        </div>
        <div class="form-grid">
          <label>类型</label><span class="ro">零结果</span>
          <label>来源任务号</label><span class="ro mono">{{ zeroTaskNo || picked.taskNo || '—' }}</span>
          <label>搜索词</label><span class="ro">{{ picked.queryText || '—' }}</span>
          <label>说明</label>
          <input
            v-model="registerNote"
            class="note-input"
            type="text"
            maxlength="200"
            placeholder="如：检索无结果，疑似语料缺失"
          >
        </div>
      </div>

      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!picked" :loading="registerSubmitting" @click="submitRegister">提交登记</el-button>
      </template>
    </el-dialog>

    <!-- 处理反馈：类型只读，判断单选，去向只读随判断联动 -->
    <el-dialog v-model="handleVisible" :title="`处理反馈 · ${current?.bizNo ?? ''}`" width="720px">
      <div v-if="current" class="form-grid">
        <label>反馈单</label>
        <span class="ro">{{ current.bizNo }} · {{ current.sourceQuery || '—' }}</span>

        <label>来源</label>
        <span class="ro">
          {{ sourceLabel(current.entryType) }}<template v-if="current.sourceTaskNo"> · {{ current.sourceTaskNo }}</template>
        </span>

        <label>类型</label>
        <span class="ro">{{ entryLabel(current.entryType) }}</span>

        <template v-if="current.items?.length">
          <label>标记条目</label>
          <div class="ro item-list">
            <div v-for="item in current.items" :key="item.itemId" class="it-row">
              <span class="it-id">{{ item.itemId }}</span><template v-if="item.name"> · <span class="it-name">{{ item.name }}</span></template>
            </div>
          </div>
        </template>

        <template v-if="isZero">
          <label>处理判断</label>
          <span class="ro">库里缺这类数据 → 补充数据</span>
        </template>
        <template v-else>
          <label>处理判断</label>
          <div class="judge-list">
            <label class="judge-opt">
              <input v-model="judge" type="radio" value="data_bad">
              <span class="jo-txt"><b>数据本身有问题</b> → <span class="jo-to">屏蔽该数据（不再出现在检索结果）</span></span>
            </label>
            <label class="judge-opt">
              <input v-model="judge" type="radio" value="match_bad">
              <span class="jo-txt"><b>数据没问题，是匹配/排序问题</b> → <span class="jo-to">调整策略（数据保留）</span></span>
            </label>
          </div>
        </template>

        <label>去向</label>
        <span class="ro">{{ targetDispositionLabel }}</span>
      </div>

      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="handling" @click="submitHandle">标记已处理</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createFeedback,
  handleFeedback,
  listBlockedItems,
  pageBehaviors,
  pageFeedbacks,
  unblockItem,
} from '../../api'
import type { BehaviorVO, BlockedItemVO, FeedbackVO, Id } from '../../api/types'
import { fmtTime } from '../../utils/format'

const route = useRoute()
const router = useRouter()

type QueryValue = string | null | (string | null)[]

function queryOne(value: QueryValue | undefined): string {
  if (Array.isArray(value)) {
    return String(value[0] ?? '').trim()
  }
  return value == null ? '' : String(value).trim()
}

const zeroRaw = queryOne(route.query.zero)
const taskRaw = queryOne(route.query.task)
const zeroTaskNo = taskRaw || (/^TASK/i.test(zeroRaw) ? zeroRaw : '')
const showBack = queryOne(route.query.back).toUpperCase() === 'P2'
const enterRegister = !!zeroRaw

const DEFAULT_NOTE = '检索无结果，疑似语料缺失'
const PAGE_SIZE = 10
const PICK_PAGE_SIZE = 10

const HANDLE_TOAST: Record<string, string> = {
  block: '已处理 · 该数据已屏蔽，检索结果不再出现',
  strategy: '已处理 · 已记为匹配问题，供策略优化',
  supplement: '已处理 · 已记录数据需求',
}

const ENTRY_LABELS: Record<string, string> = {
  mismatch: '标记不匹配',
  zero: '零结果',
}

const SOURCE_LABELS: Record<string, string> = {
  mismatch: '结果摘要',
  zero: '空结果处置',
}

const DISPOSITION_LABELS: Record<string, string> = {
  block: '屏蔽数据',
  strategy: '调整策略',
  supplement: '补充数据',
}

const loading = ref(false)
const rows = ref<FeedbackVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = PAGE_SIZE

const registerVisible = ref(false)
const registerSubmitting = ref(false)
const registerNote = ref('')
const picked = ref<BehaviorVO | null>(null)
const pickRows = ref<BehaviorVO[]>([])
const pickTotal = ref(0)
const pickPage = ref(1)
const pickKeyword = ref('')
const pickLoading = ref(false)
let pickTimer: ReturnType<typeof setTimeout> | undefined

const handleVisible = ref(false)
const handling = ref(false)
const current = ref<FeedbackVO | null>(null)
const judge = ref('data_bad')

const blockedRows = ref<BlockedItemVO[]>([])
const blockedLoading = ref(false)
const unblockingId = ref<Id | null>(null)

const isZero = computed(() => current.value?.entryType === 'zero')
const targetDisposition = computed(() => {
  if (isZero.value) {
    return 'supplement'
  }
  return judge.value === 'match_bad' ? 'strategy' : 'block'
})
const targetDispositionLabel = computed(() => dispositionLabel(targetDisposition.value))

const pickPages = computed(() => Math.max(1, Math.ceil(pickTotal.value / PICK_PAGE_SIZE)))
const pickPageList = computed<Array<number | '...'>>(() => {
  const pages = pickPages.value
  const page = pickPage.value
  if (pages <= 7) {
    return Array.from({ length: pages }, (_, index) => index + 1)
  }
  const out: Array<number | '...'> = [1]
  const start = Math.max(2, page - 1)
  const end = Math.min(pages - 1, page + 1)
  if (start > 2) {
    out.push('...')
  }
  for (let i = start; i <= end; i += 1) {
    out.push(i)
  }
  if (end < pages - 1) {
    out.push('...')
  }
  out.push(pages)
  return out
})

function entryLabel(entryType: string | null | undefined): string {
  return (entryType && ENTRY_LABELS[entryType]) || '—'
}

function sourceLabel(entryType: string | null | undefined): string {
  return (entryType && SOURCE_LABELS[entryType]) || '—'
}

function dispositionLabel(disposition: string | null | undefined): string {
  return (disposition && DISPOSITION_LABELS[disposition]) || '—'
}

function dispositionText(row: FeedbackVO): string {
  if (row.status !== 'handled') {
    return '—'
  }
  const label = dispositionLabel(row.disposition)
  return row.disposition === 'block' && row.blocked ? `${label} · 已生效` : label
}

function pickCount(row: BehaviorVO): number {
  return row.zeroResult ? 0 : (row.itemCount ?? 0)
}

async function load() {
  loading.value = true
  try {
    const page = await pageFeedbacks({ pageNum: pageNum.value, pageSize })
    rows.value = page.records
    total.value = page.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

function changePage(page: number) {
  pageNum.value = page
  load()
}

async function loadBlocked() {
  blockedLoading.value = true
  try {
    blockedRows.value = await listBlockedItems()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    blockedLoading.value = false
  }
}

async function removeBlocked(row: BlockedItemVO) {
  try {
    await ElMessageBox.confirm(
      `移除屏蔽后，条目 ${row.itemId} 将恢复出现在检索结果中。`,
      '移除屏蔽',
      { confirmButtonText: '移除屏蔽', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  unblockingId.value = row.id
  try {
    await unblockItem(row.id)
    ElMessage.success(`已移除屏蔽 · ${row.itemId}`)
    await loadBlocked()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    unblockingId.value = null
  }
}

function backToResult() {
  if (window.history.state?.back) {
    router.back()
  } else {
    router.push({ name: 'search' })
  }
}

async function loadPick() {
  pickLoading.value = true
  try {
    const page = await pageBehaviors({
      pageNum: pickPage.value,
      pageSize: PICK_PAGE_SIZE,
      keyword: pickKeyword.value.trim() || undefined,
    })
    pickRows.value = page.records
    pickTotal.value = page.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    pickLoading.value = false
  }
}

function onPickKeywordInput() {
  if (pickTimer) {
    clearTimeout(pickTimer)
  }
  pickTimer = setTimeout(() => {
    pickPage.value = 1
    loadPick()
  }, 300)
}

function gotoPickPage(page: number) {
  pickPage.value = Math.min(Math.max(1, page), pickPages.value)
  loadPick()
}

function choosePick(row: BehaviorVO) {
  picked.value = row
  if (!registerNote.value.trim()) {
    registerNote.value = DEFAULT_NOTE
  }
}

async function preselectSource(taskNo: string) {
  try {
    const page = await pageBehaviors({ pageNum: 1, pageSize: 10, taskNo })
    // 结果页传来的是 TASK 号，历史记录对外是 taskNo、对内是 srhNo，两种都兼容
    const hit = page.records.find(
      (item) => item.taskNo === taskNo || item.srhNo === taskNo,
    )
    if (hit) {
      picked.value = hit
      if (!registerNote.value.trim()) {
        registerNote.value = DEFAULT_NOTE
      }
      if (hit.queryText) {
        pickKeyword.value = hit.queryText
        pickPage.value = 1
        await loadPick()
      }
    }
  } catch {
    // 检索记录由用户在列表里手动选择
  }
}

async function openRegister() {
  picked.value = null
  registerNote.value = zeroTaskNo ? DEFAULT_NOTE : ''
  pickKeyword.value = ''
  pickPage.value = 1
  registerVisible.value = true
  await loadPick()
  if (zeroTaskNo) {
    await preselectSource(zeroTaskNo)
  }
}

async function submitRegister() {
  if (!picked.value) {
    ElMessage.warning('请先选择一条搜索记录')
    return
  }
  const note = registerNote.value.trim()
  if (!note) {
    ElMessage.warning('请填写说明')
    return
  }
  registerSubmitting.value = true
  try {
    const created = await createFeedback({
      entryType: 'zero',
      sourceTaskNo: zeroTaskNo || picked.value.taskNo || undefined,
      sourceQuery: picked.value.queryText ?? undefined,
      description: note,
    })
    registerVisible.value = false
    ElMessage.success(`反馈 ${created.bizNo} 已登记 · 零结果`)
    pageNum.value = 1
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    registerSubmitting.value = false
  }
}

function openHandle(row: FeedbackVO) {
  if (row.status === 'handled') {
    return
  }
  current.value = row
  judge.value = row.judgment === 'match_bad' ? 'match_bad' : 'data_bad'
  handleVisible.value = true
}

async function submitHandle() {
  if (!current.value) {
    return
  }
  handling.value = true
  try {
    const judgment = isZero.value ? 'need_data' : judge.value
    const disposition = targetDisposition.value
    await handleFeedback(current.value.id, { judgment, disposition })
    ElMessage.success(HANDLE_TOAST[disposition] ?? '已处理')
    handleVisible.value = false
    await load()
    if (disposition === 'block') {
      await loadBlocked()
    }
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    handling.value = false
  }
}

onMounted(async () => {
  await load()
  await loadBlocked()
  if (enterRegister) {
    await openRegister()
  }
})

onBeforeUnmount(() => {
  if (pickTimer) {
    clearTimeout(pickTimer)
  }
})
</script>

<style scoped>
.back-row { display: flex; align-items: center; gap: 8px; }
.back-link {
  padding: 5px 10px;
  border: 1px solid #d7e0ff;
  border-radius: 6px;
  background: #fff;
  color: var(--system-blue);
  font-size: 12.5px;
  text-decoration: none;
  white-space: nowrap;
}
.back-link:hover { border-color: #aebdf5; background: var(--system-blue-weak); }

.fb-src { color: var(--muted-soft); font-size: 11.5px; font-weight: 400; white-space: nowrap; }
.fb-src b { color: #475467; font-weight: 600; }
.fb-time { color: var(--muted); font-size: 12px; font-variant-numeric: tabular-nums; white-space: nowrap; }
.fb-cnt { color: #475467; font-size: 12px; font-variant-numeric: tabular-nums; }

.mb-src {
  display: block;
  width: fit-content;
  max-width: 100%;
  margin-bottom: 7px;
  padding: 2px 8px;
  border: 1px solid #eef2f8;
  border-radius: 5px;
  background: #f8fafd;
  color: var(--muted-soft);
  font-size: 11.5px;
  line-height: 1.6;
}
.mb-src b { color: #475467; font-weight: 600; }

.fb-step {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 6px 0 7px;
  color: #475467;
  font-size: 12px;
  font-weight: 700;
}
.fb-step::before { content: ''; width: 3px; height: 12px; border-radius: 2px; background: var(--system-blue); }

.hist-search {
  width: 100%;
  height: 32px;
  padding: 0 10px;
  border: 1px solid #cfd8e8;
  border-radius: 6px;
  outline: none;
  color: #273244;
  font-size: 12.5px;
  background: #fff;
}
.hist-search:focus { border-color: #8fa3ff; box-shadow: 0 0 0 2px rgba(65, 105, 246, 0.12); }

.pick-list {
  display: flex;
  flex-direction: column;
  max-height: 332px;
  margin-top: 8px;
  overflow-y: auto;
  border: 1px solid #e6edf8;
  border-radius: 8px;
}
.pick-row {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  min-width: 0;
  padding: 9px 12px;
  border-bottom: 1px solid #eef2f8;
  background: #fff;
  cursor: pointer;
}
.pick-row:last-child { border-bottom: 0; }
.pick-row:hover { background: #f8fbff; }
.pick-row.is-pick { background: #f4f8ff; }
.pick-ck { flex: 0 0 auto; margin: 2px 0 0; accent-color: var(--system-blue); cursor: pointer; }
.pick-main { flex: 1 1 auto; min-width: 0; }
.pick-q {
  display: block;
  overflow: hidden;
  color: #273244;
  font-size: 12.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pick-sub { display: block; margin-top: 2px; overflow: hidden; color: #9aa6bd; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.pick-sub .zero { color: #c99a12; font-weight: 700; }
.pick-empty { padding: 22px 12px; background: #fff; color: #9aa6bd; font-size: 12px; text-align: center; }

.pick-pager { display: flex; align-items: center; justify-content: flex-end; gap: 6px; flex-wrap: wrap; margin-top: 10px; color: var(--muted); font-size: 12px; }
.pager-btn, .pager-num {
  height: 28px;
  border: 1px solid #d7e0f0;
  border-radius: 5px;
  background: #fff;
  color: #475467;
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
}
.pager-btn { padding: 0 10px; }
.pager-num { min-width: 28px; padding: 0 6px; font-variant-numeric: tabular-nums; }
.pager-btn:hover:not(:disabled), .pager-num:hover:not(.is-cur) { border-color: var(--system-blue); color: var(--system-blue); }
.pager-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.pager-num.is-cur { border-color: var(--system-blue); background: var(--system-blue); color: #fff; font-weight: 700; cursor: default; }
.pager-gap { padding: 0 2px; color: #98a2b3; }
.pg-info { color: var(--muted); font-variant-numeric: tabular-nums; }

.fb-form-wrap { margin-top: 14px; padding-top: 12px; border-top: 1px solid #f0f4fb; }
.pick-chosen {
  margin-bottom: 10px;
  padding: 7px 10px;
  border: 1px solid #eef2f8;
  border-radius: 6px;
  background: #f8fafd;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}
.pick-chosen b { color: #273244; }

.form-grid {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 9px 10px;
  align-items: center;
  margin: 6px 0 4px;
}
.form-grid label { color: var(--muted); font-size: 12px; }
.form-grid .ro {
  padding: 6px 10px;
  border: 1px solid #eef2f8;
  border-radius: 6px;
  background: #f8fafd;
  color: #475467;
  font-size: 12.5px;
}
.form-grid .note-input {
  width: 100%;
  height: 32px;
  padding: 0 10px;
  border: 1px solid #cfd8e8;
  border-radius: 6px;
  outline: none;
  color: #273244;
  font-size: 12.5px;
  background: #fff;
}
.form-grid .note-input:focus { border-color: #8fa3ff; box-shadow: 0 0 0 2px rgba(65, 105, 246, 0.12); }

.form-grid .ro.item-list { align-self: start; display: flex; flex-direction: column; gap: 3px; line-height: 1.55; }
.item-list .it-row { overflow-wrap: anywhere; color: #475467; font-size: 12px; }
.item-list .it-id { color: #1f2a44; font-family: var(--font-mono); font-weight: 600; white-space: nowrap; }

.form-grid .judge-list {
  align-self: start;
  display: flex;
  flex-direction: column;
  padding: 6px 10px;
  border: 1px solid #eef2f8;
  border-radius: 6px;
  background: #fbfdff;
}
.judge-opt { display: flex; align-items: flex-start; gap: 7px; padding: 3px 0; color: #344054; font-size: 12.5px; line-height: 1.55; cursor: pointer; }
.judge-opt input { flex: 0 0 auto; width: 14px; height: 14px; margin: 2px 0 0; accent-color: var(--system-blue); cursor: pointer; }
.judge-opt .jo-txt b { color: #1f2a44; font-weight: 600; }
.judge-opt .jo-to { color: var(--system-blue); font-weight: 600; white-space: nowrap; }

.dev-stack { display: flex; flex-direction: column; gap: 14px; }
.dev-lead { margin: 0; color: #475467; font-size: 12.5px; line-height: 1.75; }
.dev-lead b { color: #1f2a44; }
.dev-lead code { padding: 1px 5px; border-radius: 4px; background: #f1f5ff; color: var(--system-blue); font-size: 11.5px; }
.dev-sec h4 { margin: 0 0 6px; color: #1f2a44; font-size: 13px; }
.dev-sec ul { display: flex; flex-direction: column; gap: 4px; margin: 0; padding-left: 18px; }
.dev-sec li { color: #475467; font-size: 12px; line-height: 1.7; }
.dev-sec b { color: #1f2a44; }
.dev-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.dev-table th, .dev-table td { padding: 6px 9px; border: 1px solid #e6edf8; text-align: left; vertical-align: top; }
.dev-table th { background: #f7f9fd; color: var(--muted); font-weight: 600; }
</style>
