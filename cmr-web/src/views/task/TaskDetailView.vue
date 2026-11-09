<template>
  <div class="p3-root">
    <div class="main"><div class="content">
      <section class="module-intro"><div><h2>任务详情</h2><p>查看单个任务的基础信息、执行结果与运行材料；从任务管理列表或结果页进入。</p></div><button class="dev-entry" type="button" @click="openDev"><span class="de-q" aria-hidden="true">?</span>研发说明</button></section>
      <section v-if="errorMessage" class="card task-card"><div class="empty-state">{{ errorMessage }}</div></section>
      <template v-else-if="detail">
        <section class="card task-card">
          <div class="tc-top"><span class="tc-crumb"><router-link to="/tasks">任务管理</router-link> / 任务详情</span></div>
          <div class="sec-head"><span class="sec-title">任务信息</span></div>
          <div class="tc-grid">
            <div class="tc-field"><div class="k">任务编号</div><div class="v mono">{{ detail.task.bizNo }}</div></div>
            <div class="tc-field"><div class="k">任务类型</div><div class="v">{{ typeLabel }}</div></div>
            <div class="tc-field"><div class="k">关联对象</div><div class="v mono"><span>{{ detail.task.refBizNo || '—' }}</span><router-link v-if="detail.task.refRoute" class="ref-link" :to="detail.task.refRoute">{{ refActionLabel }}</router-link></div></div>
            <div class="tc-field"><div class="k">当前步骤</div><div class="v">{{ detail.task.currentStep || '—' }}</div></div>
            <div class="tc-field"><div class="k">状态</div><div class="v"><span class="badge" :class="statusClass">{{ statusLabel }}</span></div></div>
            <div class="tc-field"><div class="k">创建时间</div><div class="v">{{ detail.task.createTime || '—' }}</div></div>
            <div class="tc-field"><div class="k">开始时间</div><div class="v">{{ detail.task.startedTime || '—' }}</div></div>
            <div class="tc-field"><div class="k">完成时间</div><div class="v">{{ detail.task.finishedTime || '—' }}</div></div>
          </div>
        </section>
        <section class="card res-card">
          <div class="sec-head"><span class="sec-title">结果</span><span class="sec-sub">{{ detail.task.status==='failed'?'本次运行失败':'本次执行结果' }}</span></div>
          <div class="res-body">
            <div v-if="detail.task.status!=='failed'" class="res-ok">
              <div class="res-count"><span class="res-k">步骤日志</span><b>{{ detail.logs.length }}</b><span>条</span></div>
              <router-link v-if="detail.task.type==='search' && detail.task.refRoute" class="btn-primary" :to="detail.task.refRoute">查看结果</router-link>
              <router-link v-else-if="detail.task.refRoute" class="btn-primary" :to="detail.task.refRoute">{{ refActionLabel }}</router-link>
              <router-link class="btn-ghost" to="/tasks">返回任务管理</router-link>
            </div>
            <div v-else class="res-fail"><span class="f-ico">⚠️</span><div class="fail-main"><div class="fail-k">失败原因</div><p>{{ detail.task.errorMsg || '未记录失败原因' }}</p></div><router-link class="btn-ghost" to="/tasks">查看失败任务</router-link></div>
          </div>
        </section>
        <section class="card mat-card">
          <div class="sec-head"><span class="sec-title">材料</span><span class="ro-tag">只读</span></div>
          <div class="mat-table">
            <div class="mat-row head"><div>步骤</div><div>说明 / 算子输出</div><div>生成时间</div></div>
            <div v-if="!detail.logs.length" class="mat-row"><div colspan="3" class="empty-cell">暂无材料</div></div>
            <div v-for="log in pagedLogs" :key="log.id" class="mat-row"><div>{{ log.step }}</div><div class="log-message">{{ log.message || log.operatorOutputJson || '—' }}</div><div>{{ log.createTime || '—' }}</div></div>
          </div>
          <div class="pager-bar"><button class="page-btn" type="button" :disabled="pageNum<=1" @click="changePage(pageNum-1)">上一页</button><span>第{{pageNum}}页 / 共{{pageCount}}页</span><button class="page-btn" type="button" :disabled="pageNum>=pageCount" @click="changePage(pageNum+1)">下一页</button></div>
        </section>
      </template>
    </div></div>
    <div class="dev-mask" :class="{open:devOpen}" @click="closeDev"></div>
