import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 构建产物直接输出到后端静态目录，打成 jar 后一条命令即可访问管理台
export default defineConfig({
  plugins: [vue()],
  base: './',
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true }
    }
  }
})
