<template>
  <div class="p9-root">
    <div class="main"><div class="content">
      <section class="module-intro"><div><h2>我的查询历史</h2><p>历史检索记录：query / 时间 / 结果数 / 状态，行内可查看结果或再次执行。系统全部任务见 <router-link class="row-link" to="/tasks">任务管理</router-link>。</p></div><button class="dev-entry" type="button" @click="openDev"><span class="de-q" aria-hidden="true">?</span>研发说明</button></section>
      <section class="card hist-card">
        <div class="sec-head"><span class="sec-title">历史记录</span><span class="sec-sub">共 {{total}} 条</span></div>
        <div class="filter-bar">
          <input v-model="keyword" class="fb-search" type="text" placeholder="关键词筛选历史 query（如：发票 / 作业 / 窗口）…" @input="scheduleKeywordSearch" @keyup.enter="loadNow">
          <select v-model="modalityFilter" class="fb-sel" title="模态对筛选" @change="load(1)"><option value="">全部模态对</option><option v-for="item in modalityPairOptions" :key="item.value" :value="item.value">{{item.label}}</option></select>
          <select v-model="adoptFilter" class="fb-sel" title="采用状态筛选" @change="load(1)"><option value="">全部状态</option><option value="adopted">已采用</option><option value="discarded">已废弃</option><option value="unadopted">未采用</option></select>
          <button class="btn-ghost" type="button" @click="resetFilters">清空筛选</button>
        </div>
        <div class="mini-table">
          <div class="mt-row head mt-cols-h"><div>时间</div><div>Query</div><div>模态</div><div>结果数</div><div>状态</div><div>任务/结果集</div><div>操作</div></div>
          <div v-if="loading" class="state-box"><span class="big">…</span>加载中</div>
          <div v-else-if="error" class="state-box err"><span class="big">⚠️</span>{{ error }}<div class="acts"><button class="btn-primary" type="button" @click="load(pageNum)">重新载入</button></div></div>
          <div v-else-if="!rows.length" class="state-box"><span class="big">🗂️</span>没有匹配的历史记录，换个关键词试试，或 <router-link class="row-link" to="/search">前往检索台</router-link> 发起新检索。</div>
          <div v-for="row in rows" v-else :key="row.id" class="mt-row">
            <div class="c">{{ row.createTime }}</div><div class="c">{{ row.queryText || '（文件语义检索）' }}</div><div>{{ modalityPair(row) }}</div><div class="num">{{ row.itemCount ?? 0 }}</div><div><span class="badge" :class="taskStatusClass(row.taskStatus)">{{ taskStatusLabel(row.taskStatus) }}</span><small class="adopt-hint">{{ adoptLabel(row.adoptStatus) }}</small></div><div class="c mono"><router-link v-if="row.taskId" class="row-link" :to="`/tasks/${row.taskId}`">{{ row.taskBizNo || row.taskId }}</router-link><span v-else>{{ row.resultSetId }}</span></div>
            <div class="act-wrap"><button class="mini-btn" type="button" @click="openDetail(row)">查看</button><button class="mini-btn primary" type="button" @click="rerun(row)">再次执行</button></div>
          </div>
        </div>
        <div class="pager-bar"><span class="pager-total">共 {{ total }} 条</span><button class="pager-btn" type="button" :disabled="pageNum<=1" @click="load(pageNum-1)">上一页</button><button class="pager-num is-cur" type="button">{{pageNum}}</button><span class="pager-gap">/</span><span class="pager-total">{{pageCount}}</span><button class="pager-btn" type="button" :disabled="pageNum>=pageCount" @click="load(pageNum+1)">下一页</button></div>
      </section>
    </div></div>
    <div class="toast" :class="{show:toastVisible,ok:toastType==='ok',err:toastType==='err'}">{{toastMessage}}</div>
    <div class="modal-mask" :class="{show:detailVisible}" @click.self="detailVisible=false"><div class="modal-box wide"><div class="modal-head"><h3>检索详情</h3><button class="modal-close" type="button" @click="detailVisible=false">×</button></div><div class="modal-body"><div class="detail-grid"><div><span>查询</span><b>{{detail?.history.queryText || '（文件语义检索）'}}</b></div><div><span>模态</span><b>{{modalityPair(detail?.history)}}</b></div><div><span>结果数</span><b>{{detail?.history.itemCount ?? 0}}</b></div><div><span>耗时</span><b>{{detail?.history.elapsedMs ?? '—'}} ms</b></div></div><div class="dtable"><table class="dt"><thead><tr><th>#</th><th>模态</th><th>得分</th><th>内容</th><th>来源</th></tr></thead><tbody><tr v-if="!detail?.items.length"><td colspan="5" class="table-empty">暂无结果条目</td></tr><tr v-for="item in detail?.items || []" :key="item.id"><td>{{item.rank}}</td><td>{{modalityLabel(item.modality)}}</td><td>{{Number(item.score).toFixed(3)}}</td><td>{{item.text || item.itemId}}</td><td class="mono">{{item.sourceFile || '—'}}</td></tr></tbody></table></div></div><div class="modal-foot"><button class="btn-ghost" type="button" @click="detailVisible=false">关闭</button><router-link v-if="detail?.history.resultSetId" class="btn-primary ghost-route" :to="`/search/results/${detail.history.resultSetId}`">查看结果页</router-link></div></div></div>
    <div class="dev-mask" :class="{open:devOpen}" @click="closeDev"></div>
