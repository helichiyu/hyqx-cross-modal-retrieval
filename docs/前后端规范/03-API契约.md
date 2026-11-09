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
| GET | /datasources | cmr:dataset:page | 数据源列表（含 datasetCount 与最近连接测试状态） |
| POST | /datasources | cmr:dataset:create | 登记数据源：{name, endpoint(http/https), protocol, authType, authConfig}；token/basic 认证信息必填 |
| POST | /datasources/{id}/test | cmr:dataset:create | 测试连接：返回 connected/statusCode/latencyMs/message，并持久化最近结果 |
| GET | /indexes?datasetId= | cmr:dataset:detail | 索引历史（candidate/active/retired + 406 结果） |
| POST | /indexes/{id}/activate | cmr:dataset:confirm | 回滚激活（旧 retired 索引重新激活） |

状态机约束在后端强制（02 文档）；非法迁移返回 code=1"不允许的状态流转"。

## 二、检索域（P1 检索台 / P2 结果页 / P9 检索历史）

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| GET | /search/suggest?scene=&prefix= | 登录即可 | 推荐词联想（enabled 词按 sort） |
| POST | /search/understand | cmr:search:create | 407 代理：{query} 或 {queryFile} → {needClarify, clarifyOptions[], queryModality, hint} |
| POST | /search | cmr:search:create | 执行检索：{query/queryFile, scene, targetModalities?, clarifiedQuery?, weights?, rerank?, topK?} → 结果集（条目+策略快照+resultSetId+taskId+srhNo）；weights/rerank 不传用场景生效策略 |
| GET | /search/results/{resultSetId} | cmr:search:page | 结果集快照回看（P2 刷新/P9 查看结果） |
| POST | /search/results/{resultSetId}/rerun | cmr:search:create | P2 错误态重跑：原参数重放，生成新 SRH/RST |
| POST | /search/results/{resultSetId}/adopt | cmr:search:adopt | 未采用→已采用 |
| POST | /search/results/{resultSetId}/discard | cmr:search:adopt | 未采用→已废弃（终态，二者互斥不可逆） |
| POST | /search/results/{resultSetId}/click | cmr:search:page | 条目点击埋点 {rank}（维护 clicked 冗余） |
| GET | /search-history | cmr:search:page | P9 分页：query 模糊/查询与目标模态/采用状态/时间/零结果 |
| GET | /search-history/{id} | cmr:search:page | 详情（含条目与策略快照） |
| POST | /search-history/{id}/rerun | cmr:search:create | 再次执行（原参数重放，新 SRH） |

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
| POST | /strategies | cmr:strategy:create | 新建：{name, scene, weights, rerank, topK} 存草稿 |
| GET | /strategies/{id} | cmr:strategy:detail | 详情+当前版本参数+向量段（只读回显激活索引 modelVersion） |
| PUT | /strategies/{id} | cmr:strategy:update | 编辑草稿参数（生效版本配置不可直接改，先存草稿） |
| POST | /strategies/{id}/publish | cmr:strategy:publish | 发布草稿→版本递增立即生效（下一次检索） |
| POST | /strategies/{id}/discard-draft | cmr:strategy:update | 放弃草稿（回到生效版本态） |
| GET | /strategies/{id}/versions | cmr:strategy:detail | 版本历史 |
| GET | /strategies/{id}/diff?from=&to= | cmr:strategy:detail | 变更评估：两版本参数 diff |
| GET | /strategies/{id}/consistency | cmr:strategy:detail | 一致性检查：模型白名单/权重合计/TopK/草稿完整性 |
| POST | /strategies/{id}/rollback | cmr:strategy:publish | 回滚到 {versionNo}：配置/状态/版本号一起回写，出核验回执 |
| GET | /strategies/active?scene= | cmr:search:create | 检索台回显当前生效策略（P1 策略提示区） |

## 五、评测域（P8）

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
| POST | /feedbacks | cmr:feedback:create | 登记反馈：{searchId, type, description}（type 四种，见字典） |
| GET | /feedbacks | cmr:feedback:page | 分页：类型/状态/时间/searchId |
| GET | /feedbacks/stats | cmr:feedback:page | 全量状态统计：{total,pending,processing,closed} |
| POST | /feedbacks/{id}/process | cmr:feedback:update | 开始处理（→处理中，回填 target 去向） |
| POST | /feedbacks/{id}/close | cmr:feedback:update | 处理完成闭环：{resultNote}；closed 终态 |
| GET | /recommend-words | cmr:word:page | 推荐词列表 |
| POST | /recommend-words | cmr:word:create | 新增 {word, scene, sort} |
| PUT | /recommend-words/{id} | cmr:word:update | 编辑/开关（即时生效：开进联想、关停推） |
| DELETE | /recommend-words/{id} | cmr:word:remove | 删除（逻辑删） |

## 七、页面 ↔ 接口映射

| 页面 | 主要接口 |
| --- | --- |
| P1 检索台 | /search/suggest、/search/understand、/search、/strategies/active、/file/upload |
| P2 结果页 | /search/results/{id}、rerun、adopt/discard、/click、/feedbacks、/file/package |
| P3 任务详情 | /tasks/{id}、/file |
| P4 库管理 | /datasets*、/datasources*、/indexes* |
| P5 策略管理 | /strategies* |
| P6 任务管理 | /tasks |
| P7 结果运营 | /feedbacks*、/recommend-words* |
| P8 评测 | /eval-sets*、/eval-runs* |
| P9 检索历史 | /search-history*、rerun |

## 八、前端路由表

| 路由 | 页面 | 菜单 |
| --- | --- | --- |
| /login | 登录 | — |
| / | 门户（九宫格） | — |
| /search | P1 检索台 | 检索中心/检索台 |
| /result/:resultSetId | P2 结果页 | （检索跳转，不独立挂菜单） |
| /task | P6 任务管理 | 检索中心/任务管理 |
| /task/:id | P3 任务详情 | （跳转） |
| /history | P9 检索历史 | 检索中心/检索历史 |
| /library | P4 库管理 | 数据管理/库管理 |
| /strategy | P5 策略管理 | 策略运营/策略管理 |
| /evaluation | P8 评测 | 策略运营/评测 |
| /operation | P7 结果运营 | 策略运营/结果运营 |
