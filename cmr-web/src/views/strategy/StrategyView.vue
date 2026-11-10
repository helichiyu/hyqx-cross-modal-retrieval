<template>
  <div class="p5-root">
    <div class="main">
      <div class="content">
        <section class="module-intro">
          <div>
            <h2>策略管理</h2>
            <p>配置检索策略（向量 / 融合 / 重排），并管理其生效、变更与回滚。</p>
          </div>
          <button class="dev-entry" type="button" title="研发说明" @click="openDev"><span class="de-q" aria-hidden="true">?</span>研发说明</button>
        </section>

        <div class="tabbar main-tabbar" role="tablist" aria-label="策略管理主标签">
          <button v-for="tab in mainTabs" :key="tab.key" type="button" :class="{ active: activeTab === tab.key }" role="tab" @click="activeTab = tab.key">{{ tab.label }}</button>
        </div>

        <div v-if="activeTab === 'config'">
          <section class="panel" style="padding:14px 18px">
            <div class="panel-head">
              <div class="sec-head"><span class="p-title">策略列表</span></div>
              <div class="p-act"><button class="mini-btn primary" type="button" @click="openNewStrategy">＋ 新建策略</button></div>
            </div>
            <div class="p5-filter">
              <select v-model="strategySceneFilter" @change="reloadStrategies">
                <option value="">全部场景</option>
                <option v-for="item in scenes" :key="item.alias" :value="item.alias">{{ item.label }}</option>
              </select>
              <select v-model="strategyStatusFilter" @change="reloadStrategies">
                <option value="">全部状态</option>
                <option v-for="item in strategyStatusDict" :key="item.alias" :value="item.alias">{{ item.label }}</option>
              </select>
              <button class="mini-btn" type="button" @click="reloadStrategies">刷新</button>
            </div>
            <div class="mini-table" style="--tc:118px minmax(150px,1.15fr) 140px 56px 110px 118px 196px">
              <div class="mt-row head"><div>策略ID</div><div>名称</div><div>适用场景</div><div>版本</div><div>状态</div><div>更新时间</div><div>操作</div></div>
              <div v-if="strategyLoading" class="mt-row"><div class="table-empty">加载中…</div></div>
              <div v-else-if="!strategies.length" class="mt-row"><div class="table-empty">暂无策略</div></div>
              <div v-for="row in strategies" v-else :key="row.id" class="mt-row clickable" @click="openEditStrategy(row)">
                <div class="q mono">{{ row.bizNo }}</div>
                <div class="c name" :title="row.name">{{ row.name }}</div>
                <div class="c">{{ sceneLabel(row.scene) }}</div>
                <div class="num">{{ row.currentVersion ? `v${row.currentVersion}` : '—' }}</div>
                <span class="badge" :class="row.status === 'active' ? 'ok' : 'pend'">{{ statusLabel(row.status) }}</span>
                <div class="c">{{ row.updateTime || row.createTime || '—' }}</div>
                <div class="act-wrap"><button class="mini-btn primary" type="button" @click.stop="openEditStrategy(row)">编辑</button></div>
              </div>
            </div>
            <div class="pager-bar"><span>共 {{ strategies.length }} 条策略</span></div>
          </section>
        </div>

        <div v-if="activeTab === 'scene'">
          <section class="panel" style="padding:14px 18px">
            <div class="panel-head">
              <div class="sec-head"><span class="p-title">业务场景字典</span></div>
              <div class="p-act"><button class="mini-btn primary" type="button" @click="sceneFormOpen = !sceneFormOpen">＋ 新建场景</button></div>
            </div>
            <div v-if="sceneFormOpen" class="scene-form">
              <label class="fld">场景名<input v-model.trim="newScene.label" maxlength="60" placeholder="例如：合同审核"></label>
              <label class="fld">描述<input v-model.trim="newScene.remark" maxlength="120" placeholder="一句话说明该场景的检索用途"></label>
              <div class="scene-form-act">
                <button class="mini-btn primary" type="button" :disabled="sceneSubmitting" @click="createScene">{{ sceneSubmitting ? '创建中…' : '创建场景' }}</button>
                <button class="mini-btn" type="button" @click="cancelSceneForm">取消</button>
              </div>
            </div>
            <div class="mini-table" style="--tc:minmax(0,1.6fr) 110px 150px">
              <div class="mt-row head"><div>业务场景</div><div>关联策略数</div><div>操作</div></div>
              <div v-if="!scenes.length" class="mt-row"><div class="table-empty">暂无业务场景</div></div>
              <div v-for="scene in scenes" v-else :key="scene.alias" class="mt-row">
                <div class="c">{{ scene.label }}<span class="scene-alias mono">{{ scene.alias }}</span></div>
                <div class="num">{{ sceneStrategyCount(scene.alias) }}</div>
                <div class="act-wrap"><button class="mini-btn" type="button" @click="showSceneStrategies(scene)">查看关联策略</button></div>
              </div>
            </div>
          </section>
        </div>

        <div v-if="activeTab === 'ops'">
          <section class="panel">
            <div class="panel-head">
              <span class="pnum">◈</span>
              <span class="p-title">策略运维</span>
              <span class="p-sub ops-sub">改前看影响，改坏能回滚</span>
              <span class="p-act"><button class="mini-btn" type="button" @click="loadOpsDetail">↻ 刷新</button></span>
            </div>
            <div class="ops-pick">
              <label class="ed-pick">策略
                <select v-model.number="opsStrategyId" class="ed-select" @change="loadOpsDetail">
                  <option :value="0" disabled>请选择策略</option>
                  <option v-for="row in strategies" :key="row.id" :value="row.id">{{ row.bizNo }} · {{ row.name }}</option>
                </select>
              </label>
              <span v-if="opsDetail" class="ops-meta">{{ sceneLabel(opsDetail.strategy.scene) }} · 当前 v{{ opsDetail.current?.versionNo ?? 0 }} · {{ statusLabel(opsDetail.strategy.status) }}</span>
            </div>
            <div class="tabbar">
              <button type="button" :class="{ active: opsTab === 'eval' }" @click="opsTab = 'eval'">变更评估</button>
              <button type="button" :class="{ active: opsTab === 'rollback' }" @click="opsTab = 'rollback'">版本回滚</button>
            </div>

            <div v-if="opsTab === 'eval'">
              <div class="eval-state" :class="opsDetail?.draft ? 'ready' : 'idle'">{{ opsDetail?.draft ? '存在待发布草稿，请核对变更后发布或放弃。' : '当前没有待发布草稿。编辑策略并保存草稿后，可在此评估变更。' }}</div>
              <template v-if="opsDetail?.draft">
                <div class="sec-head" style="margin:10px 0 8px"><span class="sec-title p5-small-title">配置 diff</span><span class="sec-sub">当前版本 → 草稿</span></div>
                <div class="mini-table" style="--tc:minmax(0,.8fr) minmax(0,1fr) minmax(0,1fr)">
                  <div class="mt-row head"><div>配置项</div><div>上一版</div><div>草稿</div></div>
                  <div v-for="item in opsDiffRows" :key="item.name" class="mt-row"><div class="c">{{ item.name }}</div><div class="num">{{ item.from }}</div><div class="num">{{ item.to }}</div></div>
                </div>
                <div class="eval-act">
                  <button class="btn-primary" type="button" @click="publishOpsDraft">发布此草稿</button>
                  <button class="mini-btn" type="button" @click="discardOpsDraft">放弃草稿</button>
                </div>
              </template>
            </div>

            <div v-if="opsTab === 'rollback'">
              <div class="sec-head" style="margin:10px 0 8px"><span class="sec-title p5-small-title">版本历史</span><span class="head-act"><span v-if="consistencyResult" class="chk-line show" :class="{ fail: consistencyResult.passed === false }">{{ consistencyResult.passed === true ? '✓ 一致性检查通过' : '✕ 一致性检查未通过' }}</span><button class="mini-btn" type="button" :disabled="consistencyChecking" @click="checkConsistency">{{ consistencyChecking ? '检查中…' : '一致性检查' }}</button></span></div>
              <div class="mini-table" style="--tc:minmax(0,.7fr) minmax(0,1.4fr) 100px minmax(0,1fr) 110px">
                <div class="mt-row head"><div>版本</div><div>变更摘要</div><div>发布时间</div><div>状态</div><div>操作</div></div>
                <div v-if="!opsDetail?.versions.length" class="mt-row"><div class="table-empty">暂无历史版本</div></div>
                <div v-for="version in opsDetail?.versions || []" v-else :key="version.id" class="mt-row">
                  <div class="num">v{{ version.versionNo }}</div>
                  <div class="c">{{ version.source === 'rollback' ? '版本回滚' : '参数发布' }} · {{ versionSummary(version) }}</div>
                  <div class="c">{{ version.publishedTime || version.createTime }}</div>
                  <div><span class="badge ok">{{ version.versionNo === opsDetail?.current?.versionNo ? '当前生效' : '历史版本' }}</span></div>
                  <div class="act-wrap"><button class="mini-btn primary" type="button" :disabled="version.versionNo === opsDetail?.current?.versionNo" @click="requestRollback(version)">回滚</button></div>
                </div>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>

    <div class="toast" :class="{ show: toastVisible, ok: toastType === 'ok', err: toastType === 'err' }">{{ toastMessage }}</div>

    <!-- 策略编辑弹窗 -->
    <div class="modal-mask stg-mask" :class="{ show: editorVisible }" @click.self="editorVisible = false">
      <div class="stg-modal" role="dialog" aria-modal="true" aria-labelledby="stgTitle">
        <div class="stg-head"><span class="ed-tt" id="stgTitle">{{ editingId ? '编辑策略' : '新建策略' }}</span><span v-if="editorReadonlyPreset" class="tag-preset">系统预置</span><button class="stg-close" type="button" title="关闭" aria-label="关闭策略弹窗" @click="editorVisible = false">×</button></div>
        <div class="stg-body">
          <section class="ed-sec">
            <div class="ed-sec-head"><span class="p-title">基本信息</span></div>
            <div class="ed-form">
              <label class="fld">策略名称<input v-model.trim="editor.name" maxlength="80" placeholder="请输入策略名称"></label>
              <label class="fld">适用场景<select v-model="editor.scene"><option v-for="scene in scenes" :key="scene.alias" :value="scene.alias">{{ scene.label }}</option></select></label>
              <div class="fld ro">版本<b>{{ editorVersion }}</b></div>
              <div class="fld ro">状态<b>{{ editorStatus }}</b></div>
            </div>
          </section>
          <section class="ed-sec">
            <div class="ed-sec-head"><span class="p-title">向量参数</span></div>
            <div class="ed-op"><label class="ed-pick">统一向量空间<select class="ed-select" disabled><option v-for="model in modelChoices" :key="model" :value="model">{{ model }}</option></select></label><span class="ed-fixed">{{ modelHint }}</span></div>
          </section>
          <section class="ed-sec">
            <div class="ed-sec-head"><span class="p-title">融合参数</span></div>
            <div class="fuse-wrap">
              <div class="fslider"><span>文本</span><input v-model.number="editor.weights.text" type="range" min="0" max="100" step="1"><span class="wval"><input v-model.number="editor.weights.text" class="wnum" type="number" min="0" max="100" step="1" aria-label="文本权重百分比" @change="normalizeWeight('text')" @blur="normalizeWeight('text')">%</span></div>
              <div class="fslider"><span>图像</span><input v-model.number="editor.weights.image" type="range" min="0" max="100" step="1"><span class="wval"><input v-model.number="editor.weights.image" class="wnum" type="number" min="0" max="100" step="1" aria-label="图像权重百分比" @change="normalizeWeight('image')" @blur="normalizeWeight('image')">%</span></div>
              <div class="fslider"><span>视频</span><input v-model.number="editor.weights.video" type="range" min="0" max="100" step="1"><span class="wval"><input v-model.number="editor.weights.video" class="wnum" type="number" min="0" max="100" step="1" aria-label="视频权重百分比" @change="normalizeWeight('video')" @blur="normalizeWeight('video')">%</span></div>
              <div class="fslider"><span>音频</span><input v-model.number="editor.weights.audio" type="range" min="0" max="100" step="1"><span class="wval"><input v-model.number="editor.weights.audio" class="wnum" type="number" min="0" max="100" step="1" aria-label="音频权重百分比" @change="normalizeWeight('audio')" @blur="normalizeWeight('audio')">%</span></div>
            </div>
            <div class="sum-line" :class="{ invalid: editorWeightSum !== 100 }">权重合计 <b>{{ editorWeightSum }}%</b> {{ editorWeightSum === 100 ? '✓' : '✕ 必须为 100%' }}</div>
          </section>
          <section class="ed-sec">
            <div class="ed-sec-head"><span class="p-title">重排参数</span></div>
            <div class="ed-op ops-switch-line">
              <label class="ed-switch"><input v-model="editor.rerank" type="checkbox"><span>启用重排</span><b>{{ editor.rerank ? '开' : '关' }}</b></label>
              <span class="ed-fixed">{{ editor.rerank ? '开 = 对候选结果重新排序（更准，稍慢）' : '关 = 只按召回顺序返回（更快）' }}</span>
              <label class="ed-pick topk-pick">返回条数<input v-model.number="editor.topK" type="number" min="1" max="100"></label>
            </div>
          </section>
        </div>
        <div class="stg-foot">
          <span class="act-wrap"><button v-if="editingId && editorDetail?.draft" class="mini-btn" type="button" @click="requestDiscardEditorDraft">放弃草稿</button></span>
          <span class="sp"></span>
          <button class="btn-ghost" type="button" @click="editorVisible = false">取消</button>
          <button class="mini-btn" type="button" :disabled="editorSaving" @click="saveStrategy(false)">{{ editorSaving ? '保存中…' : '保存草稿' }}</button>
          <button class="btn-primary" type="button" :disabled="editorSaving" @click="saveStrategy(true)">{{ editorSaving ? '处理中…' : '保存并发布' }}</button>
        </div>
      </div>
    </div>

    <!-- 通用确认弹窗 -->
    <div class="modal-mask" :class="{ show: confirmVisible }" @click.self="confirmVisible = false">
      <div class="modal-box">
        <div class="modal-head"><h3>{{ confirmTitle }}</h3><button class="modal-close" type="button" @click="confirmVisible = false">×</button></div>
        <div class="modal-body"><div class="mb-tip">{{ confirmMessage }}</div></div>
        <div class="modal-foot"><span class="modal-note">操作将留痕</span><button class="mini-btn" type="button" @click="confirmVisible = false">取消</button><button class="mini-btn primary" type="button" @click="runConfirm">确定</button></div>
      </div>
    </div>

    <!-- 场景关联策略 -->
    <div class="modal-mask" :class="{ show: sceneStrategiesVisible }" @click.self="sceneStrategiesVisible = false">
      <div class="modal-box wide">
        <div class="modal-head"><h3>场景关联策略</h3><button class="modal-close" type="button" @click="sceneStrategiesVisible = false">×</button></div>
        <div class="modal-body">
          <div class="mb-tip">{{ selectedScene?.label }} · {{ relatedStrategies.length }} 条策略</div>
          <div class="mini-table" style="--tc:minmax(0,1fr) 100px">
            <div class="mt-row head"><div>策略</div><div>状态</div></div>
            <div v-for="row in relatedStrategies" :key="row.id" class="mt-row"><div class="c">{{ row.bizNo }} · {{ row.name }}</div><div>{{ statusLabel(row.status) }}</div></div>
          </div>
        </div>
        <div class="modal-foot"><span class="modal-note">仅查看</span><button class="mini-btn" type="button" @click="sceneStrategiesVisible = false">关闭</button></div>
      </div>
    </div>

    <div class="dev-mask" :class="{ open: devOpen }" @click="closeDev"></div>
