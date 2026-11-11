import { defineStore } from 'pinia'
import { me as apiMe } from '../api'
import type { UserVO } from '../api/types'

/**
 * 当前用户与权限码。
 *
 * <p>旧版用本地 JWT 登录，迁移后登录/登出由平台 UAP 负责：
 * 子应用只调用 {@code GET /cmr/auth/me} 回显用户与权限码，Cookie 会话随请求携带。
 */
interface AuthState {
  user: UserVO | null
  loaded: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    user: null,
    loaded: false,
  }),
  getters: {
    role: (s) => s.user?.role ?? '',
    username: (s) => s.user?.username ?? '',
    nickname: (s) => s.user?.nickname ?? '',
    permissions(): Set<string> {
      return new Set(this.user?.permissions ?? [])
    },
  },
  actions: {
    /** 首次进入时拉取当前用户，之后复用 */
    async ensureLoaded() {
      if (this.loaded) {
        return
      }
      try {
        this.user = await apiMe()
      } catch {
        this.user = null
      } finally {
        this.loaded = true
      }
    },
    reset() {
      this.user = null
      this.loaded = false
    },
  },
})
