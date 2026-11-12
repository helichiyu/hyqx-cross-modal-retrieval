<template>
  <div class="page-card">
    <header class="cm-page-header">
      <div>
        <h1 class="cm-page-title">结果页</h1>
      </div>
      <div class="head-actions">
        <button class="fav-entry" type="button" title="查看我的收藏" @click="openFavorites">
          收藏夹 ({{ favoriteCount }})
        </button>
      </div>
    </header>

    <!-- Z3 检索上下文条 -->
    <section class="ctx-bar">
      <router-link class="ctx-back" :to="backRoute" title="返回来源页">← 返回</router-link>
      <span class="ctx-q" :title="contextQuery">🔍 {{ contextQuery }}</span>
      <span class="ctx-item">模态对 <span class="pair-chip">{{ contextPair }}</span></span>
      <span class="ctx-item">
        任务 <b class="mono">{{ contextTask }}</b>
        <span v-if="runNo" class="pair-chip ctx-run">运行 {{ runNo }}</span>
      </span>
      <span class="ctx-spacer" aria-hidden="true"></span>
      <router-link v-if="!isReplay" class="ctx-link" :to="modifyRoute">检索台 →</router-link>
    </section>

    <!-- Z3-1 重放条 -->
    <section v-if="isReplay" class="replay-bar" aria-label="重放结果说明">
      <span class="rp-main">
        ⟳ 重放结果 · 按当时条件重新执行 · 当时 <b>{{ replayWasText }}</b> 条 → 现在
        <b>{{ totalCount }}</b> 条
      </span>
      <button class="btn-ghost rp-rerun" type="button" :disabled="rerunning" @click="rerun">
        {{ rerunning ? '执行中…' : '重新执行' }}
      </button>
    </section>

    <!-- Z4 结果集概要 -->
    <section class="rs-bar">
      <span class="rs-line">结果集 <span class="mono">{{ resultSetId || '—' }}</span> · 召回 {{ totalCount }} 条 · 下发 {{ items.length }} 条</span>
    </section>

    <!-- 评价封存提示 -->
    <section v-if="sealed" class="seal-tip" aria-live="polite">
      <span class="st-lock" aria-hidden="true">🔒</span>
      <span>该次运行已结束评价，如需重新评价请重新执行</span>
    </section>

    <!-- Z5 双栏：侧栏筛选 + 主内容 -->
    <div class="dual-col">
      <aside class="side-filter">
        <div class="sf-head">
          <span class="sf-title">结果筛选</span>
        </div>
        <div class="filter-block">
          <label>相似度阈值 <span class="fb-val">≥ {{ threshold.toFixed(2) }}</span></label>
          <div class="sim-range">
            <input
              v-model.number="threshold"
              type="range"
              :min="sliderMin"
              :max="sliderMax"
              step="0.01"
              aria-label="相似度阈值"
              @input="onThresholdInput"
            >
          </div>
        </div>
        <div class="filter-block">
          <label>来源</label>
          <div class="src-list" role="radiogroup" aria-label="来源筛选">
            <button
              v-for="source in sourceOptions"
              :key="source.name"
              type="button"
              class="src-item"
              :class="{ on: sourceFilter === source.name }"
              role="radio"
              :aria-checked="sourceFilter === source.name"
              :title="source.name"
              @click="selectSource(source.name)"
            >
              <span class="src-dot" aria-hidden="true"></span>
              <span class="src-name">{{ source.name }}</span>
              <span class="src-cnt">{{ source.count }}</span>
            </button>
          </div>
        </div>
        <button class="sf-reset" type="button" @click="resetFilters()">重置筛选</button>
      </aside>

      <div class="p2-main-col">
        <div class="present-bar">
          <div class="gtabs">
            <button
              v-for="group in groups"
              :key="group.key"
              type="button"
              class="gtab"
              :class="{ active: activeGroup === group.key, zero: groupCount(group.key) === 0 }"
              @click="changeGroup(group.key)"
            >
              {{ group.label }} <b>{{ groupCount(group.key) }}</b>
            </button>
          </div>
          <span class="pb-sep"></span>
          <div class="pb-ctrl">
            <span>排序</span>
            <select class="pb-sort" aria-label="排序方式"><option value="sim">相似度降序</option></select>
          </div>
          <div class="pb-ctrl">
            <span>对齐热力图</span>
            <button
              class="switch"
              :class="{ on: heatMap }"
              type="button"
              role="switch"
              :aria-checked="heatMap"
              aria-label="对齐热力图开关"
              @click="toggleHeat"
            ></button>
          </div>
        </div>

        <div class="result-area">
          <!-- 结果态 -->
          <div v-if="actualView === 'result'">
            <div class="list-head">
              <span>结果条目（Top <b>{{ filteredItems.length }}</b> / 共 <b>{{ groupTotal }}</b> 条）</span>
              <span></span>
            </div>
            <div v-if="excludedTotal > 0" class="bl-note" aria-live="polite">
              <span>本次共排除 <b>{{ excludedTotal }}</b> 条</span>
              <button class="bl-link" type="button" title="查看被排除的条目，核对是否误杀" @click="excludedOpen = true">
                查看过滤清单
              </button>
            </div>
            <div v-if="pageItems.length" class="rcard-grid">
              <article
                v-for="item in pageItems"
                :key="item.id"
                class="rcard"
                role="button"
                tabindex="0"
                :aria-label="`查看条目 ${item.itemId}`"
                @click="openItemView(item)"
                @keydown.enter.prevent="openItemView(item)"
                @keydown.space.prevent="openItemView(item)"
              >
                <div class="rc-info">
                  <div class="rc-line1">
                    <b class="mono">{{ item.itemId }}</b>
                    <span class="badge" :class="modalityBadgeClass(item.modality)">{{ modalityText(item.modality) }}</span>
                  </div>
                  <div class="rc-src" :title="itemSource(item)">
                    来源：{{ itemSource(item) }}<template v-if="itemExtra(item)"> · {{ itemExtra(item) }}</template>
                  </div>
                  <div v-if="heatMap" class="rc-align">
                    对齐分
                    <i class="al-chip" :style="{ background: heatColor(alignScoreOf(item)) }">
                      {{ alignLabelOf(item) }}
                    </i>
                  </div>
                </div>
                <div class="rc-right">
                  <div class="rc-sim">{{ alignScoreOf(item).toFixed(2) }}<small>相似度</small></div>
                  <div class="rc-ops" @click.stop>
                    <button
                      type="button"
                      class="rc-fav"
                      :class="{ on: item.favorited }"
                      :aria-pressed="!!item.favorited"
                      :title="item.favorited ? '取消收藏' : '收藏'"
                      @click="favoriteItem(item)"
                    >
                      {{ item.favorited ? '★ 已收藏' : '☆ 收藏' }}
                    </button>
                    <button
                      type="button"
                      class="rc-vote"
                      :class="[voteClass(item, 'up'), 'up']"
                      :disabled="sealed"
                      :aria-pressed="item.vote === 'up'"
                      :title="voteTitle(item, 'up')"
                      @click="voteItemAction(item, 'up')"
                    >
                      赞
                    </button>
                    <button
                      type="button"
                      class="rc-vote"
                      :class="[voteClass(item, 'down'), 'down']"
                      :disabled="sealed"
                      :aria-pressed="item.vote === 'down'"
                      :title="voteTitle(item, 'down')"
                      @click="voteItemAction(item, 'down')"
                    >
                      踩
                    </button>
                  </div>
                </div>
              </article>
            </div>
            <div v-else class="filter-empty">
              <div class="fe-ico" aria-hidden="true">🔍</div>
              <h4>该来源下暂无结果</h4>
              <button class="btn-ghost" type="button" @click="resetFilters()">重置筛选</button>
            </div>
            <el-pagination
              v-if="filteredItems.length > PAGE_SIZE"
              v-model:current-page="pageNum"
              class="cm-pagination"
              layout="total, prev, pager, next"
              :total="filteredItems.length"
              :page-size="PAGE_SIZE"
            />
          </div>

          <!-- 加载态 -->
          <div v-if="actualView === 'loading'">
            <div class="skel-grid">
              <div v-for="n in 6" :key="n" class="skel-card">
                <div class="skel-block sk-thumb"></div>
                <div class="sk-lines">
                  <div class="skel-block sk-line w60"></div>
                  <div class="skel-block sk-line w80"></div>
                  <div class="skel-block sk-line w40"></div>
                </div>
              </div>
            </div>
          </div>

          <!-- 空结果态 -->
          <div v-if="actualView === 'empty'">
            <div class="empty-wrap">
              <div class="state-ico">🔍</div>
              <h3>本次检索未召回结果</h3>
              <p class="st-desc">query「{{ contextQuery }}」在已接入检索库中未命中，按可疑度给出三条处置路径：</p>
              <div class="cause-grid">
                <article class="cause-card">
                  <span class="cc-tag high">归因①</span>
                  <h4>过滤过严</h4>
                  <p>当前筛选条件（相似度阈值 + 来源）偏严，检索日志显示有候选条目被过滤规则排除。</p>
                  <div class="cc-act">
                    <router-link class="btn-ghost ghost-link" :to="relaxRoute">放宽过滤，返回重新检索</router-link>
                  </div>
                </article>
                <article class="cause-card">
                  <span class="cc-tag mid">归因②</span>
                  <h4>语义偏离</h4>
                  <p>query 与库内高频语义向量距离偏大，可换用相似表达再次检索：</p>
                  <div class="simq-list">
                    <button
                      v-for="q in similarQueries"
                      :key="q"
                      class="simq-item"
                      type="button"
                      @click="goSearch(q)"
                    >
                      {{ q }}
                    </button>
                  </div>
                </article>
                <article class="cause-card">
                  <span class="cc-tag low">归因③</span>
                  <h4>覆盖缺失</h4>
                  <p>已接入检索库无「{{ contextQuery }}」直接覆盖记录，属内容覆盖缺口，可登记需求由运营侧跟进补数。</p>
                  <div class="cc-act">
                    <router-link class="btn-ghost ghost-link" :to="zeroRoute">需求登记 → 零结果专项</router-link>
                  </div>
                </article>
              </div>
              <div class="zero-tools">
                <div class="cm-log">
                  <div class="zero-log-empty">
                    归因依据：本次下发 {{ totalCount }} 条，下发前被过滤 {{ excludedTotal }} 条，当前阈值
                    {{ threshold.toFixed(2) }}。处置需求请在结果运营登记，由运营侧跟进补数 / 调整策略。
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 错误态 -->
          <div v-if="actualView === 'error'">
            <div class="state-wrap">
              <div class="state-ico" style="background: #fdecec">⚠️</div>
              <h3>本次检索失败</h3>
              <p class="st-desc">{{ errorMessage }}</p>
              <span class="err-code">错误码 {{ errorCode || '—' }} · 关联任务 <b class="mono">{{ contextTask }}</b></span>
              <div class="state-actions">
                <button class="btn-primary" type="button" :disabled="rerunning" @click="rerun">
                  {{ rerunning ? '重试中…' : '重试检索' }}
                </button>
                <router-link class="btn-ghost ghost-link" to="/search">返回检索台</router-link>
              </div>
            </div>
          </div>

        </div>
      </div>
    </div>

    <!-- 查看弹窗（只读：占位大图 + 元数据） -->
    <el-dialog v-model="viewOpen" title="查看" width="520px">
      <div class="view-media">
        <MediaPreview v-if="viewTarget?.sourceFile" :path="viewTarget.sourceFile" :text="viewTarget.text" class="vm-media" />
        <template v-else>
          <div class="vm-ico" aria-hidden="true">{{ viewIcon }}</div>
          <div class="vm-cap">{{ viewCaption }}</div>
        </template>
      </div>
      <div class="view-meta">
        <div class="vm-row"><span class="vm-k">名称</span><span class="vm-v">{{ viewTarget?.name || '—' }}</span></div>
        <div class="vm-row"><span class="vm-k">条目 ID</span><span class="vm-v mono">{{ viewTarget?.itemId || '—' }}</span></div>
        <div class="vm-row"><span class="vm-k">来源</span><span class="vm-v">{{ viewTarget?.source || '—' }}</span></div>
        <div class="vm-row"><span class="vm-k">相似度</span><span class="vm-v mono">{{ viewScoreText }}</span></div>
        <div class="vm-row"><span class="vm-k">模态</span><span class="vm-v">{{ viewModalityText }}</span></div>
      </div>
      <template #footer>
        <el-button @click="viewOpen = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 收藏夹弹窗 -->
    <el-dialog v-model="favoritesOpen" :title="`我的收藏 (${favoriteCount})`" width="560px">
      <div class="fav-list">
        <div v-if="!favorites.length" class="fav-empty">还没有收藏，在结果卡片上点「收藏」</div>
        <div v-for="fav in favorites" :key="fav.id || fav.itemId" class="fav-item">
          <div class="fav-main">
            <div class="fav-name" :title="fav.itemName || fav.itemId">{{ fav.itemName || fav.itemId }}</div>
            <div class="fav-meta">{{ fav.itemId }} · {{ fav.source || '未记录来源' }} · {{ fmtTime(fav.createTime) }}</div>
          </div>
          <div class="fav-ops">
            <button class="fav-act" type="button" @click="openFavoriteView(fav)">查看</button>
            <button class="fav-act del" type="button" @click="removeFavorite(fav)">移除</button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="favoritesOpen = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 过滤清单弹窗（只读） -->
    <el-dialog v-model="excludedOpen" title="过滤清单" width="760px">
      <div class="flt-sub">只读 · 不改结果集</div>
      <table v-if="excludedList.length" class="flt-table">
        <thead>
          <tr><th>条目 ID</th><th>名称</th><th>来源</th><th>过滤原因</th></tr>
        </thead>
        <tbody>
          <tr v-for="(item, index) in excludedList" :key="`${item.itemId}-${index}`">
            <td class="flt-id">{{ item.itemId }}</td>
            <td class="flt-name" :title="item.name || ''">{{ item.name || '—' }}</td>
            <td class="flt-src">{{ item.source || '—' }}</td>
            <td class="flt-rsn">
              <span class="flt-tag" :class="excludedReasonClass(item.reason)">{{ excludedReasonLabel(item.reason) }}</span>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="flt-empty">
        <template v-if="excludedTotal > 0">仅返回排除合计 {{ excludedTotal }} 条，过滤明细暂未下发</template>
        <template v-else>本次没有被过滤的条目</template>
      </div>
      <div class="flt-sum">
        共 <b>{{ excludedTotal }}</b> 条<template v-if="excludedSummary">：{{ excludedSummary }}</template>
      </div>
      <template #footer>
        <el-button @click="excludedOpen = false">关闭</el-button>
      </template>
    </el-dialog>

    <div class="toast" :class="{ show: toastVisible, ok: toastType === 'ok', err: toastType === 'err' }">
      {{ toastMessage }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  clickResult,
  myFavorites,
  rerunSearchResults,
  searchResults,
  searchSummary,
  sealTask,
  suggestWords,
  toggleFavorite,
  voteItem,
} from '../../api'
import { ApiError } from '../../api/http'
import MediaPreview from '../../components/MediaPreview.vue'
import { fmtTime } from '../../utils/format'
import type { HistoryDetailVO, ResultItemVO } from '../../api/types'

