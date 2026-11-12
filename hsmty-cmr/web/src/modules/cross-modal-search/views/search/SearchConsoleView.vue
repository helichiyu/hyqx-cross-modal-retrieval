<template>
  <div v-if="!canSearch" class="page-card console-page perm-page">
    <div class="cm-empty perm-gate">
      <div class="pg-ico" aria-hidden="true">
        <el-icon :size="20"><component :is="'Lock'" /></el-icon>
      </div>
      <h3 class="cm-empty-title">当前账号未开通检索权限</h3>
      <p class="cm-empty-desc">
        检索台由登录态与权限配置决定访问范围；如需使用，请联系管理员开通检索权限后重新进入。
      </p>
    </div>
  </div>

  <div v-else class="page-card console-page">
    <!-- Hero：检索大入口 -->
    <section class="hero" aria-labelledby="heroTitle">
      <button class="dev-entry" type="button" title="研发说明" @click="devOpen = true">
        <span class="de-q" aria-hidden="true">?</span>研发说明
      </button>

      <div class="hero-inner">
        <h1 id="heroTitle">跨模态<span>检索</span></h1>
        <p class="hero-sub">用一句话、一张图、一段音频，检索图文音视频资产</p>

        <div
          class="searchbox"
          :class="{ 'is-error': actionError, drag: searchboxDragging }"
          @dragover.prevent="searchboxDragging = true"
          @dragleave="searchboxDragging = false"
          @drop.prevent="onSearchboxDrop"
          @click="onSearchboxClick"
        >
          <span class="search-ico" aria-hidden="true">
            <el-icon :size="18"><component :is="'Search'" /></el-icon>
          </span>
          <div class="search-field">
            <input
              ref="queryInputRef"
              v-model="query"
              type="text"
              maxlength="512"
              autocomplete="off"
              spellcheck="false"
              aria-label="检索内容"
              placeholder="输入一句话描述，或拖入图片 / 音频 / 视频…"
              @input="onQueryInput"
              @keydown.enter.prevent="submitSearch"
              @focus="onQueryFocus"
              @blur="onQueryBlur"
            >
            <div class="suggest-pop" :class="{ show: suggestVisible }" role="listbox" aria-label="输入联想">
              <template v-if="recentSuggestions.length">
                <div class="suggest-group">最近搜索</div>
                <button
                  v-for="item in recentSuggestions"
                  :key="`r-${item}`"
                  class="suggest-item"
                  type="button"
                  @mousedown.prevent="refill(item)"
                >{{ item }}<span class="s-hist">最近</span></button>
              </template>
              <template v-if="exampleSuggestions.length">
                <div class="suggest-group">示例搜索</div>
                <button
                  v-for="item in exampleSuggestions"
                  :key="`e-${item}`"
                  class="suggest-item"
                  type="button"
                  @mousedown.prevent="refill(item)"
                >{{ item }}</button>
              </template>
            </div>
          </div>
          <button class="btn-search" type="button" :disabled="searching || recognizing" @click="submitSearch">
            <el-icon :size="16"><component :is="'Search'" /></el-icon>
            <span v-if="searching"><span class="spinner"></span> 检索中</span>
            <span v-else>检索</span>
          </button>
        </div>

        <div class="inline-err action-err" :class="{ show: actionError }" role="alert">{{ actionError }}</div>

        <div class="search-meta">
          <div class="mode-group" :class="{ nudge: modeNudge }" role="group" aria-label="检索类型快捷选择">
            <span class="mg-label">检索类型</span>
            <button
              v-for="tab in tabs"
              :key="tab.key"
              class="mode-tab"
              type="button"
              :class="{ active: target === tab.key }"
              :aria-pressed="target === tab.key"
              @click="selectTarget(tab.key)"
            >
              <el-icon :size="17"><component :is="tab.icon" /></el-icon>{{ tab.label }}
            </button>
          </div>

          <div class="stg-pick">
            <label class="sp-label" for="stgSelect">检索策略</label>
            <select
              id="stgSelect"
              class="stg-select"
              :value="strategyId"
              :disabled="!strategyList.length"
              @change="onStrategyChange"
            >
              <option v-if="!strategyList.length" value="">系统默认策略（兜底）</option>
              <option v-for="item in strategyList" :key="item.id" :value="String(item.id)">
                {{ item.name }} · {{ item.paramSummary }}
              </option>
            </select>
            <span v-if="strategyFallback" class="stg-fb">暂无生效策略，将按默认参数检索</span>
            <span v-else class="stg-diff">{{ strategySummary }}</span>
          </div>
        </div>

        <div class="search-aux">
          <span class="pa">当前模态对 <b>{{ target === '全部' ? '全部' : selectedPair }}</b></span>
          <span class="aux-right">
            <span class="ca"><b>{{ query.length }}</b>/512</span>
            <button class="link-btn" type="button" @click="resetAll">清空</button>
          </span>
        </div>
      </div>
    </section>

    <!-- 一屏两列：左模态矩阵 / 右侧竖版上传 -->
    <section class="setup" aria-label="模态矩阵与文件上传">
      <div class="mt-and-upload">
        <div class="cm-panel matrix-panel">
          <div class="matrix-card">
            <div class="setup-head">
              <span class="ph-ico" aria-hidden="true"><el-icon :size="17"><component :is="'Grid'" /></el-icon></span>
              <h3>模态矩阵</h3>
              <span class="setup-hint">查询模态 × 目标模态</span>
            </div>

            <div class="matrix-wrap">
              <div class="matrix-legend">
                <span class="lg">
                  <span class="mk" aria-hidden="true"><el-icon :size="12"><component :is="'Check'" /></el-icon></span>支持
                </span>
              </div>
              <div class="modal-matrix" role="grid" aria-label="模态矩阵">
                <div class="mm-corner"></div>
                <div v-for="col in modes" :key="`c-${col}`" class="mm-colh">{{ col }}</div>
                <template v-for="row in matrix" :key="row.row">
                  <div class="mm-rowh" :style="{ opacity: rowDisabled(row.row) ? 0.35 : 1 }">{{ row.row }}</div>
                  <div
                    v-for="cell in row.cells"
                    :key="cell.pair"
                    class="mm-cell available"
                    :class="{ selected: selectedPair === cell.pair, dim: rowDisabled(row.row) }"
                    :title="cell.pair"
                    role="button"
                    tabindex="0"
                    @click="selectPair(cell.pair)"
                    @keydown.enter.prevent="selectPair(cell.pair)"
                  >
                    <el-icon :size="15"><component :is="'Check'" /></el-icon>
                  </div>
                </template>
              </div>
            </div>
          </div>
        </div>

        <div class="upload-panel">
          <div
            class="dropzone"
            :class="{ drag: dropDragging, success: uploadState === 'success' }"
            @click="triggerFileSelect"
            @dragover.prevent="dropDragging = true"
            @dragleave="dropDragging = false"
            @drop.prevent="onDropzoneDrop"
          >
            <div v-if="uploadState === 'default'" class="dz-default">
              <div class="dz-ico" aria-hidden="true">
                <el-icon :size="20"><component :is="'Upload'" /></el-icon>
              </div>
              <strong>拖拽或点击上传图片 / 音频 / 视频</strong>
              <span>自动识别查询模态 · JPG/PNG/MP4/WAV/MP3 · 单文件 ≤ 100MB</span>
              <div class="dz-types"><em>JPG/PNG</em><em>MP4</em><em>WAV/MP3</em></div>
            </div>

            <div v-else class="dz-file">
              <div class="f-thumb">
                <span v-if="recognizing" class="spinner dark"></span>
                <el-icon v-else :size="19"><component :is="fileIcon" /></el-icon>
              </div>
              <div class="f-meta">
                <div class="f-name">{{ queryFileName }}</div>
                <div class="f-rec">{{ uploadMessage }}</div>
              </div>
              <button class="dz-clear" type="button" title="移除文件" aria-label="移除文件" @click.stop="clearFile">
                <el-icon :size="14"><component :is="'Close'" /></el-icon>
              </button>
            </div>

            <input
              ref="fileInput"
              type="file"
              accept=".jpg,.jpeg,.png,.mp4,.wav,.mp3"
              hidden
              @click.stop
              @change="onFileInputChange"
            >
          </div>
          <div class="inline-err upload-err" :class="{ show: uploadError }" role="alert">{{ uploadError }}</div>
        </div>
      </div>
    </section>

    <!-- 意图澄清浮层（F5） -->
    <div class="clarify-mask" :class="{ open: clarify.open }" @click="closeClarify"></div>
    <div class="clarify-modal" :class="{ open: clarify.open }" role="dialog" aria-modal="true" aria-label="意图澄清">
      <div class="clarify-head">
        <h3>意图澄清</h3>
        <button class="clarify-close" type="button" title="关闭（Esc）" aria-label="关闭" @click="closeClarify">×</button>
      </div>
      <div class="clarify-body">
        <p class="clarify-lead">「{{ clarify.raw }}」较短或存在歧义，你是不是想找：</p>
        <div v-if="!clarify.options.length" class="clarify-empty">
          {{ clarify.hint || '请补充更具体的描述（对象、场景、动作）' }}
        </div>
        <div v-else class="clarify-list">
          <button
            v-for="item in clarify.options"
            :key="item"
            class="clarify-opt"
            type="button"
            @click="pickClarify(item)"
          >{{ item }}<span class="co-arrow" aria-hidden="true">→</span></button>
        </div>
        <div class="clarify-foot">
          <button class="clarify-orig" type="button" @click="keepOriginalQuery">仍按原词检索</button>
          <span class="clarify-hint">每个 query 最多澄清 1 轮</span>
        </div>
      </div>
    </div>

    <div class="toast" :class="{ show: toast.visible, err: toast.type === 'err' }">{{ toast.message }}</div>

    <DevNotesDrawer v-model="devOpen" title="研发需求说明">
      <div class="dev-card">
        <div class="dc-head">
          <span class="dc-id" :style="{ background: 'var(--system-blue)' }">总览</span>
          <span class="dc-name">页面定位 / 闭环 / 功能清单 / 设计出处</span>
        </div>
        <div class="dc-body">
          <div class="ov-grid">
            <div class="dev-ov">
              <h4>本页定位【设计】</h4>
              <p>全系统唯一查询入口：输入文本描述或图/音频/视频文件，选择「查询模态 × 目标模态」发起检索；结果进入 <span class="dev-xref">P2</span> 结果页确认与查看。</p>
            </div>
            <div class="dev-ov">
              <h4>平台全景闭环【研发】</h4>
              <p>检索台发起检索 → <span class="dev-xref">P6</span> 搜索行为调度运行 → <span class="dev-xref">P2</span> 结果页确认/查看 → <span class="dev-xref">P7</span> 结果运营（反馈处理）→ 反馈归因到 <span class="dev-xref">P5</span> 策略调整（<span class="dev-xref">P8</span> 评测验证）→ 策略发布后下次检索生效；<span class="dev-xref">P4</span> 数据源库管理持续供给检索库。八页即此闭环的八个切面。</p>
            </div>
            <div class="dev-ov">
              <h4>功能清单【设计】</h4>
              <p>本页主功能：发起检索并携参跳转结果页（F7）、输入联想（F6）、意图澄清（F5）；全局件：多模态输入（F1/F2）、模态矩阵（F3）、检索策略（F4）、权限态（F8）。完整历史在 <span class="dev-xref">P6</span> 搜索行为。</p>
            </div>
            <div class="dev-ov">
              <h4>检索类型口径【设计】</h4>
              <p>检索类型用于收窄目标模态：默认「全部」不限定目标模态，按统一向量空间跨模态混排返回，结果按相似度排序；选择具体模态（图像/文本/音频/视频）时，按当前查询模态自动选对应模态对（如文本→图像=「文搜图」），无对应组合时 toast「{查询模态}输入暂不支持检索「{目标模态}」结果」并保持原选择。</p>
            </div>
            <div class="dev-ov">
              <h4>设计出处与演示性质【设计】</h4>
              <p>依据 L2 设计_13 页面架构地图 / L3 设计_15 模板 / L4 设计_16 基础件生成；模态能力：4×4 全矩阵 16 种模态对全部可检索（4 模态统一共享空间对齐，2026-10-27 确认）。示例数据不构成交付承诺。</p>
            </div>
          </div>
        </div>
      </div>

      <div class="dev-card">
        <div class="dc-head"><span class="dc-id">F1</span><span class="dc-name">文本检索输入</span></div>
        <div class="dc-body">
          <div class="dc-row"><div class="dc-k">职责</div><div class="dc-v">输入查询文本，作为文本检索（文搜X）的查询语义；与文件同时存在时以文本 q 发起检索，文件只作上下文。</div></div>
          <div class="dc-row"><div class="dc-k">入口/前置</div><div class="dc-v">常驻可用；文本与文件二选一即可，不强制二者都有。</div></div>
          <div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>用户手输文本</li><li>单行大输入框（Hero 搜索框），最多 512 字</li><li>占位文案「输入一句话描述，或拖入图片 / 音频 / 视频…」；示例词由输入联想提供。</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">处理规则</div><div class="dc-v">实时字数统计（0/512，硬截断不允许超长）；输入去首尾空格后参与判定；去空格 ≥4 字触发 F6 联想下拉。</div></div>
          <div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>作为 Query 参数进入 F7 检索</li></ul></div></div>
          <div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>仅空格等同未输入</li><li>达到 512 字后无法继续输入</li><li>无文本也无文件时点检索，红字提示「请输入查询描述，或上传图片 / 视频 / 音频文件」，3.5 秒自动消失。</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">状态</div><div class="dc-v">默认空；聚焦输入态；错误态=输入框红框+行内红字。</div></div>
          <div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">下游 F6 联想 / F7 检索。</div></div>
          <div class="dc-row"><div class="dc-k">验收点</div><div class="dc-v">512 字截断生效；计数实时；空输入拦截并提示。</div></div>
        </div>
      </div>

      <div class="dev-card">
        <div class="dc-head"><span class="dc-id">F2</span><span class="dc-name">文件输入与查询模态自动识别</span></div>
        <div class="dc-body">
          <div class="dc-row"><div class="dc-k">职责</div><div class="dc-v">上传图/视频/音频文件，系统自动识别查询模态并点亮对应矩阵行。</div></div>
          <div class="dc-row"><div class="dc-k">入口/前置</div><div class="dc-v">拖拽区常驻；未上传时点击或拖入；已上传后该区显示文件卡片。Hero 搜索框也接受拖入文件，与拖拽区走同一 handleFile 流程。</div></div>
          <div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>单文件</li><li>支持 JPG/JPEG/PNG/MP4/WAV/MP3</li><li>页面文案承诺单文件 ≤100MB。</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">处理规则</div><div class="dc-v">按扩展名判定模态：jpg/jpeg/png→图像、mp4→视频、wav/mp3→音频；识别约 0.9 秒加载态；成功后写入 queryMode，矩阵仅保留该查询模态所在行可选（其余行置淡约 32%），非该行已选自动清除；音频额外默认选中「音搜音」。</div></div>
          <div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>文件模态=矩阵查询模态（行）</li><li>文件名与模态留在本页 state，不入 URL</li><li>成功后可点 × 移除并复位。</li></ul></div></div>
          <div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>类型不符→红字「不支持的文件类型…」并清空文件</li><li>识别中重复点检索→「文件识别中，请稍候」</li><li>上传失败清空文件与预览，避免用旧文件检索</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">状态</div><div class="dc-v">默认态（虚线上传区）/拖拽高亮/识别中（spinner）/成功（文件卡+识别结果）。</div></div>
          <div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">识别结果驱动 F3 矩阵行；文件名只留在本页 state，不入 URL。原始文件在正式系统留源系统，本中心只存检索要素。</div></div>
          <div class="dc-row"><div class="dc-k">验收点</div><div class="dc-v">JPG/JPEG/PNG/MP4/WAV/MP3 识别为正确模态；非法类型被拦截并给明确文案；成功后矩阵仅该行可选、音频自动选音搜音；×移除后完全复位。</div></div>
        </div>
      </div>

      <div class="dev-card">
        <div class="dc-head"><span class="dc-id">F3</span><span class="dc-name">模态矩阵选择（查询模态 × 目标模态）</span></div>
        <div class="dc-body">
          <div class="dc-row"><div class="dc-k">职责</div><div class="dc-v">选择「查询模态 × 目标模态」组合，确定本次检索的模态对。</div></div>
          <div class="dc-row"><div class="dc-k">入口/前置</div><div class="dc-v">与上传同处模态矩阵卡片内（一屏布局：矩阵在左、上传区在右）；4×4 全矩阵 16 种模态对全部可检索（4 模态统一共享空间对齐）；未开通库权限的账号不做置淡/锁定，点格时直接拦截。</div></div>
          <div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>4×4 矩阵</li><li>行=查询模态（文本/图像/音频/视频），列=目标模态（文本/图像/音频/视频）</li><li>上传文件后按 F2 自动锁定查询模态行。</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">处理规则</div><div class="dc-v">16 格均显示对勾、可点，点击置 selected（同时只允许一个，再点同一格不取消）；检索类型=全部时 pair=全部，不阻断检索。</div></div>
          <div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>选中模态对（如「文搜图」）进入 F7</li><li>页面「当前模态对」与 <span class="dev-xref">P2</span> 均显示该模态对。</li></ul></div></div>
          <div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>检索类型≠全部且无对应模态对时→红字「请先选择检索类型（全部 / 图像 / 文本 / 音频 / 视频）」并抖动检索类型组</li><li>切换文件致查询模态行变化时，非该行已选自动清除</li><li>无权限账号不可选。</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">状态</div><div class="dc-v">可选/选中（蓝底）/置淡约 32%（文件锁定其他行）。</div></div>
          <div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">能力清单：4×4 全矩阵 16 种模态对全部可检索（4 模态统一共享空间对齐，2026-10-27 确认）；入参供 F7、<span class="dev-xref">P2</span>。</div></div>
          <div class="dc-row"><div class="dc-k">验收点</div><div class="dc-v">可选格恰好 16 个（4×4 全亮）；单选互斥；文件上传后非查询行置淡且跨行已选被清除。</div></div>
        </div>
      </div>

      <div class="dev-card">
        <div class="dc-head"><span class="dc-id">F4</span><span class="dc-name">检索策略选择（<span class="dev-xref">P5</span> 反推）</span></div>
        <div class="dc-body">
          <div class="dc-row"><div class="dc-k">职责</div><div class="dc-v">选择本次检索所用的检索策略：检索时直接选一条策略，策略决定本次检索的相似度阈值与重排参数。</div></div>
          <div class="dc-row"><div class="dc-k">入口/前置</div><div class="dc-v">常驻下拉，默认系统默认策略（兜底）；右侧小字显示该策略的参数摘要「阈值 x.xx · 重排开/关」。</div></div>
          <div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>选项来自 <span class="dev-xref">P5</span> 已生效策略（GET strategies/options，仅返回生效中策略）</li><li>下拉展示策略名 + 参数摘要</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">处理规则</div><div class="dc-v">选择即写入本次检索策略；右侧摘要随选项动态输出。若接口未返回任何生效策略，显示「暂无生效策略，将按默认参数检索」并保留系统默认兜底，检索仍可发起。</div></div>
          <div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>策略号作为入参进入 F7 → <span class="dev-xref">P2</span>（跳转参数 stg + thr）</li><li>本次检索使用该策略的阈值与重排参数</li></ul></div></div>
          <div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>未知 / 已下线的策略号按系统默认策略处理</li><li>无生效策略时不禁用检索</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">状态</div><div class="dc-v">下拉默认系统默认策略；切换即时生效，无加载态。</div></div>
          <div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">上游=<span class="dev-xref">P5</span> 策略管理（唯一来源）；下游=F7、<span class="dev-xref">P2</span>。</div></div>
          <div class="dc-row"><div class="dc-k">验收点</div><div class="dc-v">选项与 P5 生效策略一致；默认选中兜底策略；右侧摘要与 P5 参数一致；跳转带 stg 与 thr 参数。</div></div>
        </div>
      </div>

      <div class="dev-card">
        <div class="dc-head"><span class="dc-id">F5</span><span class="dc-name">意图澄清（短词检索前的改写建议）</span></div>
        <div class="dc-body">
          <div class="dc-row"><div class="dc-k">职责</div><div class="dc-v">去空格后 &lt;4 字的短 query 在检索前给一次改写建议，帮助用户把过短输入补成可检索的描述；每个 query 只给一轮建议，不打断正常检索主流程。</div></div>
          <div class="dc-row"><div class="dc-k">入口/前置</div><div class="dc-v">点「检索」时触发；前置=有 query 且去空格长度 &lt; 4 且本轮未澄清过该 query。</div></div>
          <div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>用户输入的短 query（长度去空格后 &lt; 4）</li><li>本轮已澄清的 query 记录（会话存储 cmrClarify，最多 60 条，仅本页使用，刷新后仍在）</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">处理规则</div><div class="dc-v">弹出居中浮层（遮罩 + 约 520px 卡片）：标题「意图澄清」，正文「「{q}」较短或存在歧义，你是不是想找：」；候选取后端查询理解（407）返回的改写 query，接口无返回时按 query 拼模板兜底。</div></div>
          <div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>选候选 → 候选文本作为 query 进入 F7 检索流程（URL 的 q 即候选文本）</li><li>仍按原词 → 原 query 进入 F7 检索流程</li><li>关闭 → 不检索</li></ul></div></div>
          <div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>≥4 字不触发</li><li>同一 query 已澄清不再触发</li><li>纯文件检索（无 query）不触发</li><li>× / Esc 仅关闭，不检索、不标记</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">状态</div><div class="dc-v">隐藏 / 显示两态；显示时遮罩 + 卡片，点击遮罩或 × 或 Esc 均关闭。</div></div>
          <div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">上游=F1 检索输入、407 查询理解；下游=F7 发起检索并携参跳 <span class="dev-xref">P2</span>。</div></div>
          <div class="dc-row"><div class="dc-k">验收点</div><div class="dc-v">短词首次检索必弹且仅 1 轮；点候选后 URL 的 q 为候选文本；同一 query 第二次点检索不再弹；≥4 字与纯文件检索不弹；刷新后澄清记录仍在。</div></div>
        </div>
      </div>

      <div class="dev-card">
        <div class="dc-head"><span class="dc-id">F6</span><span class="dc-name">相似查询复用（输入联想）</span></div>
        <div class="dc-body">
          <div class="dc-row"><div class="dc-k">职责</div><div class="dc-v">输入时给最近搜索与示例搜索，减少重复输入。</div></div>
          <div class="dc-row"><div class="dc-k">入口/前置</div><div class="dc-v">输入框聚焦且去空格文本 ≥4 字时下拉显示（纯文件检索不触发）；失焦 150ms 后收起（保留点击时间）。</div></div>
          <div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>最近搜索=检索历史接口的 query 去重取前 3</li><li>示例搜索=本地示例词</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">处理规则</div><div class="dc-v">输入 ≥4 字即按包含匹配展示两组；无任何匹配时展示 3 条示例；mousedown 点选项（避免 blur 先收起）即回填到输入框、更新字数、收起下拉并聚焦。</div></div>
          <div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>回填文本进入 F1，等同手输</li><li>完整历史在 <span class="dev-xref">P6</span> 搜索行为。</li></ul></div></div>
          <div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>输入为空或 &lt;4 字不显示</li><li>Esc 收起</li><li>点击弹层外失焦收起</li><li>下拉项点击不被 blur 提前吞掉。</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">状态</div><div class="dc-v">隐藏/显示两态；分组标题「最近搜索」/「示例搜索」。</div></div>
          <div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">上游=检索历史接口；下游回填 F1。</div></div>
          <div class="dc-row"><div class="dc-k">验收点</div><div class="dc-v">≥4 字出现分组、&lt;4 字消失；无匹配时出现示例；点选项正确回填并聚焦；失焦/Esc 正常收起。</div></div>
        </div>
      </div>

      <div class="dev-card">
        <div class="dc-head"><span class="dc-id">F7</span><span class="dc-name">发起检索并携参跳转结果页</span></div>
        <div class="dc-body">
          <div class="dc-row"><div class="dc-k">职责</div><div class="dc-v">校验必填后组装检索上下文，执行检索并携参跳 <span class="dev-xref">P2</span> 结果页。</div></div>
          <div class="dc-row"><div class="dc-k">入口/前置</div><div class="dc-v">「检索」主按钮；另有「清空」复位搜索词与文件，保留策略选择。</div></div>
          <div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>Query（文本；纯文件检索时为空）、文件（可无）、模态对（可选；检索类型=全部时为空）、检索策略（默认系统默认策略）</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">检索流程</div><div class="dc-v">校验顺序：文件识别中拦截→文本与文件均空拦截→检索类型≠全部但无对应模态对拦截；通过后先判 F5：短 query 弹意图澄清，澄清出口再进入检索。检索中按钮禁用防重复提交；文件存在而无文本时不传 query。</div></div>
          <div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>携参跳 <span class="dev-xref">P2</span>：?from=P1&amp;back=P1&amp;task=&amp;q=&amp;pair=&amp;stg=&amp;thr=（q 为空时省略；文件不入 URL）</li><li>pair 未选时为「全部」，stg 为策略业务编号，thr 为该策略阈值（两位小数）</li></ul></div></div>
          <div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>三类前置校验各自给明确红字/toast</li><li>检索中按钮防重复提交</li><li>清空复位：文本、模态对、文件、错误提示、联想（策略保留）</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">状态</div><div class="dc-v">默认「检索」/「检索中」禁用；错误为行内红字 3.5 秒。</div></div>
          <div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">上游=F1-F6 全部入参；下游=<span class="dev-xref">P2</span> 结果页、<span class="dev-xref">P6</span> 搜索行为。</div></div>
          <div class="dc-row"><div class="dc-k">验收点</div><div class="dc-v">三种缺参分别被拦截且文案准确；通过后 q/pair/stg 与界面选择一致；检索中防连点；清空后搜索词与文件复位且策略不变。</div></div>
        </div>
      </div>

      <div class="dev-card">
        <div class="dc-head"><span class="dc-id">F8</span><span class="dc-name">权限态</span></div>
        <div class="dc-body">
          <div class="dc-row"><div class="dc-k">职责</div><div class="dc-v">声明本页可见范围与可操作项的权限来源：由登录态与权限配置决定。</div></div>
          <div class="dc-row"><div class="dc-k">入口/前置</div><div class="dc-v">进入页面时读取当前用户权限码；正式由登录态决定。</div></div>
          <div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>登录态身份（账号）</li><li>权限码 cmr:search:create</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">处理规则</div><div class="dc-v">拥有 cmr:search:create 按可写渲染；缺少该权限时整页权限拦截空态，不发起检索。</div></div>
          <div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>可写 / 权限拦截态</li></ul></div></div>
          <div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>无权限时不渲染检索台、不请求检索</li><li>权限变更后下次进入生效</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">状态</div><div class="dc-v">已授权 / 权限拦截。</div></div>
          <div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">上游=平台登录态与权限配置；本页无权限配置入口。</div></div>
          <div class="dc-row"><div class="dc-k">验收点</div><div class="dc-v">无权限整页拦截且不发起检索；有权限全部功能可用。</div></div>
        </div>
      </div>
    </DevNotesDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  executeSearch,
  pageHistory,
  strategyOptions,
  suggestWords,
  understand,
  uploadFile,
} from '../../api'
import type { StrategyOptionVO } from '../../api/types'
import { useAuthStore } from '../../stores/auth'
import DevNotesDrawer from '../../components/DevNotesDrawer.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const modes = ['文本', '图像', '音频', '视频'] as const
type Mode = typeof modes[number]
type TabKey = '全部' | Mode

