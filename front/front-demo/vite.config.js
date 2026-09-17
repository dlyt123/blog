import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需引入：模板里出现的 el-* 组件会自动 import，
    // 并且只打包对应组件的样式（原来是全量引入 1MB JS + 376KB CSS，实际只用了 15 个组件）。
    // ⚠️ 以「函数方式」调用的 ElMessage / ElMessageBox 和 v-loading 指令不在模板里，
    //    它们需要的样式在 main.js 里手动补。
    Components({
      resolvers: [ElementPlusResolver()],
      // 项目是纯 JS，不需要生成类型声明文件
      dts: false
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  server: {
    port: 5173,
    proxy: {
      // 前端请求 /api 和 /uploads 时，代理到后端 8080
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