const PAGE_SIZE = 20

const route = useRoute()
const router = useRouter()

const resultSetId = computed(() => String(route.params.resultSetId || ''))

const detail = ref<HistoryDetailVO | null>(null)
const items = ref<ResultItemVO[]>([])
const actualView = ref<'loading' | 'result' | 'empty' | 'error'>('loading')
const errorMessage = ref('结果集加载失败')
const errorCode = ref('')
const rerunning = ref(false)
const sealed = ref(false)

const threshold = ref(0.5)
const strategyThreshold = ref(0.5)
const sourceFilter = ref('全部来源')
const activeGroup = ref('all')
const heatMap = ref(false)
const pageNum = ref(1)

const favorites = ref<FavoriteEntry[]>([])
const favoritesOpen = ref(false)
const viewOpen = ref(false)
const viewTarget = ref<ViewTarget | null>(null)

const excludedOpen = ref(false)
const replayWas = ref<number | null>(null)

const toastVisible = ref(false)
const toastMessage = ref('')
const toastType = ref<'info' | 'ok' | 'err'>('info')
let toastTimer: ReturnType<typeof setTimeout> | undefined

interface FavoriteEntry {
  id: string
  itemId: string
  itemName: string | null
  source: string | null
  taskNo: string | null
  createTime: string | null
}

