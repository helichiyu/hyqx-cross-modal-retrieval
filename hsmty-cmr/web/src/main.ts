import { createApp, type App as VueApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import { qiankunWindow, renderWithQiankun } from 'vite-plugin-qiankun/dist/helper'
import App from './App.vue'
import { createAppRouter } from './core/router'
import './styles/index.css'

/** qiankun 注入的 props 中最少需要容器节点 */
interface QiankunProps {
  container?: HTMLElement
  [key: string]: unknown
}

let app: VueApp<Element> | null = null

function render(props: QiankunProps = {}) {
  const router = createAppRouter()
  app = createApp(App)
  app.use(createPinia())
  app.use(router)
  app.use(ElementPlus, { locale: zhCn })

  const mountPoint = props.container?.querySelector('#app') ?? '#app'
  app.mount(mountPoint as Element | string)
}

renderWithQiankun({
  bootstrap() {
    // 子应用首次加载，无需额外初始化
  },
  mount(props) {
    render(props as QiankunProps)
  },
  update() {
    // 主应用未下发可变状态，无需响应
  },
  unmount() {
    app?.unmount()
    app = null
  },
})

if (!qiankunWindow.__POWERED_BY_QIANKUN__) {
  render()
}
