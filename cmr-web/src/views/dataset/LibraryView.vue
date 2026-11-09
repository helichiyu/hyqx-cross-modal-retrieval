<template>
  <div class="p4-root">
    <div class="main">
      <div class="content">
        <header class="page-head">
          <div class="ph-main">
            <h1>库管理</h1>
            <p>把上游数据集变成可检索库：登记数据源后，数据集自动跑到「可检索」；卡住才需要处理。</p>
          </div>
          <div class="ph-actions">
            <button class="btn-primary" type="button" @click="datasetVisible = true">登记数据集</button>
            <button class="dev-entry" type="button" title="研发说明" @click="openDev"><span class="de-q" aria-hidden="true">?</span>研发说明</button>
          </div>
        </header>

        <section class="panel" aria-label="数据集列表">
          <div class="panel-head">
            <div class="sec-head">
              <span class="sec-title">数据集列表</span>
              <span class="view-badge">联动视图</span>
              <span class="sec-sub">看状态 / 卡点 / 处理</span>
            </div>
            <div class="p4-filter">
              <select v-model="statusFilter" aria-label="状态筛选">
                <option value="">全部状态</option>
                <option v-for="item in statusDict" :key="item.alias" :value="item.alias">{{ item.label }}</option>
              </select>
              <input v-model="keyword" type="search" placeholder="搜索数据集编号 / 名称" @keyup.enter="reload(false)">
              <button class="btn-mini" type="button" @click="reload(false)">查询</button>
            </div>
          </div>

          <div class="src-strip">
            <span class="ib-k">输入边界</span>
            <span class="src-chips">
              <span v-if="!datasources.length" class="src-empty">暂无数据源</span>
              <span v-for="source in datasources" :key="source.id" class="src-chip" :title="sourceTitle(source)">
                <i class="dot" :class="sourceDotClass(source)"></i>
                <span class="sc-name">{{ source.name }}</span>
                <span class="sc-proto">{{ source.protocol || '自动拉取' }}</span>
                <b>{{ datasourceCount(source.id) }}</b><span class="sc-unit">个</span>
                <span v-if="sourceTestResults[source.id]" class="sc-test">{{ sourceTestText(source) }}</span><button class="btn-link" type="button" :disabled="testingSourceIds.has(source.id)" @click="testSource(source)">{{ testingSourceIds.has(source.id) ? '测试中…' : '测试连接' }}</button>
              </span>
            </span>
            <button class="btn-primary" type="button" @click="sourceVisible = true"><span aria-hidden="true">＋</span> 登记数据源</button>
          </div>

          <div class="note">一个数据源 = 一个数据集管理池；池下的数据集进入下方列表。当前后端数据源仅登记元信息，实际数据集可通过“登记数据集”接入。</div>

          <div class="flowline" aria-label="数据集状态流转">
            <span class="ff">状态</span>
            <span class="fchip run">接入中</span><span class="farr">→</span>
            <span class="fchip run">评定中</span><span class="farr">→</span>
            <span class="fchip run">已入库</span><span class="farr">→</span>
            <span class="fchip run">建索引中</span><span class="farr">→</span>
            <span class="fchip ok">可检索</span>
            <span class="farr">／</span>
            <span class="fchip warn">卡住</span>
            <span class="fchip fail">已剔除</span>
          </div>

          <div class="dtable">
            <table class="dt">
              <thead>
                <tr><th>数据集</th><th>来源</th><th>当前状态</th><th>卡点</th><th>操作</th></tr>
              </thead>
              <tbody>
                <tr v-if="loading">
                  <td colspan="5" class="table-empty">加载中…</td>
                </tr>
                <tr v-else-if="!rows.length">
                  <td colspan="5" class="table-empty">暂无数据集</td>
                </tr>
                <tr v-for="row in rows" :key="row.id" :class="{ stuck: row.status === 'stuck' }">
                  <td>
                    <span class="ds-name mono">{{ row.bizNo }}</span>
                    <span class="ds-meta">{{ row.name }} · {{ row.itemCount ?? 0 }} 条</span>
                  </td>
                  <td>{{ datasourceName(row.datasourceId) }}</td>
                  <td><span class="badge" :class="statusClass(row.status)">{{ statusLabel(row.status) }}</span></td>
                  <td class="stk">{{ row.stuckReason || '—' }}</td>
                  <td>
                    <button v-if="row.status === 'stuck' && row.decision === 'suspend'" class="btn-mini act" type="button" @click="openAdjudicate(row)">裁决</button>
                    <button v-if="row.status === 'stuck' && row.decision !== 'suspend'" class="btn-mini act" type="button" @click="confirmProductionRow(row)">确认</button>
                    <button class="btn-mini view" type="button" @click="openDetail(row)">查看</button>
                    <button class="btn-mini" type="button" @click="rebuild(row)">重建</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="pager-bar">
            <button class="page-btn" type="button" :disabled="pageNum <= 1" @click="changePage(pageNum - 1)">上一页</button>
            <span>第 {{ pageNum }} 页 / 共 {{ Math.max(1, Math.ceil(total / pageSize)) }} 页 · {{ total }} 条</span>
            <button class="page-btn" type="button" :disabled="pageNum >= Math.ceil(total / pageSize)" @click="changePage(pageNum + 1)">下一页</button>
          </div>

          <div class="note">正常自动跑完到「可检索」，卡住才转人工：评定挂起 →「裁决」；投产召回率不足 →「确认」；治理异常 → 查看任务并重建。</div>
        </section>
      </div>
    </div>

    <div class="toast" :class="{ show: toastVisible, ok: toastType === 'ok', err: toastType === 'err' }">{{ toastMessage }}</div>

    <!-- 登记数据源 -->
    <div class="modal-mask" :class="{ show: sourceVisible }" @click.self="sourceVisible = false">
      <div class="modal" role="dialog" aria-modal="true">
        <div class="modal-head"><span class="sec-title">登记数据源</span><button class="modal-x" type="button" aria-label="关闭" @click="sourceVisible = false">×</button></div>
        <p class="modal-sub">登记要对接的数据集管理池，一个池子登记一次。</p>
        <form class="modal-body" @submit.prevent="submitSource">
          <div class="field"><label>数据源名称 *</label><input v-model.trim="sourceForm.name" maxlength="40" required placeholder="如：数据集管理-生产池"></div>
          <div class="field"><label>接口地址 *</label><input v-model.trim="sourceForm.endpoint" maxlength="120" required placeholder="https://ds.example/prod"></div>
          <div class="field-grid">
            <div class="field"><label>认证方式</label><select v-model="sourceForm.authType"><option value="token">Token</option><option value="none">无</option></select></div>
            <div class="field"><label>协议</label><select v-model="sourceForm.protocol"><option value="pull">自动拉取</option><option value="push">推送接收</option></select></div>
          </div>
          <div class="field"><label>认证信息</label><input v-model="sourceForm.authConfig" maxlength="200" placeholder="Token 或认证参数"></div>
          <div class="field"><label>描述</label><input v-model.trim="sourceForm.remark" maxlength="60" placeholder="生产环境数据集管理池"></div>
          <div class="modal-foot">
            <button class="btn-ghost" type="button" @click="sourceVisible = false">取消</button>
            <button class="btn-primary" type="submit" :disabled="sourceSubmitting">{{ sourceSubmitting ? '登记中…' : '登记数据源' }}</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 登记数据集 -->
    <div class="modal-mask" :class="{ show: datasetVisible }" @click.self="datasetVisible = false">
      <div class="modal" role="dialog" aria-modal="true">
        <div class="modal-head"><span class="sec-title">登记数据集</span><button class="modal-x" type="button" aria-label="关闭" @click="datasetVisible = false">×</button></div>
        <p class="modal-sub">上传 zip 或填写共享卷中的数据集路径，提交后触发 401→406 建库链。</p>
        <form class="modal-body" @submit.prevent="submitDataset">
          <div class="field"><label>名称 *</label><input v-model.trim="datasetForm.name" required maxlength="80"></div>
          <div class="field-grid">
            <div class="field"><label>场景</label><select v-model="datasetForm.scene"><option v-for="item in scenes" :key="item.alias" :value="item.alias">{{ item.label }}</option></select></div>
            <div class="field"><label>版本</label><input v-model.trim="datasetForm.version" maxlength="40" placeholder="v1"></div>
          </div>
          <div class="field"><label>数据集文件</label><input type="file" accept=".zip" @change="datasetFile = ($event.target as HTMLInputElement).files?.[0]"></div>
          <div class="field"><label>或数据集路径</label><input v-model.trim="datasetForm.datasetPath" placeholder="dataset/sample 或共享卷路径"></div>
          <div class="modal-foot"><button class="btn-ghost" type="button" @click="datasetVisible = false">取消</button><button class="btn-primary" type="submit" :disabled="datasetSubmitting">{{ datasetSubmitting ? '提交中…' : '触发建库' }}</button></div>
        </form>
      </div>
    </div>

    <!-- 裁决 -->
    <div class="modal-mask" :class="{ show: adjudicateVisible }" @click.self="adjudicateVisible = false">
      <div class="modal" role="dialog" aria-modal="true">
        <div class="modal-head"><span class="sec-title">评定挂起裁决</span><button class="modal-x" type="button" @click="adjudicateVisible = false">×</button></div>
        <form class="modal-body" @submit.prevent="submitAdjudicate(true)">
          <div class="note">{{ currentDataset?.stuckReason }}</div>
          <div class="field"><label>裁决说明</label><textarea v-model="adjudicateNote" placeholder="说明放行或剔除原因"></textarea></div>
          <div class="modal-foot">
            <button class="btn-ghost" type="button" @click="submitAdjudicate(false)">不通过并剔除</button>
            <button class="btn-primary" type="submit">通过并继续建库</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 详情 -->
    <div class="modal-mask detail-mask" :class="{ show: detailVisible }" @click.self="detailVisible = false">
      <div class="modal detail-modal" role="dialog" aria-modal="true">
        <div class="modal-head"><span class="sec-title">数据集详情</span><button class="modal-x" type="button" @click="detailVisible = false">×</button></div>
        <div v-if="detailLoading" class="modal-body"><div class="note">加载中…</div></div>
        <div v-else-if="detail" class="modal-body detail-body">
          <div class="detail-grid">
            <div><span>编号</span><b class="mono">{{ detail.dataset.bizNo }}</b></div>
            <div><span>状态</span><b>{{ statusLabel(detail.dataset.status) }}</b></div>
            <div><span>条目数</span><b>{{ detail.dataset.itemCount ?? 0 }}</b></div>
            <div><span>当前索引</span><b class="mono">{{ activeIndex?.indexDir || '—' }}</b></div>
          </div>
          <div class="sec-title small">索引历史</div>
          <div class="dtable"><table class="dt"><thead><tr><th>索引</th><th>模型</th><th>状态</th><th>激活时间</th><th>操作</th></tr></thead><tbody>
            <tr v-if="!detail.indexes.length"><td colspan="5" class="table-empty">暂无索引</td></tr>
            <tr v-for="index in detail.indexes" :key="index.id">
              <td class="mono">{{ index.indexDir }}</td><td>{{ index.modelVersion || '—' }}</td><td>{{ indexLabel(index.status) }}</td><td>{{ index.activatedTime || '—' }}</td>
              <td><button v-if="index.status !== 'active'" class="btn-mini act" type="button" @click="activate(index)">激活</button></td>
            </tr>
          </tbody></table></div>
          <div class="sec-title small">关联任务</div>
          <div class="dtable"><table class="dt"><thead><tr><th>任务</th><th>状态</th><th>当前步骤</th><th>错误</th><th>操作</th></tr></thead><tbody>
            <tr v-if="!detail.tasks.length"><td colspan="5" class="table-empty">暂无任务</td></tr>
            <tr v-for="task in detail.tasks" :key="task.id">
              <td class="mono">{{ task.bizNo }}</td><td>{{ task.status }}</td><td>{{ task.currentStep || '—' }}</td><td>{{ task.errorMsg || '—' }}</td>
              <td><router-link class="link" :to="`/tasks/${task.id}`">详情</router-link></td>
            </tr>
          </tbody></table></div>
        </div>
      </div>
    </div>

    <div class="dev-mask" :class="{ open: devOpen }" @click="closeDev"></div>
