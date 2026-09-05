import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { AlertTriangle, CheckCircle2, Info, X } from 'lucide-react'
import { setToastDispatcher } from '../../lib/toastBridge'
import type { ToastInput, ToastTone } from '../../lib/toastBridge'

interface ToastItem extends ToastInput {
  id: string
}

interface ToastContextValue {
  show: (toast: ToastInput) => void
}

const ToastContext = createContext<ToastContextValue | null>(null)

const AUTO_DISMISS_MS = 6000

const TONE_STYLES: Record<ToastTone, { border: string; icon: typeof AlertTriangle }> = {
  error: { border: 'border-l-4 border-l-rose-500', icon: AlertTriangle },
  success: { border: 'border-l-4 border-l-emerald-500', icon: CheckCircle2 },
  info: { border: 'border-l-4 border-l-brand-forest', icon: Info },
}

/**
 * Standardized global toast notification system (PIMS_SPEC.md error-handling
 * task, Section 1) - this codebase had none before. Hand-rolled rather than
 * pulling in a library, matching every other shared UI primitive here
 * (Modal, ui.tsx). Mount once at the app root (see main.tsx); api/client.ts
 * reaches it via lib/toastBridge.ts since it isn't a React module itself.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const timers = useRef(new Map<string, number>())

  const dismiss = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
    const timer = timers.current.get(id)
    if (timer) {
      window.clearTimeout(timer)
      timers.current.delete(id)
    }
  }, [])

  const show = useCallback(
    (toast: ToastInput) => {
      const id = crypto.randomUUID()
      setToasts((prev) => [...prev, { ...toast, id }])
      const timer = window.setTimeout(() => dismiss(id), AUTO_DISMISS_MS)
      timers.current.set(id, timer)
    },
    [dismiss],
  )

  useEffect(() => {
    setToastDispatcher(show)
    return () => setToastDispatcher(null)
  }, [show])

  return (
    <ToastContext.Provider value={{ show }}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 bottom-4 z-[100] flex flex-col items-center gap-2 px-4 sm:items-end sm:px-6">
        {toasts.map((toast) => {
          const { border, icon: Icon } = TONE_STYLES[toast.tone]
          return (
            <div
              key={toast.id}
              className={`pointer-events-auto flex w-full max-w-sm items-start gap-2 rounded-md bg-white p-3 shadow-lg ${border}`}
            >
              <Icon
                size={18}
                className={toast.tone === 'error' ? 'text-rose-500' : toast.tone === 'success' ? 'text-emerald-500' : 'text-brand-forest'}
              />
              <div className="min-w-0 flex-1">
                {toast.title && <p className="text-sm font-semibold text-slate-800">{toast.title}</p>}
                <p className="text-sm text-slate-600">{toast.message}</p>
              </div>
              <button type="button" onClick={() => dismiss(toast.id)} className="shrink-0 text-slate-400 hover:text-slate-600">
                <X size={15} />
              </button>
            </div>
          )
        })}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used within ToastProvider')
  return ctx
}