const modeValues: Record<Mode, string> = {
  文本: 'text',
  图像: 'image',
  音频: 'audio',
  视频: 'video',
}

const tabs: { key: TabKey; label: string; icon: string }[] = [
  { key: '全部', label: '全部', icon: 'Grid' },
  { key: '图像', label: '图像', icon: 'Picture' },
  { key: '文本', label: '文本', icon: 'Document' },
  { key: '音频', label: '音频', icon: 'Microphone' },
  { key: '视频', label: '视频', icon: 'VideoCamera' },
]

const fileIcons: Record<Mode, string> = {
  文本: 'Document',
  图像: 'Picture',
  音频: 'Microphone',
  视频: 'VideoCamera',
}

const pairShort: Record<Mode, string> = { 文本: '文', 图像: '图', 音频: '音', 视频: '视频' }

const matrix = modes.map(row => ({
  row,
  cells: modes.map((col, index) => ({ pair: `${pairShort[row]}搜${pairShort[col]}`, index })),
}))

const CLARIFY_KEY = 'cmrClarify'
const REFILL_KEY = 'cmRefill'
const CLARIFY_MIN_CHARS = 4

const query = ref('')
const queryInputRef = ref<HTMLInputElement | null>(null)
const queryFile = ref('')
const queryFileName = ref('')
const queryMode = ref<Mode | null>(null)
const target = ref<TabKey>('全部')
const selectedPair = ref('')

