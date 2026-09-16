import { useEffect, useState } from 'react'
import { WifiOff } from 'lucide-react'
import { isBackendUnreachable, subscribeToNetworkStatus } from '../../lib/networkStatus'

/**
 * One shared banner for "the whole backend is unreachable" (see lib/networkStatus.ts), mounted once at
 * the app root (App.tsx) rather than duplicated per page. Individual cards still show their own
 * ErrorState underneath when this fires - that's fine, this banner explains *why* they all failed at
 * once instead of leaving the user to conclude four separate features broke simultaneously.
 *
 * Distinguishes "your device is offline" (navigator.onLine - nothing on the network is reachable, not
 * specifically this backend) from "the backend specifically is unreachable" (isBackendUnreachable - a
 * failed apiClient request with no response) wherever the browser can tell the two apart, since the
 * right message and mental model differ (fix your own connection vs. wait for HRMS to come back).
 */
export function NetworkStatusBanner() {
  const [unreachable, setUnreachable] = useState(isBackendUnreachable())
  const [offline, setOffline] = useState(() => typeof navigator !== 'undefined' && !navigator.onLine)

  useEffect(() => subscribeToNetworkStatus(setUnreachable), [])

  useEffect(() => {
    const goOffline = () => setOffline(true)
    const goOnline = () => setOffline(false)
    window.addEventListener('offline', goOffline)
    window.addEventListener('online', goOnline)
    return () => {
      window.removeEventListener('offline', goOffline)
      window.removeEventListener('online', goOnline)
    }
  }, [])

  if (!unreachable && !offline) return null

  return (
    <div
      role="status"
      className="flex items-center justify-center gap-2 border-b border-amber-300 bg-amber-50 px-4 py-2 text-sm font-medium text-amber-800"
    >
      <WifiOff size={16} />
      {offline ? 'You appear to be offline. Reconnect to continue using HRMS.' : 'HRMS service is temporarily unavailable. Retrying...'}
    </div>
  )
}
