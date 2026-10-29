import { fileURLToPath, URL } from 'node:url';

import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

/**
 * Vite configuration for the FlashDrop web app.
 *
 * The dev proxy forwards the public prefixes exposed by the API gateway
 * (see `gateway/docker/gateway.yaml`) so the browser talks to a single
 * origin and never to a service directly.
 */
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:3000', changeOrigin: true },
      '/auth': { target: 'http://localhost:3000', changeOrigin: true },
      '/catalog': { target: 'http://localhost:3000', changeOrigin: true },
    },
  },
});
