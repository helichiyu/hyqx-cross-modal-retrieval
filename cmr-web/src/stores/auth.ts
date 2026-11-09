import { defineStore } from 'pinia'
import { login as apiLogin, logout as apiLogout, me as apiMe } from '../api'
import { clearToken, getToken, setToken } from '../api/http'
import type { UserVO } from '../api/types'

/* 临时关闭登录鉴权：前端固定管理员身份；恢复登录时删除本段并还原原 auth store。 */
const BYPASS_PERMISSIONS = [
  'cmr:dataset:page', 'cmr:dataset:detail', 'cmr:dataset:create', 'cmr:dataset:adjudicate', 'cmr:dataset:confirm',
  'cmr:search:create', 'cmr:search:page', 'cmr:search:adopt',
  'cmr:task:page', 'cmr:task:detail',
  'cmr:strategy:page', 'cmr:strategy:detail', 'cmr:strategy:create', 'cmr:strategy:update', 'cmr:strategy:publish',
  'cmr:eval:page', 'cmr:eval:detail', 'cmr:eval:create', 'cmr:eval:update', 'cmr:eval:run',
  'cmr:feedback:create', 'cmr:feedback:page', 'cmr:feedback:update',
  'cmr:word:page', 'cmr:word:create', 'cmr:word:update', 'cmr:word:remove',
  'cmr:file:read',
]

const BYPASS_USER: UserVO = {
  username: 'admin',
  nickname: '管理员',
  role: 'admin',
  permissions: BYPASS_PERMISSIONS,
}

interface AuthState {
  token: string
  user: UserVO | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: '',
    user: { ...BYPASS_USER },
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
      this.user = { ...BYPASS_USER }
    },
  },
})