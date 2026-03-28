import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  base: '/modules/parser/admin/',
  server: {
    host: 'localhost',
    port: 5178,
    strictPort: true,
  },
  build: {
    outDir: 'dist',
  }
})