<div class="dev-drawer" :class="{open:devOpen}" role="dialog" aria-modal="true" aria-label="研发需求说明">
  <div class="dev-head">
    <h3>研发需求说明</h3>
    <span class="dh-tag">给研发</span>
    <button type="button" class="dev-close" id="devClose" title="关闭（Esc）" aria-label="关闭" @click="closeDev">×</button>
  </div>
  <div class="dev-body">
<div class="dev-card" id="dc-ov" style="scroll-margin-top:12px"><div class="dc-head"><span class="dc-id" style="background:#1f5fd4">总览</span><span class="dc-name">页面定位 / 闭环 / 模块清单 / 编号与演示性质</span></div><div class="dc-body"><div class="ov-grid"><div class="dev-ov" style="margin:0;border:none;padding:6px 4px"><h4>本页定位【设计】</h4><p>「我的查询历史」：七列历史记录列表（置顶，query / 时间 / 模态对 / 结果数 / 状态 / 任务ID / 操作），行内可查看结果、再次执行。</p></div><div class="dev-ov" style="margin:0;border:none;padding:6px 4px"><h4>平台全景闭环【研发】</h4><p><a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a> 发起检索 → 本页沉淀历史 → 查看结果 <a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a> / 查看任务 <a class="dev-xref" href="P3_任务详情_v5.0_20261025.html">P3</a> / 再次执行回填 <a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a>。</p></div><div class="dev-ov" style="margin:0;border:none;padding:6px 4px"><h4>三级模块清单【设计】</h4><p>轻模板页（0 主挂）；联动展示：再次执行→P1、查看结果→P2、查看任务→P3。</p></div><div class="dev-ov" style="margin:0;border:none;padding:6px 4px"><h4>编号规范与演示性质【研发】</h4><p>业务编号按《平台相关ID生成规范-1026》32.1：任务 TASK（等宽可复制）。<br>本页为页内模拟，不请求真实引擎；历史条目/结果为示例数据，不构成交付承诺。</p></div></div></div></div>
<div class="dev-card" id="dc-f1"><div class="dc-head"><span class="dc-id">F1</span><span class="dc-name">历史列表</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row"><div class="dc-k">职责</div><div class="dc-v">七列展示我的历史检索记录（时间/Query/模态对/结果数/状态/任务ID/操作），状态徽标区分成功/失败；操作列=查看结果 / 再次执行。</div></div><div class="dc-row"><div class="dc-k">入口/前置</div><div class="dc-v">页面常驻；历史记录来自 <a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a> 发起的检索（仅本人）。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>历史条目（任务号 TASK、query、模态对、结果数、状态）</li></ul></div></div><div class="dc-row"><div class="dc-k">处理规则</div><div class="dc-v">列表只读（历史记录不编辑）；行内动作：查看结果跳 <a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a>?task=、再次执行携 ?q= 回 <a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a>。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>七列历史列表 + 成功/失败徽标 + 行内两动作（查看结果 / 再次执行）</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>失败行「查看结果」置灰（失败无结果集）</li><li>仅展示本人历史（他人在线任务在 P6）</li></ul></div></div><div class="dc-row"><div class="dc-k">状态</div><div class="dc-v">列表态 / 空态 / 错误态。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">上游=<a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a>；下游=<a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a>（查看结果）/ <a class="dev-xref" href="P3_任务详情_v5.0_20261025.html">P3</a>（任务ID）/ <a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a>（再次执行）。</div></div><div class="dc-row"><div class="dc-k">验收点</div><div class="dc-v">七列渲染正确；成功/失败徽标正确；失败行查看结果置灰；两动作携参正确。</div></div></div></div>
<div class="dev-card" id="dc-f2"><div class="dc-head"><span class="dc-id">F2</span><span class="dc-name">筛选</span></div><div class="dc-body"><div class="dc-row"><div class="dc-k">职责</div><div class="dc-v">按关键词、模态对、状态筛选历史列表。</div></div><div class="dc-row"><div class="dc-k">入口/前置</div><div class="dc-v">筛选条常驻列表上方。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>关键词（query 模糊匹配）、模态对（全部/文搜图/文搜视频）、状态（全部/成功/失败）</li></ul></div></div><div class="dc-row"><div class="dc-k">处理规则</div><div class="dc-v">三条件叠加过滤（AND），实时联动渲染；清空筛选恢复全部。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>过滤后的历史列表 + 计数「共 N 条」</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>过滤后无结果触发空态</li><li>处于错误态时任一筛选输入自动回到列表态</li></ul></div></div><div class="dc-row"><div class="dc-k">状态</div><div class="dc-v">默认全部条件为空。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">本页 F1 列表。</div></div><div class="dc-row"><div class="dc-k">验收点</div><div class="dc-v">三条件叠加正确；清空恢复默认；空态/错误态切换正确。</div></div></div></div>
<div class="dev-card" id="dc-f3"><div class="dc-head"><span class="dc-id">F3</span><span class="dc-name">再次执行</span></div><div class="dc-body"><div class="dc-row"><div class="dc-k">职责</div><div class="dc-v">将历史 query 回填至 <a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a> 检索台（快速复用）。</div></div><div class="dc-row"><div class="dc-k">入口/前置</div><div class="dc-v">每条历史行的「再次执行」链接。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>目标 query（行内）</li></ul></div></div><div class="dc-row"><div class="dc-k">处理规则</div><div class="dc-v">跳 <a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a> 携 ?q= 参数（URL 编码）；P1 侧读参回填关键词。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>跳转 P1?q=…（回填关键词）</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v">无（纯跳转，不校验权限）。</div></div><div class="dc-row"><div class="dc-k">状态</div><div class="dc-v">静态链接。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">下游=<a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a>（携 ?q=）。</div></div><div class="dc-row"><div class="dc-k">验收点</div><div class="dc-v">跳转 P1 且 ?q= 参数正确（URL 编码）。</div></div></div></div>
<div class="dev-card" id="dc-f4"><div class="dc-head"><span class="dc-id">F4</span><span class="dc-name">查看结果 / 查看任务</span></div><div class="dc-body"><div class="dc-row"><div class="dc-k">职责</div><div class="dc-v">由历史行进入该次检索的结果页（查看结果）或单任务档案（任务ID）。</div></div><div class="dc-row"><div class="dc-k">入口/前置</div><div class="dc-v">行内「查看结果」（成功行可点）与「任务ID」链接。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>任务号 TASK（行内，等宽可复制）</li></ul></div></div><div class="dc-row"><div class="dc-k">处理规则</div><div class="dc-v">查看结果携 ?task= 跳 <a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a>；任务ID 携 ?task= 跳 <a class="dev-xref" href="P3_任务详情_v5.0_20261025.html">P3</a>；失败行查看结果为置灰文本。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>跳转 P2?task= / P3?task=</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>失败任务无结果集，仅可跳任务详情查看失败态</li></ul></div></div><div class="dc-row"><div class="dc-k">状态</div><div class="dc-v">成功可跳结果 / 失败仅可跳任务。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">下游=<a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a>（结果）/ <a class="dev-xref" href="P3_任务详情_v5.0_20261025.html">P3</a>（任务详情）。</div></div><div class="dc-row"><div class="dc-k">验收点</div><div class="dc-v">跳转参数正确（?task= 随 TASK 编号）；失败行置灰正确。</div></div></div></div>
<div class="dev-card" id="dc-f6"><div class="dc-head"><span class="dc-id">F6</span><span class="dc-name">空态 / 错误态</span><span class="demo-limit">含演示限制，正式实现需另定</span></div><div class="dc-body"><div class="dc-row"><div class="dc-k">职责</div><div class="dc-v">空态（无匹配）与错误态（历史加载失败）切换。</div></div><div class="dc-row"><div class="dc-k">入口/前置</div><div class="dc-v">列表为空或服务不可用时触发。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>列表结果（空/失败）、系统错误码 HIST-503</li></ul></div></div><div class="dc-row"><div class="dc-k">处理规则</div><div class="dc-v">无匹配→空态引导（换关键词 / 前往 P1）；服务不可用→错误态 + 重新载入 / 回 P1。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>空态提示 + 前往 P1 / 错误态提示 + 重新载入按钮</li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>错误码 HIST-503（系统码，保留）；URL 传 fail=1 演示触发错误态</li></ul></div></div><div class="dc-row"><div class="dc-k">状态</div><div class="dc-v">空态 / 错误态（与列表态互斥切换）。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">下游=<a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a>（前往/回检索台）。</div></div><div class="dc-row"><div class="dc-k">验收点</div><div class="dc-v">空态/错误态切换正确；重新载入回到列表态。</div></div></div></div>
<div class="dev-card" id="dc-f7"><div class="dc-head"><span class="dc-id">F7</span><span class="dc-name">P9 与 P6 的分工</span></div><div class="dc-body"><div class="dc-row"><div class="dc-k">职责</div><div class="dc-v">划清两个任务相关页面的视角：本页=我的查询历史（用户视角），<a class="dev-xref" href="P6_任务管理_v5.0_20261025.html">P6</a>=系统全部任务（运行视角）。</div></div><div class="dc-row"><div class="dc-k">入口/前置</div><div class="dc-v">本页由左栏导航进入，仅列本人发起的检索。</div></div><div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>本页：本人历史（query / 时间 / 结果数 / 状态）</li><li><a class="dev-xref" href="P6_任务管理_v5.0_20261025.html">P6</a>：全部任务（队列 / 引擎健康 / 失败任务 / 配额）</li></ul></div></div><div class="dc-row"><div class="dc-k">处理规则</div><div class="dc-v">本页只管「查过的 query 去哪儿再查/看结果」；任务调度、失败处置、配额与熔断一律回 <a class="dev-xref" href="P6_任务管理_v5.0_20261025.html">P6</a>。</div></div><div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>本页出口：查看结果→<a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a>、任务ID→<a class="dev-xref" href="P3_任务详情_v5.0_20261025.html">P3</a>、再次执行→<a class="dev-xref" href="P1_检索台_v5.0_20261025.html">P1</a></li></ul></div></div><div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v"><ul><li>他人在线任务与系统级指标以 <a class="dev-xref" href="P6_任务管理_v5.0_20261025.html">P6</a> 为唯一来源</li></ul></div></div><div class="dc-row"><div class="dc-k">状态</div><div class="dc-v">静态说明。</div></div><div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v"><a class="dev-xref" href="P6_任务管理_v5.0_20261025.html">P6</a> 任务管理（运行视角）；<a class="dev-xref" href="P3_任务详情_v5.0_20261025.html">P3</a> 任务详情（单任务档案）。</div></div><div class="dc-row"><div class="dc-k">验收点</div><div class="dc-v">两页职责无重叠。</div></div></div></div>
  </div>
