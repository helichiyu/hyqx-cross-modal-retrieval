import { http } from './http'
import type {
  BehaviorVO,
  BlockedItemVO,
  DatasetDetailVO,
  DatasetVO,
  DatasourceBlacklistVO,
  DatasourceTestVO,
  DatasourceVO,
  EvaluationDetailVO,
  EvaluationRowVO,
  RemoteDatasetsVO,
  SearchSummaryVO,
  StrategyOptionVO,
  MismatchItem,
  DictItem,
  EvalItemVO,
  EvalPreviewVO,
  EvalRecordVO,
  EvalSetVO,
  FeedbackStatsVO,
  FeedbackVO,
  HistoryDetailVO,
  HistoryVO,
  Id,
  IndexVO,
  LoginVO,
  PageResult,
  SearchResultVO,
  StrategyDetailVO,
  StrategyVersionVO,
  StrategyVO,
  TaskDetailVO,
  TaskVO,
  UnderstandVO,
  UserVO,
  WordVO,
} from './types'

// ---------- 鉴权 / 字典 ----------

export const login = (username: string, password: string) =>
  http.post<LoginVO>('auth/login', { username, password })

export const me = () => http.get<UserVO>('auth/me')

export const logout = () => http.post<void>('auth/logout')

export const dict = (type: string) => http.get<DictItem[]>(`dict/${type}`)

export const createDictItem = (type: string, body: Record<string, unknown>) =>
  http.post<DictItem>(`dict/${type}`, body)

// ---------- 文件 ----------

export const uploadFile = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return http.upload<{ path: string }>('file/upload', form)
}

// 共享卷路径走 /file?path=；上游 http(s) 地址走服务端回源代理 /file/source?url=，
// 前端不直连上游、不落原始内容。
export const fileUrl = (path: string) =>
  /^https?:\/\//i.test(path) ? `file/source?url=${encodeURIComponent(path)}` : `file?path=${encodeURIComponent(path)}`

// ---------- 库管理 ----------

export const pageDatasets = (params: Record<string, unknown>) =>
  http.get<PageResult<DatasetVO>>('datasets', params)

export const createDataset = (form: FormData) => http.post<DatasetVO>('datasets', form)

export const datasetDetail = (id: Id) => http.get<DatasetDetailVO>(`datasets/${id}`)

export const rebuildDataset = (id: Id) => http.post<TaskVO>(`datasets/${id}/rebuild`)

export const listDatasources = () => http.get<DatasourceVO[]>('datasources')

export const createDatasource = (body: Record<string, unknown>) =>
  http.post<DatasourceVO>('datasources', body)

export const testDatasource = (id: Id) => http.post<DatasourceTestVO>(`datasources/${id}/test`)

export const syncDatasource = (id: Id) => http.post<RemoteDatasetsVO>(`datasources/${id}/sync`)

export const datasourceDatasets = (id: Id) => http.get<RemoteDatasetsVO>(`datasources/${id}/datasets`)

export const datasourceContract = () => http.get<Record<string, unknown>>('datasources/contract')

export const listBlacklist = () => http.get<DatasourceBlacklistVO[]>('datasource-blacklist')

export const addBlacklist = (body: Record<string, unknown>) =>
  http.post<DatasourceBlacklistVO>('datasource-blacklist', body)

export const removeBlacklist = (id: Id) => http.delete<void>(`datasource-blacklist/${id}`)

export const listIndexes = (datasetId: Id) =>
  http.get<IndexVO[]>('indexes', { datasetId })

// ---------- 任务 ----------

export const pageTasks = (params: Record<string, unknown>) =>
  http.get<PageResult<TaskVO>>('tasks', params)

export const taskDetail = (id: Id) => http.get<TaskDetailVO>(`tasks/${id}`)

// ---------- 策略 ----------

export const pageStrategies = (params: Record<string, unknown> = {}) =>
  http.get<PageResult<StrategyVO>>('strategies', params)

export const strategyModelOptions = () => http.get<Record<string, unknown>>('strategies/model-options')

export const strategyConsistency = (id: Id) => http.get<Record<string, unknown>>(`strategies/${id}/consistency`)

export const strategyDetail = (id: Id) => http.get<StrategyDetailVO>(`strategies/${id}`)

export const createStrategy = (body: Record<string, unknown>) =>
  http.post<StrategyVO>('strategies', body)

export const updateDraft = (id: Id, body: Record<string, unknown>) =>
  http.put<void>(`strategies/${id}`, body)

export const publishStrategy = (id: Id) =>
  http.post<StrategyVersionVO>(`strategies/${id}/publish`)

export const discardDraft = (id: Id) =>
  http.post<void>(`strategies/${id}/discard-draft`)

export const strategyVersions = (id: Id) =>
  http.get<StrategyVersionVO[]>(`strategies/${id}/versions`)

/** from/to 为版本号（versionNo） */
export const strategyDiff = (id: Id, from: number, to: number) =>
  http.get<Record<string, unknown>>(`strategies/${id}/diff`, { from, to })

export const rollbackStrategy = (id: Id, versionNo: number) =>
  http.post<StrategyVersionVO>(`strategies/${id}/rollback`, { versionNo })

export const strategyOptions = () => http.get<StrategyOptionVO[]>('strategies/options')

export const offlineStrategy = (id: Id, reason: string) =>
  http.post<void>(`strategies/${id}/offline`, { reason })

export const onlineStrategy = (id: Id) => http.post<void>(`strategies/${id}/online`)

// ---------- 检索 ----------

