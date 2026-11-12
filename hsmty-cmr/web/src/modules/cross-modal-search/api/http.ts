import type { ApiEnvelope } from './types'

/**
 * 旧版 cmr-web 请求封装的迁移版。
 *
 * <p>与旧版的差异只在协议层：外层统一包结构由旧后端
 * {@code {code: 0, msg, data}} 换成平台 {@code {code: '0000', message, data}}；
 * 身份由主应用/网关的 SSO Cookie 维护，前端不再保存 Token。
 * 业务路径、入参、分页结构（records/total/pageNum/pageSize）保持旧版原样。
 */

const BASE = '/hs-api/cmr/'

const SUCCESS_CODE = '0000'

/** 单请求超时：检索/重放为同步执行，超时后提示查看任务中心，避免按钮永久转圈 */
const REQUEST_TIMEOUT_MS = 120_000

const AUTH_FAILED_CODES = new Set(['C105', 'C106'])

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public code?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

function queryString(params: Record<string, unknown>): string {
  const usp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') {
      usp.set(k, String(v))
    }
  }
  const s = usp.toString()
  return s ? `?${s}` : ''
}

function notifyUnauthorized() {
  window.dispatchEvent(new CustomEvent('cms:unauthorized'))
}

/**
 * 文件流响应的统一错误处理：
 * - 401 或鉴权错误码（C105/C106）触发重新登录；
 * - 本后端业务错误统一走 HTTP 200 + JSON 信封，文件接口出错时返回的也是 JSON 信封，
 *   必须按信封 code 拦截，避免把错误体当成媒体内容静默预览/下载。
 */
async function assertBlobResponseOk(resp: Response): Promise<void> {
  const isJson = resp.headers.get('content-type')?.includes('application/json') ?? false
  if (resp.ok && !isJson) {
    return
  }
  const envelope = isJson
    ? ((await resp.clone().json().catch(() => null)) as ApiEnvelope<null> | null)
    : null
  const code = typeof envelope?.code === 'string' ? envelope.code : undefined
  if (resp.status === 401 || (code !== undefined && AUTH_FAILED_CODES.has(code))) {
    notifyUnauthorized()
    throw new ApiError('登录已过期，请重新登录', resp.status, code)
  }
  if (!resp.ok) {
    throw new ApiError(envelope?.message || `文件获取失败(${resp.status})`, resp.status, code)
  }
  // HTTP 200 但携带业务错误信封（如文件不存在 C1008）
  if (code !== undefined && code !== SUCCESS_CODE) {
    throw new ApiError(envelope?.message || '文件获取失败', resp.status, code)
  }
}

async function request<T>(
  method: string,
  path: string,
  opts: { body?: unknown; form?: FormData; params?: Record<string, unknown> } = {},
): Promise<T> {
  const headers: Record<string, string> = {}
  let body: BodyInit | undefined
  if (opts.form) {
    body = opts.form
  } else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(opts.body)
  }
  let resp: Response
  try {
    resp = await fetch(`${BASE}${path}${queryString(opts.params ?? {})}`, {
      method,
      headers,
      body,
      credentials: 'include',
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    })
  } catch (error) {
    if ((error as Error).name === 'TimeoutError' || (error as Error).name === 'AbortError') {
      throw new ApiError('请求超时，后端可能仍在执行，可稍后到任务中心查看进展', 0)
    }
    throw new ApiError('网络异常，请检查后端服务', 0)
  }
  const json = (await resp.json().catch(() => null)) as ApiEnvelope<T> | null
  if (resp.status === 401 && (!json?.code || AUTH_FAILED_CODES.has(json.code))) {
    notifyUnauthorized()
    throw new ApiError(json?.message || '登录已过期，请重新登录', 401, json?.code)
  }
  if (!json) {
    throw new ApiError(`响应解析失败(${resp.status})`, resp.status)
  }
  if (json.code !== SUCCESS_CODE) {
    throw new ApiError(json.message || '请求失败', resp.status, json.code)
  }
  return json.data
}

export const http = {
  get<T>(path: string, params?: Record<string, unknown>) {
    return request<T>('GET', path, { params })
  },
  post<T>(path: string, body?: unknown, params?: Record<string, unknown>) {
    return request<T>('POST', path, { body, params })
  },
  put<T>(path: string, body?: unknown) {
    return request<T>('PUT', path, { body })
  },
  delete<T>(path: string) {
    return request<T>('DELETE', path)
  },
  upload<T>(path: string, form: FormData) {
    return request<T>('POST', path, { form })
  },
  /** 拉取文件流（媒体预览用，Cookie 会话随请求携带） */
  async blob(path: string, params?: Record<string, unknown>): Promise<Blob> {
    const resp = await fetch(`${BASE}${path}${queryString(params ?? {})}`, {
      credentials: 'include',
    })
    await assertBlobResponseOk(resp)
    return resp.blob()
  },
}
