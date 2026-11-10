import { defineModule } from '@/core/module'

/**
 * 跨模态检索模块。
 *
 * 组件与路由统一以 `Cms` / `/cms` 为前缀，避免与主应用及其它子应用冲突。
 */
export default defineModule({
  name: 'cross-modal-search',
  title: '跨模态检索',
  basePath: '/cms',
  routes: [
    {
      path: '/cms',
      name: 'CmsHome',
      component: () => import('./views/CmsHomeView.vue'),
      meta: { title: '跨模态检索概览' },
    },
    {
      path: '/cms/search',
      name: 'CmsSearch',
      component: () => import('./views/CmsSearchView.vue'),
      meta: { title: '跨模态检索' },
    },
    {
      path: '/cms/files',
      name: 'CmsFile',
      component: () => import('./views/CmsFileView.vue'),
      meta: { title: '文件管理' },
    },
  ],
})
