/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url'

import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // La API se sirve por el mismo origen en desarrollo. El proxy reenvía la cabecera Origin,
    // por eso el backend local debe permitir http://localhost:5173 (ver backend/.env.example).
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    mockReset: true,
    unstubGlobals: true,
    // Vitest vacía los .css; la prueba de contraste necesita leer index.css (?raw) con su contenido real.
    css: { include: [/index\.css/] },
  },
})