<div class="dev-drawer" :class="{ open: devOpen }" role="dialog" aria-modal="true" aria-label="研发需求说明">
  <div class="dev-head">
    <h3>研发需求说明</h3>
    <span class="dh-tag">给研发</span>
    <button type="button" class="dev-close" id="devClose" title="关闭（Esc）" aria-label="关闭" @click="closeDev">×</button>
  </div>
  <div class="dev-body dev-stack">
      <p class="help-lead">这 15 个 COSMIC 模块是<b>后台处理环节与研发交付依据</b>；正文只保留数据集状态、卡点与卡住处理。用户看到的每个状态，都是后台一组模块处理后的结果。</p>

      <div>
        <div class="hb-t">一、15 个模块与 F-CR 范围</div>
        <div class="dtable">
          <table class="dt help-table">
            <thead>
              <tr><th>模块</th><th>模块名</th><th>F-CR 范围</th><th>覆盖方式</th></tr>
            </thead>
            <tbody>
              <tr><td class="mod">1A</td><td>检索库内容质量管理</td><td class="rng">F-CR-001 ~ 003</td><td class="cov">状态「接入中」</td></tr>
              <tr><td class="mod">1B</td><td>检索库质量验收与接入确认</td><td class="rng">F-CR-008 ~ 012</td><td class="cov">状态「评定中」+ 卡住复核</td></tr>
              <tr><td class="mod">1C</td><td>检索库数据集接入条件评定</td><td class="rng">F-CR-016 ~ 018</td><td class="cov">状态「评定中」+ 裁决</td></tr>
              <tr><td class="mod">1D</td><td>检索库质量抽样核验</td><td class="rng">F-CR-044 ~ 046</td><td class="cov">状态「评定中」</td></tr>
              <tr><td class="mod">2A</td><td>检索库元数据治理</td><td class="rng">F-CR-013 ~ 015</td><td class="cov">状态「已入库」</td></tr>
              <tr><td class="mod">2B</td><td>检索索引构建质量管理</td><td class="rng">F-CR-004 ~ 007</td><td class="cov">状态「建索引中」</td></tr>
              <tr><td class="mod">2C</td><td>检索库投产验收确认</td><td class="rng">F-CR-047 ~ 050</td><td class="cov">状态「可检索」+ 确认</td></tr>
              <tr><td class="mod">2D</td><td>检索库增量更新管理</td><td class="rng">F-CR-019 ~ 025</td><td class="cov">状态「可检索」（增量）</td></tr>
              <tr><td class="mod">3A</td><td>检索库覆盖缺失治理</td><td class="rng">F-CR-029 ~ 031</td><td class="cov">状态「卡住」+ 处理</td></tr>
              <tr><td class="mod">3B</td><td>检索库最新内容维护</td><td class="rng">F-CR-032 ~ 034</td><td class="cov">状态「可检索」（增量）</td></tr>
              <tr><td class="mod">3C</td><td>检索库模态分布治理</td><td class="rng">F-CR-035 ~ 038</td><td class="cov">状态「卡住」+ 处理</td></tr>
              <tr><td class="mod">3D</td><td>检索索引质量恢复</td><td class="rng">F-CR-039 ~ 043</td><td class="cov">状态「卡住」+ 处理</td></tr>
              <tr><td class="mod">3E</td><td>检索索引内容影响评估</td><td class="rng">F-CR-026 ~ 028</td><td class="cov">状态「卡住」+ 处理</td></tr>
              <tr><td class="mod">3F</td><td>检索库容量评估与扩容规划</td><td class="rng">F-CR-051 ~ 052</td><td class="cov">状态「可检索」（容量）</td></tr>
              <tr><td class="mod">3G</td><td>检索库失效内容与质量问题处理</td><td class="rng">F-CR-053 ~ 056</td><td class="cov">状态「卡住」+ 处理</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <div>
        <div class="hb-t">二、页面与 COSMIC 的映射</div>
        <table class="map-table">
          <thead>
            <tr><th>页面元素（用户可见）</th><th>承载方式</th><th>COSMIC 模块</th></tr>
          </thead>
          <tbody>
            <tr><td>数据源登记（输入边界）</td><td>非 COSMIC 模块；登记数据集管理池，为接入提供目录来源</td><td class="mk">—（1A 的输入）</td></tr>
            <tr><td>状态「接入中」</td><td>状态流转，自动</td><td class="mk">1A</td></tr>
            <tr><td>状态「评定中」</td><td>状态流转，自动</td><td class="mk">1B / 1C / 1D</td></tr>
            <tr><td>状态「已入库」</td><td>状态流转，自动</td><td class="mk">2A</td></tr>
            <tr><td>状态「建索引中」</td><td>状态流转，自动</td><td class="mk">2B</td></tr>
            <tr><td>状态「可检索」</td><td>状态流转；投产校验跳「策略管理」</td><td class="mk">2C / 2D</td></tr>
            <tr><td>状态「卡住」</td><td>转人工：裁决 / 确认 / 处理</td><td class="mk">1C · 2C · 3A–3G</td></tr>
            <tr><td>状态「已剔除」</td><td>评定结论，自动</td><td class="mk">1C</td></tr>
            <tr><td>「? 研发说明」</td><td>本面板，仅研发 / 产品查看</td><td class="mk">15 个模块的交付依据</td></tr>
          </tbody>
        </table>
      </div>

      <p class="help-note"><b>三、覆盖说明：</b>本页 F-CR-001 ~ 056 共 56 个功能过程，全部落在 15 个后台模块；正文不出现模块编号与 F-CR，只以 7 个数据集状态 + 卡住处理呈现。正常数据集由状态自动推进，只有卡住时才出现人工动作（裁决 / 确认 / 处理），因此功能过程由状态流转与卡住处理覆盖。</p>
  </div>