<div class="dev-drawer" :class="{open:devOpen}" role="dialog" aria-modal="true" aria-label="研发需求说明">
  <div class="dev-head">
    <h3>研发需求说明</h3>
    <span class="dh-tag">给研发</span>
    <button type="button" class="dev-close" id="devClose" title="关闭（Esc）" aria-label="关闭" @click="closeDev">×</button>
  </div>
  <div class="dev-body">
      <div class="dev-card" id="dc-ov">
        <div class="dc-head"><span class="dc-id" style="background:#1f5fd4">总览</span><span class="dc-name">页面范围 / 闭环 / 演示说明</span></div>
        <div class="dc-body">
          <div class="dev-ov"><h4>本页范围【设计】</h4><p>本页承载任务信息 + 结果 + 材料只读。</p></div>
          <div class="dev-ov"><h4>页面闭环【研发】</h4><p><a class="dev-xref" href="P6_任务管理_v5.0_20261025.html">P6</a> 任务管理进入 → 本页任务信息 / 结果 / 材料 → <a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a> 查看结果；失败任务回 <a class="dev-xref" href="P6_任务管理_v5.0_20261025.html?tab=fail">P6</a> 失败任务区处置。</p></div>
          <div class="dev-ov"><h4>演示与编号【研发】</h4><p>本页为页内模拟，不请求真实引擎；任务、结果条数与材料均为示例数据，仅用于原型演示。编号按《平台相关ID生成规范-1026》32.1：任务 TASK / 结果集 RST / 材料 MTL，等宽可复制。</p></div>
        </div>
      </div>
      <div class="dev-card" id="dc-info">
        <div class="dc-head"><span class="dc-id">核心</span><span class="dc-name">任务信息</span></div>
        <div class="dc-body">
          <div class="dc-row"><div class="dc-k">职责</div><div class="dc-v">展示单个任务的任务信息，提供返回任务列表入口。</div></div>
          <div class="dc-row"><div class="dc-k">入口/前置</div><div class="dc-v">从 <a class="dev-xref" href="P6_任务管理_v5.0_20261025.html">P6</a> 任务列表或 <a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a>「查看任务上下文」进入（携 ?task=）。</div></div>
          <div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>任务记录：query、模态对、业务场景、策略版本、状态、发起时间；任务编号用于跳转。</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">处理规则</div><div class="dc-v">6 项只读展示；按 URL 的 ?task= 载入对应任务，结果区与材料区同步呈现。</div></div>
          <div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>任务信息 6 项 + 状态徽章</li></ul></div></div>
          <div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v">无提交、无异步；?task= 不在演示任务内时回落到默认成功任务。</div></div>
          <div class="dc-row"><div class="dc-k">状态</div><div class="dc-v">成功 / 失败。</div></div>
          <div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">上游=<a class="dev-xref" href="P6_任务管理_v5.0_20261025.html">P6</a> 任务列表、<a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a> 任务上下文；下游=结果区。</div></div>
          <div class="dc-row"><div class="dc-k">检查点</div><div class="dc-v">6 项内容正确；?task= 正确载入；成功/失败结果与材料同步。</div></div>
        </div>
      </div>
      <div class="dev-card" id="dc-result">
        <div class="dc-head"><span class="dc-id">核心</span><span class="dc-name">结果</span></div>
        <div class="dc-body">
          <div class="dc-row"><div class="dc-k">职责</div><div class="dc-v">按任务状态展示结果条数与结果入口，或展示失败原因与处置入口。</div></div>
          <div class="dc-row"><div class="dc-k">入口/前置</div><div class="dc-v">任务信息卡下方常驻。</div></div>
          <div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>任务状态、结果条数（成功）、失败原因（失败）</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">处理规则</div><div class="dc-v">成功 → 展示结果条数 +「查看结果」，跳 <a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a> 并携 ?task=；失败 → 展示一句话失败原因 +「查看失败任务」，跳 <a class="dev-xref" href="P6_任务管理_v5.0_20261025.html?tab=fail">P6</a> 失败任务区（携 ?task=&amp;tab=fail）。</div></div>
          <div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>结果条数 / 失败原因 + 跳转入口</li></ul></div></div>
          <div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v">失败任务只展示失败原因与「查看失败任务」入口。</div></div>
          <div class="dc-row"><div class="dc-k">状态</div><div class="dc-v">成功 / 失败。</div></div>
          <div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">下游=<a class="dev-xref" href="P2_结果页_v5.0_20261025.html">P2</a> 结果页、<a class="dev-xref" href="P6_任务管理_v5.0_20261025.html?tab=fail">P6</a> 失败任务区。</div></div>
          <div class="dc-row"><div class="dc-k">检查点</div><div class="dc-v">成功跳转携 ?task=；失败跳转携 ?task=&amp;tab=fail；按钮文案为「查看结果」「查看失败任务」。</div></div>
        </div>
      </div>
      <div class="dev-card" id="dc-mat">
        <div class="dc-head"><span class="dc-id">核心</span><span class="dc-name">材料（只读）</span></div>
        <div class="dc-body">
          <div class="dc-row"><div class="dc-k">职责</div><div class="dc-v">列出本次运行产生的材料，只读展示。</div></div>
          <div class="dc-row"><div class="dc-k">入口/前置</div><div class="dc-v">结果区下方常驻。</div></div>
          <div class="dc-row k-in"><div class="dc-k">输入</div><div class="dc-v"><ul><li>材料名称、说明、生成时间</li></ul></div></div>
          <div class="dc-row"><div class="dc-k">处理规则</div><div class="dc-v">固定 4 类材料=查询快照 / 策略快照 / 结果清单 / 执行日志；按运行顺序只读排列；失败运行未生成的结果清单以「—」标注。</div></div>
          <div class="dc-row k-out"><div class="dc-k">输出</div><div class="dc-v"><ul><li>材料只读列表（名称 / 说明 / 时间）</li></ul></div></div>
          <div class="dc-row k-err"><div class="dc-k">异常与边界</div><div class="dc-v">无写操作；材料缺失时只保留说明。</div></div>
          <div class="dc-row"><div class="dc-k">状态</div><div class="dc-v">成功=4 类齐全；失败=结果清单未生成。</div></div>
          <div class="dc-row k-dep"><div class="dc-k">上下游依赖</div><div class="dc-v">上游=本次运行；下游=无（只读）。</div></div>
          <div class="dc-row"><div class="dc-k">检查点</div><div class="dc-v">4 类材料齐全且为只读；名称 / 说明 / 时间正确。</div></div>
        </div>
      </div>
  </div>
