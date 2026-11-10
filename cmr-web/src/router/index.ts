import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

// 菜单/路由与 03-API契约 路由表一致；permission 为空表示三角色可见
// role 维度：admin=管理员 operator=运营 user=普通检索用户
export const routes = [
  {
    path: '/',
    component: () => import('../layout/PortalLayout.vue'),
    redirect: '/search',
    children: [
      {
        path: 'search',
        name: 'search',
        component: () => import('../views/search/SearchConsoleView.vue'),
        meta: { title: '检索台', icon: 'Search', permission: 'cmr:search:create' },
      },
      {
        path: 'search/results/:resultSetId',
        name: 'search-result',
        component: () => import('../views/search/SearchResultView.vue'),
        meta: { title: '检索结果', icon: 'Document', permission: 'cmr:search:create', hidden: true },
      },
      {
        path: 'summary/:taskNo',
        name: 'search-summary',
        component: () => import('../views/search/SearchSummaryView.vue'),
        meta: { title: '搜索结果摘要', icon: 'Tickets', permission: 'cmr:search:page', hidden: true },
      },
      {
        path: 'library',
        name: 'library',
        component: () => import('../views/dataset/LibraryView.vue'),
        meta: { title: '数据源库管理', icon: 'FolderOpened', permission: 'cmr:dataset:page' },
      },
      {
        path: 'behaviors',
        name: 'behaviors',
        component: () => import('../views/search/BehaviorView.vue'),
        meta: { title: '搜索行为', icon: 'Clock', permission: 'cmr:search:page' },
      },
      {
        path: 'tasks',
        name: 'tasks',
        component: () => import('../views/task/TaskManageView.vue'),
        meta: { title: '任务管理', icon: 'List', permission: 'cmr:task:page', hidden: true },
      },
      {
        path: 'tasks/:id',
        name: 'task-detail',
        component: () => import('../views/task/TaskDetailView.vue'),
        meta: { title: '任务详情', icon: 'Tickets', permission: 'cmr:task:page', hidden: true },
      },
      {
        path: 'strategies',
        name: 'strategies',
        component: () => import('../views/strategy/StrategyView.vue'),
        meta: { title: '策略管理', icon: 'SetUp', permission: 'cmr:strategy:page' },
      },
      {
        path: 'evaluation',
        name: 'evaluation',
        component: () => import('../views/evaluation/EvaluationView.vue'),
        meta: { title: '效果评测', icon: 'DataAnalysis', permission: 'cmr:eval:page' },
      },
      {
        path: 'operation',
        name: 'operation',
        component: () => import('../views/operation/OperationView.vue'),
        meta: { title: '结果运营', icon: 'Service', permission: 'cmr:feedback:page' },
      },
      {
        path: 'history',
        redirect: '/behaviors',
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const auth = useAuthStore()

  const permission = to.meta.permission as string | undefined
  if (permission && !auth.permissions.has(permission)) {
    return { name: 'search' }
  }
  return true
})

export default router
