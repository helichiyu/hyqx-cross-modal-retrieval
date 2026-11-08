<template>
  <div class="p8-root">
    <div class="main"><div class="content">
      <section class="page-head"><div><h1>评测</h1><p>检索准不准，改策略有没有变好。</p></div><button class="dev-entry" type="button" @click="openDev"><span class="de-q" aria-hidden="true">?</span>研发说明</button></section>

      <section class="panel">
        <div class="panel-head"><div class="sec-head"><h2 class="sec-title">① 评测集</h2></div><button class="btn-ghost" type="button" @click="openCreateSet">＋ 新建评测集</button></div>
        <div class="tbl-wrap"><table class="tbl"><thead><tr><th>评测集名称</th><th>条数</th><th>来源</th><th>状态</th><th class="col-act">操作</th></tr></thead><tbody>
          <tr v-if="setsLoading"><td colspan="5" class="empty-cell">加载中…</td></tr>
          <tr v-else-if="!sets.length"><td colspan="5" class="empty-cell">暂无评测集</td></tr>
          <tr v-for="row in sets" v-else :key="row.id">
            <td><b>{{ row.name }}</b><div class="mono muted">{{ row.bizNo }}</div></td><td>{{ row.queryCount }} 条</td><td>{{ filterText(row.filtersJson) }}</td><td><span class="badge" :class="row.status==='done'?'ok':'wait'">{{ setStatusLabel(row.status) }}</span></td>
            <td class="col-act"><button class="btn-ghost btn-xs" type="button" @click="openLabel(row)">标注 {{ row.labeledCount }}/{{ row.queryCount }}</button><button v-if="row.status==='labeling'" class="btn-ghost btn-xs" type="button" @click="completeSet(row)">完成标注</button><button v-if="row.status==='done'" class="btn-ghost btn-xs" type="button" @click="chooseSetForRun(row)">发起评测</button></td>
          </tr>
        </tbody></table></div>
      </section>

      <section class="panel">
        <div class="panel-head"><div class="sec-head"><h2 class="sec-title">② 评测工作台</h2></div></div>
        <div class="run-bar">
          <label class="field">评测集<select v-model.number="benchSetId"><option :value="0" disabled>选择评测集</option><option v-for="row in doneSets" :key="row.id" :value="row.id">{{ row.name }} · {{ row.queryCount }} 条</option></select></label>
          <label class="field">评测对象（策略版本）<select v-model="benchVersionId"><option value="">当前生效策略</option><option v-for="v in allStrategyVersions" :key="v.id" :value="v.id">策略 {{ v.strategyId }} · v{{ v.versionNo }}</option></select></label>
          <button class="btn-primary" type="button" :disabled="runSubmitting" @click="runBench">{{ runSubmitting?'评测中…':'开始评测' }}</button>
          <div class="base-chip">对比基线 <b>{{ previousRun ? runMetric(previousRun,'recall','—') : '—' }}</b></div>
        </div>
        <div class="metric-cap"><span class="mc-title">指标</span><span class="mc-meta">{{ activeRun ? activeRun.bizNo : '尚未评测' }}</span></div>
        <div class="metrics">
          <div class="metric"><div class="m-top"><span class="m-name">召回率</span><span class="badge" :class="metricPass(activeRun?.metricsJson,'recall')?'ok':'wait'">{{ metricPass(activeRun?.metricsJson,'recall')?'达标':'待评' }}</span></div><div class="m-sub">Recall@5</div><div class="m-val">{{ activeRun ? runMetric(activeRun,'recall','—') : '—' }}</div><div class="m-base">上次基线 {{ previousRun ? runMetric(previousRun,'recall','—') : '—' }}</div></div>
          <div class="metric"><div class="m-top"><span class="m-name">精确率</span><span class="badge" :class="metricPass(activeRun?.metricsJson,'precision')?'ok':'wait'">{{ metricPass(activeRun?.metricsJson,'precision')?'达标':'待评' }}</span></div><div class="m-sub">Precision</div><div class="m-val">{{ activeRun ? runMetric(activeRun,'precision','—') : '—' }}</div><div class="m-base">上次基线 {{ previousRun ? runMetric(previousRun,'precision','—') : '—' }}</div></div>
          <div class="metric"><div class="m-top"><span class="m-name">排序质量（NDCG）</span><span class="badge" :class="metricPass(activeRun?.metricsJson,'ndcg')?'ok':'wait'">{{ metricPass(activeRun?.metricsJson,'ndcg')?'达标':'待评' }}</span></div><div class="m-sub">排序质量（NDCG@10）</div><div class="m-val">{{ activeRun ? runMetric(activeRun,'ndcg','—') : '—' }}</div><div class="m-base">上次基线 {{ previousRun ? runMetric(previousRun,'ndcg','—') : '—' }}</div></div>
        </div>
      </section>

      <section class="panel"><div class="panel-head"><div class="sec-head"><h2 class="sec-title">③ 报告</h2></div></div>
        <div v-if="!report" class="empty">完成评测后生成本次结论</div>
        <div v-else class="report-box"><div class="report-head"><b>{{ report.bizNo }}</b><span class="badge" :class="report.conclusion==='pass'?'ok':'fail'">{{ report.conclusion==='pass'?'达标':'未达标' }}</span></div><pre class="report-pre">{{ reportText(report) }}</pre></div>
      </section>

      <section class="panel"><div class="panel-head"><div class="sec-head"><h2 class="sec-title">评测历史</h2></div><span class="hist-note">共 {{ runs.length }} 条</span></div>
        <div class="tbl-wrap"><table class="tbl hist-tbl"><thead><tr><th>时间</th><th>评测对象（策略）</th><th>评测集</th><th>召回率</th><th>精确率</th><th>排序质量（NDCG）</th><th>结论</th><th>操作</th></tr></thead><tbody>
          <tr v-if="runsLoading"><td colspan="8" class="empty-cell">加载中…</td></tr><tr v-else-if="!runs.length"><td colspan="8" class="empty-cell">暂无评测历史</td></tr>
          <tr v-for="row in runs" v-else :key="row.id"><td>{{ row.createTime }}</td><td class="mono">{{ row.strategyVersionId || '当前策略' }}</td><td>{{ setLabel(row.evalSetId) }}</td><td>{{ runMetric(row,'recall','—') }}</td><td>{{ runMetric(row,'precision','—') }}</td><td>{{ runMetric(row,'ndcg','—') }}</td><td><span class="badge" :class="row.conclusion==='pass'?'ok':'fail'">{{ row.conclusion==='pass'?'达标':'未达标' }}</span></td><td><button class="btn-ghost btn-xs" type="button" @click="openReport(row)">查看</button></td></tr>
        </tbody></table></div>
      </section>
    </div></div>

    <div class="toast" :class="{show:toastVisible,ok:toastType==='ok',err:toastType==='err'}">{{toastMessage}}</div>

    <div class="modal-mask" :class="{show:createVisible}" @click.self="createVisible=false"><div class="modal-box"><div class="modal-head"><h3>新建评测集</h3><button class="modal-close" type="button" @click="createVisible=false">×</button></div><div class="modal-body"><div class="mb-tip">从检索历史中筛选问题 query，创建后进入标注。</div><label class="field">名称<input v-model.trim="createForm.name" maxlength="80" placeholder="例如：零结果专项评测集"></label><div class="filter-checks"><label v-for="item in filterOptions" :key="item.key"><input v-model="createForm.filters" type="checkbox" :value="item.key">{{ item.label }}</label></div><label class="field">说明<input v-model.trim="createForm.remark" placeholder="可选"></label></div><div class="modal-foot"><button class="btn-ghost" type="button" @click="createVisible=false">取消</button><button class="btn-primary" type="button" :disabled="creating" @click="submitCreateSet">创建评测集</button></div></div></div>

    <div class="modal-mask" :class="{show:labelVisible}" @click.self="labelVisible=false"><div class="modal-box wide"><div class="modal-head"><h3>标注：{{labelSet?.name}}</h3><button class="modal-close" type="button" @click="labelVisible=false">×</button></div><div class="modal-body label-list"><div v-if="!labelItems.length" class="empty">无待标注条目</div><div v-for="(item,index) in labelItems" :key="item.id" class="label-item"><div class="label-title"><span class="badge gray">#{{index+1}}</span><b>{{item.query}}</b><span v-if="item.labeled" class="badge ok">已标注</span></div><div class="label-input"><input v-model="labelAnswers[item.id]" placeholder="正确答案 itemId，逗号分隔；无正确答案留空"><button class="btn-ghost btn-xs" type="button" @click="saveLabel(item)">保存</button></div></div></div><div class="modal-foot"><button class="btn-ghost" type="button" @click="labelVisible=false">关闭</button></div></div></div>

    <div class="modal-mask" :class="{show:reportVisible}" @click.self="reportVisible=false"><div class="modal-box wide"><div class="modal-head"><h3>评测报告</h3><button class="modal-close" type="button" @click="reportVisible=false">×</button></div><div class="modal-body"><pre class="report-pre">{{report?reportText(report):''}}</pre></div><div class="modal-foot"><button class="btn-ghost" type="button" @click="reportVisible=false">关闭</button></div></div></div>
    <div class="dev-mask" :class="{open:devOpen}" @click="closeDev"></div>