const strategyId = ref('')
const strategyList = ref<StrategyOptionVO[]>([])
const strategyFallback = computed(() => strategyList.value.length === 0)
const selectedStrategy = computed(() => strategyList.value.find(item => String(item.id) === strategyId.value))
const strategySummary = computed(() => selectedStrategy.value?.paramSummary || '')

const searching = ref(false)
const recognizing = ref(false)
const uploadState = ref<'default' | 'success'>('default')
const uploadMessage = ref('')
const uploadError = ref('')
const actionError = ref('')
const searchboxDragging = ref(false)
const dropDragging = ref(false)
const modeNudge = ref(false)

const historySuggestions = ref<string[]>([])
const recentSuggestions = ref<string[]>([])
const exampleSuggestions = ref<string[]>([])
/** 示例搜索词来自后端推荐词库（/search/suggest），不再前端写死 */
const exampleWords = ref<string[]>([])
const suggestVisible = ref(false)

const clarify = reactive({
  open: false,
  raw: '',
  hint: '',
  options: [] as string[],
})

const devOpen = ref(false)
const toast = reactive({ visible: false, message: '', type: 'info' as 'info' | 'err' })
const fileInput = ref<HTMLInputElement>()

const canSearch = computed(() => !auth.user || auth.permissions.has('cmr:search:create'))

