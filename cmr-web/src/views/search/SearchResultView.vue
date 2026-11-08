<template>
  <div class="p2-root">
    <div class="main">
      <div class="content">
        <section class="module-intro">
          <div>
            <h2>结果页</h2>
            <p>承接检索任务呈现结果集：查看结果集编号与条数，按模态分组、按相似度降序浏览结果卡片；空结果时给出归因与处置建议。</p>
          </div>
          <button class="dev-entry" type="button" title="研发说明" @click="openDev"><span class="de-q" aria-hidden="true">?</span>研发说明</button>
        </section>

        <section class="ctx-bar">
          <span class="ctx-q" title="查询描述">🔍 {{ contextQuery }}</span>
          <span class="ctx-item">模态对 <span class="pair-chip">{{ contextPair }}</span></span>
          <span class="ctx-item">任务 <b>{{ contextTask }}</b></span>
          <span class="ctx-spacer"></span>
          <router-link class="ctx-link" :to="taskRoute">查看任务上下文 →</router-link>
          <router-link class="ctx-link" :to="modifyRoute">修改检索 →</router-link>
          <router-link class="ctx-link" to="/operation">结果反馈归类 →</router-link>
        </section>

        <section class="rs-bar">
          <span class="rs-line">结果集 {{ resultSetId }} · {{ items.length }} 条</span>
          <span class="rs-state">
            <span class="rs-badge" :class="{ adopt: adoptStatus === 'adopted' }">{{ adoptLabel }}</span>
            <template v-if="adoptStatus === 'unadopted'">
              <button type="button" class="rs-act" @click="setAdopted(true)">采用</button>
              <button type="button" class="rs-act" @click="setAdopted(false)">废弃</button>
            </template>
            <button type="button" class="rs-act" @click="feedbackVisible = true">结果反馈</button>
          </span>
        </section>

        <div class="batch-bar" :class="{ show: selectedCount > 0 }">
          已选 <b>{{ selectedCount }}</b> 条
          <button type="button" @click="downloadSelected">批量回源下载</button>
          <button type="button" class="bb-clear" @click="clearSelection">清除选择</button>
        </div>

        <div class="dual-col" :class="{ collapsed: !filterOpen }">
          <aside class="side-filter">
            <div class="sf-head">
              <span class="sf-title">结果筛选</span>
              <button class="sf-collapse" type="button" title="收起筛选栏" @click="filterOpen = false">收起 ›</button>
            </div>
            <div class="filter-block">
              <label>质量等级区间 <span class="fb-val">{{ qualityLabel }}</span></label>
              <div class="dual-range">
                <div class="range-track"></div>
                <div class="range-fill" :style="rangeFillStyle"></div>
                <input v-model.number="qualityMin" type="range" min="0" max="3" step="1" aria-label="质量等级下限" @input="normalizeQualityRange">
                <input v-model.number="qualityMax" type="range" min="0" max="3" step="1" aria-label="质量等级上限" @input="normalizeQualityRange">
              </div>
              <div class="range-scale"><span>D</span><span>C</span><span>B</span><span>A</span></div>
            </div>
            <div class="filter-block">
              <label>相似度阈值 <span class="fb-val">≥ {{ similarityThreshold.toFixed(2) }}</span></label>
              <div class="sim-range"><input v-model.number="similarityThreshold" type="range" min="0.5" max="0.99" step="0.01" aria-label="相似度阈值"></div>
            </div>
            <div class="filter-block">
              <label>来源</label>
              <details class="filter-select">
                <summary><span>{{ sourceFilter }}</span></summary>
                <div class="filter-options">
                  <button v-for="source in sourceOptions" :key="source" type="button" @click="sourceFilter = source">{{ source }}</button>
                </div>
              </details>
            </div>
            <button class="sf-reset" type="button" @click="resetFilters">重置筛选</button>
          </aside>

          <div class="p2-main-col">
            <button class="side-expand" :class="{ show: !filterOpen }" type="button" @click="filterOpen = true">‹ 展开结果筛选</button>

            <div class="present-bar">
              <div class="gtabs">
                <button v-for="group in groups" :key="group.key" class="gtab" :class="{ active: activeGroup === group.key, zero: groupCount(group.key) === 0 }" type="button" @click="activeGroup = group.key">
                  {{ group.label }} <b>{{ groupCount(group.key) }}</b>
                </button>
              </div>
              <span class="pb-sep"></span>
              <div class="pb-ctrl"><span>排序</span><select class="pb-sort" aria-label="排序方式"><option value="sim">相似度降序</option></select></div>
              <div class="pb-ctrl">
                <span>对齐热力图</span>
                <button class="switch" :class="{ on: heatMap }" type="button" role="switch" :aria-checked="heatMap" aria-label="对齐热力图开关" @click="toggleHeat"></button>
              </div>
            </div>

            <div class="result-area">
              <div v-if="displayView === 'result'">
                <div class="list-head" style="margin-bottom:10px">
                  <span>结果条目（Top <b>{{ filteredItems.length }}</b> / 共 <b>{{ groupTotal }}</b> 条）</span>
                  <span></span>
                </div>
                <div class="rcard-grid">
                  <label
                    v-for="item in filteredItems"
                    :key="item.id"
                    class="rcard"
                    :class="{ checked: selectedIds.has(item.id), clicked: clickedIds.has(item.id) }"
                    @click="onCardClick(item, $event)"
                  >
                    <input type="checkbox" :checked="selectedIds.has(item.id)" aria-label="选择{{ item.itemId }}" @click.stop @change="toggleSelect(item, $event)">
                    <div class="rc-info">
                      <div class="rc-line1"><b>{{ item.itemId }}</b><span class="badge" :class="`q${qualityLevel(item.score)}`">质量 {{ qualityText(item.score) }}</span></div>
                      <div class="rc-src">来源：{{ itemSource(item) }} · {{ itemExtra(item) }}</div>
                      <div v-if="heatMap" class="rc-align">
                        对齐分 <i class="al-chip" :style="{ background: heatColor(item.score) }">语 {{ item.score.toFixed(2) }}</i>
                      </div>
                      <div v-if="expandedId === item.id" class="rc-media">
                        <MediaPreview :path="item.sourceFile" />
                      </div>
                    </div>
                    <div class="rc-right">
                      <div class="rc-sim">{{ item.score.toFixed(2) }}<small>相似度</small></div>
                    </div>
                  </label>
                </div>
              </div>

              <div v-if="displayView === 'loading'">
                <div class="skel-grid">
                  <div v-for="n in 6" :key="n" class="skel-card">
                    <div class="skel-block sk-thumb"></div>
                    <div class="sk-lines"><div class="skel-block sk-line w60"></div><div class="skel-block sk-line w80"></div><div class="skel-block sk-line w40"></div></div>
                  </div>
                </div>
              </div>

              <div v-if="displayView === 'empty'">
                <div class="empty-wrap">
                  <div class="state-ico">🔍</div>
                  <h3>本次检索未召回结果</h3>
                  <p class="st-desc">query「{{ contextQuery }}」在已接入检索库中未命中，按可疑度给出三条处置路径：</p>
                  <div class="cause-grid">
                    <article class="cause-card">
                      <span class="cc-tag high">归因①</span><h4>过滤过严</h4>
                      <p>当前筛选条件（质量等级 + 相似度阈值）偏严，检索日志显示有候选条目被过滤规则排除。</p>
                      <div class="cc-act"><router-link class="btn-ghost ghost-link" :to="modifyRoute">放宽过滤，返回重新检索</router-link></div>
                    </article>
                    <article class="cause-card">
                      <span class="cc-tag mid">归因②</span><h4>语义偏离</h4>
                      <p>query 与库内高频语义向量距离偏大，可换用相似表达再次检索：</p>
                      <div class="simq-list">
                        <button v-for="q in similarQueries" :key="q" class="simq-item" type="button" @click="goSearch(q)">{{ q }}</button>
                      </div>
                    </article>
                    <article class="cause-card">
                      <span class="cc-tag low">归因③</span><h4>覆盖缺失</h4>
                      <p>已接入检索库无对应覆盖记录，属内容覆盖缺口，可登记需求由运营侧跟进补数。</p>
                      <div class="cc-act"><router-link class="btn-ghost ghost-link" to="/operation">需求登记 → 零结果专项</router-link></div>
                    </article>
                  </div>
                  <div class="zero-tools">
                    <div class="cm-acts">
                      <button class="cm-btn pri" type="button" @click="zeroCauseVisible = true">发起原因分析</button>
                      <button class="cm-btn" type="button" @click="zeroPlanVisible = true">登记处置方案</button>
                      <button class="cm-btn" type="button" @click="showToast('暂无空结果处置留痕')">处置跟踪</button>
                    </div>
                    <div class="cm-log"><div class="cm-empty">{{ zeroLog }}</div></div>
                  </div>
                </div>
              </div>

              <div v-if="displayView === 'error'">
                <div class="state-wrap">
                  <div class="state-ico" style="background:#fdecec">⚠️</div>
                  <h3>{{ errorMessage }}</h3>
                  <p class="st-desc">本次查询未生成可展示结果集。</p>
                  <span class="err-code">关联结果集 {{ resultSetId }}</span>
                  <div class="state-actions">
                    <button class="btn-primary" type="button" @click="load">重试检索</button>
                    <router-link class="btn-ghost ghost-link" to="/tasks">查看失败任务</router-link>
                  </div>
                </div>
              </div>

              <div v-if="displayView === 'edge'">
                <div class="state-wrap">
                  <div class="state-ico" style="background:#eef4ff">📐</div>
                  <h3>系统边界</h3>
                  <p class="st-desc">① 展示上限：结果区按 <b>Top50</b> 分页口径展示。<br>② 跨模态边界：无共享空间组合不提供检索。<br>③ 批量上限：单次批量操作 ≤ 200 条。<br>④ 回源边界：回源下载需源库授权。</p>
                  <div class="state-actions"><button class="btn-primary" type="button" @click="demoView = actualView">返回结果态</button></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="modal-mask" :class="{ show: feedbackVisible }" @click.self="feedbackVisible = false">
      <div class="modal-box" style="width:560px">
        <div class="modal-head"><h3>结果反馈</h3><button class="modal-close" type="button" @click="feedbackVisible = false">×</button></div>
        <div class="modal-body">
          <div class="mb-tip">反馈会进入结果运营页处理，用于修正策略、来源标注或补库。</div>
          <div class="cm-row"><label>反馈类型</label>
            <select v-model="feedbackForm.type">
              <option value="inaccurate">结果不准确</option>
              <option value="ranking">排序不合理</option>
              <option value="wrong_source">来源错误</option>
              <option value="zero_result">零结果</option>
            </select>
          </div>
          <div class="cm-row textarea"><label>说明</label><textarea v-model="feedbackForm.description" placeholder="补充说明（哪个条目、期望结果等）"></textarea></div>
        </div>
        <div class="modal-foot">
          <button class="btn-ghost" type="button" @click="feedbackVisible = false">取消</button>
          <button class="btn-primary" type="button" :disabled="feedbackSubmitting" @click="submitFeedback">{{ feedbackSubmitting ? '提交中…' : '提交反馈' }}</button>
        </div>
      </div>
    </div>

    <div class="toast" :class="{ show: toastVisible, ok: toastType === 'ok', err: toastType === 'err' }">{{ toastMessage }}</div>

    <div class="modal-mask" :class="{ show: zeroCauseVisible }" @click.self="zeroCauseVisible = false">
      <div class="modal-box" style="width:560px">
        <div class="modal-head"><h3>🔎 发起原因分析</h3><button class="modal-close" type="button" @click="zeroCauseVisible = false">×</button></div>
        <div class="modal-body">
          <div class="mb-tip">对本次零结果做归因复核：勾选分析维度 → 执行分析 → 输出结论与置信度。</div>
          <label v-for="option in causeOptions" :key="option" class="cm-ck"><input v-model="selectedCauseOptions" type="checkbox" :value="option">{{ option }}</label>
        </div>
        <div class="modal-foot">
          <span class="mf-note">分析结论仅用于处置决策，不改变结果集</span>
          <button class="btn-ghost" type="button" @click="zeroCauseVisible = false">关闭</button>
          <button class="btn-primary" type="button" @click="runCauseAnalysis">执行分析</button>
        </div>
      </div>
    </div>

    <div class="modal-mask" :class="{ show: zeroPlanVisible }" @click.self="zeroPlanVisible = false">
      <div class="modal-box" style="width:720px">
        <div class="modal-head"><h3>🧾 登记处置方案</h3><button class="modal-close" type="button" @click="zeroPlanVisible = false">×</button></div>
        <div class="modal-body">
          <div class="mb-tip">处置方案登记后进入「处置跟踪」，并同步零结果专项台账。</div>
          <div class="cm-row"><label>处置方式</label><select v-model="zeroPlan.way"><option>放宽过滤后重检</option><option>替换为相似 query 重检</option><option>需求登记（补数 / 扩库）</option><option>调整策略阈值</option><option>无处置（可接受零结果）</option></select></div>
          <div class="cm-row"><label>责任人</label><input v-model="zeroPlan.owner" type="text"></div>
          <div class="cm-row"><label>期望完成</label><input v-model="zeroPlan.due" type="date"></div>
          <div class="cm-row textarea"><label>方案说明</label><textarea v-model="zeroPlan.note" placeholder="如：先放宽相似度至 0.70 重检，仍为零结果则登记补数需求"></textarea></div>
        </div>
        <div class="modal-foot">
          <button class="btn-ghost" type="button" @click="zeroPlanVisible = false">取消</button>
          <button class="btn-primary" type="button" @click="saveZeroPlan">保存方案</button>
        </div>
      </div>
    </div>

    <div class="demo-float">
      <span class="df-tag">原型演示</span>
      <button v-for="state in demoStates" :key="state.key" class="demo-btn" :class="{ active: demoView === state.key }" type="button" @click="setDemo(state.key)">{{ state.label }}</button>
    </div>

    <div class="dev-mask" :class="{ open: devOpen }" @click="closeDev"></div>
