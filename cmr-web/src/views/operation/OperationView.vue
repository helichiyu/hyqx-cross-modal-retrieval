<template>
  <div class="page-card">
    <el-card>
      <el-tabs v-model="tab">
        <!-- 反馈单 -->
        <el-tab-pane label="结果反馈" name="feedback">
          <div class="toolbar">
            <el-select v-model="fbTypeFilter" placeholder="类型" clearable style="width: 150px">
              <el-option v-for="d in fbTypeDict" :key="d.alias" :label="d.label" :value="d.alias" />
            </el-select>
            <el-select v-model="fbStatusFilter" placeholder="状态" clearable style="width: 130px">
              <el-option v-for="d in fbStatusDict" :key="d.alias" :label="d.label" :value="d.alias" />
            </el-select>
            <el-button type="primary" @click="loadFeedbacks">查询</el-button>
          </div>
          <el-table :data="feedbacks" v-loading="fbLoading" border stripe>
            <el-table-column label="反馈编号" width="180">
              <template #default="{ row }"><BizNoTag :value="row.bizNo" /></template>
            </el-table-column>
            <el-table-column label="关联检索" width="180">
              <template #default="{ row }"><BizNoTag :value="row.searchBizNo" /></template>
            </el-table-column>
            <el-table-column label="类型" width="110">
              <template #default="{ row }">{{ dictLabel(fbTypeDict, row.type) }}</template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <StatusTag :value="row.status" :dict="fbStatusDict" />
              </template>
            </el-table-column>
            <el-table-column label="去向" width="100">
              <template #default="{ row }">{{ dictLabel(fbTargetDict, row.target) }}</template>
            </el-table-column>
            <el-table-column label="登记时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'pending'"
                  link
                  type="primary"
                  @click="openProcess(row)"
                >
                  开始处理
                </el-button>
                <el-button
                  v-if="row.status !== 'closed'"
                  link
                  type="success"
                  @click="openClose(row)"
                >
                  闭环
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="fbPage"
            :page-size="10"
            :total="fbTotal"
            layout="total, prev, pager, next"
            style="margin-top: 12px; justify-content: flex-end"
            @change="loadFeedbacks"
          />
        </el-tab-pane>

        <!-- 推荐词 -->
        <el-tab-pane label="推荐词" name="words">
          <div class="toolbar">
            <el-input
              v-model="wordKeyword"
              placeholder="词搜索"
              clearable
              style="width: 200px"
              @keyup.enter="loadWords"
            />
            <el-button type="primary" @click="loadWords">查询</el-button>
            <div class="grow" />
            <el-button type="primary" :icon="'Plus'" @click="openWord()">新增推荐词</el-button>
          </div>
          <el-table :data="words" v-loading="wordLoading" border stripe>
            <el-table-column label="编号" width="180">
              <template #default="{ row }"><BizNoTag :value="row.bizNo" /></template>
            </el-table-column>
            <el-table-column prop="word" label="词" min-width="140" />
            <el-table-column label="场景" width="120">
              <template #default="{ row }">{{ sceneLabel(row.scene) }}</template>
            </el-table-column>
            <el-table-column label="启用" width="90" align="center">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.enabled"
                  @change="(v: string | number | boolean) => toggleWord(row, v)"
                />
              </template>
            </el-table-column>
            <el-table-column prop="sort" label="排序" width="80" align="center" />
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openWord(row)">编辑</el-button>
                <el-button link type="danger" @click="removeWordRow(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 开始处理 -->
    <el-dialog v-model="processVisible" title="开始处理反馈" width="460px">
      <el-form label-width="90px">
        <el-form-item label="处理去向">
          <el-select v-model="processTarget" style="width: 100%">
            <el-option label="策略调优" value="strategy" />
            <el-option label="数据源补充" value="datasource" />
            <el-option label="数据回填" value="backfill" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="processVisible = false">取消</el-button>
        <el-button type="primary" :loading="processing" @click="submitProcess">确认处理中</el-button>
      </template>
    </el-dialog>

    <!-- 闭环 -->
    <el-dialog v-model="closeVisible" title="反馈闭环" width="460px">
      <el-input
        v-model="closeNote"
        type="textarea"
        :rows="3"
        placeholder="处理结果说明（如：已调整安防场景权重 v3 / 已补充 xx 数据集）"
      />
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="primary" :loading="closing" @click="submitClose">闭环（终态）</el-button>
      </template>
    </el-dialog>

    <!-- 推荐词编辑 -->
    <el-dialog v-model="wordVisible" :title="wordForm.id ? '编辑推荐词' : '新增推荐词'" width="440px">
      <el-form label-width="80px">
        <el-form-item label="词" required>
          <el-input v-model="wordForm.word" />
        </el-form-item>
        <el-form-item label="场景">
          <el-select v-model="wordForm.scene" style="width: 100%">
            <el-option label="全部场景" value="all" />
            <el-option v-for="d in scenes" :key="d.alias" :label="d.label" :value="d.alias" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="wordForm.enabled" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="wordForm.sort" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="wordVisible = false">取消</el-button>
        <el-button type="primary" :loading="wordSubmitting" @click="submitWord">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  closeFeedback,
  createWord,
  pageFeedbacks,
  pageWords,
  processFeedback,
  removeWord,
  updateWord,
} from '../../api'
import type { DictItem, FeedbackVO, WordVO } from '../../api/types'
import BizNoTag from '../../components/BizNoTag.vue'
import StatusTag from '../../components/StatusTag.vue'
import { useDictStore } from '../../stores/dict'
import { fmtTime } from '../../utils/format'

