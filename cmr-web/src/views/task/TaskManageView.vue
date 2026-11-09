<template>
  <div class="p6-root">
    <div class="main">
      <div class="content">
        <header class="page-head">
          <div><h1>任务管理</h1><p>查看检索、建库与评测任务；失败任务由系统自动处理，本页只读展示处理结果。</p></div>
          <button class="dev-entry" type="button" title="研发说明" @click="openDev"><span class="de-q" aria-hidden="true">?</span>研发说明</button>
        </header>

        <section class="panel" aria-label="任务列表">
          <div class="panel-head"><div class="sec-head"><span class="sec-title">任务列表</span></div><span class="hint">共 {{ total }} 条</span></div>
          <div class="chip-row">
            <button v-for="state in states" :key="state.key" class="chip" :class="{active: statusFilter===state.key}" type="button" @click="selectState(state.key)">{{ state.label }}</button>
            <span class="search-input"><input v-model="keyword" type="search" placeholder="按任务编号 / 关联对象搜索" @keyup.enter="reload(false)"></span>
          </div>
          <div class="dtable">
            <table class="dt">
              <thead><tr><th>任务ID</th><th>类型</th><th>关联对象</th><th>状态</th><th>当前步骤</th><th>失败侧</th><th>系统处理</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-if="loading"><td colspan="8" class="table-empty">加载中…</td></tr>
                <tr v-else-if="!rows.length"><td colspan="8" class="table-empty">暂无任务</td></tr>
                <tr v-for="task in rows" :key="task.id" :class="{failed: task.status==='failed'}">
                  <td><span class="mono">{{ task.bizNo }}</span></td>
                  <td>{{ typeLabel(task.type) }}</td>
                  <td><span class="mono">{{ task.refBizNo || '—' }}</span></td>
                  <td><span class="badge" :class="statusClass(task.status)">{{ statusLabel(task.status) }}</span></td>
                  <td>{{ task.currentStep || '—' }}</td>
                  <td>{{ task.failType === 'engine' ? '引擎侧' : task.failType === 'data' ? '数据侧' : '—' }}</td>
                  <td>{{ task.failDisposal || (task.status==='failed' ? '待系统处理' : '—') }}</td>
                  <td><router-link class="link" :to="`/tasks/${task.id}`">详情</router-link></td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="pager-bar">
            <button class="page-btn" type="button" :disabled="pageNum<=1" @click="changePage(pageNum-1)">上一页</button>
            <span>第 {{ pageNum }} 页 / 共 {{ pageCount }} 页</span>
            <button class="page-btn" type="button" :disabled="pageNum>=pageCount" @click="changePage(pageNum+1)">下一页</button>
          </div>
        </section>

        <section class="panel" aria-label="失败任务">
          <div class="panel-head"><div class="sec-head"><span class="sec-title">失败任务</span></div><span class="hint">共 {{ failedTotal }} 条</span></div>
          <p class="auto-note">失败由系统自动处理：引擎侧失败自动重试，数据侧失败自动转隔离；此处只展示处理结果。</p>
          <div class="dtable">
            <table class="dt">
              <thead><tr><th>任务ID</th><th>关联对象</th><th>失败原因</th><th>失败类型</th><th>系统处理</th><th>状态</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-if="failedLoading"><td colspan="7" class="table-empty">加载中…</td></tr>
                <tr v-else-if="!failedRows.length"><td colspan="7" class="table-empty">暂无失败任务</td></tr>
                <tr v-for="task in failedRows" :key="task.id">
                  <td><span class="mono">{{ task.bizNo }}</span></td><td><span class="mono">{{ task.refBizNo || '—' }}</span></td>
                  <td>{{ task.errorMsg || '—' }}</td><td>{{ task.failType === 'engine' ? '引擎侧' : '数据侧' }}</td>
                  <td>{{ task.failDisposal || '待系统处理' }}</td><td><span class="badge fail">失败</span></td>
                  <td><router-link class="link" :to="`/tasks/${task.id}`">详情</router-link></td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="pager-bar">
            <button class="page-btn" type="button" :disabled="failedPage<=1" @click="changeFailedPage(failedPage-1)">上一页</button>
            <span>第 {{ failedPage }} 页 / 共 {{ failedPageCount }} 页</span>
            <button class="page-btn" type="button" :disabled="failedPage>=failedPageCount" @click="changeFailedPage(failedPage+1)">下一页</button>
          </div>
        </section>
      </div>
    </div>
    <div class="toast" :class="{show:toastVisible,ok:toastType==='ok',err:toastType==='err'}">{{ toastMessage }}</div>
    <div class="dev-mask" :class="{ open: devOpen }" @click="closeDev"></div>
