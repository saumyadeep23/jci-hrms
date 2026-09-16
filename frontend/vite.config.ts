import { readFileSync, existsSync } from 'node:fs'
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'

// Shared mkcert dev cert at repo-root certs/ (gitignored, machine-specific - see
// `mkcert -cert-file certs/dev-cert.pem -key-file certs/dev-key.pem localhost 127.0.0.1 ::1 <every-lan-ip>`
// run from the repo root, listing EVERY address this machine might be reached by, not just one - a
// machine with more than one active network adapter (e.g. a real LAN NIC and a separate VPN/virtual
// adapter) has more than one IP, and a cert missing one of them produces exactly
// NET::ERR_CERT_COMMON_NAME_INVALID for anyone who reaches it by the address that's missing. Find every
// candidate IP first (`ipconfig` on Windows, `ip addr` on Linux) and pass all of them to mkcert in one
// run - regenerating for a newly-relevant IP is additive (just re-run the command with the full list;
// it overwrites the same two files). Optional: only wires up HTTPS when both files exist, so `npm run
// dev` keeps working with zero setup for anyone who hasn't generated one - HTTPS is only needed for
// real-device testing (a phone's camera/geolocation are secure-context-only, and localhost's exception
// doesn't cover a LAN address). See docs/DEPLOYMENT.md for the full dev-HTTPS setup.
const certFile = '../certs/dev-cert.pem'
const keyFile = '../certs/dev-key.pem'
const httpsConfig = existsSync(certFile) && existsSync(keyFile) ? { cert: readFileSync(certFile), key: readFileSync(keyFile) } : undefined

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Node-side only (this file runs in Vite's own dev-server process, not the browser) - unrelated to
  // VITE_API_BASE_URL, which src/api/client.ts reads browser-side via import.meta.env for a completely
  // different purpose (an escape hatch for a non-proxied production deployment). This one exists only
  // for a dev machine whose backend isn't reachable at the default localhost:8080 (e.g. a different
  // port) - it never affects a production build, since server.proxy has no effect outside `vite dev`.
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const devBackendTarget = env.VITE_DEV_BACKEND_TARGET || 'http://localhost:8080'

  return {
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
      // a phone testing the mobile punch flow - see api/client.ts, which requests a relative '/api/...'
      // regardless of what host/protocol this page was loaded as, and the proxy below forwards it.
      host: true,
      https: httpsConfig,
      proxy: {
        // src/api/client.ts requests a relative '/api/...' baseURL. This proxy resolves it server-side
        // (a Node-to-Node HTTP request Vite itself makes, never a browser fetch) to the backend, which
        // always serves plain HTTP on 8080 in dev (server.ssl.enabled defaults false - see
        // application.yml) - so this works unchanged whether the page itself is HTTP or HTTPS, on
        // localhost or any LAN IP: the browser never learns the backend's real host/port and never makes
        // a cross-origin request, which is what previously broke as ERR_SSL_PROTOCOL_ERROR (the frontend
        // guessing the backend's protocol from its own) and as silent CORS rejection (a LAN IP outside the
        // backend's allowlisted dev subnets). Target defaults to http://localhost:8080, overridable via
        // VITE_DEV_BACKEND_TARGET (.env.local) for a dev machine running the backend somewhere else -
        // this is the Vite dev SERVER process reaching a backend, unrelated to whatever host/IP a BROWSER
        // used to reach Vite itself.
        '/api': {
          target: devBackendTarget,
          changeOrigin: true,
        },
      },
    },
  }
})
