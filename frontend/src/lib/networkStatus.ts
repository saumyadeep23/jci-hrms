/**
 * Whole-backend reachability, shared across every apiClient call (see api/client.ts's response
 * interceptor) so the UI can show ONE "service unavailable" state instead of every independently-failing
 * card rendering its own misleading "could not load X" message when the actual problem is that nothing
 * can reach the backend at all (ERR_CONNECTION_REFUSED, DNS failure, timeout, offline).
 *
 * Deliberately not React Context: this is written to from an axios interceptor, which runs outside any
 * component's render tree and may fire before the app has even mounted a provider.
 */
type Listener = (unreachable: boolean) => void

let unreachable = false
const listeners = new Set<Listener>()

export function setBackendUnreachable(next: boolean): void {
  if (next === unreachable) return
  unreachable = next
  listeners.forEach((listener) => listener(unreachable))
}

export function isBackendUnreachable(): boolean {
  return unreachable
}

export function subscribeToNetworkStatus(listener: Listener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
