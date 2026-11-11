import type { NavigationGuard, RouteRecordRaw } from 'vue-router'

/**
 * 业务模块定义。
 *
 * 每个模块只在 `src/modules/<模块>/register.ts` 中声明一次，
 * 由 `src/editions/*` 决定当前交付版本装载哪些模块。
 */
export interface CmsModule {
  /** 模块短名，与后端模块前缀保持一致 */
  name: string
  /** 展示名称 */
  title: string
  /** 模块默认落点，供根路径兜底跳转使用 */
  basePath: string
  /** 可选的全局前置守卫（权限校验等），由 core/router 统一注册 */
  guard?: NavigationGuard
  routes: RouteRecordRaw[]
}

export function defineModule(module: CmsModule): CmsModule {
  return module
}
