import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.png', 'assets/jci-logo.png'],
      manifest: {
        name: 'The Jute Corporation of India Limited - HRMS',
        short_name: 'JCI HRMS',
        description: 'Employee Self-Service & HR management portal for The Jute Corporation of India Limited',
        start_url: '/',
        display: 'standalone',
        background_color: '#f8fafc',
        theme_color: '#0b4d2c',
        icons: [
          {
            src: '/icons/icon-192.png',
            sizes: '192x192',
            type: 'image/png',
          },
          {
            src: '/icons/icon-512.png',
            sizes: '512x512',
            type: 'image/png',
          },
          {
            src: '/icons/icon-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
      workbox: {
        // Attendance punches and payroll data must never be served stale from
        // cache - only precache the app shell itself, let every /api/* call
        // hit the network live.
        navigateFallbackDenylist: [/^\/api\//],
      },
    }),
  ],
  server: {
    port: 5173,
    // No /api dev-server proxy: src/api/client.ts already calls the backend
    // directly at an absolute http://localhost:8080/api baseURL (with
    // withCredentials: true), and the backend's SecurityConfig now serves a
    // matching CORS policy for this origin - a proxy here would just be a
    // second, redundant path to the same place.
  },
})