interface ViewTarget {
  name: string | null
  itemId: string
  source: string | null
  score: number | null
  modality: string | null
  sourceFile: string | null
  text: string | null
}

const groups = [
  { key: 'all', label: '全部' },
  { key: 'image', label: '图像' },
  { key: 'video', label: '视频' },
  { key: 'text', label: '文本' },
  { key: 'audio', label: '音频' },
]

const history = computed(() => detail.value?.history ?? null)
/** 结果集总条数（召回口径，含下发前被剔除条目）；缺失时回落到已下发条目数 */
const totalCount = computed(() => {
  const count = history.value?.itemCount
  return typeof count === 'number' ? count : items.value.length
})
const contextQuery = computed(() => history.value?.queryText?.trim() || '（以文件语义检索）')
const contextTask = computed(() => history.value?.taskBizNo || history.value?.bizNo || '—')
const contextPair = computed(() => {
  const h = history.value
  if (!h?.queryModality) {
    return '未记录'
  }
  const from = shortModalityLabel(h.queryModality)
  if (h.targetModality) {
    return `${from}搜${shortModalityLabel(h.targetModality)} ✅`
  }
  return `${from}检索`
})
const runNo = computed(() => String(route.query.run || ''))
const isReplay = computed(
  () =>
    !!history.value?.replayOfTaskNo ||
    route.query.replay === '1' ||
    route.query.from === 'replay',
)
const replayWasText = computed(() => (replayWas.value == null ? '—' : String(replayWas.value)))

const backRoute = computed(() => {
  const back = String(route.query.back || '').toUpperCase()
  if (back === 'P6') {
    return '/behaviors'
  }
  if (back === 'P8') {
    return '/evaluation'
  }
  return '/search'
})
const modifyRoute = computed(() => ({
  path: '/search',
  query: history.value?.queryText ? { q: history.value.queryText } : {},
}))
const relaxRoute = computed(() => ({
  path: '/search',
  query: {
    ...(history.value?.queryText ? { q: history.value.queryText } : {}),
    relax: '1',
  },
}))
const zeroRoute = computed(() => ({
  path: '/operation',
  query: {
    zero: '1',
    task: history.value?.taskBizNo || history.value?.bizNo || '',
    back: 'P2',
  },
}))

const relatedWords = ref<string[]>([])
const similarQueries = computed(() => {
  const q = history.value?.queryText?.trim()
  return relatedWords.value.filter(word => word && word !== q).slice(0, 3)
})

const sliderMin = computed(() => String(Math.min(0.5, threshold.value)))
const sliderMax = computed(() => String(Math.max(0.99, threshold.value)))

/**
 * 同一源文件的多个抽取位置（视频抽帧、音频切片）只保留最高分一条，避免同一素材重复占卡位。
 * 文本按条目保留，避免同一文本文件的多个段落被合并掉。
 */
const uniqueItems = computed(() => {
  const seen = new Map<string, ResultItemVO>()
  for (const item of [...items.value].sort((a, b) => alignScoreOf(b) - alignScoreOf(a))) {
    const key = item.modality === 'text'
      ? `item:${item.itemId}`
      : (item.sourceFile?.trim() || `item:${item.itemId}`)
    if (!seen.has(key)) {
      seen.set(key, item)
    }
  }
  return [...seen.values()]
})

const sourceOptions = computed(() => {
  const counts = new Map<string, number>()
  for (const item of uniqueItems.value) {
    const name = itemSource(item)
    counts.set(name, (counts.get(name) || 0) + 1)
  }
  return [
    { name: '全部来源', count: uniqueItems.value.length },
    ...[...counts.entries()].map(([name, count]) => ({ name, count })),
  ]
})

const filteredItems = computed(() =>
  uniqueItems.value
    .filter(item => activeGroup.value === 'all' || item.modality === activeGroup.value)
    // 相似度阈值比较的是真实余弦对齐分；score 是融合分（含词面加成），不是 0~1 相似度
    .filter(item => alignScoreOf(item) >= threshold.value)
    .filter(item => sourceFilter.value === '全部来源' || itemSource(item) === sourceFilter.value)
    .sort((a, b) => alignScoreOf(b) - alignScoreOf(a)),
)
const pageItems = computed(() =>
  filteredItems.value.slice((pageNum.value - 1) * PAGE_SIZE, pageNum.value * PAGE_SIZE),
)
const groupTotal = computed(() => groupCount(activeGroup.value))

const excludedList = computed(() => detail.value?.excludedItems ?? [])
const excludedTotal = computed(() => {
  const declared = detail.value?.excludedCount
  return typeof declared === 'number' ? declared : excludedList.value.length
})
const excludedSummary = computed(() => {
  const counts = new Map<string, number>()
  for (const item of excludedList.value) {
    const key = item.reason || 'other'
    counts.set(key, (counts.get(key) || 0) + 1)
  }
  return [...counts.entries()].map(([key, count]) => `${excludedReasonLabel(key)} ${count} 条`).join(' · ')
})

const favoriteCount = computed(() => favorites.value.length)
const viewIcon = computed(() => {
  const modality = viewTarget.value?.modality
  return ({ image: '🖼', video: '🎬', audio: '🎧', text: '📄' } as Record<string, string>)[modality || ''] || '🖼'
})
const viewCaption = computed(() => {
  const modality = modalityText(viewTarget.value?.modality)
  return `${modality || '条目'} · 该条目未下发源文件路径，无法预览`
})
const viewScoreText = computed(() => {
  const score = viewTarget.value?.score
  return typeof score === 'number' ? score.toFixed(2) : '—'
})
const viewModalityText = computed(() => modalityText(viewTarget.value?.modality) || '—')

