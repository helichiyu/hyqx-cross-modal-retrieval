<template>
  <div class="page-card">
    <header class="cm-page-header">
      <div>
        <h1 class="cm-page-title">数据源库管理</h1>
        <p class="cm-page-subtitle">
          只登记数据源、拉取上游元数据、维护黑名单；数据集文件仍在来源方，检索按条目 ID 引用，不做数据出域。
        </p>
      </div>
      <div class="cm-flex">
        <el-button type="primary" @click="openCreate">登记数据源</el-button>
        <el-button @click="loadAll">刷新</el-button>
      </div>
    </header>

    <el-card shadow="never">
      <template #header>
        <div class="cm-flex-between">
          <span>数据源列表</span>
          <span class="text-secondary">连接状态 · 4 项必填校验 · 同步元数据</span>
        </div>
      </template>
      <el-table v-loading="loading" :data="sources" size="small" empty-text="暂无数据源">
        <el-table-column label="数据源" min-width="200">
          <template #default="{ row }">
            <div class="cell-name">{{ row.name }}</div>
            <div class="cell-sub mono">{{ row.bizNo }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="endpoint" label="接口地址" min-width="220" show-overflow-tooltip />
        <el-table-column label="连接状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.connectStatus)" effect="light" size="small">
              {{ statusLabel(row.connectStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="必填校验" width="110">
          <template #default="{ row }">
            <el-tag :type="row.requiredOk ? 'success' : 'warning'" effect="plain" size="small">
              {{ row.requiredOk ? '4 项齐全' : '有缺失' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="条目数" width="110">
          <template #default="{ row }">
            {{ row.vectorizedCount }} / {{ row.totalCount }}
          </template>
        </el-table-column>
        <el-table-column label="上次同步" width="160">
          <template #default="{ row }">{{ row.lastSyncTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :loading="syncingIds.has(row.id)" @click="sync(row)">同步</el-button>
            <el-button link type="primary" @click="loadRemote(row)">数据集</el-button>
            <el-button link type="danger" @click="openBlacklist(row)">拉黑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="remote" shadow="never">
      <template #header>
        <div class="cm-flex-between">
          <span>上游数据集 · {{ remoteSourceName }}</span>
          <span class="text-secondary">
            共 {{ remote.datasetCount }} 个 / 条目 {{ remote.totalItems }} 条
            <el-tag v-if="remote.itemIssue" type="warning" size="small" effect="plain" class="ml8">
              条目契约：{{ remote.itemIssue }}
            </el-tag>
          </span>
        </div>
      </template>
      <el-table :data="remote.datasets" size="small" empty-text="上游未返回数据集">
        <el-table-column prop="datasetId" label="dataset_id" min-width="160" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="itemCount" label="条目数" width="100" />
        <el-table-column label="必填" width="90">
          <template #default="{ row }">
            <el-tag :type="row.requiredOk ? 'success' : 'danger'" size="small" effect="plain">
              {{ row.requiredOk ? '通过' : '缺失' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="cm-flex-between">
          <span>数据源黑名单</span>
          <span class="text-secondary">按来源名整源排除，可随时移出</span>
        </div>
      </template>
      <el-table :data="blacklist" size="small" empty-text="暂无黑名单">
        <el-table-column prop="sourceName" label="来源名" min-width="160" />
        <el-table-column prop="datasetId" label="dataset_id" min-width="140" />
        <el-table-column prop="name" label="数据集名称" min-width="160" />
        <el-table-column prop="createTime" label="加入时间" width="170" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeBlacklistRow(row)">移出</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>接入契约（只读）</template>
      <div class="contract">
        <div class="contract-row">
          <span class="contract-key">接口</span>
          <div>
            <div v-for="endpoint in contractEndpoints" :key="endpoint" class="mono">{{ endpoint }}</div>
          </div>
        </div>
        <div class="contract-row">
          <span class="contract-key">数据集必填</span>
          <div>
            <div class="mono">{{ contractDatasetRequired }}</div>
            <div class="text-secondary">可选：modal_types</div>
          </div>
        </div>
        <div class="contract-row">
          <span class="contract-key">条目必填</span>
          <div>
            <div class="mono">{{ contractItemRequired }}</div>
            <div class="text-secondary">{{ contractModalRule }}</div>
          </div>
        </div>
        <div class="contract-row">
          <span class="contract-key">说明</span>
          <div class="text-secondary">{{ contractPushMode }}</div>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="createVisible" title="登记数据源" width="520px">
      <el-form label-width="96px" :model="createForm">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" maxlength="40" placeholder="如：数据集管理-生产池" />
        </el-form-item>
        <el-form-item label="接口地址" required>
          <el-input v-model="createForm.endpoint" maxlength="200" placeholder="https://ds.example/prod" />
        </el-form-item>
        <el-form-item label="认证方式">
          <el-select v-model="createForm.authType" style="width: 100%">
            <el-option label="Token" value="token" />
            <el-option label="Basic" value="basic" />
            <el-option label="无" value="none" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="createForm.authType !== 'none'" label="认证信息" required>
          <el-input
            v-model="createForm.authConfig"
            maxlength="200"
            :placeholder="createForm.authType === 'basic' ? 'username:password' : '访问 Token'"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.remark" maxlength="60" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="blacklistVisible" title="加入黑名单" width="460px">
      <el-form label-width="90px">
        <el-form-item label="来源名" required>
          <el-input v-model="blacklistForm.sourceName" placeholder="结果页按来源名排除" />
        </el-form-item>
        <el-form-item label="dataset_id">
          <el-input v-model="blacklistForm.datasetId" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="blacklistForm.name" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="blacklistVisible = false">取消</el-button>
        <el-button type="danger" :loading="blacklisting" @click="submitBlacklist">加入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addBlacklist,
  createDatasource,
  datasourceContract,
  datasourceDatasets,
  listBlacklist,
  listDatasources,
  removeBlacklist,
  syncDatasource,
} from '../../api'
import type { DatasourceBlacklistVO, DatasourceVO, RemoteDatasetsVO } from '../../api/types'

const loading = ref(false)
const creating = ref(false)
const blacklisting = ref(false)
const sources = ref<DatasourceVO[]>([])
const blacklist = ref<DatasourceBlacklistVO[]>([])
const remote = ref<RemoteDatasetsVO | null>(null)
const remoteSourceName = ref('')
const syncingIds = ref(new Set<string>())
const contract = ref<Record<string, unknown>>({})

const createVisible = ref(false)
const createForm = ref({ name: '', endpoint: '', authType: 'token', authConfig: '', remark: '' })
const blacklistVisible = ref(false)
const blacklistForm = ref({ sourceName: '', datasetId: '', name: '' })

const contractEndpoints = computed(() => (contract.value.endpoints as string[]) ?? [])
const contractDatasetRequired = computed(
  () => ((contract.value.dataset as Record<string, unknown>)?.required as string[])?.join(' / ') ?? '',
)
const contractItemRequired = computed(
  () => ((contract.value.item as Record<string, unknown>)?.required as string[])?.join(' / ') ?? '',
)
const contractModalRule = computed(
  () => ((contract.value.item as Record<string, unknown>)?.modalRule as string) ?? '',
)
const contractPushMode = computed(() => (contract.value.pushMode as string) ?? '')

function statusLabel(status: string) {
  return { connected: '已连接', failed: '连接失败', unconnected: '未连接' }[status] ?? status
}

function statusTagType(status: string) {
  return status === 'connected' ? 'success' : status === 'failed' ? 'danger' : 'info'
}

async function loadAll() {
  loading.value = true
  try {
    const [sourceRows, blacklistRows, contractBody] = await Promise.all([
      listDatasources(),
      listBlacklist(),
      datasourceContract(),
    ])
    sources.value = sourceRows
    blacklist.value = blacklistRows
    contract.value = contractBody
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createForm.value = { name: '', endpoint: '', authType: 'token', authConfig: '', remark: '' }
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.value.name.trim() || !createForm.value.endpoint.trim()) {
    ElMessage.warning('名称与接口地址必填')
    return
  }
  creating.value = true
  try {
    await createDatasource({ ...createForm.value, protocol: 'pull' })
    ElMessage.success('数据源已登记')
    createVisible.value = false
    await loadAll()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    creating.value = false
  }
}

async function sync(row: DatasourceVO) {
  syncingIds.value.add(row.id)
  try {
    const result = await syncDatasource(row.id)
    ElMessage.success(
      `同步成功：数据集 ${result.datasetCount} 个，必填校验${result.requiredOk ? '通过' : '存在缺失'}`,
    )
    await loadAll()
  } catch (e) {
    ElMessage.error((e as Error).message)
    await loadAll()
  } finally {
    syncingIds.value.delete(row.id)
  }
}

async function loadRemote(row: DatasourceVO) {
  try {
    remote.value = await datasourceDatasets(row.id)
    remoteSourceName.value = row.name
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

function openBlacklist(row: DatasourceVO) {
  blacklistForm.value = { sourceName: row.name, datasetId: row.upstreamDatasetId ?? '', name: row.name }
  blacklistVisible.value = true
}

async function submitBlacklist() {
  if (!blacklistForm.value.sourceName.trim()) {
    ElMessage.warning('来源名必填')
    return
  }
  blacklisting.value = true
  try {
    await addBlacklist({ ...blacklistForm.value })
    ElMessage.success('已加入黑名单')
    blacklistVisible.value = false
    blacklist.value = await listBlacklist()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    blacklisting.value = false
  }
}

async function removeBlacklistRow(row: DatasourceBlacklistVO) {
  await ElMessageBox.confirm(`确认把来源「${row.sourceName}」移出黑名单？`, '移出黑名单', { type: 'warning' })
  await removeBlacklist(row.id)
  blacklist.value = await listBlacklist()
  ElMessage.success('已移出')
}

onMounted(loadAll)
</script>

<style scoped>
.cell-name { font-weight: 600; color: var(--text-primary); }
.cell-sub { color: var(--muted); font-size: 12px; }
.ml8 { margin-left: 8px; }
.contract { display: flex; flex-direction: column; gap: 12px; }
.contract-row { display: flex; gap: 16px; font-size: 13px; }
.contract-key { width: 88px; flex-shrink: 0; color: var(--muted); }
</style>
