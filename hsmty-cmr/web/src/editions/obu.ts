import type { CmsModule } from '@/core/module'
import crossModalSearch from '@/modules/cross-modal-search/register'

/**
 * OBU 交付版本：当前只装载跨模态检索模块。
 *
 * 新增业务模块时在此登记，不要在 router 中直接写死。
 */
export interface CmsEdition {
  name: string
  title: string
  modules: CmsModule[]
}

export const edition: CmsEdition = {
  name: 'obu',
  title: 'OBU',
  modules: [crossModalSearch],
}
