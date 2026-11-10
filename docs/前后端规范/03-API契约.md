# 跨模态检索中心 · REST API 契约（P1–P9）

> Base：`/hs-api/cmr`。统一信封 `{"code":0,"msg":"success","data":...}`；`code=1` 业务失败（msg 带原因）。
> 认证：除 `POST /auth/login` 外均需 `Authorization: Bearer <jwt>`；权限码标注于各接口（角色映射见 04）。
> 分页：请求 `pageNum/pageSize`，响应 `data: {records:[], total, pageNum, pageSize}`。
> 编号字段（bizNo/resultSetId 等）前端一律 monospace 展示 + 一键复制（ID 规范）。

## 〇、认证与公共

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| POST | /auth/login | 公开 | {username,password} → {token, user}；12h |
| GET | /auth/me | 登录即可 | 当前用户+角色+权限码列表 |
| POST | /auth/logout | 登录即可 | token 加黑名单 |
| GET | /file?path=runtime/... | cmr:file:read | 共享卷文件（白名单+防穿越）；支持 Range；媒体预览/材料下载统一入口 |
| POST | /file/upload | cmr:search:create | 查询文件上传（以图/音/视搜检索）→ {path:"dataset/query/xxx"} |
| POST | /file/package | cmr:file:read | 批量打包共享卷文件：{paths:[...]} → ZIP（单次 ≤200） |
| GET | /dict/{type} | 登录即可 | 字典项（场景/状态/类型枚举统一来源） |

## 一、库管理域（P4）

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| GET | /datasets | cmr:dataset:page | 分页：status/scene/name/时间筛选；含当前索引与激活状态 |
| POST | /datasets | cmr:dataset:create | 登记接入：{datasourceId?, name, scene, fileUploadId?/datasetPath?} → 创建 DS+TASK(type=build)，异步跑 401→406 |
| GET | /datasets/{id} | cmr:dataset:detail | 详情：状态、评分、模态统计、报告、血缘摘要、任务 |
| POST | /datasets/{id}/adjudicate | cmr:dataset:adjudicate | 评定挂起裁决：{pass:true/false, note}；通过→续跑建库，不通过→已剔除 |
| POST | /datasets/{id}/confirm-production | cmr:dataset:confirm | 召回率不足人工确认投产：激活该索引→可检索 |
| POST | /datasets/{id}/rebuild | cmr:dataset:create | 从断点/全链重跑（幂等，新 TASK） |
| GET | /datasources | cmr:dataset:page | 数据源列表（含 datasetCount、连接状态、必填自检、向量化/总条数、最近同步时间） |
| POST | /datasources | cmr:dataset:create | 登记数据源：{name, endpoint(http/https), protocol, authType, authConfig}；token/basic 认证信息必填 |
| POST | /datasources/{id}/test | cmr:dataset:create | 测试连接：返回 connected/statusCode/latencyMs/message，并持久化最近结果 |
| GET | /datasources/contract | cmr:dataset:page | 上游对接契约（只读）：数据集/条目接口、必填字段、模态规则、推送方式说明 |
| GET | /datasources/{id}/datasets | cmr:dataset:page | 拉取上游数据集列表：GET {base}/datasets；dataset_id/name/item_count 必填，modal_types 可选 |
| POST | /datasources/{id}/sync | cmr:dataset:update | 同步元数据（只发现，不建库）：要求数据源已连接；黑名单命中的来源/数据集跳过并返回 skippedBlacklisted；写入 required_ok/upstream_dataset_id/total_count/last_sync_time |
| POST | /datasources/{id}/ingest | cmr:dataset:create | 接入上游数据集：{datasetId, name?, version?, scene?} → 拉取条目内容落共享卷 `dataset/ds/{数据源号}/{datasetId}`，登记本地数据集并触发 401→406；要求已连接且 4 项必填通过；黑名单数据集不可接入 |
| GET | /datasource-blacklist | cmr:dataset:page | 数据源黑名单列表 |
| POST | /datasource-blacklist | cmr:dataset:create | 拉黑数据源 {sourceName, reason} |
| DELETE | /datasource-blacklist/{id} | cmr:dataset:update | 解除拉黑 |
| GET | /indexes?datasetId= | cmr:dataset:detail | 索引历史（candidate/active/retired + 406 结果） |
| POST | /indexes/{id}/activate | cmr:dataset:confirm | 回滚激活（旧 retired 索引重新激活） |

