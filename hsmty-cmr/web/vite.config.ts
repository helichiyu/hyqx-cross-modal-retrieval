import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import qiankun from 'vite-plugin-qiankun'
import { fileURLToPath, URL } from 'node:url'

/**
 * 构建配置。
 *
 * - 部署路径固定 /sdom-hsmty/，与主应用 qiankun 的 activeRule 保持一致；
 * - 开发端口 9848，/hs-api 代理到本模块后端；
 * - qiankun 子应用名取自环境变量，改名时只改 .env。
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const appName = env.VITE_QIANKUN_NAME || 'cross-modal-search'
  const publicPath = env.VITE_PUBLIC_PATH || '/sdom-hsmty/'

  return {
    base: publicPath,
    plugins: [
      vue(),
      qiankun(appName, { useDevMode: false }),
      {
        // 单独启动子应用时，根路径没有任何资源（base 是 /sdom-hsmty/），
        // 开发态重定向到部署路径，避免打开 9848 端口看到空白页。
        name: 'cms-dev-base-redirect',
        configureServer(server) {
          server.middlewares.use((req, res, next) => {
            if (req.url === '/' || req.url === '') {
              res.writeHead(302, { Location: publicPath })
              res.end()
              return
            }
            next()
          })
        },
      },
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: Number(env.VITE_PORT || 9848),
      host: '0.0.0.0',
      proxy: {
        '/hs-api': {
          target: env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080',
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
      chunkSizeWarningLimit: 1500,
    },
  }
})
