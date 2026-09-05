import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { decodeJwt, extractRoles, isExpired } from '../lib/jwt'
import { TOKEN_STORAGE_KEY } from '../api/client'
import type { AuthState, Role } from '../types/auth'

interface AuthContextValue extends AuthState {
  isAuthenticated: boolean
  setToken: (token: string) => void
  logout: () => void
  hasRole: (...roles: Role[]) => boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

function readInitialToken(): string | null {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY)
  if (!token) return null
  const decoded = decodeJwt(token)
  if (isExpired(decoded)) {
    localStorage.removeItem(TOKEN_STORAGE_KEY)
    return null
  }
  return token
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(() => readInitialToken())

  const setToken = useCallback((next: string) => {
    localStorage.setItem(TOKEN_STORAGE_KEY, next)
    setTokenState(next)
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_STORAGE_KEY)
    setTokenState(null)
  }, [])

  const value = useMemo<AuthContextValue>(() => {
    const decoded = token ? decodeJwt(token) : null
    const roles = extractRoles(decoded)
    const employeeIdRaw = decoded?.employee_id
    const employeeId =
      employeeIdRaw !== undefined && employeeIdRaw !== null && !Number.isNaN(Number(employeeIdRaw))
        ? Number(employeeIdRaw)
        : null

    return {
      token,
      roles,
      employeeId,
      subject: decoded?.sub ?? null,
      isAuthenticated: Boolean(token),
      setToken,
      logout,
      hasRole: (...allowed) => allowed.some((role) => roles.includes(role)),
    }
  }, [token, setToken, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