</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  activateIndex,
  adjudicateDataset,
  confirmProduction,
  createDataset,
  createDatasource,
  datasetDetail,
  listDatasources,
  pageDatasets,
  testDatasource,
  rebuildDataset,
} from '../../api'
import type { DatasetDetailVO, DatasetVO, DatasourceTestVO, DatasourceVO, IndexVO, TaskVO } from '../../api/types'
import { useDictStore } from '../../stores/dict'

const router = useRouter()
const dictStore = useDictStore()
const rows = ref<DatasetVO[]>([])
const lastBuildTask = ref<TaskVO | null>(null)
const datasources = ref<DatasourceVO[]>([])
const detail = ref<DatasetDetailVO | null>(null)
const currentDataset = ref<DatasetVO | null>(null)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const statusFilter = ref('')
const statusDict = ref<{ alias: string; label: string }[]>([])
const scenes = ref<{ alias: string; label: string }[]>([])
const loading = ref(false)
const detailLoading = ref(false)

const sourceVisible = ref(false)
const sourceSubmitting = ref(false)
const testingSourceIds = ref(new Set<number>())
const sourceTestResults = ref<Record<number, DatasourceTestVO>>({})
const datasetVisible = ref(false)
const datasetSubmitting = ref(false)
const adjudicateVisible = ref(false)
const detailVisible = ref(false)
const adjudicateNote = ref('')
const datasetFile = ref<File | undefined>()
const sourceForm = reactive({ name: '', endpoint: '', authType: 'token', authConfig: '', protocol: 'pull', remark: '' })
const datasetForm = reactive({ name: '', scene: 'general', version: '', datasetPath: '' })

