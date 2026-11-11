/**
 * 前端静态菜单（占位）。
 *
 * 后续前端会并入平台总前端，菜单以平台 `sys_menu` 下发为准；这里先按
 * `docs/前后端规范/04-菜单权限与字典.md` 的口径维护当前已实现的三个页面，
 * 权限码与后端 `@PreAuthorize` 保持一致。
 */
export interface CmsMenuItem {
  /** 路由地址 */
  path: string
  /** 菜单名称 */
  title: string
  /** 访问所需权限码 */
  permission: string
}

export const cmsMenus: CmsMenuItem[] = [
  { path: '/cms', title: '概览', permission: 'cmr:search:create' },
  { path: '/cms/search', title: '跨模态检索', permission: 'cmr:search:create' },
  { path: '/cms/files', title: '文件管理', permission: 'cmr:file:page' },
]
