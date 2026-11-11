<template>
  <div class="p1-root">
    <div class="page">
      <!-- Hero：检索大入口 -->
      <section class="hero" aria-labelledby="heroTitle">
        <button class="dev-entry" type="button" title="研发说明" @click="openDev">
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
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"><circle cx="10.5" cy="10.5" r="6.2"/><path d="M15.2 15.2L20 20"/></svg>
            </span>
            <div class="search-field">
              <input
                id="queryInput"
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
                <template v-if="historySuggestions.length">
                  <div class="suggest-group">历史检索</div>
                  <button
                    v-for="item in historySuggestions"
                    :key="`h-${item}`"
                    class="suggest-item"
                    type="button"
                    @mousedown.prevent="refill(item)"
                  >{{ item }}<span class="s-hist">历史</span></button>
                </template>
                <template v-if="hotSuggestions.length">
                  <div class="suggest-group">运营热词</div>
                  <button
                    v-for="item in hotSuggestions"
                    :key="`w-${item}`"
                    class="suggest-item"
                    type="button"
                    @mousedown.prevent="refill(item)"
                  >{{ item }}<span class="s-hot">热</span></button>
                </template>
              </div>
            </div>
            <button class="btn-search" type="button" :disabled="searching" @click="submitSearch">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.2"/><path d="M15.2 15.2L20 20"/></svg>
              <span v-if="searching"><span class="spinner"></span> 检索中</span>
              <span v-else>检索</span>
            </button>
          </div>

          <div class="inline-err" :class="{ show: actionError }" role="alert">{{ actionError }}</div>

          <div class="search-meta">
            <div class="mode-group" :class="{ nudge: modeNudge }" role="group" aria-label="检索类型快捷选择">
              <span class="mg-label">检索类型</span>
              <button
                v-for="target in targets"
                :key="target"
                class="mode-tab"
                type="button"
                :class="{ active: selectedTarget === target }"
                :aria-pressed="selectedTarget === target"
                @click="selectTarget(target)"
              >
                <span aria-hidden="true"><component :is="targetIcons[target]" /></span>{{ target }}
              </button>
            </div>

            <div class="scene-pick">
              <label class="sp-label" for="sceneSelect">业务场景</label>
              <select id="sceneSelect" v-model="scene" class="scene-select">
                <option v-for="item in sceneOptions" :key="item.alias" :value="item.alias">{{ item.label }}</option>
              </select>
              <label class="sp-label" for="strategySelect">策略</label>
              <select id="strategySelect" v-model="strategyId" class="scene-select strategy-select">
                <option value="">按场景生效策略</option>
                <option v-for="item in strategyList" :key="item.id" :value="item.id">
                  {{ item.name }}{{ item.preset ? '（系统默认）' : '' }} · {{ item.paramSummary }}
                </option>
              </select>
              <router-link class="scene-cfg" to="/strategies" title="场景与策略在策略管理中维护">
                配置<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M7 17L17 7"/><path d="M9 7h8v8"/></svg>
              </router-link>
            </div>
          </div>

          <div class="search-aux">
            <span class="pa">当前模态对 <b>{{ selectedPair || '未选择' }}</b></span>
            <span class="aux-right">
              <span class="ca"><b>{{ query.length }}</b>/512</span>
              <button class="link-btn" type="button" @click="resetAll">清空</button>
            </span>
          </div>
        </div>
      </section>

      <!-- 模态矩阵 + 文件上传 -->
      <section class="setup" aria-label="模态矩阵">
        <div class="panel">
          <div class="setup-head">
            <span class="ph-ico" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="4" width="6" height="6" rx="1.4"/><rect x="14" y="4" width="6" height="6" rx="1.4"/><rect x="4" y="14" width="6" height="6" rx="1.4"/><rect x="14" y="14" width="6" height="6" rx="1.4"/></svg></span>
            <h3>模态矩阵</h3>
            <span class="setup-hint">查询模态 × 目标模态</span>
          </div>

          <div class="matrix-wrap">
            <div class="matrix-legend">
              <span class="lg"><span class="mk on" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.4 4.5L19 7.4"/></svg></span>支持</span>
            </div>
            <div class="modal-matrix">
              <div class="mm-corner"></div>
              <div v-for="target in targets" :key="`h-${target}`" class="mm-colh">{{ target }}</div>
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
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12.5l4.4 4.5L19 7.4"/></svg>
                </div>
              </template>
            </div>
          </div>

          <div class="upload-strip">
            <div
              class="dropzone compact"
              :class="{ drag: dropDragging, success: uploadState === 'success', fail: uploadState === 'fail' }"
              @click="triggerFileSelect"
              @dragover.prevent="dropDragging = true"
              @dragleave="dropDragging = false"
              @drop.prevent="onDropzoneDrop"
            >
              <div v-if="uploadState === 'default'" class="dz-default">
                <div class="dz-ico" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 16V5.5"/><path d="M7.5 10L12 5.5 16.5 10"/><path d="M4.5 15.5v2.2A2.3 2.3 0 0 0 6.8 20h10.4a2.3 2.3 0 0 0 2.3-2.3v-2.2"/></svg></div>
                <strong>拖拽或点击上传图片 / 音频 / 视频</strong>
                <span>自动识别查询模态 · JPG/PNG/MP4/WAV/MP3 · 单文件 ≤ 100MB</span>
                <div class="dz-types"><em>JPG/PNG</em><em>MP4</em><em>WAV/MP3</em></div>
              </div>

              <div v-else class="dz-file">
                <div class="f-thumb" :class="{ err: uploadState === 'fail' }">
                  <span v-if="uploadState === 'loading'" class="spinner"></span>
                  <span v-else-if="uploadState === 'fail'">!</span>
                  <component v-else :is="fileIcons[queryMode || '文本']" />
                </div>
                <div class="f-meta">
                  <div class="f-name">{{ queryFileName }}</div>
                  <div class="f-rec" :class="{ err: uploadState === 'fail' }">{{ uploadMessage }}</div>
                </div>
                <button class="dz-clear" type="button" title="移除文件" aria-label="移除文件" @click.stop="clearFile">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"><path d="M6.5 6.5l11 11M17.5 6.5l-11 11"/></svg>
                </button>
              </div>

              <input ref="fileInput" type="file" accept=".jpg,.jpeg,.png,.mp4,.wav,.mp3" hidden @change="onFileInputChange">
            </div>
            <div class="inline-err" :class="{ show: uploadError }" role="alert">{{ uploadError }}</div>
          </div>
        </div>
      </section>
    </div>

    <!-- 意图澄清浮层 -->
    <div class="modal-mask" :class="{ show: clarifyVisible }" @click.self="closeClarify">
      <div class="modal-box" role="dialog" aria-modal="true" aria-label="意图澄清">
        <div class="modal-head">
          <h3><span class="mh-ico" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4l1.6 5.2L19 11l-5.4 1.8L12 18l-1.6-5.2L5 11l5.4-1.8z"/><path d="M18.5 16l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z"/></svg></span>意图澄清</h3>
          <button class="modal-close" type="button" aria-label="关闭" @click="closeClarify"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"><path d="M6.5 6.5l11 11M17.5 6.5l-11 11"/></svg></button>
        </div>
        <div class="modal-body">
          <div class="mb-tip">「<b>{{ clarifyRaw }}</b>」较短或存在歧义，你是不是想找：</div>
          <div v-if="!clarifyOptions.length" class="mb-tip">{{ clarifyHint || '请补充更具体的描述（对象、场景、动作）' }}</div>
          <button v-for="item in clarifyOptions" :key="item" class="clarify-item" type="button" @click="pickClarify(item)"><span class="ci-q">{{ item }}</span></button>
          <div class="mb-tip" style="margin-top:12px">场景建议：</div>
          <div class="clarify-scenes">
            <button class="scene-tag" type="button" @click="addSceneAndGo('训练数据筛选')">训练数据筛选</button>
            <button class="scene-tag" type="button" @click="addSceneAndGo('作业')">作业</button>
          </div>
        </div>
        <div class="modal-foot">
          <a href="javascript:void(0)" @click="searchDirect">仍按原词检索</a>
          <span class="modal-once">每个 query 最多澄清 1 轮</span>
        </div>
      </div>
    </div>

    <div class="toast" :class="{ show: toastVisible, err: toastType === 'err' }">{{ toastMessage }}</div>

    <!-- 研发说明抽屉 -->
    <div class="dev-mask" :class="{ open: devOpen }" @click="closeDev"></div>
