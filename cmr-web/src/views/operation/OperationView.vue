<template>
  <div class="p7-root">
    <div class="main"><div class="content">
      <section class="module-intro"><div><h2>结果运营</h2><p>把检索结果不准的地方反馈回去，把运营推荐词用于检索台联想。</p></div><button class="dev-entry" type="button" @click="openDev"><span class="de-q" aria-hidden="true">?</span>研发说明</button></section>
      <div class="flow">
        <section class="panel flow-block">
          <div class="blk-head">
            <div class="blk-title"><h3>反馈</h3><span class="sec-sub">登记 → 处理 → 闭环</span></div>
            <div class="panel-act">
              <select v-model="fbTypeFilter" class="p7-select" @change="loadFeedbacks(1)"><option value="">全部类型</option><option v-for="item in fbTypeDict" :key="item.alias" :value="item.alias">{{ item.label }}</option></select>
              <select v-model="fbStatusFilter" class="p7-select" @change="loadFeedbacks(1)"><option value="">全部状态</option><option v-for="item in fbStatusDict" :key="item.alias" :value="item.alias">{{ item.label }}</option></select>
              <button class="btn-primary" type="button" @click="openRegister">登记反馈</button>
            </div>
          </div>
          <div class="cov-sum"><span><b>{{ stats.total || fbTotal }}</b> 条反馈单</span><span><b>{{ pendingCount }}</b> 条待处理</span><span><b>{{ processingCount }}</b> 条处理中</span><span><b>{{ closedCount }}</b> 条已闭环</span></div>
          <div class="dtable fb-table"><table class="dt"><thead><tr><th>反馈单</th><th>类型</th><th>去向</th><th>状态</th><th>操作</th></tr></thead><tbody>
            <tr v-if="fbLoading"><td colspan="5" class="table-empty">加载中…</td></tr>
            <tr v-else-if="!feedbacks.length"><td colspan="5" class="table-empty">暂无反馈单</td></tr>
            <tr v-for="row in feedbacks" v-else :key="row.id">
              <td class="tid">{{ row.bizNo }}<div class="fb-note">{{ row.searchBizNo || '—' }} · {{ row.description || '无补充说明' }}</div></td>
              <td>{{ dictLabel(fbTypeDict,row.type) }}</td><td><router-link v-if="row.target && row.status!=='pending'" class="row-link" :to="feedbackTargetRoute(row.target)">{{ dictLabel(fbTargetDict,row.target) }} →</router-link><template v-else>{{ dictLabel(fbTargetDict,row.target) }}</template></td>
              <td><span class="badge" :class="feedbackStatusClass(row.status)">{{ dictLabel(fbStatusDict,row.status) }}</span></td>
              <td><button class="btn-ghost btn-xs" type="button" @click="openFeedbackDetail(row)">详情</button><button v-if="row.status==='pending'" class="btn-ghost btn-xs" type="button" @click="openProcess(row)">开始处理</button><router-link v-if="row.status==='processing' && row.target" class="btn-ghost btn-xs" :to="feedbackTargetRoute(row.target)">去处理</router-link><button v-if="row.status!=='closed'" class="btn-ghost btn-xs" type="button" @click="openClose(row)">闭环</button><button v-if="row.status==='closed'" class="btn-ghost btn-xs" type="button" disabled>已闭环</button></td>
            </tr>
          </tbody></table></div>
          <div class="pager-bar"><span class="pager-total">共 {{ fbTotal }} 条</span><button class="pager-btn" type="button" :disabled="fbPage<=1" @click="loadFeedbacks(fbPage-1)">上一页</button><button class="pager-num is-cur" type="button">{{ fbPage }}</button><span class="pager-gap">/</span><span class="pager-total">{{ fbPageCount }}</span><button class="pager-btn" type="button" :disabled="fbPage>=fbPageCount" @click="loadFeedbacks(fbPage+1)">下一页</button></div>
        </section>

        <section class="panel flow-block">
          <div class="blk-head"><div class="blk-title"><h3>运营推荐词</h3><span class="sec-sub">运营维护，推送到检索台联想</span></div><div class="panel-act"><button class="btn-ghost" type="button" @click="openWord()">＋ 添加推荐词</button></div></div>
          <div class="cov-sum"><span>开关即时生效</span><span>开 = 进入检索联想</span><span>关 = 停推不进入</span></div>
          <div class="dtable rec-table"><table class="dt"><thead><tr><th class="col-rank">序号</th><th>推荐词</th><th>场景</th><th>状态</th><th class="col-sw">开关</th><th class="col-act">操作</th></tr></thead><tbody>
            <tr v-if="wordLoading"><td colspan="6" class="rec-empty">加载中…</td></tr>
            <tr v-else-if="!words.length"><td colspan="6" class="rec-empty">暂无推荐词</td></tr>
            <tr v-for="(row,index) in words" v-else :key="row.id">
              <td class="mono">{{ index+1 }}</td><td class="tid">{{ row.word }}</td><td>{{ sceneLabel(row.scene) }}</td>
              <td><span class="rec-state" :class="row.enabled?'on':'off'">{{ row.enabled?'生效·进入联想':'停推·不进入联想' }}</span></td>
              <td><button class="switch" :class="{on:row.enabled}" type="button" role="switch" :aria-checked="row.enabled" @click="toggleWord(row)"><span class="knob"></span></button></td>
              <td><button class="rec-del" type="button" @click="askDeleteWord(row)">删除</button><button class="rec-del" type="button" @click="openWord(row)">编辑</button></td>
            </tr>
          </tbody></table></div>
        </section>
      </div>
    </div></div>
    <div class="toast" :class="{show:toastVisible,ok:toastType==='ok',err:toastType==='err'}">{{ toastMessage }}</div>

    <!-- 登记反馈 -->
    <div class="modal-mask" :class="{show:registerVisible}" @click.self="registerVisible=false">
      <div class="modal-box" style="width:680px">
        <div class="modal-head"><h3>登记反馈</h3><button class="modal-close" type="button" @click="registerVisible=false">×</button></div>
        <div class="modal-body">
          <div class="mb-tip">先从检索历史选择一条记录，再填写反馈类型与说明。</div>
          <input v-model="historyKeyword" class="hist-search" type="search" placeholder="搜索查询内容或检索编号" @input="scheduleHistorySearch" @keyup.enter="loadHistoryNow">
          <div class="pick-list">
            <label v-for="row in histories" :key="row.id" class="pick-row" :class="{ 'is-pick': selectedHistory?.id===row.id }">
              <input v-model="selectedHistoryId" class="pick-ck" type="radio" :value="row.id" @change="selectedHistoryRecord=row">
              <span class="pick-main"><span class="pick-q">{{ row.queryText || '（文件语义检索）' }}</span><span class="pick-sub">{{ row.createTime }} · 结果 {{ row.itemCount ?? 0 }} 条 · {{ row.bizNo }}</span></span>
            </label>
            <div v-if="historyLoading" class="pick-empty">检索历史加载中…</div><div v-else-if="!histories.length" class="pick-empty">没有匹配的检索记录</div>
          </div>
          <div class="pager"><span>共 {{ historyTotal }} 条</span><button class="pg-num" type="button" :disabled="historyPage<=1" @click="loadHistoryCandidates(historyPage-1)">上一页</button><span>{{ historyPage }} / {{ historyPageCount }}</span><button class="pg-num" type="button" :disabled="historyPage>=historyPageCount" @click="loadHistoryCandidates(historyPage+1)">下一页</button></div>
          <div v-if="selectedHistory" class="pick-chosen">已选择 <b>{{ selectedHistory.queryText || selectedHistory.bizNo }}</b></div>
          <div class="fb-step">反馈内容</div>
          <div class="p7-form-grid">
            <label class="fld">反馈类型<select v-model="registerForm.type"><option v-for="item in fbTypeDict" :key="item.alias" :value="item.alias">{{ item.label }}</option></select></label>
            <label class="fld">说明<textarea v-model="registerForm.description" rows="3" placeholder="说明问题、期望结果或来源差异"></textarea></label>
          </div>
        </div>
        <div class="modal-foot"><button class="btn-ghost" type="button" @click="registerVisible=false">取消</button><button class="btn-primary" type="button" :disabled="registerSubmitting" @click="submitRegister">{{ registerSubmitting?'提交中…':'提交登记' }}</button></div>
      </div>
    </div>

    <!-- 处理反馈 -->
    <div class="modal-mask" :class="{show:processVisible}" @click.self="processVisible=false">
      <div class="modal-box" style="width:460px"><div class="modal-head"><h3>开始处理反馈</h3><button class="modal-close" type="button" @click="processVisible=false">×</button></div>
        <div class="modal-body"><div class="mb-tip">选择处理去向后进入处理中。</div><label class="fld">处理去向<select v-model="processTarget"><option value="strategy">策略调优</option><option value="datasource">数据源补充</option><option value="backfill">数据回填</option></select></label></div>
        <div class="modal-foot"><button class="btn-ghost" type="button" @click="processVisible=false">取消</button><button class="btn-primary" type="button" :disabled="processing" @click="submitProcess">确认处理中</button></div>
      </div>
    </div>

    <!-- 闭环 -->
    <div class="modal-mask" :class="{show:closeVisible}" @click.self="closeVisible=false">
      <div class="modal-box" style="width:500px"><div class="modal-head"><h3>反馈闭环</h3><button class="modal-close" type="button" @click="closeVisible=false">×</button></div>
        <div class="modal-body"><div class="mb-tip">闭环后进入终态，不能再次修改。</div><label class="fld">处理结果说明<textarea v-model="closeNote" rows="3" placeholder="如：已调整安防场景权重 v3 / 已补充 xx 数据集"></textarea></label></div>
        <div class="modal-foot"><button class="btn-ghost" type="button" @click="closeVisible=false">取消</button><button class="btn-primary" type="button" :disabled="closing" @click="submitClose">闭环（终态）</button></div>
      </div>
    </div>

    <!-- 推荐词 -->
    <div class="modal-mask" :class="{show:wordVisible}" @click.self="wordVisible=false">
      <div class="modal-box" style="width:460px"><div class="modal-head"><h3>{{ wordForm.id?'编辑推荐词':'添加推荐词' }}</h3><button class="modal-close" type="button" @click="wordVisible=false">×</button></div>
        <div class="modal-body p7-form-grid"><label class="fld">推荐词<input v-model.trim="wordForm.word" maxlength="60"></label><label class="fld">场景<select v-model="wordForm.scene"><option value="all">全部场景</option><option v-for="item in scenes" :key="item.alias" :value="item.alias">{{ item.label }}</option></select></label><label class="fld">排序<input v-model.number="wordForm.sort" type="number" min="0"></label><label class="ed-switch"><input v-model="wordForm.enabled" type="checkbox"><span>启用</span><b>{{ wordForm.enabled?'开':'关' }}</b></label></div>
        <div class="modal-foot"><button class="btn-ghost" type="button" @click="wordVisible=false">取消</button><button class="btn-primary" type="button" :disabled="wordSubmitting" @click="submitWord">保存</button></div>
      </div>
    </div>
    <!-- 反馈详情 -->
    <div class="modal-mask" :class="{show:feedbackDetailVisible}" @click.self="feedbackDetailVisible=false">
      <div class="modal-box" style="width:620px"><div class="modal-head"><h3>反馈详情</h3><button class="modal-close" type="button" @click="feedbackDetailVisible=false">×</button></div>
        <div v-if="feedbackDetail" class="modal-body">
          <div class="detail-grid"><div><span>反馈单</span><b>{{ feedbackDetail.bizNo }}</b></div><div><span>关联检索</span><b>{{ feedbackDetail.searchBizNo || feedbackDetail.searchId }}</b></div><div><span>类型</span><b>{{ dictLabel(fbTypeDict,feedbackDetail.type) }}</b></div><div><span>状态</span><b>{{ dictLabel(fbStatusDict,feedbackDetail.status) }}</b></div><div><span>处理去向</span><b>{{ dictLabel(fbTargetDict,feedbackDetail.target) }}</b></div><div><span>创建时间</span><b>{{ feedbackDetail.createTime || '—' }}</b></div><div><span>更新时间</span><b>{{ feedbackDetail.updateTime || '—' }}</b></div></div>
          <div class="detail-block"><span>反馈说明</span><p>{{ feedbackDetail.description || '无补充说明' }}</p></div><div class="detail-block"><span>处理结果</span><p>{{ feedbackDetail.resultNote || '尚未形成处理结果' }}</p></div>
        </div>
        <div class="modal-foot"><button class="btn-ghost" type="button" @click="feedbackDetailVisible=false">关闭</button></div>
      </div>
    </div>

    <!-- 删除推荐词确认 -->
    <div class="modal-mask" :class="{show:deleteWordVisible}" @click.self="deleteWordVisible=false">
      <div class="modal-box" style="width:420px"><div class="modal-head"><h3>删除推荐词</h3><button class="modal-close" type="button" @click="deleteWordVisible=false">×</button></div>
        <div class="modal-body"><div class="mb-tip">确认删除推荐词「<b>{{ deleteWordRowData?.word }}</b>」？删除后不再进入检索联想。</div></div>
        <div class="modal-foot"><button class="btn-ghost" type="button" @click="deleteWordVisible=false">取消</button><button class="btn-primary" type="button" :disabled="deletingWord" @click="confirmDeleteWord">{{ deletingWord?'删除中…':'确认删除' }}</button></div>
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
      <div class="panel-head">
        <div class="sec-head">
          <span class="sec-title">研发说明 · 模块收敛与覆盖</span>
          <span class="sec-sub">14 个三级模块 → 2 个核心业务块</span>
        </div>
        <span class="badge gray">研发视角</span>
      </div>
      <p class="research-lead">
        页面正文只保留 2 个业务块与主操作（反馈：登记反馈；运营推荐词：添加推荐词与逐词开关），<b>模块编号与 F-CR 编号只在本说明中出现</b>。
        数据流、输入输出、价值句、来源句、块底口径与页面总结统一在本说明的「数据口径」段，正文只留控件、列表、状态与数据数字。
      </p>
      <div class="cover-note">
        <div class="cn-title">数据口径 · 逐条归档</div>
        <ul>
          <li><b>页面数据流</b>：输入 = 反馈：检索历史 query（运营从分页列表选一条登记）+ 用户上报 + 零结果统计；运营推荐词：运营手动维护的词表（添加 / 删除 / 开关）。输出 = 反馈 → 策略调整 / 数据修正；运营推荐词 → 检索台联想。</li>
          <li><b>页面总结</b>：运营从检索历史选 query 登记结果问题、形成反馈单后处理（反馈）→ 运营维护推荐词、直达检索台联想（运营推荐词）。</li>
          <li><b>反馈 · 口径</b>：运营从检索历史选 query 登记「结果有问题」，形成反馈单，处理后改策略 / 改数据。登记时先在弹窗的检索历史分页列表里选一条 query（每页 10 条，可按 query 搜索，页码与上一页 / 下一页切换），该条带时间 / 结果数，再填反馈类型（结果不准 / 排序差 / 来源标错 / 零结果）+ 说明，来源标注「检索历史 · query」。</li>
          <li><b>反馈 · 来源</b>：登记时记录来源（检索历史 / 用户上报 / 零结果），列表不单列「来源」列；系统统计仅作运营后台参考。</li>
          <li><b>反馈 · 价值与输入输出</b>：反馈单 → 运营处理 → 改策略 / 改数据 → 下次结果变好。输入 = 检索历史 query + 用户上报 + 零结果统计 → 输出 = 策略调整 / 数据修正。</li>
          <li><b>反馈 · 块底口径</b>：反馈单进入「待处理」，行内「处理」一次完成决定、执行单与核验；零结果 / 无点击 / 复搜的系统统计仅作运营后台参考。处理完即改策略或改数据，下一轮结果变好。</li>
          <li><b>反馈 · 数据数字</b>：系统统计仅作运营后台参考。</li>
          <li><b>运营推荐词 · 口径</b>：来源 = 运营手动维护（添加 / 删除 / 开关）；近 7 天搜索次数仅作参考；发布 = 每个推荐词的开关即时生效：开 = 生效进入联想，关 = 停推不进入。</li>
          <li><b>运营推荐词 · 数据口径</b>：词表来自运营维护；近 7 天搜索次数仅作参考。</li>
        </ul>
      </div>
      <p class="research-lead">
        <b>数据口径补充</b>：反馈按「检索历史选 query → 登记 → 处理 → 改策略 / 改数据」一条链路承载，检索历史在弹窗内分页呈现（每页 10 条 + 搜索）。
      </p>
      <div class="cov-sum">
        <span><b>2 块</b> 覆盖 5 个模块</span>
        <span><b>导出</b>仅入口，由接口对接下游</span>
        <span>登记反馈 从检索历史分页选一条 query，一次完成</span>
      </div>
      <div class="dtable">
        <table class="dt cov-tb">
          <thead><tr><th>模块</th><th>F-CR 范围</th><th>覆盖方式</th></tr></thead>
          <tbody>
            <tr>
              <td class="tid">结果展示 / 导出</td>
              <td class="mono">F-CR-162~164</td>
              <td><span class="cov-tag on">导出：仅入口</span><span class="cov-desc">结果按条目卡片展示；导出保留入口，由接口对接下游</span></td>
            </tr>
            <tr>
              <td class="tid">反馈登记归类</td>
              <td class="mono">F-CR-176~179</td>
              <td><span class="cov-tag on">由「反馈」块覆盖</span><span class="cov-desc">主按钮「登记反馈」：从检索历史分页列表选一条 query，再登记反馈类型与说明，一次完成归类与去向登记</span></td>
            </tr>
            <tr>
              <td class="tid">运营反馈处理跟踪</td>
              <td class="mono">F-CR-180~183</td>
              <td><span class="cov-tag on">由「反馈」块覆盖</span><span class="cov-desc">行内「处理」一次完成处理决定、执行单与核验</span></td>
            </tr>
            <tr>
              <td class="tid">零结果运营治理</td>
              <td class="mono">F-CR-199~202</td>
              <td><span class="cov-tag on">由「反馈」块覆盖</span><span class="cov-desc">系统统计仅作运营后台参考；运营从检索历史分页列表选到零结果 query 后登记「零结果」，与用户上报走同一「登记 / 处理」路径</span></td>
            </tr>
            <tr>
              <td class="tid">热词运营方案管理</td>
              <td class="mono">F-CR-203~205</td>
              <td><span class="cov-tag on">由「运营推荐词」块覆盖</span><span class="cov-desc">推荐词列表 + 逐词开关：开关即改即生效，添加 / 删除由运营维护</span></td>
            </tr>
            <tr>
              <td class="tid">运营改进跟踪</td>
              <td class="mono">F-CR-213~216</td>
              <td><span class="cov-tag on">由「反馈」块覆盖</span><span class="cov-desc">随行内「处理」登记整改问题</span></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="cover-note">
        <div class="cn-title">运营推荐词 ↔ COSMIC 检索热词运营方案管理（1 条映射）</div>
        <ul>
          <li><b>确认方案生效状态</b> → 开关（开 = 生效进入联想 / 关 = 停推）</li>
        </ul>
      </div>
      <div class="cover-note">
        <div class="cn-title">过程覆盖说明（正文不出现模块编号与 F-CR）</div>
        <ul>
          <li><b>反馈选 query</b>：弹窗内检索历史分页呈现，每页 10 条、可按 query 搜索、页码与上一页 / 下一页切换；单选一条后展开表单，提交生成反馈单，来源标注「检索历史 · query」。</li>
          <li><b>反馈口径</b>：主路径是运营从检索历史选一条 query 登记「结果有问题」（反馈类型 + 说明），来源标注「检索历史 · query」；系统统计仅作运营后台参考。</li>
        </ul>
      </div>
      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,reactive,ref} from 'vue'