const devOpen = ref(false)
const toastVisible = ref(false)
const toastMessage = ref('')
const toastType = ref<'info' | 'ok' | 'err'>('info')
let toastTimer: ReturnType<typeof setTimeout> | undefined

const activeIndex = computed(() => detail.value?.indexes.find(item => item.status === 'active'))

onMounted(async () => {
  statusDict.value = await dictStore.ensure('cmr_dataset_status')
  scenes.value = await dictStore.ensure('cmr_scene')
  if (scenes.value[0]) datasetForm.scene = scenes.value[0].alias
  await Promise.all([reload(false), loadDatasources()])
})

async function reload(showMessage = true) {
  loading.value = true
  try {
    const page = await pageDatasets({ pageNum: pageNum.value, pageSize: pageSize.value, status: statusFilter.value, name: keyword.value })
    rows.value = page.records
    total.value = page.total
    if (showMessage) showToast('数据集列表已刷新', 'ok')
  } catch (error) {
    showToast((error as Error).message, 'err')
  } finally { loading.value = false }
}

async function loadDatasources() {
  datasources.value = await listDatasources()
}

function changePage(page: number) { pageNum.value = page; void reload(false) }

function datasourceName(id: number | null) {
  if (!id) return '手动登记'
  return datasources.value.find(item => item.id === id)?.name || '未知数据源'
}

function datasourceCount(id: number) {
  return rows.value.filter(item => item.datasourceId === id).length
}

function statusLabel(status: string) {
  return statusDict.value.find(item => item.alias === status)?.label || status
}

function statusClass(status: string) {
  return ({ ingest: 'run', scoring: 'run', stored: 'run', indexing: 'run', searchable: 'ok', stuck: 'warn', rejected: 'fail' } as Record<string, string>)[status] || 'run'
}

function indexLabel(status: string) {
  return ({ candidate: '候选', active: '生效', retired: '已退役' } as Record<string, string>)[status] || status
}

async function testSource(source: DatasourceVO) {
  if (testingSourceIds.value.has(source.id)) return
  testingSourceIds.value = new Set([...testingSourceIds.value, source.id])
  try {
    const result = await testDatasource(source.id)
    sourceTestResults.value = { ...sourceTestResults.value, [source.id]: result }
    showToast(sourceTestMessage(source, result), result.connected === null ? 'info' : result.connected ? 'ok' : 'err')
  } catch (error) {
    showToast((error as Error).message, 'err')
  } finally {
    const next = new Set(testingSourceIds.value)
    next.delete(source.id)
    testingSourceIds.value = next
  }
}

function sourceTestResult(source: DatasourceVO): DatasourceTestVO | null {
  const current = sourceTestResults.value[source.id]
  if (current) return current
  if (!source.lastTestTime) return null
  return {
    connected: source.lastTestConnected,
    statusCode: source.lastTestStatusCode,
    latencyMs: source.lastTestLatencyMs,
    message: source.lastTestMessage || '最近测试结果未记录说明',
    testedAt: source.lastTestTime,
  }
}

function sourceDotClass(source: DatasourceVO) {
  const result = sourceTestResult(source)
  if (!result) return ''
  if (result.connected === null) return 'wait'
  return result.connected ? 'on' : 'off'
}

function sourceTestText(source: DatasourceVO) {
  const result = sourceTestResult(source)
  if (!result) return '未测试'
  if (result.connected === null) return '待推送验证'
  return result.connected ? '连接成功' : '连接失败'
}

