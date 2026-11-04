import { defineStore } from 'pinia'
import { dict } from '../api'
import type { DictItem } from '../api/types'

// 字典缓存：cmr_scene / cmr_dataset_status / cmr_task_status ... 一次拉取全程复用
export const useDictStore = defineStore('dict', {
  state: () => ({
    cache: {} as Record<string, DictItem[]>,
    loading: {} as Record<string, boolean>,
  }),
  actions: {
    async ensure(type: string): Promise<DictItem[]> {
      if (this.cache[type]) {
        return this.cache[type]
      }
      if (this.loading[type]) {
        return []
      }
      this.loading[type] = true
      try {
        this.cache[type] = await dict(type)
      } catch {
        this.cache[type] = []
      } finally {
        this.loading[type] = false
      }
      return this.cache[type]
    },
  },
})