<div class="dev-drawer" :class="{open:devOpen}" role="dialog" aria-modal="true" aria-label="研发需求说明">
  <div class="dev-head">
    <h3>研发需求说明</h3>
    <span class="dh-tag">给研发</span>
    <button type="button" class="dev-close" id="devClose" title="关闭（Esc）" aria-label="关闭" @click="closeDev">×</button>
  </div>
  <div class="dev-body">
    <p class="dev-lead">评测 = 用某个策略跑评测集，算指标，判断这个策略好不好；换策略再跑就是对比。</p>
    <div class="dev-block">
      <h4>1 评测的定位</h4>
      <p>验证检索效果，判断策略改动是否有效。P5 改完 → P8 验证 → 再发布。</p>
    </div>
    <div class="dev-block">
      <h4>2 评测集从哪来</h4>
      <p>评测集 = 检索历史里的真实 query → 筛出「已暴露问题的 query」→ 人工标注标准答案（标出每条 query 下哪些结果对 / 错）→ 形成评测集。</p>
      <p>链路：检索历史 → 新建评测集（勾选问题信号：零结果 / 无点击 / 复搜 + 业务场景 + 时间范围）→ 筛出这批 query → 生成「标注中」评测集 → 逐条标对 / 错 → 完成标注 → 「已完成」，工作台才可选。</p>
      <p>评测集来源 = 从检索历史筛「问题 query」（零结果 / 无点击 / 复搜）+ 人工标注；标注完成前不可用。</p>
      <p><b>为什么筛问题 query</b>：专项评测集测「难点修好没有」，比通用评测集有用——通用集把难点摊平在均值里，看不出这几类 query 到底修没修好。</p>
      <p>演示口径：筛出的 query 固定演示 8 条；标注面板只列前 4 条 query，标注完这 4 条即视为整批完成；「标注中」的评测集在下拉里置灰不可选。</p>
    </div>
    <div class="dev-block">
      <h4>3 指标怎么算出来</h4>
      <p>工作台 = 用「评测对象（某个策略）」去检索「评测集里的每条 query」→ 把检索结果和「标准答案」对比 → 算出召回率 / 精确率 / NDCG。</p>
      <p>评测集提供「标准答案」，策略提供「检索能力」，两者一比才有指标；只有评测集没有对象，就没有检索结果可比；只有对象没有标准答案，就判断不了对错。</p>
      <ul>
        <li><b>召回率 Recall@5</b>：该 query 的标准答案里，落进前 5 条的比例。</li>
        <li><b>精确率</b>：返回结果里判对（命中标准答案）的条数，占返回条数的比例。</li>
        <li><b>NDCG@10</b>：按位置折损累计命中，命中越靠前得分越高；再除以理想排序，归一到 0–1。</li>
      </ul>
      <p>每条 query 各算一遍再取平均，就是本次指标；同一评测集换评测对象再跑，就是两条策略的对比结果。</p>
      <p><b>验收线从哪来</b>：验收线＝业务定的目标值（当前演示：召回率 ≥ 0.90、精确率 ≥ 0.90、NDCG ≥ 0.85）；正式由业务/评测标准配置，页面只读取并判断达标 / 未达标。</p>
    </div>
    <div class="dev-block">
      <h4>4 评测对象、评测历史与报告</h4>
      <ul>
        <li><b>评测对象</b>：从策略管理（P5）来，可选当前生效策略 / 候选草稿 / 系统默认兜底；它只提供检索能力，不带标准答案。</li>
        <li><b>评测历史</b>：每点一次「开始评测」就存一条记录（时间 / 评测对象 / 评测集 / 三项指标 / 结论），新记录插在最前；记录按当时的策略版本留存，旧版本下线后仍显示原名称。演示数据为两条历史记录，刷新页面后回到这两条。</li>
        <li><b>基线</b>：取评测历史里「同一评测集」的最近一条，指标卡与报告都按它算涨跌；该评测集第一次评测时没有上一条，指标卡显示「—」，报告对比行也显示「—」。</li>
        <li><b>报告</b>：给出本次评测结论，并逐项列出与上次评测的涨跌。</li>
      </ul>
    </div>
    <div class="dev-block">
      <h4>5 闭环</h4>
      <p>评测 → 发现短板 → 转策略调整（P5）→ 改完再评测。</p>
    </div>
  </div>