onMounted(() => {
  void load()
})

onBeforeUnmount(() => {
  clearTimeout(toastTimer)
  const taskNo = history.value?.bizNo
  // 离开页面即封存本次运行的评价；错误态没有产生可评价的结果集，不封存
  if (taskNo && !sealed.value && actualView.value !== 'error') {
    void sealTask(taskNo).catch(() => undefined)
  }
})

async function load(id = resultSetId.value) {
  actualView.value = 'loading'
  try {
    const data = await searchResults(id)
    detail.value = data
    items.value = data.items || []
    sealed.value = data.history.sealed === 'T'
    strategyThreshold.value = normalizeThreshold(data.history.threshold)
    threshold.value = strategyThreshold.value
    sourceFilter.value = '全部来源'
    activeGroup.value = 'all'
    pageNum.value = 1
    // 结果条目被黑名单/屏蔽全部剔除时仍留在结果区，走页内「该来源下暂无结果」，不跳整页空结果态
    const excludedOnLoad = typeof data.excludedCount === 'number' ? data.excludedCount : 0
    actualView.value = items.value.length || excludedOnLoad > 0 ? 'result' : 'empty'
    void loadFavorites()
    void loadReplayWas(data)
    void loadRelatedWords(data.history.scene)
  } catch (error) {
    errorMessage.value = errorText(error)
    errorCode.value = error instanceof ApiError ? error.code || String(error.status) : ''
    actualView.value = 'error'
  }
}

async function loadFavorites() {
  try {
    const rows = await myFavorites()
    favorites.value = rows.map(raw => ({
      id: String(raw.id ?? ''),
      itemId: String(raw.itemId ?? ''),
      itemName: raw.itemName == null ? null : String(raw.itemName),
      source: raw.source == null ? null : String(raw.source),
      taskNo: raw.taskNo == null ? null : String(raw.taskNo),
      createTime: raw.createTime == null ? null : String(raw.createTime),
    }))
  } catch {
    favorites.value = []
  }
}

/** 相似 query 取自后端推荐词（场景词库），不再前端拼接示例词 */
async function loadRelatedWords(scene: string | null | undefined) {
  try {
    relatedWords.value = await suggestWords(scene ?? undefined)
  } catch {
    relatedWords.value = []
  }
}

async function loadReplayWas(data: HistoryDetailVO) {
  replayWas.value = null
  if (!data.history.replayOfTaskNo) {
    return
  }
  const fromUrl = Number(route.query.was)
  if (Number.isFinite(fromUrl) && fromUrl > 0) {
    replayWas.value = fromUrl
    return
  }
  try {
    const summary = await searchSummary(data.history.replayOfTaskNo)
    replayWas.value = typeof summary.itemCount === 'number' ? summary.itemCount : null
  } catch {
    replayWas.value = null
  }
}

function normalizeThreshold(value: number | null | undefined) {
  const num = Number(value)
  if (!Number.isFinite(num) || num < 0 || num > 1) {
    return 0.5
  }
  return Math.round(num * 100) / 100
}

function errorText(error: unknown) {
  if (error instanceof Error) {
    return error.message
  }
  return '结果集加载失败'
}

function onThresholdInput() {
  pageNum.value = 1
}

function selectSource(name: string) {
  sourceFilter.value = name
  pageNum.value = 1
}

function changeGroup(key: string) {
  activeGroup.value = key
  pageNum.value = 1
}

function resetFilters() {
  threshold.value = strategyThreshold.value
  sourceFilter.value = '全部来源'
  activeGroup.value = 'all'
  pageNum.value = 1
  showToast('筛选已重置')
}

function groupCount(key: string) {
  // 分组计数与结果区可浏览条数一致：同一源文件只算一条
  return key === 'all'
    ? uniqueItems.value.length
    : uniqueItems.value.filter(item => item.modality === key).length
}

function toggleHeat() {
  heatMap.value = !heatMap.value
  showToast(heatMap.value ? '对齐热力图已开启（蓝→红，低分→高分）' : '对齐热力图已关闭')
}

function shortModalityLabel(modality: string) {
  return ({ text: '文', image: '图', audio: '音', video: '视频' } as Record<string, string>)[modality] || modality
}

function modalityText(modality: string | null | undefined) {
  return ({ text: '文本', image: '图像', video: '视频', audio: '音频' } as Record<string, string>)[modality || ''] || ''
}

function modalityBadgeClass(modality: string | null | undefined) {
  return ({ image: 'mod-img', video: 'mod-video', audio: 'mod-audio' } as Record<string, string>)[modality || ''] || 'gray'
}

function itemSource(item: ResultItemVO) {
  const source = item.source?.trim()
  if (source) {
    return source
  }
  const path = item.sourceFile || ''
  const parts = path.split(/[\\/]/).filter(Boolean)
  return parts.length > 1 ? parts[parts.length - 2] : path || '未记录来源'
}

function itemExtra(item: ResultItemVO) {
  const text = item.text?.trim()
  if (text) {
    return text.length > 42 ? `${text.slice(0, 42)}…` : text
  }
  const file = item.sourceFile?.split(/[\\/]/).pop()
  return file || ''
}

function heatColor(value: number) {
  const r = Math.round(65 + 164 * value)
  const g = Math.round(105 - 33 * value)
  const b = Math.round(246 - 169 * value)
  return `rgb(${r},${g},${b})`
}

function alignScoreOf(item: ResultItemVO) {
  return Number(item.alignScore ?? item.score)
}

function alignLabelOf(item: ResultItemVO) {
  return `${shortModalityLabel(item.modality).slice(0, 1)} ${alignScoreOf(item).toFixed(2)}`
}

function openItemView(item: ResultItemVO, reportClick = true) {
  viewTarget.value = {
    name: item.name || item.itemId,
    itemId: item.itemId,
    source: itemSource(item),
    score: alignScoreOf(item),
    modality: item.modality,
    sourceFile: item.sourceFile ?? null,
    text: item.text ?? null,
  }
  viewOpen.value = true
  // 点击埋点：后端按 resultSetId + rank 记录，供 P6 搜索行为与 P8 评测的点击统计
  if (reportClick) {
    void clickResult(resultSetId.value, item.rank).catch(() => undefined)
  }
}

function openFavorites() {
  favoritesOpen.value = true
  void loadFavorites()
}

function openFavoriteView(fav: FavoriteEntry) {
  const item = items.value.find(row => row.itemId === fav.itemId)
  if (item) {
    openItemView(item, false)
    return
  }
  viewTarget.value = {
    name: fav.itemName,
    itemId: fav.itemId,
    source: fav.source,
    score: null,
    modality: null,
    sourceFile: null,
    text: null,
  }
  viewOpen.value = true
}

async function removeFavorite(fav: FavoriteEntry) {
  try {
    await toggleFavorite({
      itemId: fav.itemId,
      itemName: fav.itemName,
      source: fav.source,
      taskNo: fav.taskNo,
    })
    const item = items.value.find(row => row.itemId === fav.itemId)
    if (item) {
      item.favorited = false
    }
    await loadFavorites()
    showToast('已移除', 'ok')
  } catch (error) {
    showToast(errorText(error), 'err')
  }
}

