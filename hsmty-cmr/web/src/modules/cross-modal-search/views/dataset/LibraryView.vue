<template>
  <div class="page-card">
    <header class="cm-page-header">
      <div class="ph-main">
        <h1 class="cm-page-title">数据源库管理</h1>
      </div>
    </header>

    <section class="cm-panel lib-panel" aria-label="数据源">
      <div class="panel-head">
        <div class="sec-head">
          <span class="sec-idx" aria-hidden="true">1</span>
          <span class="sec-title">数据源</span>
        </div>
        <div class="ph-tools">
          <el-button class="ghost-btn" @click="openContract">接口对接定义</el-button>
          <el-button class="ghost-btn" @click="openBlacklist">黑名单 ({{ blacklist.length }})</el-button>
          <el-button type="primary" class="primary-btn" @click="openCreate">＋ 对接数据源</el-button>
          <span class="hint">共 {{ sources.length }} 条</span>
        </div>
      </div>

      <el-table v-loading="loading" :data="sources" empty-text="暂无数据源">
        <el-table-column label="名称" min-width="170">
          <template #default="{ row }">
            <span class="ds-nm">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="地址" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.endpoint }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <span class="badge" :class="statusClass(row.connectStatus)">{{ statusLabel(row.connectStatus) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="字段完备" width="92">
          <template #default="{ row }">
            <el-tooltip :content="fieldTip(row)" placement="top">
              <span class="badge" :class="fieldClass(row)">{{ fieldRatio(row) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="向量化" width="132">
          <template #default="{ row }">
            <el-tooltip :content="vecHint(row)" placement="top">
              <span class="vec-num" :class="vecClass(row)">{{ vecText(row) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="上次同步" width="152">
          <template #default="{ row }">
            <span class="cell-time">{{ row.lastSyncTime ? fmtTime(row.lastSyncTime) : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <div class="row-ops">
              <el-button class="op-btn" :disabled="testingId === row.id" @click="testConnection(row)">
                {{ testingId === row.id ? '测试中…' : '测试连接' }}
              </el-button>
              <el-button
                v-if="row.connectStatus === 'connected'"
                class="op-btn sync"
                :disabled="syncingId === row.id"
                @click="sync(row)"
              >
                {{ syncingId === row.id ? '同步中…' : '同步' }}
              </el-button>
              <el-button
                v-if="row.connectStatus === 'connected'"
                class="op-btn"
                :disabled="ingestingId === row.id"
                @click="openIngest(row)"
              >
                接入
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 接口对接定义：只读契约 -->
    <el-dialog v-model="contractOpen" title="接口对接定义" width="780px" class="contract-dialog">
      <div v-loading="contractLoading" class="contract-body">
        <p class="contract-lead">
          上游只提供原始文件与元数据，不提供向量。{{ contractPushMode }}
        </p>

        <div v-if="contractError" class="contract-error">{{ contractError }}</div>

        <template v-else>
          <div class="contract-sec">
            <div class="hb-t">数据集级字段</div>
            <table class="contract-table">
              <thead>
                <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
              </thead>
              <tbody>
                <tr v-for="field in datasetFieldRows" :key="field.field">
                  <td class="mk">{{ field.field }}</td>
                  <td>{{ field.type }}</td>
                  <td>{{ field.required }}</td>
                  <td>{{ field.desc }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="contract-sec">
            <div class="hb-t">条目级字段</div>
            <table class="contract-table">
              <thead>
                <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
              </thead>
              <tbody>
                <tr v-for="field in itemFieldRows" :key="field.field">
                  <td class="mk">{{ field.field }}</td>
                  <td>{{ field.type }}</td>
                  <td>{{ field.required }}</td>
                  <td>{{ field.desc }}</td>
                </tr>
              </tbody>
            </table>
            <p v-if="contractModalRule" class="field-tip">{{ contractModalRule }}</p>
          </div>

          <div class="contract-sec">
            <div class="hb-t">modals 每项结构</div>
            <div class="struct-box">
              <div class="struct-line">{ type: "image", url: "…jpg" }</div>
              <div class="struct-line">{ type: "text", content: "…" } 或 { type: "text", url: "…" }</div>
              <div class="struct-line">{ type: "audio", url: "…wav" }</div>
              <div class="struct-line">{ type: "video", url: "…mp4" }</div>
            </div>
          </div>

          <div class="contract-sec">
            <div class="hb-t">接口</div>
            <div class="api-list">
              <div v-for="api in contractApiRows" :key="api.raw" class="api-row">
                <span class="api-m">{{ api.method }}</span>
                <span class="api-p">{{ api.path }}</span>
                <span class="api-d">{{ api.desc }}</span>
              </div>
            </div>
          </div>

          <div class="contract-sec">
            <div class="hb-t">示例请求</div>
            <div class="struct-box">
              <div class="struct-line">GET {base}/datasets</div>
              <div class="struct-line">Accept: application/json</div>
            </div>
          </div>

          <div class="contract-sec">
            <div class="hb-t">示例响应</div>
            <div class="struct-box">
              <div class="struct-line">[ { "dataset_id": "ds-001", "name": "示例数据集",</div>
              <div class="struct-line">    "item_count": 1200, "modal_types": ["image", "text"] } ]</div>
              <div class="struct-line">{ "item_id": "it-0001", "modals": [</div>
              <div class="struct-line">    { "type": "image", "url": "https://…/1.jpg" },</div>
              <div class="struct-line">    { "type": "text", "content": "…" } ] }</div>
            </div>
          </div>

          <div class="contract-sec">
            <div class="hb-t">关键约束</div>
            <ul class="rule-list">
              <li>每个数据集的模态构成可以不同，由条目级 <b>modals</b> 声明</li>
              <li>同一条目下的多个模态即对齐关系，无需额外字段</li>
              <li>缺 <b>modals</b> → 无法确定模态，不可接入</li>
            </ul>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="contractOpen = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 测试连接：逐字段检测 -->
    <el-dialog v-model="probeOpen" :title="probeTitle" width="720px" class="probe-dialog">
      <div class="probe-body">
        <div v-if="probeLoading" class="probe-loading">
          <span class="dot" />
          正在逐字段检测…
        </div>
        <template v-else-if="probeResult">
          <div class="probe-meta">
            <span class="badge" :class="probeStatus.cls">{{ probeStatus.label }}</span>
            <span class="probe-meta-text">{{ probeResult.message }}</span>
            <span
              v-if="probeResult.statusCode != null || probeResult.latencyMs != null"
              class="hint"
            >
              HTTP {{ probeResult.statusCode ?? '—' }} · {{ probeResult.latencyMs ?? '—' }} ms
            </span>
          </div>
          <div v-if="probeChecks.length" class="probe-list">
            <div
              v-for="check in probeChecks"
              :key="check.field"
              class="pf-row"
              :class="check.ok ? 'ok' : 'bad'"
            >
              <span class="pf-mark" aria-hidden="true">{{ check.ok ? '✓' : '✗' }}</span>
              <span class="pf-label">{{ check.field }}</span>
              <span class="pf-val">{{ checkValue(check) }}</span>
            </div>
          </div>
          <div class="probe-concl" :class="probeConclusionClass">
            <b>结论：{{ probeConclusionTitle }}</b> —— {{ probeConclusion }}
          </div>
        </template>
        <div v-else class="probe-concl bad">
          <b>结论：检测失败</b> —— {{ probeError || '未获取到检测结果' }}
        </div>
      </div>
      <template #footer>
        <el-button @click="probeOpen = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 接入上游数据集：拉取到共享卷并触发建库链 -->
    <el-dialog v-model="ingestOpen" :title="ingestTitle" width="720px">
      <div v-loading="ingestLoading" class="ingest-body">
        <div v-if="ingestError" class="probe-concl bad">
          <b>拉取失败</b> —— {{ ingestError }}
        </div>
        <template v-else>
          <p class="field-tip">
            选择上游数据集后，系统只拉取元数据并生成 manifest，自动触发 401→405 建库链，完成后该数据集可检索。
          </p>
          <el-table :data="ingestDatasets" size="small" empty-text="上游未返回数据集">
            <el-table-column label="dataset_id" min-width="140">
              <template #default="{ row }">
                <span class="mono">{{ row.datasetId || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="名称" min-width="140">
              <template #default="{ row }">{{ row.name || '—' }}</template>
            </el-table-column>
            <el-table-column label="条目数" width="90">
              <template #default="{ row }">{{ row.itemCount ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="必填完备" width="92">
              <template #default="{ row }">
                <span class="badge" :class="row.requiredOk ? 'ok' : 'bad'">
                  {{ row.requiredOk ? '齐全' : '缺项' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="92" fixed="right">
              <template #default="{ row }">
                <el-button
                  link
                  type="primary"
                  :disabled="!row.datasetId || !row.requiredOk || ingestingId === ingestSource?.id"
                  :title="row.requiredOk ? '接入并建库' : '上游缺少必填字段，无法接入'"
                  @click="doIngest(row)"
                >
                  {{ ingestingId === ingestSource?.id ? '接入中…' : '接入' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
      <template #footer>
        <el-button @click="ingestOpen = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 对接数据源 -->
    <el-dialog v-model="createOpen" title="对接数据源" width="460px">
      <el-form :model="createForm" label-width="84px">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" maxlength="40" placeholder="高质量数据集平台" />
        </el-form-item>
        <el-form-item label="来源地址" required :error="createAddrError">
          <el-input
            v-model="createForm.endpoint"
            maxlength="160"
            placeholder="http://ds-platform/api"
            @blur="createTouched = true"
          />
        </el-form-item>
        <el-form-item label="协议">
          <el-select v-model="createForm.protocol" style="width: 100%">
            <!-- 后端契约不支持推送接收 push（见「接口对接定义」），故不提供该选项 -->
            <el-option label="接口拉取" value="pull" />
          </el-select>
        </el-form-item>
        <el-form-item label="认证方式">
          <el-select v-model="createForm.authType" style="width: 100%">
            <el-option label="无" value="none" />
            <el-option label="Token" value="token" />
            <el-option label="Basic" value="basic" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="createForm.authType !== 'none'" label="认证信息" required :error="createAuthError">
          <el-input
            v-model="createForm.authConfig"
            maxlength="200"
            :placeholder="createForm.authType === 'basic' ? 'username:password' : '访问 Token'"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" maxlength="60" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">对接</el-button>
      </template>
    </el-dialog>

    <!-- 黑名单：右侧 560px 抽屉 -->
    <el-drawer v-model="blacklistOpen" class="bl-drawer" size="560px" :with-header="true">
      <template #header>
        <div class="bh-main">
          <h3>黑名单</h3>
          <p class="bh-sub">被屏蔽的数据集不出现在检索结果中</p>
        </div>
        <span class="hint bl-hint">共 {{ blacklist.length }} 条</span>
      </template>
      <el-table :data="blacklist" empty-text="黑名单为空">
        <el-table-column label="数据集 ID" min-width="104">
          <template #default="{ row }">
            <span class="mono ds-id">{{ row.datasetId || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="名称" min-width="96">
          <template #default="{ row }">
            <span class="ds-nm">{{ row.name || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源名" min-width="116">
          <template #default="{ row }">{{ row.sourceName || '—' }}</template>
        </el-table-column>
        <el-table-column label="地址" min-width="116" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.endpoint || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="58" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="removeRow(row)">移出</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button type="primary" @click="openAddBlacklist">＋ 添加黑名单</el-button>
      </template>
    </el-drawer>

    <!-- 添加黑名单 -->
    <el-dialog v-model="addBlOpen" title="添加黑名单" width="460px">
      <el-form :model="blForm" label-width="84px">
        <el-form-item label="数据集 ID" required :error="blIdError">
          <el-input v-model="blForm.datasetId" maxlength="40" placeholder="PKG261025F9K2VB" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="blForm.name" maxlength="40" placeholder="四模态产物（选填）" />
        </el-form-item>
        <el-form-item label="来源名">
          <el-input v-model="blForm.sourceName" maxlength="60" placeholder="产线作业语料包v2.3（选填）" />
          <div class="field-tip">与结果页「来源」一致时整源排除；留空则仅按数据集 ID 排除</div>
        </el-form-item>
        <el-form-item label="地址" :error="blAddrError">
          <el-input
            v-model="blForm.endpoint"
            maxlength="120"
            placeholder="https://ds.example/prod/4mod/v2"
            @blur="blTouched = true"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addBlOpen = false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="submitAddBlacklist">保存</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  addBlacklist,
  createDatasource,
  datasourceContract,
  datasourceDatasets,
  ingestDatasource,
  listBlacklist,
  listDatasources,
  removeBlacklist,
  syncDatasource,
  testDatasource,
} from '../../api'
import type {
  DatasourceBlacklistVO,
  DatasourceCheckVO,
  DatasourceTestVO,
  DatasourceVO,
  RemoteDatasetVO,
} from '../../api/types'
import { fmtTime } from '../../utils/format'

/** 上游 4 项必填的固定数量（与契约 dataset_id/name/item_count/modals 一致） */
const REQUIRED_TOTAL = 4

const NUM_FMT = new Intl.NumberFormat('en-US')

interface ContractFieldNode {
  required?: string[]
  optional?: string[]
  modalRule?: string
}

interface ContractBody {
  endpoints?: string[]
  dataset?: ContractFieldNode
  item?: ContractFieldNode
  pushMode?: string
}

interface FieldRow {
  field: string
  type: string
  required: string
  desc: string
}

/** 字段类型与说明展示文案（字段名来自契约，类型与说明为展示补充） */
const FIELD_META: Record<string, { type: string; desc: string }> = {
  dataset_id: { type: 'string', desc: '数据集唯一标识' },
  name: { type: 'string', desc: '数据集名称' },
  item_count: { type: 'int', desc: '条目数' },
  modal_types: { type: 'array', desc: '声明包含哪些模态，仅用于展示' },
  item_id: { type: 'string', desc: '条目唯一标识' },
  modals: { type: 'array', desc: '1~4 个模态，每条自己声明' },
}

const loading = ref(false)
const creating = ref(false)
const adding = ref(false)
const sources = ref<DatasourceVO[]>([])
const blacklist = ref<DatasourceBlacklistVO[]>([])
const testingId = ref('')
const syncingId = ref('')
const ingestingId = ref('')


const createOpen = ref(false)
const createTouched = ref(false)
const createForm = ref({
  name: '',
  endpoint: '',
  protocol: 'pull',
  authType: 'none',
  authConfig: '',
  remark: '',
})

const blacklistOpen = ref(false)
const addBlOpen = ref(false)
const blTouched = ref(false)
const blForm = ref({ datasetId: '', name: '', sourceName: '', endpoint: '' })

const probeOpen = ref(false)
const probeLoading = ref(false)
const probeSource = ref<DatasourceVO | null>(null)
const probeResult = ref<DatasourceTestVO | null>(null)
const probeError = ref('')

const ingestOpen = ref(false)
const ingestLoading = ref(false)
const ingestError = ref('')
const ingestSource = ref<DatasourceVO | null>(null)
const ingestDatasets = ref<RemoteDatasetVO[]>([])

const contractOpen = ref(false)
const contractLoading = ref(false)
const contractLoaded = ref(false)
const contractError = ref('')
const contract = ref<ContractBody>({})

const probeTitle = computed(() =>
  probeSource.value ? `测试连接 · ${probeSource.value.name}` : '测试连接',
)

const ingestTitle = computed(() =>
  ingestSource.value ? `接入数据集 · ${ingestSource.value.name}` : '接入数据集',
)

const probeChecks = computed<DatasourceCheckVO[]>(() => probeResult.value?.checks ?? [])

/** 接口层状态：连接成功或拿到上游 2xx/3xx 响应视为可达；无法判定时显示不适用 */
const probeStatus = computed<{ label: string; cls: string }>(() => {
  const r = probeResult.value
  if (!r) {
    return { label: '', cls: 'gray' }
  }
  if (r.connected) {
    return { label: '接口可达', cls: 'ok' }
  }
  if (r.statusCode != null && r.statusCode >= 200 && r.statusCode < 400) {
    return { label: '接口可达', cls: 'ok' }
  }
  if (r.statusCode == null && r.connected == null) {
    return { label: '不适用', cls: 'gray' }
  }
  return { label: '接口不可达', cls: 'fail' }
})

/** 真正的逐字段缺失项：detail 与整体 message 相同的是连接级失败，不算字段缺失 */
const probeMissingChecks = computed(() =>
  probeChecks.value.filter((check) => !check.ok && check.detail !== probeResult.value?.message),
)

const probeConclusionTitle = computed(() => {
  const r = probeResult.value
  if (!r) {
    return ''
  }
  if (r.connected == null) {
    return '不适用'
  }
  return r.connected ? '接入成功' : '接入失败'
})

const probeConclusionClass = computed(() => {
  const r = probeResult.value
  if (!r || r.connected == null) {
    return 'gray'
  }
  return r.connected ? 'ok' : 'bad'
})

const probeConclusion = computed(() => {
  const r = probeResult.value
  if (!r) {
    return ''
  }
  if (r.connected) {
    return '4 项必填齐全，契约校验通过；可点击「接入」选择上游数据集并触发建库。'
  }
  if (r.connected == null) {
    return `${r.message}。`
  }
  const missing = probeMissingChecks.value
  if (missing.length) {
    return `上游缺少 ${missing.length} 个必填字段（${missing
      .map((check) => check.label)
      .join('、')}），补齐后可重新测试。`
  }
  return `${r.message}，状态保持不变。`
})

const contractPushMode = computed(() => contract.value.pushMode ?? '')

const contractModalRule = computed(() => contract.value.item?.modalRule ?? '')

const datasetFieldRows = computed(() => buildFieldRows(contract.value.dataset))

const itemFieldRows = computed(() => buildFieldRows(contract.value.item))

const contractApiRows = computed(() =>
  (contract.value.endpoints ?? []).map((raw) => {
    const matched = raw.match(/^([A-Za-z]+)\s+(.+)$/)
    const method = matched?.[1] ?? 'GET'
    const path = matched?.[2] ?? raw
    return { raw, method, path, desc: apiDescription(path) }
  }),
)

const createAddrError = computed(() => addressError(createForm.value.endpoint, createTouched.value))

const createAuthError = computed(() => {
  if (createForm.value.authType === 'none' || !createTouched.value) {
    return ''
  }
  const auth = createForm.value.authConfig.trim()
  if (!auth) {
    return '认证信息不能为空'
  }
  if (createForm.value.authType === 'basic' && !auth.includes(':')) {
    return '格式必须为 username:password'
  }
  return ''
})

const blIdError = computed(() => {
  const datasetId = blForm.value.datasetId.trim()
  if (!datasetId) {
    return blTouched.value ? '请填写数据集 ID' : ''
  }
  if (blacklist.value.some((row) => row.datasetId === datasetId)) {
    return '该数据集已在黑名单'
  }
  return ''
})

const blAddrError = computed(() => addressError(blForm.value.endpoint, blTouched.value))

function buildFieldRows(node?: ContractFieldNode): FieldRow[] {
  if (!node) {
    return []
  }
  const rows: FieldRow[] = []
  for (const field of node.required ?? []) {
    const meta = FIELD_META[field] ?? { type: 'string', desc: '' }
    rows.push({ field, required: '必填', ...meta })
  }
  for (const field of node.optional ?? []) {
    const meta = FIELD_META[field] ?? { type: 'string', desc: '' }
    rows.push({ field, required: '可选', ...meta })
  }
  return rows
}

function apiDescription(path: string): string {
  if (path.includes('/items')) {
    return '条目列表（item_id / modals），分页'
  }
  if (/\/datasets\/?$/.test(path.replace(/\?.*$/, ''))) {
    return '列出数据集（dataset_id / name / item_count）'
  }
  if (path.includes('/datasets/')) {
    return '数据集详情'
  }
  return ''
}

function addressError(value: string, touched: boolean): string {
  const address = value.trim()
  if (!address) {
    return touched ? '请填写地址' : ''
  }
  if (!/^https?:\/\//i.test(address)) {
    return '地址需以 http:// 或 https:// 开头'
  }
  if (/\s/.test(address)) {
    return '地址中不能有空格'
  }
  return ''
}

function statusLabel(status: string): string {
  if (status === 'connected') {
    return '已连接'
  }
  if (status === 'failed') {
    return '连接失败'
  }
  return '未连接'
}

function statusClass(status: string): string {
  if (status === 'connected') {
    return 'ok'
  }
  if (status === 'failed') {
    return 'fail'
  }
  return 'off'
}

function fieldRatio(row: DatasourceVO): string {
  const missing = row.missingFields
  if (missing == null) {
    return '—'
  }
  return `${REQUIRED_TOTAL - missing.length}/${REQUIRED_TOTAL}`
}

function fieldClass(row: DatasourceVO): string {
  const missing = row.missingFields
  if (missing == null) {
    return 'gray'
  }
  return missing.length ? 'fail' : 'ok'
}

function fieldTip(row: DatasourceVO): string {
  const missing = row.missingFields
  if (missing == null) {
    return '尚未测试连接，字段完备未判定'
  }
  return missing.length ? `缺少：${missing.join('、')}` : `${REQUIRED_TOTAL} 个必填字段全部提供`
}

function vecAvailable(row: DatasourceVO): boolean {
  return row.connectStatus === 'connected' && Number(row.totalCount) > 0
}

function vecText(row: DatasourceVO): string {
  if (!vecAvailable(row)) {
    return '—'
  }
  return `${NUM_FMT.format(Number(row.vectorizedCount) || 0)} / ${NUM_FMT.format(Number(row.totalCount) || 0)}`
}

function vecClass(row: DatasourceVO): string {
  if (!vecAvailable(row)) {
    return 'none'
  }
  return Number(row.vectorizedCount) >= Number(row.totalCount) ? 'done' : 'part'
}

function vecHint(row: DatasourceVO): string {
  const base = '平台接入后自动抽取向量写入向量库，使用「图文对齐 · CLIP双塔 v3」'
  if (!vecAvailable(row)) {
    return base
  }
  const percent = Math.round((Number(row.vectorizedCount) / Number(row.totalCount)) * 100)
  return percent >= 100 ? base : `${base}；已完成 ${percent}%`
}

function checkValue(check: DatasourceCheckVO): string {
  if (check.detail) {
    return check.detail
  }
  return check.ok ? '已提供' : '未提供'
}

async function loadSources() {
  loading.value = true
  try {
    sources.value = await listDatasources()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function loadBlacklistRows() {
  try {
    blacklist.value = await listBlacklist()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function openBlacklist() {
  blacklistOpen.value = true
  void loadBlacklistRows()
}

function openCreate() {
  createTouched.value = false
  createForm.value = {
    name: '',
    endpoint: '',
    protocol: 'pull',
    authType: 'none',
    authConfig: '',
    remark: '',
  }
  createOpen.value = true
}

async function submitCreate() {
  createTouched.value = true
  const name = createForm.value.name.trim()
  const endpoint = createForm.value.endpoint.trim()
  if (!name) {
    ElMessage.error('请填写名称')
    return
  }
  if (!endpoint) {
    ElMessage.error('请填写地址')
    return
  }
  const addrError = addressError(endpoint, true)
  if (addrError) {
    ElMessage.error(addrError)
    return
  }
  if (createForm.value.authType !== 'none' && !createForm.value.authConfig.trim()) {
    ElMessage.error('认证信息不能为空')
    return
  }
  if (createForm.value.authType === 'basic' && !createForm.value.authConfig.includes(':')) {
    ElMessage.error('basic 认证信息格式必须为 username:password')
    return
  }
  creating.value = true
  try {
    await createDatasource({
      name,
      endpoint,
      protocol: createForm.value.protocol,
      authType: createForm.value.authType,
      authConfig: createForm.value.authConfig,
      remark: createForm.value.remark,
    })
    ElMessage.success('已对接数据源')
    createOpen.value = false
    await loadSources()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    creating.value = false
  }
}

async function sync(row: DatasourceVO) {
  syncingId.value = row.id
  try {
    const result = await syncDatasource(row.id)
    ElMessage.success(`已同步 ${result.datasetCount} 个数据集`)
    await loadSources()
  } catch (error) {
    ElMessage.error((error as Error).message)
    await loadSources()
  } finally {
    syncingId.value = ''
  }
}

async function testConnection(row: DatasourceVO) {
  probeSource.value = row
  probeResult.value = null
  probeError.value = ''
  probeOpen.value = true
  probeLoading.value = true
  testingId.value = row.id
  try {
    const result = await testDatasource(row.id)
    probeResult.value = result
    if (result.connected) {
      ElMessage.success('连接成功，契约校验通过')
    } else if (result.connected == null) {
      ElMessage.warning(result.message)
    } else {
      const missing = probeMissingChecks.value
      ElMessage.error(
        missing.length ? `接入失败：缺少 ${missing.length} 个必填字段` : `接入失败：${result.message}`,
      )
    }
  } catch (error) {
    probeError.value = (error as Error).message
    ElMessage.error((error as Error).message)
  } finally {
    probeLoading.value = false
    testingId.value = ''
    await loadSources()
  }
}

async function openIngest(row: DatasourceVO) {
  ingestSource.value = row
  ingestOpen.value = true
  ingestLoading.value = true
  ingestError.value = ''
  ingestDatasets.value = []
  try {
    const result = await datasourceDatasets(row.id)
    ingestDatasets.value = result.datasets || []
    if (!ingestDatasets.value.length) {
      ingestError.value = '上游未返回可接入的数据集'
    }
  } catch (error) {
    ingestError.value = (error as Error).message
  } finally {
    ingestLoading.value = false
  }
}

async function doIngest(dataset: RemoteDatasetVO) {
  const source = ingestSource.value
  if (!source || !dataset.datasetId) {
    return
  }
  ingestingId.value = source.id
  try {
    const created = await ingestDatasource(source.id, {
      datasetId: dataset.datasetId,
      name: dataset.name || undefined,
    })
    ElMessage.success(`已接入「${created.name || dataset.datasetId}」，建库任务已提交`)
    ingestOpen.value = false
    await loadSources()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    ingestingId.value = ''
  }
}

function openContract() {
  contractOpen.value = true
  void ensureContract()
}

async function ensureContract() {
  if (contractLoaded.value || contractLoading.value) {
    return
  }
  contractLoading.value = true
  contractError.value = ''
  try {
    contract.value = (await datasourceContract()) as unknown as ContractBody
    contractLoaded.value = true
  } catch (error) {
    contractError.value = (error as Error).message
  } finally {
    contractLoading.value = false
  }
}

function openAddBlacklist() {
  blTouched.value = false
  blForm.value = { datasetId: '', name: '', sourceName: '', endpoint: '' }
  addBlOpen.value = true
}

async function submitAddBlacklist() {
  blTouched.value = true
  const datasetId = blForm.value.datasetId.trim()
  const name = blForm.value.name.trim()
  const sourceName = blForm.value.sourceName.trim()
  const endpoint = blForm.value.endpoint.trim()
  if (!datasetId) {
    ElMessage.error('请填写数据集 ID')
    return
  }
  // 后端按 datasetId 去重：已存在时更新名称/来源名/地址，不再前端拦截
  const existing = blacklist.value.some((row) => row.datasetId === datasetId)
  if (endpoint) {
    const addrError = addressError(endpoint, true)
    if (addrError) {
      ElMessage.error(addrError)
      return
    }
  }
  adding.value = true
  try {
    await addBlacklist({
      datasetId,
      name: name || undefined,
      sourceName: sourceName || undefined,
      endpoint: endpoint || undefined,
    })
    ElMessage.success(existing ? '已更新黑名单' : '已加入黑名单')
    addBlOpen.value = false
    await loadBlacklistRows()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    adding.value = false
  }
}

async function removeRow(row: DatasourceBlacklistVO) {
  try {
    await removeBlacklist(row.id)
    ElMessage.success('已移出黑名单')
    await loadBlacklistRows()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

onMounted(() => {
  void loadSources()
  void loadBlacklistRows()
})
</script>

<style scoped>
/* 容器：原型 1180 / 18-24-40 / gap 14（在 .page-card 基线上局部覆写） */
.page-card {
  max-width: 1180px;
  padding: 18px 24px 40px;
  gap: 14px;
}

.ph-main { min-width: 0; }

/* 面板头 */
.lib-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}
.sec-head { display: flex; align-items: center; gap: 8px; min-width: 0; }
.sec-idx {
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
  border-radius: 5px;
  background: var(--system-blue);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
}
.sec-title { font-size: 15px; font-weight: 700; color: #1f2a44; white-space: nowrap; }
.ph-tools { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.hint {
  font-size: 11.5px;
  color: var(--muted-soft);
  font-variant-numeric: tabular-nums;
}

/* 工具条按钮 */
.ghost-btn.el-button {
  height: 32px;
  padding: 0 16px;
  border-color: var(--control-line-soft);
  background: #fff;
  color: #475467;
  font-size: 13px;
  font-weight: 600;
}
.ghost-btn.el-button:hover {
  border-color: #b9c9ff;
  background: var(--system-blue-weak);
  color: var(--system-blue);
}
.primary-btn.el-button { height: 30px; padding: 0 14px; font-size: 12.5px; font-weight: 600; }

/* 表格单元 */
.ds-nm { font-size: 12.5px; color: #273244; }
.ds-id { font-size: 11.5px; font-weight: 600; color: #273244; }
.cell-time { color: #475467; font-variant-numeric: tabular-nums; white-space: nowrap; }

/* 向量化列 */
.vec-num {
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.2px;
  white-space: nowrap;
}
.vec-num.done { color: var(--success); }
.vec-num.part { color: var(--warning); }
.vec-num.none { color: #aab4c8; }

/* 行内操作 */
.row-ops { display: flex; flex-wrap: wrap; gap: 4px; }
.row-ops :deep(.el-button + .el-button) { margin-left: 0 !important; }
.op-btn.el-button {
  height: 24px;
  padding: 0 10px;
  border: 1px solid var(--control-line-soft);
  border-radius: 5px;
  background: #fff;
  color: var(--system-blue);
  font-size: 11.5px;
  font-weight: 600;
}
.op-btn.el-button:hover {
  border-color: #b9c9ff;
  background: var(--system-blue-weak);
  color: var(--system-blue);
}
.op-btn.sync.el-button {
  border-color: var(--system-blue);
  background: var(--system-blue);
  color: #fff;
}
.op-btn.sync.el-button:hover {
  border-color: var(--system-blue-hover);
  background: var(--system-blue-hover);
  color: #fff;
}
.op-btn.el-button.is-disabled,
.op-btn.el-button.is-disabled:hover {
  opacity: 0.6;
  border-color: var(--control-line-soft);
  background: #fff;
  color: var(--muted);
}

/* 弹窗小标题 */
.hb-t {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 6px;
  color: #1f2a44;
  font-size: 12.5px;
  font-weight: 700;
}
.hb-t::before {
  content: '';
  width: 3px;
  height: 12px;
  border-radius: 2px;
  background: var(--system-blue);
}

.field-tip { margin-top: 2px; font-size: 11px; color: var(--muted-soft); line-height: 1.4; }

/* 接口对接定义 */
.contract-body { display: flex; flex-direction: column; gap: 11px; min-height: 80px; }
.contract-lead {
  margin: 0;
  padding: 9px 12px;
  border: 1px dashed #cdd9f5;
  border-radius: 8px;
  background: var(--system-blue-weak);
  color: #475467;
  font-size: 12px;
  line-height: 1.75;
}
.contract-error {
  padding: 12px;
  border: 1px dashed #f7d4d5;
  border-radius: 8px;
  background: #fdf0f0;
  color: #b42318;
  font-size: 12px;
}
.contract-sec { display: flex; flex-direction: column; gap: 6px; }
.contract-table { width: 100%; border-collapse: collapse; font-size: 11.5px; }
.contract-table th {
  padding: 6px 9px;
  border: 1px solid #e6edf8;
  background: #f4f6fb;
  color: #7a8493;
  font-size: 10.5px;
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
}
.contract-table td {
  padding: 5px 9px;
  border: 1px solid #eef2f8;
  color: #475467;
  line-height: 1.6;
  vertical-align: top;
}
.contract-table td.mk,
.mk {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  color: #273244;
  white-space: nowrap;
}
.struct-box {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 8px 10px;
  border: 1px solid #eef2f8;
  border-radius: 8px;
  background: var(--content-soft);
}
.struct-line {
  font-family: var(--font-mono);
  font-size: 11px;
  color: #365070;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
.api-list {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 8px 10px;
  border: 1px solid #eef2f8;
  border-radius: 8px;
  background: var(--content-soft);
}
.api-row { display: flex; align-items: baseline; gap: 9px; min-width: 0; font-size: 11.5px; line-height: 1.65; }
.api-m {
  flex: 0 0 auto;
  padding: 1px 6px;
  border: 1px solid #dbe5ff;
  border-radius: 4px;
  background: #eef3ff;
  color: #1d4ed8;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
}
.api-p {
  flex: 0 0 auto;
  color: #273244;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.api-d { min-width: 0; color: #7a8493; }
.rule-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 5px;
  color: #475467;
  font-size: 11.5px;
  line-height: 1.7;
}
.rule-list li { position: relative; padding-left: 13px; }
.rule-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 7px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--warning);
}
.rule-list b { color: #1f2a44; font-weight: 700; }

/* 测试连接 */
.probe-body { display: flex; flex-direction: column; gap: 11px; }
.probe-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  color: #475467;
  font-size: 12px;
  line-height: 1.6;
}
.probe-meta-text { min-width: 0; word-break: break-word; }
.probe-list {
  display: flex;
  flex-direction: column;
  border: 1px solid #eef2f8;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}
.pf-row {
  display: grid;
  grid-template-columns: 16px minmax(96px, 132px) 1fr;
  gap: 8px;
  padding: 6px 11px;
  border-bottom: 1px solid #f1f5fa;
  font-size: 12px;
  line-height: 1.55;
}
.pf-row:last-child { border-bottom: 0; }
.pf-mark { font-weight: 700; text-align: center; }
.pf-row.ok .pf-mark { color: var(--success); }
.pf-row.bad .pf-mark { color: var(--error); }
.pf-label {
  color: #273244;
  font-family: var(--font-mono);
  font-size: 11.5px;
  font-weight: 600;
  word-break: break-all;
}
.pf-val { min-width: 0; color: #475467; word-break: break-word; }
.pf-val .pf-miss { color: var(--error); font-weight: 600; }
.probe-concl { padding: 9px 12px; border-radius: 7px; font-size: 12px; line-height: 1.75; }
.probe-concl.ok { border: 1px solid #cdebd7; background: #eef9f1; color: #1e7b3c; }
.probe-concl.bad { border: 1px solid #f7d4d5; background: #fdf0f0; color: #b42318; }
.probe-concl.gray { border: 1px solid #e4e8f0; background: #f0f2f7; color: #5c6573; }
.probe-concl b { font-weight: 700; }
.probe-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border: 1px dashed #dbe5f5;
  border-radius: 8px;
  color: var(--muted);
  font-size: 12px;
}
.probe-loading .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--system-blue);
  animation: probePulse 1s ease-in-out infinite;
}
@keyframes probePulse {
  0%, 100% { opacity: 0.25; }
  50% { opacity: 1; }
}

/* 黑名单抽屉头 */
.bh-main { min-width: 0; }
.bh-main h3 { margin: 0; color: #1f2937; font-size: 15px; font-weight: 700; line-height: 1.4; }
.bh-sub { margin: 2px 0 0; color: #9aa6bd; font-size: 11.5px; line-height: 1.5; }
.bl-hint { flex: 0 0 auto; margin-right: 4px; }

/* 弹窗体高度上限（原型 72vh） */
:global(.contract-dialog .el-dialog__body),
:global(.probe-dialog .el-dialog__body) {
  max-height: 72vh;
  overflow-y: auto;
}
:global(.contract-dialog) {
  max-width: calc(100vw - 32px);
}

/* 抽屉内部（Teleport 到 body，用页级唯一类名限定） */
:global(.bl-drawer .el-drawer__footer) {
  padding: 12px 18px;
  border-top: 1px solid #e4eaf3;
  background: #fff;
  text-align: right;
}
:global(.bl-drawer .el-table) {
  --el-table-border-color: #eef2f8;
  --el-table-header-bg-color: #f4f6fb;
  --el-table-header-text-color: #7a8493;
  --el-table-row-hover-bg-color: #f8fbff;
  --el-table-text-color: #344054;
  border: 1px solid #eef2f8;
  border-radius: 8px;
  overflow: hidden;
  font-size: 12px;
}
:global(.bl-drawer .el-table th.el-table__cell) {
  padding: 7px 0;
  color: #7a8493;
  font-size: 11px;
  font-weight: 600;
}
:global(.bl-drawer .el-table td.el-table__cell) { padding: 6px 0; }
:global(.bl-drawer .el-table .cell) { line-height: 1.5; }

@media (max-width: 1000px) {
  .page-card { padding: 14px 14px 28px; }
}
</style>