</div>
  </div>
</template>

<script setup lang="ts">
import{computed,onMounted,reactive,ref}from'vue'
import{completeEvalSet,createEvalSet,evalRunDetail,evalSetDetail,labelEvalItem,pageEvalRuns,pageEvalSets,pageStrategies,runEval,strategyVersions as fetchStrategyVersions}from'../../api'
import type{EvalItemVO,EvalRecordVO,EvalSetVO,StrategyVersionVO}from'../../api/types'
import{useDictStore}from'../../stores/dict'
const dictStore=useDictStore()
const sets=ref<EvalSetVO[]>([]);const runs=ref<EvalRecordVO[]>([]);const setsLoading=ref(false);const runsLoading=ref(false);const setStatusDict=ref<{alias:string;label:string}[]>([])
const createVisible=ref(false);const creating=ref(false);const createForm=reactive({name:'',remark:'',filters:['zeroResult'] as string[]})
const labelVisible=ref(false);const labelSet=ref<EvalSetVO|null>(null);const labelItems=ref<EvalItemVO[]>([]);const labelAnswers=reactive<Record<number,string>>({})
const benchSetId=ref(0);const benchVersionId=ref<number|string>('');const strategyVersionsList=ref<StrategyVersionVO[]>([]);const runSubmitting=ref(false)
const reportVisible=ref(false);const report=ref<EvalRecordVO|null>(null)
const devOpen=ref(false);const toastVisible=ref(false);const toastMessage=ref('');const toastType=ref<'info'|'ok'|'err'>('info');let toastTimer:ReturnType<typeof setTimeout>|undefined
const filterOptions=[{key:'zeroResult',label:'零结果'},{key:'noClick',label:'无点击'},{key:'reSearch',label:'复搜'}]
const doneSets=computed(()=>sets.value.filter(x=>x.status==='done'));const activeRun=computed(()=>runs.value[0]||null);const previousRun=computed(()=>runs.value[1]||null);const allStrategyVersions=computed(()=>strategyVersionsList.value)
onMounted(async()=>{setStatusDict.value=await dictStore.ensure('cmr_eval_set_status');await Promise.all([loadSets(),loadRuns(),loadStrategyVersions()])})
function setStatusLabel(s:string){return setStatusDict.value.find(x=>x.alias===s)?.label||s}
function filterText(raw:string){try{const obj=JSON.parse(raw) as Record<string,boolean>;const hits=Object.entries(obj).filter(([,v])=>v).map(([k])=>filterOptions.find(x=>x.key===k)?.label||k);return hits.length?hits.join(' / '):'全部'}catch{return raw||'—'}}
function parseMetrics(raw?:string|null){try{return JSON.parse(raw||'{}')as Record<string,number>}catch{return{}}}
function runMetric(row:EvalRecordVO|null,key:string,fallback:string){const metricKey=key.includes('@')?key:`${key}@10`;const value=parseMetrics(row?.metricsJson)[metricKey];return value==null?fallback:Number(value).toFixed(3)}
function metricPass(raw:string|undefined|null,key:string){const metricKey=key.includes('@')?key:`${key}@10`;const m=parseMetrics(raw);return Object.prototype.hasOwnProperty.call(m,metricKey)}
function setLabel(id:number){return sets.value.find(x=>x.id===id)?.name||String(id)}
function reportText(row:EvalRecordVO){const metrics=parseMetrics(row.metricsJson);const lines=parseMetrics(row.linesJson);return ['结论：'+(row.conclusion==='pass'?'达标':'未达标'),'任务：'+(row.taskBizNo||'—'),'评测集：'+setLabel(row.evalSetId),'',...Object.entries(metrics).map(([k,v])=>`${k}: 实测 ${Number(v).toFixed(4)} / 线值 ${lines[k]??'—'}`)].join('\n')}
async function loadSets(){setsLoading.value=true;try{const page=await pageEvalSets();sets.value=page.records;if(!benchSetId.value&&doneSets.value[0])benchSetId.value=doneSets.value[0].id}catch(e){showToast((e as Error).message,'err')}finally{setsLoading.value=false}}
async function loadRuns(){runsLoading.value=true;try{const page=await pageEvalRuns();runs.value=page.records}catch(e){showToast((e as Error).message,'err')}finally{runsLoading.value=false}}
async function loadStrategyVersions(){try{const page=await pageStrategies();const all:StrategyVersionVO[]=[];for(const s of page.records){try{all.push(...await fetchStrategyVersions(s.id))}catch{}}strategyVersionsList.value=all}catch{strategyVersionsList.value=[]}}
function openCreateSet(){createVisible.value=true;createForm.name='';createForm.remark='';createForm.filters=['zeroResult']}
async function submitCreateSet(){if(!createForm.filters.length){showToast('至少勾选一个筛选条件','err');return}creating.value=true;try{const filters={zeroResult:createForm.filters.includes('zeroResult'),noClick:createForm.filters.includes('noClick'),reSearch:createForm.filters.includes('reSearch')};await createEvalSet({name:createForm.name||undefined,filters,remark:createForm.remark||undefined});createVisible.value=false;showToast('评测集已创建，请进入标注','ok');await loadSets()}catch(e){showToast((e as Error).message,'err')}finally{creating.value=false}}
async function openLabel(row:EvalSetVO){try{const data=await evalSetDetail(row.id,{pageNum:1,pageSize:200});labelSet.value=data.evalSet;labelItems.value=data.items.records;for(const item of labelItems.value){try{labelAnswers[item.id]=(JSON.parse(item.answersJson||'[]')as string[]).join(',')}catch{labelAnswers[item.id]=''}}labelVisible.value=true}catch(e){showToast((e as Error).message,'err')}}
async function saveLabel(item:EvalItemVO){if(!labelSet.value)return;const answers=(labelAnswers[item.id]||'').split(/[,，\s]+/).map(x=>x.trim()).filter(Boolean);try{await labelEvalItem(labelSet.value.id,item.id,answers);item.labeled=true;item.answersJson=JSON.stringify(answers);labelSet.value.labeledCount=labelItems.value.filter(x=>x.labeled).length;showToast('标注已保存','ok')}catch(e){showToast((e as Error).message,'err')}}
async function completeSet(row:EvalSetVO){try{await completeEvalSet(row.id);showToast('标注已完成','ok');await loadSets()}catch(e){showToast((e as Error).message,'err')}}
function chooseSetForRun(row:EvalSetVO){benchSetId.value=row.id}
async function runBench(){if(!benchSetId.value){showToast('请选择评测集','err');return}runSubmitting.value=true;try{const record=await runEval(benchSetId.value,typeof benchVersionId.value==='number'?benchVersionId.value:undefined);report.value=record;showToast('评测已提交','ok');await loadRuns();reportVisible.value=true}catch(e){showToast((e as Error).message,'err')}finally{runSubmitting.value=false}}
async function openReport(row:EvalRecordVO){try{report.value=await evalRunDetail(row.id);reportVisible.value=true}catch(e){showToast((e as Error).message,'err')}}
function showToast(message:string,type:'info'|'ok'|'err'='info'){toastMessage.value=message;toastType.value=type;toastVisible.value=true;clearTimeout(toastTimer);toastTimer=setTimeout(()=>toastVisible.value=false,2600)}
function openDev(){devOpen.value=true}function closeDev(){devOpen.value=false}
</script>
<style scoped>
.empty-cell{text-align:center;color:#98a2b3;padding:24px!important}.muted{color:#98a2b3;font-size:11px;margin-top:3px}.filter-checks{display:flex;gap:14px;flex-wrap:wrap;margin:12px 0}.filter-checks label{display:flex;align-items:center;gap:6px;font-size:12px;color:#475467}.label-list{display:flex;flex-direction:column;gap:10px;max-height:64vh;overflow:auto}.label-item{border:1px solid #e6edf8;border-radius:8px;padding:10px;background:#fbfdff}.label-title{display:flex;align-items:center;gap:8px;margin-bottom:8px}.label-input{display:flex;gap:8px}.label-input input{flex:1;height:32px;border:1px solid #cfd8e8;border-radius:6px;padding:0 9px}.report-box{border:1px solid #e4edf8;border-radius:8px;background:#fbfdff;padding:14px}.report-head{display:flex;align-items:center;gap:10px;margin-bottom:10px}.report-pre{white-space:pre-wrap;margin:0;color:#344054;font-size:12px;line-height:1.75;font-family:inherit}
:root{
  --workspace-bg:#f6f9fe;
  --content-soft:#fbfdff;
  --content-line:#e6edf8;
  --system-blue:#4169f6;
  --system-blue-weak:#f1f5ff;
  --muted:#667085;
  --success:#1e8e4a;
  --warning:#b7791f;
  --error:#c0392b;
  --text-primary:#262626;
}
*{margin:0;padding:0;box-sizing:border-box}
html,body{height:100%}
body{
  font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif;
  background:var(--workspace-bg);
  color:var(--text-primary);
  font-size:14px;
  line-height:1.55;
  display:flex;
}
button{font-family:inherit;cursor:pointer}
a{color:inherit;text-decoration:none}
::selection{background:rgba(65,105,246,.16)}

/* ============ 左侧全局导航 ============ */
.sidebar{
  width:220px;flex-shrink:0;
  background:#fff;border-right:1px solid var(--content-line);
  display:flex;flex-direction:column;
  height:100vh;position:sticky;top:0;
}
.nav-scroll{flex:1;overflow-y:auto;padding:14px 12px}
.nav-item{
  display:flex;align-items:center;gap:9px;
  padding:9px 10px;margin-bottom:2px;border-radius:6px;color:#344054;font-size:13px;
  cursor:pointer;user-select:none;
}
.nav-item .ico{width:18px;height:18px;display:flex;align-items:center;justify-content:center;color:#8a96ad;flex-shrink:0}
.nav-item .ico svg{width:16px;height:16px;display:block}
.nav-item:hover{background:var(--system-blue-weak);color:var(--system-blue)}
.nav-item.active{
  background:var(--system-blue-weak);color:var(--system-blue);font-weight:600;
  box-shadow:inset 2px 0 0 var(--system-blue);
}
.nav-item.active .ico{color:var(--system-blue)}
.nav-divider{height:1px;background:var(--content-line);margin:8px 10px}

/* ============ 主区 ============ */
.main{flex:1;min-width:0;height:100vh;overflow-y:auto}
.content{padding:22px 28px 40px;display:flex;flex-direction:column;gap:16px;max-width:1180px;width:100%;margin:0 auto}

/* 页头 */
.page-head{
  display:flex;align-items:flex-start;justify-content:space-between;gap:16px;flex-wrap:wrap;
  padding:18px 20px;
  border:1px solid #e4edf8;
  border-radius:10px;
  background:#fff;
}
.page-head h1{font-size:20px;font-weight:700;color:#111827;line-height:1.3}
.page-head p{font-size:13px;color:#667085;line-height:1.65;margin-top:5px;max-width:760px}

/* 面板 */
.panel{padding:15px 18px;border:1px solid #e4edf8;border-radius:10px;background:#fff;display:flex;flex-direction:column;gap:13px}
.panel-head{display:flex;align-items:flex-start;justify-content:space-between;gap:10px;flex-wrap:wrap}
.sec-head{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.sec-head::before{content:"";width:3px;height:14px;border-radius:2px;background:var(--system-blue);flex:0 0 auto}
.sec-title{font-size:15px;font-weight:700;color:#1f2a44}

/* 评测集列表 */
.tbl-wrap{overflow-x:auto}
.tbl{width:100%;border-collapse:collapse;font-size:12px}
.tbl th{background:#f6f8fc;color:#8c98ad;font-weight:600;text-align:left;padding:8px 10px;border-bottom:1px solid #e6edf8;white-space:nowrap}
.tbl td{padding:10px;border-bottom:1px solid #eef3fa;color:#475467;vertical-align:middle}
.tbl tbody tr:last-child td{border-bottom:0}
.tbl tbody tr:hover{background:#fbfdff}
.tbl td.set-name{font-weight:600;color:#273244}
.tbl td.num{font-variant-numeric:tabular-nums;white-space:nowrap}
.tbl td.src{color:#475467;white-space:nowrap}
.tbl .col-act{text-align:right}
.tbl td.act{text-align:right}

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
.st{display:inline-flex;align-items:center;font-size:10.5px;line-height:1;padding:4px 8px;border-radius:10px;background:#f3f5f9;color:#8c98ad;font-weight:600;white-space:nowrap}
.st.ok{background:#eaf7ef;color:var(--success)}
.st.bad{background:#fdeceb;color:var(--error)}
.st.doing{background:#fff6e6;color:var(--warning)}
.btn-mini{height:26px;padding:0 10px;border:1px solid #dbe5f4;border-radius:5px;background:#fff;color:var(--system-blue);font-size:11.5px;font-weight:600}
.btn-mini:hover{border-color:var(--system-blue);background:#f7f9ff}

/* 按钮 */
.btn-primary{
  height:34px;padding:0 16px;border:0;border-radius:6px;
  background:var(--system-blue);color:#fff;font-size:12.5px;font-weight:600;
  display:inline-flex;align-items:center;justify-content:center;gap:6px;
  box-shadow:0 3px 8px rgba(65,105,246,.2);transition:background .15s;
}
.btn-primary:hover{background:#335be8}
.btn-ghost{height:32px;padding:0 14px;border-radius:6px;border:1px solid #d7e0f0;background:#fff;color:#475467;font-size:12px;display:inline-flex;align-items:center;justify-content:center;gap:6px}
.btn-ghost:hover{border-color:var(--system-blue);color:var(--system-blue)}

/* 工作台跑评测 */
.run-bar{display:flex;align-items:flex-end;gap:12px;flex-wrap:wrap;padding:11px 12px;border:1px solid #e8eff9;border-radius:8px;background:var(--content-soft)}
.field{display:flex;flex-direction:column;gap:5px;font-size:11px;color:#8c98ad;font-weight:600}
.field select{height:33px;min-width:220px;border:1px solid #cfd8e8;border-radius:6px;background:#fff;color:#273244;font-size:12.5px;padding:0 9px;outline:none}
.field select:focus{border-color:#8fa3ff;box-shadow:0 0 0 2px rgba(65,105,246,.12)}
.base-chip{height:33px;display:inline-flex;align-items:center;gap:6px;padding:0 10px;border:1px dashed #d7e0f0;border-radius:6px;font-size:11.5px;color:#667085;background:#fff;margin-left:auto;white-space:nowrap}
.base-chip b{color:#344054;font-weight:600}

/* 指标结果 */
.metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}
.metric{border:1px solid #e4edf8;border-radius:9px;padding:12px 14px;background:linear-gradient(180deg,#fff 0%,#f8fbff 100%);display:flex;flex-direction:column;gap:5px;min-width:0}
.m-top{display:flex;align-items:center;justify-content:space-between;gap:8px}
.m-name{font-size:12.5px;font-weight:700;color:#273244}
.m-sub{font-size:10.5px;color:#98a2b3}
.m-val{font-size:28px;font-weight:800;color:#273244;line-height:1.1;font-variant-numeric:tabular-nums;letter-spacing:-.5px}
.m-base{font-size:11px;color:#8c98ad;display:flex;align-items:center;gap:6px;flex-wrap:wrap;font-variant-numeric:tabular-nums}
.tag{font-size:10px;font-weight:700;padding:3px 7px;border-radius:9px;background:#f3f5f9;color:#8c98ad;white-space:nowrap}
.tag.ok{background:#eaf7ef;color:var(--success)}
.tag.bad{background:#fdeceb;color:var(--error)}
.delta{font-weight:700;font-size:11px;color:#8c98ad;font-variant-numeric:tabular-nums}
.delta.up{color:var(--success)}
.delta.down{color:var(--error)}
.metric-cap{display:flex;align-items:baseline;justify-content:space-between;gap:10px;flex-wrap:wrap;padding:0 2px 9px}
.mc-title{font-size:13.5px;font-weight:700;color:#1f2a44}
.mc-title b{color:var(--system-blue);font-weight:700}
.mc-meta{font-size:11px;color:#8c98ad;font-variant-numeric:tabular-nums}

/* 报告 */
.verdict{border:1px solid #dbe7fb;border-radius:9px;background:linear-gradient(180deg,#fff 0%,#f8fbff 100%);padding:14px 16px;display:flex;flex-direction:column;gap:9px}
.v-top{display:flex;align-items:center;justify-content:space-between;gap:10px}
.v-kicker{font-size:11px;font-weight:700;color:var(--system-blue);background:var(--system-blue-weak);border-radius:10px;padding:3px 8px}
.v-time{font-size:10.5px;color:#98a2b3;font-variant-numeric:tabular-nums}
.v-text{font-size:15px;font-weight:700;color:#1f2a44;line-height:1.55}
.v-bar{display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap;border-top:1px dashed #e6eef9;padding-top:10px}

/* 对比上次评测 */
.v-cmp{display:flex;flex-direction:column;gap:7px;border-top:1px dashed #e6eef9;padding-top:10px}
.v-cmp-head{font-size:11.5px;font-weight:700;color:#8c98ad;display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.v-cmp-head span{font-weight:600;color:#98a2b3;font-variant-numeric:tabular-nums}
.v-cmp-items{display:flex;gap:8px;flex-wrap:wrap}
.v-cmp-item{font-size:11.5px;color:#667085;background:#f8fafd;border:1px solid #e8eff9;border-radius:7px;padding:5px 10px;font-variant-numeric:tabular-nums;white-space:nowrap}
.v-cmp-item b{color:#273244;font-weight:700}
.v-cmp-item i{font-style:normal}

/* 评测历史 */
.hist-note{font-size:11px;color:#98a2b3;font-variant-numeric:tabular-nums}
.hist-tbl td:first-child{color:#667085;font-variant-numeric:tabular-nums}
.hist-tbl td.pass-cell{white-space:nowrap}

/* 空态 */
.empty{padding:28px 12px;text-align:center;font-size:12px;color:#98a2b3;border:1px dashed #e2ebf7;border-radius:8px;background:var(--content-soft)}

/* 权限态 */
.perm-gate{display:none}
.perm-gate.show{display:flex;justify-content:center;padding:60px 20px}
.pg-card{text-align:center;padding:44px 40px;border:1px solid #e4edf8;border-radius:12px;background:#fff;max-width:720px}
.pg-card h3{font-size:17px;color:#1f2a44;margin-bottom:8px}
.pg-card p{font-size:12.5px;color:var(--muted)}

/* 弹窗 */
.modal-mask{position:fixed;inset:0;background:rgba(23,33,66,.42);z-index:100;display:none;align-items:center;justify-content:center;padding:20px}
.modal-mask.show{display:flex}
.modal-box{width:560px;max-width:100%;max-height:90vh;background:#fff;border-radius:10px;box-shadow:0 16px 48px rgba(20,32,70,.28);display:flex;flex-direction:column;overflow:hidden;animation:pop .18s ease}
@keyframes pop{from{transform:translateY(8px) scale(.98);opacity:0}to{transform:none;opacity:1}}
.modal-head{padding:15px 20px 0;display:flex;align-items:flex-start;justify-content:space-between;gap:10px;flex:0 0 auto}
.modal-head h3{font-size:15px;color:#1f2a44;line-height:1.4}
.modal-close{border:0;background:transparent;color:#b3bccc;font-size:20px;line-height:1;padding:0 2px}
.modal-close:hover{color:#667085}
.modal-body{padding:12px 20px 4px;font-size:12.5px;color:#475467;overflow:auto;display:flex;flex-direction:column;gap:9px;min-height:0}
.modal-foot{padding:12px 20px 16px;display:flex;justify-content:flex-end;gap:9px;border-top:1px solid #f0f4fb;margin-top:10px;flex:0 0 auto}
.form-row{display:grid;grid-template-columns:64px minmax(0,1fr);gap:10px;align-items:center;font-size:12px;color:#475467}
.form-row select,.form-row input{height:32px;border:1px solid #cfd8e8;border-radius:5px;padding:0 9px;font-size:12px;outline:none;background:#fff;color:#273244;min-width:0}
.form-row select:focus,.form-row input:focus{border-color:#8fa3ff;box-shadow:0 0 0 2px rgba(65,105,246,.12)}
.kv{display:grid;grid-template-columns:84px minmax(0,1fr);gap:8px;font-size:12px;color:#667085;padding:7px 0;border-bottom:1px dashed #eef2f8;line-height:1.55}
.kv:last-child{border-bottom:0}
.kv b{color:#273244;font-weight:600;overflow-wrap:anywhere}
.form-note{font-size:11px;color:#98a2b3;line-height:1.65;padding:2px 2px 0}

/* 筛选条件多选 */
.form-row.chk-row{align-items:flex-start}
.form-row.chk-row > span{padding-top:8px}
.checks{display:flex;flex-direction:column;gap:6px;min-width:0;padding:2px 0}
.chk{display:flex;align-items:center;gap:7px;font-size:12px;color:#273244;line-height:1.5;cursor:pointer}
.form-row .chk input[type="checkbox"]{width:14px;height:14px;min-width:14px;margin:0;padding:0;border:0;background:transparent;accent-color:var(--system-blue);flex:0 0 auto}
.form-row .chk input[type="checkbox"]:focus{box-shadow:none;border:0}
.chk small{font-size:10.5px;color:#98a2b3;font-weight:400}
.req{font-style:normal;color:var(--error);font-weight:700;margin-left:2px}

/* 标注 */
.anno-bar{display:flex;align-items:center;justify-content:space-between;gap:10px;font-size:11.5px;color:#8c98ad;font-variant-numeric:tabular-nums}
.anno-bar b{color:var(--system-blue);font-weight:700}
.anno-row{display:flex;align-items:center;gap:12px;padding:10px 12px;border:1px solid #e4edf8;border-radius:8px;background:var(--content-soft)}
.anno-main{flex:1;min-width:0}
.anno-q{font-size:12.5px;color:#273244;font-weight:600;line-height:1.5}
.anno-res{font-size:11px;color:#8c98ad;margin-top:3px}
.anno-pick{display:flex;gap:6px;flex:0 0 auto}
.pick{height:28px;min-width:46px;border:1px solid #dbe5f4;border-radius:5px;background:#fff;color:#667085;font-size:11.5px;font-weight:600}
.pick:hover{border-color:#8fa3ff;color:var(--system-blue)}
.pick.on{background:#eaf7ef;border-color:#9ad9b3;color:var(--success)}
.pick.on[data-v="0"]{background:#fdeceb;border-color:#efb1a9;color:var(--error)}

/* Toast */
.toast{
  position:fixed;top:26px;left:50%;transform:translateX(-50%) translateY(-10px);
  z-index:300;background:#2b3245;color:#fff;font-size:12.5px;
  padding:8px 16px;border-radius:7px;box-shadow:0 8px 24px rgba(20,32,70,.25);
  opacity:0;pointer-events:none;transition:all .2s;max-width:min(90vw,720px);text-align:center;
}
.toast.show{opacity:1;transform:translateX(-50%) translateY(0)}
.toast.err{background:#c0392b}
.toast.ok{background:#1e8e4a}

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
.dev-lead{font-size:11.5px;color:#667085;line-height:1.65;margin-bottom:10px}
.dev-block{background:#fff;border:1px solid #e4eaf3;border-radius:8px;padding:11px 13px;margin-bottom:10px}
.dev-block h4{font-size:12px;color:#1f2934;margin-bottom:5px}
.dev-block p{font-size:11.5px;color:#667085;line-height:1.7}
.dev-block p + p{margin-top:6px}
.dev-block ul{margin:2px 0 0 16px}
.dev-block li{font-size:11.5px;color:#667085;line-height:1.7;margin-bottom:3px}
.dev-block li b{color:#344054}
.dev-table{width:100%;border-collapse:collapse;background:#fff;font-size:11px;border:1px solid #e4eaf3;border-radius:8px;overflow:hidden}
.dev-table th{background:#fbfcfe;color:#98a2b3;font-weight:600;text-align:left;padding:7px 9px;border-bottom:1px solid #eef2f8;white-space:nowrap}
.dev-table td{padding:7px 9px;border-bottom:1px solid #eef2f8;color:#475467;vertical-align:top;line-height:1.55}
.dev-table tr:last-child td{border-bottom:0}
.dev-table td:first-child{font-weight:600;color:#273244;white-space:normal}
.why-table th:nth-child(2),.why-table td:nth-child(2){width:64px;white-space:nowrap}

@media (max-width:1100px){
  .metrics{grid-template-columns:repeat(2,minmax(0,1fr))}
}
@media (max-width:760px){
  .sidebar{display:none}
  .content{padding:16px 14px 30px}
  .page-head h1{font-size:20px}
  .metrics{grid-template-columns:1fr}
  .base-chip{margin-left:0}
  .field,.field select{width:100%;min-width:0}
}
</style>
