import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { env } from 'node:process'

export default defineConfig({
  plugins: [vue()],
  server: { port: 5173, strictPort: true, proxy: {
    '/api': env.CF_API_TARGET ?? 'http://localhost:8080',
    '/_AMapService': env.CF_API_TARGET ?? 'http://localhost:8080',
  } },
})
