import axios from 'axios'
import { notifyToast } from '../lib/toastBridge'
import { setBackendUnreachable } from '../lib/networkStatus'
import { isNetworkError } from '../lib/apiError'

const TOKEN_STORAGE_KEY = 'jci-hrms-token'

// Relative by design - NEVER derive the backend's protocol/host from window.location. The frontend page's
// own protocol (which can be HTTPS purely for getUserMedia's/geolocation's secure-context requirement on a
// LAN address - see vite.config.ts) has no necessary relationship to the backend's protocol, and copying it
// (`${window.location.protocol}//${window.location.hostname}:8080/api`, the previous approach) sent HTTPS
// requests at Spring Boot's plain-HTTP port the moment the page itself went HTTPS, on ANY host - this is
// exactly what produced ERR_SSL_PROTOCOL_ERROR for both "localhost:8080" and "10.0.11.100:8080" alike; the
// hostname was never the bug. A relative baseURL asks the browser for `${current origin}/api/...`, which:
//  - in dev, hits Vite's own origin (any protocol, any host/IP) and is proxied server-side (Node-to-Node,
//    not a browser fetch, so it is never subject to CORS or mixed-content) to the backend over plain HTTP,
//    see vite.config.ts's server.proxy;
//  - in production, hits the same reverse-proxy origin the page itself was served from, which terminates
//    TLS and forwards /api/* to the backend internally - see docs/DEPLOYMENT.md.
// VITE_API_BASE_URL remains as an explicit escape hatch (e.g. a frontend bundle deployed separately from
// any reverse proxy) - it must be a full origin the operator has verified actually serves this protocol.
// resolveApiBaseUrl() below refuses to let a misconfigured http:// override silently create a
// mixed-content failure on an https:// page - it falls back to the safe relative default instead (never
// the reverse: an override is never "upgraded" to https on the operator's behalf either, since this
// code cannot know whether that origin actually serves TLS).
export function resolveApiBaseUrl(configured: string | undefined, pageProtocol: string): string {
  if (!configured) return '/api'

  let parsed: URL
  try {
    parsed = new URL(configured, 'http://placeholder-for-relative-check.invalid')
  } catch {
    // Not a parseable URL at all - pass it through as-is rather than guessing; axios will surface a
    // clear error for a genuinely malformed baseURL.
    return configured
  }
  const isRelative = !/^[a-z][a-z0-9+.-]*:/i.test(configured)
  if (isRelative) return configured

  if (pageProtocol === 'https:' && parsed.protocol === 'http:') {
    console.error(
      `[apiClient] VITE_API_BASE_URL="${configured}" is http:// but this page is https:// - browsers block ` +
        'this as mixed content, and it would also send credentials over a non-TLS connection. Falling back ' +
        "to the safe relative '/api' (same-origin, proxied/reverse-proxied) instead of using it. Fix " +
        'VITE_API_BASE_URL to an https:// origin you have verified actually serves TLS, or unset it.',
    )
    return '/api'
  }
  return configured
}

const API_BASE_URL = resolveApiBaseUrl(import.meta.env.VITE_API_BASE_URL, window.location.protocol)

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  // Matches SecurityConfig.corsConfigurationSource() on the backend, which
  // sets Access-Control-Allow-Credentials: true for the allowed dev origins.
  // Harmless no-op for the normal same-origin (proxied) path above.
  withCredentials: true,
})

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// A 401 means the token is missing/expired/rejected by the resource server -
// there is no refresh-token flow anywhere in this system, so the only
// correct move is to drop it and send the user back to the login screen.
//
// Only network failures, 403s, and 5xx get an automatic toast here. 400/404/
// 409/422 carry a specific business message (`ErrorResponse.message`) that
// the calling form already surfaces inline (ErrorState / FormErrorBanner /
// a field error) - auto-toasting those too would show the same failure
// twice in two different places, which is worse UX, not better.
apiClient.interceptors.response.use(
  (response) => {
    setBackendUnreachable(false)
    return response
  },
  (error) => {
    if (isNetworkError(error)) {
      // No response at all (ERR_CONNECTION_REFUSED, DNS failure, timeout, offline) - this is the whole
      // backend being unreachable, not any one card's data failing. Surfaced once via the shared
      // NetworkStatusBanner (see App.tsx) rather than a separate toast per failed request - four
      // simultaneously-failing cards previously produced four identical "Connection lost" toasts and four
      // unrelated-looking per-card error messages, which is exactly what made the dashboard look like four
      // independent bugs instead of one outage.
      setBackendUnreachable(true)
      return Promise.reject(error)
    }
    setBackendUnreachable(false)

    const status = error.response.status
    if (status === 401) {
      localStorage.removeItem(TOKEN_STORAGE_KEY)
      if (!window.location.pathname.startsWith('/login')) {
        window.location.assign('/login')
      }
    } else if (status === 403) {
      notifyToast({ tone: 'error', title: 'Access denied', message: "You don't have permission to do that." })
    } else if (status >= 500) {
      notifyToast({ tone: 'error', title: 'Server error', message: 'Something went wrong on our end. Please try again shortly.' })
    }

    return Promise.reject(error)
  },
)

export { TOKEN_STORAGE_KEY }
