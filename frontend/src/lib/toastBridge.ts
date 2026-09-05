export type ToastTone = 'error' | 'success' | 'info'

export interface ToastInput {
  tone: ToastTone
  title?: string
  message: string
}

type Dispatcher = (toast: ToastInput) => void

/**
 * Lets non-React code (api/client.ts's axios interceptor) trigger a toast
 * without importing React - ToastProvider registers itself here on mount.
 * Calling notifyToast() before the provider has mounted is a silent no-op
 * (there's nothing to show it in yet), not an error.
 */
let dispatcher: Dispatcher | null = null

export function setToastDispatcher(fn: Dispatcher | null): void {
  dispatcher = fn
}

export function notifyToast(toast: ToastInput): void {
  dispatcher?.(toast)
}