<div class="dev-drawer" :class="{ open: devOpen }" role="dialog" aria-modal="true" aria-label="研发需求说明">
  <div class="dev-head">
    <h3>研发需求说明</h3>
    <span class="dh-tag">给研发</span>
    <button type="button" class="dev-close" id="devClose" title="关闭（Esc）" aria-label="关闭" @click="closeDev">×</button>
  </div>
  <div class="dev-body">
<div class="dev-card" id="dc-ov" style="scroll-margin-top:12px"><div class="dc-head"><span class="dc-id" style="background:#1f5fd4">总览</span><span class="dc-name">页面定位 / 闭环 / 模块清单 / 编号与演示性质</span></div><div class="dc-body"><div class="ov-grid"><div class="dev-ov" style="margin:0;border:none;padding:6px 4px"><h4>本页定位【设计】</h4><p>检索结果的浏览与筛选现场（流B 主战场）；反馈归类进入流C 策略闭环。</p></div><div class="dev-ov" style="margin:0;border:none;padding:6px 4px"><h4>平台全景闭环【研发】</h4><p><a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a> 发起检索 → <a class="dev-xref" href="P6_任务管理_v5.0_20261025.html">P6</a> 任务调度 → 本页浏览/筛选 → <a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a> 结果运营（精选/反馈）→ <a class="dev-xref" href="P5_策略管理_v5.0_20261025.html">P5</a> 策略调整 → <a class="dev-xref" href="P8_评测_v5.0_20261025.html">P8</a> 评测验证 → 下次检索生效。</p></div><div class="dev-ov" style="margin:0;border:none;padding:6px 4px"><h4>三级模块清单【设计】</h4><p>主挂×1：空结果归因处置（F7）；联动×3：批量操作（F3）、D4 展示方案（F5）、反馈归类入口（F8，登记归类在 P7）。</p></div><div class="dev-ov" style="margin:0;border:none;padding:6px 4px"><h4>编号规范与演示性质【研发】</h4><p>业务编号按《平台相关ID生成规范-1026》32.1：任务 TASK / 库 KB / 策略 STG / 结果集 RST / 条目 ITM（等宽可复制）。<br>本页为页内模拟，不请求真实引擎；召回/相似度均为示例数据，不构成交付承诺。</p></div></div></div></div>
<div class="dev-card" id="dc-f1"><div class="dc-head"><span class="dc-id">F1</span><span class="dc-name">检索上下文条</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">展示本次检索的查询对象、模态对、任务号，提供「查看任务上下文」「修改检索」两个跳转。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v">常驻；来自 <a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a> 检索发起（携 query/模态对/场景）。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>query（上游带入）、模态对、任务号（TASK 业务编号，等宽可复制）</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v">仅展示不可编辑；「查看任务上下文」跳 <a class="dev-xref" href="P3_任务详情_v5.0_20261025.html">P3</a>（携 ?task=），「修改检索」回 <a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a>（携 ?q= 回填）。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>两个跳转：P3?task= / P1?q=</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v">只读展示，无提交无异步，本点不涉及异常。</div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">静态展示。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">上游=<a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a>；下游=<a class="dev-xref" href="P3_任务详情_v5.0_20261025.html">P3</a> / <a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a>。</div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">query/模态对/任务号正确；两个跳转参数正确。</div></div></div></div>
<div class="dev-card" id="dc-f3"><div class="dc-head"><span class="dc-id">F3</span><span class="dc-name">批量操作</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">对勾选条目批量回源下载。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v">勾选 ≥1 条时显示批量条。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>勾选的条目集合（单次 ≤ 200 条）</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v">批量回源下载＝两层权限校验。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>已选计数、批量条显隐、toast</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>guest 无下载权限</li><li>超 200 条自动截断并提示</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">隐藏（0 条）/ 显示（≥1 条）。</div></div><div class="dc-row "><div class="dc-k">上下游依赖</div><div class="dc-v">下游无跳转（下载在本页完成）。</div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">计数正确；权限拦截正确。</div></div></div></div>
<div class="dev-card" id="dc-f4"><div class="dc-head"><span class="dc-id">F4</span><span class="dc-name">结果筛选</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">按质量等级区间、相似度阈值、来源过滤结果。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v">侧栏常驻，可收起。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>质量等级区间 D~A（双柄）、相似度阈值 ≥0.50、来源下拉</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v">双柄质量区间、相似度阈值滑块、来源下拉；实时联动渲染结果卡片。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>过滤后的结果卡片（F6）</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>过滤后无结果触发空态归因（F7）</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">默认全部；收起/展开。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">本页内联动 F6；质量等级继承自 <a class="dev-xref" href="P4_库管理_v5.2_20261026.html">P4</a> 接入评定。</div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">三筛选联动正确；重置恢复默认。</div></div></div></div>
<div class="dev-card" id="dc-f5"><div class="dc-head"><span class="dc-id">F5</span><span class="dc-name">展示方案（联动 D4）</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">按模态分组、排序、对齐热力图开关展示结果。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v">展示方案条常驻；分组/排序/热力图默认在 <a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a> 配置。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>分组 Tab（全部/图像/视频/文本/音频）、排序（相似度降序）、热力图开关</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v">分组=按模态过滤；排序=相似度降序；热力图开关默认关，开启显示文/视对齐分并按蓝→红着色。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>结果卡片重排 / 对齐分着色（F6）</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>文本分组 0 条置灰</li><li>对齐分默认收起（开关 HTML 与 JS 初始态一致）</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">默认全部 + 相似度排序 + 热力图关。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">上游=<a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a> 展示方案配置；本页 F6。</div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">分组/相似度排序/热力图联动正确；开关初态一致。</div></div></div></div>
<div class="dev-card" id="dc-f6"><div class="dc-head"><span class="dc-id">F6</span><span class="dc-name">结果列表与条目</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">展示结果卡片（缩略图/来源/质量等级/相似度/对齐分），支持勾选、单条回源下载、单条反馈。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v">结果区常驻（结果态）。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>结果条目（ITM 编号、来源、模态、相似度、质量等级）</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v">卡片勾选→批量条（F3）；单条回源下载=两层权限+来源标注；单条反馈→弹窗（F8）。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>卡片列表 + 下载/反馈 toast</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>guest 无下载权限</li><li>结果区按 Top50 分页口径（演示为 Top8 预览）</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">结果态 / 加载态（骨架屏）。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">上游=F4/F5 过滤排序；下游=F3 批量 / F8 反馈。</div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">样例条目渲染正确；勾选/下载/反馈行为正确。</div></div></div></div>
<div class="dev-card" id="dc-f7"><div class="dc-head"><span class="dc-id">F7</span><span class="dc-name">空结果归因处置（主挂）</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">空结果时给归因三分类 + 三动作。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v">无结果时触发空态。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>query、目标库、归因分析结果</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v">归因三分类（过滤过严/语义偏离/覆盖缺失）；三动作=放宽过滤（回 P1 ?relax=1）/相似 query（回填 P1）/需求登记（回 P7 ?zero=）。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>三张归因卡 + 三个动作</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>空态只显示归因卡，不显示结果列表</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">空态。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">下游=<a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a>（放宽/相似）、<a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a>（需求登记）。</div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">三归因卡文案正确；三动作跳转携参正确。</div></div></div></div>
<div class="dev-card" id="dc-f8"><div class="dc-head"><span class="dc-id">F8</span><span class="dc-name">反馈归类入口（联动 P7）</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">把结果反馈送入 <a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a> 反馈面板；登记归类与分发在 P7 完成（主挂 D4，本页只做入口）。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v">检索上下文条右侧「结果反馈归类 →」链接（携结果集编号）。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>结果集编号 RST（随链接携带）</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v">点击跳 <a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a> 反馈面板；反馈表单统一在 P7 维护。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>跳转 P7?rs=…</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v">纯跳转，无提交；反馈类型必选等校验在 P7 执行。</div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">静态链接。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">上游=本页结果集；下游=<a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a> 反馈面板。</div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">链接携结果集编号正确；P7 侧可定位反馈面板。</div></div></div></div>
<div class="dev-card" id="dc-f9"><div class="dc-head"><span class="dc-id">F9</span><span class="dc-name">视图状态与系统边界</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">声明本页可呈现的视图状态（结果/加载/空结果/引擎错误/边界）与产品硬边界。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v">左下角「原型演示」浮条切换视图，仅用于评审覆盖；正式由接口状态驱动。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>视图选择（五态按钮）</li><li>登录态与库授权（正式决定可见范围，由 P4 授予）</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v">五态互斥显示；边界态列四条硬约束：Top50 分页 / 批量 ≤200 / 回源两层权限 / 无共享空间组合不渲染。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>当前视图状态 + 边界说明</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>错误态含系统错误码 ENG-TIMEOUT-504，可重试</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">五态切换即时生效。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">上游=P4 库管理（库授权）；视图状态由任务/结果集接口给出。</div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">五态切换正确；边界态四条齐全；错误态可重试回结果态。</div></div></div></div>
  </div>