<div class="dev-drawer" :class="{ open: devOpen }" role="dialog" aria-modal="true" aria-label="研发需求说明">
  <div class="dev-head">
    <h3>研发需求说明</h3>
    <span class="dh-tag">给研发</span>
    <button type="button" class="dev-close" id="devClose" title="关闭（Esc）" aria-label="关闭" @click="closeDev">×</button>
  </div>
  <div class="dev-body dev-stack">
      <p class="help-lead">这 4 个模块是<b>后台调度、引擎与运维的交付依据</b>。任务管理正文只保留两块：<b>任务列表</b>、<b>失败任务</b>——用户在这里看任务、看失败的系统处理结果。</p>

      <div>
        <div class="hb-t">一、4 个模块与 F-CR 范围</div>
        <div class="dtable">
          <table class="dt help-table">
            <thead>
              <tr><th>模块名</th><th>F-CR 范围</th><th>覆盖方式</th></tr>
            </thead>
            <tbody>
              <tr>
                <td>检索在线优先级与配额治理</td>
                <td class="rng">F-CR-116 ~ 120</td>
                <td class="cov">后台调度策略，配额与优先级由研发配置</td>
              </tr>
              <tr>
                <td>检索熔断降级规则管理</td>
                <td class="rng">F-CR-125 ~ 126</td>
                <td class="cov">引擎侧熔断规则，由研发配置</td>
              </tr>
              <tr>
                <td>检索在线运行验收确认</td>
                <td class="rng">F-CR-121 ~ 124</td>
                <td class="cov">运维验收环节，归后台 / 运维</td>
              </tr>
              <tr>
                <td>检索失败修复与根因处置</td>
                <td class="rng">F-CR-135 ~ 142</td>
                <td class="cov">由系统自动重试 / 转隔离覆盖，本页只读展示<span class="cov-sub">引擎侧失败自动重试（最多 3 次），数据侧失败自动转隔离区；根因分析与修复验收归后台</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <p class="help-note"><b>二、覆盖说明：</b>本页覆盖 F-CR-116 ~ 126（含 121 ~ 124）与 F-CR-135 ~ 142。正文不出现模块编号与 F-CR，只以任务列表与失败任务呈现；配额、熔断、在线验收均为后台配置或运维环节。失败的修复与根因处置由系统自动重试 / 转隔离覆盖，本页只读展示处理结果。</p>
  </div>
</div>

  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { pageTasks } from '../../api'
import type { TaskVO } from '../../api/types'
import { useDictStore } from '../../stores/dict'

const dictStore=useDictStore()
const rows=ref<TaskVO[]>([]); const total=ref(0); const pageNum=ref(1); const pageSize=ref(10)
const keyword=ref(''); const statusFilter=ref(''); const loading=ref(false)
const statusDict=ref<{alias:string;label:string}[]>([]); const typeDict=ref<{alias:string;label:string}[]>([]); const disposalDict=ref<{alias:string;label:string}[]>([])
const failedRows=ref<TaskVO[]>([]); const failedTotal=ref(0); const failedPage=ref(1); const failedPageSize=ref(10); const failedLoading=ref(false)
const states=[{key:'',label:'全部'},{key:'ready',label:'就绪'},{key:'running',label:'执行中'},{key:'done',label:'完成'},{key:'failed',label:'失败'}]
const pageCount=computed(()=>Math.max(1,Math.ceil(total.value/pageSize.value)))
const failedPageCount=computed(()=>Math.max(1,Math.ceil(failedTotal.value/failedPageSize.value)))
const devOpen=ref(false); const toastVisible=ref(false); const toastMessage=ref(''); const toastType=ref<'info'|'ok'|'err'>('info')
let toastTimer:ReturnType<typeof setTimeout>|undefined
let pollTimer:ReturnType<typeof setTimeout>|undefined

