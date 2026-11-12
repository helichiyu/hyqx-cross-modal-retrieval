// 与后端 VO 一一对应的类型定义（docs/前后端方案/03-API契约）

export interface ApiEnvelope<T> {
  code: number
  msg: string
  data: T
}

/**
 * 后端 Long 主键统一序列化为字符串（雪花 ID 超出 JS 安全整数范围，数字下发会丢精度）。
 * 前端一律按字符串传递，不要参与算术运算。
 */
export type Id = string

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface UserVO {
  username: string
  nickname: string
  role: string
  permissions: string[]
}

export interface LoginVO {
  token: string
  user: UserVO
}

export interface DictItem {
  alias: string
  label: string
}

export interface HistoryVO {
  id: Id
  bizNo: string
  resultSetId: string
  taskId: Id | null
  indexId: Id | null
  taskBizNo: string | null
  taskStatus: string | null
  queryText: string | null
  queryFile: string | null
  queryModality: string | null
  targetModality: string | null
  clarified: boolean | null
  scene: string | null
  itemCount: number | null
  elapsedMs: number | null
  adoptStatus: string | null
  userId: Id | null
  operatorId: Id | null
  operatorName: string | null
  strategyNo: string | null
  strategyVersion: number | null
  threshold: number | null
  sealed: string | null
  replayOfTaskNo: string | null
  createTime: string | null
}

export interface ResultItemVO {
  id: Id
  rank: number
  itemId: string
  name: string | null
  modality: string
  score: number
  /** 来源名（摘要展示） */
  source: string | null
  /** 对齐分 */
  alignScore: number | null
  /** 质量等级（继承 P4 评定：A/B/C/D；历史数据为空） */
  qualityGrade: string | null
  /** 当前用户赞踩：up/down/null */
  vote: 'up' | 'down' | null
  /** 当前用户是否收藏 */
  favorited: boolean | null
  sourceFile: string | null
  locatorJson: string | null
  text: string | null
  clicked: boolean | null
  deleted: boolean
}

export interface HistoryDetailVO {
  history: HistoryVO
  strategySnapshotJson: string | null
  items: ResultItemVO[]
}

export interface UnderstandVO {
  needClarify: boolean
  clarifyOptions: string[]
  queryModality: string
  hint: string
  queryFile: string
}

export interface SearchResultVO {
  id: Id
  srhNo: string
  resultSetId: string
  taskId: Id
  taskBizNo: string
  queryModality: string
  targetModality: string | null
  itemCount: number
  elapsedMs: number
  strategy: Record<string, unknown>
  items: ResultItemVO[]
}

export interface DatasetVO {
  id: Id
  bizNo: string
  datasourceId: Id | null
  name: string
  version: string | null
  scene: string | null
  status: string
  itemCount: number | null
  modalityJson: string | null
  currentIndexId: Id | null
  createTime: string | null
}

export interface IndexVO {
  id: Id
  datasetId: Id
  indexDir: string
  modelVersion: string | null
  status: string
  countsJson: string | null
  activatedTime: string | null
  createTime: string | null
}

export interface TaskVO {
  id: Id
  bizNo: string
  type: string
  refId: Id | null
  refBizNo: string | null
  refRoute: string | null
  status: string
  failType: string | null
  failDisposal: string | null
  retryCount: number | null
  currentStep: string | null
  errorMsg: string | null
  startedTime: string | null
  finishedTime: string | null
  createTime: string | null
}

export interface TaskLogVO {
  id: Id
  taskId: Id
  step: string
  status: string
  message: string | null
  operatorOutputJson: string | null
  createTime: string | null
}

export interface TaskDetailVO {
  task: TaskVO
  logs: TaskLogVO[]
}

export interface DatasetDetailVO {
  dataset: DatasetVO
  metaJson: string | null
  indexes: IndexVO[]
  tasks: TaskVO[]
}

export interface DatasourceVO {
  id: Id
  bizNo: string
  name: string
  endpoint: string
  protocol: string | null
  authType: string | null
  remark: string | null
  datasetCount: number
  /** unconnected/connected/failed */
  connectStatus: 'unconnected' | 'connected' | 'failed' | string
  /** 上游 4 项必填是否齐全 */
  requiredOk: boolean
  upstreamDatasetId: string | null
  vectorizedCount: number
  totalCount: number
  lastSyncTime: string | null
  lastTestConnected: boolean | null
  lastTestStatusCode: number | null
  lastTestLatencyMs: number | null
  lastTestMessage: string | null
  lastTestTime: string | null
  createTime: string | null
}

export interface DatasourceTestVO {
  connected: boolean | null
  statusCode: number | null
  latencyMs: number | null
  message: string
  testedAt: string
}

export interface StrategyVO {
  id: Id
  bizNo: string
  name: string
  scene: string
  status: string
  /** 系统预置（兜底），不可下线 */
  preset: boolean
  offlineReason: string | null
  offlineTime: string | null
  currentVersion: number | null
  /** 参数摘要，如 “阈值 0.60 · 重排开” */
  paramSummary: string | null
  remark: string | null
  createTime: string | null
  updateTime: string | null
}