async function favoriteItem(item: ResultItemVO) {
  try {
    const on = await toggleFavorite({
      itemId: item.itemId,
      itemName: item.name || item.itemId,
      source: item.source || itemSource(item),
      taskNo: history.value?.bizNo,
    })
    item.favorited = on
    await loadFavorites()
    showToast(on ? '已收藏' : '已取消收藏', 'ok')
  } catch (error) {
    showToast(errorText(error), 'err')
  }
}

function voteClass(item: ResultItemVO, dir: 'up' | 'down') {
  if (sealed.value) {
    return 'sealed'
  }
  if (item.vote === dir) {
    return `done ${dir}`
  }
  return item.vote ? 'muted' : ''
}

function voteTitle(item: ResultItemVO, dir: 'up' | 'down') {
  if (sealed.value) {
    return '该次运行已结束评价，如需重新评价请重新执行'
  }
  const label = dir === 'up' ? '赞' : '踩'
  if (item.vote === dir) {
    return `这条数据被${label}（再点取消）`
  }
  if (item.vote) {
    return `点此改为${label}`
  }
  return `这条数据被${label}`
}

async function voteItemAction(item: ResultItemVO, dir: 'up' | 'down') {
  const taskNo = history.value?.bizNo
  if (!taskNo) {
    return
  }
  if (sealed.value) {
    showToast('该次运行已结束评价，如需重新评价请重新执行')
    return
  }
  const next = item.vote === dir ? 'none' : dir
  try {
    const result = await voteItem(taskNo, item.itemId, next)
    item.vote = (result.vote as 'up' | 'down' | null) ?? null
    showToast(next === 'none' ? '已取消' : '已记录', 'ok')
  } catch (error) {
    showToast(errorText(error), 'err')
  }
}

function excludedReasonLabel(reason: string | null | undefined) {
  return (
    ({ blacklist: '数据源黑名单', quality: '质量过滤', strategy: '策略过滤' } as Record<string, string>)[reason || ''] ||
    '其他过滤'
  )
}

function excludedReasonClass(reason: string | null | undefined) {
  if (reason === 'blacklist') {
    return 'bl'
  }
  if (reason === 'quality') {
    return 'ql'
  }
  return 'st'
}

function goSearch(query: string) {
  void router.push({ path: '/search', query: { q: query } })
}

async function rerun() {
  if (rerunning.value) {
    return
  }
  rerunning.value = true
  const previousTaskNo = history.value?.bizNo
  const previousWasError = actualView.value === 'error'
  try {
    const result = await rerunSearchResults(resultSetId.value)
    // 重新执行会生成新任务号，旧任务随之封存（旧任务已封存时忽略异常）
    if (previousTaskNo && !sealed.value && !previousWasError) {
      void sealTask(previousTaskNo).catch(() => undefined)
    }
    pageNum.value = 1
    await router.replace(`/search/results/${result.resultSetId}`)
    await load(result.resultSetId)
    showToast('检索已重新执行', 'ok')
  } catch (error) {
    showToast(errorText(error), 'err')
  } finally {
    rerunning.value = false
  }
}

function showToast(message: string, type: 'info' | 'ok' | 'err' = 'info') {
  toastMessage.value = message
  toastType.value = type
  toastVisible.value = true
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastVisible.value = false
  }, 2600)
}
</script>

<style scoped>
.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.fav-entry {
  height: 30px;
  padding: 0 14px;
  border: 1px solid #d7e0f0;
  border-radius: 999px;
  background: #fff;
  color: var(--muted);
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  font-variant-numeric: tabular-nums;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}
.fav-entry:hover {
  border-color: #b9c9ff;
  background: var(--system-blue-weak);
  color: var(--system-blue);
}

/* Z3 检索上下文条 */
.ctx-bar {
  padding: 13px 18px;
  border: 1px solid #e4edf8;
  border-radius: var(--radius);
  background: linear-gradient(180deg, #fff 0%, #f8fbff 100%);
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}
.ctx-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  color: var(--system-blue);
  text-decoration: none;
  padding: 5px 10px;
  border: 1px solid #d7e0ff;
  border-radius: 6px;
  background: #fff;
  white-space: nowrap;
  line-height: 1.2;
}
.ctx-back:hover {
  background: var(--system-blue-weak);
  border-color: #aebdf5;
}
.ctx-bar .ctx-q {
  font-size: 15px;
  font-weight: 700;
  color: #1f2a44;
  padding: 5px 12px;
  background: var(--system-blue-weak);
  border: 1px solid #d7e0ff;
  border-radius: 6px;
  max-width: 360px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ctx-item {
  font-size: 12px;
  color: var(--muted);
  display: flex;
  align-items: center;
  gap: 5px;
}
.ctx-item b {
  color: #1f2a44;
  font-weight: 600;
}
.pair-chip {
  font-size: 11px;
  font-weight: 600;
  color: var(--system-blue);
  background: var(--system-blue-weak);
  border: 1px solid #d7e0ff;
  border-radius: 4px;
  padding: 1px 7px;
}
.ctx-run {
  margin-left: 5px;
  font-weight: 600;
}
.ctx-spacer {
  flex: 1 1 auto;
}
.ctx-link {
  margin-left: auto;
  font-size: 12.5px;
  color: var(--system-blue);
  text-decoration: none;
  border-bottom: 1px dashed #aebdf5;
  padding-bottom: 1px;
  white-space: nowrap;
}
.ctx-link:hover {
  border-bottom-style: solid;
}

/* 重放条 */
.replay-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 8px 14px;
  border: 1px solid #f0dfb2;
  border-radius: 7px;
  background: #fffbf0;
  font-size: 12px;
  line-height: 1.6;
  color: #8a6a1f;
  font-variant-numeric: tabular-nums;
}
.replay-bar .rp-main {
  font-weight: 700;
  color: #8a5a00;
}
.replay-bar b {
  color: #8a5a00;
  font-weight: 700;
}
.rp-rerun {
  margin-left: auto;
  height: 28px;
  font-size: 12px;
}

/* Z4 结果集概要条 */
.rs-bar {
  padding: 12px 18px;
  border: 1px solid #dfe8fb;
  border-radius: var(--radius);
  background: #fff;
  display: flex;
  align-items: center;
  gap: 10px;
}
.rs-line {
  font-size: 14px;
  color: #1f2a44;
  font-variant-numeric: tabular-nums;
}

/* 封存提示条 */
.seal-tip {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 14px;
  border: 1px solid #e5e9f0;
  border-radius: 7px;
  background: #f8f9fc;
  font-size: 12px;
  line-height: 1.6;
  color: var(--muted);
}
.seal-tip .st-lock {
  font-size: 12px;
  line-height: 1;
  opacity: 0.8;
}