</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adoptResults, clickResult, createFeedback, searchResults } from '../../api'
import { http } from '../../api/http'
import MediaPreview from '../../components/MediaPreview.vue'
import type { HistoryDetailVO, ResultItemVO } from '../../api/types'

const route = useRoute()
const router = useRouter()
const resultSetId = computed(() => String(route.params.resultSetId || ''))

const detail = ref<HistoryDetailVO | null>(null)
const items = ref<ResultItemVO[]>([])
const actualView = ref<'loading' | 'result' | 'empty' | 'error'>('loading')
const demoView = ref<'result' | 'loading' | 'empty' | 'error' | 'edge' | ''>('')
const errorMessage = ref('结果集加载失败')
const adoptStatus = ref('unadopted')
const heatMap = ref(false)
const filterOpen = ref(true)
const activeGroup = ref('all')
const qualityMin = ref(0)
const qualityMax = ref(3)
const similarityThreshold = ref(0.5)
const sourceFilter = ref('全部来源')
const selectedIds = ref(new Set<number>())
const clickedIds = ref(new Set<number>())
const expandedId = ref<number | null>(null)
const feedbackVisible = ref(false)
const feedbackSubmitting = ref(false)
const feedbackForm = reactive({ type: 'inaccurate', description: '' })

const zeroCauseVisible = ref(false)
const zeroPlanVisible = ref(false)
const selectedCauseOptions = ref(['过滤条件回放', '语义向量距离', '覆盖缺口'])
const causeOptions = ['过滤条件回放（质量等级 / 相似度阈值）', '语义向量距离分布', '覆盖缺口（库内是否有对应内容）', '索引新鲜度 / 同步延迟']
const zeroLog = ref('暂无空结果处置留痕')
const zeroPlan = reactive({ way: '放宽过滤后重检', owner: '', due: '', note: '' })

