import { defineModule } from '@/core/module'
import { useAuthStore } from './stores/auth'

/**
 * 跨模态检索模块。
 *
 * <p>页面与原型 v5 一一对应：检索台（P1）、检索结果（P2）、结果摘要（P3）、
 * 数据源库管理（P4）、策略管理（P5）、搜索行为（P6）、结果运营（P7）、效果评测（P8）。
 * 失败任务并入搜索行为展示，不单独提供任务管理入口；登录由平台 UAP 统一负责。
 */
export default defineModule({
  name: 'cross-modal-search',
  title: '跨模态检索',
  basePath: '/search',
  guard: async (to) => {
    const auth = useAuthStore()
    await auth.ensureLoaded()
    const permission = to.meta.permission as string | undefined
    if (permission && auth.user && !auth.permissions.has(permission)) {
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
          meta: { title: '数据源库管理', icon: 'Coin', permission: 'cmr:dataset:page' },
        },
        {
          path: 'strategies',
          name: 'strategies',
          component: () => import('./views/strategy/StrategyView.vue'),
          meta: { title: '策略管理', icon: 'SetUp', permission: 'cmr:strategy:page' },
        },
        {
          path: 'behaviors',
          name: 'behaviors',
          component: () => import('./views/search/BehaviorView.vue'),
          meta: { title: '搜索行为', icon: 'DocumentCopy', permission: 'cmr:search:page' },
        },
        {
          path: 'operation',
          name: 'operation',
          component: () => import('./views/operation/OperationView.vue'),
          meta: { title: '结果运营', icon: 'Star', permission: 'cmr:feedback:page' },
        },
        {
          path: 'evaluation',
          name: 'evaluation',
          component: () => import('./views/evaluation/EvaluationView.vue'),
          meta: { title: '效果评测', icon: 'Histogram', permission: 'cmr:eval:page' },
        },
      ],
    },
  ],
})