<div class="dev-drawer" :class="{ open: devOpen }" role="dialog" aria-modal="true" aria-label="研发需求说明">
  <div class="dev-head">
    <h3>研发需求说明</h3>
    <span class="dh-tag">给研发</span>
    <button type="button" class="dev-close" id="devClose" title="关闭（Esc）" aria-label="关闭" @click="closeDev">×</button>
  </div>
  <div class="dev-body">
      <div class="dev-card" id="dc-ov" style="scroll-margin-top:14px">
        <div class="dc-head"><span class="dc-id" style="background:#1f5fd4">总览</span><span class="dc-name">页面定位 / 闭环 / 模块清单 / 设计出处</span></div>
        <div class="dc-body">
          <div class="ov-grid">
            <div class="dev-ov" style="margin:0;border:none;padding:6px 4px"><h4>本页定位【设计】</h4><p>全系统唯一查询入口：输入文本描述或图/视频文件，选择「查询模态 × 目标模态」发起检索；结果进入 <a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a> 确认与导出。</p></div>
            <div class="dev-ov" style="margin:0;border:none;padding:6px 4px"><h4>平台全景闭环【研发】</h4><p>检索台发起检索 → <a class="dev-xref" href="P6_任务管理_v5.0_20261025.html">P6</a> 任务管理调度运行 → <a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a> 结果页确认/导出 → <a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a> 结果运营（导出验收/精选/热词反馈）→ 反馈归因到 <a class="dev-xref" href="P5_策略管理_v5.0_20261025.html">P5</a> 策略调整（<a class="dev-xref" href="P8_评测_v5.0_20261025.html">P8</a> 评测验证）→ 策略发布后下次检索生效；<a class="dev-xref" href="P4_库管理_v5.2_20261026.html">P4</a> 库管理持续供给检索库。九页即此闭环的九个切面。</p></div>
            <div class="dev-ov" style="margin:0;border:none;padding:6px 4px"><h4>三级模块清单【设计】</h4><p>本页主挂 2 个三级模块：检索查询意图澄清（A＝F5）、检索相似查询复用（B＝F6）；全局件：多模态输入 G1（F1/F2）、模态矩阵 G2（F3）、场景标签（F4）。完整历史在 <a class="dev-xref" href="P9_检索历史_v5.0_20261025.html">P9</a>、热词运营在 <a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a>。</p></div>
            <div class="dev-ov" style="margin:0;border:none;padding:6px 4px"><h4>设计出处与演示性质【设计】</h4><p>依据 L2 设计_13 页面架构地图 / L3 设计_15 模板 / L4 设计_16 基础件生成；模态能力：4×4 全矩阵 16 种模态对全部可检索（4 模态统一共享空间对齐，2026-10-27 确认）。本页检索为页内模拟，不请求真实引擎；示例数据不构成交付承诺。</p></div>
          </div>
        </div>
      </div>
<div class="dev-card" id="dc-f1"><div class="dc-head"><span class="dc-id">F1</span><span class="dc-name">文本检索输入</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">输入查询文本，作为文本检索（文搜X）的查询语义；与文件同时存在时以文件为主、文本作语义补充。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v">常驻可用；文本与文件二选一即可，不强制二者都有。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>用户手输文本</li><li>单行大输入框（Hero 搜索框），最多 512 字</li><li>占位文案给出输入提示（一句话描述或拖入文件），示例词由输入联想 / 意图澄清提供。</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v">实时字数统计（0/512，硬截断不允许超长）；输入去首尾空格后参与判定；输入≥4 字触发 F6 联想下拉。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>作为 Query 参数进入 F7 检索</li><li>不足 4 字的短词走 F5 意图澄清。</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>仅空格等同未输入</li><li>达到 512 字后无法继续输入</li><li>无文本也无文件时点检索，红字提示「请输入查询描述，或上传图片/视频文件」，3.5 秒自动消失。</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">默认空；聚焦输入态；错误态=输入框红框+行内红字。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">下游 F5 澄清 / F6 联想 / F7 检索。</div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">512 字截断生效；计数实时；空输入拦截并提示。</div></div></div></div>
<div class="dev-card" id="dc-f2"><div class="dc-head"><span class="dc-id">F2</span><span class="dc-name">文件输入与查询模态自动识别</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">上传图/视频/音频文件，系统自动识别查询模态并点亮对应矩阵行。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v">拖拽区常驻；未上传时点击或拖入；已上传后该区显示文件卡片。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>单文件</li><li>支持 JPG/JPEG/PNG/MP4/WAV/MP3</li><li>页面文案承诺单文件 ≤100MB。</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v">按扩展名判定模态：jpg/jpeg/png→图像、mp4→视频、wav/mp3→音频；识别约 0.9 秒加载态；成功后写入 queryMode，矩阵仅保留该查询模态所在行可选（其余行 35% 置淡），非该行已选自动清除；音频额外默认选中「音搜音」。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>文件模态=矩阵查询模态（行）</li><li>文件名与模态进入 F7 上下文</li><li>成功后可点 × 移除并复位。</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>类型不符→失败卡片+红字「不支持的文件类型…」</li><li>识别中重复点检索→「文件识别中，请稍候」</li><li>移除文件后矩阵行限制与已选全部复位。</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">默认态（虚线上传区）/拖拽高亮/识别中（spinner）/成功（文件卡+识别结果）/失败（红!卡+错误文案）。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">识别结果驱动 F3 矩阵行；文件名进入 F7。原始文件在正式系统留源系统，本中心只存检索要素。</div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">五类合法扩展名识别为正确模态；非法类型被拦截并给明确文案；成功后矩阵仅该行可选、音频自动选音搜音；×移除后完全复位。</div></div></div></div>
<div class="dev-card" id="dc-f3"><div class="dc-head"><span class="dc-id">F3</span><span class="dc-name">模态矩阵选择（查询模态 × 目标模态）</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">选择「查询模态 × 目标模态」组合，确定本次检索的模态对。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v">与上传同处模态矩阵卡片内（矩阵在上、上传为辅助条）；4×4 全矩阵 16 种模态对全部可检索（4 模态统一共享空间对齐）；无库权限账号整组锁定。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>4×4 矩阵</li><li>行=查询模态（文本/图像/音频/视频），列=目标模态（文本/图像/音频/视频）</li><li>上传文件后按 F2 自动锁定查询模态行。</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v">16 格均显示对勾、可点，点击置 selected（同时只允许一个）；未开通库权限时可选格置 45% 淡并锁定，点击 toast「当前账号未开通库权限」。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>选中模态对（如「文搜图」）作为必填入参进入 F7</li><li>占位参数条与 <a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a> 均显示该模态对。</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>未选模态对直接检索→红字「请在模态矩阵中选择目标模态对」</li><li>切换文件致查询模态行变化时，非该行已选自动清除</li><li>无权限账号不可选。</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">可选/选中（蓝底）/置淡（文件锁定其他行）/锁定（无权限）。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">能力清单：4×4 全矩阵 16 种模态对全部可检索（4 模态统一共享空间对齐，2026-10-27 确认）；入参供 F7、<a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a>。</div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">可选格恰好 16 个（4×4 全亮）；单选互斥；文件上传后非查询行置淡且越行已选被清除；guest 点击被拦截提示。</div></div></div></div>
<div class="dev-card" id="dc-f4"><div class="dc-head"><span class="dc-id">F4</span><span class="dc-name">业务场景选择（<a class="dev-xref" href="P5_策略管理_v5.0_20261025.html">P5</a> 反推）</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">选择业务场景，决定本次检索由哪条策略参数集处理；场景是路由标签，不是策略。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v">常驻下拉，默认「通用」；右侧「场景配置 ↗」跳转 <a class="dev-xref" href="P5_策略管理_v5.0_20261025.html">P5</a>。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>选项不由本页维护：<a class="dev-xref" href="P5_策略管理_v5.0_20261025.html">P5</a> 策略绑定场景并发布后该场景才出现在此</li><li>选项括号内标注当前生效/验证中的策略。演示固定 4 项：通用（STG261018K7M2PQ）、专业（同默认）、作业（STG261025A3F9KX）、训练数据筛选（切换验证中·样板）；括号展示业务编号（STG+日期+安全码，规范32.1）。</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v">选择即写入 state.scene；一场景同一时刻只有一条生效策略，无专属策略走系统默认策略 STG261018K7M2PQ 兜底；F5 场景建议标签可回写本下拉。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>场景作为入参进入 F7→<a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a>（pScene）</li><li>决定后端走哪条已发布策略。</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>新增 / 编辑场景入口统一在 <a class="dev-xref" href="P5_策略管理_v5.0_20261025.html">P5</a> 场景字典（防止悬空）</li><li>解绑的场景不应再出现（演示为静态选项，正式由 <a class="dev-xref" href="P5_策略管理_v5.0_20261025.html">P5</a> 动态拉取）。</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">下拉默认通用；切换即时生效，无加载态。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">上游=<a class="dev-xref" href="P5_策略管理_v5.0_20261025.html">P5</a> 策略管理「业务场景字典」（唯一来源）；下游=F7/P2/P5；训练数据筛选为样板场景，不代表已发布承诺。</div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">4 个选项及括号策略文案与 <a class="dev-xref" href="P5_策略管理_v5.0_20261025.html">P5</a> 字典一致；切换后 F7 携参 pScene 正确；澄清浮层场景标签点击后本下拉同步。</div></div></div></div>
<div class="dev-card" id="dc-f5"><div class="dc-head"><span class="dc-id">F5</span><span class="dc-name">检索意图澄清（浮层，模块A）</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">短词/歧义词检索前给一次改写建议，提升召回质量；每个 query 最多 1 轮。<br>· <b>F-CR-108 检查跨模态检索查询意图澄清准确性</b>：由<b>检索台澄清浮层</b>的响应结果承载（用户选意图后继续检索）。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v">点检索后拦截弹出；条件=有 query 且去空格长度&lt;4 且该 query 本轮未澄清过。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>原始短词</li><li>浮层给 3 条建议候选 + 2 个场景建议标签（训练数据筛选/作业）。</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v">三条出口：①点建议候选→替换原词直接检索；②点场景标签→同步 F4 场景并按原词检索；③「仍按原词检索」→原词直检。任一出口都把该 query 标记为已澄清，本轮不再弹；×/Esc 关闭则不标记、不检索。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>选定的改写词或原词进入 F7</li><li>场景标签回写 F4。</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>≥4 字不触发</li><li>同一 query 已澄清过不再触发</li><li>纯文件检索（无文本）不触发</li><li>Esc 与 × 等价关闭。</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">遮罩浮层默认隐藏；触发显示；选择后关闭并继续；仅 1 轮。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">前端 F1/F4/F7；正式系统建议候选应由后端按召回置信度生成（当前为演示固定项）。</div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">短词首次检索必弹且仅 1 轮；三出口行为分别正确；场景标签同步下拉；Esc/×不检索不标记。</div></div></div></div>
<div class="dev-card" id="dc-f6"><div class="dc-head"><span class="dc-id">F6</span><span class="dc-name">相似查询复用（输入联想，模块B）</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">输入时给历史检索与运营热词，减少重复输入。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v">输入框聚焦且去空格文本≥4 字时下拉显示；失焦 150ms 后收起（保留点击时间）。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>历史=本人近 20 条去重（演示 3 条固定）</li><li>热词=运营推送 Top3。</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v">输入≥4 字即展示两组（演示按输入即现）；mousedown 点选项（避免 blur 先收起）即回填到输入框、更新字数、收起下拉并聚焦。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>回填文本进入 F1，等同手输</li><li>热词运营在 <a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a>、完整历史在 <a class="dev-xref" href="P9_检索历史_v5.0_20261025.html">P9</a>。</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>&lt;4 字不显示</li><li>Esc 收起</li><li>点击弹层外失焦收起</li><li>下拉项点击不被 blur 提前吞掉。</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">隐藏/显示两态；分组标题「历史检索」「运营热词」。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">上游=<a class="dev-xref" href="P9_检索历史_v5.0_20261025.html">P9</a> 检索历史（历史）、<a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a> 结果运营（热词）；下游回填 F1。</div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">≥4 字出现两组、&lt;4 字消失；点选项正确回填并聚焦；失焦/Esc 正常收起。</div></div></div></div>
<div class="dev-card" id="dc-f7"><div class="dc-head"><span class="dc-id">F7</span><span class="dc-name">发起检索并携参跳转结果页</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">校验必填后组装检索上下文，执行检索并跳 <a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a> 结果页（演示为占位确认）。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v">「检索」主按钮；另有「清空」复位全部条件。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>Query（文本或文件语义）、文件（可无）、模态对（必填）、场景（必填，默认通用）。</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v">前置校验顺序：识别中拦截→文本与文件均空拦截→未选模态对拦截；通过后短词走 F5，否则按钮转「检索中」禁用态约 1.2 秒；文件存在而无文本时 Query 显示「（以文件语义检索）」。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>输出四参 Query/文件/模态对/场景，演示弹占位条展示，正式系统携参跳 <a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a> 并创建检索任务（进 <a class="dev-xref" href="P6_任务管理_v5.0_20261025.html">P6</a> 调度）。</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>三类前置校验各自给明确红字/toast</li><li>检索中按钮防重复提交</li><li>清空复位：文本、模态对、文件、场景回通用、澄清记录、错误提示、联想。</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">默认「检索」/加载「 spinner 检索中」禁用/完成弹占位条；错误为行内红字 3.5 秒。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">上游=F1-F6 全部入参；下游=<a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a> 结果页（确认/导出）、<a class="dev-xref" href="P6_任务管理_v5.0_20261025.html">P6</a> 任务管理（正式系统建任务）。</div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">三种缺参分别被拦截且文案准确；通过后四参与界面选择一致；检索中防连点；清空后页面回到初始态。</div></div></div></div>
<div class="dev-card" id="dc-f8"><div class="dc-head"><span class="dc-id">F8</span><span class="dc-name">权限态</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">声明本页可见范围与可操作项的权限来源：由登录态与库授权决定。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v">正式由登录态与库授权决定。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>登录态身份（账号）</li><li>库授权范围（P4 库管理授予）</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v">已授权按可写渲染；未授权按只读渲染，模态矩阵点选与检索动作拦截并提示「当前账号未开通库权限」。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>可写 / 只读渲染态</li><li>无库权限提示</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>未授权点选只提示不开选</li><li>授权范围变更后下次进入生效</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">已授权 / 只读。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">上游=P4 库管理（库授权）；正式由登录态与库授权决定。</div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">未授权不进入检索；只读态提示文案明确。</div></div></div></div>
  </div>