状态机约束在后端强制（02 文档）；非法迁移返回 code=1"不允许的状态流转"。

## 二、检索域（P1 检索台 / P2 结果页 / P9 检索历史）

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| GET | /search/suggest?scene=&prefix= | 登录即可 | 推荐词联想（enabled 词按 sort） |
| POST | /search/understand | cmr:search:create | 407 代理：{query} 或 {queryFile} → {needClarify, clarifyOptions[], queryModality, hint} |
| POST | /search | cmr:search:create | 执行检索：{query/queryFile, scene, targetModalities?, clarified?, clarifiedQuery?, weights?, rerank?, topK?, strategyId?, threshold?} → 结果集（条目：itemId/rank/score/modality/source/alignScore/qualityGrade/vote/favorited；qualityGrade 继承 P4 数据集 402 评定分 A≥0.90/B≥0.80/C≥0.70/其余 D；+策略快照+resultSetId+taskId+srhNo）；strategyId 不传用场景生效策略，threshold 不传用策略版本阈值；文本 query 少于 4 字且 clarified≠true 时返回「请先完成澄清」，queryFile 检索跳过澄清；屏蔽条目与数据源黑名单来源不下发，itemCount 为实际下发条数 |
| GET | /search/results/{resultSetId} | cmr:search:page | 结果集快照回看（P2）；条目名称按 ID 实时回查，条目已删显示「已删除」占位；被屏蔽条目不返回 |
| POST | /search/results/{resultSetId}/rerun | cmr:search:create | P2 错误态重跑：原参数重放，生成新 SRH/RST |
| POST | /search/results/{resultSetId}/adopt | cmr:search:adopt | 未采用→已采用 |
| POST | /search/results/{resultSetId}/discard | cmr:search:adopt | 未采用→已废弃（终态，二者互斥不可逆） |
| POST | /search/results/{resultSetId}/click | cmr:search:page | 条目点击埋点 {rank}（维护 clicked 冗余） |
| POST | /search/tasks/{taskNo}/vote | cmr:search:update | 条目赞踩：{itemId, vote: up/down/none} → {vote, upCount, downCount}；任务封存后拒绝 |
| POST | /search/favorites | cmr:search:update | 收藏/取消收藏：{itemId, itemName, source, taskNo} → true/false |
| GET | /search/favorites | cmr:search:page | 我的收藏列表 |
| GET | /search-summary/{taskNo} | cmr:search:page | P3 搜索结果摘要：Top-20 条目 + 赞/踩/收藏 + 结果数 + 到期时间；30 天外标记 expired |
| POST | /search-summary/{taskNo}/seal | cmr:search:update | 封存任务：sealed=T，赞踩锁定 |
| GET | /search-behaviors | cmr:search:page | P6 搜索行为流水：keyword/operatorName/zeroResult/from/to；含任务号、query、模态、操作人、策略+版本、结果数/零结果、赞踩、状态/封存 |
| GET | /search-behaviors/blocked-items | cmr:search:page | 屏蔽条目列表 |
| POST | /search-behaviors/blocked-items/{id}/unblock | cmr:search:update | 解除屏蔽 |
| GET | /search-history | cmr:search:page | 兼容旧入口（已并入 /search-behaviors） |
| GET | /search-history/{id} | cmr:search:page | 详情（含条目与策略快照） |
| POST | /search-history/{id}/rerun | cmr:search:create | 再次执行（原参数重放，新 SRH） |

检索日志可见窗口（02 保留策略）：30 天滚动删除（含 search 型任务）；被反馈单或评测集条目关联（被踩/被标记/评测溯源）的检索永久保留可查。

## 三、任务域（P3 任务详情 / P6 任务管理）

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| GET | /tasks | cmr:task:page | 分页：type/status/失败处置/时间；列表两列：任务状态、失败处置（无失败显示 —） |
| GET | /tasks/{id} | cmr:task:detail | 任务信息 + 步骤日志（材料：各环节 outputFile 可经 /file 查看）+ 关联对象链接 |

