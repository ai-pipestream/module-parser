import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  // Extra hostnames the dev server should accept Host headers for.
  // Comma-separated. Use .env.local (gitignored) for machine-specific
  // entries like "krick" so they don't land in the committed config.
  const allowedHosts = env.VITE_ALLOWED_HOSTS
    ? env.VITE_ALLOWED_HOSTS.split(',').map((h) => h.trim()).filter(Boolean)
    : undefined

  return {
    plugins: [vue()],
    base: '/admin/',
    server: {
      host: 'localhost',
      port: 5178,
      strictPort: true,
      ...(allowedHosts ? { allowedHosts } : {}),
    },
    build: {
      outDir: 'dist',
    },
  }
})