export interface StrategyVersionVO {
  id: Id
  strategyId: Id
  versionNo: number
  weightsJson: string
  rerank: boolean
  topK: number
  threshold: number | null
  status: string
  source: string | null
  publishedTime: string | null
  createTime: string
}

export interface StrategyDetailVO {
  strategy: StrategyVO
  current: StrategyVersionVO | null
  draft: StrategyVersionVO | null
  modelVersion: string | null
  versions: StrategyVersionVO[]
}

export interface EvalSetVO {
  id: Id
  bizNo: string
  name: string
  filtersJson: string
  status: string
  queryCount: number
  labeledCount: number
  createTime: string
}

export interface EvalItemVO {
  id: Id
  evalSetId: Id
  searchId: Id
  query: string
  answersJson: string
  labeled: boolean
}

export interface EvalPreviewItemVO {
  searchId: Id
  searchBizNo: string
  query: string
  queryModality: string | null
  targetModality: string | null
  itemCount: number | null
  createTime: string | null
}

export interface EvalPreviewVO {
  count: number
  items: EvalPreviewItemVO[]
}

export interface EvalRecordVO {
  id: Id
  bizNo: string
  evalSetId: Id
  indexId: Id
  strategyVersionId: Id | null
  taskId: Id | null
  taskBizNo: string | null
  taskStatus: string | null
  metricsJson: string | null
  linesJson: string | null
  conclusion: string
  failedLinesJson: string | null
  comparisonJson: string | null
  createTime: string
}

export interface FeedbackStatsVO {
  total: number
  pending: number
  handled: number
  blocked: number
  strategy: number
  supplement: number
}

export interface MismatchItem {
  itemId: string
  name: string | null
  source: string | null
}

export interface FeedbackVO {
  id: Id
  bizNo: string
  searchId: Id | null
  sourceTaskNo: string | null
  sourceQuery: string | null
  /** mismatch/zero */
  entryType: string
  items: MismatchItem[]
  description: string | null
  /** pending/handled */
  status: string
  /** data_bad/match_bad/need_data */
  judgment: string | null
  /** block/strategy/supplement */
  disposition: string | null
  blocked: boolean
  resultNote: string | null
  handlerId: Id | null
  createTime: string
  updateTime: string | null
}

export interface WordVO {
  id: Id
  bizNo: string
  word: string
  scene: string
  enabled: boolean
  sort: number
}

// ---------- 原型 v5 新增 ----------

/** P1 策略下拉选项 */
export interface StrategyOptionVO {
  id: Id
  bizNo: string
  name: string
  scene: string
  preset: boolean
  versionNo: number | null
  threshold: number
  rerank: boolean
  topK: number
  paramSummary: string
}

/** P6 搜索行为流水 */
export interface BehaviorVO {
  id: Id
  taskNo: string
  resultSetId: string
  taskId: Id | null
  taskBizNo: string | null
  taskStatus: string | null
  queryText: string | null
  queryModality: string | null
  targetModality: string | null
  operatorName: string | null
  strategyNo: string | null
  strategyVersion: number | null
  threshold: number | null
  itemCount: number | null
  elapsedMs: number | null
  sealed: boolean
  zeroResult: boolean
  upCount: number
  downCount: number
  createTime: string | null
}

/** P3 搜索结果摘要 */
export interface SearchSummaryVO {
  taskNo: string
  searchId: Id
  resultSetId: string
  queryText: string | null
  queryModality: string | null
  targetModality: string | null
  operatorName: string | null
  strategyNo: string | null
  strategyVersion: number | null
  threshold: number | null
  itemCount: number | null
  sealed: boolean
  createTime: string | null
  expireTime: string | null
  expired: boolean
  upCount: number
  downCount: number
  items: ResultItemVO[]
}

/** P8 评测列表行 */
export interface EvaluationRowVO {
  id: Id
  bizNo: string
  query: string
  baseTaskNo: string
  baseTime: string | null
  baseN: number | null
  baseDown: number | null
  replayCount: number
  conclusion: 'degraded' | 'normal' | 'unevaluated' | string
  reasons?: string[]
  latestTaskNo: string | null
  latestTime: string | null
}

/** P8 评测详情 */
export interface EvaluationDetailVO {
  id: Id
  bizNo: string
  query: string
  base: Record<string, unknown>
  replays: Record<string, unknown>[]
  latest?: Record<string, unknown>
  conclusion: string
  reasons: string[]
  downRateDelta?: number
  resultDropRatio?: number
}

/** P4 屏蔽/黑名单 */
export interface BlockedItemVO {
  id: Id
  itemId: string
  itemName: string | null
  source: string | null
  reason: string | null
  feedbackId: Id | null
  createTime: string | null
}

export interface DatasourceBlacklistVO {
  id: Id
  datasetId: string | null
  name: string | null
  sourceName: string
  endpoint: string | null
  createTime: string | null
}

/** P4 上游数据集摘要 */
export interface RemoteDatasetVO {
  datasetId: string | null
  name: string | null
  itemCount: number | null
  modalTypes: unknown
  requiredOk: boolean
}

export interface RemoteDatasetsVO {
  datasetCount: number
  datasets: RemoteDatasetVO[]
  requiredOk: boolean
  firstDatasetId: string | null
  totalItems: number
  itemIssue: string | null
}