const fileIcon = computed(() => fileIcons[queryMode.value || '文本'])

let toastTimer: ReturnType<typeof setTimeout> | undefined
let actionTimer: ReturnType<typeof setTimeout> | undefined
let modeTimer: ReturnType<typeof setTimeout> | undefined

onMounted(async () => {
  await auth.ensureLoaded()
  if (!canSearch.value) {
    return
  }
  await loadStrategyOptions()
  await Promise.all([loadHistory(), loadExampleWords()])
  applyInitialParams()
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  clearTimeout(toastTimer)
  clearTimeout(actionTimer)
  clearTimeout(modeTimer)
})

async function loadStrategyOptions() {
  try {
    strategyList.value = await strategyOptions()
  } catch {
    strategyList.value = []
  }
  if (!strategyList.value.length) {
    strategyId.value = ''
    return
  }
  const preset = strategyList.value.find(item => item.preset)
  strategyId.value = String((preset ?? strategyList.value[0]).id)
}

async function loadHistory() {
  try {
    const page = await pageHistory({ pageNum: 1, pageSize: 20 })
    historySuggestions.value = [...new Set(page.records.map(item => (item.queryText || '').trim()).filter(Boolean))].slice(0, 20)
  } catch {
    historySuggestions.value = []
  }
}

/** 示例搜索词：运营维护的推荐词，场景取当前所选检索类型 */
async function loadExampleWords() {
  try {
    exampleWords.value = await suggestWords()
  } catch {
    exampleWords.value = []
  }
}

