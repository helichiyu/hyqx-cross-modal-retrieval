<template>
  <div class="page-card">
    <el-card>
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="查询内容/编号搜索"
          clearable
          style="width: 220px"
          @keyup.enter="reload"
        />
        <el-select v-model="modalityFilter" placeholder="查询模态" clearable style="width: 130px">
          <el-option label="文本" value="text" />
          <el-option label="图片" value="image" />
          <el-option label="音频" value="audio" />
          <el-option label="视频" value="video" />
        </el-select>
        <el-select v-model="adoptFilter" placeholder="采用状态" clearable style="width: 130px">
          <el-option v-for="d in adoptDict" :key="d.alias" :label="d.label" :value="d.alias" />
        </el-select>
        <el-checkbox v-model="zeroOnly">仅零结果</el-checkbox>
        <el-button type="primary" @click="reload">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column label="SRH 编号" width="180">
          <template #default="{ row }"><BizNoTag :value="row.bizNo" /></template>
        </el-table-column>
        <el-table-column label="查询" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.clarified" size="small" type="warning" style="margin-right: 4px">
              澄清
            </el-tag>
            {{ row.queryText || row.queryFile || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="模态" width="80">
          <template #default="{ row }">{{ modalityLabel(row.queryModality) }}</template>
        </el-table-column>
        <el-table-column label="场景" width="90">
          <template #default="{ row }">{{ row.scene || 'all' }}</template>
        </el-table-column>
        <el-table-column label="结果数" width="80" align="right">
          <template #default="{ row }">{{ row.itemCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="耗时(ms)" width="90" align="right">
          <template #default="{ row }">{{ row.elapsedMs ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="采用状态" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.adoptStatus" :dict="adoptDict" />
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="warning" @click="doRerun(row)">再次执行</el-button>
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

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="检索详情" size="56%">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="SRH 编号">
            <BizNoTag :value="detail.history.bizNo" />
          </el-descriptions-item>
          <el-descriptions-item label="结果集">
            <span class="mono">{{ detail.history.resultSetId }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="查询" :span="2">
            {{ detail.history.queryText || detail.history.queryFile }}
          </el-descriptions-item>
          <el-descriptions-item label="模态">
            {{ modalityLabel(detail.history.queryModality) }}
          </el-descriptions-item>
          <el-descriptions-item label="场景">
            {{ detail.history.scene || 'all' }}
          </el-descriptions-item>
          <el-descriptions-item label="结果数">
            {{ detail.history.itemCount ?? 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="耗时">
            {{ detail.history.elapsedMs ?? '-' }} ms
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">策略快照</el-divider>
        <pre class="mono snapshot">{{ pretty(detail.strategySnapshotJson) }}</pre>

        <el-divider content-position="left">结果条目</el-divider>
        <el-table :data="detail.items" border size="small" max-height="360">
          <el-table-column prop="rank" label="#" width="50" align="center" />
          <el-table-column label="模态" width="70">
            <template #default="{ row }">{{ modalityLabel(row.modality) }}</template>
          </el-table-column>
          <el-table-column label="得分" width="90">
            <template #default="{ row }">{{ Number(row.score).toFixed(4) }}</template>
          </el-table-column>
          <el-table-column prop="text" label="内容" min-width="160" show-overflow-tooltip />
          <el-table-column label="来源" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="mono">{{ row.sourceFile || row.itemId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="点击" width="60" align="center">
            <template #default="{ row }">{{ row.clicked ? '✓' : '' }}</template>
          </el-table-column>
        </el-table>
        <el-button
          style="margin-top: 10px"
          type="primary"
          plain
          @click="$router.push(`/search/results/${detail.history.resultSetId}`)"
        >
          打开结果页
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { historyDetail, pageHistory, rerunHistory } from '../../api'
import type { DictItem, HistoryDetailVO, HistoryVO } from '../../api/types'
import BizNoTag from '../../components/BizNoTag.vue'
import StatusTag from '../../components/StatusTag.vue'
import { fmtTime, modalityLabel } from '../../utils/format'

const router = useRouter()

const rows = ref<HistoryVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const modalityFilter = ref('')
const adoptFilter = ref('')
const zeroOnly = ref(false)
const loading = ref(false)
const adoptDict = ref<DictItem[]>([
  { alias: 'adopted', label: '已采用' },
  { alias: 'unadopted', label: '未采用' },
  { alias: 'discarded', label: '已废弃' },
])

async function reload() {
  loading.value = true
  try {
    const page = await pageHistory({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value,
      modality: modalityFilter.value,
      adoptStatus: adoptFilter.value,
      zeroResult: zeroOnly.value || undefined,
    })
    rows.value = page.list
    total.value = page.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

const detailVisible = ref(false)
const detail = ref<HistoryDetailVO | null>(null)

async function openDetail(row: HistoryVO) {
  try {
    detail.value = await historyDetail(row.id)
    detailVisible.value = true
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function doRerun(row: HistoryVO) {
  try {
    const result = await rerunHistory(row.id)
    ElMessage.success(`已按原参数重新执行：${result.srhNo}`)
    router.push(`/search/results/${result.resultSetId}`)
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

const pretty = (raw?: string | null) => {
  if (!raw) {
    return '（无快照）'
  }
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

onMounted(reload)
</script>

<style scoped>
.snapshot {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  max-height: 200px;
  overflow: auto;
}
</style>