</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { executeSearch, pageHistory, strategyOptions, suggestWords, understand, uploadFile } from '../../api'
import type { StrategyOptionVO } from '../../api/types'
import { useDictStore } from '../../stores/dict'
import type { DictItem } from '../../api/types'

const route = useRoute()
const router = useRouter()
const dictStore = useDictStore()

const targets = ['文本', '图像', '音频', '视频'] as const
type Target = typeof targets[number]
type UploadState = 'default' | 'loading' | 'success' | 'fail'

const matrix = [
  { row: '文本', cells: [
    { pair: '文搜文' }, { pair: '文搜图' }, { pair: '文搜音' }, { pair: '文搜视频' },
  ]},
  { row: '图像', cells: [
    { pair: '图搜文' }, { pair: '图搜图' }, { pair: '图搜音' }, { pair: '图搜视频' },
  ]},
  { row: '音频', cells: [
    { pair: '音搜文' }, { pair: '音搜图' }, { pair: '音搜音' }, { pair: '音搜视频' },
  ]},
  { row: '视频', cells: [
    { pair: '视频搜文' }, { pair: '视频搜图' }, { pair: '视频搜音' }, { pair: '视频搜视频' },
  ]},
]

const query = ref('')
const queryInputRef = ref<HTMLInputElement | null>(null)
const queryFile = ref('')
const queryFileName = ref('')
const queryMode = ref<Target | null>(null)
const selectedPair = ref('')
const scene = ref('general')

/** 检索台策略下拉：空=按场景取生效策略 */
const strategyId = ref('')
const strategyList = ref<StrategyOptionVO[]>([])
const searching = ref(false)
const recognizing = ref(false)
const uploadState = ref<UploadState>('default')
const uploadMessage = ref('')
const uploadError = ref('')
const actionError = ref('')
const selectedSceneLabel = ref('通用')

const scenes = ref<DictItem[]>([])
const historySuggestions = ref<string[]>([])
const hotSuggestions = ref<string[]>([])
const suggestVisible = ref(false)
const searchboxDragging = ref(false)
const dropDragging = ref(false)
const modeNudge = ref(false)

const clarifyVisible = ref(false)
const clarifyRaw = ref('')
const clarifyHint = ref('')
const clarifyOptions = ref<string[]>([])
const clarifiedQueries = ref(new Set<string>())

const devOpen = ref(false)
const toastVisible = ref(false)
const toastMessage = ref('')
const toastType = ref<'info' | 'err'>('info')
const fileInput = ref<HTMLInputElement>()

const sceneOptions = computed(() => scenes.value.length
  ? scenes.value
  : [{ alias: 'general', label: '通用' }])

const selectedTarget = computed(() => {
  if (!selectedPair.value) return ''
  return targets[findTargetIndex(selectedPair.value)] || ''
})

const targetValues: Record<string, string> = {
  文本: 'text',
  图像: 'image',
  音频: 'audio',
  视频: 'video',
}
const targetIcons: Record<Target, string> = {
  文本: 'Document',
  图像: 'Picture',
  音频: 'Microphone',
  视频: 'VideoCamera',
}
const fileIcons: Record<Target, string> = {
  文本: 'Document',
  图像: 'Picture',
  音频: 'Microphone',
  视频: 'VideoCamera',
}

let suggestTimer: ReturnType<typeof setTimeout> | undefined
let actionTimer: ReturnType<typeof setTimeout> | undefined
let modeTimer: ReturnType<typeof setTimeout> | undefined
let toastTimer: ReturnType<typeof setTimeout> | undefined

onMounted(async () => {
  const routeQuery = route.query.q
  if (typeof routeQuery === 'string' && routeQuery.trim()) {
    query.value = routeQuery.trim().slice(0, 512)
  }
  scenes.value = await dictStore.ensure('cmr_scene')
  if (!scenes.value.some(item => item.alias === scene.value) && scenes.value[0]) {
    scene.value = scenes.value[0].alias
  }
  selectedSceneLabel.value = sceneOptions.value.find(item => item.alias === scene.value)?.label || '通用'
  try {
    const page = await pageHistory({ pageNum: 1, pageSize: 20 })
    historySuggestions.value = [...new Set(page.records.map(item => item.queryText || '').filter(Boolean))].slice(0, 3)
  } catch {
    historySuggestions.value = []
  }
  try {
    strategyList.value = await strategyOptions()
  } catch {
    strategyList.value = []
  }
  if (query.value.trim().length >= 4) {
    onQueryInput()
  }
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  clearTimeout(suggestTimer)
  clearTimeout(actionTimer)
  clearTimeout(modeTimer)
  clearTimeout(toastTimer)
})

function rowDisabled(row: string) {
  return !!queryMode.value && row !== queryMode.value
}

function findTargetIndex(pair: string) {
  for (const row of matrix) {
    const index = row.cells.findIndex(cell => cell.pair === pair)
    if (index >= 0) return index
  }
  return -1
}

function selectPair(pair: string) {
  selectedPair.value = selectedPair.value === pair ? '' : pair
  hideActionError()
}

function selectTarget(target: Target) {
  const source = queryMode.value || '文本'
  const row = matrix.find(item => item.row === source)
  const index = targets.indexOf(target)
  const cell = row?.cells[index]
  if (!cell) {
    showToast(`${source}输入暂不支持检索「${target}」结果`, 'err')
    return
  }
  selectedPair.value = cell.pair
  hideActionError()
}

function onQueryInput() {
  hideActionError()
  clearTimeout(suggestTimer)
  if (query.value.trim().length < 4) {
    suggestVisible.value = false
    return
  }
  suggestTimer = setTimeout(async () => {
    try {
      const words = await suggestWords(scene.value, query.value.trim())
      hotSuggestions.value = words.slice(0, 3)
    } catch {
      hotSuggestions.value = []
    }
    suggestVisible.value = Boolean(historySuggestions.value.length || hotSuggestions.value.length)
  }, 180)
}