<div class="dev-drawer" :class="{ open: devOpen }" role="dialog" aria-modal="true" aria-label="研发需求说明">
  <div class="dev-head">
    <h3>研发需求说明</h3>
    <span class="dh-tag">给研发</span>
    <button type="button" class="dev-close" id="devClose" title="关闭（Esc）" aria-label="关闭" @click="closeDev">×</button>
  </div>
  <div class="dev-body">
<div class="dev-ov" id="dc-note" style="background:#fffdf6;border-color:#f0e2c4"><h4 style="color:#b54708">页内说明（正文只留操作，说明收进此处）</h4><p>· 策略 = 向量 / 融合 / 重排三段参数：向量为白名单模型，融合为四模态权重（合计 100%），重排为两阶段开关。<br>· <b>保存并发布时三段参数一起生效</b>；融合合计校验只在弹窗内以行内提示呈现（≠100% 标红）。<br>· 每个参数区只留参数本身；生效入口＝弹窗底部「保存并发布」或变更评估「发布此草稿」。<br>· 向量模型下拉只列<b>产线白名单</b>内模型，白名单外向量禁入检索库（假跨模态风险防线）。<br>· 系统默认策略（兜底）作为<b>系统预置</b>行保留在策略列表：标注「系统预置」，仅管理员可编辑、其他角色只读引用；场景无专属策略时自动回落兜底。<br>· 分层结构：Tab1 策略配置 = 策略列表常驻（4 条业务演示策略 + 系统默认兜底）＋ 页面级大弹窗编辑（弹窗 ≈900px / 88vw · max-height 86vh · 内部滚动 · 底部操作区钉住，× / 取消 / 遮罩 / Esc 关闭后回到列表、列表不丢；仅 基本信息 + 向量 / 融合 / 重排 参数字段）；Tab2 业务场景字典 = 场景管理（场景列表 / 新建场景 / 查看关联策略，场景与策略的关联仍由策略「适用场景」维护）；Tab3 策略运维 = 先选一条策略（下拉，默认第一条）+ 2 个子 Tab（变更评估 / 版本回滚；变更评估有草稿才出 diff，无草稿显示「当前无待发布变更」），两块都针对这条策略，顶部一句业务价值「改前看影响，改坏能回滚」；新建 / 编辑入口在策略列表，底部「保存草稿」不生效、「保存并发布」下次检索生效。<br>· 正文只留标题 / 控件 / 状态 / 按钮，研发与逻辑说明全部收进本面板；策略状态收敛为 <b>草稿 / 编辑中 / 生效中</b> 三态；编号 STG 保留、版本只用 v1/v2/v3；系统预置行仅保留行尾小徽标。</p></div>
<div class="dev-ov" id="dc-opsnote" style="background:#f2fbf4;border-color:#cdeed6"><h4 style="color:#1e7a43">策略运维定位（保证改策略不翻车）</h4><p>· <b>定位</b>：先选一条策略（下拉，默认第一条），再看两块——①变更评估（前提＝该策略存在草稿 / 待发布版本：有草稿才对比上一版，展示配置 diff；无草稿显示「当前无待发布变更（已生效 vX）」并隐藏 diff）②版本回滚（发布后版本历史 + 一键回滚，回写版本 / 状态 / 配置并出核验回执）；顶部只留一句「改前看影响，改坏能回滚」。<br>· <b>变更评估闭环</b>：有草稿时 diff + 影响下方 1 行「发布此草稿 / 放弃草稿」——发布＝草稿转生效版（沿用草稿版本号）＋写版本历史＋清空待发布＋列表回「生效中」＋发布回执；放弃＝二次确认后丢弃草稿、状态回「当前无待发布变更（已生效 vX）」；状态条小字标注「草稿来自：编辑策略 → 保存草稿」。<br>· <b>场景绑定</b>：由策略编辑弹窗的「适用场景」字段维护，保存并发布后对该场景下次检索生效。</p></div>
<div class="dev-ov" id="dc-modalnote" style="background:#fffdf6;border-color:#f0e2c4"><h4 style="color:#b54708">新建 / 编辑弹窗口径（说明性文字统一收进此处）</h4><p>· 弹窗正文只保留「业务字段 + 控件 + 按钮 + 状态」，口径以本段为准。<br>· <b>底部按钮语义</b>：「保存草稿」＝写入策略表、状态停留「草稿 / 编辑中」，<b>不发布、不影响检索</b>；「保存并发布」＝发布到「适用场景」绑定场景，<b>该场景下次检索即生效</b>；「取消 / × / 遮罩 / Esc」＝关闭回列表，未保存的新建空行丢弃，列表不重绘、不丢。<br>· <b>基本信息</b>：策略名称（必填，保存 / 发布前校验「请填写策略名称」）+ 适用场景（发布生效的绑定键，场景字典在 Tab2 维护）+ 版本 / 状态（只读回显，状态三态：草稿 / 编辑中 / 生效中）。<br>· <b>向量参数</b>：下拉<b>只列产线白名单</b>内模型（来源对齐产线 R2），白名单外向量禁入检索库——白名单是假跨模态风险防线。<br>· <b>融合参数</b>：四模态（文本 / 图像 / 视频 / 音频）权重合计须为 <b>100%</b>；行内「权重合计 100% ✓ / （需调整至 100%）」是<b>校验显示</b>而非说明句，≠100% 时数字标红（.sum-line.over）。<br>· <b>重排参数</b>：一个开关即「启用重排」＝粗排召回 topN → 精排模型重排；关＝仅用粗排结果。<br>· <b>生效口径</b>：三段参数在底部一次性写入——「保存草稿」只存不生效，「保存并发布」与变更评估「发布此草稿」是两处生效入口。<br>· <b>只读与状态</b>：系统预置（兜底）策略仅管理员可编辑，其他角色整窗控件与「保存草稿 / 保存并发布」禁用（仅保留禁用态 tooltip）；弹窗头只挂状态徽标（草稿 / 编辑中 / 生效中）与「系统预置」小标。<br>· <b>演示口径</b>：演示值只存在于字段默认值（名称「作业场景检索策略」、权重 50/30/10/10、模型「图文对齐 · CLIP双塔 v3」），正式实现由接口回填。下拉项名里的「（白名单）」是模型枚举名（同时作为 data-model 存储值）。</p></div>
<div class="dev-card" id="dc-ov" style="scroll-margin-top:14px"><div class="dc-head"><span class="dc-id" style="background:#1f5fd4">总览</span><span class="dc-name">页面定位 / 闭环 / 模块清单 / 编号与演示性质</span></div><div class="dc-body"><div class="ov-grid"><div class="dev-ov" style="margin:0;border:none;padding:6px 4px"><h4>本页定位【设计】</h4><p>检索中心的「大脑」：三段式管理检索策略——①向量 ②融合 ③重排，每段只留参数本身（② 融合保留四模态权重滑块），加默认策略兜底；策略经评测（P8）验证后保存并发布，对 <a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a> 下次检索即生效。页面类型＝配置/编辑页（规范2.3），编辑弹窗底部主操作唯一＝保存并发布。</p></div><div class="dev-ov" style="margin:0;border:none;padding:6px 4px"><h4>平台全景闭环【研发】</h4><p><a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a> 结果运营反馈 / <a class="dev-xref" href="P8_评测_v5.0_20261025.html">P8</a> 评测建议 → 本页配三段参数（F5–F7）→ F9 保存并发布 → <a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a> 下次检索生效 → <a class="dev-xref" href="P6_任务管理_v5.0_20261025.html">P6</a> 调度 → <a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a> 结果 → 回到 <a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a>/<a class="dev-xref" href="P8_评测_v5.0_20261025.html">P8</a>；<a class="dev-xref" href="P4_库管理_v5.2_20261026.html">P4</a> 新库投产由库管理页自行校验，本页默认策略行直接编辑兜底参数（F8）。</p></div><div class="dev-ov" style="margin:0;border:none;padding:6px 4px"><h4>三级模块清单【设计】</h4><p>A 档×4：向量 / 融合 / 重排 / 默认策略；B 档×2：变更评估·版本回滚（先选一条策略；场景绑定并入策略编辑「适用场景」）。功能点 F1–F3、F5–F12 与之对应（运维两块聚合到 F11，权限归 F12）。</p></div><div class="dev-ov" style="margin:0;border:none;padding:6px 4px"><h4>编号规范与演示性质【研发】</h4><p>策略 ID 按《平台相关ID生成规范-1026》32.1：内部主键雪花 19 位不外暴；业务编号=STG+yyMMdd+6 位安全码（等宽可复制）；语义编码=小写下划线、发布不可改。版本号 v1/v2/v3 保留。<br>本页 <b>库/任务/策略编号已按 canonical 统一为 KB / TASK / STG；评测、反馈为历史前缀，正式实现按 canonical 归并为 EVL / FBK</b>。<br>所有校验结果均为页内写死演示，不请求真实引擎，不构成交付承诺。</p></div></div></div></div>
<div class="dev-card" id="dc-f1"><div class="dc-head"><span class="dc-id">F1</span><span class="dc-name">策略列表与编辑弹窗载入</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">策略的统一入口：列出全部策略，点行 / 「编辑」把该策略的三段参数（向量/融合/重排）载入<b>页面级大弹窗编辑</b>。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v"><ul><li>进入本页即展示列表（策略列表置于 Tab1 最上），无需前置操作</li><li>仅管理员可编辑/发布；访客整页只读并弹出无权限遮罩（见 F12 权限）</li></ul></div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>策略表 7 列：策略ID（业务编号）/名称/适用场景/版本/状态/更新时间/操作（「编辑」）</li><li>业务编号按《平台相关ID生成规范》32.1：STG+yyMMdd+6 位安全码，等宽字体展示；演示 4 条业务策略：作业 <span class='mono'>STG261025A3F9KX</span>、专业 <span class='mono'>STG261022P6L4MN</span>、票证 <span class='mono'>STG261020D8N3QR</span>、阈值收紧草稿 <span class='mono'>STG261024H5T6UW</span>，另有系统预置 <span class='mono'>STG261018K7M2PQ</span></li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v"><ul><li>点「编辑」或行任意处载入：弹出编辑弹窗并载入参数字段；系统默认策略为系统预置行（仅管理员可编辑，其他角色只读引用）</li><li>策略状态仅三态：草稿 / 编辑中 / 生效中</li><li>列表行由「新建策略」保存后产生</li></ul></div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>被选策略的配置快照载入编辑弹窗（F5–F7）</li><li>编辑弹窗头部显示「当前：名称 · 业务编号 · 版本」</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>系统预置策略仅管理员可编辑；其他角色只读引用</li><li>访客点编辑类按钮被无权限遮罩拦截</li><li>列表为空（暂无策略）时给「＋新建策略」主引导（正式态，演示为 4 条静态数据）</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v"><ul><li>默认=列表加载态（演示直接渲染）/载入成功 toast/只读态（其他角色/访客）/空态</li></ul></div></div><div class="dc-row "><div class="dc-k">上下游依赖</div><div class="dc-v"><ul><li>上游=策略配置库（正式由后端返回）</li><li>下游=F2 新建 / F5–F8 编辑校验发布；场景列与 F3 场景字典一致</li></ul></div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">4 条业务策略 + 系统预置策略业务编号均为 STG+6 位安全码、等宽显示；编辑业务策略与系统预置策略均可载入；行操作含「编辑」；上下文条编号与版本正确；其他角色只读。</div></div></div></div><div class="dev-card" id="dc-f2"><div class="dc-head"><span class="dc-id">F2</span><span class="dc-name">新建策略草稿</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">创建一条空白策略草稿：点「＋ 新建策略」弹出<b>页面级大弹窗（空白策略）</b>，配置后「保存草稿」在列表新增 STG 草稿行。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v"><ul><li>策略列表右上「＋ 新建策略」按钮；新建时默认适用场景「通用」，可在弹窗内切换；仅管理员可用</li></ul></div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v">点击；弹窗内填名称 / 选场景，其余取默认值（三段参数默认、融合权重 50/30/10/10）。</div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v"><ul><li>点击即弹出空白编辑弹窗（标题「新建策略」，状态「草稿」），默认：CLIP双塔 v3、融合权重 50/30/10/10、启用重排开，名称为空、版本 v1</li><li>保存草稿 → 关闭弹窗回列表、顶部新增草稿行（草稿，不影响检索）；保存并发布 → 关闭弹窗回列表、新增生效行（v1·生效中）并写适用场景</li><li>关闭弹窗丢弃未保存空草稿，列表不产生空行</li></ul></div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>草稿行/生效行出现在策略列表顶部并高亮；正式实现落库并返回新策略业务编号（STG+日期+安全码）与语义编码</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>未填名称时保存草稿/保存并发布被拦截并 toast</li><li>未发布前不改动任何适用场景</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">编辑弹窗默认值；保存草稿 toast 未发布；保存并发布走二次确认。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v"><ul><li>上游=F1 列表；下游=F5–F9 配置、发布</li></ul></div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">点击弹出空白编辑弹窗且为默认值（权重 50/30/10/10）；保存后返回列表、行正确；草稿不改变适用场景。</div></div></div></div><div class="dev-card" id="dc-f3"><div class="dc-head"><span class="dc-id">F3</span><span class="dc-name">业务场景字典（场景管理）</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v"><ul><li>场景管理：列出业务场景名与关联策略数，支持新建场景、查看该场景下关联策略（名称 / 编号 / 版本 / 状态）；场景与策略的关联在「策略配置 → 编辑策略 → 适用场景」维护</li><li>场景可单独新建（演示态前端数组，场景名 + 描述）；新建后可在策略编辑的「适用场景」中选择</li></ul><br>· <b>F-CR-103 检查检索场景停用影响范围</b>：由「场景字典『查看关联策略』」动作承载。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v"><ul><li>主 Tab「② 业务场景字典」；内置通用 / 专业 / 作业 / 训练数据筛选四场景，可继续新建</li></ul></div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>场景行：业务场景名（含描述）/ 关联策略数 / 操作（「查看关联策略」）</li><li>数据来源：遍历策略列表的「适用场景」汇总，一个场景可关联多条策略；演示：通用=票证策略、专业=专业策略、训练数据筛选=作业策略+阈值收紧、作业=0（兜底）</li><li>新建场景表单：场景名 + 描述 → 加入场景列表并同步到策略编辑弹窗「适用场景」下拉</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v"><ul><li>场景与策略的关联在「策略配置 → 编辑策略 → 适用场景」维护；本 Tab 只管场景本身并查看关联策略</li><li>「查看关联策略」只读弹层列出该场景下策略名称 / 编号 / 版本 / 状态；0 条时提示由系统默认策略（兜底）承接</li><li>「＋新建场景」小表单（场景名 + 描述），演示态写前端数组并同步「适用场景」下拉；重名校验</li><li>保存草稿 / 发布后调用 renderSceneTable 重算关联策略数</li></ul></div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>场景清单与关联策略数（随策略「适用场景」变化）</li><li>新场景同步到策略编辑弹窗「适用场景」下拉，供策略发布时绑定</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>场景名为空 / 重名时拦截并 toast</li><li>删除策略或改绑后，场景关联数需重算（renderSceneTable）</li><li>新场景为演示态前端数组，刷新后重置；正式由后端字典持久化</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">场景数 ≥ 4；新场景带「自定义」标记；关联数 0 时显示兜底提示。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v"><ul><li>上游=策略列表「适用场景」</li><li>下游=<a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a> 场景下拉、策略编辑「适用场景」选项</li></ul></div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">3 个主 Tab 可切；场景行展示名称 / 关联策略数 /「查看关联策略」；新建场景（场景名+描述）加入列表并同步下拉；查看关联策略只读列出名称/编号/版本/状态；场景关联数随策略「适用场景」变化。</div></div></div></div><div class="dev-card" id="dc-f5"><div class="dc-head"><span class="dc-id">F5</span><span class="dc-name">向量模型选择</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">在策略内从<b>产线白名单</b>选择向量模型，随三段参数一起保存生效。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v"><ul><li>编辑弹窗「向量参数」；仅管理员可编辑，系统预置策略也仅管理员可编辑</li></ul></div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>模型下拉 4 项白名单：图文对齐 · CLIP双塔 v3（默认）/ 视频关键帧 · CLIP双塔 v2 / 文本语义 · 语义向量引擎 v2 / 音频特征 · 音频引擎 A1</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v"><ul><li>onSchemeChange('model')：切换即标脏，草稿徽标改「编辑中」</li><li>不写回执、不单独落版本；随底部「保存草稿 / 保存并发布」一次性写入</li></ul></div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>模型写入策略 data-model</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>下拉只列白名单内模型（假跨模态风险防线）</li><li>只读角色下拉禁用；演示不校验真实向量维度</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v"><ul><li>默认 CLIP双塔 v3 / 编辑中 / 只读禁用</li></ul></div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v"><ul><li>上游=产线模型白名单、P4 库向量版本（<a class="dev-xref" href="P4_库管理_v5.2_20261026.html">P4</a>）</li><li>下游=F9 发布</li></ul></div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">下拉只含 4 个白名单模型；保存并发布后模型随策略生效。</div></div></div></div><div class="dev-card" id="dc-f6"><div class="dc-head"><span class="dc-id">F6</span><span class="dc-name">融合权重配置</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">配置文本 / 图像 / 视频 / 音频四路融合权重（合计 100%）。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v"><ul><li>编辑弹窗「融合方案」；仅管理员可编辑，系统预置策略也仅管理员可编辑</li></ul></div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>4 个滑块：文本（默认 50）/ 图像（30）/ 视频（10）/ 音频（10），范围 0–100，右侧实时显示百分比</li><li>合计行「权重合计 100% ✓」，≠100% 时数字标红并提示「需调整至 100%」</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v"><ul><li>拖动即时 syncFuseWeights：刷新四路百分比与合计，合计 100% 绿勾 / 否则标红提示</li><li>onWeightChange：标脏，草稿徽标改「编辑中」</li><li>不写回执、不单独落版本；随底部「保存草稿 / 保存并发布」一次性写入</li></ul></div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>四模态权重写入策略 data-w</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>只读角色滑块禁用</li><li>合计≠100% 仅提示不硬阻止；演示不落库</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v"><ul><li>默认 50/30/10/10；拖动实时；合计 100% 绿 / ≠100% 红</li></ul></div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v"><ul><li>上游=产线模态能力（音频随产线开放）</li><li>下游=F9 发布</li></ul></div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">4 个滑块可调且合计实时；≠100% 标红、=100% 打勾；保存并发布后权重随策略生效。</div></div></div></div><div class="dev-card" id="dc-f7"><div class="dc-head"><span class="dc-id">F7</span><span class="dc-name">重排开关</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">用「启用重排」开关配置重排段。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v"><ul><li>编辑弹窗「重排方案」；仅管理员可编辑，系统预置策略也仅管理员可编辑</li></ul></div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>开关：启用重排 = 开（默认）/ 关，旁显「开 / 关」状态字</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v"><ul><li>切换：syncRerankLabel 即时更新状态字并标脏</li><li>不写回执、不单独落版本；随底部「保存草稿 / 保存并发布」一次性写入</li></ul></div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>重排开关（on / off）写入策略 data-rerank</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>只读角色开关禁用</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v"><ul><li>默认开 / 关闭 / 只读禁用</li></ul></div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v"><ul><li>上游=F5–F6 的向量与融合参数</li><li>下游=<a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a> 最终排序、F9 发布</li></ul></div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">开关是唯一控制且状态字联动；保存并发布后开关随策略生效。</div></div></div></div><div class="dev-card" id="dc-f8"><div class="dc-head"><span class="dc-id">F8</span><span class="dc-name">系统默认策略（系统预置兜底）</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v"><ul><li>系统默认策略作为<b>系统预置</b>行保留在策略列表，仅管理员可编辑，其他角色只读引用</li><li>场景无专属策略时自动回落此策略，保证检索永远可用；参数随「保存并发布」更新</li></ul><br>· <b>F-CR-084 登记跨模态检索默认策略接入申请</b>：由「系统预置行 + 适用场景兜底」动作承载。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v"><ul><li>策略列表最后一行「系统默认策略（兜底）」，名称后带「系统预置」标记</li><li>与普通策略同一编辑弹窗（仅参数字段）</li></ul></div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>点击行内「编辑」→ 载入兜底参数</li><li>向量 / 融合 / 重排 三段参数 + 适用场景（全部场景（兜底））</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v"><ul><li>admin 可编辑并「保存草稿 / 保存并发布」；其他角色只读引用（正式由登录态控制）</li><li>保存并发布后更新兜底参数，未绑定专属策略的场景即时回落</li><li>兜底可用性由场景字典关联数与运维绑定矩阵体现</li></ul></div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>系统默认策略新版本（v3）与兜底参数</li><li>策略列表「系统预置」行回写，场景数量 / 兜底提示同步</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>非管理员编辑被只读拦截</li><li>场景未绑定专属策略时始终可回落默认策略</li><li>演示参数为前端数组，不请求真实引擎</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v"><ul><li>系统预置标记；admin 可编辑 / 其他只读；保存草稿、已发布两种状态</li></ul></div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v"><ul><li>上游=策略列表 / 场景字典；下游=<a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a> 检索（未绑定场景回落）、策略运维绑定矩阵</li></ul></div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">默认策略行标注「系统预置」；编辑弹窗只含基本信息与向量 / 融合 / 重排三段参数字段；admin 可保存草稿 / 发布，其他角色只读；未绑定场景回落提示正确。</div></div></div></div><div class="dev-card" id="dc-f9"><div class="dc-head"><span class="dc-id">F9</span><span class="dc-name">保存并发布</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v"><ul><li>把编辑弹窗内已配好的三段参数（向量 / 融合 / 重排）一次性发布到该策略「适用场景」绑定的场景，对 <a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a> 下次检索生效</li></ul></div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v"><ul><li>策略编辑弹窗底部钉住区「保存并发布」（唯一主操作）；变更评估「发布此草稿」为第二处生效入口</li><li>发布前置：候选策略在 <a class="dev-xref" href="P8_评测_v5.0_20261025.html">P8</a> 用评测集验证通过</li></ul></div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>策略名称、适用场景、三段参数（向量模型 / 融合权重 / 重排开关）</li><li><a class="dev-xref" href="P8_评测_v5.0_20261025.html">P8</a> 评测结论（S1 验收线）作为发布依据</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v"><ul><li>融合权重合计须为 100%，≠100% 时弹窗内行内提示标红、发布被拦截；系统预置（兜底）策略仅管理员可发布，其他角色只读</li><li>「保存并发布」（编辑弹窗底部钉住区）：通用确认弹层（cfMask）二次确认，列出向量/融合/重排、适用场景切换、下次检索生效、发布生成新版本且上一版可回滚；确认后返回列表</li></ul></div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>确认后：场景关联数重算、「训练数据筛选」绑定切到该策略、版本沿用当前版本、徽标变「生效中」</li><li>发布即写入版本历史，版本号按 v1 / v2 / v3 递增</li><li>返回列表、行更新为「生效中」，并在策略列表生成「前往 <a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a> →」链接与发布提示条</li><li>系统默认策略仍为全场景兜底；历史版本可在 F11 回滚</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>策略名称未填：toast「请先填写策略名称」，发布中断</li><li>融合权重 ≠100%：发布拦截并标红合计行</li><li>非管理员发布：按钮禁用并提示只读</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v"><ul><li>草稿 → 编辑中 → 生效中；发布后回列表；未发布不影响检索</li></ul></div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v"><ul><li>上游=<a class="dev-xref" href="P8_评测_v5.0_20261025.html">P8</a> 评测结论（S1 验收线）、<a class="dev-xref" href="P4_库管理_v5.2_20261026.html">P4</a> 向量版本</li><li>下游=<a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a>（下次检索生效）、<a class="dev-xref" href="P6_任务管理_v5.0_20261025.html">P6</a>（策略版本留痕）、F11 变更评估 / 版本回滚</li></ul></div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">「保存并发布」二次确认列出三段参数与场景切换；确认后绑定 / 徽标 / 返回列表 / 列表提示联动正确；列表行操作仅「编辑」。</div></div></div></div><div class="dev-card" id="dc-f10"><div class="dc-head"><span class="dc-id">F10</span><span class="dc-name">反馈建议采纳（P7 来件）</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">承接 <a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a> 结果运营的空结果归因，一键采纳为策略调整建议，进入变更评估 diff</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v">运维区「变更评估」子 Tab 的待处理横幅（p7Pending）。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li><a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a> 反馈单 FB-0417：低质票据混入归因，建议提高图像权重并开启启用重排</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v"><ul><li>adoptP7：横幅转绿「✓ 已采纳反馈归因建议：图像权重提高并开启启用重排（FB-0417），已写入草稿」，按钮置灰</li><li>采纳只产生改动建议并写入变更 diff，不直接发布，需经评测（P8）验证后再发布</li></ul></div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>变更 diff 新增条目；待处理横幅转已采纳；引导重新评测</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>采纳后未发布前，改动不生效</li><li>同一建议重复采纳：按钮置灰防重</li><li>演示为静态横幅，正式对接 <a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a> 单据状态回写</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v">待处理（黄条）→ 已采纳（绿条、按钮置灰）。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v"><ul><li>上游=<a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a>（反馈 FB 单）；下游=F6 融合权重改动、F9 发布</li></ul></div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">采纳按钮把横幅转绿且文案含单号；按钮置灰；改动提示需发布而非直接生效。</div></div></div></div><div class="dev-card" id="dc-f11"><div class="dc-head"><span class="dc-id">F11</span><span class="dc-name">策略运维：变更评估 / 版本回滚</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v">运维对象＝下拉选中的一条策略（默认第一条）。只保留两块：①变更评估（前提＝该策略存在草稿 / 待发布版本：有草稿才对比上一版，展示配置 diff；无草稿显示「当前无待发布变更（已生效 vX）」并隐藏 diff）②版本回滚（发布后版本历史 + 一键回滚，回写版本 / 状态 / 配置并出核验回执）。有草稿时 diff + 影响下方一行「发布此草稿 / 放弃草稿」：发布即草稿转生效版并出回执，放弃需二次确认后回到无变更。</div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v"><ul><li>「策略运维」区先选策略（下拉，默认第一条），再切 2 个子 Tab，默认「变更评估」；变更评估的前置＝该策略有草稿 / 待发布版本，无草稿只显示状态句、不出现 diff；管理员可操作，访客只读</li></ul></div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>选中策略的待发布草稿 vs 上一版（生效版）的配置 diff（向量 / 融合权重 / 重排 三段差异）+ <a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a> 待处理 FB-0417</li><li>版本历史：按选中策略返回各版本（草稿待发布 / v3 / v2 / v1），非当前版本且非草稿提供回滚入口</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v"><ul><li>选中策略后 activateTab 切换 2 个子 Tab（变更评估 / 版本回滚）</li><li>变更评估先判断前提：有草稿才渲染草稿 vs 上一版的 diff、影响场景、日均任务、关联评测，无草稿只显示「当前无待发布变更（已生效 vX）」并隐藏 diff；评估结果只展示；有草稿时下方一行「发布此草稿 / 放弃草稿」——发布沿用草稿版本写入版本历史并出回执，放弃二次确认后回到无变更；采纳 <a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a> 建议走 F10</li><li>doRollback：二次确认，回写版本 / 状态 / 配置，适用场景同步回退，未发布草稿随之放弃，生成核验条与回执；回滚后可再次发布恢复</li></ul></div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>评估结果＝diff / 影响场景 / 日均任务 / 关联评测的只读展示</li><li>发布草稿后的发布回执：生效版本 / 配置摘要 / 生效时间；放弃草稿后状态回「当前无待发布变更（已生效 vX）」</li><li>回滚后版本表状态回写，适用场景同步到目标版本</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>无待发布草稿时不出现 diff / 影响区域，避免「没改也评估」；回滚须同步适用场景与配置，不能只改版本号</li><li>回滚入口只对非当前版本且非草稿的版本开放</li><li>演示为静态数据与 toast，正式由配置中心存储并保留审计台账</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v"><ul><li>有草稿 / 无草稿两种评估态；子 Tab 切换；二次确认弹层；回滚核验条与回执</li></ul></div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v"><ul><li>上游=<a class="dev-xref" href="P7_结果运营_v5.0_20261025.html">P7</a> 反馈；下游=F3 场景字典、<a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a> 场景策略、审计台账</li></ul></div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">下拉默认第一条策略、切换后两块同步；仅 2 个子 Tab 可切；有草稿的作业策略显示「待发布变更：v3 → v4（草稿）」与 diff / 影响场景 / 日均任务 / 关联评测，以及「发布此草稿 / 放弃草稿」；专业策略显示「当前无待发布变更（已生效 v2）」；回滚二次确认、回写版本与适用场景、生成回执。</div></div></div></div><div class="dev-card" id="dc-f12"><div class="dc-head"><span class="dc-id">F12</span><span class="dc-name">角色权限态</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row "><div class="dc-k">职责</div><div class="dc-v"><ul><li>控制本页角色权限：仅管理员可编辑 / 发布，访客整页只读</li></ul></div></div><div class="dc-row "><div class="dc-k">入口/前置</div><div class="dc-v"><ul><li>固定权限遮罩 permMask；正式由登录态与库授权决定，原型默认管理员</li></ul></div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>角色：admin / guest</li></ul></div></div><div class="dc-row "><div class="dc-k">处理规则</div><div class="dc-v"><ul><li>访客：permMask 无权限遮罩展示「当前角色无管理页权限」，全部编辑 / 发布类入口不可用，列表只读</li><li>管理员：恢复全部操作</li></ul></div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>权限态决定全页可操作性</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>访客任何写操作都被遮罩拦截（正式需后端 RBAC 二次校验）</li><li>演示为前端模拟角色，正式以登录态权限为准</li></ul></div></div><div class="dc-row "><div class="dc-k">状态</div><div class="dc-v"><ul><li>管理员可操作 / 访客只读遮罩</li></ul></div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v"><ul><li>权限对接平台统一身份 / RBAC</li></ul></div></div><div class="dc-row "><div class="dc-k">验收点</div><div class="dc-v">访客遮罩覆盖全页写操作；管理员编辑 / 发布 / 回滚全部可用。</div></div></div></div>
  </div>