/** 进入页面回填：?q=&pair=&stg=&relax=，其次 localStorage.cmRefill（{query,pair,stg}） */
function applyInitialParams() {
  const qp = route.query
  const urlQuery = typeof qp.q === 'string' ? qp.q.trim() : ''
  const urlPair = typeof qp.pair === 'string' ? qp.pair : ''
  const urlStg = typeof qp.stg === 'string' ? qp.stg : ''

  let refill: { query?: string; q?: string; pair?: string; stg?: string } | null = null
  try {
    const raw = localStorage.getItem(REFILL_KEY)
    if (raw) {
      refill = JSON.parse(raw)
    }
  } catch {
    refill = null
  }

  const fillQuery = urlQuery || (refill?.query || refill?.q || '').trim()
  if (fillQuery) {
    query.value = fillQuery.slice(0, 512)
  }

  const fillPair = urlPair || refill?.pair || ''
  applyPair(fillPair)

  const fillStg = urlStg || refill?.stg || ''
  if (fillStg) {
    selectStrategy(fillStg)
  }

  if (refill) {
    try {
      localStorage.removeItem(REFILL_KEY)
    } catch {
      // 存储不可用时忽略
    }
  }

  if (qp.relax === '1' && fillQuery) {
    showToast('已放宽相似度阈值，按原 query 重新检索')
    void runSearch(undefined, true)
  }
}

function rowDisabled(row: string) {
  return !!queryMode.value && row !== queryMode.value
}

function pairColumnIndex(pair: string) {
  for (const row of matrix) {
    const hit = row.cells.find(cell => cell.pair === pair)
    if (hit) return hit.index
  }
  return -1
}

function selectPair(pair: string) {
  if (!pair) return
  selectedPair.value = pair
  const index = pairColumnIndex(pair)
  if (index >= 0) {
    target.value = modes[index]
  }
  hideActionError()
}

/** 回填 URL / cmRefill 的模态对：空或「全部」= 全部检索类型 */
function applyPair(pair: string) {
  const value = (pair || '').trim()
  if (!value || value === '全部') {
    selectedPair.value = ''
    target.value = '全部'
    return
  }
  const index = pairColumnIndex(value)
  if (index < 0) return
  selectedPair.value = value
  target.value = modes[index]
}

/** 选择检索类型：全部=清空模态对；具体模态按当前查询模态自动选对应模态对 */
function selectTarget(key: TabKey) {
  if (key === '全部') {
    target.value = '全部'
    selectedPair.value = ''
    hideActionError()
    return
  }
  const row = matrix.find(item => item.row === (queryMode.value || '文本'))
  const cell = row?.cells.find(item => item.index === modes.indexOf(key))
  if (!cell) {
    showToast(`${queryMode.value || '文本'}输入暂不支持检索「${key}」结果`, 'err')
    return
  }
  selectedPair.value = cell.pair
  target.value = key
  hideActionError()
}

/** 上传成功后按模态行收敛选择；音频默认选中「音搜音」 */
function applyFileFilter() {
  if (!queryMode.value) return
  const row = matrix.find(item => item.row === queryMode.value)
  if (selectedPair.value && !row?.cells.some(cell => cell.pair === selectedPair.value)) {
    selectedPair.value = ''
    target.value = '全部'
  }
}

function onQueryInput() {
  hideActionError()
  renderSuggest()
}

/** 联想：≥4 字触发，最近搜索（检索历史）+ 示例搜索；无匹配时展示示例 */
function renderSuggest() {
  const value = query.value.trim()
  if (value.length < CLARIFY_MIN_CHARS) {
    suggestVisible.value = false
    recentSuggestions.value = []
    exampleSuggestions.value = []
    return
  }
  const recent = historySuggestions.value.filter(item => item.includes(value)).slice(0, 3)
  let examples = exampleWords.value.filter(item => item.includes(value)).slice(0, 3)
  if (!recent.length && !examples.length) {
    examples = exampleWords.value.slice(0, 3)
  }
  recentSuggestions.value = recent
  exampleSuggestions.value = examples
  suggestVisible.value = Boolean(recent.length || examples.length)
}

function onSearchboxClick(event: MouseEvent) {
  const element = event.target as HTMLElement | null
  if (element?.closest('button, a, select, .suggest-pop')) return
  queryInputRef.value?.focus()
}

function onQueryFocus() {
  if (query.value.trim().length >= CLARIFY_MIN_CHARS) {
    renderSuggest()
  }
}

function onQueryBlur() {
  setTimeout(() => { suggestVisible.value = false }, 150)
}

function refill(value: string) {
  query.value = value
  suggestVisible.value = false
  hideActionError()
  queryInputRef.value?.focus()
}

function onStrategyChange(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  strategyId.value = value
  hideActionError()
}

function selectStrategy(value: string) {
  const hit = strategyList.value.find(item =>
    String(item.id) === value || item.bizNo === value || item.name === value)
  if (hit) {
    strategyId.value = String(hit.id)
  }
}

function triggerFileSelect() {
  if (uploadState.value !== 'default') return
  fileInput.value?.click()
}

function onSearchboxDrop(event: DragEvent) {
  searchboxDragging.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) void handleFile(file)
}

function onDropzoneDrop(event: DragEvent) {
  dropDragging.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) void handleFile(file)
}

function onFileInputChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) void handleFile(file)
}

