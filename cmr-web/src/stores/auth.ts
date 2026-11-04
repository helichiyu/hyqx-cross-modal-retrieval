import { defineStore } from 'pinia'
import { login as apiLogin, logout as apiLogout, me as apiMe } from '../api'
import { clearToken, getToken, setToken } from '../api/http'
import type { UserVO } from '../api/types'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken(),
    user: null as UserVO | null,
  }),
  getters: {
    role: (s) => s.user?.role ?? '',
    permissions(): Set<string> {
      return new Set(this.user?.permissions ?? [])
    },
  },
  actions: {
    async login(username: string, password: string) {
      const vo = await apiLogin(username, password)
      this.token = vo.token
      this.user = vo.user
      setToken(vo.token)
    },
    async fetchUser() {
      if (!this.token) {
        return
      }
      try {
        this.user = await apiMe()
      } catch {
        this.reset()
      }
    },
    async logout() {
      try {
        await apiLogout()
      } catch {
        // 退出登录失败也按本地登出处理
      }
      this.reset()
    },
    reset() {
      this.token = ''
      this.user = null
      clearToken()
    },
  },
})