</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  createDictItem,
  createStrategy,
  discardDraft,
  pageStrategies,
  publishStrategy,
  rollbackStrategy,
  strategyConsistency,
  strategyDetail,
  strategyDiff,
  strategyModelOptions,
  updateDraft,
} from '../../api'
import type { DictItem, Id, StrategyDetailVO, StrategyVersionVO, StrategyVO } from '../../api/types'
import { useDictStore } from '../../stores/dict'

type MainTab = 'config' | 'scene' | 'ops'
type OpsTab = 'eval' | 'rollback'

const route = useRoute()
const dictStore = useDictStore()
const activeTab = ref<MainTab>('config')
const opsTab = ref<OpsTab>('eval')
const strategyLoading = ref(false)
const strategies = ref<StrategyVO[]>([])
const scenes = ref<DictItem[]>([])
const strategyStatusDict = ref<DictItem[]>([])
const strategySceneFilter = ref('')
const strategyStatusFilter = ref('')

const editorVisible = ref(false)
const editorSaving = ref(false)
const editingId = ref<Id | null>(null)
const editorDetail = ref<StrategyDetailVO | null>(null)
const editor = reactive({
  name: '',
  scene: 'general',
  weights: { text: 25, image: 25, audio: 25, video: 25 },
  rerank: false,
  topK: 20,
})