async function handleFile(file: File) {
  if (recognizing.value) {
    showToast('文件识别中，请稍候', 'err')
    return
  }
  const extension = file.name.split('.').pop()?.toLowerCase() || ''
  const image = ['jpg', 'jpeg', 'png'].includes(extension)
  const video = extension === 'mp4'
  const audio = ['wav', 'mp3'].includes(extension)
  if (!image && !video && !audio) {
    failUpload('不支持的文件类型，请上传 JPG/PNG/MP4/WAV/MP3')
    return
  }
  if (file.size > 100 * 1024 * 1024) {
    failUpload('文件超过 100MB 上限')
    return
  }

  recognizing.value = true
  uploadState.value = 'success'
  queryFileName.value = file.name
  uploadMessage.value = '正在识别模态…'
  uploadError.value = ''
  try {
    const uploaded = await uploadFile(file)
    // 模态以 407 查询理解结果为准；407 不可用时回退扩展名判定
    let mode: Mode = image ? '图像' : video ? '视频' : '音频'
    let path = uploaded.path
    try {
      const understood = await understand({ queryFile: uploaded.path })
      const detected = modeOfModality(understood.queryModality)
      if (detected) {
        mode = detected
      }
      if (understood.queryFile) {
        path = understood.queryFile
      }
    } catch {
      // 407 不可用时保留扩展名判定，不阻断上传
    }
    queryFile.value = path
    queryMode.value = mode
    uploadState.value = 'success'
    uploadMessage.value = `已识别为${mode}模态，请选择目标检索类型`
    applyFileFilter()
    if (mode === '音频') {
      selectPair('音搜音')
    }
  } catch (error) {
    failUpload((error as Error).message || '文件上传失败')
  } finally {
    recognizing.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

/** 后端 queryModality（text/image/audio/video）→ 检索台模态 */
function modeOfModality(modality: string | null | undefined): Mode | null {
  return ({ text: '文本', image: '图像', audio: '音频', video: '视频' } as Record<string, Mode>)[modality || ''] ?? null
}

/** 上传失败：清空文件与预览，避免用旧文件检索 */
function failUpload(message: string) {
  recognizing.value = false
  queryFile.value = ''
  queryFileName.value = ''
  queryMode.value = null
  uploadState.value = 'default'
  uploadMessage.value = ''
  uploadError.value = message
  if (fileInput.value) fileInput.value.value = ''
  applyFileFilter()
}

function clearFile() {
  queryFile.value = ''
  queryFileName.value = ''
  queryMode.value = null
  uploadState.value = 'default'
  uploadMessage.value = ''
  uploadError.value = ''
  if (fileInput.value) fileInput.value.value = ''
  applyFileFilter()
}

/** 清空搜索词与文件，保留策略选择 */
function resetAll() {
  query.value = ''
  clearFile()
  selectedPair.value = ''
  target.value = '全部'
  actionError.value = ''
  suggestVisible.value = false
  recentSuggestions.value = []
  exampleSuggestions.value = []
  closeClarify()
  showToast('已清空检索条件')
}

async function submitSearch() {
  if (recognizing.value) {
    showToast('文件识别中，请稍候', 'err')
    return
  }
  const text = query.value.trim()
  if (!text && !queryFile.value) {
    showActionError('请输入查询描述，或上传图片 / 视频 / 音频文件')
    return
  }
  if (target.value !== '全部' && !selectedPair.value) {
    showActionError('请先选择检索类型（全部 / 图像 / 文本 / 音频 / 视频）')
    nudgeModeGroup()
    return
  }
  hideActionError()

  if (text && text.length < CLARIFY_MIN_CHARS && !isClarified(text)) {
    void openClarify(text)
    return
  }

  await runSearch()
}

async function runSearch(clarifiedQuery?: string, relaxed = false) {
  if (searching.value) return
  const text = (clarifiedQuery ?? query.value).trim()
  const clarified = Boolean(text) && (Boolean(clarifiedQuery) || (text.length < CLARIFY_MIN_CHARS && isClarified(text)))

  const activeTarget = target.value
  searching.value = true
  try {
    const body: Record<string, unknown> = {}
    if (queryFile.value) body.queryFile = queryFile.value
    if (text) body.query = text
    if (strategyId.value) body.strategyId = strategyId.value
    if (activeTarget !== '全部' && selectedPair.value) {
      body.targetModalities = [modeValues[activeTarget]]
    }
    if (clarified) {
      body.clarified = true
      body.clarifiedQuery = text
    }
    if (relaxed) {
      // P2 空结果「放宽过滤」：在策略阈值基础上下调 0.20，最低 0.30
      const base = Number(selectedStrategy.value?.threshold ?? 0.6)
      body.threshold = Math.max(0.3, Math.round((base - 0.2) * 100) / 100)
    }
    const result = await executeSearch(body)

    const params = new URLSearchParams()
    params.set('from', 'P1')
    params.set('back', 'P1')
    if (result.taskBizNo) params.set('task', result.taskBizNo)
    if (text) params.set('q', text)
    params.set('pair', activeTarget === '全部' ? '全部' : selectedPair.value)
    if (selectedStrategy.value) {
      params.set('stg', selectedStrategy.value.bizNo || String(selectedStrategy.value.id))
      params.set('thr', Number(selectedStrategy.value.threshold).toFixed(2))
    }
    await router.push(`/search/results/${result.resultSetId}?${params.toString()}`)
  } catch (error) {
    showToast((error as Error).message, 'err')
  } finally {
    searching.value = false
  }
}

/* ---------- 意图澄清 ---------- */

function readClarified(): string[] {
  try {
    const raw = sessionStorage.getItem(CLARIFY_KEY)
    if (!raw) return []
    const list = JSON.parse(raw)
    return Array.isArray(list) ? list.map(item => String(item)) : []
  } catch {
    return []
  }
}

function isClarified(value: string) {
  return readClarified().includes(value)
}

/** 澄清记录写会话存储，保留最近 60 条 */
function markClarified(value: string) {
  if (!value) return
  try {
    const list = readClarified().filter(item => item !== value)
    list.push(value)
    sessionStorage.setItem(CLARIFY_KEY, JSON.stringify(list.slice(-60)))
  } catch {
    // 存储不可用时忽略
  }
}

async function openClarify(value: string) {
  clarify.raw = value
  clarify.hint = ''
  clarify.options = []
  clarify.open = true
  suggestVisible.value = false
  hideActionError()
  try {
    const result = await understand({ query: value })
    clarify.hint = result.hint || ''
    clarify.options = normalizeClarifyOptions(result.clarifyOptions)
  } catch {
    clarify.options = []
  }
  // 后端未返回候选时按输入派生三条模板候选，避免「你是不是想找：」后面空着
  if (!clarify.options.length) {
    clarify.options = buildClarifyOptions(value)
  }
}

/** 短 query 的本地兜底候选：围绕图片、视频、场景描述补全，不写死业务文案 */
function buildClarifyOptions(value: string): string[] {
  const base = value.trim()
  if (!base) {
    return []
  }
  return [`${base}的高清图片`, `${base}的相关视频`, `${base}的场景描述`]
}

/** 407 候选可能是 {intent, query, hint} 序列化后的 JSON 串，取改写 query 作为候选文本 */
function normalizeClarifyOptions(options: string[]): string[] {
  return (options ?? [])
    .map(option => {
      try {
        const parsed = JSON.parse(option) as { query?: unknown; intent?: unknown } | null
        if (parsed && typeof parsed === 'object') {
          return String(parsed.query || parsed.intent || option)
        }
        return option
      } catch {
        return option
      }
    })
    .filter((item): item is string => Boolean(item))
    .slice(0, 3)
}

function pickClarify(value: string) {
  markClarified(clarify.raw)
  query.value = value
  clarify.open = false
  void runSearch(value)
}

function keepOriginalQuery() {
  const original = clarify.raw || query.value.trim()
  markClarified(original)
  clarify.open = false
  void runSearch(original)
}

function closeClarify() {
  clarify.open = false
}

/* ---------- 提示 ---------- */

function showActionError(message: string) {
  actionError.value = message
  clearTimeout(actionTimer)
  actionTimer = setTimeout(hideActionError, 3500)
}

function hideActionError() {
  actionError.value = ''
}

function nudgeModeGroup() {
  modeNudge.value = false
  requestAnimationFrame(() => {
    modeNudge.value = true
    clearTimeout(modeTimer)
    modeTimer = setTimeout(() => { modeNudge.value = false }, 600)
  })
}

function showToast(message: string, type: 'info' | 'err' = 'info') {
  toast.message = message
  toast.type = type
  toast.visible = true
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toast.visible = false }, 2600)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    suggestVisible.value = false
    clarify.open = false
  }
}

</script>

<style scoped>
/* ================= 一屏布局：左模态矩阵 / 右侧竖版上传 ================= */
.page-card.console-page {
  height: 100vh;
  min-height: 0;
  max-width: 1200px;
  padding: 20px 26px;
  gap: 18px;
  overflow: hidden;
}

.hero {
  position: relative;
  flex: 0 0 auto;
  overflow: hidden;
  padding: 26px 28px;
  border: 1px solid #e7effb;
  border-radius: 22px;
  background:
    radial-gradient(760px 380px at 50% -18%, rgba(65, 105, 246, 0.1), rgba(65, 105, 246, 0) 68%),
    linear-gradient(180deg, #fcfdff 0%, #f4f9ff 100%);
  text-align: center;
}
.hero::before {
  content: '';
  position: absolute;
  left: -170px;
  top: -230px;
  width: 440px;
  height: 440px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(65, 105, 246, 0.14), rgba(65, 105, 246, 0) 70%);
  pointer-events: none;
}
.hero::after {
  content: '';
  position: absolute;
  right: -190px;
  bottom: -240px;
  width: 480px;
  height: 380px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(110, 140, 255, 0.15), rgba(110, 140, 255, 0) 70%);
  pointer-events: none;
}
.hero-inner {
  position: relative;
  z-index: 1;
  max-width: 1000px;
  margin: 0 auto;
}
.hero h1 {
  margin: 0;
  font-size: 32px;
  line-height: 1.1;
  font-weight: 700;
  letter-spacing: 0.01em;
  color: #10203f;
}
.hero h1 span { color: var(--system-blue); }
.hero-sub {
  margin: 8px auto 0;
  max-width: 660px;
  font-size: 14.5px;
  line-height: 1.55;
  color: #5b6b85;
}
.hero .dev-entry {
  position: absolute;
  top: 20px;
  right: 22px;
  z-index: 3;
}