/** 整个大搜索框都是输入热区：点空白处（图标、框内留白）也聚焦输入框 */
function onSearchboxClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  if (target?.closest('button, a, select, .suggest-pop')) return
  queryInputRef.value?.focus()
}

function onQueryFocus() {
  if (query.value.trim().length >= 4 && (historySuggestions.value.length || hotSuggestions.value.length)) {
    suggestVisible.value = true
  }
}

function onQueryBlur() {
  setTimeout(() => { suggestVisible.value = false }, 150)
}

function refill(value: string) {
  query.value = value
  suggestVisible.value = false
  hideActionError()
}

function triggerFileSelect() {
  if (uploadState.value !== 'default') return
  fileInput.value?.click()
}

function onSearchboxDrop(event: DragEvent) {
  searchboxDragging.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) handleFile(file)
}

function onDropzoneDrop(event: DragEvent) {
  dropDragging.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) handleFile(file)
}

function onFileInputChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) handleFile(file)
}

async function handleFile(file: File) {
  const extension = file.name.split('.').pop()?.toLowerCase() || ''
  const image = ['jpg', 'jpeg', 'png'].includes(extension)
  const video = extension === 'mp4'
  const audio = ['wav', 'mp3'].includes(extension)
  if (!image && !video && !audio) {
    uploadState.value = 'fail'
    queryFileName.value = file.name
    uploadMessage.value = '不支持的文件类型，请上传 JPG/PNG/MP4/WAV/MP3'
    uploadError.value = uploadMessage.value
    return
  }
  if (file.size > 100 * 1024 * 1024) {
    uploadState.value = 'fail'
    queryFileName.value = file.name
    uploadMessage.value = '文件超过 100MB 上限'
    uploadError.value = uploadMessage.value
    return
  }

  recognizing.value = true
  uploadState.value = 'loading'
  queryFileName.value = file.name
  uploadMessage.value = '正在识别模态…'
  uploadError.value = ''
  try {
    const [uploaded] = await Promise.all([uploadFile(file), delay(900)])
    queryFile.value = uploaded.path
    queryMode.value = image ? '图像' : video ? '视频' : '音频'
    uploadState.value = 'success'
    uploadMessage.value = `已识别为${queryMode.value}模态，请选择目标检索类型`
    if (selectedPair.value && !matrix.find(item => item.row === queryMode.value)?.cells.some(cell => cell.pair === selectedPair.value)) {
      selectedPair.value = ''
    }
    if (audio) selectedPair.value = '音搜音'
  } catch (error) {
    uploadState.value = 'fail'
    uploadMessage.value = (error as Error).message || '文件上传失败'
    uploadError.value = uploadMessage.value
    queryFile.value = ''
    queryMode.value = null
  } finally {
    recognizing.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

function clearFile() {
  queryFile.value = ''
  queryFileName.value = ''
  queryMode.value = null
  uploadState.value = 'default'
  uploadMessage.value = ''
  uploadError.value = ''
  if (fileInput.value) fileInput.value.value = ''
}

function resetAll() {
  query.value = ''
  queryFile.value = ''
  queryFileName.value = ''
  queryMode.value = null
  selectedPair.value = ''
  clarifiedQueries.value.clear()
  scene.value = sceneOptions.value[0]?.alias || 'general'
  uploadState.value = 'default'
  uploadMessage.value = ''
  uploadError.value = ''
  actionError.value = ''
  suggestVisible.value = false
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
  if (!selectedPair.value) {
    showActionError('请先选择检索类型（图像 / 文本 / 音频 / 视频）')
    modeNudge.value = false
    setTimeout(() => { modeNudge.value = true }, 0)
    clearTimeout(modeTimer)
    modeTimer = setTimeout(() => { modeNudge.value = false }, 600)
    return
  }

  if (text && !clarifiedQueries.value.has(text)) {
    try {
      const result = await understand(buildUnderstandBody())
      if (result.needClarify) {
        clarifyRaw.value = text
        clarifyHint.value = result.hint || ''
        clarifyOptions.value = normalizeClarifyOptions(result.clarifyOptions)
        clarifyVisible.value = true
        return
      }
    } catch {
      // 查询理解失败时允许继续检索，检索接口仍会给出最终错误。
    }
  }
  await runSearch()
}

function buildUnderstandBody() {
  return queryFile.value ? { queryFile: queryFile.value } : { query: query.value.trim() }
}

async function runSearch(clarifiedQuery?: string) {
  searching.value = true
  try {
    const body: Record<string, unknown> = {}
    if (queryFile.value) body.queryFile = queryFile.value
    if (query.value.trim()) body.query = query.value.trim()
    if (scene.value) body.scene = scene.value
    if (strategyId.value) body.strategyId = strategyId.value
    if (selectedTarget.value) body.targetModalities = [targetValues[selectedTarget.value]]
    if (clarifiedQuery) {
      body.query = clarifiedQuery
      body.clarified = true
      body.clarifiedQuery = clarifiedQuery
    }
    const result = await executeSearch(body)
    router.push(`/search/results/${result.resultSetId}`)
  } catch (error) {
    showToast((error as Error).message, 'err')
  } finally {
    searching.value = false
  }
}

function normalizeClarifyOptions(options: string[]) {
  const normalized = (options ?? []).map(option => {
    try {
      const value = JSON.parse(option) as { query?: string; intent?: string }
      return value.query || value.intent || option
    } catch {
      return option
    }
  }).filter(Boolean)
  return normalized.slice(0, 3)
}

function closeClarify() {
  clarifyVisible.value = false
}

function pickClarify(value: string) {
  const original = query.value.trim()
  clarifiedQueries.value.add(original)
  query.value = value
  clarifyVisible.value = false
  void runSearch(value)
}

function addSceneAndGo(label: string) {
  const original = query.value.trim()
  clarifiedQueries.value.add(original)
  const found = sceneOptions.value.find(item => item.label === label)
  if (found) scene.value = found.alias
  clarifyVisible.value = false
  void runSearch(original)
}

function searchDirect() {
  const original = query.value.trim()
  clarifiedQueries.value.add(original)
  clarifyVisible.value = false
  void runSearch(original)
}

function showActionError(message: string) {
  actionError.value = message
  clearTimeout(actionTimer)
  actionTimer = setTimeout(hideActionError, 3500)
}

function hideActionError() {
  actionError.value = ''
}

function showToast(message: string, type: 'info' | 'err' = 'info') {
  toastMessage.value = message
  toastType.value = type
  toastVisible.value = true
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toastVisible.value = false }, 2600)
}

function openDev() {
  devOpen.value = true
}

function closeDev() {
  devOpen.value = false
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    suggestVisible.value = false
    clarifyVisible.value = false
    devOpen.value = false
  }
}

function delay(ms: number) {
  return new Promise(resolve => setTimeout(resolve, ms))
}
</script>

<style scoped>
:root{
  /* 清爽冷白蓝 token */
  --workspace-bg:#f6f9fe;
  --module-bg:#f3f8ff;
  --content-card:#ffffff;
  --content-soft:#fbfdff;
  --flow-card:#f8fbff;
  --content-line:#e6edf8;
  --content-line-strong:#d9e7f8;
  --system-blue:#4169f6;
  --system-blue-weak:#f1f5ff;
  --ink:#1f2937;
  --muted:#667085;
  /* 语义色（L4 token，蓝色体系统一微调） */
  --success:#34a853;
  --warning:#f0a020;
  --error:#e5484d;
  --text-primary:#262626;
  --text-secondary:#8c8c8c;
  --radius:8px;
  --shadow-card:0 2px 8px rgba(31,58,122,.05);
  --shadow-pop:0 10px 22px rgba(64,80,112,.14);
}
*{margin:0;padding:0;box-sizing:border-box}
html,body{height:100%}
body{
  font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif;
  background:var(--workspace-bg);
  color:var(--text-primary);
  font-size:14px;
  line-height:1.5;
  display:flex;
}
button{font-family:inherit;cursor:pointer}
::selection{background:rgba(65,105,246,.16)}