const sceneFormOpen = ref(false)
const sceneSubmitting = ref(false)
const newScene = reactive({ label: '', remark: '' })
const sceneStrategiesVisible = ref(false)
const selectedScene = ref<DictItem | null>(null)

const opsStrategyId = ref<Id | ''>('')
const opsDetail = ref<StrategyDetailVO | null>(null)
const modelOptions = ref<Record<string, unknown> | null>(null)
const consistencyResult = ref<Record<string, unknown> | null>(null)
const consistencyChecking = ref(false)

const confirmVisible = ref(false)
const confirmTitle = ref('确认')
const confirmMessage = ref('')
let confirmAction: (() => Promise<void>) | null = null

const devOpen = ref(false)
const toastVisible = ref(false)
const toastMessage = ref('')
const toastType = ref<'info' | 'ok' | 'err'>('info')
let toastTimer: ReturnType<typeof setTimeout> | undefined

const mainTabs = [
  { key: 'config', label: '① 策略配置' },
  { key: 'scene', label: '② 业务场景字典' },
  { key: 'ops', label: '③ 策略运维' },
] as const

const editorWeightSum = computed(() => editor.weights.text + editor.weights.image + editor.weights.audio + editor.weights.video)

/** 手输权重：限制在 0~100 的整数（滑块与数字框双向联动） */
function normalizeWeight(key: 'text' | 'image' | 'audio' | 'video') {
  const value = Number(editor.weights[key])
  editor.weights[key] = Number.isFinite(value) ? Math.min(100, Math.max(0, Math.round(value))) : 0
}
const editorVersion = computed(() => {
  if (!editingId.value) return 'v1（待创建）'
  const current = editorDetail.value?.current?.versionNo ?? 0
  return editorDetail.value?.draft ? `v${current} → v${current + 1}（草稿）` : `v${current}`
})
const editorStatus = computed(() => editorDetail.value?.strategy.status === 'active' ? '生效中，编辑中' : '草稿')
const editorModelVersion = computed(() => editorDetail.value?.modelVersion || '')
const modelChoices = computed(() => {
  const models = modelOptions.value?.models as string[] | undefined
  return models?.length ? models : editorModelVersion.value ? [editorModelVersion.value] : ['当前生效索引模型']
})
const modelHint = computed(() => {
  if (!modelOptions.value) return '统一检索固定一套向量空间，不可切换'
  const enabled = modelOptions.value.whitelistEnabled === true
  const allowed = modelOptions.value.activeAllowed === true
  return `${enabled ? '白名单模型' : '索引模型'} · ${allowed ? '当前索引模型校验通过' : '当前索引模型不在白名单内'}`
})
const editorReadonlyPreset = computed(() => editorDetail.value?.strategy.name?.includes('系统默认') || false)
const relatedStrategies = computed(() => selectedScene.value ? strategies.value.filter(item => item.scene === selectedScene.value?.alias) : [])
watch(activeTab, tab => {
  if (tab === 'ops' && opsStrategyId.value && !opsDetail.value) {
    void loadOpsDetail()
  }
})

