import { http, type PageQuery, type PageResult } from '@/core/http'

/** 后端文件元数据（com.hsmty.cmr.pojo.vo.FileObjectVO） */
export interface FileObject {
  id: string
  bizNo: string
  bizType?: string
  originalName: string
  contentType?: string
  size: number
  sha256?: string
  storeType?: string
  status?: string
  createTime?: string
}

export interface FileObjectQuery extends PageQuery {
  keyword?: string
  status?: string
}

export function pageFiles(query: FileObjectQuery): Promise<PageResult<FileObject>> {
  return http.get<PageResult<FileObject>>('/files', { ...query })
}

export function getFile(id: string): Promise<FileObject> {
  return http.get<FileObject>(`/files/${id}`)
}

export function uploadFile(file: File, bizType?: string): Promise<FileObject> {
  const form = new FormData()
  form.append('file', file)
  if (bizType) {
    form.append('bizType', bizType)
  }
  return http.upload<FileObject>('/files', form)
}

export function removeFiles(ids: string[]): Promise<void> {
  return http.delete<void>('/files', { ids })
}

export function downloadFile(id: string): Promise<{ blob: Blob; filename: string }> {
  return http.download(`/files/${id}/content`)
}
