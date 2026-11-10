/**
 * 统一请求封装。
 *
 * 对齐 HSMTY 平台返回结构：`{ code, message, data, param }`，`code === '0000'` 为成功。
 * 业务失败码带前缀（如 `C1003` 文件不存在、`C108` 权限不足）。
 * 所有请求携带 Cookie，身份由主应用/网关维护的 SSO 会话提供，前端不保存 Token。
 */

export interface ApiResult<T> {
  code: string
  message: string
  data: T
  param?: unknown
}

/** 平台分页结构（com.hsmty.core.common.entity.PageResult） */
export interface PageResult<T> {
  current: number
  limit: number
  totalPage: number
  total: number
  rows: T[]
}

/** 平台分页入参（com.hsmty.core.common.entity.PageQuery） */
export interface PageQuery {
  page?: number
  limit?: number
}

export const SUCCESS_CODE = '0000'

/** 未登录/登录失效相关业务码 */
const AUTH_FAILED_CODES = new Set(['C105', 'C106'])

export class ApiError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status?: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

type QueryValue = string | number | boolean | undefined | null

const API_BASE = (import.meta.env.VITE_API_BASE || '/hs-api').replace(/\/$/, '')
const MODULE_PREFIX = (import.meta.env.VITE_MODULE_PREFIX || 'cmr').replace(/^\/|\/$/g, '')
const BASE = `${API_BASE}/${MODULE_PREFIX}`

function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const url = `${BASE}${path.startsWith('/') ? path : `/${path}`}`
  if (!query) {
    return url
  }
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.append(key, String(value))
    }
  })
  const qs = params.toString()
  return qs ? `${url}?${qs}` : url
}

async function readBody(response: Response): Promise<ApiResult<unknown> | null> {
  return (await response
    .clone()
    .json()
    .catch(() => null)) as ApiResult<unknown> | null
}

async function parseError(response: Response): Promise<ApiError> {
  const body = await readBody(response)
  const message = body?.message || response.statusText || '请求失败'
  return new ApiError(body?.code ?? String(response.status), message, response.status)
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(path, { credentials: 'include', ...init })
  const body = (await response.json().catch(() => null)) as ApiResult<T> | null

  if (response.status === 401 && (!body?.code || AUTH_FAILED_CODES.has(body.code))) {
    window.dispatchEvent(new CustomEvent('cms:unauthorized'))
    throw new ApiError(body?.code ?? '401', body?.message || '未登录或登录已过期', 401)
  }
  if (!response.ok) {
    throw new ApiError(body?.code ?? String(response.status), body?.message || response.statusText || '请求失败', response.status)
  }
  if (!body || typeof body.code !== 'string') {
    throw new ApiError('INVALID_RESPONSE', '响应格式不正确', response.status)
  }
  if (body.code !== SUCCESS_CODE) {
    throw new ApiError(body.code, body.message || '请求失败', response.status)
  }
  return body.data
}

function jsonInit(method: string, body?: unknown): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  }
}

export const http = {
  get<T>(path: string, query?: Record<string, QueryValue>): Promise<T> {
    return request<T>(buildUrl(path, query), { method: 'GET' })
  },

  post<T>(path: string, body?: unknown): Promise<T> {
    return request<T>(buildUrl(path), jsonInit('POST', body))
  },

  put<T>(path: string, body?: unknown): Promise<T> {
    return request<T>(buildUrl(path), jsonInit('PUT', body))
  },

  delete<T>(path: string, body?: unknown): Promise<T> {
    return request<T>(buildUrl(path), jsonInit('DELETE', body))
  },

  upload<T>(path: string, form: FormData): Promise<T> {
    return request<T>(buildUrl(path), { method: 'POST', body: form })
  },

  async download(path: string): Promise<{ blob: Blob; filename: string }> {
    const response = await fetch(buildUrl(path), { method: 'GET', credentials: 'include' })
    if (!response.ok) {
      throw await parseError(response)
    }
    const disposition = response.headers.get('Content-Disposition') || ''
    const utf8Match = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
    const plainMatch = /filename="?([^";]+)"?/i.exec(disposition)
    let filename = 'download'
    if (utf8Match) {
      filename = decodeURIComponent(utf8Match[1])
    } else if (plainMatch) {
      filename = plainMatch[1]
    }
    return { blob: await response.blob(), filename }
  },
}
