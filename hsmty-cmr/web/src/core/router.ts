import { createRouter, createWebHistory, type Router } from 'vue-router'
import { edition } from '@/editions/obu'

/**
 * 应用路由：业务路由全部来自模块定义，最后挂一条兜底路由。
 */
export function createAppRouter(): Router {
  const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [],
  })

  edition.modules.forEach((module) => {
    if (module.guard) {
      router.beforeEach(module.guard)
    }
    module.routes.forEach((route) => router.addRoute(route))
  })

  router.addRoute({
    path: '/:pathMatch(.*)*',
    redirect: edition.modules[0]?.basePath || '/',
  })

  return router
}
