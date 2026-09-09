import axios from 'axios'
import { notifyToast } from '../lib/toastBridge'

const TOKEN_STORAGE_KEY = 'jci-hrms-token'

// Derived from whatever host/protocol this page was itself loaded from, rather than a hardcoded
// "localhost" - that broke entirely for a phone on the LAN (its own "localhost" is the phone, not the
// dev machine serving the frontend). The backend is always dev-server-port 8080 on that same host, and
// same protocol as the page (SecurityConfig's CORS origins are HTTPS-only for a non-localhost host, since
// an HTTPS page calling an HTTP API gets blocked as mixed content anyway).
const API_BASE_URL = `${window.location.protocol}//${window.location.hostname}:8080/api`

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  // Matches SecurityConfig.corsConfigurationSource() on the backend, which
  // sets Access-Control-Allow-Credentials: true for the allowed dev origins.
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
  (response) => response,
  (error) => {
    if (!error.response) {
      notifyToast({
        tone: 'error',
        title: 'Connection lost',
        message: 'Network connection lost. Please check your connection and retry.',
      })
      return Promise.reject(error)
    }

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
