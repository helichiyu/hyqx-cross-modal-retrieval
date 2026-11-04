<template>
  <div class="page-card">
    <el-card>
      <div class="toolbar">
        <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 160px">
          <el-option v-for="d in statusDict" :key="d.alias" :label="d.label" :value="d.alias" />
        </el-select>
        <el-select v-model="sceneFilter" placeholder="场景" clearable style="width: 130px">
          <el-option v-for="d in scenes" :key="d.alias" :label="d.label" :value="d.alias" />
        </el-select>
        <el-input
          v-model="nameFilter"
          placeholder="名称搜索"
          clearable
          style="width: 220px"
          @keyup.enter="reload"
        />
        <el-button type="primary" :icon="'Search'" @click="reload">查询</el-button>
        <div class="grow" />
        <el-button :icon="'Connection'" @click="dsVisible = true">数据源登记</el-button>
        <el-button type="primary" :icon="'Plus'" @click="createVisible = true">新建数据集</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column label="数据集编号" width="180">
          <template #default="{ row }"><BizNoTag :value="row.bizNo" /></template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="场景" width="90">
          <template #default="{ row }">{{ sceneLabel(row.scene) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag :value="row.status" :dict="statusDict" />
          </template>
        </el-table-column>
        <el-table-column label="评分" width="80">
          <template #default="{ row }">{{ row.score ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="条目数" width="90" align="right">
          <template #default="{ row }">{{ row.itemCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="卡住原因" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="text-secondary">{{ row.stuckReason || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
            <el-button
              v-if="row.status === 'stuck' && row.decision === 'suspend'"
              link
              type="warning"
              @click="openAdjudicate(row)"
            >
              评定裁决
            </el-button>
            <el-button
              v-if="row.status === 'stuck' && row.decision !== 'suspend'"
              link
              type="warning"
              @click="doConfirm(row)"
            >
              确认投产
            </el-button>
            <el-button
              v-if="row.status === 'stuck' || row.status === 'searchable'"
              link
              type="danger"
              @click="doRebuild(row)"
            >
              重建
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next, sizes"
        style="margin-top: 12px; justify-content: flex-end"
        @change="reload"
      />
    </el-card>

    <!-- 新建数据集 -->
    <el-dialog v-model="createVisible" title="新建数据集（触发建库链 401→406）" width="560px">
      <el-form label-width="110px">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" placeholder="如：通用场景多媒体库 v1" />
        </el-form-item>
        <el-form-item label="业务场景">
          <el-select v-model="createForm.scene" style="width: 100%">
            <el-option v-for="d in scenes" :key="d.alias" :label="d.label" :value="d.alias" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据集压缩包">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            :on-change="(f: {raw?: File}) => (createForm.file = f.raw ?? null)"
            accept=".zip"
          >
            <el-button>
              选择 zip{{ createForm.file ? `（已选：${createForm.file.name}）` : '' }}
            </el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="或已有目录">
          <el-input
            v-model="createForm.datasetPath"
            placeholder="dataset/ds/xxxx 已解压目录（与压缩包二选一）"
            class="mono"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">提交并建库</el-button>
      </template>
    </el-dialog>

    <!-- 数据源登记 -->
    <el-dialog v-model="dsVisible" title="上游数据源登记" width="640px">
      <el-table :data="datasources" border size="small" style="margin-bottom: 12px">
        <el-table-column label="编号" width="170">
          <template #default="{ row }"><BizNoTag :value="row.bizNo" /></template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="endpoint" label="地址" min-width="180" show-overflow-tooltip />
        <el-table-column prop="authType" label="认证" width="90" />
      </el-table>
      <el-divider content-position="left">新增</el-divider>
      <el-form label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="sourceForm.name" />
        </el-form-item>
        <el-form-item label="地址" required>
          <el-input v-model="sourceForm.endpoint" placeholder="上游平台访问地址（http 或 https）" class="mono" />
        </el-form-item>
        <el-form-item label="认证方式">
          <el-select v-model="sourceForm.authType" style="width: 160px">
            <el-option label="无" value="none" />
            <el-option label="Token" value="token" />
            <el-option label="账号密码" value="basic" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="sourceForm.authType !== 'none'" label="认证信息">
          <el-input
            v-model="sourceForm.authConfig"
            type="textarea"
            :rows="2"
            placeholder='JSON，如 {"apiKey":"..."}（落库加密存储）'
            class="mono"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dsVisible = false">关闭</el-button>
        <el-button type="primary" :loading="dsSubmitting" @click="submitDatasource">登记</el-button>
      </template>
    </el-dialog>

    <!-- 评定裁决 -->
    <el-dialog v-model="adjVisible" title="评定挂起裁决（402 质检分低于线值）" width="480px">
      <div class="text-secondary" style="margin-bottom: 12px">
        {{ adjRow?.name }} 质检分 {{ adjRow?.score ?? '-' }}，
        卡住原因：{{ adjRow?.stuckReason || '-' }}
      </div>
      <el-input v-model="adjNote" type="textarea" :rows="2" placeholder="裁决备注（可选）" />
      <template #footer>
        <el-button @click="adjVisible = false">取消</el-button>
        <el-button type="danger" @click="doAdjudicate(false)">不通过（剔除）</el-button>
        <el-button type="success" @click="doAdjudicate(true)">通过（续跑建库）</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" size="62%" :title="detailData?.dataset.name ?? '数据集详情'">
      <template v-if="detailData">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="编号">
            <BizNoTag :value="detailData.dataset.bizNo" />
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :value="detailData.dataset.status" :dict="statusDict" />
          </el-descriptions-item>
          <el-descriptions-item label="场景">
            {{ sceneLabel(detailData.dataset.scene) }}
          </el-descriptions-item>
          <el-descriptions-item label="质检分">
            {{ detailData.dataset.score ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="条目数">
            {{ detailData.dataset.itemCount ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="模态构成">
            {{ modalitySummary(detailData.dataset.modalityJson) }}
          </el-descriptions-item>
          <el-descriptions-item label="卡住原因" :span="2">
            {{ detailData.dataset.stuckReason || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-tabs style="margin-top: 12px">
          <el-tab-pane label="索引">
            <el-table :data="detailData.indexes" border size="small">
              <el-table-column label="索引目录" min-width="240">
                <template #default="{ row }"><span class="mono">{{ row.indexDir }}</span></template>
              </el-table-column>
              <el-table-column prop="modelVersion" label="模型版本" width="130" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <StatusTag :value="row.status" :dict="indexDict" />
                </template>
              </el-table-column>
              <el-table-column label="激活时间" width="160">
                <template #default="{ row }">{{ fmtTime(row.activatedTime) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="90">
                <template #default="{ row }">
                  <el-button
                    v-if="row.status !== 'active' && row.status !== 'candidate'"
                    link
                    type="primary"
                    @click="doActivate(row.id)"
                  >
                    激活
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="建库任务">
            <el-table :data="detailData.tasks" border size="small">
              <el-table-column label="任务编号" width="180">
                <template #default="{ row }"><BizNoTag :value="row.bizNo" /></template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <StatusTag :value="row.status" :dict="taskDict" />
                </template>
              </el-table-column>
              <el-table-column prop="currentStep" label="当前步骤" width="130" />
              <el-table-column prop="errorMsg" label="错误" min-width="200" show-overflow-tooltip />
              <el-table-column label="更新时间" width="160">
                <template #default="{ row }">{{ fmtTime(row.finishedTime || row.createTime) }}</template>
              </el-table-column>
              <el-table-column label="" width="80">
                <template #default="{ row }">
                  <router-link :to="`/tasks/${row.id}`">
                    <el-button link type="primary">详情</el-button>
                  </router-link>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="接入报告">
            <pre class="mono report">{{ pretty(detailData.metaJson) }}</pre>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  activateIndex,
  adjudicateDataset,
  confirmProduction,
  createDataset,
  createDatasource,
  datasetDetail,
  listDatasources,
  pageDatasets,
  rebuildDataset,
} from '../../api'
import type { DatasetDetailVO, DatasetVO, DatasourceVO, DictItem } from '../../api/types'
import BizNoTag from '../../components/BizNoTag.vue'
import StatusTag from '../../components/StatusTag.vue'
import { useDictStore } from '../../stores/dict'
import { fmtTime, parseJson } from '../../utils/format'

const dictStore = useDictStore()

const rows = ref<DatasetVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const nameFilter = ref('')
const sceneFilter = ref('')
const statusFilter = ref('')
const loading = ref(false)

const statusDict = ref<DictItem[]>([])
const indexDict = ref<DictItem[]>([])
const taskDict = ref<DictItem[]>([])
const scenes = ref<DictItem[]>([])

const sceneLabel = (alias?: string | null) =>
  alias ? (scenes.value.find((d) => d.alias === alias)?.label ?? alias) : 'all'

const modalitySummary = (raw?: string | null) => {
  const m = parseJson<Record<string, number>>(raw, {})
  const parts = Object.entries(m).map(([k, v]) => `${k}:${v}`)
  return parts.length ? parts.join(' / ') : '-'
}

const pretty = (raw?: string | null) => {
  if (!raw) {
    return '（无）'
  }
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

async function reload() {
  loading.value = true
  try {
    const page = await pageDatasets({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      status: statusFilter.value,
      scene: sceneFilter.value,
      name: nameFilter.value,
    })
    rows.value = page.list
    total.value = page.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

// ---- 新建 ----
const createVisible = ref(false)
const creating = ref(false)
const createForm = reactive({
  name: '',
  scene: 'general',
  file: null as File | null,
  datasetPath: '',
  remark: '',
})

async function submitCreate() {
  if (!createForm.name.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  if (!createForm.file && !createForm.datasetPath.trim()) {
    ElMessage.warning('压缩包与已有目录必须二选一')
    return
  }
  creating.value = true
  try {
    const form = new FormData()
    form.append('name', createForm.name.trim())
    form.append('scene', createForm.scene)
    if (createForm.remark) {
      form.append('remark', createForm.remark)
    }
    if (createForm.file) {
      form.append('file', createForm.file)
    } else {
      form.append('datasetPath', createForm.datasetPath.trim())
    }
    await createDataset(form)
    ElMessage.success('已提交，建库链任务已启动（401→406）')
    createVisible.value = false
    createForm.name = ''
    createForm.file = null
    createForm.datasetPath = ''
    await reload()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    creating.value = false
  }
}

// ---- 数据源 ----
const dsVisible = ref(false)
const dsSubmitting = ref(false)
const datasources = ref<DatasourceVO[]>([])
const sourceForm = reactive({ name: '', endpoint: '', authType: 'none', authConfig: '' })

async function loadDatasources() {
  try {
    datasources.value = await listDatasources()
  } catch {
    // 列表失败不影响主流程
  }
}

async function submitDatasource() {
  if (!sourceForm.name.trim() || !sourceForm.endpoint.trim()) {
    ElMessage.warning('请填写名称与地址')
    return
  }
  dsSubmitting.value = true
  try {
    // 认证信息按后端约定以字符串提交（后端 AES 加密落库）
    const authConfig = sourceForm.authType === 'none' ? '' : sourceForm.authConfig.trim()
    await createDatasource({
      name: sourceForm.name.trim(),
      endpoint: sourceForm.endpoint.trim(),
      authType: sourceForm.authType,
      authConfig,
    })
    ElMessage.success('数据源已登记')
    sourceForm.name = ''
    sourceForm.endpoint = ''
    sourceForm.authConfig = ''
    await loadDatasources()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    dsSubmitting.value = false
  }
}

// ---- 裁决 / 投产 / 重建 ----
const adjVisible = ref(false)
const adjRow = ref<DatasetVO | null>(null)
const adjNote = ref('')

function openAdjudicate(row: DatasetVO) {
  adjRow.value = row
  adjNote.value = ''
  adjVisible.value = true
}

async function doAdjudicate(pass: boolean) {
  if (!adjRow.value) {
    return
  }
  try {
    await adjudicateDataset(adjRow.value.id, pass, adjNote.value || undefined)
    ElMessage.success(pass ? '已通过，建库链续跑' : '已剔除（终态）')
    adjVisible.value = false
    await reload()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function doConfirm(row: DatasetVO) {
  try {
    await ElMessageBox.confirm(
      '召回率不足线值但人工确认可投产，将激活该候选索引，继续？',
      '确认投产',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await confirmProduction(row.id)
    ElMessage.success('候选索引已激活，数据集进入可检索')
    await reload()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function doRebuild(row: DatasetVO) {
  try {
    await ElMessageBox.confirm('全链重跑（401→406），生成新索引，继续？', '重建', { type: 'warning' })
  } catch {
    return
  }
  try {
    const task = await rebuildDataset(row.id)
    ElMessage.success(`重建任务 ${task.bizNo} 已启动`)
    await reload()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function doActivate(indexId: number) {
  try {
    await activateIndex(indexId)
    ElMessage.success('索引已重新激活')
    if (detailData.value) {
      await openDetail(detailData.value.dataset.id)
    }
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

// ---- 详情 ----
const detailVisible = ref(false)
const detailData = ref<DatasetDetailVO | null>(null)

async function openDetail(id: number) {
  try {
    detailData.value = await datasetDetail(id)
    detailVisible.value = true
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(async () => {
  statusDict.value = await dictStore.ensure('cmr_dataset_status')
  indexDict.value = await dictStore.ensure('cmr_index_status')
  taskDict.value = await dictStore.ensure('cmr_task_status')
  scenes.value = await dictStore.ensure('cmr_scene')
  await reload()
  await loadDatasources()
})
</script>

<style scoped>
.report {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  max-height: 420px;
  overflow: auto;
}
</style>
