<template>
  <div class="page-card">
    <header class="cm-page-header">
      <div>
        <h1 class="cm-page-title">搜索结果摘要</h1>
        <p class="cm-page-subtitle">
          任务 {{ taskNo }} · Top-20 条目与赞踩；摘要保留 30 天，完整结果页从「查看完整结果」进入。
        </p>
      </div>
      <div class="cm-flex">
        <el-button @click="router.back()">返回</el-button>
        <el-button v-if="summary" @click="openFullResult">查看完整结果</el-button>
      </div>
    </header>

    <el-card v-if="summary" shadow="never">
      <template #header>
        <div class="cm-flex-between">
          <span>{{ summary.queryText || '（文件检索）' }}</span>
          <div class="cm-flex">
            <el-tag v-if="summary.expired" type="info" effect="plain" size="small">摘要已过期</el-tag>
            <el-tag v-if="summary.sealed" type="warning" effect="plain" size="small">已封存</el-tag>
            <span class="text-secondary">
              赞 {{ summary.upCount }} · 踩 {{ summary.downCount }} · {{ summary.itemCount ?? 0 }} 条结果
            </span>
          </div>
        </div>
      </template>
      <div class="meta-grid">
        <div><span class="meta-k">操作人</span><span>{{ summary.operatorName || '—' }}</span></div>
        <div><span class="meta-k">策略</span><span class="mono">{{ summary.strategyNo || '默认' }}</span>
          <span v-if="summary.strategyVersion" class="text-secondary"> v{{ summary.strategyVersion }}</span>
        </div>
        <div><span class="meta-k">阈值</span><span>{{ summary.threshold ?? '—' }}</span></div>
        <div><span class="meta-k">检索时间</span><span>{{ summary.createTime }}</span></div>
        <div><span class="meta-k">摘要到期</span><span>{{ summary.expireTime }}</span></div>
      </div>

      <div class="toolbar">
        <el-checkbox v-model="selectMode" label="选择条目标记不匹配" />
        <span class="cm-grow" />
        <el-button
          v-if="selectMode"
          type="warning"
          :disabled="selected.length === 0"
          @click="openMismatchDialog"
        >
          标记不匹配（{{ selected.length }}）
        </el-button>
      </div>

      <el-table v-loading="loading" :data="summary.items" size="small" row-key="itemId" empty-text="该任务无结果">
        <el-table-column v-if="selectMode" width="48" type="selection" @selection-change="onSelectionChange" />
        <el-table-column prop="rank" label="#" width="56" />
        <el-table-column label="条目" min-width="220">
          <template #default="{ row }">
            <div class="item-name">{{ row.name || '已删除' }}</div>
            <div class="text-secondary">{{ row.itemId }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="modality" label="模态" width="80" />
        <el-table-column label="来源" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.source || row.sourceFile || '—' }}</template>
        </el-table-column>
        <el-table-column label="分数" width="90">
          <template #default="{ row }">{{ row.score?.toFixed?.(3) ?? row.score }}</template>
        </el-table-column>
        <el-table-column label="赞/踩" width="130">
          <template #default="{ row }">
            <el-button
              link
              :type="row.vote === 'up' ? 'success' : 'default'"
              :disabled="summary.sealed"
              @click="vote(row, 'up')"
            >▲</el-button>
            <el-button
              link
              :type="row.vote === 'down' ? 'danger' : 'default'"
              :disabled="summary.sealed"
              @click="vote(row, 'down')"
            >▼</el-button>
          </template>
        </el-table-column>
        <el-table-column label="收藏" width="80">
          <template #default="{ row }">
            <el-button link :type="row.favorited ? 'warning' : 'default'" @click="favorite(row)">
              {{ row.favorited ? '★' : '☆' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="mismatchVisible" title="标记不匹配" width="480px">
      <p class="text-secondary">已选 {{ selected.length }} 条，提交后进入结果运营待处置。</p>
      <el-input v-model="mismatchNote" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="补充说明（可选）" />
      <template #footer>
        <el-button @click="mismatchVisible = false">取消</el-button>
        <el-button type="primary" :loading="mismatchSubmitting" @click="submitMismatch">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  createFeedback,
  searchSummary,
  toggleFavorite,
  voteItem,
} from '../../api'
import type { MismatchItem, ResultItemVO, SearchSummaryVO } from '../../api/types'

const route = useRoute()
const router = useRouter()
const taskNo = String(route.params.taskNo)

const loading = ref(false)
const summary = ref<SearchSummaryVO | null>(null)
const selectMode = ref(false)
const selected = ref<MismatchItem[]>([])
const mismatchVisible = ref(false)
const mismatchSubmitting = ref(false)
const mismatchNote = ref('')

async function load() {
  loading.value = true
  try {
    summary.value = await searchSummary(taskNo)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function openFullResult() {
  if (summary.value?.resultSetId) {
    router.push({ name: 'search-result', params: { resultSetId: summary.value.resultSetId } })
  }
}

async function vote(row: ResultItemVO, vote: 'up' | 'down') {
  const next = row.vote === vote ? 'none' : vote
  const result = await voteItem(taskNo, row.itemId, next)
  row.vote = result.vote as 'up' | 'down' | null
  if (summary.value) {
    summary.value.upCount = result.upCount
    summary.value.downCount = result.downCount
  }
}

async function favorite(row: ResultItemVO) {
  const favorited = await toggleFavorite({
    itemId: row.itemId,
    itemName: row.name,
    source: row.source,
    taskNo,
  })
  row.favorited = favorited
  ElMessage.success(favorited ? '已收藏' : '已取消收藏')
}

function onSelectionChange(rows: ResultItemVO[]) {
  selected.value = rows.map((row) => ({ itemId: row.itemId, name: row.name, source: row.source }))
}

function openMismatchDialog() {
  mismatchNote.value = ''
  mismatchVisible.value = true
}

async function submitMismatch() {
  mismatchSubmitting.value = true
  try {
    await createFeedback({
      entryType: 'mismatch',
      sourceTaskNo: taskNo,
      sourceQuery: summary.value?.queryText ?? undefined,
      items: selected.value,
      description: mismatchNote.value,
    })
    ElMessage.success('已提交，可在结果运营处置')
    mismatchVisible.value = false
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    mismatchSubmitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px 20px;
  margin-bottom: 14px;
  font-size: 13px;
}
.meta-k { color: var(--muted); margin-right: 8px; }
.item-name { font-weight: 600; color: var(--text-primary); }
.toolbar { display: flex; align-items: center; gap: 8px; margin: 6px 0 12px; }
</style>
