import type { DecodedToken, Role } from '../types/auth'

/**
 * Decodes a JWT's payload for UI purposes only (role-gating which nav items
 * and routes render). This performs NO signature verification - the backend
 * resource server (see SecurityConfig.jwtDecoder in the Spring app) is the
 * only party that actually validates a token. Never treat anything derived
 * from this as an authorization decision the backend hasn't already made.
 */
export function decodeJwt(token: string): DecodedToken | null {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=')
    const json = decodeURIComponent(
      atob(padded)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    )
    return JSON.parse(json) as DecodedToken
  } catch {
    return null
  }
}

const KNOWN_ROLES: Role[] = ['EMPLOYEE', 'HR_ADMIN', 'FINANCE_ADMIN', 'CPF_ADMIN', 'COOP_ADMIN', 'SUPER_ADMIN']

export function extractRoles(decoded: DecodedToken | null): Role[] {
  if (!decoded) return []
  const roles = new Set<string>()
  // Root-level flat claim - checked first since it's the most common shape
  // for a hand-minted local-dev token (see LoginPage's "Developer sign-in").
  decoded.roles?.forEach((r) => roles.add(r))
  decoded.realm_access?.roles?.forEach((r) => roles.add(r))
  Object.values(decoded.resource_access ?? {}).forEach((client) => client.roles?.forEach((r) => roles.add(r)))
  return KNOWN_ROLES.filter((r) => roles.has(r))
}

export function isExpired(decoded: DecodedToken | null): boolean {
  if (!decoded?.exp) return false
  return decoded.exp * 1000 < Date.now()
}