const devOpen = ref(false)
const toastVisible = ref(false)
const toastMessage = ref('')
const toastType = ref<'info' | 'ok' | 'err'>('info')
let toastTimer: ReturnType<typeof setTimeout> | undefined

const groups = [
  { key: 'all', label: '全部' },
  { key: 'image', label: '图像' },
  { key: 'video', label: '视频' },
  { key: 'text', label: '文本' },
  { key: 'audio', label: '音频' },
]
const demoStates = [
  { key: 'result', label: '结果态' },
  { key: 'loading', label: '加载态' },
  { key: 'empty', label: '空结果' },
  { key: 'error', label: '引擎错误' },
  { key: 'edge', label: '边界态' },
] as const

const displayView = computed(() => demoView.value || actualView.value)
const contextQuery = computed(() => detail.value?.history.queryText || '以文件语义检索')
const contextTask = computed(() => detail.value?.history.taskId || '未关联')
const contextPair = computed(() => detail.value?.history.queryModality ? `${modalityLabel(detail.value.history.queryModality)}检索` : '未记录')
const taskRoute = computed(() => `/tasks/${detail.value?.history.taskId || ''}`)
const modifyRoute = computed(() => `/search?q=${encodeURIComponent(detail.value?.history.queryText || '')}`)
const adoptLabel = computed(() => adoptStatus.value === 'adopted' ? '已采用' : adoptStatus.value === 'discarded' ? '已废弃' : '未采用')
const qualityLabel = computed(() => `${['D', 'C', 'B', 'A'][qualityMin.value]} ~ ${['D', 'C', 'B', 'A'][qualityMax.value]}${qualityMin.value === 0 && qualityMax.value === 3 ? '（全部）' : ''}`)
const rangeFillStyle = computed(() => ({ left: `${qualityMin.value / 3 * 100}%`, width: `${(qualityMax.value - qualityMin.value) / 3 * 100}%` }))
const selectedCount = computed(() => selectedIds.value.size)
const sourceOptions = computed(() => ['全部来源', ...new Set(items.value.map(item => itemSource(item)))])
const groupTotal = computed(() => activeGroup.value === 'all' ? items.value.length : groupCount(activeGroup.value))
const similarQueries = computed(() => [`${contextQuery.value} 高清`, `${contextQuery.value} 位置示意`, contextQuery.value])

const filteredItems = computed(() => items.value
  .filter(item => activeGroup.value === 'all' || item.modality === activeGroup.value)
  .filter(item => {
    const quality = qualityLevel(item.score)
    return quality >= qualityMin.value && quality <= qualityMax.value
  })
  .filter(item => item.score >= similarityThreshold.value)
  .filter(item => sourceFilter.value === '全部来源' || itemSource(item) === sourceFilter.value)
  .sort((a, b) => b.score - a.score))

onMounted(load)
onBeforeUnmount(() => clearTimeout(toastTimer))

async function load() {
  actualView.value = 'loading'
  try {
    const data = await searchResults(resultSetId.value)
    detail.value = data
    items.value = data.items || []
    adoptStatus.value = data.history.adoptStatus || 'unadopted'
    actualView.value = items.value.length ? 'result' : 'empty'
  } catch (error) {
    errorMessage.value = (error as Error).message || '结果集加载失败'
    actualView.value = 'error'
  }
}

function groupCount(key: string) {
  return key === 'all' ? items.value.length : items.value.filter(item => item.modality === key).length
}

function modalityLabel(modality: string) {
  return ({ text: '文本', image: '图像', audio: '音频', video: '视频' } as Record<string, string>)[modality] || modality
}

function qualityLevel(score: number) {
  if (score >= 0.9) return 3
  if (score >= 0.8) return 2
  if (score >= 0.7) return 1
  return 0
}

function qualityText(score: number) {
  return ['D', 'C', 'B', 'A'][qualityLevel(score)]
}

function itemSource(item: ResultItemVO) {
  const source = item.sourceFile || ''
  const parts = source.split(/[\\/]/).filter(Boolean)
  return parts.length > 1 ? parts[parts.length - 2] : source || '未记录来源'
}

function itemExtra(item: ResultItemVO) {
  return item.text?.slice(0, 42) || item.sourceFile || item.itemId
}

function heatColor(value: number) {
  const r = Math.round(65 + 164 * value)
  const g = Math.round(105 - 33 * value)
  const b = Math.round(246 - 169 * value)
  return `rgb(${r},${g},${b})`
}

function normalizeQualityRange() {
  if (qualityMin.value > qualityMax.value) {
    const temp = qualityMin.value
    qualityMin.value = qualityMax.value
    qualityMax.value = temp
  }
}

function resetFilters() {
  qualityMin.value = 0
  qualityMax.value = 3
  similarityThreshold.value = 0.5
  sourceFilter.value = '全部来源'
  activeGroup.value = 'all'
}

function toggleHeat() {
  heatMap.value = !heatMap.value
  showToast(heatMap.value ? '对齐热力图已开启（蓝→红，低分→高分）' : '对齐热力图已关闭')
}

function toggleSelect(item: ResultItemVO, event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  const next = new Set(selectedIds.value)
  if (checked) next.add(item.id)
  else next.delete(item.id)
  selectedIds.value = next
}

function clearSelection() {
  selectedIds.value = new Set<number>()
}

async function onCardClick(item: ResultItemVO, event: MouseEvent) {
  if ((event.target as HTMLElement).tagName === 'INPUT') return
  try {
    await clickResult(resultSetId.value, item.rank)
    const next = new Set(clickedIds.value)
    next.add(item.id)
    clickedIds.value = next
    expandedId.value = expandedId.value === item.id ? null : item.id
  } catch {
    // 点击埋点失败不影响浏览。
  }
}

async function setAdopted(adopted: boolean) {
  try {
    await adoptResults(resultSetId.value, adopted)
    adoptStatus.value = adopted ? 'adopted' : 'discarded'
    showToast(adopted ? '结果集已采用' : '结果集已废弃', 'ok')
  } catch (error) {
    showToast((error as Error).message, 'err')
  }
}

async function downloadSelected() {
  const selected = items.value.filter(item => selectedIds.value.has(item.id) && item.sourceFile)
  if (!selected.length) {
    showToast('所选条目没有可回源文件', 'err')
    return
  }
  try {
    for (const item of selected.slice(0, 200)) {
      const blob = await http.blob('file', { path: item.sourceFile, download: true })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = item.sourceFile?.split(/[\\/]/).pop() || `${item.itemId}.bin`
      anchor.click()
      URL.revokeObjectURL(url)
    }
    showToast('批量回源下载已开始', 'ok')
  } catch (error) {
    showToast((error as Error).message, 'err')
  }
}

