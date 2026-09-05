import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import { KeyRound, Loader2, ShieldAlert } from 'lucide-react'
import { BrandMark } from '../../components/common/BrandMark'
import { useAuth } from '../../auth/AuthContext'
import { apiClient } from '../../api/client'

/**
 * There is no token-issuance endpoint anywhere in the backend yet (it's a
 * pure OAuth2 resource server - see SecurityConfig on the Spring side - it
 * validates JWTs, it doesn't mint them). The username/password form below
 * posts to the conventional /api/auth/login path and will fail until that's
 * built. Until then, "Developer sign-in" lets you paste a JWT you obtained
 * some other way (e.g. minted against the local HS256 dev secret) so the
 * rest of this app is actually testable against the real backend today.
 */
export function LoginPage() {
  const { isAuthenticated, setToken } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showDevSignIn, setShowDevSignIn] = useState(false)
  const [pastedToken, setPastedToken] = useState('')

  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const response = await apiClient.post<{ token: string }>('/auth/login', { username, password })
      setToken(response.data.token)
    } catch {
      setError('Sign-in is not available yet - this backend has no identity provider wired up. Use Developer sign-in below.')
    } finally {
      setSubmitting(false)
    }
  }

  function handleDevSignIn(e: React.FormEvent) {
    e.preventDefault()
    if (pastedToken.trim()) {
      setToken(pastedToken.trim())
    }
  }

  return (
    <div className="flex min-h-dvh items-center justify-center bg-gradient-to-b from-brand-forest to-brand-forest-dark p-4">
      <div className="w-full max-w-md rounded-2xl bg-white p-8 shadow-2xl">
        <div className="mb-8 flex flex-col items-center gap-4 text-center">
          <BrandMark size={72} showLegalName={false} />
          <div>
            <h1 className="text-lg font-semibold text-brand-forest">THE JUTE CORPORATION OF INDIA LIMITED</h1>
            <p className="text-xs text-slate-500">(A Govt. of India Enterprise)</p>
            <p className="mt-2 text-sm font-medium text-slate-600">HRMS Employee Portal</p>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Username / Employee Code</label>
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none focus:ring-1 focus:ring-brand-forest"
              placeholder="EMP-001"
            />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none focus:ring-1 focus:ring-brand-forest"
              placeholder="••••••••"
            />
          </div>

          {error && (
            <div className="flex items-start gap-2 rounded-md bg-amber-50 p-3 text-xs text-amber-800">
              <ShieldAlert size={16} className="mt-0.5 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="flex w-full items-center justify-center gap-2 rounded-md bg-brand-forest px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-brand-forest-dark disabled:opacity-60"
          >
            {submitting && <Loader2 size={16} className="animate-spin" />}
            Sign in
          </button>
        </form>

        <button
          type="button"
          onClick={() => setShowDevSignIn((v) => !v)}
          className="mt-6 flex w-full items-center justify-center gap-1.5 text-xs font-medium text-slate-400 hover:text-slate-600"
        >
          <KeyRound size={13} />
          Developer sign-in (paste a JWT)
        </button>

        {showDevSignIn && (
          <form onSubmit={handleDevSignIn} className="mt-3 space-y-2">
            <textarea
              value={pastedToken}
              onChange={(e) => setPastedToken(e.target.value)}
              rows={3}
              className="w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-xs focus:border-brand-forest focus:outline-none"
              placeholder="eyJhbGciOi..."
            />
            <button
              type="submit"
              className="w-full rounded-md border border-slate-300 bg-slate-50 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
            >
              Use this token
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
