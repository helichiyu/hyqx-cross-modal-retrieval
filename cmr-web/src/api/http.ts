import type { ApiEnvelope } from './types'
import { useAuthStore } from '../stores/auth'
import router from '../router'

const BASE = '/hs-api/cmr'
const TOKEN_KEY = 'cmr_token'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message)
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

async function request<T>(
  method: string,
  path: string,
  opts: { body?: unknown; form?: FormData; params?: Record<string, unknown> } = {},
): Promise<T> {
  const headers: Record<string, string> = {}
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
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
    })
  } catch {
    throw new ApiError('网络异常，请检查后端服务', 0)
  }
  if (resp.status === 401) {
    clearToken()
    router.push('/login')
    throw new ApiError('登录已过期，请重新登录', 401)
  }
  const json = (await resp.json().catch(() => null)) as ApiEnvelope<T> | null
  if (!json) {
    throw new ApiError(`响应解析失败(${resp.status})`, resp.status)
  }
  if (json.code !== 0) {
    throw new ApiError(json.msg || '请求失败', resp.status)
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
  /** 携带鉴权头拉取文件流（媒体预览用，避免 <img> 无法带 token） */
  async blob(path: string, params?: Record<string, unknown>): Promise<Blob> {
    const resp = await fetch(`${BASE}${path}${queryString(params ?? {})}`, {
      headers: { Authorization: `Bearer ${getToken()}` },
    })
    if (!resp.ok) {
      throw new ApiError(`文件获取失败(${resp.status})`, resp.status)
    }
    return resp.blob()
  },
}