const opsDiffRows = ref<Array<{ name: string; from: string; to: string }>>([])

onMounted(async () => {
  ;[scenes.value, strategyStatusDict.value] = await Promise.all([
    dictStore.ensure('cmr_scene'),
    dictStore.ensure('cmr_strategy_status'),
  ])
  if (scenes.value[0]) editor.scene = scenes.value[0].alias
  await Promise.all([loadModelOptions(), reloadStrategies()])
  await locateStrategyFromRoute()
})

async function loadModelOptions() {
  try {
    modelOptions.value = await strategyModelOptions()
  } catch {
    modelOptions.value = null
  }
}

async function checkConsistency() {
  if (!opsStrategyId.value) {
    showToast('请先选择策略', 'err')
    return
  }
  consistencyChecking.value = true
  try {
    consistencyResult.value = await strategyConsistency(opsStrategyId.value)
    showToast(consistencyResult.value.passed === true ? '一致性检查通过' : '一致性检查未通过', consistencyResult.value.passed === true ? 'ok' : 'err')
  } catch (error) {
    showToast((error as Error).message, 'err')
  } finally {
    consistencyChecking.value = false
  }
}

async function reloadStrategies() {
  strategyLoading.value = true
  try {
    const page = await pageStrategies({ pageNum: 1, pageSize: 100, scene: strategySceneFilter.value, status: strategyStatusFilter.value })
    strategies.value = page.records
    if (!opsStrategyId.value && strategies.value[0]) {
      opsStrategyId.value = strategies.value[0].id
    }
  } catch (error) {
    showToast((error as Error).message, 'err')
  } finally {
    strategyLoading.value = false
  }
}

function sceneLabel(scene: string | null) {
  if (scene === 'all') return '全部场景（兜底）'
  return scenes.value.find(item => item.alias === scene)?.label || scene || '—'
}

function statusLabel(status: string) {
  return strategyStatusDict.value.find(item => item.alias === status)?.label || status
}

function sceneStrategyCount(alias: string) {
  return strategies.value.filter(item => item.scene === alias).length
}

function openNewStrategy() {
  editingId.value = null
  editorDetail.value = null
  editor.name = ''
  editor.scene = scenes.value[0]?.alias || 'general'
  editor.weights = { text: 25, image: 25, audio: 25, video: 25 }
  editor.rerank = false
  editor.topK = 20
  editorVisible.value = true
}

async function openEditStrategy(row: StrategyVO) {
  editingId.value = row.id
  try {
    const detail = await strategyDetail(row.id)
    editorDetail.value = detail
    const base = detail.draft || detail.current
    const weights = parseWeights(base?.weightsJson)
    editor.name = detail.strategy.name
    editor.scene = detail.strategy.scene
    editor.weights = weights
    editor.rerank = base?.rerank ?? false
    editor.topK = base?.topK ?? 20
    editorVisible.value = true
  } catch (error) {
    showToast((error as Error).message, 'err')
  }
}

function validateEditor() {
  if (!editor.name.trim()) {
    showToast('请输入策略名称', 'err')
    return false
  }
  if (!editor.scene) {
    showToast('请选择适用场景', 'err')
    return false
  }
  if (editorWeightSum.value !== 100) {
    showToast(`权重合计必须为 100%，当前 ${editorWeightSum.value}%`, 'err')
    return false
  }
  if (editor.topK < 1 || editor.topK > 100) {
    showToast('返回条数须在 1 到 100 之间', 'err')
    return false
  }
  return true
}

async function saveStrategy(shouldPublish: boolean) {
  if (!validateEditor()) return
  editorSaving.value = true
  const body = {
    name: editor.name.trim(),
    scene: editor.scene,
    remark: editorDetail.value?.strategy.remark || '',
    weights: { ...editor.weights },
    rerank: editor.rerank,
    topK: editor.topK,
  }
  try {
    let id = editingId.value
    if (!id) {
      const created = await createStrategy(body)
      id = created.id
      editingId.value = id
    } else {
      await updateDraft(id, body)
    }
    if (shouldPublish) {
      await publishStrategy(id)
      showToast('策略已保存并发布', 'ok')
    } else {
      showToast('策略草稿已保存', 'ok')
    }
    editorVisible.value = false
    await reloadStrategies()
    if (opsStrategyId.value === id) await loadOpsDetail()
  } catch (error) {
    showToast((error as Error).message, 'err')
  } finally {
    editorSaving.value = false
  }
}

async function locateStrategyFromRoute() {
  const strategyVersionId = String(route.query.strategyVersionId ?? '')
  if (!strategyVersionId) {
    return
  }
  for (const row of strategies.value) {
    try {
      const detail = await strategyDetail(row.id)
      if (detail.versions.some(version => version.id === strategyVersionId)) {
        opsStrategyId.value = row.id
        opsDetail.value = detail
        activeTab.value = 'ops'
        return
      }
    } catch {
      // 单个策略详情失败时继续查找下一个。
    }
  }
}

async function loadOpsDetail() {
  if (!opsStrategyId.value) {
    opsDetail.value = null
    return
  }
  try {
    opsDetail.value = await strategyDetail(opsStrategyId.value)
    await loadOpsDiff(opsDetail.value)
  } catch (error) {
    showToast((error as Error).message, 'err')
  }
}

