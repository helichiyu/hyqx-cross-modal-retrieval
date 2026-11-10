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
