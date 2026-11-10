/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE: string
  readonly VITE_MODULE_PREFIX: string
  readonly VITE_QIANKUN_NAME: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

interface Window {
  __POWERED_BY_QIANKUN__?: boolean
  __INJECTED_PUBLIC_PATH_BY_QIANKUN__?: string
}