/* ---- 大搜索框 ---- */
.searchbox {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 60px;
  margin: 20px auto 0;
  padding: 0 0 0 18px;
  border: 1px solid #e3ecfa;
  border-radius: 17px;
  background: #fff;
  box-shadow: 0 18px 44px rgba(21, 66, 150, 0.12);
  transition: border-color 0.18s, box-shadow 0.18s;
}
.searchbox:focus-within {
  border-color: #b9cef4;
  box-shadow: 0 22px 52px rgba(21, 66, 150, 0.16), 0 0 0 4px rgba(65, 105, 246, 0.07);
}
.searchbox.is-error {
  border-color: #efb1b4;
  box-shadow: 0 18px 44px rgba(170, 40, 40, 0.12), 0 0 0 4px rgba(217, 45, 32, 0.05);
}
.searchbox.drag {
  border-color: #9dbcf0;
  background: #fbfdff;
  box-shadow: 0 0 0 4px rgba(65, 105, 246, 0.08), 0 18px 44px rgba(21, 66, 150, 0.12);
}
.search-ico {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  color: #7d90ae;
}
.search-field {
  position: static;
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  align-items: center;
}
.search-field input {
  width: 100%;
  height: 100%;
  border: 0;
  outline: none;
  background: transparent;
  font-size: 15.5px;
  color: #1b2b45;
  letter-spacing: 0.005em;
}
.search-field input::placeholder { color: #9dabc1; }
.btn-search {
  flex: 0 0 auto;
  width: 118px;
  height: 60px;
  padding: 0;
  border: 0;
  border-radius: 15px;
  background: linear-gradient(135deg, #5a7cff 0%, #4169f6 62%, #335be8 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.04em;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-shadow: 0 12px 26px rgba(65, 105, 246, 0.3);
  transition: transform 0.15s, box-shadow 0.15s;
}
.btn-search:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 30px rgba(65, 105, 246, 0.36);
}
.btn-search:disabled {
  opacity: 0.78;
  cursor: wait;
  transform: none;
  box-shadow: 0 8px 18px rgba(65, 105, 246, 0.22);
}
.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
.spinner.dark {
  border-color: rgba(65, 105, 246, 0.35);
  border-top-color: var(--system-blue);
}
@keyframes spin { to { transform: rotate(360deg); } }

.inline-err {
  display: none;
  font-size: 12.5px;
  line-height: 1.5;
  color: #d92d20;
}
.inline-err.show { display: block; }
.action-err {
  max-width: 1000px;
  margin: 5px auto 0;
  padding-left: 8px;
  text-align: left;
}

/* ---- 输入联想 ---- */
.suggest-pop {
  position: absolute;
  left: 12px;
  right: 12px;
  top: calc(100% + 10px);
  z-index: 60;
  display: none;
  padding: 8px;
  border: 1px solid #e2eaf7;
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--shadow-pop);
  text-align: left;
}
.suggest-pop.show { display: block; }
.suggest-group {
  padding: 6px 9px 4px;
  font-size: 11px;
  font-weight: 600;
  color: #9aa8bf;
  letter-spacing: 0.04em;
}
.suggest-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  font-size: 13px;
  color: var(--ink-body);
  text-align: left;
  transition: background 0.12s, color 0.12s;
}
.suggest-item:hover { background: var(--system-blue-weak); color: var(--system-blue); }
.suggest-item .s-hist {
  margin-left: auto;
  font-size: 11px;
  color: #a8b3c5;
}

/* ---- 检索类型 / 检索策略 ---- */
.search-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  min-height: 50px;
  margin: 14px auto 0;
  max-width: 1000px;
  text-align: left;
}
.mode-group { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.mg-label {
  margin-right: 2px;
  font-size: 12px;
  font-weight: 600;
  color: #8b9ab2;
  letter-spacing: 0.08em;
}
.mode-tab {
  height: 42px;
  padding: 0 16px;
  border: 1px solid #e2eaf7;
  border-radius: 11px;
  background: #fff;
  color: #43536b;
  font-size: 13.5px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  transition: border-color 0.15s, background 0.15s, color 0.15s, box-shadow 0.15s;
}
.mode-tab:hover { border-color: #c6d8f5; background: #f8fbff; color: var(--system-blue); }
.mode-tab.active {
  border-color: #bcd2f6;
  background: var(--system-blue-weak);
  color: var(--system-blue);
  font-weight: 600;
  box-shadow: inset 0 0 0 1px rgba(65, 105, 246, 0.08);
}
.mode-group.nudge { animation: mgNudge 0.45s ease; }
@keyframes mgNudge {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-4px); }
  60% { transform: translateX(4px); }
  80% { transform: translateX(-2px); }
}
.stg-pick {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 4px 4px 12px;
  border: 1px solid #e2eaf7;
  border-radius: 11px;
  background: #fff;
}
.stg-pick .sp-label {
  font-size: 12px;
  font-weight: 600;
  color: #8b9ab2;
  letter-spacing: 0.04em;
  white-space: nowrap;
}
.stg-select {
  height: 38px;
  min-width: 190px;
  max-width: 300px;
  padding: 0 6px;
  border: 0;
  outline: none;
  background: transparent;
  font-size: 13px;
  color: #26374f;
}
.stg-select:disabled { color: var(--muted); cursor: default; }
.stg-diff {
  padding: 0 8px 0 10px;
  margin-left: 2px;
  border-left: 1px solid #eef2f9;
  font-size: 11.5px;
  color: #8b9ab2;
  white-space: nowrap;
}
.stg-fb {
  padding: 0 8px 0 10px;
  margin-left: 2px;
  border-left: 1px solid #eef2f9;
  font-size: 11.5px;
  color: #c98a12;
  white-space: nowrap;
}

