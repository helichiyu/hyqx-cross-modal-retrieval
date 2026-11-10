import { createRouter, createWebHistory, type Router } from 'vue-router'
import { edition } from '@/editions/obu'

/**
 * 应用路由：只注册核心兜底路由，业务路由全部来自模块定义。
 */
export function createAppRouter(): Router {
  const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
      {
        path: '/',
        redirect: edition.modules[0]?.basePath || '/cms',
      },
    ],
  })

  edition.modules.forEach((module) => {
    module.routes.forEach((route) => router.addRoute(route))
  })

  return router
}