async function loadOpsDiff(detail: StrategyDetailVO) {
  if (!detail.draft) {
    opsDiffRows.value = []
    return
  }
  if (detail.current) {
    try {
      const data = await strategyDiff(detail.strategy.id, detail.current.versionNo, detail.draft.versionNo)
      const weights = (data.weights || {}) as Record<string, { from: number; to: number }>
      const rerank = data.rerank as { from: boolean; to: boolean } | undefined
      const topK = data.topK as { from: number; to: number } | undefined
      opsDiffRows.value = [
        { name: '文本权重', from: `${weights.text?.from ?? 0}%`, to: `${weights.text?.to ?? 0}%` },
        { name: '图像权重', from: `${weights.image?.from ?? 0}%`, to: `${weights.image?.to ?? 0}%` },
        { name: '音频权重', from: `${weights.audio?.from ?? 0}%`, to: `${weights.audio?.to ?? 0}%` },
        { name: '视频权重', from: `${weights.video?.from ?? 0}%`, to: `${weights.video?.to ?? 0}%` },
        { name: '重排', from: rerank?.from ? '开' : '关', to: rerank?.to ? '开' : '关' },
        { name: 'TopK', from: String(topK?.from ?? '—'), to: String(topK?.to ?? '—') },
      ]
      return
    } catch {
      // 后端 diff 失败时保留本地回显，避免策略运维整块不可用。
    }
  }
  const current = detail.current
  const draft = detail.draft
  const cw = parseWeights(current?.weightsJson)
  const dw = parseWeights(draft.weightsJson)
  opsDiffRows.value = [
    { name: '文本权重', from: `${cw.text}%`, to: `${dw.text}%` },
    { name: '图像权重', from: `${cw.image}%`, to: `${dw.image}%` },
    { name: '音频权重', from: `${cw.audio}%`, to: `${dw.audio}%` },
    { name: '视频权重', from: `${cw.video}%`, to: `${dw.video}%` },
    { name: '重排', from: current?.rerank ? '开' : '关', to: draft.rerank ? '开' : '关' },
    { name: 'TopK', from: String(current?.topK ?? '—'), to: String(draft.topK ?? '—') },
  ]
}

async function publishOpsDraft() {
  if (!opsDetail.value) return
  try {
    await publishStrategy(opsDetail.value.strategy.id)
    showToast('草稿已发布并生效', 'ok')
    await loadOpsDetail()
    await reloadStrategies()
  } catch (error) {
    showToast((error as Error).message, 'err')
  }
}

function discardOpsDraft() {
  if (!opsDetail.value) return
  askConfirm('放弃草稿', '确认放弃当前未发布草稿？', async () => {
    await discardDraft(opsDetail.value!.strategy.id)
    showToast('草稿已放弃', 'ok')
    await loadOpsDetail()
    await reloadStrategies()
  })
}

function requestDiscardEditorDraft() {
  if (!editingId.value) return
  askConfirm('放弃草稿', '确认放弃当前未发布草稿？', async () => {
    await discardDraft(editingId.value!)
    editorVisible.value = false
    showToast('草稿已放弃', 'ok')
    await reloadStrategies()
  })
}

function requestRollback(version: StrategyVersionVO) {
  if (!opsDetail.value) return
  askConfirm('版本回滚', `确认回滚到 v${version.versionNo}？系统会生成新版本并立即生效。`, async () => {
    await rollbackStrategy(opsDetail.value!.strategy.id, version.versionNo)
    showToast('版本回滚已生效', 'ok')
    await loadOpsDetail()
    await reloadStrategies()
  })
}

function versionSummary(version: StrategyVersionVO) {
  const weights = parseWeights(version.weightsJson)
  return `${weights.text}/${weights.image}/${weights.audio}/${weights.video} · 重排${version.rerank ? '开' : '关'}`
}

async function createScene() {
  if (!newScene.label.trim()) {
    showToast('请输入场景名', 'err')
    return
  }
  sceneSubmitting.value = true
  try {
    const created = await createDictItem('cmr_scene', { label: newScene.label.trim(), remark: newScene.remark.trim() })
    scenes.value = [...scenes.value, created]
    dictStore.cache.cmr_scene = [...scenes.value]
    newScene.label = ''
    newScene.remark = ''
    sceneFormOpen.value = false
    showToast('业务场景已创建', 'ok')
  } catch (error) {
    showToast((error as Error).message, 'err')
  } finally {
    sceneSubmitting.value = false
  }
}

function cancelSceneForm() {
  sceneFormOpen.value = false
  newScene.label = ''
  newScene.remark = ''
}

function showSceneStrategies(scene: DictItem) {
  selectedScene.value = scene
  sceneStrategiesVisible.value = true
}

function parseWeights(source?: string | null) {
  try {
    const value = JSON.parse(source || '{}') as Record<string, number>
    return {
      text: value.text ?? 0,
      image: value.image ?? 0,
      audio: value.audio ?? 0,
      video: value.video ?? 0,
    }
  } catch {
    return { text: 25, image: 25, audio: 25, video: 25 }
  }
}

function askConfirm(title: string, message: string, action: () => Promise<void>) {
  confirmTitle.value = title
  confirmMessage.value = message
  confirmAction = action
  confirmVisible.value = true
}

