import { readFileSync, existsSync } from 'node:fs'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'

// Shared mkcert dev cert at repo-root certs/ (gitignored, machine-specific - see
// `mkcert -cert-file certs/dev-cert.pem -key-file certs/dev-key.pem localhost 127.0.0.1 ::1 <lan-ip>`
// run from the repo root). Optional: only wires up HTTPS when both files exist, so `npm run dev` keeps
// working with zero setup for anyone who hasn't generated one - HTTPS is only needed for real-device
// testing (a phone's camera/geolocation are secure-context-only, and localhost's exception doesn't
// cover a LAN address).
const certFile = '../certs/dev-cert.pem'
const keyFile = '../certs/dev-key.pem'
const httpsConfig = existsSync(certFile) && existsSync(keyFile) ? { cert: readFileSync(certFile), key: readFileSync(keyFile) } : undefined

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
    // 0.0.0.0 (not just localhost) so the dev server is reachable from another device on the LAN, e.g.
    // a phone testing the mobile punch flow - see api/client.ts for how the frontend then finds the
    // backend from whatever host it was itself loaded from.
    host: true,
    https: httpsConfig,
    // No /api dev-server proxy: src/api/client.ts already calls the backend
    // directly at an absolute http://localhost:8080/api baseURL (with
    // withCredentials: true), and the backend's SecurityConfig now serves a
    // matching CORS policy for this origin - a proxy here would just be a
    // second, redundant path to the same place.
  },
})