任务只读展示（数据流 3.3：失败处置系统自动，不设人工改状态接口）。

## 四、策略域（P5）

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| GET | /strategies | cmr:strategy:page | 列表：场景/状态筛选 |
| GET | /strategies/model-options | cmr:strategy:page | 向量模型白名单、当前索引模型与一致性状态 |
| GET | /strategies/options | cmr:search:create | P1 策略下拉：生效策略的 id/bizNo/name/scene/preset/versionNo/threshold/rerank/topK/paramSummary |
| POST | /strategies | cmr:strategy:create | 新建：{name, scene, weights, rerank, topK, threshold(0.30–0.99，默认 0.60)} 存草稿 |
| POST | /strategies/{id}/offline | cmr:strategy:publish | 下线：{reason: tuning/poor/replaced/other}；预置策略不可下线 |
| POST | /strategies/{id}/online | cmr:strategy:publish | 重新上线（回到 draft，清空下线原因/时间） |
| GET | /strategies/{id} | cmr:strategy:detail | 详情+当前版本参数+向量段（只读回显激活索引 modelVersion） |
| PUT | /strategies/{id} | cmr:strategy:update | 编辑草稿参数（生效版本配置不可直接改，先存草稿） |
| POST | /strategies/{id}/publish | cmr:strategy:publish | 发布草稿→版本递增立即生效（下一次检索） |
| POST | /strategies/{id}/discard-draft | cmr:strategy:update | 放弃草稿（回到生效版本态） |
| GET | /strategies/{id}/versions | cmr:strategy:detail | 版本历史 |
| GET | /strategies/{id}/diff?from=&to= | cmr:strategy:detail | 变更评估：两版本参数 diff |
| GET | /strategies/{id}/consistency | cmr:strategy:detail | 一致性检查：权重合计/TopK/阈值/重排/模型（5 项 + passed） |
| POST | /strategies/{id}/rollback | cmr:strategy:publish | 回滚到 {versionNo}：配置/状态/版本号一起回写，出核验回执 |
| GET | /strategies/active?scene= | cmr:search:create | 检索台回显当前生效策略（P1 策略提示区） |

## 五、评测域（P8）

v5 口径：**一条 query 一条评测**，以历史检索为基准，重放后对比踩数/踩率/结果数。

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| GET | /evaluations | cmr:eval:page | 评测列表：query、基准任务、基准结果/踩数、重放次数、结论（degraded/normal/unevaluated）、退化原因、最近重放时间 |
| POST | /evaluations | cmr:eval:create | 新建评测：{baseTaskNo}；同一 query 只保留一条（重复创建返回已有记录） |
| GET | /evaluations/{id} | cmr:eval:detail | 基准 vs 最近重放对比 + 结论 + 原因 + 踩率变化/结果数变化 |
| POST | /evaluations/{id}/replay | cmr:eval:create | 重放：{strategyId?}（不传沿用基准快照）；生成新 SRH（写入 eval_id/replay_of_task_no） |

退化规则（任一命中即 degraded）：踩数上升 / 踩率上升 ≥ 5 个百分点 / 结果数下降 ≥ 10%；未重放为 unevaluated。

以下为 v4 评测集/评测执行接口，保留兼容：

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| GET | /eval-sets | cmr:eval:page | 评测集列表（状态/名称） |
| GET | /eval-sets/preview | cmr:eval:create | 创建前预览：筛选条件 → {count, items[10]} |
| POST | /eval-sets | cmr:eval:create | 新建：{name, filters:{zeroResult,noClick,reSearch 至少一项}, dateRange?} → 按条件筛检索历史生成条目，状态=标注中 |
| GET | /eval-sets/{id} | cmr:eval:detail | 详情+条目分页（query/答案/已标） |
| PUT | /eval-sets/{id}/items/{itemId} | cmr:eval:update | 标注：{answers:[...]}（itemId 或来源文件名） |
| POST | /eval-sets/{id}/complete | cmr:eval:update | 完成标注（每条已标才允许）→ 已完成 |
| POST | /eval-runs | cmr:eval:run | 执行评测：{evalSetId, target:{type:strategyVersion/system, strategyVersionId?}, k?, lines?} → 创建 EVAL 记录+TASK(type=eval) 异步跑 409 |
| GET | /eval-runs | cmr:eval:page | 评测历史：结论/指标/对比（improved 标"较上次提升"） |
| GET | /eval-runs/{id} | cmr:eval:detail | 报告：指标、验收线、未达标清单、逐条明细 |
| GET | /eval-runs/{id}/per-query | cmr:eval:detail | 逐条明细分页 |