import{closeFeedback,createFeedback,createWord,feedbackStats,pageFeedbacks,pageHistory,pageWords,processFeedback,removeWord,updateWord}from'../../api'
import type{DictItem,FeedbackStatsVO,FeedbackVO,HistoryVO,WordVO}from'../../api/types'
import{useDictStore}from'../../stores/dict'
const dictStore=useDictStore()
const feedbacks=ref<FeedbackVO[]>([]);const fbLoading=ref(false);const fbTotal=ref(0);const fbPage=ref(1);const fbPageSize=10
const fbTypeFilter=ref('');const fbStatusFilter=ref('');const fbTypeDict=ref<DictItem[]>([]);const fbStatusDict=ref<DictItem[]>([]);const fbTargetDict=ref<DictItem[]>([])
const words=ref<WordVO[]>([]);const wordLoading=ref(false);const scenes=ref<DictItem[]>([])
const histories=ref<HistoryVO[]>([]);const historyKeyword=ref('');const historyPage=ref(1);const historyPageSize=10;const historyTotal=ref(0);const historyLoading=ref(false);const selectedHistoryId=ref<number|null>(null);const selectedHistoryRecord=ref<HistoryVO|null>(null);const selectedHistory=computed(()=>selectedHistoryRecord.value);const historyPageCount=computed(()=>Math.max(1,Math.ceil(historyTotal.value/historyPageSize)))
const registerVisible=ref(false);const registerSubmitting=ref(false);const registerForm=reactive({type:'inaccurate',description:''})
const processVisible=ref(false);const processing=ref(false);const processRow=ref<FeedbackVO|null>(null);const processTarget=ref('strategy')
const closeVisible=ref(false);const closing=ref(false);const closeRow=ref<FeedbackVO|null>(null);const closeNote=ref('')
const feedbackDetailVisible=ref(false);const feedbackDetail=ref<FeedbackVO|null>(null)
const deleteWordVisible=ref(false);const deleteWordRowData=ref<WordVO|null>(null);const deletingWord=ref(false)
const wordVisible=ref(false);const wordSubmitting=ref(false);const wordForm=reactive({id:0,word:'',scene:'all',enabled:true,sort:0})
const devOpen=ref(false);const toastVisible=ref(false);const toastMessage=ref('');const toastType=ref<'info'|'ok'|'err'>('info');let toastTimer:ReturnType<typeof setTimeout>|undefined;let historySearchTimer:ReturnType<typeof setTimeout>|undefined
const fbPageCount=computed(()=>Math.max(1,Math.ceil(fbTotal.value/fbPageSize)));const stats=ref<FeedbackStatsVO>({total:0,pending:0,processing:0,closed:0});const pendingCount=computed(()=>stats.value.pending);const processingCount=computed(()=>stats.value.processing);const closedCount=computed(()=>stats.value.closed)
onMounted(async()=>{[fbTypeDict.value,fbStatusDict.value,fbTargetDict.value,scenes.value]=await Promise.all([dictStore.ensure('cmr_feedback_type'),dictStore.ensure('cmr_feedback_status'),dictStore.ensure('cmr_feedback_target'),dictStore.ensure('cmr_scene')]);await Promise.all([loadFeedbacks(1),loadStats(),loadWords()])})
onBeforeUnmount(()=>{clearTimeout(toastTimer);clearTimeout(historySearchTimer)})
function dictLabel(dict:DictItem[],alias?:string|null){return dict.find(x=>x.alias===alias)?.label||alias||'—'}
function sceneLabel(alias:string){return alias==='all'?'全部场景':scenes.value.find(x=>x.alias===alias)?.label||alias}
function feedbackTargetRoute(target:string){return target==='strategy'?'/strategies':'/library'}
function feedbackStatusClass(status:string){return({pending:'wait',processing:'run',closed:'ok'}as Record<string,string>)[status]||'gray'}
async function loadStats(){try{stats.value=await feedbackStats()}catch(e){/* 统计失败时保留当前值，列表错误另行提示 */}}
async function loadFeedbacks(page:number){fbPage.value=page;fbLoading.value=true;try{const data=await pageFeedbacks({pageNum:page,pageSize:fbPageSize,type:fbTypeFilter.value,status:fbStatusFilter.value});feedbacks.value=data.records;fbTotal.value=data.total}catch(e){showToast((e as Error).message,'err')}finally{fbLoading.value=false}}
async function openRegister(){registerVisible.value=true;selectedHistoryId.value=null;selectedHistoryRecord.value=null;registerForm.description='';historyKeyword.value='';await loadHistoryCandidates(1)}
async function loadHistoryCandidates(page:number){historyPage.value=page;historyLoading.value=true;try{const data=await pageHistory({pageNum:page,pageSize:historyPageSize,keyword:historyKeyword.value.trim()});histories.value=data.records;historyTotal.value=data.total}catch(e){showToast((e as Error).message,'err')}finally{historyLoading.value=false}}
function scheduleHistorySearch(){clearTimeout(historySearchTimer);historySearchTimer=setTimeout(()=>void loadHistoryCandidates(1),350)}
function loadHistoryNow(){clearTimeout(historySearchTimer);void loadHistoryCandidates(1)}
async function submitRegister(){if(!selectedHistoryId.value){showToast('请先选择一条检索历史','err');return}registerSubmitting.value=true;try{await createFeedback({searchId:selectedHistoryId.value,type:registerForm.type,description:registerForm.description});registerVisible.value=false;showToast('反馈已登记','ok');await Promise.all([loadFeedbacks(1),loadStats()])}catch(e){showToast((e as Error).message,'err')}finally{registerSubmitting.value=false}}
function openFeedbackDetail(row:FeedbackVO){feedbackDetail.value=row;feedbackDetailVisible.value=true}
function openProcess(row:FeedbackVO){processRow.value=row;processTarget.value='strategy';processVisible.value=true}
async function submitProcess(){if(!processRow.value)return;processing.value=true;try{await processFeedback(processRow.value.id,processTarget.value);processVisible.value=false;showToast('已进入处理中','ok');await Promise.all([loadFeedbacks(fbPage.value),loadStats()])}catch(e){showToast((e as Error).message,'err')}finally{processing.value=false}}
function openClose(row:FeedbackVO){closeRow.value=row;closeNote.value='';closeVisible.value=true}
async function submitClose(){if(!closeRow.value)return;closing.value=true;try{await closeFeedback(closeRow.value.id,closeNote.value||undefined);closeVisible.value=false;showToast('反馈已闭环','ok');await Promise.all([loadFeedbacks(fbPage.value),loadStats()])}catch(e){showToast((e as Error).message,'err')}finally{closing.value=false}}
async function loadWords(){wordLoading.value=true;try{const data=await pageWords({pageNum:1,pageSize:100});words.value=data.records}catch(e){showToast((e as Error).message,'err')}finally{wordLoading.value=false}}
function openWord(row?:WordVO){wordForm.id=row?.id||0;wordForm.word=row?.word||'';wordForm.scene=row?.scene||'all';wordForm.enabled=row?.enabled??true;wordForm.sort=row?.sort||0;wordVisible.value=true}
async function submitWord(){if(!wordForm.word.trim()){showToast('请填写推荐词','err');return}wordSubmitting.value=true;try{const body={word:wordForm.word.trim(),scene:wordForm.scene,enabled:wordForm.enabled,sort:wordForm.sort};if(wordForm.id)await updateWord(wordForm.id,body);else await createWord(body);wordVisible.value=false;showToast('推荐词已保存','ok');await loadWords()}catch(e){showToast((e as Error).message,'err')}finally{wordSubmitting.value=false}}
async function toggleWord(row:WordVO){try{await updateWord(row.id,{word:row.word,scene:row.scene,enabled:!row.enabled,sort:row.sort});row.enabled=!row.enabled;showToast(row.enabled?'已启用':'已停用','ok')}catch(e){showToast((e as Error).message,'err')}}
function askDeleteWord(row:WordVO){deleteWordRowData.value=row;deleteWordVisible.value=true}
async function confirmDeleteWord(){if(!deleteWordRowData.value)return;deletingWord.value=true;try{await removeWord(deleteWordRowData.value.id);deleteWordVisible.value=false;showToast('推荐词已删除','ok');await loadWords()}catch(e){showToast((e as Error).message,'err')}finally{deletingWord.value=false}}
function showToast(message:string,type:'info'|'ok'|'err'='info'){toastMessage.value=message;toastType.value=type;toastVisible.value=true;clearTimeout(toastTimer);toastTimer=setTimeout(()=>toastVisible.value=false,2600)}
function openDev(){devOpen.value=true}function closeDev(){devOpen.value=false}
</script>

