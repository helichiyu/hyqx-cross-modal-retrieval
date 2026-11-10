import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 会话状态。
 *
 * 身份由主应用 SSO Cookie 维护，子应用不保存 Token；这里只记录
 * 会话是否已失效，用于提示用户重新登录主应用。
 */
export const useSessionStore = defineStore('cms-session', () => {
  const expired = ref(false)

  function markExpired() {
    expired.value = true
  }

  function reset() {
    expired.value = false
  }

  return { expired, markExpired, reset }
})

/** 统一挂载未登录事件监听（在主应用内嵌时由主应用负责跳登录）。 */
export function bindUnauthorizedListener(onUnauthorized: () => void): () => void {
  const handler = () => onUnauthorized()
  window.addEventListener('cms:unauthorized', handler)
  return () => window.removeEventListener('cms:unauthorized', handler)
}
