import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(import.meta.dirname, 'src') },
  },
  server: {
    // The end-to-end tests run a second copy next to the one a developer may have open (e2e/).
    port: Number(process.env.FRONTEND_PORT ?? 5173),
    // Same-origin API calls in development, so the session and CSRF cookies work without CORS.
    proxy: {
      '/api': { target: process.env.API_PROXY_TARGET ?? 'http://localhost:8080' },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