function sourceTestMessage(source: DatasourceVO, result?: DatasourceTestVO | null) {
  const current = result || sourceTestResult(source)
  if (!current) return `${source.name} 尚未测试`
  const parts = [`${source.name} ${sourceTestText(source)}`]
  if (current.statusCode != null) parts.push(`HTTP ${current.statusCode}`)
  if (current.latencyMs != null) parts.push(`${current.latencyMs}ms`)
  if (current.testedAt) parts.push(new Date(current.testedAt).toLocaleString())
  parts.push(current.message)
  return parts.join(' · ')
}

function sourceTitle(source: DatasourceVO) {
  const base = `${source.endpoint} · ${source.protocol || '自动拉取'} · ${source.authType || 'none'}`
  const result = sourceTestResult(source)
  return result ? `${base} · ${sourceTestMessage(source, result)}` : base
}

async function submitSource() {
  sourceSubmitting.value = true
  try {
    await createDatasource({ ...sourceForm })
    sourceVisible.value = false
    sourceForm.name = ''; sourceForm.endpoint = ''; sourceForm.authConfig = ''; sourceForm.remark = ''
    await loadDatasources()
    showToast('数据源已登记', 'ok')
  } catch (error) { showToast((error as Error).message, 'err') }
  finally { sourceSubmitting.value = false }
}

async function submitDataset() {
  if (!datasetFile.value && !datasetForm.datasetPath) {
    showToast('请选择 zip 文件或填写数据集路径', 'err')
    return
  }
  datasetSubmitting.value = true
  try {
    const form = new FormData()
    form.append('name', datasetForm.name)
    form.append('scene', datasetForm.scene)
    if (datasetForm.version) form.append('version', datasetForm.version)
    if (datasetFile.value) form.append('file', datasetFile.value)
    else form.append('datasetPath', datasetForm.datasetPath)
    const created = await createDataset(form)
    datasetVisible.value = false
    datasetForm.name = ''; datasetForm.version = ''; datasetForm.datasetPath = ''; datasetFile.value = undefined
    await reload(false)
    const detail = await datasetDetail(created.id)
    lastBuildTask.value = detail.tasks.find(task => task.type === 'build') || null
    showToast(lastBuildTask.value ? `建库任务已启动：${lastBuildTask.value.bizNo}` : `数据集已登记：${created.bizNo}`, 'ok')
  } catch (error) { showToast((error as Error).message, 'err') }
  finally { datasetSubmitting.value = false }
}

function openAdjudicate(row: DatasetVO) { currentDataset.value = row; adjudicateNote.value = ''; adjudicateVisible.value = true }

async function submitAdjudicate(pass: boolean) {
  if (!currentDataset.value) return
  try {
    await adjudicateDataset(currentDataset.value.id, pass, adjudicateNote.value)
    adjudicateVisible.value = false
    await reload(false)
    showToast(pass ? '已通过评定，继续建库' : '已剔除数据集', 'ok')
  } catch (error) { showToast((error as Error).message, 'err') }
}

async function confirmProductionRow(row: DatasetVO) {
  try { await confirmProduction(row.id); await reload(false); showToast('已确认投产', 'ok') }
  catch (error) { showToast((error as Error).message, 'err') }
}

async function rebuild(row: DatasetVO) {
  try {
    const task = await rebuildDataset(row.id)
    lastBuildTask.value = task
    showToast(`重建任务已启动：${task.bizNo}`, 'ok')
  } catch (error) { showToast((error as Error).message, 'err') }
}

async function openDetail(row: DatasetVO) {
  detailVisible.value = true; detailLoading.value = true
  try { detail.value = await datasetDetail(row.id) }
  catch (error) { detailVisible.value = false; showToast((error as Error).message, 'err') }
  finally { detailLoading.value = false }
}

async function activate(index: IndexVO) {
  try { await activateIndex(index.id); if (detail.value) await openDetail(detail.value.dataset); showToast('索引已激活', 'ok') }
  catch (error) { showToast((error as Error).message, 'err') }
}

function showToast(message: string, type: 'info' | 'ok' | 'err' = 'info') {
  toastMessage.value = message; toastType.value = type; toastVisible.value = true
  clearTimeout(toastTimer); toastTimer = setTimeout(() => { toastVisible.value = false }, 2600)
}
function openDev() { devOpen.value = true }
function closeDev() { devOpen.value = false }
</script>

