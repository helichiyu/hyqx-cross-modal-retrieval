import { defineModule } from '@/core/module'
import { useAuthStore } from './stores/auth'

/**
 * 跨模态检索模块（旧版 cmr-web 全量复刻）。
 *
 * <p>路由与旧版一一对应：检索台、检索结果、搜索结果摘要、数据源库管理、
 * 搜索行为、任务管理/详情、策略管理、效果评测、结果运营。
 * 登录页已移除，身份由平台 UAP 统一提供。
 */
export default defineModule({
  name: 'cross-modal-search',
  title: '跨模态检索',
  basePath: '/search',
  guard: async (to) => {
    const auth = useAuthStore()
    await auth.ensureLoaded()
    const permission = to.meta.permission as string | undefined
    if (permission && !auth.permissions.has(permission)) {
      // 缺权限时回到检索台，避免与兜底路由互相重定向
      return to.path === '/search' ? true : { path: '/search' }
    }
    return true
  },
  routes: [
    {
      path: '/',
      component: () => import('./layout/PortalLayout.vue'),
      redirect: '/search',
      children: [
        {
          path: 'search',
          name: 'search',
          component: () => import('./views/search/SearchConsoleView.vue'),
          meta: { title: '检索台', icon: 'Search', permission: 'cmr:search:create' },
        },
        {
          path: 'search/results/:resultSetId',
          name: 'search-result',
          component: () => import('./views/search/SearchResultView.vue'),
          meta: { title: '检索结果', icon: 'Document', permission: 'cmr:search:create', hidden: true },
        },
        {
          path: 'summary/:taskNo',
          name: 'search-summary',
          component: () => import('./views/search/SearchSummaryView.vue'),
          meta: { title: '搜索结果摘要', icon: 'Tickets', permission: 'cmr:search:page', hidden: true },
        },
        {
          path: 'library',
          name: 'library',
          component: () => import('./views/dataset/LibraryView.vue'),
          meta: { title: '数据源库管理', icon: 'FolderOpened', permission: 'cmr:dataset:page' },
        },
        {
          path: 'behaviors',
          name: 'behaviors',
          component: () => import('./views/search/BehaviorView.vue'),
          meta: { title: '搜索行为', icon: 'Clock', permission: 'cmr:search:page' },
        },
        {
          path: 'tasks',
          name: 'tasks',
          component: () => import('./views/task/TaskManageView.vue'),
          meta: { title: '任务管理', icon: 'List', permission: 'cmr:task:page', hidden: true },
        },
        {
          path: 'tasks/:id',
          name: 'task-detail',
          component: () => import('./views/task/TaskDetailView.vue'),
          meta: { title: '任务详情', icon: 'Tickets', permission: 'cmr:task:page', hidden: true },
        },
        {
          path: 'strategies',
          name: 'strategies',
          component: () => import('./views/strategy/StrategyView.vue'),
          meta: { title: '策略管理', icon: 'SetUp', permission: 'cmr:strategy:page' },
        },
        {
          path: 'evaluation',
          name: 'evaluation',
          component: () => import('./views/evaluation/EvaluationView.vue'),
          meta: { title: '效果评测', icon: 'DataAnalysis', permission: 'cmr:eval:page' },
        },
        {
          path: 'operation',
          name: 'operation',
          component: () => import('./views/operation/OperationView.vue'),
          meta: { title: '结果运营', icon: 'Service', permission: 'cmr:feedback:page' },
        },
        {
          path: 'history',
          redirect: '/behaviors',
        },
      ],
    },
  ],
})