/* Z5 双栏布局 */
.dual-col {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  position: relative;
}
.p2-main-col {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.side-filter {
  border: 1px solid #e4edf8;
  border-radius: var(--radius);
  background: #fff;
  padding: 14px 16px;
  position: sticky;
  top: 56px;
}
.side-filter .sf-head {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
}
.side-filter .sf-title {
  font-size: 13px;
  font-weight: 700;
  color: #1f2a44;
}
.filter-block {
  padding: 11px 0;
  border-bottom: 1px dashed #eef2f8;
}
.filter-block:last-of-type {
  border-bottom: 0;
}
.filter-block label {
  display: block;
  font-size: 12px;
  color: var(--muted);
  font-weight: 600;
  margin-bottom: 7px;
}
.filter-block .fb-val {
  font-size: 12px;
  color: var(--system-blue);
  font-weight: 600;
  float: right;
}
.sim-range input[type='range'] {
  width: 100%;
  -webkit-appearance: none;
  height: 4px;
  border-radius: 2px;
  background: #e6edf8;
  outline: none;
}
.sim-range input[type='range']::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid var(--system-blue);
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(65, 105, 246, 0.35);
}
.sim-range input[type='range']::-moz-range-thumb {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid var(--system-blue);
  cursor: pointer;
}

.src-list {
  max-height: 260px;
  overflow-y: auto;
  padding: 4px;
  border: 1px solid #e6edf8;
  border-radius: 6px;
  background: #fbfdff;
}
.src-list::-webkit-scrollbar {
  width: 6px;
}
.src-list::-webkit-scrollbar-thumb {
  background: #d7e0ef;
  border-radius: 3px;
}
.src-item {
  width: 100%;
  height: 32px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--ink-body);
  font-size: 12.5px;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  white-space: nowrap;
}
.src-item:hover {
  background: var(--system-blue-weak);
  color: var(--system-blue);
}
.src-item .src-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex: 0 0 auto;
  border: 1.5px solid #bcc8dc;
  background: #fff;
}
.src-item .src-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}
.src-item .src-cnt {
  flex: 0 0 auto;
  font-size: 11px;
  font-weight: 400;
  color: #9aa6bd;
  font-variant-numeric: tabular-nums;
}
.src-item.on {
  background: var(--system-blue-weak);
  color: var(--system-blue);
  font-weight: 600;
}
.src-item.on .src-dot {
  background: var(--system-blue);
  border-color: var(--system-blue);
  box-shadow: 0 0 0 2px rgba(65, 105, 246, 0.14);
}
.src-item.on .src-cnt {
  color: #6b86ff;
}
.sf-reset {
  width: 100%;
  height: 30px;
  margin-top: 10px;
  border: 1px solid #d7e0f0;
  border-radius: 5px;
  background: #fff;
  color: var(--ink-body);
  font-size: 12px;
  cursor: pointer;
}
.sf-reset:hover {
  border-color: var(--system-blue);
  color: var(--system-blue);
}

/* 展示方案条 */
.present-bar {
  border: 1px solid #e4edf8;
  border-radius: var(--radius);
  background: #fff;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}
.gtabs {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.gtab {
  height: 30px;
  padding: 0 13px;
  border: 1px solid transparent;
  border-radius: 15px;
  background: transparent;
  color: var(--muted);
  font-size: 12.5px;
  cursor: pointer;
}
.gtab:hover {
  background: var(--system-blue-weak);
  color: var(--system-blue);
}
.gtab.active {
  background: var(--system-blue);
  color: #fff;
  font-weight: 600;
}
.gtab.zero:not(.active) {
  color: #aab4c8;
}
.pb-sep {
  width: 1px;
  height: 20px;
  background: var(--content-line);
}
.pb-ctrl {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  color: var(--muted);
}
.pb-sort {
  height: 30px;
  border: 1px solid var(--control-line);
  border-radius: 5px;
  background: #fff;
  color: var(--ink-body);
  font-size: 12px;
  padding: 0 8px;
  outline: none;
}
.switch {
  position: relative;
  width: 34px;
  height: 18px;
  border-radius: 9px;
  background: #dbe3f2;
  border: 0;
  transition: background 0.15s;
  flex-shrink: 0;
  cursor: pointer;
}
.switch.on {
  background: var(--system-blue);
}
.switch::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #fff;
  transition: left 0.15s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}
.switch.on::after {
  left: 18px;
}

/* 结果卡片 */
.result-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 10px;
}
.list-head b {
  color: #1f2a44;
}
.bl-note {
  margin: -4px 0 10px;
  font-size: 11.5px;
  line-height: 1.6;
  color: #98a2b3;
  font-variant-numeric: tabular-nums;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.bl-note b {
  color: var(--muted);
  font-weight: 700;
}
.bl-link {
  border: 0;
  background: transparent;
  padding: 0;
  margin: 0;
  color: var(--system-blue);
  font-size: 11.5px;
  line-height: 1.6;
  font-family: inherit;
  border-bottom: 1px dashed #aebdf5;
  white-space: nowrap;
  cursor: pointer;
}
.bl-link:hover {
  border-bottom-style: solid;
  background: var(--system-blue-weak);
}
.filter-empty {
  border: 1px solid #e4edf8;
  border-radius: 10px;
  background: #fff;
  padding: 38px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.filter-empty .fe-ico {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--system-blue-weak);
  display: grid;
  place-items: center;
  font-size: 20px;
}
.filter-empty h4 {
  font-size: 14px;
  font-weight: 600;
  color: #1f2a44;
  margin: 0;
}
.rcard-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.rcard {
  border: 1px solid #e4edf8;
  border-radius: 10px;
  background: #fff;
  display: flex;
  gap: 12px;
  padding: 12px;
  cursor: pointer;
  position: relative;
  transition: transform 0.15s, box-shadow 0.15s, border-color 0.15s;
}
.rcard:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(31, 58, 122, 0.09);
  border-color: #c8d6f5;
}
.rcard:focus-visible {
  outline: 2px solid #cdd9ff;
  outline-offset: 2px;
}
.rc-info {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.rc-line1 {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
}
.rc-line1 b {
  font-size: 13.5px;
  color: #1f2a44;
}
.rc-line1 .badge {
  font-size: 10.5px;
  padding: 1px 7px;
  border-radius: 9px;
  font-weight: 600;
}
.badge.mod-img {
  background: #eef3ff;
  color: var(--system-blue);
}
.badge.mod-video {
  background: #f0ebff;
  color: #7a5fd3;
}
.badge.mod-audio {
  background: #e6f7f1;
  color: #0f9d6e;
}
.rc-src {
  font-size: 11.5px;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.rc-align {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #9aa6bd;
  flex-wrap: wrap;
}
.al-chip {
  font-size: 11px;
  font-weight: 600;
  border-radius: 4px;
  padding: 1px 7px;
  color: #fff;
  font-variant-numeric: tabular-nums;
  font-style: normal;
}
.rc-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  flex-shrink: 0;
}
.rc-sim {
  font-size: 19px;
  font-weight: 700;
  color: var(--system-blue);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}
.rc-sim small {
  display: block;
  font-size: 10px;
  color: #9aa6bd;
  font-weight: 400;
  text-align: center;
  margin-top: 2px;
}
.rc-ops {
  display: flex;
  gap: 6px;
}

/* 单条评价 */
.rc-vote {
  height: 22px;
  padding: 0 7px;
  border: 1px solid transparent;
  border-radius: 5px;
  background: transparent;
  color: #98a2b3;
  font-size: 11.5px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  white-space: nowrap;
  cursor: pointer;
}
.rc-vote.up:hover {
  color: #1e8e4a;
  background: #eefaf2;
  border-color: #c6e9d1;
}
.rc-vote.down:hover {
  color: var(--error);
  background: #fef3f2;
  border-color: #f3c4c6;
}
.rc-vote.done.up {
  color: #1e8e4a;
  background: #e8f7ec;
  border-color: #bfe6cb;
  font-weight: 600;
}
.rc-vote.done.down {
  color: var(--error);
  background: #fdecec;
  border-color: #f3c4c6;
  font-weight: 600;
}
.rc-vote.muted {
  color: #c6cedb;
}
.rc-vote.sealed,
.rc-vote.sealed:hover {
  color: #b6bdc9;
  background: #f5f7fa;
  border-color: #e8ecf2;
  font-weight: 400;
  cursor: not-allowed;
}

/* 收藏 */
.rc-fav {
  height: 22px;
  padding: 0 7px;
  border: 1px solid transparent;
  border-radius: 5px;
  background: transparent;
  color: #98a2b3;
  font-size: 11.5px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  white-space: nowrap;
  cursor: pointer;
}
.rc-fav:hover {
  color: var(--warning);
  background: #fff8e1;
  border-color: #f2d79b;
}
.rc-fav.on {
  color: var(--warning);
  font-weight: 600;
  background: #fff8e1;
  border-color: #f2d79b;
}

/* 骨架屏 */
.skel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.skel-card {
  border: 1px solid #eef2f8;
  border-radius: 10px;
  background: #fff;
  padding: 12px;
  display: flex;
  gap: 12px;
}
.skel-block {
  border-radius: 7px;
  background: linear-gradient(90deg, #eef2f8, #f7f9fd, #eef2f8);
  background-size: 200% 100%;
  animation: shimmer 1.2s infinite;
}
@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}
.sk-thumb {
  width: 110px;
  height: 62px;
  flex-shrink: 0;
}
.sk-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  justify-content: center;
}
.sk-line {
  height: 12px;
  border-radius: 4px;
}
.sk-line.w60 {
  width: 60%;
}
.sk-line.w80 {
  width: 80%;
}
.sk-line.w40 {
  width: 40%;
}