<style scoped>
.p7-select{height:30px;border:1px solid #d9e7f8;border-radius:6px;padding:0 9px;background:#fff;color:#344054}
.table-empty{text-align:center;color:#9aa6bd;padding:20px!important}
.p7-form-grid{display:flex;flex-direction:column;gap:12px;margin-top:12px}
.p7-form-grid textarea{width:100%;resize:vertical;border:1px solid #cfd8e8;border-radius:6px;padding:8px;font:inherit;color:#273244}
:root{
  /* 清爽冷白蓝 token（与检索台基准一致） */
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
button[disabled]{cursor:default}
a{color:inherit;text-decoration:none}
::selection{background:rgba(65,105,246,.16)}
[hidden]{display:none !important}

/* ============ 左侧全局导航（壳层，未改） ============ */
.sidebar{
  width:220px;flex-shrink:0;
  background:#fff;border-right:1px solid var(--content-line);
  display:flex;flex-direction:column;
  height:100vh;position:sticky;top:0;
}
.nav-scroll{flex:1;overflow-y:auto;padding:10px 12px}
.nav-item{
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
.nav-divider{height:1px;background:var(--content-line);margin:8px 10px}

/* ============ 主区 ============ */
.main{flex:1;min-width:0;display:flex;flex-direction:column;height:100vh;overflow-y:auto}
.content{padding:20px 24px 32px;display:flex;flex-direction:column;gap:14px;max-width:1280px;width:100%;margin:0 auto}

/* 页面定位 */
.module-intro{
  display:flex;align-items:flex-start;justify-content:space-between;gap:16px;flex-wrap:wrap;
  padding:18px 20px;
  border:1px solid #e4edf8;
  border-radius:10px;
  background:#fff;
}
.module-intro h2{font-size:20px;font-weight:700;color:#111827}
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
.dev-drawer .dtable{overflow-x:auto}

/* ============ 面板 ============ */
.panel{padding:16px 18px;border:1px solid #e4edf8;border-radius:var(--radius);background:#fff;display:flex;flex-direction:column;gap:12px}
.panel-head{display:flex;align-items:flex-start;gap:10px;justify-content:space-between;flex-wrap:wrap}
.sec-head{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.sec-head .sec-title{font-size:15px;font-weight:700;color:#1f2a44}
.sec-head .sec-sub{font-size:12px;color:#9aa6bd}
.sec-head::before{content:"";width:3px;height:14px;border-radius:2px;background:var(--system-blue)}
.panel-act{display:flex;gap:8px;align-items:center;flex-wrap:wrap}

/* 按钮体系 */
.btn-primary{
  height:34px;padding:0 20px;border:0;border-radius:6px;
  background:var(--system-blue);color:#fff;font-size:13px;font-weight:600;
  display:inline-flex;align-items:center;gap:7px;
  box-shadow:0 3px 8px rgba(65,105,246,.25);transition:all .15s;
}
.btn-primary:hover{background:#335be8;box-shadow:0 4px 12px rgba(65,105,246,.32)}
.btn-primary[disabled]{opacity:.5;box-shadow:none;background:#a8b6f7;cursor:default}
.btn-ghost{
  height:32px;padding:0 16px;border-radius:6px;border:1px solid #d7e0f0;
  background:#fff;color:#475467;font-size:12.5px;display:inline-flex;align-items:center;gap:6px;
}
.btn-ghost:hover{border-color:var(--system-blue);color:var(--system-blue)}
.btn-ghost[disabled]{opacity:.55;border-color:#e6edf8;color:#9aa6bd;background:#fafbfd}
.btn-xs{height:27px;padding:0 12px;font-size:12px}

/* 徽章 */
.badge{font-size:11px;padding:2px 8px;border-radius:10px;font-weight:600;display:inline-block;white-space:nowrap}
.badge.ok{background:#e8f7ec;color:var(--success)}
.badge.fail{background:#fdecec;color:var(--error)}
.badge.run{background:#e9f0ff;color:var(--system-blue)}
.badge.wait{background:#fff8e1;color:#c99a12}
.badge.gray{background:#f0f2f7;color:#8c98ad}

/* ============ 业务块 ============ */
.flow{display:flex;flex-direction:column;gap:16px}
.flow-block{padding:18px 20px;gap:14px}
.blk-head{display:flex;align-items:flex-start;justify-content:space-between;gap:14px;flex-wrap:wrap}
.blk-title{display:flex;align-items:center;gap:12px;min-width:0}
.blk-title h3{font-size:16px;font-weight:700;color:#1f2a44;line-height:1.4}
.blk-title .sec-sub{font-size:12px;color:#9aa6bd;font-weight:400;white-space:nowrap}

.fb-note{font-size:11px;color:#8c98ad;font-weight:400;margin-top:2px;line-height:1.5;white-space:normal}
.fb-table{overflow-x:auto}

/* 登记反馈：检索历史选择（分页） */
.fb-step{display:flex;align-items:center;gap:6px;font-size:12px;font-weight:700;color:#475467;margin:6px 0 7px}
.fb-step::before{content:"";width:3px;height:12px;border-radius:2px;background:var(--system-blue)}
.hist-search{
  width:100%;height:32px;border:1px solid #cfd8e8;border-radius:6px;padding:0 10px;
  font-size:12.5px;color:#273244;background:#fff;outline:none;margin-bottom:8px;
}
.hist-search:focus{border-color:#8fa3ff;box-shadow:0 0 0 2px rgba(65,105,246,.12)}
.pick-list{display:flex;flex-direction:column;border:1px solid #e6edf8;border-radius:8px;overflow:hidden;max-height:332px;overflow-y:auto}
.pick-row{display:flex;align-items:flex-start;gap:9px;padding:9px 12px;border-bottom:1px solid #eef2f8;background:#fff;cursor:pointer;min-width:0}
.pick-row:last-child{border-bottom:0}
.pick-row:hover{background:#f8fbff}
.pick-row.is-pick{background:#f4f8ff}
.pick-ck{width:14px;height:14px;margin:2px 0 0;accent-color:var(--system-blue);flex-shrink:0;cursor:pointer}
.pick-main{min-width:0;flex:1 1 auto}
.pick-q{display:block;font-size:12.5px;font-weight:600;color:#273244;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.pick-sub{display:block;font-size:11px;color:#9aa6bd;margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.pick-sub .zero{color:#c99a12;font-weight:700}
.pick-empty{padding:22px 12px;text-align:center;font-size:12px;color:#9aa6bd;background:#fff}
.detail-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.detail-grid div{border:1px solid #e6edf8;border-radius:7px;background:#fbfdff;padding:8px}.detail-grid span,.detail-block span{display:block;color:#98a2b3;font-size:10.5px;margin-bottom:3px}.detail-grid b{font-size:12px;color:#273244}.detail-block{margin-top:10px;border:1px solid #e6edf8;border-radius:7px;padding:9px;background:#fff}.detail-block p{margin:0;color:#344054;font-size:12px;line-height:1.7;white-space:pre-wrap}
.pager{display:flex;align-items:center;gap:6px;flex-wrap:wrap;margin-top:9px}
.pg-num{
  min-width:27px;height:27px;padding:0 6px;border:1px solid #d7e0f0;border-radius:6px;
  background:#fff;color:#475467;font-size:12px;font-variant-numeric:tabular-nums;
}
.pg-num:hover{border-color:var(--system-blue);color:var(--system-blue)}
.pg-num.is-cur{background:var(--system-blue);border-color:var(--system-blue);color:#fff;font-weight:700}
.pg-info{font-size:11.5px;color:#8c98ad;white-space:nowrap;font-variant-numeric:tabular-nums;margin-left:2px}
.fb-form-wrap{margin-top:14px;padding-top:12px;border-top:1px solid #f0f4fb}
.pick-chosen{font-size:12px;color:#667085;background:#f8fafd;border:1px solid #eef2f8;border-radius:6px;padding:7px 10px;margin-bottom:10px;line-height:1.6}
.pick-chosen b{color:#273244}

/* 表格 */
.dtable{width:100%;border:1px solid #eef2f8;border-radius:8px;overflow:hidden}
table.dt{width:100%;border-collapse:collapse;font-size:12.5px}
table.dt th{
  background:#f4f6fb;color:#7a8493;font-weight:600;font-size:11.5px;text-align:left;
  padding:9px 12px;border-bottom:1px solid #e6edf8;white-space:nowrap;
}
table.dt td{padding:9px 12px;border-bottom:1px solid #eef2f8;color:#344054;vertical-align:middle}
table.dt tbody tr:last-child td{border-bottom:0}
table.dt tbody tr:hover td{background:#f8fbff}
table.dt td.mono{font-variant-numeric:tabular-nums;color:#475467}
table.dt td.tid{font-weight:600;color:#273244;white-space:nowrap}

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

/* 运营推荐词 */
.rec-table .col-rank{width:58px}
.rec-table .col-sw{width:66px}
.rec-table .col-act{width:74px}
.rec-table th .th-sub{font-size:10.5px;color:#b3bccc;font-weight:400;margin-left:4px}
.rec-table td.rec-cnt{font-size:11px;color:#9aa6bd;font-variant-numeric:tabular-nums;white-space:nowrap}
.rec-state{font-size:12px;font-weight:600;white-space:nowrap}
.rec-state.on{color:#1e8e4a}
.rec-state.off{color:#98a2b3}
.rec-del{border:0;background:none;padding:0;font-size:12px;color:#98a2b3;cursor:pointer}
.rec-del:hover{color:#e5484d;text-decoration:underline}
.rec-empty{text-align:center;color:#9aa6bd;padding:20px}

/* 开关 */
.switch{
  width:34px;height:18px;border-radius:9px;border:0;padding:0;flex-shrink:0;
  background:#d9e0ec;position:relative;transition:background .15s;
}
.switch .knob{
  position:absolute;top:2px;left:2px;width:14px;height:14px;border-radius:50%;
  background:#fff;box-shadow:0 1px 2px rgba(20,32,70,.2);transition:left .15s;
}
.switch.on{background:var(--system-blue)}
.switch.on .knob{left:18px}
.switch:focus-visible{outline:2px solid #cdd9ff;outline-offset:2px}

/* 说明与留痕 */
.rule-note{
  font-size:11.5px;color:#7a8493;border-left:3px solid var(--content-line-strong);
  padding:4px 10px;background:var(--content-soft);border-radius:0 6px 6px 0;
}

/* 研发说明 */
.research-lead{font-size:12px;color:#667085;line-height:1.8}
.research-lead b{color:#344054}
.cov-sum{
  display:flex;gap:14px;flex-wrap:wrap;font-size:11.5px;color:#667085;
  background:#fff;border:1px solid #eef2f8;border-radius:7px;padding:8px 12px;
}
.cov-sum b{color:#344054;font-weight:700}
.cov-tb td{vertical-align:top;line-height:1.7}
.cov-tb td:first-child{white-space:nowrap}
.cov-tag{display:inline-block;font-size:11px;font-weight:700;padding:1px 7px;border-radius:4px;margin-right:6px}
.cov-tag.on{background:#e9f0ff;color:var(--system-blue)}
.cov-tag.off{background:#f0f2f7;color:#8c98ad}
.cov-desc{font-size:11.5px;color:#667085}
.cover-note{border:1px dashed #d9e4f5;background:#fff;border-radius:8px;padding:10px 12px}
.cover-note .cn-title{font-size:11.5px;font-weight:700;color:#475467;margin-bottom:6px}
.cover-note ul{margin:0;padding-left:16px}
.cover-note li{font-size:11.5px;color:#667085;line-height:1.8;margin:0}
.cover-note li b{color:#344054;font-weight:700}

/* 弹窗 */
.modal-mask{
  position:fixed;inset:0;background:rgba(23,33,66,.42);z-index:100;
  display:none;align-items:center;justify-content:center;padding:20px;
}
.modal-mask.show{display:flex}
.modal-box{
  width:720px;max-width:100%;background:#fff;border-radius:10px;
  box-shadow:0 16px 48px rgba(20,32,70,.28);overflow:hidden;animation:pop .18s ease;
}
@keyframes pop{from{transform:translateY(8px) scale(.98);opacity:0}to{transform:none;opacity:1}}
.modal-head{padding:16px 20px 0;display:flex;align-items:flex-start;justify-content:space-between}
.modal-head h3{font-size:16px;color:#1f2a44}
.modal-close{border:0;background:transparent;color:#b3bccc;font-size:18px;line-height:1}
.modal-close:hover{color:#667085}
.modal-body{padding:12px 20px 4px;font-size:13px;color:#475467;max-height:min(66vh,600px);overflow:auto}
.modal-body .mb-tip{font-size:12px;color:var(--muted);margin-bottom:10px;line-height:1.7}
.modal-body .mb-src{
  display:block;width:fit-content;max-width:100%;
  font-size:11.5px;color:#8c98ad;line-height:1.6;
  background:#f8fafd;border:1px solid #eef2f8;border-radius:5px;
  padding:2px 8px;margin-bottom:7px;
}
.modal-body .mb-src b{color:#475467;font-weight:600}
.modal-foot{padding:14px 20px 18px;display:flex;justify-content:flex-end;gap:10px;border-top:1px solid #f0f4fb;margin-top:10px}

/* 弹窗表单 */
.form-grid{display:grid;grid-template-columns:92px minmax(0,1fr);gap:9px 10px;align-items:center;margin:6px 0 4px}
.form-grid label{font-size:12px;color:#667085}
.form-grid input[type=text],.form-grid select{
  height:32px;border:1px solid #cfd8e8;border-radius:6px;padding:0 10px;
  font-size:12.5px;color:#273244;background:#fff;outline:none;width:100%;
}
.form-grid input:focus,.form-grid select:focus{border-color:#8fa3ff;box-shadow:0 0 0 2px rgba(65,105,246,.12)}
.form-grid .ro{font-size:12.5px;color:#475467;background:#f8fafd;border:1px solid #eef2f8;border-radius:6px;padding:6px 10px}

/* 权限态 */
.perm-gate{display:none;padding:60px 20px}
.perm-gate.show{display:flex;justify-content:center}
.pg-card{text-align:center;padding:44px 40px;border:1px solid #e4edf8;border-radius:12px;background:#fff;max-width:720px}
.pg-ico{width:54px;height:54px;border-radius:14px;background:var(--system-blue-weak);display:grid;place-items:center;font-size:24px;margin:0 auto 14px}
.pg-card h3{font-size:17px;color:#1f2a44;margin-bottom:8px}
.pg-card p{font-size:12.5px;color:var(--muted)}

/* 全局消息 */
.toast{
  position:fixed;top:56px;left:50%;transform:translateX(-50%) translateY(-12px);
  z-index:200;background:#2b3245;color:#fff;font-size:13px;
  padding:9px 18px;border-radius:7px;box-shadow:0 8px 24px rgba(20,32,70,.25);
  opacity:0;pointer-events:none;transition:all .2s;
}
.toast.show{opacity:1;transform:translateX(-50%) translateY(0)}
.toast.err{background:#c0392b}
.toast.ok{background:#1e8e4a}

@media (max-width: 900px) {
  .content { padding: 16px; }
  .blk-head { flex-direction: column; }
  .p7-select { max-width: 100%; }
  .pick-list { min-width: 0; }
  .modal-box.wide { width: 94vw !important; }
}
@media (max-width: 600px) {
  .filter-bar { flex-wrap: wrap; }
  .fb-search { width: 100%; }
}
</style>
