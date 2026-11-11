import { describe, expect, it } from 'vitest'
import crossModalSearch from './register'
import { cmsMenus } from './menu'
import { MODALITY_OPTIONS } from './dict'

describe('cross-modal-search 模块注册', () => {
  it('使用 cms 作为模块名与路由前缀', () => {
    expect(crossModalSearch.name).toBe('cross-modal-search')
    expect(crossModalSearch.basePath).toBe('/cms')
    expect(crossModalSearch.routes.every((route) => route.path.startsWith('/cms'))).toBe(true)
  })

  it('登记概览、检索、文件三类路由', () => {
    expect(crossModalSearch.routes.map((route) => route.name)).toEqual([
      'CmsHome',
      'CmsSearch',
      'CmsFile',
    ])
  })

  it('静态菜单覆盖已实现页面且权限码与后端一致', () => {
    expect(cmsMenus.map((menu) => menu.path)).toEqual([
      '/cms',
      '/cms/search',
      '/cms/files',
    ])
    expect(cmsMenus.every((menu) => menu.permission.startsWith('cmr:'))).toBe(true)
  })

  it('字典包含四类模态', () => {
    expect(MODALITY_OPTIONS.map((item) => item.value)).toEqual([
      'text',
      'image',
      'audio',
      'video',
    ])
  })
})