export const suggestWords = (scene: string | undefined, prefix: string) =>
  http.get<string[]>('search/suggest', { scene, prefix })

export const understand = (body: Record<string, unknown>) =>
  http.post<UnderstandVO>('search/understand', body)

export const executeSearch = (body: Record<string, unknown>) =>
  http.post<SearchResultVO>('search', body)

export const searchResults = (resultSetId: string) =>
  http.get<HistoryDetailVO>(`search/results/${resultSetId}`)

export const rerunSearchResults = (resultSetId: string) =>
  http.post<SearchResultVO>(`search/results/${resultSetId}/rerun`)

export const adoptResults = (resultSetId: string, adopted: boolean) =>
  http.post<void>(`search/results/${resultSetId}/${adopted ? 'adopt' : 'discard'}`)

export const clickResult = (resultSetId: string, rank: number) =>
  http.post<void>(`search/results/${resultSetId}/click`, { rank })

export const pageHistory = (params: Record<string, unknown>) =>
  http.get<PageResult<HistoryVO>>('search-history', params)

export const historyDetail = (id: Id) => http.get<HistoryDetailVO>(`search-history/${id}`)

export const rerunHistory = (id: Id) =>
  http.post<SearchResultVO>(`search-history/${id}/rerun`)

// ---------- 搜索行为 / 摘要（P3/P6） ----------

export const pageBehaviors = (params: Record<string, unknown> = {}) =>
  http.get<PageResult<BehaviorVO>>('search-behaviors', params)

export const searchSummary = (taskNo: string) =>
  http.get<SearchSummaryVO>(`search-summary/${taskNo}`)

export const sealTask = (taskNo: string) => http.post<void>(`search-summary/${taskNo}/seal`)

export const voteItem = (taskNo: string, itemId: string, vote: 'up' | 'down' | 'none') =>
  http.post<{ vote: string | null; upCount: number; downCount: number }>(
    `search/tasks/${taskNo}/vote`,
    { itemId, vote },
  )

export const toggleFavorite = (body: Record<string, unknown>) =>
  http.post<boolean>('search/favorites', body)

export const myFavorites = () => http.get<Record<string, unknown>[]>('search/favorites')

export const listBlockedItems = () => http.get<BlockedItemVO[]>('search-behaviors/blocked-items')

export const unblockItem = (id: Id) =>
  http.post<void>(`search-behaviors/blocked-items/${id}/unblock`)

// ---------- 效果评测（P8） ----------

export const pageEvaluations = (params: Record<string, unknown> = {}) =>
  http.get<PageResult<EvaluationRowVO>>('evaluations', params)

export const createEvaluation = (baseTaskNo: string) =>
  http.post<Record<string, unknown>>('evaluations', { baseTaskNo })

export const evaluationDetail = (id: Id) => http.get<EvaluationDetailVO>(`evaluations/${id}`)

export const replayEvaluation = (id: Id, strategyId?: Id) =>
  http.post<SearchResultVO>(`evaluations/${id}/replay`, strategyId ? { strategyId } : {})

// ---------- 评测集（历史能力，逐步下线） ----------

export const pageEvalSets = (params: Record<string, unknown> = {}) =>
  http.get<PageResult<EvalSetVO>>('eval-sets', params)

export const evalSetDetail = (id: Id, page?: Record<string, unknown>) =>
  http.get<{ evalSet: EvalSetVO; items: PageResult<EvalItemVO> }>(`eval-sets/${id}`, page)

export const previewEvalSet = (filters: Record<string, boolean>) =>
  http.get<EvalPreviewVO>('eval-sets/preview', filters)

export const createEvalSet = (body: Record<string, unknown>) =>
  http.post<EvalSetVO>('eval-sets', body)

export const labelEvalItem = (setId: Id, itemId: Id, answers: string[]) =>
  http.put<void>(`eval-sets/${setId}/items/${itemId}`, { answers })

export const completeEvalSet = (setId: Id) =>
  http.post<void>(`eval-sets/${setId}/complete`)

export const runEval = (evalSetId: Id, targetStrategyVersionId?: Id) =>
  http.post<EvalRecordVO>('eval-runs', { evalSetId, targetStrategyVersionId })

export const pageEvalRuns = (params: Record<string, unknown> = {}) =>
  http.get<PageResult<EvalRecordVO>>('eval-runs', params)

export const evalRunDetail = (id: Id) => http.get<EvalRecordVO>(`eval-runs/${id}`)

// ---------- 运营 ----------

export const pageFeedbacks = (params: Record<string, unknown> = {}) =>
  http.get<PageResult<FeedbackVO>>('feedbacks', params)

export const feedbackStats = () => http.get<FeedbackStatsVO>('feedbacks/stats')

export const createFeedback = (body: {
  entryType: 'mismatch' | 'zero'
  sourceTaskNo?: string
  sourceQuery?: string
  items?: MismatchItem[]
  description?: string
}) => http.post<FeedbackVO>('feedbacks', body)

export const handleFeedback = (id: Id, body: Record<string, unknown>) =>
  http.post<void>(`feedbacks/${id}/handle`, body)

export const pageWords = (params: Record<string, unknown> = {}) =>
  http.get<PageResult<WordVO>>('recommend-words', params)

export const createWord = (body: Record<string, unknown>) =>
  http.post<WordVO>('recommend-words', body)

export const updateWord = (id: Id, body: Record<string, unknown>) =>
  http.put<WordVO>(`recommend-words/${id}`, body)

export const removeWord = (id: Id) => http.delete<void>(`recommend-words/${id}`)
