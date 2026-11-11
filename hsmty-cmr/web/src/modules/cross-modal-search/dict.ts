/**
 * 前端字典（占位）。
 *
 * 平台字典类型以 `docs/前后端规范/04-菜单权限与字典.md` 登记为准；当前先用
 * 本地常量支撑页面展示，等前端并入总前端且字典下发后替换为字典接口。
 */
export interface CmsDictOption {
  value: string
  label: string
}

/** 模态：与 cmr_result_item.modality、算子返回口径一致 */
export const MODALITY_OPTIONS: CmsDictOption[] = [
  { value: 'text', label: '文本' },
  { value: 'image', label: '图像' },
  { value: 'audio', label: '音频' },
  { value: 'video', label: '视频' },
]

export const MODALITY_LABELS: Record<string, string> = Object.fromEntries(
  MODALITY_OPTIONS.map((item) => [item.value, item.label]),
)

/** 文件对象状态：cmr_file_object.status */
export const FILE_STATUS_OPTIONS: CmsDictOption[] = [
  { value: 'active', label: '正常' },
]

/** 检索采用状态：cmr_search_history.adopt_status */
export const ADOPT_STATUS_OPTIONS: CmsDictOption[] = [
  { value: 'unadopted', label: '未采用' },
  { value: 'adopted', label: '已采用' },
  { value: 'discarded', label: '已废弃' },
]