筛选条件口径：zeroResult=结果条数 0 的检索；noClick=有条目但零点击（依赖点击埋点）；reSearch=同 query（归一化后）出现 ≥2 次。

## 六、运营域（P7）

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| POST | /feedbacks | cmr:feedback:create | 登记反馈：{entryType: mismatch/zero, sourceTaskNo, sourceQuery?, items?: [{itemId,name,source}], description?}；mismatch 必须带有效来源任务 |
| GET | /feedbacks | cmr:feedback:page | 分页：entryType/status/sourceTaskNo |
| GET | /feedbacks/stats | cmr:feedback:page | 统计：{total, pending, handled, blocked, strategy, supplement} |
| POST | /feedbacks/{id}/handle | cmr:feedback:update | 处置：{judgment: data_bad/match_bad/need_data, disposition: block/strategy/supplement, resultNote?}；disposition=block 时把勾选条目写入屏蔽列表；handled 终态不可重复操作 |
| GET | /recommend-words | cmr:word:page | 推荐词列表 |
| POST | /recommend-words | cmr:word:create | 新增 {word, scene, sort} |
| PUT | /recommend-words/{id} | cmr:word:update | 编辑/开关（即时生效：开进联想、关停推） |
| DELETE | /recommend-words/{id} | cmr:word:remove | 删除（逻辑删） |

## 七、页面 ↔ 接口映射

| 页面 | 主要接口 |
| --- | --- |
| P1 检索台 | /search/suggest、/search/understand、/search、/strategies/options、/file/upload |
| P2 结果页 | /search/results/{id}、rerun、adopt/discard、/click、/search/tasks/{taskNo}/vote、/search/favorites、/search-summary/{taskNo}/seal、/feedbacks、/file/package |
| P3 搜索结果摘要 | /search-summary/{taskNo}、/search/tasks/{taskNo}/vote、/search/favorites、/feedbacks |
| P4 数据源库管理 | /datasets*、/datasources*、/datasources/contract、/datasources/{id}/datasets、/datasources/{id}/sync、/datasource-blacklist*、/indexes* |
| P5 策略管理 | /strategies*、/strategies/options、/strategies/{id}/offline、/strategies/{id}/online |
| P6 搜索行为 | /search-behaviors、/search-behaviors/blocked-items*、/search-summary/{taskNo}、/search-summary/{taskNo}/seal、/feedbacks |
| P7 结果运营 | /feedbacks*、/recommend-words* |
| P8 效果评测 | /evaluations*（/eval-sets*、/eval-runs* 兼容保留） |

## 八、前端路由表

| 路由 | 页面 | 菜单 |
| --- | --- | --- |
| /login | 登录 | — |
| / | 门户（九宫格） | — |
| /search | P1 检索台 | 检索中心/检索台 |
| /search/results/:resultSetId | P2 结果页 | （检索跳转，不挂菜单） |
| /summary/:taskNo | P3 搜索结果摘要 | （行为流水跳转，不挂菜单） |
| /behaviors | P6 搜索行为 | 检索中心/搜索行为 |
| /tasks | 失败任务排查（内部） | （隐藏，错误态跳转） |
| /tasks/:id | 任务详情（内部） | （隐藏） |
| /history | → /behaviors 重定向 | 检索中心/搜索行为 |
| /library | P4 数据源库管理 | 数据管理/数据源库管理 |
| /strategies | P5 策略管理 | 策略运营/策略管理 |
| /evaluation | P8 效果评测 | 策略运营/效果评测 |
| /operation | P7 结果运营 | 策略运营/结果运营 |
