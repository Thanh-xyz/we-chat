import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  const proxyTarget = env.VITE_DEV_PROXY_TARGET

  return {
    plugins: [react()],
    server: proxyTarget
      ? {
          proxy: {
            '/api': { target: proxyTarget, changeOrigin: true },
            '/ws': { target: proxyTarget, ws: true, changeOrigin: true },
          },
        }
      : undefined,
    test: {
      environment: 'jsdom',
      setupFiles: './src/test/setup.js',
      css: true,
      clearMocks: true,
      exclude: ['**/node_modules/**', '**/dist/**', 'e2e/**'],
    },
  }
})
