import { http } from '@/core/http'

/** 澄清选项（407 查询理解） */
export interface ClarifyOption {
  intent: string
  query: string
  hint?: string
}

/** 查询理解结果（com.hsmty.cmr.pojo.vo.QueryUnderstandVO） */
export interface QueryUnderstandResult {
  needClarify: boolean
  clarifyOptions?: ClarifyOption[]
  queryModality?: string
  hint?: string
  ambiguousTerm?: string
  normalizedQuery?: string
}

export interface QueryUnderstandPayload {
  uniqueId?: string
  query?: string
  queryFile?: string
}

/**
 * 查询理解（后端代理 407 算子）。
 *
 * 文本与查询文件二选一，文件路径需与算子共享卷一致。
 */
export function understandQuery(payload: QueryUnderstandPayload): Promise<QueryUnderstandResult> {
  return http.post<QueryUnderstandResult>('/search/understand', payload)
}

/** 检索请求（com.hsmty.cmr.pojo.dto.SearchCreateDTO） */
export interface SearchPayload {
  query?: string
  queryFile?: string
  scene?: string
  targetModalities?: string[]
  clarified?: boolean
  clarifiedQuery?: string
  weights?: Record<string, number>
  rerank?: boolean
  topK?: number
  threshold?: number
}

/** 检索结果条目（com.hsmty.cmr.pojo.vo.SearchResultItemVO） */
export interface SearchResultItem {
  rank: number
  score?: number
  modality?: string
  itemId?: string
  sourceFile?: string
  source?: string
  alignScore?: number
  qualityGrade?: string
  locator?: Record<string, unknown>
  text?: string
}

/** 检索结果（com.hsmty.cmr.pojo.vo.SearchResultVO） */
export interface SearchResult {
  searchNo: string
  resultSetId: string
  query: string
  queryModality?: string
  count: number
  items: SearchResultItem[]
  modelVersion?: string
  threshold?: number
  elapsedMs?: number
}

/**
 * 执行检索（后端代理 408 算子并落检索历史）。
 *
 * 已完成澄清时传 clarified=true 与 clarifiedQuery，后端优先使用改写查询。
 */
export function executeSearch(payload: SearchPayload): Promise<SearchResult> {
  return http.post<SearchResult>('/search', payload)
}