const dictStore = useDictStore()

const tab = ref('feedback')

const dictLabel = (dict: DictItem[], alias?: string | null) =>
  dict.find((d) => d.alias === alias)?.label ?? (alias || '-')

// ---- 反馈 ----
const feedbacks = ref<FeedbackVO[]>([])
const fbLoading = ref(false)
const fbTotal = ref(0)
const fbPage = ref(1)
const fbTypeFilter = ref('')
const fbStatusFilter = ref('')
const fbTypeDict = ref<DictItem[]>([])
const fbStatusDict = ref<DictItem[]>([])
const fbTargetDict = ref<DictItem[]>([])

const processVisible = ref(false)
const processing = ref(false)
const processRow = ref<FeedbackVO | null>(null)
const processTarget = ref('strategy')

const closeVisible = ref(false)
const closing = ref(false)
const closeRow = ref<FeedbackVO | null>(null)
const closeNote = ref('')

async function loadFeedbacks() {
  fbLoading.value = true
  try {
    const page = await pageFeedbacks({
      pageNum: fbPage.value,
      type: fbTypeFilter.value,
      status: fbStatusFilter.value,
    })
    feedbacks.value = page.list
    fbTotal.value = page.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    fbLoading.value = false
  }
}

function openProcess(row: FeedbackVO) {
  processRow.value = row
  processTarget.value = 'strategy'
  processVisible.value = true
}

async function submitProcess() {
  if (!processRow.value) {
    return
  }
  processing.value = true
  try {
    await processFeedback(processRow.value.id, processTarget.value)
    ElMessage.success('已进入处理中')
    processVisible.value = false
    await loadFeedbacks()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    processing.value = false
  }
}

function openClose(row: FeedbackVO) {
  closeRow.value = row
  closeNote.value = ''
  closeVisible.value = true
}

async function submitClose() {
  if (!closeRow.value) {
    return
  }
  closing.value = true
  try {
    await closeFeedback(closeRow.value.id, closeNote.value || undefined)
    ElMessage.success('已闭环（终态）')
    closeVisible.value = false
    await loadFeedbacks()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    closing.value = false
  }
}

// ---- 推荐词 ----
const words = ref<WordVO[]>([])
const wordLoading = ref(false)
const wordKeyword = ref('')
const scenes = ref<DictItem[]>([])

const wordVisible = ref(false)
const wordSubmitting = ref(false)
const wordForm = reactive({
  id: 0,
  word: '',
  scene: 'all',
  enabled: true,
  sort: 0,
})

const sceneLabel = (alias?: string | null) =>
  alias === 'all' ? '全部场景' : (scenes.value.find((d) => d.alias === alias)?.label ?? alias ?? '-')

async function loadWords() {
  wordLoading.value = true
  try {
    const page = await pageWords({ keyword: wordKeyword.value })
    words.value = page.list
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    wordLoading.value = false
  }
}

function openWord(row?: WordVO) {
  wordForm.id = row?.id ?? 0
  wordForm.word = row?.word ?? ''
  wordForm.scene = row?.scene ?? 'all'
  wordForm.enabled = row?.enabled ?? true
  wordForm.sort = row?.sort ?? 0
  wordVisible.value = true
}

async function submitWord() {
  if (!wordForm.word.trim()) {
    ElMessage.warning('请填写推荐词')
    return
  }
  wordSubmitting.value = true
  try {
    const body = {
      word: wordForm.word.trim(),
      scene: wordForm.scene,
      enabled: wordForm.enabled,
      sort: wordForm.sort,
    }
    if (wordForm.id) {
      await updateWord(wordForm.id, body)
    } else {
      await createWord(body)
    }
    ElMessage.success('已保存（联想即时生效）')
    wordVisible.value = false
    await loadWords()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    wordSubmitting.value = false
  }
}

async function toggleWord(row: WordVO, v: string | number | boolean) {
  try {
    await updateWord(row.id, {
      word: row.word,
      scene: row.scene,
      enabled: Boolean(v),
      sort: row.sort,
    })
    row.enabled = Boolean(v)
    ElMessage.success(v ? '已启用' : '已停用')
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function removeWordRow(row: WordVO) {
  try {
    await ElMessageBox.confirm(`删除推荐词「${row.word}」？`, '删除', { type: 'warning' })
  } catch {
    return
  }
  try {
    await removeWord(row.id)
    ElMessage.success('已删除')
    await loadWords()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(async () => {
  fbTypeDict.value = await dictStore.ensure('cmr_feedback_type')
  fbStatusDict.value = await dictStore.ensure('cmr_feedback_status')
  fbTargetDict.value = await dictStore.ensure('cmr_feedback_target')
  scenes.value = await dictStore.ensure('cmr_scene')
  await loadFeedbacks()
  await loadWords()
})
</script>