async function runConfirm() {
  const action = confirmAction
  confirmVisible.value = false
  confirmAction = null
  if (!action) return
  try {
    await action()
  } catch (error) {
    showToast((error as Error).message, 'err')
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
.p5-root { min-height: 100%; }
.p5-filter { display:flex; gap:8px; align-items:center; flex-wrap:wrap; margin-bottom:10px; }
.p5-filter select {
  height:32px; border:1px solid #d9e7f8; border-radius:8px; padding:0 28px 0 10px;
  background-color:#fff; color:#344054; font-size:12.5px; cursor:pointer; outline:none;
  appearance:none; -webkit-appearance:none; -moz-appearance:none;
  background-image:url("data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%237f90ad' stroke-width='2.2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E");
  background-repeat:no-repeat; background-position:right 8px center; background-size:13px 13px;
  transition:border-color .15s, box-shadow .15s;
}
.p5-filter select:hover { border-color:#bcd2f6; }
.p5-filter select:focus { border-color:#8fa3ff; box-shadow:0 0 0 3px rgba(65,105,246,.10); }
.ops-sub { color:#1f5fd4; font-weight:600; }
.p5-small-title { font-size:13px; }
.scene-alias { margin-left:8px; color:#98a2b3; font-size:11px; }
.ops-switch-line { gap:16px; }
.topk-pick { display:flex; flex-direction:row; align-items:center; gap:8px; }
.topk-pick input { width:80px; height:30px; border:1px solid #d9e7f8; border-radius:6px; padding:0 8px; }
.sum-line.invalid { color:#d92d20; }
.table-empty { grid-column:1/-1; text-align:center; color:#98a2b3; padding:28px 0; font-size:12px; }
:root{
  /* 清爽冷白蓝 token（与 P1 定稿基准一致） */
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

/* ============ 左侧全局导航（与P1同壳） ============ */
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
  cursor:pointer;user-select:none;
}
a.nav-item{text-decoration:none;color:#344054}
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
.crumb-bar{
  height:40px;flex-shrink:0;
  background:#fff;border-bottom:1px solid var(--content-line);
  display:flex;align-items:center;gap:14px;padding:0 24px;
  position:sticky;top:0;z-index:20;
}
.crumb{font-size:12px;color:var(--muted);display:flex;align-items:center;gap:6px}
.crumb b{color:var(--system-blue);font-weight:600}
.crumb .sep{color:#c4cede}
.crumb-spacer{flex:1}
.crumb-note{font-size:11px;color:#9aa6bd;border:1px solid #e6edf8;border-radius:10px;padding:1px 9px;background:var(--content-soft)}
.hb-time{font-size:11px;color:#9aa6bd;font-variant-numeric:tabular-nums}
.content{padding:20px 24px 32px;display:flex;flex-direction:column;gap:16px;max-width:1280px;width:100%;margin:0 auto}
.card{border:1px solid #e4edf8;border-radius:var(--radius);background:#fff}

/* 模块介绍（与P1同构） */
.module-intro{
  display:flex;align-items:flex-start;justify-content:space-between;gap:16px;flex-wrap:wrap;
  padding:18px 20px;
  border:1px solid #e4edf8;
  border-radius:10px;
  background:#fff;
}
.module-intro h2{font-size:20px;font-weight:700;color:#111827;margin-bottom:7px;display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.tag-mvp{
  font-size:11px;font-weight:600;color:var(--system-blue);
  background:var(--system-blue-weak);border:1px solid #d7e0ff;
  padding:2px 8px;border-radius:10px;white-space:nowrap;
}
.module-intro p{color:var(--muted);line-height:1.65;max-width:760px;font-size:13px}
.module-summary{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}
.module-summary article{
  padding:12px 14px;border:1px solid #e0eaf7;border-radius:8px;
  background:linear-gradient(180deg,#fff 0%,#f8fbff 100%);
  display:flex;flex-direction:column;justify-content:center;
}
.module-summary span{font-size:12px;color:#7a8493}
.module-summary strong{display:block;margin-top:6px;color:#1f2a44;font-size:23px;font-weight:700;font-variant-numeric:tabular-nums}
.module-summary em{font-style:normal;font-size:11px;color:#9aa6bd;margin-top:2px}
.health-skel{display:inline-block;width:52px;height:16px;border-radius:4px;background:linear-gradient(90deg,#eef2f8,#f7f9fd,#eef2f8);background-size:200% 100%;animation:shimmer 1.2s infinite}
@keyframes shimmer{to{background-position:-200% 0}}

/* 分区标题 */
.sec-head{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.sec-head .sec-title{font-size:15px;font-weight:700;color:#1f2a44}
.sec-head .sec-sub{font-size:12px;color:#9aa6bd}
.sec-head::before{content:"";width:3px;height:14px;border-radius:2px;background:var(--system-blue)}
.sec-head .head-act{margin-left:auto;display:flex;gap:8px;align-items:center}

/* 面板容器 */
.panel{border:1px solid #e4edf8;border-radius:var(--radius);background:#fff;padding:16px 18px;display:flex;flex-direction:column;gap:12px}
.panel-head{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.pnum{
  width:22px;height:22px;border-radius:6px;flex-shrink:0;
  background:var(--system-blue-weak);color:var(--system-blue);
  font-weight:700;font-size:12px;display:grid;place-items:center;
  border:1px solid #d7e0ff;
}
.panel-head .p-title{font-size:15px;font-weight:700;color:#1f2a44}
.panel-head .p-sub{font-size:12px;color:#9aa6bd}
/* 策略运维：先选一条策略，再评估 / 回滚 */
.ops-pick{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.ops-pick .ed-select{width:340px;min-width:0;max-width:100%}
.ops-meta{font-size:11.5px;color:#98a2b3;display:inline-flex;align-items:center;gap:8px;flex-wrap:wrap}
.ops-meta b{color:#475467;font-weight:600}
.panel-head .p-act{margin-left:auto;display:flex;gap:8px;align-items:center}
.note-line{font-size:11.5px;color:#9aa6bd;line-height:1.6}


/* 变更评估：先判断有无待发布变更，再决定是否展示 diff / 影响 */
.eval-state{display:flex;align-items:center;gap:10px;flex-wrap:wrap;border:1px solid #e6edf8;border-radius:8px;background:#f7f9fc;padding:11px 13px;margin:12px 0 12px}
.eval-state .es-main{font-size:13px;font-weight:700;color:#475467}
.eval-state .es-main b{color:var(--system-blue);font-variant-numeric:tabular-nums}
.eval-state .es-sub{font-size:11.5px;color:#98a2b3;font-variant-numeric:tabular-nums}
.eval-state.pending{background:#fffaf0;border-color:#f5e3bd}
.eval-state.pending .es-main{color:#8a6116}
.eval-state.pending .es-main b{color:#b57500}
.ver-draft{background:#fffaf0}

/* 变更评估：三项影响（场景 / 任务量 / 评测），各自标来源 */
.eval-src{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-top:10px}
.eval-src .src-item{border:1px solid #e6edf8;border-radius:8px;background:#f8fbff;padding:8px 11px;display:flex;flex-direction:column;gap:3px;min-width:0}
.eval-src .si-main{font-size:12.5px;color:#475467;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.eval-src .si-main b{color:#1f2a44;font-weight:700;font-variant-numeric:tabular-nums}
.eval-src .si-src{font-size:10.5px;color:#98a2b3}
@media (max-width:900px){.eval-src{grid-template-columns:1fr}}
/* 变更评估：diff + 影响下方的发布 / 放弃一行 */
.eval-act{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-top:12px;padding-top:10px;border-top:1px solid #eef2f8}
.eval-act .btn-primary{height:32px;padding:0 18px;font-size:12.5px;box-shadow:0 2px 6px rgba(65,105,246,.22)}
.eval-act .mini-btn{height:32px;padding:0 14px;font-size:12.5px}

/* 迷你表格 */
.mini-table{border:1px solid #eef2f8;border-radius:8px;overflow-x:auto;overflow-y:hidden}
.mt-row{
  min-height:38px;padding:9px 12px;border-top:1px solid #eef2f8;
  display:grid;grid-template-columns:var(--tc,minmax(0,1fr) 100px);gap:8px;align-items:center;
  font-size:12px;
}
.mt-row:first-child{border-top:0}
.mt-row.head{color:#7a8493;background:#f4f6fb;font-weight:700;font-size:11.5px;min-height:34px}
.mt-row:not(.head):hover{background:#f8fbff}
.mt-row .q{white-space:nowrap;overflow:hidden;text-overflow:ellipsis;color:#273244}
.mt-row .c{color:var(--muted);font-size:11.5px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.mt-row .c.name{color:#1f2a44;font-weight:600;font-size:12px;min-width:0}

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
.mt-row .num{color:#1f2a44;font-weight:600;font-variant-numeric:tabular-nums}
.badge{font-size:11px;padding:2px 8px;border-radius:10px;font-weight:600;display:inline-block;justify-self:start;white-space:nowrap}
.badge.ok{background:#e8f7ec;color:var(--success)}
.badge.fail{background:#fdecec;color:var(--error)}
.badge.run{background:#e9f0ff;color:var(--system-blue)}
.badge.warn{background:#fdf3e1;color:#c98a12}
.badge.pend{background:#f0f2f6;color:#667085}
.badge.lock{background:#f4f5f8;color:#a8b0c0}

/* 小按钮 */
.mini-btn{
  height:26px;padding:0 10px;border-radius:4px;border:1px solid #d7e0f0;
  background:#fff;color:#475467;font-size:12px;white-space:nowrap;display:inline-flex;align-items:center;gap:5px;
}
.mini-btn:hover{border-color:var(--system-blue);color:var(--system-blue)}
.mini-btn.primary{background:var(--system-blue);border-color:var(--system-blue);color:#fff}
.mini-btn.primary:hover{background:#335be8;color:#fff}
.mini-btn.danger{color:var(--error);border-color:#f3c4c6}
.mini-btn.danger:hover{border-color:var(--error);color:var(--error)}
.mini-btn:disabled{opacity:.6;cursor:default}
.btn-primary{
  height:34px;padding:0 22px;border:0;border-radius:6px;
  background:var(--system-blue);color:#fff;font-size:13px;font-weight:600;
  display:inline-flex;align-items:center;gap:7px;
  box-shadow:0 3px 8px rgba(65,105,246,.25);transition:all .15s;
}
.btn-primary:hover{background:#335be8}
.btn-ghost{
  height:34px;padding:0 16px;border-radius:6px;border:1px solid #d7e0f0;
  background:#fff;color:#475467;font-size:13px;
}
.btn-ghost:hover{border-color:var(--system-blue);color:var(--system-blue)}
.small-primary{height:30px;padding:0 14px;border:0;border-radius:4px;background:var(--system-blue);color:#fff;font-size:12px;font-weight:600}
.small-primary:hover{background:#335be8}
.spinner{
  width:13px;height:13px;border:2px solid rgba(255,255,255,.4);border-top-color:#fff;
  border-radius:50%;animation:spin .7s linear infinite;display:inline-block;
}
.spinner.blue{border-color:rgba(65,105,246,.3);border-top-color:var(--system-blue)}
@keyframes spin{to{transform:rotate(360deg)}}

/* 参数行（向量模型 / 融合权重 / 重排开关：只留参数，不逐段确认） */
.ed-op{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.ed-pick{display:inline-flex;align-items:center;gap:8px;font-size:12.5px;color:#5b6472}
.ed-select{
  height:32px;min-width:260px;border:1px solid #d9e7f8;border-radius:8px;background-color:#fff;
  padding:0 28px 0 10px;font-size:13px;color:var(--text-primary);outline:none;cursor:pointer;
  appearance:none;-webkit-appearance:none;-moz-appearance:none;
  background-image:url("data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%237f90ad' stroke-width='2.2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E");
  background-repeat:no-repeat;background-position:right 8px center;background-size:13px 13px;
  transition:border-color .15s,box-shadow .15s;
}
.ed-select:focus{border-color:#8fa3ff;box-shadow:0 0 0 2px rgba(65,105,246,.12)}
.ed-select:disabled{background:#f5f7fb;color:#3f4a5c;cursor:default;opacity:1}
.ed-fixed{font-size:11.5px;color:#9aa6bd}
.ed-input{
  flex:1 1 auto;min-width:240px;height:32px;border:1px solid #cfd8e8;border-radius:6px;background:#fff;
  padding:0 10px;font-size:13px;color:var(--text-primary);outline:none;font-family:inherit;
}
.ed-input:focus{border-color:#8fa3ff;box-shadow:0 0 0 2px rgba(65,105,246,.12)}
.ed-switch{display:inline-flex;align-items:center;gap:8px;font-size:12.5px;color:#5b6472;cursor:pointer}
.ed-switch input{width:16px;height:16px;accent-color:var(--system-blue);cursor:pointer}
.ed-switch b{color:#1f2a44;font-weight:600}

/* 融合权重滑块（唯一可调参数，极简实现） */
.fuse-wrap{display:flex;flex-direction:column;gap:7px;max-width:720px}
.fslider{display:grid;grid-template-columns:44px minmax(0,1fr) 78px;gap:10px;align-items:center;font-size:12.5px;color:#5b6472}
.fslider input[type=range]{width:100%;accent-color:var(--system-blue)}
.fslider b{text-align:right;font-variant-numeric:tabular-nums;color:#1f2a44;font-size:12px;font-weight:600}
.fslider .wval{display:inline-flex;align-items:center;justify-content:flex-end;gap:2px;color:#1f2a44;font-size:12px;font-weight:600;font-variant-numeric:tabular-nums}
.wnum{width:52px;height:26px;border:1px solid #d9e7f8;border-radius:6px;padding:0 6px;text-align:right;font:inherit;font-size:12px;color:#1f2a44;background:#fff;outline:none;font-variant-numeric:tabular-nums}
.wnum:focus{border-color:#8fa3ff;box-shadow:0 0 0 2px rgba(65,105,246,.12)}
.wnum::-webkit-outer-spin-button,.wnum::-webkit-inner-spin-button{-webkit-appearance:none;margin:0}
.wnum{-moz-appearance:textfield;appearance:textfield}
.sum-line{font-size:12px;color:var(--muted)}
.sum-line b{color:var(--success);font-variant-numeric:tabular-nums}
.sum-line.over b{color:var(--error)}
.ed-form .fld.ro b{display:flex;align-items:center;height:32px;font-size:13px;color:#1f2a44;font-weight:600}

/* 只读行 */
.ro-line{display:flex;gap:10px;font-size:12.5px}
.ro-line span{color:#9aa6bd;min-width:78px;flex-shrink:0}
.ro-line b{color:#1f2a44;font-weight:600}

.sk-row{height:34px;border-radius:6px;background:linear-gradient(90deg,#eef2f8,#f7f9fd,#eef2f8);background-size:200% 100%;animation:shimmer 1.2s infinite;margin-bottom:6px}

/* Tab */
.tabbar{display:flex;gap:2px;border-bottom:1px solid var(--content-line);overflow-x:auto}
.tabbar button{
  border:0;background:transparent;padding:10px 16px;font-size:13px;color:var(--muted);
  position:relative;white-space:nowrap;
}
.tabbar button:hover{color:var(--system-blue)}
.tabbar button.active{color:var(--system-blue);font-weight:600}
.tabbar button.active::after{content:"";position:absolute;left:11px;right:11px;bottom:-1px;height:2px;background:var(--system-blue);border-radius:2px}

/* 横幅 */
.err-banner{
  display:flex;align-items:center;gap:10px;padding:10px 13px;border-radius:8px;
  background:#fff5f5;border:1px solid #f8d7d9;color:#b3373c;font-size:12.5px;flex-wrap:wrap;
}
.warn-banner{
  display:flex;align-items:center;gap:10px;padding:10px 13px;border-radius:8px;
  background:#fff8ea;border:1px solid #f5e3bd;color:#8a6116;font-size:12.5px;flex-wrap:wrap;
}
.ok-banner{
  display:flex;align-items:center;gap:10px;padding:10px 13px;border-radius:8px;
  background:#f2fbf4;border:1px solid #cdeed6;color:#1e7a43;font-size:12.5px;flex-wrap:wrap;
}
.banner-act{margin-left:auto;display:flex;gap:8px}
.warn-banner.em{animation:glow 1.4s 3}
@keyframes glow{0%,100%{box-shadow:0 0 0 0 rgba(240,160,32,0)}50%{box-shadow:0 0 0 5px rgba(240,160,32,.25)}}

/* 弹层（与P1同构） */
.modal-mask{
  position:fixed;inset:0;background:rgba(23,33,66,.42);z-index:130;
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
.modal-body{padding:12px 20px 4px;font-size:13px;color:#344054;line-height:1.7}
.modal-body .mb-tip{font-size:12px;color:var(--muted);margin-bottom:10px}
.checkline{display:flex;align-items:flex-start;gap:8px;font-size:12.5px;margin:5px 0}
.checkline .ok-ico{color:var(--success);font-weight:700}
.modal-foot{padding:12px 20px 18px;display:flex;align-items:center;justify-content:flex-end;gap:10px;margin-top:8px;border-top:1px solid #f0f4fb}
.modal-foot .modal-note{margin-right:auto;font-size:11px;color:#b3bccc}
.modal-foot a{font-size:12.5px;color:var(--system-blue);text-decoration:underline;text-underline-offset:3px}

/* ===== 最小交互补丁：一致性检查 / 生效回执 ===== */
.act-wrap{display:flex;gap:6px;flex-wrap:wrap;align-items:center}
.chk-line{display:none;font-size:11.5px;color:#1e7a43;background:#f2fbf4;border:1px solid #cdeed6;border-radius:4px;padding:1px 8px;white-space:nowrap}
.chk-line.show{display:inline-block}
.chk-line.fail{color:#b42318;background:#fff2f0;border-color:#f2c6c0}
.receipt{display:none;border:1px dashed #b7e2c4;background:#f7fdf9;border-radius:8px;padding:9px 12px;font-size:12px;color:#3d6b52;margin-top:8px}
.receipt.show{display:block}
.receipt .rc-head{display:flex;align-items:center;gap:8px;flex-wrap:wrap;font-weight:700;color:#1e7a43;margin-bottom:5px}
.receipt .rc-id{font-family:ui-monospace,Consolas,monospace;font-size:11.5px;background:#e8f7ec;border-radius:4px;padding:0 6px;letter-spacing:.2px;color:#1e7a43}
.receipt .rc-line{line-height:1.75}
.receipt .rc-line span{color:#7a9c88}
.modal-box.wide{width:660px}
.modal-box.wide .modal-body{max-height:58vh;overflow:auto}
.fld{display:block;font-size:12px;color:#475467;margin:10px 0 4px}
.fld input,.fld select{width:100%;height:32px;border:1px solid #d9e7f8;border-radius:8px;padding:0 10px;font-size:13px;background:#fff;outline:none;color:var(--text-primary)}
.fld select{
  padding-right:28px;cursor:pointer;
  appearance:none;-webkit-appearance:none;-moz-appearance:none;
  background-image:url("data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%237f90ad' stroke-width='2.2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E");
  background-repeat:no-repeat;background-position:right 8px center;background-size:13px 13px;
}
.fld select:focus{border-color:#8fa3ff;box-shadow:0 0 0 2px rgba(65,105,246,.12)}

/* 权限遮罩 */
.placeholder-mask{
  position:fixed;inset:0;background:rgba(23,33,66,.45);z-index:140;
  display:none;align-items:center;justify-content:center;padding:20px;
}
.placeholder-mask.show{display:flex}
.placeholder-box{
  width:720px;max-width:100%;background:#fff;border-radius:12px;padding:28px 28px 24px;
  box-shadow:0 20px 60px rgba(20,32,70,.3);text-align:center;
}
.placeholder-box .pb-ico{
  width:54px;height:54px;border-radius:14px;background:var(--system-blue-weak);
  display:grid;place-items:center;font-size:24px;margin:0 auto 14px;
}
.placeholder-box h3{font-size:17px;color:#1f2a44;margin-bottom:8px}
.placeholder-box p{font-size:12.5px;color:var(--muted);margin-bottom:6px;line-height:1.7}

/* 全局消息 */
.toast{
  position:fixed;top:56px;left:50%;transform:translateX(-50%) translateY(-12px);
  z-index:200;background:#2b3245;color:#fff;font-size:13px;
  padding:9px 18px;border-radius:7px;box-shadow:0 8px 24px rgba(20,32,70,.25);
  opacity:0;pointer-events:none;transition:all .2s;display:flex;align-items:center;gap:7px;
  max-width:80vw;
}
.toast.show{opacity:1;transform:translateX(-50%) translateY(0)}
.toast.err{background:#c0392b}
.toast.ok{background:#1e8e4a}

/* ===== 分层结构：主 Tab + 页面级大弹窗编辑（策略列表常驻，弹窗覆盖其上） ===== */
.main-tabbar{background:#fff;border:1px solid #e4edf8;border-radius:var(--radius);padding:0 6px;gap:4px}
.main-tabbar button{padding:12px 22px;font-size:13.5px}
#ptab-config,#ptab-scene,#ptab-ops{display:flex;flex-direction:column;gap:16px}
#configListView{display:flex;flex-direction:column;gap:16px}

/* 列表行：点行 / 「编辑」→ 弹出策略编辑弹窗 */
.mt-row.clickable{cursor:pointer}
.mt-row.clickable:hover{background:#f8fbff}
.mt-row.sel{background:#f3f8ff;box-shadow:inset 3px 0 0 var(--system-blue)}
.mt-row.sel .q{color:var(--system-blue);font-weight:600}
.mt-row.sel:hover{background:#eff5ff}
.mt-row.flash{background:#f2fbf4;transition:background .2s}

/* ===== 策略编辑弹窗：页面级大弹窗（≈900px / 88vw · max-height 86vh · 内部滚动 · 底部钉住） ===== */
.modal-mask.stg-mask{z-index:120;padding:24px 18px}
.stg-modal{
  width:900px;max-width:88vw;max-height:86vh;
  display:flex;flex-direction:column;background:#fff;border-radius:12px;
  box-shadow:0 24px 64px rgba(20,32,70,.32);overflow:hidden;
  animation:pop .18s ease;
}
.stg-head{
  flex:0 0 auto;display:flex;align-items:center;gap:8px 12px;flex-wrap:wrap;
  padding:13px 18px;border-bottom:1px solid var(--content-line);background:#fff;
}
.ed-tt{font-size:15px;font-weight:700;color:#1f2a44;display:inline-flex;align-items:center;gap:7px;white-space:nowrap}
.ed-tt::before{content:"";width:3px;height:15px;border-radius:2px;background:var(--system-blue)}
.stg-head .ed-tt{flex:1 1 auto}
.stg-close{
  margin-left:auto;width:30px;height:30px;flex:0 0 auto;border:0;border-radius:6px;
  background:#f2f4f8;color:#667085;font-size:18px;line-height:1;
}
.stg-close:hover{background:#e6eaf2;color:#1f2a44}
.stg-body{
  flex:1 1 auto;min-height:0;overflow-y:auto;overscroll-behavior:contain;
  padding:14px 18px 16px;display:flex;flex-direction:column;gap:12px;background:#fbfdff;
}
.stg-foot{
  flex:0 0 auto;z-index:1;
  display:flex;align-items:center;gap:8px;flex-wrap:wrap;
  padding:11px 18px;background:#fff;border-top:1px solid var(--content-line);
  box-shadow:0 -6px 18px rgba(31,58,122,.06);
}
.stg-foot .sp{flex:1}
.stg-foot .btn-primary{height:32px;padding:0 18px;font-size:12.5px;box-shadow:0 2px 6px rgba(65,105,246,.22)}
.stg-foot .btn-ghost{height:32px;padding:0 14px;font-size:12.5px}
.stg-foot .mini-btn{height:32px;padding:0 14px;font-size:12.5px}
.ed-sec{
  background:#fff;border:1px solid #e4edf8;border-radius:10px;
  padding:14px 16px;display:flex;flex-direction:column;gap:11px;
  box-shadow:var(--shadow-card);
}
.ed-sec-head{display:flex;align-items:flex-start;gap:10px;flex-wrap:wrap}
.ed-sec-head .p-title{font-size:15px;font-weight:700;color:#1f2a44}
.ed-sec-head .p-act{margin-left:auto;display:flex;gap:6px;align-items:center}
.ed-sec.collapsed > *:not(.ed-sec-head){display:none}
.ed-form{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:0 12px}
.ed-form .fld{margin:6px 0 4px}
.ed-form .fld.full{grid-column:span 2}
.card-note{
  font-size:12px;color:#5b6472;line-height:1.75;
  background:#f8fbff;border:1px dashed #dfe8f7;border-radius:8px;padding:9px 12px;
}
.card-note .ico{color:var(--system-blue);font-weight:700;margin-right:5px}
.card-note b{color:#1f2a44}
.ev-subblock{display:flex;flex-direction:column;gap:8px;margin-top:2px}
.ev-subblock .sub-title{font-size:13px;font-weight:700;color:#1f2a44;display:flex;align-items:center;gap:7px;flex-wrap:wrap}
.ev-subblock .sub-title::before{content:"";width:3px;height:12px;border-radius:2px;background:#c7d6f2}
.ev-subblock .sub-sub{font-size:11.5px;color:#9aa6bd;font-weight:400}
@media(max-width:1100px){
  .ed-form{grid-template-columns:repeat(2,minmax(0,1fr))}
  .ed-form .fld.full{grid-column:1/-1}
}

/* 场景字典（场景管理） */
.scene-link{font-size:11.5px;color:var(--system-blue);text-decoration:none;border-bottom:1px dashed #9dbcf5;white-space:nowrap;cursor:pointer}
.scene-link:hover{border-bottom-style:solid;color:#335be8}
#sceneTable .mt-row .q{white-space:normal;line-height:1.45}
#sceneTable .num{font-size:14px}
.scene-desc{font-size:11px;color:#98a2b3;margin-top:2px;line-height:1.45;white-space:normal}
.scene-form{display:grid;grid-template-columns:minmax(0,.9fr) minmax(0,1.5fr) auto;gap:10px;align-items:end;border:1px solid #e6edf8;border-radius:8px;padding:12px;background:#fbfdff}
.scene-form .fld{margin:0}
.scene-form-act{display:flex;gap:8px}
.tag-preset{font-size:10px;font-weight:600;color:#8a6116;background:#fdf3e1;border:1px solid #f5e3bd;border-radius:4px;padding:0 5px;margin-left:5px;white-space:nowrap;vertical-align:1px;display:inline-block}
.act-wrap .tag-preset{margin-left:0;align-self:center;vertical-align:middle}
@media(max-width:900px){.scene-form{grid-template-columns:1fr}}

/* ===== 研发说明入口（九页统一：头部胶囊按钮 + 右侧抽屉） ===== */
.dev-entry{flex-shrink:0;display:inline-flex;align-items:center;gap:7px;height:30px;padding:0 13px 0 5px;border:1px solid #d7e0f0;border-radius:999px;background:#fff;color:#667085;font-size:12px;line-height:1;white-space:nowrap;cursor:pointer;transition:border-color .15s,color .15s,background .15s}
.dev-entry:hover{border-color:#b9c9ff;background:var(--system-blue-weak);color:var(--system-blue)}
.dev-entry:hover .de-q{background:var(--system-blue);color:#fff}
.dev-entry:focus-visible{outline:2px solid #cdd9ff;outline-offset:1px}
.dev-entry .de-q{width:20px;height:20px;border-radius:50%;flex:0 0 auto;background:var(--system-blue-weak);color:var(--system-blue);font-size:12px;font-weight:700;line-height:20px;text-align:center;transition:background .15s,color .15s}
.mono{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:11.5px;letter-spacing:.2px}
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
.dev-ov h4{margin:0 0 6px;font-size:13px;color:#1f5fd4}
.dev-ov p{margin:0;line-height:1.75;color:#475467;font-size:12.5px}
.ov-grid{display:grid;grid-template-columns:1fr;gap:12px}
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
@media print{.dev-card{break-inside:avoid}.demo-limit{border:1px solid #ccc}}
</style>