/* ---- 辅助信息 ---- */
.search-aux {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 8px auto 0;
  max-width: 1000px;
  font-size: 12px;
  color: #93a1b7;
}
.search-aux .pa b { color: var(--system-blue); font-weight: 600; }
.search-aux .ca b { color: var(--muted); font-weight: 600; font-variant-numeric: tabular-nums; }
.search-aux .aux-right { display: inline-flex; align-items: center; gap: 12px; }
.link-btn {
  padding: 2px 4px;
  border: 0;
  background: transparent;
  font-size: 12px;
  color: #93a1b7;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.link-btn:hover { color: var(--system-blue); }

/* ---- 矩阵 + 上传 ---- */
.setup { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.mt-and-upload {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 16px;
  align-items: stretch;
}
.matrix-panel {
  height: 100%;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 24px;
}
.matrix-card { width: 100%; max-width: 760px; }
.setup-head {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.ph-ico { width: 22px; height: 22px; display: grid; place-items: center; color: #8fa2c0; }
.setup-head h3 { font-size: 14px; font-weight: 700; color: #1b2a44; letter-spacing: 0.01em; }
.setup-hint { margin-left: 2px; font-size: 11px; color: #a2aec2; }

.matrix-legend {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--muted);
}
.matrix-legend .lg { display: flex; align-items: center; gap: 5px; }
.matrix-legend .mk {
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  border: 1px solid #d7e3f8;
  border-radius: 6px;
  background: #f3f7ff;
  color: var(--system-blue);
}

.modal-matrix {
  display: grid;
  grid-template-columns: 56px repeat(4, minmax(0, 1fr));
  grid-template-rows: 32px repeat(4, 44px);
  gap: 6px;
  width: 100%;
  max-width: 720px;
}
.mm-corner, .mm-colh, .mm-rowh {
  display: grid;
  place-items: center;
  font-size: 12.5px;
  font-weight: 600;
  color: #8b9ab2;
}
.mm-colh { height: 32px; letter-spacing: 0.04em; }
.mm-rowh { justify-content: flex-end; padding-right: 6px; font-weight: 500; color: var(--muted); }
.mm-cell {
  height: 44px;
  min-height: 0;
  display: grid;
  place-items: center;
  border-radius: 6px;
  user-select: none;
}
.mm-cell.available {
  border: 1px solid #d7e3f8;
  background: #f3f7ff;
  color: var(--system-blue);
  cursor: pointer;
  transition: background 0.12s, border-color 0.12s, transform 0.12s, box-shadow 0.12s;
}
.mm-cell.available:hover { background: #e8f0ff; border-color: #a9c4ee; transform: translateY(-1px); }
.mm-cell.selected {
  border-color: var(--system-blue);
  background: var(--system-blue);
  color: #fff;
  box-shadow: 0 6px 14px rgba(65, 105, 246, 0.28);
}
.mm-cell.dim { opacity: 0.32; pointer-events: none; }

.upload-panel { height: 100%; min-width: 0; display: flex; flex-direction: column; gap: 6px; }
.dropzone {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px 20px;
  border: 1.5px dashed #cfdcf0;
  border-radius: 16px;
  background: #fbfdff;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.dropzone:hover, .dropzone.drag { border-color: #9dbcf0; background: #f4f9ff; }
.dropzone.success { border-style: solid; border-color: #bfe3c9; background: #f7fdf9; cursor: default; }
.dz-default { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.dz-ico {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: #eef4ff;
  color: var(--system-blue);
}
.dropzone strong { font-size: 14px; font-weight: 600; color: #273244; }
.dropzone span { font-size: 12.5px; line-height: 1.55; color: #8b9ab2; }
.dz-types { display: flex; gap: 6px; flex-wrap: wrap; justify-content: center; margin-top: 4px; }
.dz-types em {
  padding: 1px 5px;
  border: 1px solid #e2ebf7;
  border-radius: 4px;
  font-style: normal;
  font-size: 10px;
  color: #8fa0bd;
}
.dz-file { display: flex; align-items: center; gap: 10px; width: 100%; text-align: left; }
.dz-file .f-thumb {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: #eef4ff;
  color: var(--system-blue);
}
.dz-file .f-meta { min-width: 0; flex: 1; }
.dz-file .f-name {
  font-size: 12.5px;
  font-weight: 600;
  color: #1f2a44;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dz-file .f-rec { margin-top: 2px; font-size: 11px; color: #1e8e4a; }
.dz-clear {
  display: grid;
  place-items: center;
  padding: 5px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #b3bccc;
  line-height: 1;
}
.dz-clear:hover { background: #fdf2f2; color: #d92d20; }
.upload-err { padding: 0 4px; }

/* ---- 权限拦截 ---- */
.perm-page { justify-content: center; }
.perm-gate { row-gap: 6px; padding: 48px 24px; }
.pg-ico {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  margin-bottom: 2px;
  border-radius: 12px;
  background: var(--system-blue-weak);
  color: var(--system-blue);
}

/* ---- 意图澄清浮层 ---- */
.clarify-mask {
  position: fixed;
  inset: 0;
  z-index: 900;
  background: rgba(15, 23, 42, 0.45);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s;
}
.clarify-mask.open { opacity: 1; pointer-events: auto; }
.clarify-modal {
  position: fixed;
  left: 50%;
  top: 50%;
  z-index: 901;
  width: 520px;
  max-width: calc(100vw - 32px);
  border: 1px solid #e2eaf7;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 26px 64px rgba(20, 40, 90, 0.22);
  transform: translate(-50%, -50%) scale(0.98);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.18s, transform 0.18s;
}
.clarify-modal.open { opacity: 1; pointer-events: auto; transform: translate(-50%, -50%) scale(1); }
.clarify-head { display: flex; align-items: center; gap: 10px; padding: 16px 20px 8px; }
.clarify-head h3 { margin: 0; font-size: 15px; font-weight: 700; color: #1b2a44; letter-spacing: 0.01em; }
.clarify-close {
  margin-left: auto;
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 7px;
  background: #f2f4f8;
  color: var(--muted);
  font-size: 15px;
  line-height: 1;
}
.clarify-close:hover { background: #e6eaf2; color: #1f2937; }
.clarify-body { padding: 0 20px 18px; }
.clarify-lead { margin: 0 0 12px; font-size: 13px; line-height: 1.65; color: var(--ink-body); }
.clarify-list { border: 1px solid #e6edf8; border-radius: 12px; overflow: hidden; background: #fff; }
.clarify-opt {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 12px 14px;
  border: 0;
  border-bottom: 1px solid #eef2f9;
  background: #fff;
  font-size: 13.5px;
  color: #26374f;
  text-align: left;
  transition: background 0.12s, color 0.12s;
}
.clarify-opt:last-child { border-bottom: 0; }
.clarify-opt:hover { background: var(--system-blue-weak); color: var(--system-blue); }
.clarify-opt .co-arrow { margin-left: auto; color: #c2cbdb; font-size: 14px; }
.clarify-opt:hover .co-arrow { color: var(--system-blue); }
.clarify-empty {
  padding: 12px 14px;
  border: 1px dashed #e2eaf7;
  border-radius: 12px;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--muted);
}
.clarify-foot { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 14px; }
.clarify-orig {
  padding: 2px 0;
  border: 0;
  background: transparent;
  font-size: 12.5px;
  color: var(--system-blue);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.clarify-orig:hover { color: var(--system-blue-hover); }
.clarify-hint { font-size: 11.5px; color: #a2aec2; white-space: nowrap; }

/* ---- 全局消息 ---- */
.toast {
  position: fixed;
  top: 64px;
  left: 50%;
  z-index: 1200;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 10px 18px;
  border-radius: 10px;
  background: #16233d;
  color: #fff;
  font-size: 13px;
  box-shadow: 0 12px 30px rgba(20, 32, 70, 0.28);
  opacity: 0;
  pointer-events: none;
  transform: translateX(-50%) translateY(-12px);
  transition: all 0.2s;
}
.toast.show { opacity: 1; transform: translateX(-50%) translateY(0); }
.toast.err { background: #b42318; }

/* ---- 研发说明抽屉内容 ---- */
.dev-card {
  margin-bottom: 14px;
  border: 1px solid #e4eaf3;
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
}
.dc-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 11px 16px;
  border-bottom: 1px solid #eef2f8;
  background: #fbfcfe;
}
.dc-id {
  flex: 0 0 auto;
  padding: 1px 7px;
  border-radius: 4px;
  background: #b54708;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}
.dc-name { font-size: 13.5px; font-weight: 700; color: #1f2937; }
.dc-body { padding: 6px 16px 12px; }
.dc-row {
  display: flex;
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px dashed #f0f3f8;
  font-size: 12.5px;
  line-height: 1.7;
}
.dc-row:last-child { border-bottom: none; }
.dc-k { flex: 0 0 78px; font-weight: 700; color: var(--ink-body); }
.dc-v { flex: 1; color: #475467; }
.dc-v ul { margin: 0; padding-left: 17px; }
.dc-v li { margin: 2px 0; }
.k-in .dc-k { color: #027a48; }
.k-out .dc-k { color: #1f5fd4; }
.k-err .dc-k { color: #c0362c; }
.k-dep .dc-k { color: #7a5af8; }
.ov-grid { display: grid; grid-template-columns: 1fr; gap: 12px; }
.dev-ov { padding: 8px 4px; }
.dev-ov h4 { margin: 0 0 6px; font-size: 13px; color: var(--system-blue); }
.dev-ov p { margin: 0; font-size: 12.5px; line-height: 1.75; color: #475467; }
.dev-xref {
  padding: 0 1px;
  color: #1f5fd4;
  font-weight: 700;
  border-bottom: 1px dashed #9dbcf5;
  white-space: nowrap;
}

/* ---- 响应式 ---- */
@media (max-width: 1000px), (max-height: 680px) {
  .page-card.console-page {
    height: auto;
    min-height: 100vh;
    padding-bottom: 28px;
    overflow: visible;
  }
  .setup { flex: none; }
  .mt-and-upload { grid-template-columns: 1fr; }
  .dropzone { min-height: 140px; }
  .matrix-panel { height: auto; }
}
@media (max-width: 820px) {
  .page-card.console-page { padding: 18px 16px 48px; gap: 18px; }
  .hero { padding: 26px 20px; border-radius: 20px; }
  .hero h1 { font-size: 28px; }
  .hero .dev-entry { top: 14px; right: 14px; }
  .searchbox { height: 54px; margin-top: 18px; padding-left: 14px; }
  .search-field input { font-size: 14.5px; }
  .btn-search { width: 92px; height: 54px; font-size: 14px; border-radius: 13px; }
  .search-meta { flex-direction: column; align-items: stretch; gap: 10px; }
  .stg-pick { width: 100%; flex-wrap: wrap; row-gap: 2px; }
  .stg-select { flex: 1; min-width: 0; max-width: none; }
  .search-aux { flex-direction: column; align-items: flex-start; gap: 6px; }
  .search-aux .aux-right { width: 100%; justify-content: space-between; }
  .modal-matrix { grid-template-columns: 48px repeat(4, minmax(0, 1fr)); gap: 5px; }
  .mm-cell { height: 38px; }
  .clarify-modal { border-radius: 16px; }
  .clarify-head { padding: 14px 16px 6px; }
  .clarify-body { padding: 0 16px 16px; }
  .clarify-foot { flex-wrap: wrap; row-gap: 6px; }
}
</style>