/* ============ 左侧全局导航 ============ */
.sidebar{
  width:220px;flex-shrink:0;
  background:#fff;border-right:1px solid var(--content-line);
  display:flex;flex-direction:column;
  height:100vh;position:sticky;top:0;
}
.brand{
  padding:18px 18px 14px;
  display:flex;align-items:center;gap:10px;
  border-bottom:1px solid var(--content-line);
}
.brand .logo{
  width:30px;height:30px;border-radius:7px;flex-shrink:0;
  background:linear-gradient(135deg,#4169f6,#6e8cff);
  display:grid;place-items:center;color:#fff;font-size:15px;font-weight:700;
}
.brand b{font-size:14px;color:#16204a;display:block;line-height:1.25}
.brand span{font-size:11px;color:var(--text-secondary)}
.nav-scroll{flex:1;overflow-y:auto;padding:10px 12px}
.nav-item{text-decoration:none;
  display:flex;align-items:center;gap:9px;
  padding:9px 10px;margin-bottom:2px;
  border-radius:6px;color:#344054;font-size:13px;
  cursor:pointer;user-select:none;
}
.nav-item .ico{width:18px;height:18px;display:flex;align-items:center;justify-content:center;color:#8a96ad;flex-shrink:0}
.nav-item .ico svg{width:16px;height:16px;display:block}
.nav-item.active .ico{color:var(--system-blue)}
.nav-item:hover{background:var(--system-blue-weak);color:var(--system-blue)}
.nav-item.active{
  background:var(--system-blue-weak);color:var(--system-blue);font-weight:600;
  box-shadow:inset 2px 0 0 var(--system-blue);
}
.nav-item.soon{color:#b3bccc;cursor:not-allowed}
.nav-item.soon:hover{background:transparent;color:#b3bccc}
.nav-divider{height:1px;background:var(--content-line);margin:8px 10px}

/* ============ 主区 ============ */
.main{flex:1;min-width:0;display:flex;flex-direction:column;height:100vh;overflow-y:auto}

.content{padding:20px 24px 32px;display:flex;flex-direction:column;gap:16px;max-width:1280px;width:100%;margin:0 auto}

/* 卡片基础 */
.card{border:1px solid #e4edf8;border-radius:var(--radius);background:#fff}

/* Section1 模块介绍 */
.module-intro{
  display:block;
  padding:18px 20px;
  border:1px solid #e4edf8;
  border-radius:10px;
  background:#fff;
}
.module-intro h2{font-size:20px;font-weight:700;color:#111827;margin-bottom:7px;display:flex;align-items:center;gap:10px}
.module-intro p{color:var(--muted);line-height:1.65;max-width:760px;font-size:13px}

/* Section3 检索工作台 */
.workbench{padding:18px 20px;display:flex;flex-direction:column;gap:16px}
.sec-head{display:flex;align-items:center;gap:8px}
.sec-head .sec-title{font-size:15px;font-weight:700;color:#1f2a44}
.sec-head .sec-sub{font-size:12px;color:#9aa6bd}
.sec-head::before{content:"";width:3px;height:14px;border-radius:2px;background:var(--system-blue)}

.input-row{display:grid;grid-template-columns:minmax(0,1fr) 280px;gap:14px}
.query-box{position:relative}
.query-box textarea{
  width:100%;height:150px;resize:none;
  border:1px solid #cfd8e8;border-radius:var(--radius);
  padding:12px 14px;font-size:14px;font-family:inherit;color:var(--text-primary);
  outline:none;transition:border-color .15s,box-shadow .15s;line-height:1.6;
}
.query-box textarea:focus{border-color:#8fa3ff;box-shadow:0 0 0 3px rgba(65,105,246,.12)}
.query-box textarea.err{border-color:var(--error);box-shadow:0 0 0 3px rgba(229,72,77,.1)}
.query-foot{display:flex;align-items:center;justify-content:space-between;margin-top:6px;font-size:12px;color:var(--text-secondary)}
.inline-err{color:var(--error);font-size:12px;display:none}
.inline-err.show{display:block}

/* Z3 联想下拉 */
.suggest-pop{
  position:absolute;left:0;right:0;top:calc(100% + 34px);z-index:30;
  border:1px solid #d8e1ef;border-radius:var(--radius);background:#fff;
  box-shadow:var(--shadow-pop);padding:6px;display:none;
}
.suggest-pop.show{display:block}
.suggest-group{padding:5px 8px 3px;font-size:11px;color:#9aa6bd;font-weight:600}
.suggest-item{
  display:flex;align-items:center;gap:8px;width:100%;
  padding:7px 8px;border:0;border-radius:5px;background:transparent;
  font-size:13px;color:#344054;text-align:left;
}
.suggest-item:hover{background:var(--system-blue-weak);color:var(--system-blue)}
.suggest-item .s-hot{
  margin-left:auto;font-size:10px;color:#fff;background:linear-gradient(135deg,#f0a020,#f5b942);
  border-radius:3px;padding:1px 5px;font-weight:600;
}
.suggest-item .s-hist{margin-left:auto;font-size:11px;color:#b3bccc}

/* 拖拽上传 */
.dropzone{
  border:1.5px dashed #c3d2ec;border-radius:var(--radius);
  background:var(--content-soft);
  display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;
  padding:16px;text-align:center;min-height:150px;
  transition:all .15s;cursor:pointer;position:relative;
}
.dropzone:hover,.dropzone.drag{border-color:var(--system-blue);background:var(--system-blue-weak)}
.dropzone .dz-ico{
  width:38px;height:38px;border-radius:10px;background:#eef3ff;
  display:grid;place-items:center;font-size:18px;color:var(--system-blue);
}
.dropzone strong{font-size:13px;color:#273244}
.dropzone span{font-size:11.5px;color:var(--muted);line-height:1.5}
.dropzone.success{border-style:solid;border-color:#bfe3c9;background:#f6fef8;cursor:default}
.dropzone.fail{border-style:solid;border-color:#f3c4c6;background:#fff7f7;cursor:default}
.dz-file{display:flex;align-items:center;gap:9px;width:100%;text-align:left}
.dz-file .f-thumb{
  width:40px;height:40px;border-radius:6px;background:#eaf0ff;color:var(--system-blue);
  display:grid;place-items:center;font-size:17px;flex-shrink:0;
}
.dz-file .f-meta{min-width:0;flex:1}
.dz-file .f-name{font-size:12.5px;font-weight:600;color:#1f2a44;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.dz-file .f-rec{font-size:11px;color:var(--success);margin-top:2px}
.dz-file .f-rec.err{color:var(--error)}
.dz-clear{border:0;background:transparent;color:#b3bccc;font-size:15px;padding:4px}
.dz-clear:hover{color:var(--error)}
.dz-types{display:flex;gap:6px;margin-top:2px;flex-wrap:wrap;justify-content:center}
.dz-types em{font-style:normal;font-size:10px;color:#8fa0bd;border:1px solid #e2ebf7;border-radius:3px;padding:1px 5px}

/* Z2b 模态矩阵 */
.matrix-wrap{position:relative}
.matrix-legend{display:flex;gap:16px;align-items:center;margin-bottom:10px;font-size:12px;color:var(--muted);flex-wrap:wrap}
.matrix-legend .lg{display:flex;align-items:center;gap:5px}
.matrix-legend .mk{
  width:22px;height:22px;border-radius:5px;display:grid;place-items:center;font-size:11px;font-weight:700;
}
.mk.on{background:#eef3ff;color:var(--system-blue);border:1px solid #cdd9ff}
.mk.lock{background:#f4f5f8;color:#a8b0c0;border:1px solid #e6e9f0}
.mk.off{background:#fafbfd;border:1px dashed #edf0f6;color:#d5dbe6}
.modal-matrix{
  display:grid;grid-template-columns:84px repeat(4,minmax(0,1fr));gap:6px;
  max-width:620px;
}
.mm-corner,.mm-colh,.mm-rowh{font-size:12px;color:var(--muted);display:grid;place-items:center;font-weight:600}
.mm-colh{height:26px}
.mm-rowh{justify-content:flex-end;padding-right:4px;font-weight:400;color:#667085}
.mm-cell{
  height:40px;border-radius:6px;display:grid;place-items:center;font-size:14px;
  position:relative;user-select:none;
}
.mm-cell.available{
  background:#eef3ff;border:1px solid #cdd9ff;color:var(--system-blue);cursor:pointer;
  transition:all .12s;
}
.mm-cell.available:hover{background:#e0e9ff;border-color:var(--system-blue);transform:translateY(-1px)}
.mm-cell.selected{
  background:var(--system-blue);border-color:var(--system-blue);color:#fff;
  box-shadow:0 4px 10px rgba(65,105,246,.3);
}
.mm-cell.locked{
  background:#f4f5f8;border:1px solid #e6e9f0;color:#b8bfcc;cursor:not-allowed;opacity:.75;
}
.mm-cell.locked .lk{font-size:11px}
.mm-cell.blank{background:#fafbfd;border:1px dashed #edf0f6;cursor:default}
.mm-cell.dim{opacity:.35;pointer-events:none}

/* 场景 + 按钮 */
.scene-row{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.scene-row .sl{font-size:13px;color:var(--muted);font-weight:600}
.scene-select{height:32px;border:1px solid var(--content-line-strong);border-radius:6px;padding:0 10px;font-size:12.5px;color:#1f2a44;background:#fff;min-width:280px}
.scene-select:focus{outline:none;border-color:var(--system-blue)}
.scene-cfg{font-size:12px;color:var(--system-blue);text-decoration:none;margin-left:2px}
.scene-cfg:hover{text-decoration:underline}
.scene-tag{
  border:1px solid var(--content-line-strong);background:#fff;color:#475467;
  border-radius:15px;padding:5px 14px;font-size:12.5px;position:relative;
  display:inline-flex;align-items:center;gap:6px;
}
.scene-tag:hover{border-color:#aebdf5;color:var(--system-blue)}
.scene-tag.active{background:var(--system-blue);border-color:var(--system-blue);color:#fff;font-weight:600}
.action-row{display:flex;align-items:center;gap:10px;margin-top:2px}
.btn-primary{
  height:36px;padding:0 26px;border:0;border-radius:6px;
  background:var(--system-blue);color:#fff;font-size:14px;font-weight:600;
  display:inline-flex;align-items:center;gap:7px;
  box-shadow:0 3px 8px rgba(65,105,246,.25);transition:all .15s;
}
.btn-primary:hover{background:#335be8;box-shadow:0 4px 12px rgba(65,105,246,.32)}
.btn-primary:disabled{opacity:.7;cursor:wait}
.btn-ghost{
  height:36px;padding:0 18px;border-radius:6px;border:1px solid #d7e0f0;
  background:#fff;color:#475467;font-size:13px;
}
.btn-ghost:hover{border-color:var(--system-blue);color:var(--system-blue)}
.spinner{
  width:14px;height:14px;border:2px solid rgba(255,255,255,.4);border-top-color:#fff;
  border-radius:50%;animation:spin .7s linear infinite;
}
@keyframes spin{to{transform:rotate(360deg)}}
.action-hint{font-size:12px;color:#9aa6bd}

.badge{font-size:11px;padding:2px 8px;border-radius:10px;font-weight:600;display:inline-block;justify-self:start}
.badge.ok{background:#e8f7ec;color:var(--success)}
.badge.fail{background:#fdecec;color:var(--error)}
.badge.run{background:#e9f0ff;color:var(--system-blue)}

/* Z4 意图澄清浮层 */
.modal-mask{
  position:fixed;inset:0;background:rgba(23,33,66,.42);z-index:100;
  display:none;align-items:center;justify-content:center;padding:20px;
}
.modal-mask.show{display:flex}
.modal-box{
  width:480px;max-width:100%;background:#fff;border-radius:10px;
  box-shadow:0 16px 48px rgba(20,32,70,.28);overflow:hidden;
  animation:pop .18s ease;
}
@keyframes pop{from{transform:translateY(8px) scale(.98);opacity:0}to{transform:none;opacity:1}}
.modal-head{padding:16px 20px 0;display:flex;align-items:flex-start;justify-content:space-between}
.modal-head h3{font-size:16px;color:#1f2a44;display:flex;align-items:center;gap:8px}
.modal-close{border:0;background:transparent;color:#b3bccc;font-size:18px;line-height:1}
.modal-close:hover{color:#667085}
.modal-body{padding:12px 20px 4px}
.modal-body .mb-tip{font-size:12px;color:var(--muted);margin-bottom:10px}
.clarify-item{
  width:100%;display:flex;align-items:center;gap:10px;text-align:left;
  border:1px solid var(--content-line-strong);border-radius:7px;background:#fff;
  padding:10px 12px;margin-bottom:8px;font-size:13px;color:#273244;
}
.clarify-item:hover{border-color:var(--system-blue);background:var(--system-blue-weak);color:var(--system-blue)}
.clarify-item .ci-q{flex:1}
.clarify-scenes{display:flex;gap:8px;margin:6px 0 4px}
.modal-foot{padding:12px 20px 18px;display:flex;align-items:center;justify-content:space-between;margin-top:6px;border-top:1px solid #f0f4fb}
.modal-foot a{font-size:12.5px;color:var(--muted);text-decoration:underline;text-underline-offset:3px}
.modal-foot a:hover{color:var(--system-blue)}
.modal-once{font-size:11px;color:#b3bccc}

/* 全局消息 */
.toast{
  position:fixed;top:56px;left:50%;transform:translateX(-50%) translateY(-12px);
  z-index:200;background:#2b3245;color:#fff;font-size:13px;
  padding:9px 18px;border-radius:7px;box-shadow:0 8px 24px rgba(20,32,70,.25);
  opacity:0;pointer-events:none;transition:all .2s;display:flex;align-items:center;gap:7px;
}
.toast.show{opacity:1;transform:translateX(-50%) translateY(0)}
.toast.err{background:#c0392b}
.toast.ok{background:#1e8e4a}

@media (max-width:1100px){
  .input-row{grid-template-columns:1fr}
}

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
.dev-ov{background:#fff;border:1px solid #e4eaf3;border-radius:10px;padding:14px 18px;margin-bottom:16px}
.dev-ov h4{margin:0 0 6px;font-size:13px;color:var(--system-blue)}
.dev-ov p{margin:0;line-height:1.75;color:#475467;font-size:12.5px}
.dev-ov+.dev-ov{margin-top:0}
.ov-grid{display:grid;grid-template-columns:1fr;gap:12px}
/* 功能卡 */
.dev-card{background:#fff;border:1px solid #e4eaf3;border-radius:10px;margin-bottom:14px;overflow:hidden;scroll-margin-top:14px}
.dc-head{display:flex;align-items:center;gap:8px;padding:11px 16px;background:#fbfcfe;border-bottom:1px solid #eef2f8}
.dc-id{font-size:11px;font-weight:700;color:#fff;background:#b54708;border-radius:4px;padding:1px 7px;flex:0 0 auto}
.dc-name{font-size:13.5px;font-weight:700;color:#1f2937}
.dc-body{padding:6px 16px 12px}
.dc-row{display:flex;gap:10px;padding:7px 0;border-bottom:1px dashed #f0f3f8;font-size:12.5px;line-height:1.7}
.dc-row:last-child{border-bottom:none}
.dc-k{flex:0 0 78px;font-weight:700;color:#344054}
.dc-v{flex:1;color:#475467}
.dc-v ul{margin:0;padding-left:17px}
.dc-v li{margin:2px 0}
.k-in .dc-k{color:#027a48}.k-out .dc-k{color:#1f5fd4}.k-err .dc-k{color:#c0362c}.k-dep .dc-k{color:#7a5af8}
.demo-limit{display:inline-block;font-size:10.5px;font-weight:600;color:#b54708;background:#fdf3e6;border:1px solid #f6d9b0;border-radius:4px;padding:0 6px;margin-left:6px;vertical-align:1px}
.dev-xref{color:#1f5fd4;font-weight:700;text-decoration:none;border-bottom:1px dashed #9dbcf5;padding:0 1px;white-space:nowrap}
.dev-xref:hover{background:#eaf1fe;border-bottom-style:solid}

/* ===== 意图澄清浮层 ===== */
#clarifyMask .modal-box{max-height:88vh;overflow:auto}

/* ================================================================
   P1 v5.1 · C 端检索首页视觉重构（覆盖上方旧工作台样式）
   主色 #1f5fd4 / 大留白 / 大字号 Hero / 大圆角 + 轻阴影
   ================================================================ */
:root{
  --brand:#1f5fd4;
  --brand-deep:#17479f;
  --brand-soft:#eef4ff;
  --brand-line:#d6e4fb;
  --brand-ink:#10203f;
  --text-1:#1f2937;
  --text-2:#475467;
  --text-3:#667085;
  --text-4:#98a2b3;
  --line:#e8eef8;
  --line-soft:#f0f4fb;
  --radius-lg:22px;
  --radius-md:16px;
  --radius-sm:10px;
  --shadow-card:0 2px 10px rgba(23,52,110,.05);
  --shadow-soft:0 12px 32px rgba(23,52,110,.07);
  --shadow-hero:0 26px 64px rgba(21,66,150,.14),0 2px 8px rgba(21,66,150,.06);
  --shadow-pop:0 18px 40px rgba(20,40,90,.16);
}
*{margin:0;padding:0;box-sizing:border-box}
html,body{height:100%}
body{
  font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif;
  background:
    radial-gradient(1100px 560px at 12% -8%,#e9f1ff 0%,rgba(233,241,255,0) 62%),
    radial-gradient(900px 520px at 102% 2%,#eef1ff 0%,rgba(238,241,255,0) 55%),
    var(--workspace-bg);
  color:var(--text-1);
  font-size:14px;
  line-height:1.5;
  display:flex;
  min-height:100vh;
}
button{font-family:inherit;cursor:pointer;color:inherit}
input,select,textarea{font-family:inherit}
::selection{background:rgba(31,95,212,.16)}

/* ---- 主区容器 ---- */
.main{flex:1;min-width:0;height:100vh;overflow-y:auto}
.page{
  max-width:1200px;width:100%;margin:0 auto;
  padding:30px 40px 72px;
  display:flex;flex-direction:column;gap:22px;
}

/* ---- Hero ---- */
.hero{
  position:relative;overflow:hidden;
  border:1px solid #e7effb;border-radius:30px;
  background:
    radial-gradient(760px 380px at 50% -18%,rgba(31,95,212,.10),rgba(31,95,212,0) 68%),
    linear-gradient(180deg,#fcfdff 0%,#f4f9ff 100%);
  box-shadow:0 24px 60px rgba(24,60,130,.06);
  padding:54px 46px 38px;
  text-align:center;
}
.hero::before{content:"";position:absolute;left:-170px;top:-230px;width:440px;height:440px;border-radius:50%;background:radial-gradient(circle,rgba(31,95,212,.14),rgba(31,95,212,0) 70%);pointer-events:none}
.hero::after{content:"";position:absolute;right:-190px;bottom:-240px;width:480px;height:380px;border-radius:50%;background:radial-gradient(circle,rgba(110,140,255,.15),rgba(110,140,255,0) 70%);pointer-events:none}
.hero-inner{position:relative;z-index:1;max-width:940px;margin:0 auto}
.hero h1{
  margin:0;font-size:clamp(40px,4.2vw,54px);line-height:1.12;
  font-weight:700;letter-spacing:.01em;color:var(--brand-ink);
}
.hero h1 span{color:var(--brand)}
.hero-sub{margin:16px auto 0;max-width:660px;font-size:16.5px;line-height:1.7;color:#5b6b85;letter-spacing:.005em}

/* ---- 大搜索框 ---- */
.searchbox{
  position:relative;display:flex;align-items:center;gap:10px;
  height:80px;margin:34px auto 0;padding:0 10px 0 22px;
  background:#fff;border:1px solid #e3ecfa;border-radius:24px;
  box-shadow:var(--shadow-hero);
  transition:border-color .18s,box-shadow .18s;
}
.searchbox:focus-within{border-color:#b9cef4;box-shadow:0 28px 68px rgba(21,66,150,.18),0 0 0 4px rgba(31,95,212,.07)}
.searchbox.is-error{border-color:#efb1b4;box-shadow:0 24px 56px rgba(170,40,40,.12),0 0 0 4px rgba(217,45,32,.05)}
.searchbox.drag{border-color:#9dbcf0;background:#fbfdff;box-shadow:0 0 0 4px rgba(31,95,212,.08),var(--shadow-hero)}
.search-ico{flex:0 0 auto;width:24px;height:24px;display:grid;place-items:center;color:#7d90ae}
.search-ico svg{width:22px;height:22px}
.search-field{position:static;flex:1;min-width:0;height:100%;display:flex;align-items:center}
#queryInput{
  width:100%;height:100%;border:0;outline:none;background:transparent;
  font-size:18.5px;color:#1b2b45;letter-spacing:.005em;
}
#queryInput::placeholder{color:#9dabc1}
.btn-search{
  flex:0 0 auto;height:58px;padding:0 30px;border:0;border-radius:17px;
  background:linear-gradient(135deg,#2a68e0 0%,#1f5fd4 62%,#1a51bc 100%);
  color:#fff;font-size:16px;font-weight:700;letter-spacing:.04em;
  display:inline-flex;align-items:center;justify-content:center;gap:8px;
  box-shadow:0 12px 26px rgba(31,95,212,.30);
  transition:transform .15s,box-shadow .15s,filter .15s;
}
.btn-search:hover{transform:translateY(-1px);box-shadow:0 16px 30px rgba(31,95,212,.36)}
.btn-search:active{transform:translateY(0);box-shadow:0 8px 18px rgba(31,95,212,.28)}
.btn-search:disabled{opacity:.78;cursor:wait;transform:none;box-shadow:0 8px 18px rgba(31,95,212,.22)}
.btn-search svg{width:18px;height:18px}
.inline-err{display:none;font-size:12.5px;color:#d92d20;line-height:1.5}
.inline-err.show{display:block}
#actionErr{max-width:940px;margin:10px auto 0;text-align:left;padding-left:8px}
#uploadErr{margin-top:8px}

/* ---- 联想下拉 ---- */
.suggest-pop{
  position:absolute;left:12px;right:12px;top:calc(100% + 10px);z-index:60;
  background:#fff;border:1px solid #e2eaf7;border-radius:16px;
  box-shadow:var(--shadow-pop);padding:8px;display:none;text-align:left;
}
.suggest-pop.show{display:block}
.suggest-group{padding:6px 9px 4px;font-size:11px;color:#9aa8bf;font-weight:600;letter-spacing:.04em}
.suggest-item{
  display:flex;align-items:center;gap:8px;width:100%;
  padding:8px 10px;border:0;border-radius:9px;background:transparent;
  font-size:13px;color:#344054;text-align:left;transition:background .12s,color .12s;
}
.suggest-item:hover{background:var(--brand-soft);color:var(--brand)}
.suggest-item .s-hot{
  margin-left:auto;font-size:10px;color:#fff;background:linear-gradient(135deg,#f0a020,#f5b942);
  border-radius:4px;padding:1px 5px;font-weight:600;
}
.suggest-item .s-hist{margin-left:auto;font-size:11px;color:#a8b3c5}

/* ---- 检索类型 / 业务场景 ---- */
.search-meta{
  display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap;
  margin:18px auto 0;max-width:940px;text-align:left;
}
.mode-group{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.mg-label{font-size:12px;font-weight:600;color:#8b9ab2;letter-spacing:.08em;margin-right:2px}
.mode-tab{
  height:42px;padding:0 16px;border-radius:12px;border:1px solid #e2eaf7;background:#fff;
  color:#43536b;font-size:13.5px;display:inline-flex;align-items:center;gap:7px;
  transition:border-color .15s,background .15s,color .15s,box-shadow .15s;
}
.mode-tab svg{width:17px;height:17px;color:#7b8ca8;transition:color .15s}
.mode-tab:hover{border-color:#c6d8f5;color:var(--brand);background:#f8fbff}
.mode-tab:hover svg{color:var(--brand)}
.mode-tab.active{
  background:var(--brand-soft);border-color:#bcd2f6;color:var(--brand);font-weight:600;
  box-shadow:inset 0 0 0 1px rgba(31,95,212,.08);
}
.mode-tab.active svg{color:var(--brand)}
.mode-group.nudge{animation:mgNudge .45s ease}
@keyframes mgNudge{0%,100%{transform:translateX(0)}20%{transform:translateX(-4px)}60%{transform:translateX(4px)}80%{transform:translateX(-2px)}}
.scene-pick{
  display:flex;align-items:center;gap:4px;padding:3px 4px 3px 12px;
  border:1px solid #e2eaf7;border-radius:12px;background:#fff;
}
.scene-pick .sp-label{font-size:12px;font-weight:600;color:#8b9ab2;letter-spacing:.04em;white-space:nowrap}
.scene-select{
  height:34px;border:1px solid transparent;border-radius:9px;outline:none;
  background-color:transparent;font-size:13px;color:#26374f;
  padding:0 26px 0 8px;min-width:268px;max-width:100%;cursor:pointer;
  appearance:none;-webkit-appearance:none;-moz-appearance:none;
  background-image:url("data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%237f90ad' stroke-width='2.2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E");
  background-repeat:no-repeat;background-position:right 6px center;background-size:13px 13px;
  transition:background-color .15s,border-color .15s;
}
.scene-select:hover{background-color:#f6f9ff;border-color:#e2eaf7}
.scene-select:focus{border-color:#bcd2f6;background-color:#fff;box-shadow:0 0 0 3px rgba(31,95,212,.08)}
.scene-select option{color:#26374f;background:#fff}
.scene-cfg{
  display:inline-flex;align-items:center;gap:3px;font-size:11.5px;color:#7f90ad;
  text-decoration:none;padding:0 8px 0 8px;border-left:1px solid #eef2f9;margin-left:2px;white-space:nowrap;
}
.scene-cfg svg{width:12px;height:12px}
.scene-cfg:hover{color:var(--brand)}

/* ---- 辅助信息 ---- */
.search-aux{
  display:flex;align-items:center;justify-content:space-between;gap:12px;
  margin:12px auto 0;max-width:940px;font-size:12px;color:#93a1b7;
}
.search-aux .pa b{color:var(--brand);font-weight:600}
.search-aux .ca b{color:#667085;font-weight:600;font-variant-numeric:tabular-nums}
.search-aux .aux-right{display:inline-flex;align-items:center;gap:12px}
.link-btn{border:0;background:transparent;font-size:12px;color:#93a1b7;padding:2px 4px;text-decoration:underline;text-underline-offset:3px}
.link-btn:hover{color:var(--brand)}
.link-btn b{color:var(--brand);font-weight:600;font-variant-numeric:tabular-nums}

/* ---- 模态矩阵（直接展示）+ 文件上传 ---- */
.setup{display:block}
.panel{
  background:#fff;border:1px solid #e8eef8;border-radius:20px;
  padding:22px 24px 22px;box-shadow:var(--shadow-card);
  transition:border-color .18s,box-shadow .18s;
}
.panel:hover{border-color:#dfe9f7;box-shadow:var(--shadow-soft)}
.setup-head{display:flex;align-items:center;gap:7px;margin-bottom:14px;flex-wrap:wrap}
.ph-ico{width:22px;height:22px;display:grid;place-items:center;color:#8fa2c0}
.ph-ico svg{width:17px;height:17px}
.setup-head h3{font-size:14px;font-weight:700;color:#1b2a44;letter-spacing:.01em}
.setup-hint{font-size:11px;color:#a2aec2;margin-left:2px}
.upload-strip{margin-top:18px;padding-top:16px;border-top:1px solid #eef3fb}
.dropzone.compact{
  flex-direction:row;align-items:center;justify-content:flex-start;gap:12px;
  min-height:0;padding:12px 14px;text-align:left;border-radius:12px;
}
.dropzone.compact .dz-default{display:grid;grid-template-columns:auto minmax(0,1fr);column-gap:12px;row-gap:2px;align-items:center;width:100%}
.dropzone.compact .dz-ico{grid-row:1/3;width:38px;height:38px;border-radius:10px}
.dropzone.compact .dz-default>strong{grid-column:2;font-size:12.5px;line-height:1.4}
.dropzone.compact .dz-default>span{grid-column:2;font-size:11px;line-height:1.5}
.dropzone.compact .dz-types{display:none}

/* ---- 拖拽上传 ---- */
.dropzone{
  border:1.5px dashed #cfdcf0;border-radius:14px;background:#fbfdff;
  display:flex;flex-direction:column;align-items:center;justify-content:center;gap:7px;
  padding:18px;text-align:center;min-height:148px;
  transition:border-color .15s,background .15s;cursor:pointer;position:relative;
}
.dropzone:hover,.dropzone.drag{border-color:#9dbcf0;background:#f4f9ff}
.dropzone .dz-ico{width:42px;height:42px;border-radius:12px;background:#eef4ff;color:var(--brand);display:grid;place-items:center}
.dropzone .dz-ico svg{width:20px;height:20px}
.dropzone strong{font-size:13px;color:#273244;font-weight:600}
.dropzone span{font-size:11.5px;color:#8b9ab2;line-height:1.55}
.dropzone.success{border-style:solid;border-color:#bfe3c9;background:#f7fdf9;cursor:default}
.dropzone.fail{border-style:solid;border-color:#f3c4c6;background:#fff7f7;cursor:default}
.dz-file{display:flex;align-items:center;gap:10px;width:100%;text-align:left}
.dz-file .f-thumb{
  width:40px;height:40px;border-radius:10px;background:#eef4ff;color:var(--brand);
  display:grid;place-items:center;flex-shrink:0;
}
.dz-file .f-thumb svg{width:19px;height:19px}
.dz-file .f-meta{min-width:0;flex:1}
.dz-file .f-name{font-size:12.5px;font-weight:600;color:#1f2a44;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.dz-file .f-rec{font-size:11px;color:#1e8e4a;margin-top:2px}
.dz-file .f-rec.err{color:#d92d20}
.dz-clear{border:0;background:transparent;color:#b3bccc;padding:5px;line-height:1;border-radius:6px;display:grid;place-items:center}
.dz-clear svg{width:14px;height:14px}
.dz-clear:hover{color:#d92d20;background:#fdf2f2}
.dz-types{display:flex;gap:6px;margin-top:2px;flex-wrap:wrap;justify-content:center}
.dz-types em{font-style:normal;font-size:10px;color:#8fa0bd;border:1px solid #e2ebf7;border-radius:4px;padding:1px 5px}

/* ---- 模态矩阵 ---- */
.matrix-wrap{position:relative}
.matrix-legend{display:flex;gap:14px;align-items:center;margin-bottom:12px;font-size:11.5px;color:#8b9ab2;flex-wrap:wrap}
.matrix-legend .lg{display:flex;align-items:center;gap:5px}
.matrix-legend .mk{width:20px;height:20px;border-radius:6px;display:grid;place-items:center}
.matrix-legend .mk svg{width:12px;height:12px}
.mk.on{background:#f3f7ff;color:var(--brand);border:1px solid #d7e3f8}
.mk.sel{background:var(--brand);color:#fff;border:1px solid var(--brand)}
.mk.off{background:#fbfcfe;border:1px dashed #e7ecf5}
.modal-matrix{
  display:grid;grid-template-columns:76px repeat(4,minmax(0,1fr));gap:7px;
  max-width:640px;
}
.mm-corner,.mm-colh,.mm-rowh{font-size:11.5px;color:#8b9ab2;display:grid;place-items:center;font-weight:600}
.mm-colh{height:24px;letter-spacing:.04em}
.mm-rowh{justify-content:flex-end;padding-right:6px;font-weight:500;color:#667085}
.mm-cell{
  height:42px;border-radius:9px;display:grid;place-items:center;
  position:relative;user-select:none;
}
.mm-cell svg{width:15px;height:15px}
.mm-cell.available{
  background:#f3f7ff;border:1px solid #d7e3f8;color:var(--brand);cursor:pointer;
  transition:background .12s,border-color .12s,transform .12s,box-shadow .12s;
}
.mm-cell.available:hover{background:#e8f0ff;border-color:#a9c4ee;transform:translateY(-1px)}
.mm-cell.selected{
  background:linear-gradient(135deg,#2a68e0,#1f5fd4);border-color:var(--brand);color:#fff;
  box-shadow:0 6px 14px rgba(31,95,212,.28);
}
.mm-cell.locked{
  background:#f4f5f8;border:1px solid #e6e9f0;color:#b8bfcc;cursor:not-allowed;opacity:.75;
}
.mm-cell.blank{background:#fbfcfe;border:1px dashed #eef2f8;cursor:default}
.mm-cell.dim{opacity:.32;pointer-events:none}

/* ---- 通用按钮 ---- */
.btn-primary{
  height:38px;padding:0 24px;border:0;border-radius:10px;
  background:var(--brand);color:#fff;font-size:13.5px;font-weight:600;
  display:inline-flex;align-items:center;justify-content:center;gap:7px;
  box-shadow:0 6px 16px rgba(31,95,212,.24);transition:background .15s,box-shadow .15s,transform .15s;
}
.btn-primary:hover{background:var(--brand-deep);box-shadow:0 8px 20px rgba(31,95,212,.3)}
.btn-ghost{height:38px;padding:0 18px;border-radius:10px;border:1px solid #d7e0f0;background:#fff;color:#475467;font-size:13px;display:inline-flex;align-items:center;justify-content:center;gap:6px;transition:border-color .15s,color .15s}
.btn-ghost:hover{border-color:var(--brand);color:var(--brand)}
.spinner{
  width:14px;height:14px;border:2px solid rgba(255,255,255,.4);border-top-color:#fff;
  border-radius:50%;animation:spin .7s linear infinite;
}
@keyframes spin{to{transform:rotate(360deg)}}

/* ---- 浮层 ---- */
.modal-mask{
  position:fixed;inset:0;background:rgba(16,32,63,.46);z-index:100;
  display:none;align-items:center;justify-content:center;padding:22px;
  backdrop-filter:blur(2px);
}
.modal-mask.show{display:flex}
.modal-box{
  width:540px;max-width:100%;background:#fff;border-radius:20px;
  box-shadow:0 30px 80px rgba(15,32,70,.32);overflow:hidden;
  animation:pop .2s ease;
}
@keyframes pop{from{transform:translateY(8px) scale(.985);opacity:0}to{transform:none;opacity:1}}
.modal-head{padding:22px 24px 0;display:flex;align-items:flex-start;justify-content:space-between;gap:14px}
.modal-head h3{font-size:17px;font-weight:700;color:#1b2a44;display:flex;align-items:center;gap:9px;letter-spacing:.01em;line-height:1.35}
.mh-ico{width:34px;height:34px;border-radius:10px;background:#eef4ff;color:var(--brand);display:grid;place-items:center;flex-shrink:0}
.mh-ico svg{width:18px;height:18px}
.modal-close{
  width:32px;height:32px;border:0;background:#f4f7fc;color:#7f8ea8;border-radius:9px;
  display:grid;place-items:center;flex-shrink:0;transition:background .15s,color .15s;
}
.modal-close svg{width:15px;height:15px}
.modal-close:hover{background:#e9eef7;color:#1b2a44}
.modal-body{padding:14px 24px 6px}
.modal-body .mb-tip{font-size:12.5px;color:var(--text-3);margin-bottom:10px;line-height:1.6}
.modal-body .mb-tip b{color:#1b2a44;font-weight:600}
.clarify-item{
  width:100%;display:flex;align-items:center;gap:10px;text-align:left;
  border:1px solid #e7eef9;border-radius:12px;background:#fff;
  padding:12px 14px;margin-bottom:8px;font-size:13.5px;color:#273244;
  transition:border-color .15s,background .15s,color .15s,transform .15s;
}
.clarify-item::after{content:"→";margin-left:auto;color:#c3cedd;font-size:14px;transition:color .15s,transform .15s}
.clarify-item:hover{border-color:#b9cef4;background:#f7faff;color:var(--brand);transform:translateX(2px)}
.clarify-item:hover::after{color:var(--brand);transform:translateX(2px)}
.clarify-item .ci-q{flex:1}
.clarify-scenes{display:flex;gap:8px;margin:6px 0 4px;flex-wrap:wrap}
.modal-foot{
  padding:14px 24px 20px;display:flex;align-items:center;justify-content:space-between;
  gap:12px;margin-top:8px;border-top:1px solid #f0f4fb;flex-wrap:wrap;
}
.modal-foot a{font-size:12.5px;color:var(--text-3);text-decoration:underline;text-underline-offset:3px}
.modal-foot a:hover{color:var(--brand)}
.modal-once{font-size:11px;color:#b3bccc;line-height:1.5}
.scene-tag{
  border:1px solid #d7e3f8;background:#f7faff;color:#3b5277;
  border-radius:999px;padding:8px 16px;font-size:12.5px;position:relative;
  display:inline-flex;align-items:center;gap:6px;transition:border-color .15s,background .15s,color .15s;
}
.scene-tag:hover{border-color:#a9c4ee;color:var(--brand);background:#eef4ff}
.scene-tag.active{background:var(--brand);border-color:var(--brand);color:#fff;font-weight:600}


/* ---- 全局消息 ---- */
.toast{
  position:fixed;top:64px;left:50%;transform:translateX(-50%) translateY(-12px);
  z-index:200;background:#16233d;color:#fff;font-size:13px;
  padding:10px 18px;border-radius:10px;box-shadow:0 12px 30px rgba(20,32,70,.28);
  opacity:0;pointer-events:none;transition:all .2s;display:flex;align-items:center;gap:7px;
}
.toast.show{opacity:1;transform:translateX(-50%) translateY(0)}
.toast.err{background:#b42318}
.toast.ok{background:#1e8e4a}

/* ---- 研发说明入口：Hero 头部卡片右上角（与标题同一行、右对齐） ---- */
.hero .dev-entry{position:absolute;top:24px;right:28px;z-index:3}
@media (max-width:820px){
  .hero .dev-entry{top:16px;right:16px;height:28px;font-size:11.5px;padding:0 11px 0 4px}
  .hero .dev-entry .de-q{width:18px;height:18px;line-height:18px;font-size:11px}
}

/* ---- 响应式 ---- */
@media (max-width:1180px){
  .page{padding:24px 26px 60px}
  .hero{padding:46px 30px 34px}
  .modal-matrix{max-width:100%}
}
@media (max-width:820px){
  .page{padding:18px 16px 48px;gap:18px}
  .hero{border-radius:24px;padding:36px 20px 30px}
  .hero h1{font-size:34px}
  .hero-sub{font-size:14.5px}
  .searchbox{height:68px;border-radius:18px;padding:0 8px 0 16px;margin-top:26px}
  #queryInput{font-size:16px}
  .btn-search{height:50px;padding:0 20px;font-size:14px;border-radius:14px}
  .search-meta{flex-direction:column;align-items:stretch;gap:10px}
  .scene-pick{width:100%}
  .scene-select{flex:1;min-width:0}
  .search-aux{flex-direction:column;align-items:flex-start;gap:6px}
  .search-aux .aux-right{width:100%;justify-content:space-between}
  .dropzone.compact{padding:11px 12px}
  .modal-matrix{grid-template-columns:64px repeat(4,minmax(0,1fr));gap:5px}
  .mm-cell{height:38px}
  .modal-box{border-radius:16px}
  .modal-head,.modal-body,.modal-foot{padding-left:18px;padding-right:18px}
  .clarify-scenes{flex-wrap:wrap}
}
@media (prefers-reduced-motion:reduce){
  *,*::before,*::after{animation-duration:.01ms !important;animation-iteration-count:1 !important;transition-duration:.01ms !important;scroll-behavior:auto !important}
}
</style>
