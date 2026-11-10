import { http } from './http'
import type {
  DatasetDetailVO,
  DatasetVO,
  DatasourceTestVO,
  DatasourceVO,
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

export const fileUrl = (path: string, download = false) =>
  `file?path=${encodeURIComponent(path)}${download ? '&download=true' : ''}`

// ---------- 库管理 ----------

export const pageDatasets = (params: Record<string, unknown>) =>
  http.get<PageResult<DatasetVO>>('datasets', params)

export const createDataset = (form: FormData) => http.post<DatasetVO>('datasets', form)

export const datasetDetail = (id: Id) => http.get<DatasetDetailVO>(`datasets/${id}`)

export const adjudicateDataset = (id: Id, pass: boolean, note?: string) =>
  http.post<void>(`datasets/${id}/adjudicate`, { pass, note })

export const confirmProduction = (id: Id) =>
  http.post<void>(`datasets/${id}/confirm-production`)

export const rebuildDataset = (id: Id) => http.post<TaskVO>(`datasets/${id}/rebuild`)

export const listDatasources = () => http.get<DatasourceVO[]>('datasources')

export const createDatasource = (body: Record<string, unknown>) =>
  http.post<DatasourceVO>('datasources', body)

export const testDatasource = (id: Id) => http.post<DatasourceTestVO>(`datasources/${id}/test`)

export const listIndexes = (datasetId: Id) =>
  http.get<IndexVO[]>('indexes', { datasetId })

export const activateIndex = (id: Id) => http.post<IndexVO>(`indexes/${id}/activate`)

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

// ---------- 评测 ----------

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

export const createFeedback = (body: Record<string, unknown>) =>
  http.post<FeedbackVO>('feedbacks', body)

export const processFeedback = (id: Id, target?: string) =>
  http.post<void>(`feedbacks/${id}/process`, target ? { target } : {})

export const closeFeedback = (id: Id, resultNote?: string) =>
  http.post<void>(`feedbacks/${id}/close`, resultNote ? { resultNote } : {})

export const pageWords = (params: Record<string, unknown> = {}) =>
  http.get<PageResult<WordVO>>('recommend-words', params)

export const createWord = (body: Record<string, unknown>) =>
  http.post<WordVO>('recommend-words', body)

export const updateWord = (id: Id, body: Record<string, unknown>) =>
  http.put<WordVO>(`recommend-words/${id}`, body)

export const removeWord = (id: Id) => http.delete<void>(`recommend-words/${id}`)
