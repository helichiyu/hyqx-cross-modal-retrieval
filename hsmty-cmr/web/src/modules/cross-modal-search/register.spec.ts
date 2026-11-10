import { describe, expect, it } from 'vitest'
import crossModalSearch from './register'

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
})