/* 空结果态 */
.empty-wrap {
  border: 1px solid #e4edf8;
  border-radius: var(--radius);
  background: #fff;
  padding: 26px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.state-ico {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: var(--system-blue-weak);
  display: grid;
  place-items: center;
  font-size: 26px;
  margin-bottom: 12px;
}
.empty-wrap h3 {
  font-size: 16px;
  color: #1f2a44;
  margin: 0 0 6px;
}
.empty-wrap .st-desc {
  font-size: 12.5px;
  color: var(--muted);
  text-align: center;
  max-width: 560px;
  margin: 0 0 18px;
}
.cause-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}
.cause-card {
  border: 1px solid #e0e9f7;
  border-radius: 10px;
  padding: 14px 14px 16px;
  background: linear-gradient(180deg, #fff 0%, #f9fbff 100%);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.cause-card .cc-tag {
  align-self: flex-start;
  font-size: 11px;
  font-weight: 700;
  border-radius: 3px;
  padding: 2px 8px;
}
.cc-tag.high {
  background: #fdecec;
  color: var(--error);
}
.cc-tag.mid {
  background: #fff4e0;
  color: #b37514;
}
.cc-tag.low {
  background: #eef2f8;
  color: var(--muted);
}
.cause-card h4 {
  font-size: 13.5px;
  color: #1f2a44;
  margin: 0;
}
.cause-card p {
  font-size: 12px;
  color: var(--muted);
  line-height: 1.55;
  flex: 1;
  margin: 0;
}
.cause-card .cc-act {
  margin-top: 4px;
}
.ghost-link {
  display: inline-flex;
  align-items: center;
  text-decoration: none;
  color: var(--system-blue);
  border-color: #aebdf5;
}
.simq-list {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.simq-item {
  text-align: left;
  border: 1px solid var(--content-line-strong);
  border-radius: 6px;
  background: #fff;
  padding: 6px 9px;
  font-size: 12px;
  color: var(--ink-body);
  cursor: pointer;
}
.simq-item:hover {
  border-color: var(--system-blue);
  color: var(--system-blue);
  background: var(--system-blue-weak);
}

/* 空结果处置工具 */
.zero-tools {
  width: 100%;
  margin-top: 14px;
  border-top: 1px dashed #e6edf8;
  padding-top: 12px;
  text-align: left;
}
.cm-acts {
  display: flex;
  gap: 7px;
  flex-wrap: wrap;
  align-items: center;
}
.cm-btn {
  height: 26px;
  padding: 0 9px;
  border: 1px solid #d7e0f0;
  border-radius: 5px;
  background: #fff;
  color: var(--ink-body);
  font-size: 11.5px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  cursor: pointer;
}
.cm-btn:hover {
  border-color: var(--system-blue);
  color: var(--system-blue);
}
.cm-btn.pri {
  background: var(--system-blue);
  border-color: var(--system-blue);
  color: #fff;
}
.cm-btn.pri:hover {
  background: var(--system-blue-hover);
  color: #fff;
}
.cm-tag {
  font-size: 10.5px;
  border-radius: 3px;
  padding: 1px 6px;
  font-weight: 600;
  background: #f1f5ff;
  color: var(--system-blue);
  white-space: nowrap;
}
.cm-tag.red {
  background: #fdecec;
  color: var(--error);
}
.cm-tag.green {
  background: #e8f7ec;
  color: var(--success);
}
.cm-tag.gray {
  background: #f2f4f8;
  color: var(--muted-badge);
}
.cm-tag.warn {
  background: #fff4e0;
  color: #b37514;
}
.cm-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.cm-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--ink-body);
  border: 1px solid #eef2f8;
  border-radius: 6px;
  padding: 6px 9px;
  background: #fff;
  text-align: left;
  flex-wrap: wrap;
}
.cm-item .k {
  font-weight: 600;
  color: #1f2a44;
}
.cm-item .sp {
  margin-left: auto;
  display: flex;
  gap: 6px;
}
.cm-plan-meta {
  color: var(--muted);
}
.cm-plan-note {
  flex-basis: 100%;
  font-size: 11.5px;
  color: #9aa6bd;
  margin-top: 3px;
}
.cm-plan-done {
  font-size: 11px;
  color: #9aa6bd;
}
.cm-plan-empty {
  border-style: dashed;
  color: #9aa6bd;
  font-size: 11.5px;
}
.cm-log {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.cm-log-row {
  display: flex;
  gap: 7px;
  align-items: flex-start;
  font-size: 11.5px;
  color: var(--ink-body);
  background: #fbfdff;
  border: 1px dashed #e6edf8;
  border-radius: 6px;
  padding: 5px 8px;
  text-align: left;
}
.cm-log-row time {
  margin-left: auto;
  color: #9aa6bd;
  white-space: nowrap;
}
.zero-log-empty {
  font-size: 11.5px;
  color: #9aa6bd;
  padding: 3px 0;
}
.cm-panel {
  margin-top: 8px;
  border: 1px solid #eaf0f9;
  border-radius: 8px;
  padding: 10px 12px;
  background: var(--content-soft);
}
.cm-panel-t {
  font-size: 12px;
  font-weight: 700;
  color: #1f2a44;
  margin-bottom: 7px;
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
}
.cm-panel-body {
  font-size: 12px;
  line-height: 1.75;
  color: var(--ink-body);
}
.cm-row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  margin-bottom: 9px;
  text-align: left;
}
.cm-row > label {
  font-size: 12px;
  color: var(--ink-body);
}
.cm-row select,
.cm-row input[type='text'],
.cm-row input[type='date'],
.cm-row textarea {
  width: 100%;
  min-height: 30px;
  border: 1px solid var(--control-line);
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 12.5px;
  font-family: inherit;
  outline: none;
  background: #fff;
  color: #1f2a44;
}
.cm-row textarea {
  min-height: 54px;
  resize: vertical;
}
.cm-row.textarea {
  align-items: start;
}
.cm-ck {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--ink-body);
  padding: 5px 0;
  text-align: left;
}
.cm-ck input {
  width: 14px;
  height: 14px;
  accent-color: var(--system-blue);
  cursor: pointer;
}
.mb-tip {
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 10px;
  line-height: 1.6;
}
.mf-note {
  font-size: 11px;
  color: #b3bccc;
  margin-right: auto;
}