function runCauseAnalysis() {
  const count = selectedCauseOptions.value.length
  zeroLog.value = count ? `已完成 ${count} 项分析：建议优先复核过滤阈值与库覆盖。` : '请至少选择一个分析维度'
  zeroCauseVisible.value = false
}

async function saveZeroPlan() {
  try {
    await createFeedback({
      type: 'zero_result',
      description: `[${zeroPlan.way}] ${zeroPlan.note || '零结果处置方案'}`,
      searchId: detail.value?.history.id,
    })
    zeroLog.value = `${new Date().toLocaleTimeString()} · ${zeroPlan.way} · 已登记`
    zeroPlanVisible.value = false
    showToast('处置方案已登记', 'ok')
  } catch (error) {
    showToast((error as Error).message, 'err')
  }
}

async function submitFeedback() {
  if (!detail.value?.history.id) {
    showToast('缺少关联检索历史', 'err')
    return
  }
  feedbackSubmitting.value = true
  try {
    await createFeedback({
      searchId: detail.value.history.id,
      type: feedbackForm.type,
      description: feedbackForm.description,
    })
    feedbackVisible.value = false
    feedbackForm.description = ''
    showToast('反馈已登记，可在结果运营页跟踪', 'ok')
  } catch (error) {
    showToast((error as Error).message, 'err')
  } finally {
    feedbackSubmitting.value = false
  }
}

function goSearch(query: string) {
  router.push({ path: '/search', query: { q: query } })
}

function setDemo(state: typeof demoStates[number]['key']) {
  demoView.value = state
  if (state === 'loading') {
    setTimeout(() => {
      if (demoView.value === 'loading') {
        demoView.value = actualView.value
        showToast('加载完成（模拟）', 'ok')
      }
    }, 1600)
  }
}

function showToast(message: string, type: 'info' | 'ok' | 'err' = 'info') {
  toastMessage.value = message
  toastType.value = type
  toastVisible.value = true
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toastVisible.value = false }, 2600)
}

function openDev() { devOpen.value = true }
function closeDev() { devOpen.value = false }
</script>