<style scoped>
.p4-filter { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.p4-filter select, .p4-filter input { height: 30px; border: 1px solid #d9e7f8; border-radius: 6px; padding: 0 9px; background: #fff; color: #344054; outline: none; }
.p4-filter input { width: 210px; }
.ph-actions { display: flex; align-items: center; gap: 8px; }
.src-empty { color: #98a2b3; font-size: 12px; }
.table-empty { text-align: center; color: #98a2b3; padding: 28px 0 !important; }
.page-btn { border: 1px solid #d9e7f8; background: #fff; border-radius: 6px; height: 26px; padding: 0 10px; color: #475467; cursor: pointer; }
.page-btn:disabled { opacity: .45; cursor: not-allowed; }
.detail-modal { max-width: 920px !important; width: 92% !important; }
.detail-body { overflow: auto; max-height: 72vh; }
.detail-grid { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 10px; margin-bottom: 16px; }
.detail-grid div { border: 1px solid #eef2f8; border-radius: 8px; padding: 9px; background: #fbfdff; }
.detail-grid span { display: block; color: #98a2b3; font-size: 11px; margin-bottom: 3px; }
.sec-title.small { margin: 14px 0 8px; font-size: 13px; }
:root{
  /* 冷白蓝 token（与 P1 定稿基准一致） */
  --workspace-bg:#f6f9fe;
  --content-soft:#fbfdff;
  --content-line:#e6edf8;
  --system-blue:#4169f6;
  --system-blue-weak:#f1f5ff;
  --ink:#1f2937;
  --muted:#667085;
  --success:#34a853;
  --warning:#f0a020;
  --error:#e5484d;
  --text-primary:#262626;
  --radius:8px;
}
*{margin:0;padding:0;box-sizing:border-box}
html,body{height:100%}
body{
  font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif;
  background:var(--workspace-bg);color:var(--text-primary);font-size:14px;line-height:1.5;display:flex;
}
button{font-family:inherit;cursor:pointer}
::selection{background:rgba(65,105,246,.16)}

/* ============ 左侧全局导航（与 P1 同壳，不改） ============ */
.sidebar{width:220px;flex-shrink:0;background:#fff;border-right:1px solid var(--content-line);display:flex;flex-direction:column;height:100vh;position:sticky;top:0}
.nav-scroll{flex:1;overflow-y:auto;padding:10px 12px}
.nav-item{display:flex;align-items:center;gap:9px;padding:9px 10px;margin-bottom:2px;border-radius:6px;color:#344054;font-size:13px;cursor:pointer;user-select:none;text-decoration:none}
.nav-item .ico{width:18px;height:18px;display:flex;align-items:center;justify-content:center;color:#8a96ad;flex-shrink:0}
.nav-item .ico svg{width:16px;height:16px;display:block}
.nav-item:hover{background:var(--system-blue-weak);color:var(--system-blue)}
.nav-item.active{background:var(--system-blue-weak);color:var(--system-blue);font-weight:600;box-shadow:inset 2px 0 0 var(--system-blue)}
.nav-item.active .ico{color:var(--system-blue)}
.nav-divider{height:1px;background:var(--content-line);margin:8px 10px}

/* ============ 主区 ============ */
.main{flex:1;min-width:0;height:100vh;overflow-y:auto}
.content{padding:18px 24px 40px;display:flex;flex-direction:column;gap:14px;max-width:1180px;width:100%;margin:0 auto}
.page-head{
  display:flex;align-items:flex-start;justify-content:space-between;gap:16px;flex-wrap:wrap;
  padding:18px 20px;
  border:1px solid #e4edf8;
  border-radius:10px;
  background:#fff;
}
.ph-main{min-width:0}
.page-head h1{font-size:20px;font-weight:700;color:#111827;line-height:1.3}
.page-head p{font-size:13px;color:#667085;line-height:1.65;margin-top:5px;max-width:760px}

/* ===== 研发说明入口（九页统一：头部胶囊按钮 + 右侧抽屉） ===== */
.dev-entry{flex-shrink:0;display:inline-flex;align-items:center;gap:7px;height:30px;padding:0 13px 0 5px;border:1px solid #d7e0f0;border-radius:999px;background:#fff;color:#667085;font-size:12px;line-height:1;white-space:nowrap;cursor:pointer;transition:border-color .15s,color .15s,background .15s}
.dev-entry:hover{border-color:#b9c9ff;background:var(--system-blue-weak);color:var(--system-blue)}
.dev-entry:hover .de-q{background:var(--system-blue);color:#fff}
.dev-entry:focus-visible{outline:2px solid #cdd9ff;outline-offset:1px}
.dev-entry .de-q{width:20px;height:20px;border-radius:50%;flex:0 0 auto;background:var(--system-blue-weak);color:var(--system-blue);font-size:12px;font-weight:700;line-height:20px;text-align:center;transition:background .15s,color .15s}
/* 右侧抽屉：520px / 全高 / 从右滑入 + 半透明遮罩 */
.dev-mask{position:fixed;inset:0;background:rgba(15,23,42,.45);z-index:10000;opacity:0;pointer-events:none;transition:opacity .22s}
.dev-mask.open{opacity:1;pointer-events:auto}
.dev-drawer{position:fixed;top:0;right:0;width:720px;max-width:100vw;height:100vh;background:#f6f8fc;z-index:10001;display:flex;flex-direction:column;transform:translateX(102%);transition:transform .28s ease;box-shadow:-8px 0 30px rgba(15,23,42,.18)}
.dev-drawer.open{transform:translateX(0)}
.dev-head{display:flex;align-items:center;gap:10px;padding:14px 18px;background:#fff;border-bottom:1px solid #e4eaf3;flex:0 0 auto}
.dev-head h3{margin:0;font-size:15px;color:#1f2937}
.dev-head .dh-tag{font-size:11px;font-weight:600;color:#b54708;background:#fdf3e6;border-radius:4px;padding:2px 8px}
.dev-close{margin-left:auto;width:28px;height:28px;border:none;background:#f2f4f8;border-radius:6px;font-size:15px;line-height:1;cursor:pointer;color:#475467;flex:0 0 auto}
.dev-close:hover{background:#e6eaf2}
.dev-body{flex:1;min-height:0;overflow:auto;padding:16px 18px 36px}
.dev-stack{display:flex;flex-direction:column;gap:12px}

/* 面板 */
.panel{padding:14px 16px;border:1px solid #e4edf8;border-radius:var(--radius);background:#fff;display:flex;flex-direction:column;gap:10px}
.panel-head{display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap}
.sec-head{display:flex;align-items:center;gap:8px;flex-wrap:wrap;min-width:0}
.sec-title{font-size:15px;font-weight:700;color:#1f2a44;white-space:nowrap}
.sec-sub{font-size:11.5px;color:#9aa6bd}
.view-badge{font-size:10.5px;font-weight:700;color:var(--system-blue);background:var(--system-blue-weak);border:1px solid #d7e0ff;border-radius:4px;padding:1px 6px;white-space:nowrap}
.hint{font-size:11.5px;color:#8a96ad;font-variant-numeric:tabular-nums}
.note{font-size:11.5px;color:#8a96ad;line-height:1.7;padding:0 2px}

/* 输入边界：数据源小条 */
.src-strip{display:flex;align-items:center;gap:8px;flex-wrap:wrap;border:1px dashed #cdd9f5;border-radius:7px;background:var(--system-blue-weak);padding:6px 9px}
.ib-k{flex-shrink:0;font-size:10.5px;font-weight:700;color:var(--system-blue);background:#fff;border:1px solid #d7e0ff;border-radius:4px;padding:1px 6px;white-space:nowrap}
.src-chips{display:flex;flex-wrap:wrap;gap:6px;align-items:center;min-width:0}
.src-chip{display:inline-flex;align-items:center;gap:5px;background:#fff;border:1px solid #dbe4f5;border-radius:999px;padding:2px 4px 2px 9px;font-size:11.5px;color:#344054;max-width:100%}
.src-chip .dot{width:7px;height:7px;border-radius:50%;background:#c8cfdd;flex-shrink:0}
.src-chip .dot.on{background:var(--success)}
.src-chip .dot.off{background:var(--danger,#e5484d)}
.src-chip .dot.wait{background:#f0b323}
.sc-test{color:#8a96ad;font-size:10.5px;white-space:nowrap}
.sc-name{font-weight:600;color:#273244;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.sc-proto{color:#8a96ad;font-size:10.5px;white-space:nowrap}
.src-chip b{font-variant-numeric:tabular-nums;color:#1f2a44;font-weight:700}
.sc-unit{color:#8a96ad;font-size:10.5px}
.btn-link{border:0;border-left:1px solid #e6edf8;background:transparent;color:var(--system-blue);font-size:11px;font-weight:600;padding:0 6px;margin-left:2px;white-space:nowrap}
.btn-link:hover{text-decoration:underline}
.btn-link:disabled{color:#9aa6bd;cursor:default}
.task-entry{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-top:6px;font-size:11.5px;color:#667085}
.task-entry b{color:#1f2a44}

/* 状态流转说明条 */
.flowline{display:flex;flex-wrap:wrap;align-items:center;gap:4px 5px;border:1px dashed #e2e9f5;border-radius:6px;background:#f8fbff;padding:5px 9px;font-size:11px;color:#8a96ad;line-height:1.6}
.ff{font-weight:700;color:#6d7a92;margin-right:2px}
.fchip{font-size:10.5px;font-weight:600;border-radius:4px;padding:0 6px;border:1px solid #e6edf8;background:#fff;color:#475467;white-space:nowrap}
.fchip.run{color:#335be8;border-color:#d7e0ff;background:#f5f8ff}
.fchip.ok{color:#1e7f4b;border-color:#c9ecd7;background:#f3fbf6}
.fchip.warn{color:#a9741a;border-color:#f0d9a8;background:#fffaf0}
.fchip.fail{color:#c0392b;border-color:#f6cdcd;background:#fdf5f5}
.farr{color:#b6c1d6}

/* 数据表 */
.dtable{border:1px solid #eef2f8;border-radius:8px;overflow-x:auto}
table.dt{width:100%;min-width:640px;border-collapse:collapse;font-size:12.5px}
table.dt th{background:#f4f6fb;color:#7a8493;font-weight:600;font-size:11px;text-align:left;padding:8px 11px;border-bottom:1px solid #e6edf8;white-space:nowrap}
table.dt td{padding:7px 11px;border-bottom:1px solid #eef2f8;color:#344054;vertical-align:middle}
table.dt tbody tr:last-child td{border-bottom:0}
table.dt tbody tr:hover td{background:#f8fbff}
table.dt tbody tr.stuck td{background:#fffdf7}
table.dt tbody tr.stuck td:first-child{box-shadow:inset 3px 0 0 var(--warning)}
table.dt tbody tr.stuck:hover td{background:#fff9ee}

/* ================= 分页组件（表格下方 · 右对齐） ================= */
.pager-bar{display:flex;align-items:center;justify-content:flex-end;gap:6px;flex-wrap:wrap;margin-top:10px;font-size:12px;color:#667085}
.pager-total{color:#8a96ad;margin-right:4px;font-variant-numeric:tabular-nums}
.pager-btn,.pager-num{height:28px;border:1px solid #d7e0f0;background:#fff;border-radius:5px;color:#475467;font-size:12px;cursor:pointer;font-family:inherit}
.pager-btn{padding:0 10px}
.pager-num{min-width:28px;padding:0 6px;font-variant-numeric:tabular-nums}
.pager-btn:hover:not(:disabled),.pager-num:hover:not(.is-cur){border-color:#4169f6;color:#4169f6}
.pager-btn:disabled{opacity:.45;cursor:not-allowed}
.pager-num.is-cur{background:#4169f6;border-color:#4169f6;color:#fff;font-weight:700;cursor:default}
.pager-gap{color:#98a2b3;padding:0 2px}

.mono{font-family:ui-monospace,Consolas,"Courier New",monospace;font-variant-numeric:tabular-nums;word-break:break-all}
.ds-name{font-size:12px;font-weight:600;color:#273244;line-height:1.5}
.ds-meta{font-size:10.5px;color:#9aa6bd;margin-left:6px;line-height:1.5}
.stk{color:#a9741a;font-weight:600}
.cell-mute{color:#aab4c8}
a.link{color:var(--system-blue);text-decoration:none;font-weight:600}
a.link:hover{text-decoration:underline}
.row-flash td{animation:rowFlash 1s ease-out}
@keyframes rowFlash{from{background:#e3ecff}to{background:transparent}}

/* 按钮 */
.btn-primary{height:30px;padding:0 14px;border:0;border-radius:6px;background:var(--system-blue);color:#fff;font-size:12.5px;font-weight:600;display:inline-flex;align-items:center;gap:5px;box-shadow:0 2px 6px rgba(65,105,246,.2);transition:all .15s}
.btn-primary:hover{background:#335be8}
.btn-primary:disabled{opacity:.6;cursor:not-allowed}
.btn-mini{height:24px;padding:0 10px;border:1px solid #d7e0f0;border-radius:5px;background:#fff;color:var(--system-blue);font-size:11.5px;font-weight:600;white-space:nowrap}
.btn-mini:hover{border-color:#b9c9ff;background:var(--system-blue-weak)}
.btn-mini.act{border-color:#f0d9a8;background:#fffaf0;color:#a9741a}
.btn-mini.act:hover{border-color:#e8c987;background:#fdf3e1}
.btn-mini.view{color:#667085;border-color:#e6edf8}
.btn-ghost{height:32px;padding:0 16px;border:1px solid #d7e0f0;border-radius:6px;background:#fff;color:#475467;font-size:13px;font-weight:600}
.btn-ghost:hover{border-color:#b9c9ff;background:var(--system-blue-weak);color:var(--system-blue)}

/* 徽章 */
.badge{font-size:10.5px;padding:1px 7px;border-radius:9px;font-weight:600;display:inline-block;white-space:nowrap;line-height:1.7}
.badge.ok{background:#e8f7ec;color:var(--success)}
.badge.fail{background:#fdecec;color:var(--error)}
.badge.run{background:#e9f0ff;color:var(--system-blue)}
.badge.warn{background:#fdf3e1;color:#c98a12}
.badge.pend{background:#f0f2f6;color:#667085}

/* 弹层 */
.modal-mask{position:fixed;inset:0;z-index:180;background:rgba(20,32,70,.36);display:none;align-items:flex-start;justify-content:center;padding:44px 16px 24px;overflow-y:auto}
.modal-mask.open{display:flex}
.modal{width:100%;max-width:720px;background:#fff;border:1px solid #e4edf8;border-radius:12px;box-shadow:0 20px 50px rgba(20,32,70,.26);overflow:hidden;animation:modalIn .16s ease-out}
.modal.wide{max-width:840px}
@keyframes modalIn{from{opacity:0;transform:translateY(-8px)}to{opacity:1;transform:none}}
.modal-head{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:13px 16px;border-bottom:1px solid #eef2f8;background:#fbfdff}
.modal-x{width:26px;height:26px;flex-shrink:0;border:0;border-radius:6px;background:transparent;color:#8a96ad;font-size:18px;line-height:1}
.modal-x:hover{background:#eef2fb;color:#475467}
.modal-sub{font-size:11.5px;color:#667085;line-height:1.7;padding:10px 16px 0}
.modal-body{display:flex;flex-direction:column;gap:11px;padding:12px 16px 16px;max-height:72vh;overflow-y:auto}
.modal-foot{display:flex;justify-content:flex-end;gap:8px;border-top:1px dashed #eef2f8;padding-top:12px;margin-top:2px}
.field{display:flex;flex-direction:column;gap:5px;min-width:0}
.field > label{font-size:12px;font-weight:600;color:#475467}
.field .req{color:var(--error);font-weight:700}
.field .opt{font-size:10.5px;font-weight:400;color:#9aa6bd}
.field input,.field select{height:32px;width:100%;border:1px solid #cfd8e8;border-radius:6px;padding:0 9px;font-size:12.5px;background:#fff;color:#1f2a44;outline:none;font-family:inherit}
.field input:focus,.field select:focus{border-color:#8fa3ff;box-shadow:0 0 0 2px rgba(65,105,246,.12)}
.field input::placeholder{color:#b3bccd}
.field-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.modal-note{font-size:11px;color:#8a6a1f;background:#fdf8ee;border:1px dashed #f0d9a8;border-radius:6px;padding:6px 9px;line-height:1.65}

/* 研发说明内容 */
.help-lead{font-size:12.5px;color:#475467;line-height:1.75;background:var(--system-blue-weak);border:1px dashed #cdd9f5;border-radius:8px;padding:9px 12px}
.help-lead b{color:#1f2a44;font-weight:700}
.hb-t{display:flex;align-items:center;gap:7px;font-size:12.5px;font-weight:700;color:#1f2a44;margin-bottom:6px}
.hb-t::before{content:"";width:3px;height:12px;border-radius:2px;background:var(--system-blue)}
.help-table{min-width:0}
.help-table th{font-size:10.5px;padding:6px 9px}
.help-table td{padding:5px 9px;font-size:11.5px;line-height:1.55}
.help-table td.mod{font-weight:700;color:var(--system-blue);white-space:nowrap;font-variant-numeric:tabular-nums}
.help-table td.rng{font-family:ui-monospace,Consolas,"Courier New",monospace;font-size:10.5px;color:#5b6472;white-space:nowrap}
.help-table td.cov{color:#1e7f4b;font-weight:600}
.map-table{width:100%;border-collapse:collapse;font-size:11.5px}
.map-table th{background:#f4f6fb;color:#7a8493;font-weight:600;font-size:10.5px;text-align:left;padding:6px 9px;border:1px solid #e6edf8;white-space:nowrap}
.map-table td{padding:5px 9px;border:1px solid #eef2f8;color:#475467;line-height:1.55;vertical-align:top}
.map-table td.mk{font-family:ui-monospace,Consolas,"Courier New",monospace;font-size:10.5px;color:#5b6472;white-space:nowrap}
.help-note{font-size:11.5px;color:#475467;line-height:1.75;border-top:1px dashed #eef2f8;padding-top:9px}
.help-note b{color:#1f2a44;font-weight:700}

/* 全局消息 */
.toast{position:fixed;top:56px;left:50%;transform:translateX(-50%) translateY(-12px);z-index:200;background:#2b3245;color:#fff;font-size:13px;padding:9px 18px;border-radius:7px;box-shadow:0 8px 24px rgba(20,32,70,.25);opacity:0;pointer-events:none;transition:all .2s;max-width:80vw}
.toast.show{opacity:1;transform:translateX(-50%) translateY(0)}
.toast.err{background:#c0392b}
.toast.ok{background:#1e8e4a}

@media (max-width:1000px){
  .content{padding:14px 14px 28px}
  .page-head{flex-direction:column;gap:8px}
}
@media (max-width:560px){
  .field-grid{grid-template-columns:1fr}
}
</style>