onMounted(async()=>{
  [statusDict.value,typeDict.value,disposalDict.value]=await Promise.all([
    dictStore.ensure('cmr_task_status'),dictStore.ensure('cmr_task_type'),dictStore.ensure('cmr_fail_disposal')
  ])
  await reload(false)
})
onBeforeUnmount(()=>{clearTimeout(toastTimer);clearTimeout(pollTimer)})
async function reload(showMessage=true){
  loading.value=true; failedLoading.value=true
  try{
    const [page]=await Promise.all([
      pageTasks({pageNum:pageNum.value,pageSize:pageSize.value,status:statusFilter.value,keyword:keyword.value}),
      loadFailedTasks(false),
    ])
    rows.value=page.records; total.value=page.total
    scheduleAutoRefresh()
    if(showMessage)showToast('任务列表已刷新','ok')
  }catch(e){showToast((e as Error).message,'err')}finally{loading.value=false;failedLoading.value=false}
}
async function loadFailedTasks(showLoading=true){
  if(showLoading)failedLoading.value=true
  try{
    const page=await pageTasks({pageNum:failedPage.value,pageSize:failedPageSize.value,status:'failed'})
    failedRows.value=page.records; failedTotal.value=page.total
  }finally{
    if(showLoading)failedLoading.value=false
  }
}
function scheduleAutoRefresh(){clearTimeout(pollTimer);if(rows.value.some(task=>task.status==='ready'||task.status==='running')){pollTimer=setTimeout(()=>void reload(false),5000)}}
function selectState(state:string){statusFilter.value=state;pageNum.value=1;void reload(false)}
function changePage(page:number){pageNum.value=page;void reload(false)}
function changeFailedPage(page:number){failedPage.value=page;void loadFailedTasks().catch(e=>showToast((e as Error).message,'err'))}
function statusLabel(status:string){return statusDict.value.find(item=>item.alias===status)?.label||status}
function typeLabel(type:string){return typeDict.value.find(item=>item.alias===type)?.label||type}
function statusClass(status:string){return({ready:'run',running:'run',done:'ok',failed:'fail'}as Record<string,string>)[status]||'run'}
function showToast(message:string,type:'info'|'ok'|'err'='info'){toastMessage.value=message;toastType.value=type;toastVisible.value=true;clearTimeout(toastTimer);toastTimer=setTimeout(()=>toastVisible.value=false,2600)}
function openDev(){devOpen.value=true}function closeDev(){devOpen.value=false}
</script>
<style scoped>
.table-empty{text-align:center;color:#98a2b3;padding:28px 0!important}
.page-btn{border:1px solid #d9e7f8;background:#fff;border-radius:6px;height:26px;padding:0 10px;color:#475467;cursor:pointer}
.page-btn:disabled{opacity:.45;cursor:not-allowed}
.failed{background:#fffafa}
:root{
  --workspace-bg:#f6f9fe;
  --content-soft:#fbfdff;
  --content-line:#e6edf8;
  --system-blue:#4169f6;
  --system-blue-weak:#f1f5ff;
  --muted:#667085;
  --success:#34a853;
  --warning:#f0a020;
  --error:#e5484d;
  --radius:8px;
}
*{margin:0;padding:0;box-sizing:border-box}
html,body{height:100%}
body{
  font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif;
  background:var(--workspace-bg);
  color:#262626;
  font-size:14px;
  line-height:1.5;
  display:flex;
}
button{font-family:inherit;cursor:pointer}
a{color:inherit;text-decoration:none}
::selection{background:rgba(65,105,246,.16)}

/* ============ 左侧全局导航（壳层与 P1 一致） ============ */
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
.content{padding:20px 24px 32px;display:flex;flex-direction:column;gap:16px;max-width:1280px;width:100%;margin:0 auto}

.page-head{
  display:flex;align-items:flex-start;justify-content:space-between;gap:16px;flex-wrap:wrap;
  padding:18px 20px;
  border:1px solid #e4edf8;
  border-radius:10px;
  background:#fff;
}
.page-head h1{font-size:20px;font-weight:700;color:#111827;line-height:1.3}
.page-head p{font-size:13px;color:#667085;line-height:1.65;max-width:760px;margin-top:5px}

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
.panel{
  padding:16px 18px;border:1px solid #e4edf8;border-radius:var(--radius);
  background:#fff;display:flex;flex-direction:column;gap:12px;
  scroll-margin-top:12px;
}
.panel-head{display:flex;align-items:flex-start;gap:10px;justify-content:space-between}
.sec-head{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.sec-head .sec-title{font-size:15px;font-weight:700;color:#1f2a44}
.sec-head .sec-sub{font-size:12px;color:#9aa6bd}
.sec-head::before{content:"";width:3px;height:14px;border-radius:2px;background:var(--system-blue)}
.sec-flash{animation:secFlash 1.1s ease-out}
@keyframes secFlash{0%{box-shadow:0 0 0 3px rgba(65,105,246,.2)}100%{box-shadow:0 0 0 0 rgba(65,105,246,0)}}

/* 过滤 chips / 搜索 */
.chip-row{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.chip{
  border:1px solid #d7e0f0;border-radius:14px;padding:4px 13px;background:#fff;
  color:#475467;font-size:12px;white-space:nowrap;
}
.chip:hover{border-color:#aebdf5;color:var(--system-blue)}
.chip.active{background:var(--system-blue);color:#fff;border-color:var(--system-blue);font-weight:600}
.search-input input{
  height:30px;border:1px solid #cfd8e8;border-radius:5px;background:#fff;
  padding:0 11px;font-size:12.5px;outline:none;min-width:180px;
}
.search-input input:focus{border-color:#8fa3ff;box-shadow:0 0 0 2px rgba(65,105,246,.12)}

/* 数据表格 */
.dtable{width:100%;border:1px solid #eef2f8;border-radius:8px;overflow:auto}
table.dt{width:100%;min-width:720px;border-collapse:collapse;font-size:12.5px}
table.dt th{
  background:#f4f6fb;color:#7a8493;font-weight:600;font-size:11.5px;text-align:left;
  padding:9px 12px;border-bottom:1px solid #e6edf8;white-space:nowrap;
}
table.dt td{padding:9px 12px;border-bottom:1px solid #eef2f8;color:#344054;vertical-align:middle}
table.dt tbody tr:last-child td{border-bottom:0}
table.dt tbody tr.rowlink{cursor:pointer}
table.dt tbody tr.rowlink:hover td{background:#f8fbff}
table.dt td .mono{font-variant-numeric:tabular-nums;color:#475467}
table.dt td.tid{font-weight:600;color:#273244;white-space:nowrap}
table.dt td.tq{max-width:230px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
#taskBody td.act{white-space:nowrap}
#failBody td.tq{max-width:180px}
#failBody td.sys{white-space:nowrap}

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

/* 系统自动处理说明（只读） */
.auto-note{
  font-size:12px;color:#475467;line-height:1.7;
  background:var(--system-blue-weak);border:1px dashed #cdd9f5;
  border-radius:8px;padding:9px 12px;
}

/* 按钮 */
.btn-mini{
  height:26px;padding:0 11px;border-radius:5px;border:1px solid #d7e0f0;
  background:#fff;color:#475467;font-size:11.5px;white-space:nowrap;display:inline-flex;align-items:center;
}
.btn-mini:hover{border-color:var(--system-blue);color:var(--system-blue)}

/* 徽章 */
.badge{font-size:11px;padding:2px 8px;border-radius:10px;font-weight:600;display:inline-block;white-space:nowrap}
.badge.ok{background:#e8f7ec;color:var(--success)}
.badge.fail{background:#fdecec;color:var(--error)}
.badge.run{background:#e9f0ff;color:var(--system-blue)}
.badge.wait{background:#fff8e1;color:#c99a12}
.badge.gray{background:#f0f2f7;color:#8c98ad}

/* 空态 / 辅助文本 */
.empty{padding:26px 20px;text-align:center;color:#9aa6bd;font-size:12.5px}
.empty .e-ico{font-size:24px;opacity:.45;margin-bottom:8px}
.empty .e-act{margin-top:10px}

/* 权限态 */
.perm-gate{display:none;padding:60px 20px}
.perm-gate.show{display:flex;justify-content:center}
.pg-card{
  text-align:center;padding:44px 40px;border:1px solid #e4edf8;border-radius:12px;
  background:linear-gradient(180deg,#fff 0%,#f8fbff 100%);max-width:720px;
}
.pg-ico{
  width:54px;height:54px;border-radius:14px;background:var(--system-blue-weak);
  display:grid;place-items:center;font-size:24px;margin:0 auto 14px;
}
.pg-card h3{font-size:17px;color:#1f2a44;margin-bottom:8px}
.pg-card p{font-size:12.5px;color:var(--muted)}

/* 研发说明弹层 */
.modal-mask{
  position:fixed;inset:0;z-index:180;background:rgba(20,32,70,.36);
  display:none;align-items:flex-start;justify-content:center;
  padding:44px 16px 24px;overflow-y:auto;
}
.modal-mask.open{display:flex}
.modal{
  width:100%;max-width:840px;background:#fff;border:1px solid #e4edf8;
  border-radius:12px;box-shadow:0 20px 50px rgba(20,32,70,.26);
  overflow:hidden;animation:modalIn .16s ease-out;
}
@keyframes modalIn{from{opacity:0;transform:translateY(-8px)}to{opacity:1;transform:none}}
.modal-head{
  display:flex;align-items:center;justify-content:space-between;gap:10px;
  padding:13px 16px;border-bottom:1px solid #eef2f8;background:#fbfdff;
}
.modal-x{
  width:26px;height:26px;flex-shrink:0;border:0;border-radius:6px;
  background:transparent;color:#8a96ad;font-size:18px;line-height:1;
}
.modal-x:hover{background:#eef2fb;color:#475467}
.help-lead{
  font-size:12.5px;color:#475467;line-height:1.75;
  background:var(--system-blue-weak);border:1px dashed #cdd9f5;
  border-radius:8px;padding:9px 12px;
}
.help-lead b{color:#1f2a44;font-weight:700}
.hb-t{display:flex;align-items:center;gap:7px;font-size:12.5px;font-weight:700;color:#1f2a44;margin-bottom:6px}
.hb-t::before{content:"";width:3px;height:12px;border-radius:2px;background:var(--system-blue)}
.help-table th{font-size:10.5px;padding:7px 9px}
.help-table td{padding:7px 9px;font-size:11.5px;line-height:1.55;vertical-align:top}
.help-table td.rng{font-family:ui-monospace,Consolas,"Courier New",monospace;font-size:10.5px;color:#5b6472;white-space:nowrap}
.help-table td.cov{color:#1e7f4b;font-weight:600}
.help-table .cov-sub{display:block;color:#475467;font-weight:400;margin-top:2px}
.help-note{font-size:11.5px;color:#475467;line-height:1.75;border-top:1px dashed #eef2f8;padding-top:9px}
.help-note b{color:#1f2a44;font-weight:700}

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
  .content{padding:14px 14px 28px}
  .page-head{flex-direction:column;gap:8px}
}
@media (max-width:560px){
  .modal-mask{padding:20px 10px 16px}
  .modal-head{padding:11px 12px}
}
</style>