<style scoped>
.p2-main-col { min-width: 0; display: flex; flex-direction: column; gap: 12px; }
.rc-media { width: 100%; margin-top: 8px; border-top: 1px dashed #eef2f8; padding-top: 8px; }
.rc-media :deep(.media) { max-height: 220px; }

.ghost-link { display: inline-flex; align-items: center; text-decoration: none; color: #4169f6; border-color: #aebdf5; }
.cm-row.textarea { align-items: start; }
:root{
  /* 清爽冷白蓝 token（与 P1 定稿基准一致） */
  --workspace-bg:#f6f9fe;
  --module-bg:#f3f8ff;
  --content-card:#ffffff;
  --content-soft:#fbfdff;
  --content-line:#e6edf8;
  --content-line-strong:#d9e7f8;
  --system-blue:#4169f6;
  --system-blue-weak:#f1f5ff;
  --ink:#1f2937;
  --muted:#667085;
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

/* ============ 左侧全局导航（壳层，与 P1 一致） ============ */
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
.nav-item{
  display:flex;align-items:center;gap:9px;
  padding:9px 10px;margin-bottom:2px;
  border-radius:6px;color:#344054;font-size:13px;
  cursor:pointer;user-select:none;text-decoration:none;
}
.nav-item .ico{width:18px;height:18px;display:flex;align-items:center;justify-content:center;color:#8a96ad;flex-shrink:0}
.nav-item .ico svg{width:16px;height:16px;display:block}
.nav-item.active .ico{color:var(--system-blue)}
.nav-item:hover{background:var(--system-blue-weak);color:var(--system-blue)}
.nav-item.active{
  background:var(--system-blue-weak);color:var(--system-blue);font-weight:600;
  box-shadow:inset 2px 0 0 var(--system-blue);
}
.nav-divider{height:1px;background:var(--content-line);margin:8px 10px}

/* ============ 主区 ============ */
.main{flex:1;min-width:0;display:flex;flex-direction:column;height:100vh;overflow-y:auto}

@keyframes shimmer{to{background-position:-200% 0}}

.content{padding:20px 24px 32px;display:flex;flex-direction:column;gap:16px;max-width:1280px;width:100%;margin:0 auto}
.card{border:1px solid #e4edf8;border-radius:var(--radius);background:#fff}

/* 页头模块介绍（壳层模式） */
.module-intro{
  display:flex;align-items:flex-start;justify-content:space-between;gap:16px;flex-wrap:wrap;
  padding:18px 20px;
  border:1px solid #e4edf8;
  border-radius:10px;
  background:#fff;
}
.module-intro h2{font-size:20px;font-weight:700;color:#111827;margin-bottom:7px;display:flex;align-items:center;gap:10px}
.module-intro p{color:var(--muted);line-height:1.65;max-width:760px;font-size:13px}

.sec-head{display:flex;align-items:center;gap:8px}
.sec-head .sec-title{font-size:15px;font-weight:700;color:#1f2a44}
.sec-head .sec-sub{font-size:12px;color:#9aa6bd}
.sec-head::before{content:"";width:3px;height:14px;border-radius:2px;background:var(--system-blue)}

/* Z3 检索上下文条 */
.ctx-bar{
  padding:13px 18px;border:1px solid #e4edf8;border-radius:var(--radius);
  background:linear-gradient(180deg,#fff 0%,#f8fbff 100%);
  display:flex;align-items:center;gap:14px;flex-wrap:wrap;
}
.ctx-bar .ctx-q{
  font-size:15px;font-weight:700;color:#1f2a44;
  padding:5px 12px;background:var(--system-blue-weak);border:1px solid #d7e0ff;border-radius:6px;
  max-width:360px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;
}
.ctx-item{font-size:12px;color:var(--muted);display:flex;align-items:center;gap:5px}
.ctx-item b{color:#1f2a44;font-weight:600}
.pair-chip{
  font-size:11px;font-weight:600;color:var(--system-blue);
  background:var(--system-blue-weak);border:1px solid #d7e0ff;border-radius:4px;padding:1px 7px;
}
.ctx-spacer{flex:1}
.ctx-link{font-size:12.5px;color:var(--system-blue);text-decoration:none;border-bottom:1px dashed #aebdf5;padding-bottom:1px}
.ctx-link:hover{border-bottom-style:solid}

/* Z4 结果集概要条 */
.rs-bar{
  padding:12px 18px;border:1px solid #dfe8fb;border-radius:var(--radius);
  background:#fff;display:flex;align-items:center;
  box-shadow:var(--shadow-card);
}
.rs-line{font-size:14px;color:#1f2a44;font-variant-numeric:tabular-nums}
.rs-state{display:flex;align-items:center;gap:8px;margin-left:auto}
.rs-badge{font-size:11px;font-weight:600;padding:2px 9px;border-radius:10px;background:#f0f2f7;color:#8c98ad;white-space:nowrap}
.rs-badge.adopt{background:#e8f7ec;color:var(--success)}
.rs-act{height:26px;padding:0 12px;border-radius:4px;border:1px solid #d7e0f0;background:#fff;color:#475467;font-size:12px}
.rs-act:hover{border-color:var(--system-blue);color:var(--system-blue)}
.btn-primary{
  height:34px;padding:0 20px;border:0;border-radius:6px;
  background:var(--system-blue);color:#fff;font-size:13px;font-weight:600;
  display:inline-flex;align-items:center;gap:7px;text-decoration:none;
  box-shadow:0 3px 8px rgba(65,105,246,.25);transition:all .15s;
}
.btn-primary:hover{background:#335be8;box-shadow:0 4px 12px rgba(65,105,246,.32)}
.btn-primary:disabled{opacity:.5;cursor:not-allowed;box-shadow:none}
.btn-ghost{
  height:34px;padding:0 16px;border-radius:6px;border:1px solid #d7e0f0;
  background:#fff;color:#475467;font-size:13px;
}
.btn-ghost:hover{border-color:var(--system-blue);color:var(--system-blue)}
.btn-ghost:disabled{opacity:.5;cursor:not-allowed}

/* 批量操作条 */
.batch-bar{
  display:none;align-items:center;gap:12px;
  padding:9px 16px;border:1px solid #cdd9f5;border-radius:var(--radius);
  background:var(--system-blue-weak);font-size:12.5px;color:#344054;
}
.batch-bar.show{display:flex}
.batch-bar b{color:var(--system-blue)}
.batch-bar button{
  height:28px;padding:0 13px;border-radius:5px;border:1px solid #c3d2ec;background:#fff;
  color:#344054;font-size:12px;
}
.batch-bar button:hover{border-color:var(--system-blue);color:var(--system-blue)}
.batch-bar .bb-clear{margin-left:auto;border:0;background:transparent;color:#8c98ad}
.batch-bar .bb-clear:hover{color:var(--error)}

/* Z5 双栏布局：侧栏筛选 260px + 主内容 */
.dual-col{display:grid;grid-template-columns:260px minmax(0,1fr);gap:16px;align-items:start;position:relative}
.side-filter{
  border:1px solid #e4edf8;border-radius:var(--radius);background:#fff;
  padding:14px 16px;position:sticky;top:56px;
}
.side-filter .sf-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:6px}
.side-filter .sf-title{font-size:13px;font-weight:700;color:#1f2a44}
.sf-collapse{
  border:0;background:transparent;color:#9aa6bd;font-size:12px;padding:2px 4px;
}
.sf-collapse:hover{color:var(--system-blue)}
.side-filter.collapsed{display:none}
.dual-col.side-hidden{grid-template-columns:minmax(0,1fr)}
.side-expand{
  display:none;border:1px dashed #c3d2ec;border-radius:6px;background:#fff;
  color:#7a8493;font-size:12px;padding:4px 10px;margin-bottom:10px;
}
.side-expand:hover{color:var(--system-blue);border-color:var(--system-blue)}
.dual-col.side-hidden .side-expand{display:inline-block}

.filter-block{padding:11px 0;border-bottom:1px dashed #eef2f8}
.filter-block:last-of-type{border-bottom:0}
.filter-block label{display:block;font-size:12px;color:var(--muted);font-weight:600;margin-bottom:7px}
.filter-block .fb-val{font-size:12px;color:var(--system-blue);font-weight:600;float:right}

/* 双柄区间滑块 */
.dual-range{position:relative;height:26px;margin:2px 0 4px}
.dual-range input[type=range]{
  position:absolute;top:0;left:0;width:100%;height:26px;
  -webkit-appearance:none;appearance:none;background:transparent;pointer-events:none;margin:0;
}
.dual-range input[type=range]::-webkit-slider-runnable-track{height:4px;border-radius:2px;background:transparent}
.dual-range input[type=range]::-webkit-slider-thumb{
  -webkit-appearance:none;pointer-events:auto;
  width:14px;height:14px;border-radius:50%;margin-top:-5px;
  background:#fff;border:2px solid var(--system-blue);
  box-shadow:0 1px 4px rgba(65,105,246,.35);cursor:pointer;
}
.dual-range input[type=range]::-moz-range-track{height:4px;border-radius:2px;background:transparent}
.dual-range input[type=range]::-moz-range-thumb{
  pointer-events:auto;width:12px;height:12px;border-radius:50%;
  background:#fff;border:2px solid var(--system-blue);cursor:pointer;
}
.range-track{position:absolute;top:11px;left:0;right:0;height:4px;border-radius:2px;background:#e6edf8}
.range-fill{position:absolute;top:11px;height:4px;border-radius:2px;background:var(--system-blue)}
.range-scale{display:flex;justify-content:space-between;font-size:10px;color:#9aa6bd;margin-top:2px}

.sim-range input[type=range]{
  width:100%;-webkit-appearance:none;height:4px;border-radius:2px;background:#e6edf8;outline:none;
}
.sim-range input[type=range]::-webkit-slider-thumb{
  -webkit-appearance:none;width:14px;height:14px;border-radius:50%;
  background:#fff;border:2px solid var(--system-blue);cursor:pointer;box-shadow:0 1px 4px rgba(65,105,246,.35);
}
.sim-range input[type=range]::-moz-range-thumb{width:12px;height:12px;border-radius:50%;background:#fff;border:2px solid var(--system-blue);cursor:pointer}

/* 下拉（details 模式，与 P1 一致） */
.filter-select{position:relative;min-width:0}
.filter-select summary{
  height:32px;display:grid;grid-template-columns:minmax(0,1fr) 10px;align-items:center;gap:8px;
  padding:0 11px;border:1px solid #cfd8e8;border-radius:4px;color:#273244;background:#fff;
  font-size:12px;list-style:none;cursor:pointer;
}
.filter-select summary::-webkit-details-marker{display:none}
.filter-select summary::after{
  content:"";width:6px;height:6px;border-right:1.5px solid #667085;border-bottom:1.5px solid #667085;
  transform:rotate(45deg);justify-self:end;
}
.filter-select[open] summary{border-color:#8fa3ff;box-shadow:0 0 0 2px rgba(65,105,246,.14)}
.filter-select[open] summary::after{transform:rotate(-135deg)}
.filter-options{
  position:absolute;top:calc(100% + 4px);left:0;z-index:40;width:100%;padding:4px;
  border:1px solid #d8e1ef;border-radius:4px;background:#fff;box-shadow:var(--shadow-pop);
}
.filter-options button{
  width:100%;min-height:30px;border:0;border-radius:3px;padding:0 10px;color:#273244;
  background:transparent;text-align:left;font-size:12px;
}
.filter-options button:hover{color:var(--system-blue);background:var(--system-blue-weak)}
.sf-reset{
  width:100%;height:30px;margin-top:10px;border:1px solid #d7e0f0;border-radius:5px;
  background:#fff;color:#475467;font-size:12px;
}
.sf-reset:hover{border-color:var(--system-blue);color:var(--system-blue)}

/* 主栏：展示方案条 */
.present-bar{
  border:1px solid #e4edf8;border-radius:var(--radius);background:#fff;
  padding:12px 16px;display:flex;align-items:center;gap:14px;flex-wrap:wrap;
}
.gtabs{display:flex;gap:4px;flex-wrap:wrap}
.gtab{
  height:30px;padding:0 13px;border:1px solid transparent;border-radius:15px;
  background:transparent;color:#667085;font-size:12.5px;
}
.gtab:hover{background:var(--system-blue-weak);color:var(--system-blue)}
.gtab.active{background:var(--system-blue);color:#fff;font-weight:600}
.gtab.zero{color:#aab4c8}
.gtab.locked{
  opacity:.4;cursor:not-allowed;position:relative;
  background:#f4f5f8;color:#8c98ad;border:1px solid #e6e9f0;
}
.gtab.locked:hover{background:#f4f5f8;color:#8c98ad}
.gtab .lk-tip{
  position:absolute;bottom:calc(100% + 6px);left:50%;transform:translateX(-50%);
  background:#2b3245;color:#fff;font-size:11px;padding:5px 9px;border-radius:5px;
  white-space:nowrap;opacity:0;pointer-events:none;transition:opacity .12s;z-index:10;
}
.gtab.locked:hover .lk-tip{opacity:1}
.pb-sep{width:1px;height:20px;background:var(--content-line)}
.pb-ctrl{display:flex;align-items:center;gap:7px;font-size:12px;color:var(--muted)}
.pb-sort{
  height:30px;border:1px solid #cfd8e8;border-radius:5px;background:#fff;color:#344054;
  font-size:12px;padding:0 8px;outline:none;
}
.switch{
  position:relative;width:34px;height:18px;border-radius:9px;background:#dbe3f2;border:0;
  transition:background .15s;flex-shrink:0;
}
.switch.on{background:var(--system-blue)}
.switch::after{
  content:"";position:absolute;top:2px;left:2px;width:14px;height:14px;border-radius:50%;
  background:#fff;transition:left .15s;box-shadow:0 1px 3px rgba(0,0,0,.2);
}
.switch.on::after{left:18px}

/* 原型演示状态切换器 */
.demo-bar{
  display:flex;align-items:center;gap:8px;flex-wrap:wrap;
  padding:8px 14px;border:1px dashed #d5dfef;border-radius:var(--radius);
  background:var(--content-soft);
}
.demo-tag{
  font-size:10px;font-weight:700;color:#fff;background:#8c98ad;border-radius:3px;padding:2px 7px;letter-spacing:1px;
}
.demo-label{font-size:12px;color:#9aa6bd}
.demo-btn{
  height:26px;padding:0 12px;border:1px solid #d7e0f0;border-radius:13px;
  background:#fff;color:#667085;font-size:12px;
}
.demo-btn:hover{border-color:var(--system-blue);color:var(--system-blue)}
.demo-btn.active{background:var(--system-blue);border-color:var(--system-blue);color:#fff;font-weight:600}

/* 结果卡片列表 */
.result-area{display:flex;flex-direction:column;gap:12px}
.list-head{display:flex;align-items:center;justify-content:space-between;font-size:12px;color:var(--muted)}
.list-head b{color:#1f2a44}
.rcard-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}
.rcard{
  border:1px solid #e4edf8;border-radius:10px;background:#fff;
  display:flex;gap:12px;padding:12px;cursor:pointer;position:relative;
  transition:transform .15s,box-shadow .15s,border-color .15s;
}
.rcard:hover{transform:translateY(-2px);box-shadow:0 6px 18px rgba(31,58,122,.09);border-color:#c8d6f5}
.rcard.checked{border-color:var(--system-blue);background:#f8faff}
.rc-sel{
  -webkit-appearance:none;appearance:none;width:16px;height:16px;flex-shrink:0;margin-top:2px;
  border:1.5px solid #c3d0e8;border-radius:4px;background:#fff;cursor:pointer;position:relative;
}
.rc-sel:checked{background:var(--system-blue);border-color:var(--system-blue)}
.rc-sel:checked::after{
  content:"✓";position:absolute;inset:0;display:grid;place-items:center;
  color:#fff;font-size:11px;font-weight:700;
}
.rc-thumb{
  width:110px;height:62px;border-radius:7px;flex-shrink:0;
  background:linear-gradient(135deg,#eaf0ff,#f6f9ff);border:1px solid #e0e9f8;
  display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2px;
  color:var(--system-blue);font-size:20px;
}
.rc-thumb span{font-size:10px;color:#8fa0bd;max-width:96px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.rc-info{min-width:0;flex:1;display:flex;flex-direction:column;gap:5px}
.rc-line1{display:flex;align-items:center;gap:7px;flex-wrap:wrap}
.rc-line1 b{font-size:13.5px;color:#1f2a44}
.rc-line1 .badge{font-size:10.5px;padding:1px 7px;border-radius:9px;font-weight:600}
.badge.mod-img{background:#eef3ff;color:var(--system-blue)}
.badge.mod-video{background:#f0ebff;color:#7a5fd3}
.badge.mod-audio{background:#e6f7f1;color:#0f9d6e}
.badge.qA{background:#e8f7ec;color:var(--success)}
.badge.qB{background:#fff8e1;color:#b37514}
.badge.qC{background:#fdf0e6;color:#d2701e}
.rc-src{font-size:11.5px;color:var(--muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.rc-align{display:flex;align-items:center;gap:6px;font-size:11px;color:#9aa6bd;flex-wrap:wrap}
.al-chip{
  font-size:11px;font-weight:600;border-radius:4px;padding:1px 7px;color:#fff;
  font-variant-numeric:tabular-nums;
}
.al-chip.off{background:#eef2f8;color:#667085}
.rc-right{display:flex;flex-direction:column;align-items:flex-end;justify-content:space-between;flex-shrink:0}
.rc-sim{font-size:19px;font-weight:700;color:var(--system-blue);font-variant-numeric:tabular-nums;line-height:1}
.rc-sim small{display:block;font-size:10px;color:#9aa6bd;font-weight:400;text-align:center;margin-top:2px}
.rc-ops{display:flex;gap:6px}
.rc-op{
  width:30px;height:28px;border:1px solid #d7e0f0;border-radius:6px;background:#fff;
  font-size:13px;color:#667085;display:grid;place-items:center;
}
.rc-op:hover{border-color:var(--system-blue);color:var(--system-blue);background:var(--system-blue-weak)}
.rc-op:disabled{opacity:.5;cursor:not-allowed}

/* 骨架屏（加载态） */
.skel-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}
.skel-card{border:1px solid #eef2f8;border-radius:10px;background:#fff;padding:12px;display:flex;gap:12px}
.skel-block{border-radius:7px;background:linear-gradient(90deg,#eef2f8,#f7f9fd,#eef2f8);background-size:200% 100%;animation:shimmer 1.2s infinite}
.sk-thumb{width:110px;height:62px;flex-shrink:0}
.sk-lines{flex:1;display:flex;flex-direction:column;gap:8px;justify-content:center}
.sk-line{height:12px;border-radius:4px}
.sk-line.w60{width:60%}
.sk-line.w80{width:80%}
.sk-line.w40{width:40%}

/* 空结果态 */
.empty-wrap{
  border:1px solid #e4edf8;border-radius:var(--radius);background:#fff;padding:26px 24px;
  display:flex;flex-direction:column;align-items:center;
}
.state-ico{
  width:56px;height:56px;border-radius:14px;background:var(--system-blue-weak);
  display:grid;place-items:center;font-size:26px;margin-bottom:12px;
}
.empty-wrap h3{font-size:16px;color:#1f2a44;margin-bottom:6px}
.empty-wrap .st-desc{font-size:12.5px;color:var(--muted);text-align:center;max-width:560px;margin-bottom:18px}
.cause-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;width:100%}
.cause-card{
  border:1px solid #e0e9f7;border-radius:10px;padding:14px 14px 16px;
  background:linear-gradient(180deg,#fff 0%,#f9fbff 100%);
  display:flex;flex-direction:column;gap:8px;
}
.cause-card .cc-tag{
  align-self:flex-start;font-size:11px;font-weight:700;border-radius:3px;padding:2px 8px;
}
.cc-tag.high{background:#fdecec;color:var(--error)}
.cc-tag.mid{background:#fff4e0;color:#b37514}
.cc-tag.low{background:#eef2f8;color:#667085}
.cause-card h4{font-size:13.5px;color:#1f2a44}
.cause-card p{font-size:12px;color:var(--muted);line-height:1.55;flex:1}
.cause-card .cc-act{margin-top:4px}
.simq-list{display:flex;flex-direction:column;gap:5px}
.simq-item{
  text-align:left;border:1px solid var(--content-line-strong);border-radius:6px;background:#fff;
  padding:6px 9px;font-size:12px;color:#344054;
}
.simq-item:hover{border-color:var(--system-blue);color:var(--system-blue);background:var(--system-blue-weak)}
.simq-item em{font-style:normal;font-size:10.5px;color:#9aa6bd;margin-left:6px}

/* 错误态 / 权限态 */
.state-wrap{
  border:1px solid #e4edf8;border-radius:var(--radius);background:#fff;padding:34px 24px;
  display:flex;flex-direction:column;align-items:center;text-align:center;
}
.state-wrap h3{font-size:16px;color:#1f2a44;margin-bottom:6px}
.state-wrap .st-desc{font-size:12.5px;color:var(--muted);max-width:720px;margin-bottom:16px;line-height:1.6}
.state-wrap .err-code{
  font-size:12px;color:var(--error);background:#fdecec;border:1px solid #f3c4c6;border-radius:5px;
  padding:4px 12px;margin-bottom:16px;font-variant-numeric:tabular-nums;
}
.state-actions{display:flex;gap:10px;flex-wrap:wrap;justify-content:center}
.st-link{font-size:12.5px;color:var(--system-blue);text-decoration:none;border-bottom:1px dashed #aebdf5}
.st-link:hover{border-bottom-style:solid}

.spinner{
  width:14px;height:14px;border:2px solid rgba(255,255,255,.4);border-top-color:#fff;
  border-radius:50%;animation:spin .7s linear infinite;display:inline-block;
}
.spinner.blue{border-color:rgba(65,105,246,.3);border-top-color:var(--system-blue)}
@keyframes spin{to{transform:rotate(360deg)}}

/* Z6 结果反馈入口（跳 P7，本页不建表单） */
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
.modal-body .mb-tip{font-size:12px;color:var(--muted);margin-bottom:10px;line-height:1.6}
.modal-foot{padding:12px 20px 18px;display:flex;align-items:center;justify-content:flex-end;gap:10px;margin-top:6px;border-top:1px solid #f0f4fb}
.modal-foot .mf-note{font-size:11px;color:#b3bccc;margin-right:auto}

/* toast */
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
  .dual-col{grid-template-columns:1fr}
  .side-filter{position:static}
  .rcard-grid,.skel-grid{grid-template-columns:1fr}
  .cause-grid{grid-template-columns:1fr}
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
.dev-ov{background:#fff;border:1px solid #e4eaf3;border-radius:10px;padding:12px 14px;margin-bottom:12px}
.dev-ov h4{margin:0 0 5px;font-size:12.5px;color:var(--system-blue)}
.dev-ov p{margin:0;line-height:1.7;color:#475467;font-size:12px}
.ov-grid{display:grid;grid-template-columns:1fr;gap:10px}
.dev-card{background:#fff;border:1px solid #e4eaf3;border-radius:10px;margin-bottom:12px;overflow:hidden;scroll-margin-top:12px}
.dc-head{display:flex;align-items:center;gap:7px;padding:10px 14px;background:#fbfcfe;border-bottom:1px solid #eef2f8;flex-wrap:wrap}
.dc-id{font-size:11px;font-weight:700;color:#fff;background:#b54708;border-radius:4px;padding:1px 7px;flex:0 0 auto}
.dc-name{font-size:13px;font-weight:700;color:#1f2937}
.dc-body{padding:6px 14px 12px}
.dc-row{display:flex;gap:10px;padding:6px 0;border-bottom:1px dashed #f0f3f8;font-size:12px;line-height:1.7}
.dc-row:last-child{border-bottom:none}
.dc-k{flex:0 0 74px;font-weight:700;color:#344054}
.dc-v{flex:1;color:#475467}
.dc-v ul{margin:0;padding-left:16px}
.dc-v li{margin:2px 0}
.k-in .dc-k{color:#027a48}.k-out .dc-k{color:#1f5fd4}.k-err .dc-k{color:#c0362c}.k-dep .dc-k{color:#7a5af8}
.demo-limit{display:inline-block;font-size:10px;font-weight:600;color:#b54708;background:#fdf3e6;border:1px solid #f6d9b0;border-radius:4px;padding:0 5px;margin-left:6px;vertical-align:1px}
.dev-xref{color:#1f5fd4;font-weight:700;text-decoration:none;border-bottom:1px dashed #9dbcf5;padding:0 1px;white-space:nowrap}
.dev-xref:hover{background:#eaf1fe;border-bottom-style:solid}
/* 原型演示·状态切换浮条（左下角，与 ? 按钮分置） */
.demo-float{position:fixed;left:24px;bottom:24px;z-index:90;display:flex;align-items:center;gap:6px;background:#fff;border:1px solid #d5dfef;border-radius:8px;padding:6px 10px;box-shadow:0 2px 8px rgba(31,41,55,.08);flex-wrap:wrap}
.demo-float .df-tag{font-size:10px;font-weight:700;color:#8c98ad;border-radius:3px;padding:2px 6px;letter-spacing:1px;background:#f2f4f8}
.demo-float .demo-btn{height:24px;padding:0 10px;border:1px solid #d7e0f0;border-radius:12px;background:#fff;color:#667085;font-size:11px;cursor:pointer}
.demo-float .demo-btn:hover{border-color:var(--system-blue);color:var(--system-blue)}
.demo-float .demo-btn.active{background:var(--system-blue);border-color:var(--system-blue);color:#fff;font-weight:600}

/* ===== 空结果处置最小交互 ===== */
.cm-acts{display:flex;gap:7px;flex-wrap:wrap;align-items:center}
.cm-btn{height:26px;padding:0 9px;border:1px solid #d7e0f0;border-radius:5px;background:#fff;color:#475467;font-size:11.5px;display:inline-flex;align-items:center;gap:4px;white-space:nowrap;cursor:pointer}
.cm-btn:hover{border-color:var(--system-blue);color:var(--system-blue)}
.cm-btn.pri{background:var(--system-blue);border-color:var(--system-blue);color:#fff}
.cm-btn.pri:hover{background:#335be8;color:#fff}
.cm-tag{font-size:10.5px;border-radius:3px;padding:1px 6px;font-weight:600;background:#f1f5ff;color:var(--system-blue);white-space:nowrap}
.cm-tag.red{background:#fdecec;color:var(--error)}
.cm-tag.green{background:#e8f7ec;color:var(--success)}
.cm-tag.gray{background:#f2f4f8;color:#7a8493}
.cm-tag.warn{background:#fff4e0;color:#b37514}
.zero-tools{width:100%;margin-top:14px;border-top:1px dashed #e6edf8;padding-top:12px;text-align:left}
.zero-tools .zt{font-size:11.5px;color:#9aa6bd;margin-bottom:8px}
.cm-list{display:flex;flex-direction:column;gap:6px}
.cm-item{display:flex;align-items:center;gap:8px;font-size:12px;color:#344054;border:1px solid #eef2f8;border-radius:6px;padding:6px 9px;background:#fff;text-align:left}
.cm-item .k{font-weight:600;color:#1f2a44}
.cm-item .sp{margin-left:auto;display:flex;gap:6px}
.cm-item.flag{background:#fff7f7;border-color:#f3c4c6;color:#b42318}
.cm-log{margin-top:8px;display:flex;flex-direction:column;gap:5px}
.cm-log-row{display:flex;gap:7px;align-items:flex-start;font-size:11.5px;color:#475467;background:#fbfdff;border:1px dashed #e6edf8;border-radius:6px;padding:5px 8px;text-align:left}
.cm-log-row time{margin-left:auto;color:#9aa6bd;white-space:nowrap}
.cm-empty{font-size:11.5px;color:#9aa6bd;padding:3px 0}
.cm-panel{margin-top:8px;border:1px solid #eaf0f9;border-radius:8px;padding:10px 12px;background:var(--content-soft)}
.cm-panel-t{font-size:12px;font-weight:700;color:#1f2a44;margin-bottom:7px;display:flex;align-items:center;gap:7px;flex-wrap:wrap}
.cm-row{display:grid;grid-template-columns:88px minmax(0,1fr);gap:8px;align-items:center;margin-bottom:9px;text-align:left}
.cm-row>label{font-size:12px;color:#475467}
.cm-row select,.cm-row input[type=text],.cm-row input[type=date],.cm-row textarea{width:100%;min-height:30px;border:1px solid #cfd8e8;border-radius:6px;padding:4px 8px;font-size:12.5px;font-family:inherit;outline:none;background:#fff;color:#1f2a44}
.cm-row textarea{min-height:54px;resize:vertical}
.cm-ck{display:flex;align-items:center;gap:7px;font-size:12.5px;color:#344054;padding:5px 0;text-align:left}
.cm-ck input{width:14px;height:14px;accent-color:var(--system-blue);cursor:pointer}
</style>
