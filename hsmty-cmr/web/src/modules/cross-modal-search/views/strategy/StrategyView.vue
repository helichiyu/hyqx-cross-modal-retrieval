<template>
  <div class="page-card">
    <header class="cm-page-header">
      <div>
        <h1 class="cm-page-title">策略管理</h1>
        <p class="cm-page-subtitle">管理检索策略的参数（向量 / 召回与融合 / 重排），以及发布、变更评估与版本回滚。</p>
      </div>
      <button class="dev-entry" type="button" title="研发说明" @click="devOpen = true">
        <span class="de-q" aria-hidden="true">?</span>研发说明
      </button>
    </header>

    <section class="cm-panel panel-pad" aria-label="策略列表">
      <div class="cm-panel-head">
        <h3 class="cm-panel-title">策略列表</h3>
        <div class="cm-flex">
          <el-button size="small" @click="openConsistency">一致性检查</el-button>
          <el-button size="small" type="primary" @click="openNewStrategy">＋ 新建策略</el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        size="small"
        style="width: 100%"
        :row-class-name="rowClassName"
        @row-click="openEditor"
      >
        <el-table-column label="策略 ID" width="164">
          <template #default="{ row }">
            <span class="mono">{{ row.bizNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="名称" min-width="200">
          <template #default="{ row }">
            <div class="name-cell">
              <span class="name-text" :title="row.name">{{ row.name }}</span>
              <span v-if="row.preset" class="tag-preset">系统预置</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="80">
          <template #default="{ row }">
            <span class="num-cell">{{ row.currentVersion ? `v${row.currentVersion}` : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <span class="badge" :class="statusMeta(row.status).cls">{{ statusMeta(row.status).label }}</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">
            <span class="time-cell">{{ fmtTime(row.updateTime || row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="252" fixed="right">
          <template #default="{ row }">
            <div class="row-acts">
              <el-button link type="primary" @click.stop="openEditor(row)">编辑</el-button>
              <el-button
                link
                type="primary"
                :disabled="!row.hasDraft"
                :title="row.hasDraft ? '发布当前草稿' : '没有待发布草稿，请先编辑保存'"
                @click.stop="requestPublish(row)"
              >发布</el-button>
              <el-button link type="primary" @click.stop="openVersions(row)">版本历史</el-button>
              <el-button link type="primary" @click.stop="openCompare(row)">对比</el-button>
              <el-button
                v-if="row.status === 'active'"
                link
                type="danger"
                :disabled="row.preset"
                :title="row.preset ? '系统预置策略不可下线' : '下线后检索台不再出现该策略'"
                @click.stop="requestOffline(row)"
              >下线</el-button>
              <el-button
                v-else-if="row.status === 'offline'"
                link
                type="primary"
                @click.stop="requestOnline(row)"
              >上线</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <div class="cm-empty table-empty">
            <div class="cm-empty-title">暂无策略</div>
            <el-button size="small" type="primary" @click="openNewStrategy">＋ 新建策略</el-button>
          </div>
        </template>
      </el-table>

      <el-pagination
        class="cm-pagination"
        layout="total, prev, pager, next"
        :total="total"
        :current-page="pageNum"
        :page-size="PAGE_SIZE"
        @current-change="changePage"
      />
    </section>

    <!-- 策略编辑弹窗：基本信息 / 向量 / 召回与融合 / 重排 -->
    <el-dialog
      v-model="editorOpen"
      :title="editingId ? '编辑策略' : '新建策略'"
      width="900px"
      class="stg-dialog"
      :close-on-click-modal="false"
    >
      <div class="ed-body">
        <section class="ed-sec">
          <div class="ed-sec-head">
            <span class="ed-sec-title">基本信息</span>
            <span v-if="editorPreset" class="tag-preset">系统预置</span>
          </div>
          <el-form label-width="92px" class="basic-form">
            <el-form-item label="策略名称" required>
              <el-input v-model="editor.name" maxlength="80" placeholder="请输入策略名称" />
            </el-form-item>
          </el-form>
          <div class="meta-row">
            <div class="meta-item"><span class="meta-k">版本</span><b>{{ editorVersion }}</b></div>
            <div class="meta-item"><span class="meta-k">状态</span><b>{{ editorStatus }}</b></div>
          </div>
        </section>

        <section class="ed-sec">
          <div class="ed-sec-head">
            <span class="ed-sec-title">向量参数</span>
          </div>
          <div class="ed-line">
            <span class="ed-label">统一向量空间</span>
            <el-input :model-value="editorModel" class="model-input mono" disabled />
            <span class="ed-fixed">统一检索固定一套向量空间，不可切换</span>
          </div>
        </section>

        <section class="ed-sec">
          <div class="ed-sec-head">
            <span class="ed-sec-title">召回与融合</span>
            <button
              class="ed-q"
              type="button"
              :aria-expanded="recallHelpOpen ? 'true' : 'false'"
              aria-controls="recallHelp"
              title="召回与融合参数说明"
              @click="recallHelpOpen = !recallHelpOpen"
            >?</button>
          </div>
          <div v-show="recallHelpOpen" id="recallHelp" class="ed-help">
            <p><b>相似度阈值：</b>低于此值的条目不返回。调高更准更少，调低更全更杂。</p>
            <p><b>融合权重：</b>只对含多种表征的条目生效（视频的画面 / 音频 / 字幕）；图片与文档为单表征，不参与加权。</p>
            <p><b>结果条数：</b>由相似度阈值决定，结果分页展示。</p>
          </div>
          <div class="rc-line">
            <span class="ed-label">相似度阈值</span>
            <el-input-number
              v-model="editor.threshold"
              class="rc-input"
              :min="0.3"
              :max="0.99"
              :step="0.05"
              :precision="2"
              :controls="false"
              inputmode="decimal"
            />
            <span class="ed-fixed">低于此值的条目不返回</span>
          </div>
          <div class="fuse-head">
            <span class="fuse-title">融合权重</span>
            <span class="ed-fixed">只对含多种表征的条目生效（视频的画面 / 音频 / 字幕）；图片与文档为单表征，不参与加权。</span>
          </div>
          <div class="fuse-wrap">
            <div v-for="field in WEIGHT_FIELDS" :key="field.key" class="fslider">
              <span>{{ field.label }}</span>
              <el-slider
                v-model="editor.weights[field.key]"
                :min="0"
                :max="100"
                :step="1"
                :show-tooltip="false"
              />
              <span class="wval">{{ editor.weights[field.key] }}%</span>
            </div>
          </div>
          <div class="sum-line" :class="{ over: weightSum !== 100 }">
            权重合计 <b>{{ weightSum }}%</b> {{ weightSum === 100 ? '✓' : '（需调整至 100%）' }}
          </div>
        </section>

        <section class="ed-sec">
          <div class="ed-sec-head">
            <span class="ed-sec-title">重排参数</span>
          </div>
          <div class="ed-line">
            <label class="ed-switch">
              <el-switch v-model="editor.rerank" />
              <span>启用重排</span>
              <b>{{ editor.rerank ? '开' : '关' }}</b>
            </label>
            <span class="ed-fixed">
              开 = 在向量召回结果上用精细模型再排一次（更准，多几十毫秒） · 关 = 直接按向量召回的相似度顺序返回（更快，可能有少量错序）
            </span>
          </div>
        </section>
      </div>
      <template #footer>
        <div class="stg-foot">
          <el-button v-if="canDiscardDraft" link type="danger" @click="discardEditorDraft">放弃草稿</el-button>
          <span class="cm-grow" />
          <el-button @click="editorOpen = false">取消</el-button>
          <el-button :loading="saving" @click="saveDraftOnly">保存草稿</el-button>
          <el-button type="primary" :loading="saving" @click="saveAndPublish">保存并发布</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 版本历史 -->
    <el-dialog v-model="versionOpen" :title="versionTitle" width="900px">
      <el-table v-loading="versionLoading" :data="versionRows" size="small" style="width: 100%" empty-text="暂无历史版本">
        <el-table-column label="版本" width="72">
          <template #default="{ row }">
            <span class="mono">{{ row.versionLabel }}</span>
          </template>
        </el-table-column>
        <el-table-column label="名称" min-width="170">
          <template #default="{ row }">
            <span class="name-text" :title="row.name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="104">
          <template #default="{ row }">
            <span class="badge" :class="row.statusCls">{{ row.statusLabel }}</span>
          </template>
        </el-table-column>
        <el-table-column label="权重" width="104">
          <template #default="{ row }">
            <span class="num-cell">{{ row.weightsLabel }}</span>
          </template>
        </el-table-column>
        <el-table-column label="重排" width="64">
          <template #default="{ row }">{{ row.rerankLabel }}</template>
        </el-table-column>
        <el-table-column label="阈值" width="64">
          <template #default="{ row }">
            <span class="num-cell">{{ row.thresholdLabel }}</span>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="156">
          <template #default="{ row }">
            <span class="time-cell">{{ row.publishedLabel }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="90">
          <template #default="{ row }">{{ row.sourceLabel }}</template>
        </el-table-column>
        <el-table-column label="操作" width="84" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.canRollback" link type="primary" @click="requestRollback(row)">回滚</el-button>
            <span v-else class="muted-cell">—</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="ver-limit">仅保留最近 10 个版本</div>
      <template #footer>
        <el-button @click="versionOpen = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 策略对比 -->
    <el-dialog v-model="compareOpen" :title="compareTitle" width="760px">
      <div class="cmp-bar">
        <span class="ed-label">对比策略</span>
        <el-select
          v-model="compareId"
          class="cmp-select"
          size="small"
          placeholder="请选择对比策略"
          :disabled="!compareOptions.length"
          @change="loadCompare"
        >
          <el-option
            v-for="item in compareOptions"
            :key="item.id"
            :label="`${item.name}（v${item.versionNo ?? '—'}）`"
            :value="item.id"
          />
        </el-select>
        <span v-if="!compareOptions.length" class="ed-fixed">暂无可对比策略</span>
      </div>
      <el-table v-loading="compareLoading" :data="compareRows" size="small" style="width: 100%" empty-text="暂无可对比数据">
        <el-table-column label="配置项" width="120">
          <template #default="{ row }">
            <span class="cmp-key">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="对比策略" min-width="220">
          <template #default="{ row }">
            <span class="cmp-val">{{ row.left }}</span>
          </template>
        </el-table-column>
        <el-table-column label="当前策略" min-width="220">
          <template #default="{ row }">
            <span class="cmp-val" :class="{ 'is-diff': row.diff }">{{ row.right }}</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="compareOpen = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 一致性检查（全局只读 5 项） -->
    <el-dialog v-model="consistencyOpen" :title="consistencyTitle" width="620px">
      <div v-loading="consistencyLoading" class="chk-list">
        <div
          v-for="item in consistencyChecks"
          :key="item.key"
          class="chk-row"
          :class="{ bad: !item.ok }"
        >
          <span class="chk-ico" aria-hidden="true">{{ item.ok ? '✓' : '✗' }}</span>
          <span class="chk-k">{{ item.label }}</span>
          <span class="chk-v">{{ item.detail || '—' }}</span>
        </div>
        <div v-if="!consistencyLoading && !consistencyChecks.length" class="muted-cell">
          {{ consistencyError || '未获取到检查结果' }}
        </div>
      </div>
      <template #footer>
        <div class="stg-foot">
          <span class="chk-time">检查时间 {{ consistencyAt }}</span>
          <span class="cm-grow" />
          <el-button @click="consistencyOpen = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 下线策略 -->
    <el-dialog v-model="offlineOpen" title="下线策略" width="560px" :close-on-click-modal="false">
      <div class="dlg-row">
        <span class="dlg-k">策略</span>
        <span class="dlg-v">
          {{ offlineTarget?.name || '—' }}
          <span class="mono">· {{ offlineTarget?.bizNo }}</span>
          · v{{ offlineTarget?.currentVersion ?? '—' }}
        </span>
      </div>
      <div class="dlg-row">
        <span class="dlg-k">下线原因</span>
        <span class="dlg-v">
          <el-select v-model="offlineReason" style="width: 200px">
            <el-option v-for="item in OFFLINE_REASONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </span>
      </div>
      <div class="dlg-row">
        <span class="dlg-k">备注</span>
        <span class="dlg-v">
          <el-input v-model="offlineRemark" maxlength="60" placeholder="选填" />
        </span>
      </div>
      <div class="dlg-row">
        <span class="dlg-k">影响</span>
        <span class="dlg-v impact">下线后，检索台的「检索策略」下拉里不再出现该策略；已使用该策略的历史任务不受影响。</span>
      </div>
      <template #footer>
        <div class="stg-foot">
          <span class="chk-time">操作将留痕</span>
          <span class="cm-grow" />
          <el-button @click="offlineOpen = false">取消</el-button>
          <el-button type="danger" :loading="offlineSaving" @click="confirmOffline">确认下线</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 发布 / 回滚回执 -->
    <el-dialog v-model="receiptOpen" :title="receipt.title" width="540px">
      <div class="receipt">
        <div class="rc-head">
          ✓ {{ receipt.title }}
          <span v-if="receipt.versionNo" class="rc-id">v{{ receipt.versionNo }}</span>
        </div>
        <div class="rc-line"><span>策略：</span>{{ receipt.name }}<span class="mono"> · {{ receipt.bizNo }}</span></div>
        <div class="rc-line"><span>生效版本：</span>{{ receipt.versionNo ? `v${receipt.versionNo}` : '—' }}</div>
        <div class="rc-line"><span>配置摘要：</span>{{ receipt.summary }}</div>
        <div class="rc-line"><span>生效时间：</span>{{ receipt.time }}</div>
        <div class="rc-tip">可前往检索台验证：该策略的下次检索即按新版本参数执行。</div>
      </div>
      <template #footer>
        <el-button @click="receiptOpen = false">关闭</el-button>
        <el-button type="primary" @click="goSearchConsole">前往检索台验证 →</el-button>
      </template>
    </el-dialog>

    <DevNotesDrawer v-model="devOpen" title="研发需求说明">
      <p class="help-lead">
        本页是检索中心的「大脑」：三段式管理检索策略——①向量（只读回显）②召回与融合（相似度阈值 + 四模态权重）③重排（启用开关）。
        策略经评测（P8）验证后保存并发布，对检索台（P1）下次检索即生效。
      </p>

      <div>
        <div class="hb-t">策略列表 6 列</div>
        <table class="help-table">
          <thead>
            <tr><th>列</th><th>口径</th></tr>
          </thead>
          <tbody>
            <tr><td class="fld">策略 ID</td><td>业务编号 STG+yyMMdd+6 位安全码，等宽字体展示；内部雪花主键不外暴。</td></tr>
            <tr><td class="fld">名称</td><td>策略名；系统默认（兜底）策略在名称后标「系统预置」。</td></tr>
            <tr><td class="fld">版本</td><td>当前生效版本号 v1 / v2 / v3；从未发布显示「—」。</td></tr>
            <tr><td class="fld">状态</td><td>生效中（绿）/ 草稿（灰）/ 已下线（灰，整行文字变浅）；「编辑中」是保存过程中的过渡态，不落库。</td></tr>
            <tr><td class="fld">更新时间</td><td>最近一次保存 / 发布 / 下线上线时间。</td></tr>
            <tr><td class="fld">操作</td><td>编辑、发布、版本历史、对比、下线或上线；草稿行不出现下线 / 上线。</td></tr>
          </tbody>
        </table>
      </div>

      <p class="help-note">
        <b>编辑弹窗：</b>名称必填；向量模型只读（产线白名单，统一检索固定一套向量空间）；相似度阈值必填且限定 0.30–0.99（新建默认 0.60，编辑取当前版本值）；
        四模态权重（文本 / 图像 / 视频 / 音频）合计必须 100%，默认 50/30/10/10；重排开关默认开。三段参数在弹窗底部一次性写入。
      </p>

      <p class="help-note">
        <b>保存草稿 vs 保存并发布：</b>保存草稿只写待发布参数，检索仍用当前生效版本（新建策略落「草稿」，不进检索台下拉）；
        保存并发布把参数写成生效版本、写版本历史，该策略下次检索即生效，回执显示新版本号并可跳转检索台验证。
      </p>

      <p class="help-note">
        <b>下线 / 上线：</b>下线圈定四种原因（策略调整中 / 效果不佳 / 已被替代 / 其他），确认后转已下线，检索台下拉不再出现该策略，已用该策略的历史任务不受影响；
        <b>系统预置（兜底）策略不可下线</b>。已下线行点「上线」直接回生效中，不弹二次确认。
      </p>

      <p class="help-note">
        <b>版本历史：</b>草稿置顶且不可回滚；其余按版本倒序，当前生效版本不可回滚；回滚会回写版本 / 状态 / 配置并出回滚回执。仅保留最近 10 个版本，更早版本由后端物理清理。
      </p>

      <p class="help-note">
        <b>对比：</b>选择另一条策略，按「相似度阈值 / 向量模型 / 文本·图像·视频·音频权重 / 重排 / 返回条数」逐行对比，差异行以主色高亮。
      </p>

      <p class="help-note">
        <b>一致性检查（页面级只读）：</b>固定 5 项——阈值区间 0.30–0.99、权重合计 100%、权重非负、重排开关有值、默认策略存在且生效；只展示结果，不改任何数据。
      </p>
    </DevNotesDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createStrategy,
  discardDraft,
  offlineStrategy,
  onlineStrategy,
  pageStrategies,
  publishStrategy,
  rollbackStrategy,
  strategyCompareOptions,
  strategyConsistency,
  strategyDetail,
  strategyDiff,
  strategyModelOptions,
  updateDraft,
} from '../../api'
import type {
  ConsistencyCheckVO,
  Id,
  StrategyDetailVO,
  StrategyVersionVO,
  StrategyVO,
} from '../../api/types'
import { fmtTime, parseJson } from '../../utils/format'
import DevNotesDrawer from '../../components/DevNotesDrawer.vue'

const PAGE_SIZE = 10

const WEIGHT_FIELDS = [
  { key: 'text', label: '文本' },
  { key: 'image', label: '图像' },
  { key: 'video', label: '视频' },
  { key: 'audio', label: '音频' },
] as const

type WeightKey = (typeof WEIGHT_FIELDS)[number]['key']

const DEFAULT_WEIGHTS: Record<WeightKey, number> = { text: 50, image: 30, video: 10, audio: 10 }
const DEFAULT_THRESHOLD = 0.6

/** 下线原因：label 为原型文案，value 为后端枚举 alias */
const OFFLINE_REASONS = [
  { value: 'tuning', label: '策略调整中' },
  { value: 'poor', label: '效果不佳' },
  { value: 'replaced', label: '已被替代' },
  { value: 'other', label: '其他' },
]

const STATUS_META: Record<string, { label: string; cls: string }> = {
  active: { label: '生效中', cls: 'ok' },
  draft: { label: '草稿', cls: 'gray' },
  offline: { label: '已下线', cls: 'off' },
}

/** strategies/{id}/diff 返回结构（仅前端用到的行） */
interface DiffPair<T> {
  from: T | null
  to: T | null
}

interface StrategyDiff {
  weights?: Record<string, DiffPair<number>>
  rerank?: DiffPair<boolean>
  topK?: DiffPair<number>
  threshold?: DiffPair<number>
  vector?: DiffPair<string>
}

interface VersionRow {
  id: Id
  versionNo: number | null
  versionLabel: string
  name: string
  statusLabel: string
  statusCls: string
  weightsLabel: string
  rerankLabel: string
  thresholdLabel: string
  publishedLabel: string
  sourceLabel: string
  canRollback: boolean
}

interface CompareRow {
  name: string
  left: string
  right: string
  diff: boolean
}

const router = useRouter()

const devOpen = ref(false)

const rows = ref<StrategyVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)

const editorOpen = ref(false)
const editingId = ref<Id | null>(null)
const editorDetail = ref<StrategyDetailVO | null>(null)
const saving = ref(false)
const recallHelpOpen = ref(false)
const editor = reactive({
  name: '',
  /** 原型已移除「适用场景」字段，但 StrategySaveDTO.scene 必填，编辑时原样透传、新建用 general */
  scene: 'general',
  threshold: DEFAULT_THRESHOLD,
  /** 原型已移除「返回条数」输入；StrategySaveDTO.topK 有默认值 20，必须显式透传现值，否则保存会把它写回 20 */
  topK: 20,
  weights: { ...DEFAULT_WEIGHTS } as Record<WeightKey, number>,
  rerank: true,
})

/** 向量模型只读回显：优先取策略详情的当前索引模型 */
const activeModel = ref('')

const versionOpen = ref(false)
const versionTarget = ref<StrategyVO | null>(null)
const versionDetail = ref<StrategyDetailVO | null>(null)
const versionLoading = ref(false)

const compareOpen = ref(false)
const compareTarget = ref<StrategyVO | null>(null)
type CompareOption = {
  id: Id
  bizNo: string
  name: string
  scene: string
  status: string
  versionNo: number | null
}
const compareOptions = ref<CompareOption[]>([])
const compareRows = ref<CompareRow[]>([])
const compareId = ref<Id | ''>('')
const compareLoading = ref(false)

const consistencyOpen = ref(false)
const consistencyLoading = ref(false)
const consistencyChecks = ref<ConsistencyCheckVO[]>([])
const consistencyPassed = ref(false)
const consistencyError = ref('')
const consistencyAt = ref('')

const offlineOpen = ref(false)
const offlineSaving = ref(false)
const offlineTarget = ref<StrategyVO | null>(null)
const offlineReason = ref('tuning')
const offlineRemark = ref('')

const receiptOpen = ref(false)
const receipt = reactive({
  title: '',
  name: '',
  bizNo: '',
  versionNo: null as number | null,
  summary: '',
  time: '',
})

const weightSum = computed(
  () =>
    Number(editor.weights.text || 0) +
    Number(editor.weights.image || 0) +
    Number(editor.weights.video || 0) +
    Number(editor.weights.audio || 0),
)

const editorPreset = computed(() => editorDetail.value?.strategy.preset === true)

const editorVersion = computed(() => {
  if (!editingId.value) {
    return 'v1'
  }
  const current = editorDetail.value?.current?.versionNo ?? 0
  if (editorDetail.value?.draft) {
    return `v${current} → v${current + 1}（草稿）`
  }
  return current > 0 ? `v${current}` : 'v1'
})

const editorStatus = computed(() => {
  if (!editingId.value) {
    return '草稿'
  }
  return statusMeta(editorDetail.value?.strategy.status).label
})

const editorModel = computed(
  () => editorDetail.value?.modelVersion || activeModel.value || '统一向量空间',
)

const canDiscardDraft = computed(() => !!editingId.value && !!editorDetail.value?.draft)

const versionTitle = computed(() =>
  versionTarget.value ? `版本历史 · ${versionTarget.value.name}` : '版本历史',
)

const compareTitle = computed(() =>
  compareTarget.value ? `策略对比 · ${compareTarget.value.name}` : '策略对比',
)

const consistencyBadCount = computed(() => consistencyChecks.value.filter((item) => !item.ok).length)

const consistencyTitle = computed(() => {
  if (consistencyLoading.value) {
    return '一致性检查中…'
  }
  if (consistencyError.value) {
    return '✗ 一致性检查失败'
  }
  if (!consistencyChecks.value.length) {
    return '一致性检查无结果'
  }
  // 只有后端明确 passed=true 且无坏项才显示通过，避免请求失败/空结果时误报“通过”
  return consistencyPassed.value && !consistencyBadCount.value
    ? '✓ 一致性检查通过'
    : `✗ 发现 ${consistencyBadCount.value} 项问题`
})

const versionRows = computed<VersionRow[]>(() => {
  const detail = versionDetail.value
  if (!detail) {
    return []
  }
  const current = detail.strategy.currentVersion ?? 0
  const list: VersionRow[] = []
  // 草稿置顶：不在 versionsOf 返回内，单独补一行且不可回滚
  if (detail.draft) {
    list.push({
      id: detail.draft.id,
      versionNo: null,
      versionLabel: `v${current + 1}`,
      name: detail.draft.name || detail.strategy.name,
      statusLabel: '草稿',
      statusCls: 'gray',
      weightsLabel: weightsLabel(detail.draft.weightsJson),
      rerankLabel: detail.draft.rerank ? '开' : '关',
      thresholdLabel: thresholdText(detail.draft.threshold),
      publishedLabel: fmtTime(detail.draft.createTime),
      sourceLabel: '草稿',
      canRollback: false,
    })
  }
  for (const version of detail.versions) {
    const isCurrent = version.versionNo === current
    const offline = detail.strategy.status === 'offline'
    list.push({
      id: version.id,
      versionNo: version.versionNo,
      versionLabel: `v${version.versionNo}`,
      name: version.name || detail.strategy.name,
      statusLabel: isCurrent ? (offline ? '当前已下线' : '当前生效') : '历史版本',
      statusCls: isCurrent ? (offline ? 'off' : 'ok') : 'gray',
      weightsLabel: weightsLabel(version.weightsJson),
      rerankLabel: version.rerank ? '开' : '关',
      thresholdLabel: thresholdText(version.threshold),
      publishedLabel: fmtTime(version.publishedTime || version.createTime),
      sourceLabel: version.source === 'rollback' ? '版本回滚' : '参数发布',
      canRollback: !isCurrent && current > 0,
    })
  }
  return list
})

onMounted(async () => {
  await Promise.all([loadModelOptions(), reload()])
})

async function loadModelOptions() {
  try {
    const data = (await strategyModelOptions()) as { activeModel?: string }
    activeModel.value = data.activeModel || ''
  } catch {
    activeModel.value = ''
  }
}

async function reload() {
  loading.value = true
  try {
    const page = await pageStrategies({ pageNum: pageNum.value, pageSize: PAGE_SIZE })
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
  void reload()
}

function rowClassName({ row }: { row: StrategyVO }) {
  return row.status === 'offline' ? 'row-off' : ''
}

function statusMeta(status?: string | null) {
  return STATUS_META[status || ''] || { label: status || '—', cls: 'gray' }
}

function thresholdText(value?: number | null) {
  if (value === null || value === undefined) {
    return '—'
  }
  const num = Number(value)
  return Number.isFinite(num) ? num.toFixed(2) : '—'
}

function toWeight(value: unknown) {
  const num = Number(value)
  return Number.isFinite(num) ? Math.min(100, Math.max(0, Math.round(num))) : 0
}

function readWeights(raw?: string | null): Record<WeightKey, number> {
  const value = parseJson<Record<string, unknown>>(raw || '', {})
  const hasAny = WEIGHT_FIELDS.some((field) => typeof value[field.key] === 'number')
  if (!hasAny) {
    return { ...DEFAULT_WEIGHTS }
  }
  return {
    text: toWeight(value.text),
    image: toWeight(value.image),
    video: toWeight(value.video),
    audio: toWeight(value.audio),
  }
}

function weightsLabel(raw?: string | null) {
  const value = readWeights(raw)
  return `${value.text}/${value.image}/${value.video}/${value.audio}`
}

function versionSummary(version?: StrategyVersionVO | null) {
  if (!version) {
    return '—'
  }
  return `融合 ${weightsLabel(version.weightsJson)} · 阈值 ${thresholdText(version.threshold)} · ${version.rerank ? '启用重排' : '不启用重排'}`
}

function nowText() {
  const date = new Date()
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

// ---------- 编辑弹窗 ----------

function openNewStrategy() {
  editingId.value = null
  editorDetail.value = null
  editor.name = ''
  editor.scene = 'general'
  editor.threshold = DEFAULT_THRESHOLD
  editor.topK = 20
  editor.weights = { ...DEFAULT_WEIGHTS }
  editor.rerank = true
  recallHelpOpen.value = false
  editorOpen.value = true
}

async function openEditor(row: StrategyVO) {
  try {
    const detail = await strategyDetail(row.id)
    editingId.value = row.id
    editorDetail.value = detail
    const base = detail.draft || detail.current
    editor.name = detail.strategy.name
    editor.scene = detail.strategy.scene || 'general'
    editor.threshold =
      base?.threshold === null || base?.threshold === undefined
        ? DEFAULT_THRESHOLD
        : Number(base.threshold)
    editor.topK = base?.topK ?? 20
    editor.weights = readWeights(base?.weightsJson)
    editor.rerank = base ? base.rerank !== false : true
    recallHelpOpen.value = false
    editorOpen.value = true
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function validateEditor() {
  if (!editor.name.trim()) {
    ElMessage.error('请填写策略名称')
    return false
  }
  const threshold = Number(editor.threshold)
  if (!Number.isFinite(threshold) || threshold < 0.3 || threshold > 0.99) {
    ElMessage.error('相似度阈值需在 0.30 到 0.99 之间')
    return false
  }
  if (weightSum.value !== 100) {
    ElMessage.error('融合权重合计需为 100%')
    return false
  }
  return true
}

/** 新建走 create、编辑走 updateDraft；threshold 每次必带，避免后端回落默认值 */
async function persistDraft() {
  const body: Record<string, unknown> = {
    name: editor.name.trim(),
    scene: editor.scene || 'general',
    remark: editorDetail.value?.strategy.remark || '',
    weights: {
      text: editor.weights.text,
      image: editor.weights.image,
      video: editor.weights.video,
      audio: editor.weights.audio,
    },
    rerank: editor.rerank,
    threshold: Number(editor.threshold),
    topK: editor.topK,
  }
  if (!editingId.value) {
    const created = await createStrategy(body)
    editingId.value = created.id
    return { id: created.id, bizNo: created.bizNo, name: String(body.name) }
  }
  await updateDraft(editingId.value, body)
  return {
    id: editingId.value,
    bizNo: editorDetail.value?.strategy.bizNo || '',
    name: String(body.name),
  }
}

async function saveDraftOnly() {
  if (!validateEditor()) {
    return
  }
  saving.value = true
  try {
    await persistDraft()
    editorOpen.value = false
    ElMessage.success('已保存草稿')
    await reload()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function saveAndPublish() {
  if (!validateEditor()) {
    return
  }
  try {
    await ElMessageBox.confirm(
      '发布后该策略的下次检索即按新参数执行，确认保存并发布？',
      '保存并发布',
      { confirmButtonText: '保存并发布', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  saving.value = true
  try {
    const saved = await persistDraft()
    const version = await publishStrategy(saved.id)
    editorOpen.value = false
    await reload()
    openReceipt('发布回执', saved.name, saved.bizNo, version)
    ElMessage.success(version?.versionNo ? `已发布 v${version.versionNo}` : '已发布')
  } catch (error) {
    // 新建已落库但发布失败时，列表需要反映这条草稿
    ElMessage.error((error as Error).message)
    await reload()
  } finally {
    saving.value = false
  }
}

async function discardEditorDraft() {
  if (!editingId.value) {
    return
  }
  try {
    await ElMessageBox.confirm('将丢弃当前未发布草稿，草稿配置不写入生效版本。', '放弃草稿', {
      confirmButtonText: '放弃草稿',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await discardDraft(editingId.value)
    editorOpen.value = false
    ElMessage.success('已放弃草稿')
    await reload()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ---------- 发布 / 下线 / 上线 ----------

async function requestPublish(row: StrategyVO) {
  if (!row.hasDraft) {
    ElMessage.warning('该策略没有待发布草稿，请先编辑保存后再发布')
    return
  }
  try {
    await ElMessageBox.confirm(
      `发布后「${row.name}」的下次检索即按新参数执行，确认发布当前草稿？`,
      '发布策略',
      { confirmButtonText: '发布', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    const version = await publishStrategy(row.id)
    await reload()
    openReceipt('发布回执', row.name, row.bizNo, version)
    ElMessage.success(version?.versionNo ? `已发布 v${version.versionNo}` : '已发布')
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function requestOffline(row: StrategyVO) {
  if (row.preset) {
    ElMessage.error('系统预置策略不可下线')
    return
  }
  if (row.status !== 'active') {
    ElMessage.warning('当前策略不在生效中，无需下线')
    return
  }
  offlineTarget.value = row
  offlineReason.value = 'tuning'
  offlineRemark.value = ''
  offlineOpen.value = true
}

async function confirmOffline() {
  const target = offlineTarget.value
  if (!target) {
    return
  }
  offlineSaving.value = true
  try {
    await offlineStrategy(target.id, offlineReason.value, offlineRemark.value)
    offlineOpen.value = false
    ElMessage.success('已下线')
    await reload()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    offlineSaving.value = false
  }
}

async function requestOnline(row: StrategyVO) {
  try {
    await onlineStrategy(row.id)
    ElMessage.success('已上线')
    await reload()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ---------- 版本历史 ----------

async function openVersions(row: StrategyVO) {
  versionTarget.value = row
  versionDetail.value = null
  versionOpen.value = true
  await loadVersionDetail()
}

async function loadVersionDetail() {
  const target = versionTarget.value
  if (!target) {
    return
  }
  versionLoading.value = true
  try {
    versionDetail.value = await strategyDetail(target.id)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    versionLoading.value = false
  }
}

async function requestRollback(row: VersionRow) {
  const target = versionTarget.value
  if (!target || row.versionNo === null) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `将「${target.name}」回滚至 v${row.versionNo}，回写版本 / 状态 / 配置，未发布草稿随之放弃。`,
      '版本回滚',
      { confirmButtonText: '回滚', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    const version = await rollbackStrategy(target.id, row.versionNo)
    await loadVersionDetail()
    await reload()
    openReceipt('回滚核验条', target.name, target.bizNo, version)
    ElMessage.success('已回滚')
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

// ---------- 对比 ----------

async function openCompare(row: StrategyVO) {
  compareTarget.value = row
  compareOpen.value = true
  compareRows.value = []
  compareId.value = ''
  compareOptions.value = []
  try {
    // 对比对象不限于生效策略：草稿 / 已下线策略同样可选，后端按“生效 > 草稿 > 最近发布”取可用版本
    const options = await strategyCompareOptions()
    compareOptions.value = options.filter((item) => item.id !== row.id)
    if (compareOptions.value.length) {
      compareId.value = compareOptions.value[0].id
      await loadCompare()
    }
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function loadCompare() {
  const target = compareTarget.value
  if (!target || !compareId.value) {
    compareRows.value = []
    return
  }
  compareLoading.value = true
  try {
    const detail = await strategyDetail(target.id)
    const versionNo = Math.max(1, detail.current?.versionNo ?? detail.draft?.versionNo ?? 1)
    const data = (await strategyDiff(target.id, versionNo, versionNo, compareId.value)) as StrategyDiff
    compareRows.value = buildCompareRows(data)
  } catch (error) {
    compareRows.value = []
    ElMessage.error((error as Error).message)
  } finally {
    compareLoading.value = false
  }
}

/** 左列＝对比策略（diff.to），右列＝当前策略（diff.from） */
function buildCompareRows(data: StrategyDiff): CompareRow[] {
  const list: CompareRow[] = []
  const push = (name: string, self: string, other: string) => {
    list.push({ name, left: other, right: self, diff: self !== other })
  }
  push('相似度阈值', thresholdText(data.threshold?.from), thresholdText(data.threshold?.to))
  // 向量模型不属于策略参数（只读回显当前激活索引），双方永远相同，不在对比表中展示
  const weights = data.weights || {}
  for (const field of WEIGHT_FIELDS) {
    const pair = weights[field.key]
    push(
      `${field.label}权重`,
      pair?.from === null || pair?.from === undefined ? '—' : `${pair.from}%`,
      pair?.to === null || pair?.to === undefined ? '—' : `${pair.to}%`,
    )
  }
  push(
    '重排',
    data.rerank?.from === null || data.rerank?.from === undefined ? '—' : data.rerank.from ? '开' : '关',
    data.rerank?.to === null || data.rerank?.to === undefined ? '—' : data.rerank.to ? '开' : '关',
  )
  push(
    '返回条数',
    data.topK?.from === null || data.topK?.from === undefined ? '—' : String(data.topK.from),
    data.topK?.to === null || data.topK?.to === undefined ? '—' : String(data.topK.to),
  )
  return list
}

// ---------- 一致性检查 ----------

async function openConsistency() {
  consistencyOpen.value = true
  consistencyLoading.value = true
  consistencyChecks.value = []
  consistencyPassed.value = false
  consistencyError.value = ''
  consistencyAt.value = ''
  try {
    const data = (await strategyConsistency()) as {
      checks?: ConsistencyCheckVO[]
      passed?: boolean
    }
    consistencyChecks.value = data.checks || []
    consistencyPassed.value =
      data.passed ?? consistencyChecks.value.every((item) => item.ok)
    consistencyAt.value = nowText()
  } catch (error) {
    consistencyError.value = (error as Error).message
    ElMessage.error(consistencyError.value)
  } finally {
    consistencyLoading.value = false
  }
}

// ---------- 回执 ----------

function openReceipt(
  title: string,
  name: string,
  bizNo: string,
  version?: StrategyVersionVO | null,
) {
  receipt.title = title
  receipt.name = name
  receipt.bizNo = bizNo || '—'
  receipt.versionNo = version?.versionNo ?? null
  receipt.summary = versionSummary(version)
  receipt.time = version?.publishedTime ? fmtTime(version.publishedTime) : nowText()
  receiptOpen.value = true
}

function goSearchConsole() {
  receiptOpen.value = false
  void router.push('/search')
}
</script>

<style scoped>
.panel-pad {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 18px;
}

.name-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.name-text {
  overflow: hidden;
  color: #1f2a44;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tag-preset {
  flex: 0 0 auto;
  padding: 0 5px;
  border: 1px solid #f5e3bd;
  border-radius: 4px;
  background: #fdf3e1;
  color: #8a6116;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
  white-space: nowrap;
}
.num-cell {
  color: #1f2a44;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  white-space: nowrap;
}
.time-cell {
  color: #475467;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.muted-cell {
  color: #b3bccb;
  font-size: 12px;
}
.row-acts {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-wrap: nowrap;
}
.row-acts :deep(.el-button) {
  padding: 0 2px;
  font-size: 12px;
}

:deep(.el-table__row.row-off) .cell {
  color: #aab3c2;
}
:deep(.el-table__row.row-off) .name-text,
:deep(.el-table__row.row-off) .num-cell,
:deep(.el-table__row.row-off) .time-cell {
  color: #aab3c2;
  font-weight: 400;
}
:deep(.el-table__row) {
  cursor: pointer;
}

.table-empty {
  border: 0;
  background: transparent;
  padding: 18px 12px;
}

/* 编辑弹窗 */
.ed-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 2px;
}
.ed-sec {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid #e4edf8;
  border-radius: 10px;
  background: #fff;
}
.ed-sec-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ed-sec-title {
  font-size: 15px;
  font-weight: 700;
  color: #1f2a44;
}
.ed-q {
  width: 18px;
  height: 18px;
  border: 1px solid #d7e0f0;
  border-radius: 50%;
  background: #fff;
  color: var(--muted-soft);
  font-size: 11px;
  font-weight: 700;
  line-height: 16px;
  cursor: pointer;
}
.ed-q:hover,
.ed-q[aria-expanded='true'] {
  border-color: #b9c9ff;
  background: var(--system-blue-weak);
  color: var(--system-blue);
}
.ed-help {
  padding: 9px 12px;
  border: 1px dashed #dfe8f7;
  border-radius: 8px;
  background: #f8fbff;
}
.ed-help p {
  margin: 0;
  color: #5b6577;
  font-size: 12px;
  line-height: 1.7;
}
.ed-help p + p {
  margin-top: 6px;
}
.ed-help b {
  color: #344054;
}
.basic-form :deep(.el-form-item) {
  margin-bottom: 0;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 28px;
  flex-wrap: wrap;
  padding-top: 4px;
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.meta-k {
  color: #475467;
  font-size: 12px;
}
.meta-item b {
  color: #1f2a44;
  font-weight: 600;
}
.ed-line {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.ed-label {
  flex: 0 0 auto;
  color: #475467;
  font-size: 12.5px;
}
.ed-fixed {
  color: #9aa6bd;
  font-size: 11.5px;
  line-height: 1.6;
}
.model-input {
  width: 280px;
}
.rc-line {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 12.5px;
}
.rc-line .ed-label {
  width: 72px;
}
.rc-input {
  width: 108px;
}
.fuse-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
  max-width: 720px;
  padding-top: 2px;
}
.fuse-title {
  color: #475467;
  font-size: 12.5px;
  font-weight: 600;
}
.fuse-wrap {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 720px;
}
.fslider {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) 72px;
  gap: 10px;
  align-items: center;
  color: #5b6472;
  font-size: 12.5px;
}
.fslider :deep(.el-slider) {
  --el-slider-height: 4px;
  --el-slider-button-size: 14px;
}
.wval {
  color: #1f2a44;
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  text-align: right;
}
.sum-line {
  color: var(--muted);
  font-size: 12px;
}
.sum-line b {
  color: var(--success);
  font-variant-numeric: tabular-nums;
}
.sum-line.over b {
  color: var(--error);
}
.ed-switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #5b6472;
  font-size: 12.5px;
  cursor: pointer;
}
.ed-switch b {
  color: #1f2a44;
  font-weight: 600;
}
.stg-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

/* 版本历史 */
.ver-limit {
  margin-top: 10px;
  color: #98a2b3;
  font-size: 11.5px;
}

/* 对比 */
.cmp-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.cmp-select {
  width: 280px;
}
.cmp-key {
  color: #475467;
  font-size: 12.5px;
}
.cmp-val {
  color: #344054;
  font-size: 12.5px;
}
.cmp-val.is-diff {
  color: var(--system-blue);
  font-weight: 600;
}

/* 一致性检查 */
.chk-list {
  min-height: 120px;
  padding: 4px 12px;
  border: 1px solid #eef2f8;
  border-radius: 8px;
  background: #fbfdff;
}
.chk-row {
  display: grid;
  grid-template-columns: 16px 132px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  padding: 7px 0;
  border-top: 1px dashed #eef2f8;
  font-size: 12.5px;
  line-height: 1.6;
}
.chk-row:first-child {
  border-top: 0;
}
.chk-ico {
  color: var(--success);
  font-weight: 700;
}
.chk-k {
  color: #1f2a44;
  font-weight: 600;
}
.chk-v {
  color: #5b6572;
}
.chk-row.bad .chk-ico,
.chk-row.bad .chk-k,
.chk-row.bad .chk-v {
  color: var(--error);
}
.chk-time {
  color: #b3bccc;
  font-size: 11.5px;
}

/* 下线弹窗 */
.dlg-row {
  display: flex;
  gap: 12px;
  padding: 5px 0;
  font-size: 13px;
  line-height: 1.7;
}
.dlg-k {
  flex: 0 0 72px;
  color: #98a2b3;
}
.dlg-v {
  flex: 1;
  min-width: 0;
  color: #1f2a44;
}
.dlg-v.impact {
  color: #5b6572;
  font-size: 12.5px;
}

/* 回执 */
.receipt {
  padding: 10px 14px;
  border: 1px dashed #b7e2c4;
  border-radius: 8px;
  background: #f7fdf9;
  color: #3d6b52;
  font-size: 12px;
}
.rc-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
  color: #1e7a43;
  font-weight: 700;
}
.rc-id {
  padding: 0 6px;
  border-radius: 4px;
  background: #e8f7ec;
  color: #1e7a43;
  font-family: var(--font-mono);
  font-size: 11.5px;
  letter-spacing: 0.2px;
}
.rc-line {
  line-height: 1.75;
}
.rc-line span {
  color: #7a9c88;
}
.rc-tip {
  margin-top: 8px;
  padding-top: 7px;
  border-top: 1px dashed #cdeed6;
  color: #1e7a43;
  line-height: 1.7;
}
</style>
