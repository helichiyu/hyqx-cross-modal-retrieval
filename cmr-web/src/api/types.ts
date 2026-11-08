// 与后端 VO 一一对应的类型定义（docs/前后端方案/03-API契约）

export interface ApiEnvelope<T> {
  code: number
  msg: string
  data: T
}

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
  id: number
  bizNo: string
  resultSetId: string
  taskId: number | null
  queryText: string | null
  queryFile: string | null
  queryModality: string | null
  clarified: boolean | null
  scene: string | null
  itemCount: number | null
  elapsedMs: number | null
  adoptStatus: string | null
  userId: number | null
  createTime: string | null
}

export interface ResultItemVO {
  id: number
  rank: number
  itemId: string
  modality: string
  score: number
  sourceFile: string | null
  locatorJson: string | null
  text: string | null
  clicked: boolean | null
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
  id: number
  srhNo: string
  resultSetId: string
  taskId: number
  taskBizNo: string
  queryModality: string
  itemCount: number
  elapsedMs: number
  strategy: Record<string, unknown>
  items: ResultItemVO[]
}

export interface DatasetVO {
  id: number
  bizNo: string
  datasourceId: number | null
  name: string
  version: string | null
  scene: string | null
  status: string
  stuckReason: string | null
  score: number | null
  decision: string | null
  itemCount: number | null
  modalityJson: string | null
  currentIndexId: number | null
  createTime: string | null
}

export interface IndexVO {
  id: number
  datasetId: number
  indexDir: string
  modelVersion: string | null
  status: string
  countsJson: string | null
  checkResultJson: string | null
  activatedTime: string | null
  createTime: string | null
}

export interface TaskVO {
  id: number
  bizNo: string
  type: string
  refId: number | null
  refBizNo: string | null
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
  id: number
  taskId: number
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
  id: number
  bizNo: string
  name: string
  endpoint: string
  protocol: string | null
  authType: string | null
  remark: string | null
  createTime: string | null
}

export interface StrategyVO {
  id: number
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
  id: number
  strategyId: number
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
  id: number
  bizNo: string
  name: string
  filtersJson: string
  status: string
  queryCount: number
  labeledCount: number
  createTime: string
}

export interface EvalItemVO {
  id: number
  evalSetId: number
  searchId: number
  query: string
  answersJson: string
  labeled: boolean
}

export interface EvalRecordVO {
  id: number
  bizNo: string
  evalSetId: number
  indexId: number
  strategyVersionId: number | null
  taskId: number | null
  taskBizNo: string | null
  metricsJson: string | null
  linesJson: string | null
  conclusion: string
  failedLinesJson: string | null
  comparisonJson: string | null
  createTime: string
}

export interface FeedbackVO {
  id: number
  bizNo: string
  searchId: number
  searchBizNo: string | null
  type: string
  description: string | null
  status: string
  target: string | null
  resultNote: string | null
  handlerId: number | null
  createTime: string
}

export interface WordVO {
  id: number
  bizNo: string
  word: string
  scene: string
  enabled: boolean
  sort: number
}
