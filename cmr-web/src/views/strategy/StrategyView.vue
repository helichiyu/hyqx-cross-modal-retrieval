<template>
  <div class="page-card">
    <el-card>
      <div class="toolbar">
        <el-select v-model="sceneFilter" placeholder="场景" clearable style="width: 140px">
          <el-option v-for="d in scenes" :key="d.alias" :label="d.label" :value="d.alias" />
        </el-select>
        <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 130px">
          <el-option v-for="d in strategyDict" :key="d.alias" :label="d.label" :value="d.alias" />
        </el-select>
        <el-button type="primary" @click="reload">查询</el-button>
        <div class="grow" />
        <el-button type="primary" :icon="'Plus'" @click="openCreate">新建策略</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column label="策略编号" width="180">
          <template #default="{ row }"><BizNoTag :value="row.bizNo" /></template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column label="场景" width="110">
          <template #default="{ row }">{{ sceneLabel(row.scene) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag :value="row.status" :dict="strategyDict" />
          </template>
        </el-table-column>
        <el-table-column label="当前版本" width="90" align="center">
          <template #default="{ row }">v{{ row.currentVersion ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.updateTime || row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">配置</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建 -->
    <el-dialog v-model="createVisible" title="新建策略（初始为草稿）" width="520px">
      <el-form label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" />
        </el-form-item>
        <el-form-item label="场景">
          <el-select v-model="createForm.scene" style="width: 100%">
            <el-option v-for="d in scenes" :key="d.alias" :label="d.label" :value="d.alias" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建草稿</el-button>
      </template>
    </el-dialog>

    <!-- 配置抽屉：草稿编辑 + 版本历史 -->
    <el-drawer v-model="detailVisible" size="58%" :title="detail?.strategy.name ?? '策略配置'">
      <template v-if="detail">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="场景">
            {{ sceneLabel(detail.strategy.scene) }}
          </el-descriptions-item>
          <el-descriptions-item label="生效版本">
            v{{ detail.current?.versionNo ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="向量模型">
            {{ detail.modelVersion || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          type="info"
          :closable="false"
          style="margin: 12px 0"
          title="三段参数：向量模型版本只读；模态权重与重排可编辑。发布后下一次检索即生效。"
        />

        <el-card shadow="never" style="margin-bottom: 12px">
          <template #header>
            <div class="toolbar" style="margin-bottom: 0">
              <b>草稿参数</b>
              <el-tag v-if="!detail.draft" size="small" type="info">暂无草稿（编辑当前生效参数生成）</el-tag>
              <div class="grow" />
              <el-button
                v-if="detail.draft"
                size="small"
                @click="confirmDiscardDraft"
              >
                放弃草稿
              </el-button>
              <el-button
                v-if="detail.draft"
                type="primary"
                size="small"
                :loading="publishing"
                @click="publish"
              >
                发布（v{{ nextVersion }}）
              </el-button>
            </div>
          </template>

          <el-form label-width="90px">
            <el-form-item label="模态权重">
              <div class="weights">
                <div v-for="m in MODALITIES" :key="m.key" class="weight-row">
                  <span class="weight-label">{{ m.label }}</span>
                  <el-slider
                    v-model="draftForm.weights[m.key]"
                    :min="0"
                    :max="100"
                    :step="5"
                    style="flex: 1; margin: 0 12px"
                  />
                  <span class="weight-value">{{ draftForm.weights[m.key] }}</span>
                </div>
                <div class="text-secondary">
                  合计 {{ draftSum }}（须为 100）
                </div>
              </div>
            </el-form-item>
            <el-form-item label="重排">
              <el-switch v-model="draftForm.rerank" />
              <span class="text-secondary" style="margin-left: 8px">
                精排更准但增加约 3s 延迟
              </span>
            </el-form-item>
            <el-form-item label="返回条数">
              <el-input-number v-model="draftForm.topK" :min="1" :max="100" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="saveDraft">
                {{ detail.draft ? '保存草稿' : '从生效版本拷贝并编辑' }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never">
          <template #header>
            <div class="toolbar" style="margin-bottom: 0">
              <b>版本历史</b>
              <div class="grow" />
              <el-button size="small" @click="diffVisible = true">版本对比</el-button>
            </div>
          </template>
          <el-table :data="detail.versions" border size="small">
            <el-table-column label="版本" width="70">
              <template #default="{ row }">v{{ row.versionNo }}</template>
            </el-table-column>
            <el-table-column label="权重" min-width="180">
              <template #default="{ row }">
                <span class="mono">{{ weightsText(row.weightsJson) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="重排" width="70" align="center">
              <template #default="{ row }">{{ row.rerank ? '开' : '关' }}</template>
            </el-table-column>
            <el-table-column prop="topK" label="topK" width="70" align="center" />
            <el-table-column label="来源" width="90">
              <template #default="{ row }">{{ row.source || 'edit' }}</template>
            </el-table-column>
            <el-table-column label="发布时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.publishedTime || row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="130">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'published' && row.id !== detail.current?.id"
                  link
                  type="warning"
                  @click="rollback(row.versionNo)"
                >
                  回滚到此版
                </el-button>
                <el-tag v-if="row.id === detail.current?.id" size="small" type="success">生效中</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </template>
    </el-drawer>

    <!-- 版本对比 -->
    <el-dialog v-model="diffVisible" title="版本对比" width="640px">
      <el-form inline>
        <el-form-item label="对比">
          <el-select v-model="diffFrom" placeholder="起始版本" style="width: 140px">
            <el-option
              v-for="v in publishedVersions"
              :key="v.id"
              :label="`v${v.versionNo}`"
              :value="v.versionNo"
            />
          </el-select>
          <span style="margin: 0 8px">→</span>
          <el-select v-model="diffTo" placeholder="目标版本" style="width: 140px">
            <el-option
              v-for="v in publishedVersions"
              :key="v.id"
              :label="`v${v.versionNo}`"
              :value="v.versionNo"
            />
          </el-select>
          <el-button style="margin-left: 8px" @click="loadDiff">对比</el-button>
        </el-form-item>
      </el-form>
      <pre v-if="diffText" class="mono diff">{{ diffText }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createStrategy,
  discardDraft,
  pageStrategies,
  publishStrategy,
  rollbackStrategy,
  strategyDetail,
  strategyDiff,
  updateDraft,
} from '../../api'
import type { DictItem, StrategyDetailVO, StrategyVO } from '../../api/types'
import BizNoTag from '../../components/BizNoTag.vue'
import StatusTag from '../../components/StatusTag.vue'
import { useDictStore } from '../../stores/dict'
import { fmtTime, parseJson } from '../../utils/format'

const dictStore = useDictStore()

type ModalityKey = 'text' | 'image' | 'audio' | 'video'

const MODALITIES: { key: ModalityKey; label: string }[] = [
  { key: 'text', label: '文本' },
  { key: 'image', label: '图片' },
  { key: 'audio', label: '音频' },
  { key: 'video', label: '视频' },
]

const rows = ref<StrategyVO[]>([])
const sceneFilter = ref('')
const statusFilter = ref('')
const loading = ref(false)
const scenes = ref<DictItem[]>([])
const strategyDict = ref<DictItem[]>([])

/** 已发布版本（diff/回滚只针对生效过的版本号） */
const publishedVersions = computed(
  () => (detail.value?.versions ?? []).filter((v) => v.versionNo > 0),
)

const sceneLabel = (alias?: string | null) =>
  alias ? (scenes.value.find((d) => d.alias === alias)?.label ?? alias) : 'all'

const weightsText = (raw: string) => {
  const w = parseJson<Record<string, number>>(raw, {})
  return MODALITIES.map((m) => `${w[m.key] ?? 0}`).join(' / ')
}

async function reload() {
  loading.value = true
  try {
    const page = await pageStrategies({ scene: sceneFilter.value, status: statusFilter.value })
    rows.value = page.list
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

// ---- 新建 ----
const createVisible = ref(false)
const creating = ref(false)
const createForm = reactive({ name: '', scene: 'general', remark: '' })

function openCreate() {
  createForm.name = ''
  createForm.remark = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.name.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  creating.value = true
  try {
    await createStrategy({
      ...createForm,
      weights: { text: 25, image: 25, audio: 25, video: 25 },
      rerank: false,
      topK: 20,
    })
    ElMessage.success('草稿已创建')
    createVisible.value = false
    await reload()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    creating.value = false
  }
}

// ---- 详情/草稿 ----
const detailVisible = ref(false)
const detail = ref<StrategyDetailVO | null>(null)
const saving = ref(false)
const publishing = ref(false)
const draftForm = reactive<{
  weights: Record<ModalityKey, number>
  rerank: boolean
  topK: number
}>({
  weights: { text: 25, image: 25, audio: 25, video: 25 },
  rerank: false,
  topK: 20,
})

const draftSum = computed(
  () =>
    draftForm.weights.text +
    draftForm.weights.image +
    draftForm.weights.audio +
    draftForm.weights.video,
)

const nextVersion = computed(() => (detail.value?.current?.versionNo ?? 0) + 1)

async function openDetail(id: number) {
  try {
    detail.value = await strategyDetail(id)
    const base = detail.value.draft ?? detail.value.current
    if (base) {
      const w = parseJson<Record<string, number>>(base.weightsJson, {})
      for (const m of MODALITIES) {
        draftForm.weights[m.key] = w[m.key] ?? 0
      }
      draftForm.rerank = base.rerank
      draftForm.topK = base.topK
    }
    detailVisible.value = true
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function saveDraft() {
  if (!detail.value) {
    return
  }
  if (draftSum.value !== 100) {
    ElMessage.warning(`权重合计须为 100，当前 ${draftSum.value}`)
    return
  }
  saving.value = true
  try {
    await updateDraft(detail.value.strategy.id, {
      name: detail.value.strategy.name,
      scene: detail.value.strategy.scene,
      remark: detail.value.strategy.remark,
      weights: { ...draftForm.weights },
      rerank: draftForm.rerank,
      topK: draftForm.topK,
    })
    ElMessage.success('草稿已保存')
    await openDetail(detail.value.strategy.id)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

async function confirmDiscardDraft() {
  if (!detail.value) {
    return
  }
  try {
    await ElMessageBox.confirm('放弃未发布草稿，回到生效版本态？', '放弃草稿', { type: 'warning' })
  } catch {
    return
  }
  try {
    await discardDraft(detail.value.strategy.id)
    ElMessage.success('草稿已放弃')
    await openDetail(detail.value.strategy.id)
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function publish() {
  if (!detail.value) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `发布 v${nextVersion.value} 后立即生效，下一次检索即用新参数，继续？`,
      '发布',
      { type: 'warning' },
    )
  } catch {
    return
  }
  publishing.value = true
  try {
    await publishStrategy(detail.value.strategy.id)
    ElMessage.success(`v${nextVersion.value} 已发布并生效`)
    await openDetail(detail.value.strategy.id)
    await reload()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    publishing.value = false
  }
}

async function rollback(versionNo: number) {
  if (!detail.value) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `回滚到 v${versionNo}：以该版参数生成新版本并立即生效，继续？`,
      '回滚',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await rollbackStrategy(detail.value.strategy.id, versionNo)
    ElMessage.success('已回滚')
    await openDetail(detail.value.strategy.id)
    await reload()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

// ---- diff ----
const diffVisible = ref(false)
const diffFrom = ref<number>()
const diffTo = ref<number>()
const diffText = ref('')

async function loadDiff() {
  if (!detail.value || !diffFrom.value || !diffTo.value) {
    ElMessage.warning('请选择两个版本')
    return
  }
  try {
    const d = await strategyDiff(detail.value.strategy.id, diffFrom.value, diffTo.value)
    diffText.value = JSON.stringify(d, null, 2)
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(async () => {
  scenes.value = await dictStore.ensure('cmr_scene')
  strategyDict.value = await dictStore.ensure('cmr_strategy_status')
  await reload()
})
</script>

<style scoped>
.weights {
  width: 100%;
}
.weight-row {
  display: flex;
  align-items: center;
}
.weight-label {
  width: 40px;
}
.weight-value {
  width: 32px;
  text-align: right;
  font-family: Consolas, monospace;
}
.diff {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  max-height: 400px;
  overflow: auto;
}
</style>