</div>
  </div>
</template>
<script setup lang="ts">
import{computed,onBeforeUnmount,onMounted,ref}from'vue';import{useRouter}from'vue-router';import{historyDetail,pageHistory,rerunHistory}from'../../api';import type{HistoryDetailVO,HistoryVO}from'../../api/types'
const router=useRouter();const rows=ref<HistoryVO[]>([]);const total=ref(0);const pageNum=ref(1);const pageSize=10;const keyword=ref('');const modalityFilter=ref('');const adoptFilter=ref('');const loading=ref(false);const error=ref('');const detail=ref<HistoryDetailVO|null>(null);const detailVisible=ref(false);const devOpen=ref(false);const toastVisible=ref(false);const toastMessage=ref('');const toastType=ref<'info'|'ok'|'err'>('info');let toastTimer:ReturnType<typeof setTimeout>|undefined;let searchTimer:ReturnType<typeof setTimeout>|undefined
const pageCount=computed(()=>Math.max(1,Math.ceil(total.value/pageSize)))
const modalityPairOptions=[
  {value:'text:text',label:'文搜文'},{value:'text:image',label:'文搜图'},{value:'text:audio',label:'文搜音'},{value:'text:video',label:'文搜视频'},
  {value:'image:text',label:'图搜文'},{value:'image:image',label:'图搜图'},{value:'image:audio',label:'图搜音'},{value:'image:video',label:'图搜视频'},
  {value:'audio:text',label:'音搜文'},{value:'audio:image',label:'音搜图'},{value:'audio:audio',label:'音搜音'},{value:'audio:video',label:'音搜视频'},
  {value:'video:text',label:'视频搜文'},{value:'video:image',label:'视频搜图'},{value:'video:audio',label:'视频搜音'},{value:'video:video',label:'视频搜视频'},
]
function historyFilterParams(){const[modality,targetModality]=modalityFilter.value.split(':');return{modality:modality||'',targetModality:targetModality||''}}
onMounted(()=>load(1))
onBeforeUnmount(()=>{clearTimeout(toastTimer);clearTimeout(searchTimer)})
function scheduleKeywordSearch(){clearTimeout(searchTimer);searchTimer=setTimeout(()=>void load(1),350)}
function loadNow(){clearTimeout(searchTimer);void load(1)}
function taskStatusLabel(value?:string|null){return({ready:'就绪',running:'执行中',done:'完成',failed:'失败'}as Record<string,string>)[value||'']||'未知'}
function taskStatusClass(value?:string|null){return({ready:'run',running:'run',done:'ok',failed:'fail'}as Record<string,string>)[value||'']||'wait'}
async function load(page:number){pageNum.value=page;loading.value=true;error.value='';try{const data=await pageHistory({pageNum:page,pageSize,keyword:keyword.value,adoptStatus:adoptFilter.value,...historyFilterParams()});rows.value=data.records;total.value=data.total}catch(e){error.value=(e as Error).message}finally{loading.value=false}}
function resetFilters(){clearTimeout(searchTimer);keyword.value='';modalityFilter.value='';adoptFilter.value='';void load(1)}
function modalityLabel(value?:string|null){return({text:'文本',image:'图像',audio:'音频',video:'视频'}as Record<string,string>)[value||'']||value||'—'}
function adoptLabel(value?:string|null){return({adopted:'已采用',discarded:'已废弃',unadopted:'未采用'}as Record<string,string>)[value||'']||'未采用'}
function shortModalityLabel(value?:string|null){return({text:'文',image:'图',audio:'音',video:'视频'}as Record<string,string>)[value||'']||value||'—'}
function modalityPair(history?:HistoryVO|null){if(!history)return '—';if(history.queryModality&&history.targetModality)return `${shortModalityLabel(history.queryModality)}搜${shortModalityLabel(history.targetModality)}`;return history.queryModality?`${shortModalityLabel(history.queryModality)}检索`:'—'}
async function openDetail(row:HistoryVO){try{detail.value=await historyDetail(row.id);detailVisible.value=true}catch(e){showToast((e as Error).message,'err')}}
async function rerun(row:HistoryVO){try{const result=await rerunHistory(row.id);router.push(`/search/results/${result.resultSetId}`)}catch(e){showToast((e as Error).message,'err')}}
function showToast(message:string,type:'info'|'ok'|'err'='info'){toastMessage.value=message;toastType.value=type;toastVisible.value=true;clearTimeout(toastTimer);toastTimer=setTimeout(()=>toastVisible.value=false,2600)}function openDev(){devOpen.value=true}function closeDev(){devOpen.value=false}
</script>
<style scoped>
.hist-card{padding:15px 18px}.detail-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin-bottom:14px}.detail-grid div{border:1px solid #eef2f8;border-radius:8px;padding:9px;background:#fbfdff}.detail-grid span{display:block;color:#98a2b3;font-size:11px;margin-bottom:3px}.table-empty{text-align:center;color:#98a2b3;padding:24px!important}.ghost-route{text-decoration:none;display:inline-flex;align-items:center}.p9-root .mini-table{--tc:150px minmax(0,1.5fr) 90px 80px 90px 180px 160px}.state-box{grid-column:1/-1}.adopt-hint{display:block;margin-top:3px;color:#98a2b3;font-size:10.5px;text-align:center}
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

.module-intro{
  display:flex;align-items:flex-start;justify-content:space-between;gap:16px;flex-wrap:wrap;
  padding:18px 20px;
  border:1px solid #e4edf8;
  border-radius:10px;
  background:#fff;
}
.module-intro h2{font-size:20px;font-weight:700;color:#111827;margin-bottom:7px;display:flex;align-items:center;gap:10px}
.module-intro p{color:var(--muted);line-height:1.65;max-width:760px;font-size:13px}

/* 筛选条 */
.filter-bar{display:flex;gap:10px;align-items:center;flex-wrap:wrap;padding:12px 14px}
.filter-bar .fb-search{
  flex:1;min-width:260px;height:34px;border:1px solid #d7e0f0;border-radius:7px;
  padding:0 12px;font-size:13px;outline:none;background:#fff;color:var(--ink);
}
.filter-bar .fb-search:focus{border-color:var(--system-blue);box-shadow:0 0 0 3px rgba(65,105,246,.12)}
.fb-sel{
  height:34px;border:1px solid #d7e0f0;border-radius:7px;background:#fff;
  font-size:12.5px;color:#344054;padding:0 8px;outline:none;
}
.btn-ghost{
  height:34px;padding:0 16px;border-radius:6px;border:1px solid #d7e0f0;
  background:#fff;color:#475467;font-size:13px;text-decoration:none;
  display:inline-flex;align-items:center;gap:6px;
}
.btn-ghost:hover{border-color:var(--system-blue);color:var(--system-blue)}
.btn-primary{
  height:34px;padding:0 20px;border:0;border-radius:6px;
  background:var(--system-blue);color:#fff;font-size:13px;font-weight:600;
  display:inline-flex;align-items:center;gap:7px;text-decoration:none;
  box-shadow:0 3px 8px rgba(65,105,246,.25);transition:all .15s;
}
.btn-primary:hover{background:#335be8}
.badge{font-size:11px;padding:2px 9px;border-radius:10px;font-weight:600;display:inline-block}
.badge.ok{background:#e8f7ec;color:var(--success)}
.badge.fail{background:#fdecec;color:var(--error)}

/* 历史列表 */
.sec-head{display:flex;align-items:center;gap:8px;margin-bottom:12px}
.sec-head .sec-title{font-size:15px;font-weight:700;color:#1f2a44}
.sec-head .sec-sub{font-size:12px;color:#9aa6bd}
.sec-head::before{content:"";width:3px;height:14px;border-radius:2px;background:var(--system-blue)}
.hist-card{padding:16px 18px}
.hist-card .filter-bar{padding:0 0 12px}
.mini-table{border:1px solid #eef2f8;border-radius:8px;overflow:hidden;background:#fff}
.mt-row{
  min-height:40px;padding:10px 12px;border-top:1px solid #eef2f8;
  display:grid;gap:8px;align-items:center;font-size:12px;
}
.mt-row:first-child{border-top:0}
.mt-row.head{color:#7a8493;background:#f4f6fb;font-weight:700;font-size:11.5px;min-height:34px}
.mt-row:not(.head):hover{background:#f8fbff}
.mt-cols-h{grid-template-columns:132px minmax(0,1.6fr) 82px 68px 66px 124px 150px}
.mt-row .c{color:var(--muted);font-size:11.5px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.mt-row .b{color:#1f2a44;font-weight:600;font-variant-numeric:tabular-nums}
.row-link{
  font-size:12px;color:var(--system-blue);background:none;border:0;text-decoration:none;
  border-bottom:1px dashed #aebdf5;padding:0;
}
.row-link:hover{border-bottom-style:solid}
.row-link:disabled{color:#b3bccc;border:0;cursor:not-allowed}

/* ================= 分页组件（列表下方 · 右对齐） ================= */
.pager-bar{display:flex;align-items:center;justify-content:flex-end;gap:6px;flex-wrap:wrap;margin-top:10px;font-size:12px;color:#667085}
.pager-total{color:#8a96ad;margin-right:4px;font-variant-numeric:tabular-nums}
.pager-btn,.pager-num{height:28px;border:1px solid #d7e0f0;background:#fff;border-radius:5px;color:#475467;font-size:12px;cursor:pointer;font-family:inherit}
.pager-btn{padding:0 10px}
.pager-num{min-width:28px;padding:0 6px;font-variant-numeric:tabular-nums}
.pager-btn:hover:not(:disabled),.pager-num:hover:not(.is-cur){border-color:#4169f6;color:#4169f6}
.pager-btn:disabled{opacity:.45;cursor:not-allowed}
.pager-num.is-cur{background:#4169f6;border-color:#4169f6;color:#fff;font-weight:700;cursor:default}
.pager-gap{color:#98a2b3;padding:0 2px}

/* 空态 / 错误态 */
.state-box{
  padding:38px 20px;text-align:center;color:#8c98ad;font-size:13px;
  background:var(--content-soft);border:1px dashed var(--content-line-strong);border-radius:8px;
}
.state-box .big{font-size:30px;display:block;margin-bottom:8px}
.state-box b{color:var(--system-blue)}
.state-box.err .big{filter:grayscale(.3)}
.state-box .acts{margin-top:12px;display:flex;gap:8px;justify-content:center}

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
  .mt-cols-h{grid-template-columns:110px minmax(0,1.4fr) 78px 58px 64px 118px 170px}
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
</style>
