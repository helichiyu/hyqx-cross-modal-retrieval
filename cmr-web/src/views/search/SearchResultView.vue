<template>
  <div class="page-card">
    <!-- 结果集概要 -->
    <el-card class="summary">
      <div class="summary-row">
        <div>
          <div class="summary-title">
            检索结果
            <BizNoTag :value="history?.bizNo" />
          </div>
          <div class="text-secondary">
            查询：{{ history?.queryText || history?.queryFile || '-' }}
            · 模态 {{ modalityLabel(history?.queryModality) }}
            · 场景 {{ history?.scene || 'all' }}
            · {{ history?.itemCount ?? 0 }} 条
            · 耗时 {{ history?.elapsedMs ?? '-' }} ms
            · {{ fmtTime(history?.createTime) }}
            <el-tag v-if="history?.clarified" size="small" type="warning" style="margin-left: 6px">
              已澄清
            </el-tag>
          </div>
        </div>
        <div class="summary-actions">
          <StatusTag :value="history?.adoptStatus ?? 'unadopted'" :dict="adoptDict" />
          <el-button
            v-if="history?.adoptStatus === 'unadopted'"
            type="success"
            plain
            size="small"
            @click="markAdopt(true)"
          >
            标记采用
          </el-button>
          <el-button
            v-if="history?.adoptStatus === 'unadopted'"
            type="danger"
            plain
            size="small"
            @click="markAdopt(false)"
          >
            标记废弃
          </el-button>
          <el-button size="small" @click="feedbackVisible = true">结果反馈</el-button>
          <el-button size="small" @click="$router.push('/search')">再查一次</el-button>
        </div>
      </div>
    </el-card>

    <!-- 空结果处置 -->
    <el-alert
      v-if="loaded && items.length === 0"
      type="warning"
      :closable="false"
      class="empty-tip"
      title="本次检索无结果"
    >
      <div>可尝试：改写查询词 / 更换查询模态（文本↔图片/音频）/ 调整模态权重后重检。</div>
      <div style="margin-top: 6px">
        仍无结果可
        <el-link type="primary" :underline="false" @click="feedbackVisible = true">
          提交零结果反馈
        </el-link>
        ，供评测集筛选与数据补充。
      </div>
    </el-alert>

    <!-- 条目列表 -->
    <el-card>
      <div class="toolbar">
        <el-radio-group v-model="modalityFilter" size="small">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button v-for="m in MODALITIES" :key="m.key" :value="m.key">
            {{ m.label }}
          </el-radio-button>
        </el-radio-group>
        <div class="grow" />
        <span class="text-secondary">点击条目卡片即上报点击埋点（用于评测筛选）</span>
      </div>

      <div v-infinite-scroll="loadMore" :infinite-scroll-disabled="pageEnd" class="items">
        <el-card
          v-for="item in shownItems"
          :key="item.id"
          shadow="hover"
          class="item"
          :class="{ clicked: item.clicked }"
          @click="onItemClick(item)"
        >
          <div class="item-head">
            <el-tag size="small">#{{ item.rank }}</el-tag>
            <el-tag size="small" type="info" effect="plain">{{ modalityLabel(item.modality) }}</el-tag>
            <span class="mono score">score {{ Number(item.score).toFixed(4) }}</span>
            <el-tag v-if="item.clicked" size="small" type="success">已点击</el-tag>
          </div>
          <MediaPreview v-if="expandedId === item.id" :path="item.sourceFile" class="item-media" />
          <div v-if="item.text" class="item-text">{{ item.text }}</div>
          <div class="text-secondary mono">{{ item.sourceFile || item.itemId }}</div>
        </el-card>
      </div>
      <div v-if="items.length === 0 && loaded" class="text-secondary" style="padding: 24px; text-align: center">
        无匹配条目
      </div>
    </el-card>

    <!-- 反馈弹窗 -->
    <el-dialog v-model="feedbackVisible" title="结果反馈" width="520px">
      <el-form label-width="90px">
        <el-form-item label="反馈类型">
          <el-select v-model="feedbackForm.type" style="width: 100%">
            <el-option label="结果不准确" value="inaccurate" />
            <el-option label="排序不合理" value="ranking" />
            <el-option label="来源错误" value="wrong_source" />
            <el-option label="零结果" value="zero_result" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="feedbackForm.description"
            type="textarea"
            :rows="3"
            placeholder="补充说明（哪个条目、期望结果等）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackVisible = false">取消</el-button>
        <el-button type="primary" :loading="feedbackSubmitting" @click="submitFeedback">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  adoptResults,
  clickResult,
  createFeedback,
  searchResults,
} from '../../api'
import type { DictItem, HistoryDetailVO, ResultItemVO } from '../../api/types'
import BizNoTag from '../../components/BizNoTag.vue'
import MediaPreview from '../../components/MediaPreview.vue'
import StatusTag from '../../components/StatusTag.vue'
import { useAuthStore } from '../../stores/auth'
import { fmtTime, modalityLabel } from '../../utils/format'

