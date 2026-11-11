// 展示格式化小工具

export function fmtTime(v?: string | null): string {
  if (!v) {
    return '-'
  }
  return String(v).replace('T', ' ').slice(0, 19)
}

/** JSON 字符串安全解析（后端 *_Json 字段展示用） */
export function parseJson<T>(raw: string | null | undefined, fallback: T): T {
  if (!raw) {
    return fallback
  }
  try {
    return JSON.parse(raw) as T
  } catch {
    return fallback
  }
}

export const MODALITY_LABELS: Record<string, string> = {
  text: '文本',
  image: '图片',
  audio: '音频',
  video: '视频',
}

export function modalityLabel(m?: string | null): string {
  if (!m) {
    return '-'
  }
  return MODALITY_LABELS[m] ?? m
}
