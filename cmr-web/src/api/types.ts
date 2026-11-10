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
  createTime: string | null
}

export interface ResultItemVO {
  id: Id
  rank: number
  itemId: string
  name: string | null
  modality: string
  score: number
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
  stuckReason: string | null
  score: number | null
  decision: string | null
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
  checkResultJson: string | null
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
  currentVersion: number | null
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
  processing: number
  closed: number
}

export interface FeedbackVO {
  id: Id
  bizNo: string
  searchId: Id
  searchBizNo: string | null
  type: string
  description: string | null
  status: string
  target: string | null
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