const route = useRoute()
const auth = useAuthStore()

const MODALITIES = [
  { key: 'text', label: '文本' },
  { key: 'image', label: '图片' },
  { key: 'audio', label: '音频' },
  { key: 'video', label: '视频' },
]

const resultSetId = computed(() => route.params.resultSetId as string)
const detail = ref<HistoryDetailVO | null>(null)
const loaded = ref(false)
const modalityFilter = ref('')
const expandedId = ref<number | null>(null)
const pageSize = 10
const shownCount = ref(pageSize)

const history = computed(() => detail.value?.history ?? null)
const items = computed(() => detail.value?.items ?? [])
const adoptDict = ref<DictItem[]>([{ alias: 'adopted', label: '已采用' }, { alias: 'unadopted', label: '未采用' }, { alias: 'discarded', label: '已废弃' }])

const shownItems = computed(() => {
  const list = modalityFilter.value
    ? items.value.filter((i) => i.modality === modalityFilter.value)
    : items.value
  return list.slice(0, shownCount.value)
})
const pageEnd = computed(() => shownItems.value.length >= (modalityFilter.value ? items.value.filter((i) => i.modality === modalityFilter.value).length : items.value.length))

function loadMore() {
  shownCount.value += pageSize
}

async function load() {
  loaded.value = false
  try {
    detail.value = await searchResults(resultSetId.value)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loaded.value = true
  }
}

async function markAdopt(adopted: boolean) {
  try {
    await adoptResults(resultSetId.value, adopted)
    ElMessage.success(adopted ? '已标记采用（不可再变更）' : '已标记废弃（不可再变更）')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function onItemClick(item: ResultItemVO) {
  expandedId.value = expandedId.value === item.id ? null : item.id
  if (!item.clicked) {
    try {
      await clickResult(resultSetId.value, item.rank)
      item.clicked = true
    } catch {
      // 埋点失败不影响浏览
    }
  }
}

const feedbackVisible = ref(false)
const feedbackSubmitting = ref(false)
const feedbackForm = reactive({ type: 'inaccurate', description: '' })

async function submitFeedback() {
  if (!history.value) {
    return
  }
  feedbackSubmitting.value = true
  try {
    await createFeedback({
      searchId: history.value.id,
      type: feedbackForm.type,
      description: feedbackForm.description,
    })
    ElMessage.success('反馈已登记，可在结果运营页跟踪')
    feedbackVisible.value = false
    feedbackForm.description = ''
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    feedbackSubmitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.summary-title {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}
.summary-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.empty-tip {
  margin: 12px 0;
}
.items {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
  max-height: calc(100vh - 320px);
  overflow: auto;
}
.item {
  cursor: pointer;
}
.item.clicked {
  border-color: var(--el-color-success-light-5);
}
.item-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.score {
  color: #909399;
}
.item-text {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.item-media {
  margin-top: 8px;
}
</style>
