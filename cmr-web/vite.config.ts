import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发代理到本机后端（/hs-api），生产由 nginx 反代同源
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/hs-api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