/* 错误态 / 边界态 */
.state-wrap {
  border: 1px solid #e4edf8;
  border-radius: var(--radius);
  background: #fff;
  padding: 34px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.state-wrap h3 {
  font-size: 16px;
  color: #1f2a44;
  margin: 0 0 6px;
}
.state-wrap .st-desc {
  font-size: 12.5px;
  color: var(--muted);
  max-width: 720px;
  margin: 0 0 16px;
  line-height: 1.6;
}
.state-wrap .err-code {
  font-size: 12px;
  color: var(--error);
  background: #fdecec;
  border: 1px solid #f3c4c6;
  border-radius: 5px;
  padding: 4px 12px;
  margin-bottom: 16px;
  font-variant-numeric: tabular-nums;
}
.state-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
}

.btn-primary {
  height: 34px;
  padding: 0 20px;
  border: 0;
  border-radius: 6px;
  background: var(--system-blue);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  text-decoration: none;
  box-shadow: 0 3px 8px rgba(65, 105, 246, 0.25);
  transition: all 0.15s;
  cursor: pointer;
}
.btn-primary:hover {
  background: var(--system-blue-hover);
  box-shadow: 0 4px 12px rgba(65, 105, 246, 0.32);
}
.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
}
.btn-ghost {
  height: 34px;
  padding: 0 16px;
  border-radius: 6px;
  border: 1px solid #d7e0f0;
  background: #fff;
  color: var(--ink-body);
  font-size: 13px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.btn-ghost:hover {
  border-color: var(--system-blue);
  color: var(--system-blue);
}

/* 查看弹窗：容器高度由媒体原始比例撑开，禁止固定高度导致媒体压住下方元数据 */
.view-media {
  height: auto;
  min-height: 120px;
  background: transparent;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--system-blue);
  border-radius: 8px;
  overflow: visible;
}
.view-media .vm-media {
  width: 100%;
  --preview-media-max: min(52vh, 420px);
  --preview-text-max: min(40vh, 320px);
}
.view-media .vm-ico {
  font-size: 44px;
  line-height: 1;
}
.view-media .vm-cap {
  font-size: 12px;
  color: #8fa0bd;
  max-width: 82%;
  text-align: center;
  line-height: 1.6;
}
.view-meta {
  padding: 10px 2px 0;
}
.vm-row {
  display: flex;
  gap: 14px;
  padding: 7px 0;
  border-bottom: 1px dashed #eef2f8;
  font-size: 13px;
  align-items: center;
}
.vm-row:last-child {
  border-bottom: 0;
}
.vm-k {
  flex: 0 0 62px;
  color: var(--text-secondary);
}
.vm-v {
  flex: 1;
  min-width: 0;
  color: #1f2a44;
  word-break: break-all;
}

/* 收藏夹弹窗 */
.fav-list {
  max-height: 56vh;
  overflow-y: auto;
}
.fav-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 13px 0;
  border-bottom: 1px solid #f0f4fb;
}
.fav-item:last-child {
  border-bottom: 0;
}
.fav-main {
  flex: 1;
  min-width: 0;
}
.fav-name {
  font-size: 13.5px;
  font-weight: 600;
  color: #1f2a44;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.fav-meta {
  font-size: 11.5px;
  color: var(--muted);
  margin-top: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-variant-numeric: tabular-nums;
}
.fav-ops {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.fav-act {
  height: 26px;
  padding: 0 10px;
  border: 1px solid #d7e0f0;
  border-radius: 5px;
  background: #fff;
  color: var(--ink-body);
  font-size: 11.5px;
  cursor: pointer;
}
.fav-act:hover {
  border-color: var(--system-blue);
  color: var(--system-blue);
}
.fav-act.del {
  color: #98a2b3;
}
.fav-act.del:hover {
  border-color: #f3c4c6;
  color: var(--error);
  background: #fef3f2;
}
.fav-empty {
  padding: 34px 10px;
  text-align: center;
  font-size: 12.5px;
  color: #9aa6bd;
  line-height: 1.7;
}

/* 过滤清单弹窗 */
.flt-sub {
  font-size: 11.5px;
  color: #9aa6bd;
  margin-bottom: 8px;
}
.flt-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
  table-layout: auto;
}
.flt-table th {
  background: #fbfcfe;
  text-align: left;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--muted);
  white-space: nowrap;
  padding: 9px 10px;
  border-bottom: 1px solid #e6edf8;
}
.flt-table td {
  padding: 9px 10px;
  border-bottom: 1px solid #f4f7fc;
  color: #1f2a44;
  vertical-align: middle;
  line-height: 1.6;
}
.flt-table tr:last-child td {
  border-bottom: 0;
}
.flt-table tbody tr:hover {
  background: #fbfdff;
}
.flt-id {
  font-family: var(--font-mono);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  color: var(--ink-body);
}
.flt-name {
  min-width: 190px;
  word-break: break-all;
}
.flt-src {
  color: var(--ink-body);
  min-width: 130px;
}
.flt-rsn {
  white-space: nowrap;
  width: 120px;
}
.flt-tag {
  font-size: 10.5px;
  font-weight: 600;
  border-radius: 3px;
  padding: 1px 7px;
  white-space: nowrap;
}
.flt-tag.bl {
  background: #fdecec;
  color: #b42318;
}
.flt-tag.ql {
  background: #fff4e0;
  color: #b37514;
}
.flt-tag.st {
  background: #eef2f8;
  color: var(--muted);
}
.flt-empty {
  padding: 34px 10px;
  text-align: center;
  font-size: 12.5px;
  color: #9aa6bd;
  line-height: 1.7;
}
.flt-sum {
  margin-top: 10px;
  font-size: 12px;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}
.flt-sum b {
  color: #1f2a44;
  font-weight: 700;
}

/* toast */
.toast {
  position: fixed;
  top: 56px;
  left: 50%;
  transform: translateX(-50%) translateY(-12px);
  z-index: 200;
  background: #2b3245;
  color: #fff;
  font-size: 13px;
  padding: 9px 18px;
  border-radius: 7px;
  box-shadow: 0 8px 24px rgba(20, 32, 70, 0.25);
  opacity: 0;
  pointer-events: none;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 7px;
}
.toast.show {
  opacity: 1;
  transform: translateX(-50%) translateY(0);
}
.toast.err {
  background: #c0392b;
}
.toast.ok {
  background: #1e8e4a;
}

@media (max-width: 1100px) {
  .dual-col {
    grid-template-columns: 1fr;
  }
  .side-filter {
    position: static;
  }
  .rcard-grid,
  .skel-grid {
    grid-template-columns: 1fr;
  }
  .cause-grid {
    grid-template-columns: 1fr;
  }
}
</style>