</div>
  </div>
</template>
<script setup lang="ts">
import {computed,onMounted,ref} from 'vue';import{useRoute}from'vue-router';import{taskDetail}from'../../api';import type{TaskDetailVO}from'../../api/types';import{useDictStore}from'../../stores/dict'
const route=useRoute();const dictStore=useDictStore();const detail=ref<TaskDetailVO|null>(null);const errorMessage=ref('');const loading=ref(true);const typeDict=ref<{alias:string;label:string}[]>([]);const statusDict=ref<{alias:string;label:string}[]>([]);const pageNum=ref(1);const pageSize=ref(8);const devOpen=ref(false)
const taskId=computed(()=>Number(route.params.id));const refActionLabel=computed(()=>detail.value?.task.type==='search'?'查看结果':detail.value?.task.type==='build'?'查看数据集':detail.value?.task.type==='eval'?'查看评测':'查看关联对象');const typeLabel=computed(()=>typeDict.value.find(x=>x.alias===detail.value?.task.type)?.label||detail.value?.task.type||'—');const statusLabel=computed(()=>statusDict.value.find(x=>x.alias===detail.value?.task.status)?.label||detail.value?.task.status||'—');const statusClass=computed(()=>({ready:'run',running:'run',done:'ok',failed:'fail'}as Record<string,string>)[detail.value?.task.status||'']||'run');const pageCount=computed(()=>Math.max(1,Math.ceil((detail.value?.logs.length||0)/pageSize.value)));const pagedLogs=computed(()=>{const list=detail.value?.logs||[];return list.slice((pageNum.value-1)*pageSize.value,pageNum.value*pageSize.value)})
onMounted(async()=>{[typeDict.value,statusDict.value]=await Promise.all([dictStore.ensure('cmr_task_type'),dictStore.ensure('cmr_task_status')]);try{detail.value=await taskDetail(taskId.value)}catch(e){errorMessage.value=(e as Error).message}finally{loading.value=false}})
function changePage(p:number){pageNum.value=p}function openDev(){devOpen.value=true}function closeDev(){devOpen.value=false}
</script>
<style scoped>
.empty-state{padding:48px;text-align:center;color:#98a2b3}
.empty-cell{grid-column:1/-3;color:#98a2b3;text-align:center;padding:24px}
.log-message{white-space:pre-wrap;word-break:break-all;max-height:90px;overflow:auto}
.page-btn{border:1px solid #d9e7f8;background:#fff;border-radius:6px;height:26px;padding:0 10px;color:#475467;cursor:pointer}.page-btn:disabled{opacity:.45;cursor:not-allowed}
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

.sec-head{display:flex;align-items:center;gap:8px}
.sec-head .sec-title{font-size:15px;font-weight:700;color:#1f2a44}
.sec-head .sec-sub{font-size:12px;color:#9aa6bd}
.sec-head::before{content:"";width:3px;height:14px;border-radius:2px;background:var(--system-blue)}
.ro-tag{font-size:10.5px;font-weight:600;color:#7a8493;background:#f2f4f8;border-radius:3px;padding:1px 6px;white-space:nowrap}

.btn-primary{
  height:34px;padding:0 20px;border:0;border-radius:6px;
  background:var(--system-blue);color:#fff;font-size:13px;font-weight:600;
  display:inline-flex;align-items:center;gap:7px;text-decoration:none;
  box-shadow:0 3px 8px rgba(65,105,246,.25);transition:all .15s;
}
.btn-primary:hover{background:#335be8;box-shadow:0 4px 12px rgba(65,105,246,.32)}
.btn-ghost{
  height:34px;padding:0 16px;border-radius:6px;border:1px solid #d7e0f0;
  background:#fff;color:#475467;font-size:13px;text-decoration:none;
  display:inline-flex;align-items:center;gap:6px;
}
.btn-ghost:hover{border-color:var(--system-blue);color:var(--system-blue)}

.badge{font-size:11px;padding:2px 9px;border-radius:10px;font-weight:600;display:inline-block}
.badge.ok{background:#e8f7ec;color:var(--success)}
.badge.fail{background:#fdecec;color:var(--error)}

/* 任务信息卡 */
.task-card{padding:16px 18px}
.tc-top{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:14px}
.tc-crumb{font-size:12.5px;color:var(--muted)}
.tc-crumb a{color:var(--system-blue);text-decoration:none}
.tc-crumb a:hover{text-decoration:underline}
.task-card .sec-head{margin-bottom:12px}
.tc-grid{
  display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px 18px;
  padding:13px 14px;border:1px solid #eaf0f9;border-radius:8px;background:var(--content-soft);
}
.tc-field .k{font-size:11px;color:#9aa6bd;margin-bottom:3px}
.tc-field .v{font-size:13px;color:#1f2a44;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}

/* 结果区 */
.res-card{padding:16px 18px}
.res-body{margin-top:12px}
.res-ok{
  display:flex;align-items:center;gap:16px;flex-wrap:wrap;
  padding:14px 16px;border:1px solid #eaf0f9;border-radius:8px;background:var(--content-soft);
}
.res-count{display:flex;align-items:baseline;gap:7px}
.res-count b{font-size:26px;color:#1f2a44;font-variant-numeric:tabular-nums;line-height:1}
.res-count span{font-size:13px;color:var(--muted)}
.res-count .res-k{font-size:12px;color:#9aa6bd;margin-right:2px}
.res-ok .btn-primary{margin-left:auto}
.ref-link{margin-left:8px;color:var(--system-blue);font-weight:600;text-decoration:none}
.ref-link:hover{text-decoration:underline}
.res-fail{
  display:flex;align-items:center;gap:14px;flex-wrap:wrap;
  padding:13px 16px;border:1px solid #f3c4c6;border-radius:8px;background:#fff7f7;
}
.res-fail .f-ico{font-size:18px}
.res-fail .btn-ghost{margin-left:auto}
.fail-main{flex:1;min-width:260px}
.fail-k{font-size:11px;color:#b42318;font-weight:700;margin-bottom:3px}
.fail-main p{font-size:12.5px;color:#8c5560;line-height:1.6}

/* 材料只读列表 */
.mat-card{padding:16px 18px}
.mat-table{margin-top:12px;border:1px solid #eef2f8;border-radius:8px;overflow:hidden;background:#fff}
.mat-row{
  display:grid;grid-template-columns:150px minmax(0,1fr) 140px;gap:12px;align-items:center;
  padding:11px 14px;border-top:1px solid #eef2f8;font-size:12.5px;color:#475467;
}
.mat-row:first-child{border-top:0}
.mat-row.head{background:#f4f6fb;color:#7a8493;font-weight:700;font-size:11.5px;padding:9px 14px}
.mat-row .name{color:#1f2a44;font-weight:600}
.mat-row .desc{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.mat-row .time{color:#667085;font-variant-numeric:tabular-nums;white-space:nowrap}
.mat-row.is-empty .name{color:#9aa6bd;font-weight:400}

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
.dev-xref{color:#1f5fd4;font-weight:700;text-decoration:none;border-bottom:1px dashed #9dbcf5;padding:0 1px;white-space:nowrap}
.dev-xref:hover{background:#eaf1fe;border-bottom-style:solid}

@media (max-width:1100px){
  .tc-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
  .mat-row{grid-template-columns:120px minmax(0,1fr) 120px}
}
</style>
