import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { CheckCircle2, LogIn, LogOut, MapPin } from 'lucide-react'
import { AttendanceDetailTable } from '../../components/attendance/AttendanceDetailTable'
import { CameraCapture } from '../../components/attendance/CameraCapture'
import { RegisteredDevicesCard } from '../../components/attendance/RegisteredDevicesCard'
import { useGeolocation } from '../../components/attendance/useGeolocation'
import { Badge, Card, ErrorState, PageHeader, PrimaryButton } from '../../components/common/ui'
import { useAuth } from '../../auth/AuthContext'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import type { MobilePunchRequest, MobilePunchResponse, PunchType } from '../../types/api'

/**
 * Phase 12 spec asks for POST /api/attendance/mobile-punch. The backend's
 * real route (Phase 4, MobilePunchController) is POST /api/attendance/punch
 * - there is no /mobile-punch path anywhere. Wired to the route that
 * actually exists rather than one that 404s.
 */
const PUNCH_ENDPOINT = '/attendance/punch'

/** Pulls the backend's actual ErrorResponse body out of an Axios error so validation failures are visible, not just a generic status code. */
function describePunchError(error: unknown): string {
  if (isAxiosError(error)) {
    const body = error.response?.data
    if (body && typeof body === 'object') {
      const message = 'message' in body ? String((body as Record<string, unknown>).message) : undefined
      const fieldErrors = 'errors' in body ? (body as Record<string, unknown>).errors : undefined
      return [message, fieldErrors ? JSON.stringify(fieldErrors) : null].filter(Boolean).join(' - ') || error.message
    }
    return error.message
  }
  return error instanceof Error ? error.message : 'Punch failed.'
}

export function AttendancePunchPage() {
  const { employeeId } = useAuth()
  const queryClient = useQueryClient()
  const [punchType, setPunchType] = useState<PunchType>('IN')
  const [photo, setPhoto] = useState<string | null>(null)
  const { position, error: geoError, loading: geoLoading, capture: captureLocation } = useGeolocation()

  const punchMutation = useMutation({
    mutationFn: async () => {
      if (!position) throw new Error('Capture your location before punching.')
      // employeeId is included when the token exposes it, but it's optional -
      // the backend now derives it from the JWT's employee_id claim if omitted.
      const payload: MobilePunchRequest = {
        ...(employeeId ? { employeeId } : {}),
        punchTime: new Date().toISOString(),
        punchType,
        latitude: position.latitude,
        longitude: position.longitude,
        accuracyMeters: position.accuracy || 10,
        deviceId: navigator.userAgent.slice(0, 100),
        // Placeholder key, not a real upload - there is no object-storage
        // endpoint anywhere in this backend to actually put the captured
        // photo (see CameraCapture's on-screen note), so this string is
        // sent as-is rather than derived from anything real.
        photoS3Key: 'photos/web_cam_punch.jpg',
      }
      const response = await apiClient.post<MobilePunchResponse>(PUNCH_ENDPOINT, payload)
      return response.data
    },
    onSuccess: () => {
      // The backend upserts today's daily_attendance row synchronously as
      // part of the punch itself (MobilePunchService.create), so refetching
      // now is enough to show it - an IN-only punch appears as IN_PROGRESS
      // until an OUT punch closes the day out.
      queryClient.invalidateQueries({ queryKey: ['attendance-aggregation'] })
    },
    onError: (error) => {
      // eslint-disable-next-line no-console
      console.error(
        'Attendance punch failed:',
        isAxiosError(error) ? { status: error.response?.status, body: error.response?.data, request: error.config?.data } : error,
      )
    },
  })

  const canSubmit = Boolean(position) && Boolean(photo) && !punchMutation.isPending

  return (
    <div>
      <PageHeader title="Mobile Attendance" description="Facial + geo-fenced punch capture" />

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <div className="mb-4 flex justify-center gap-2">
            {(['IN', 'OUT'] as const).map((type) => (
              <button
                key={type}
                type="button"
                onClick={() => setPunchType(type)}
                className={`flex items-center gap-1.5 rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
                  punchType === type ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'
                }`}
              >
                {type === 'IN' ? <LogIn size={14} /> : <LogOut size={14} />}
                Punch {type}
              </button>
            ))}
          </div>

          <CameraCapture captured={photo} onCapture={setPhoto} onRetake={() => setPhoto(null)} />

          <div className="mt-5 border-t border-slate-100 pt-4">
            <div className="flex items-center justify-between">
              <p className="flex items-center gap-1.5 text-sm font-medium text-slate-700">
                <MapPin size={15} /> Location
              </p>
              {!position && (
                <button
                  type="button"
                  onClick={captureLocation}
                  disabled={geoLoading}
                  className="text-xs font-medium text-brand-forest hover:underline disabled:opacity-50"
                >
                  {geoLoading ? 'Locating...' : 'Capture location'}
                </button>
              )}
            </div>
            {position && (
              <p className="mt-1 text-xs text-slate-500">
                {position.latitude.toFixed(6)}, {position.longitude.toFixed(6)} (±{Math.round(position.accuracy)}m)
              </p>
            )}
            {geoError && <p className="mt-1 text-xs text-red-600">{geoError}</p>}
          </div>

          <PrimaryButton onClick={() => punchMutation.mutate()} disabled={!canSubmit} className="mt-4 w-full justify-center">
            Submit Punch {punchType}
          </PrimaryButton>

          {punchMutation.isError && (
            <div className="mt-3">
              <ErrorState message={describePunchError(punchMutation.error)} />
              <p className="mt-1 text-center text-[11px] text-slate-400">Full response body logged to the browser console.</p>
            </div>
          )}
        </Card>

        <div className="space-y-6">
        <Card>
          <h2 className="mb-3 text-sm font-semibold text-slate-700">Latest Punch Result</h2>
          {punchMutation.isSuccess ? (
            // A 201 Created is itself the success signal - the punch was
            // recorded regardless of how reviewStatus/isWithinGeofence come
            // back, so the checkmark is unconditional; those two fields are
            // reported as their own badges below, not folded into the icon.
            <div className="space-y-4">
              <div className="flex items-center gap-2 rounded-lg bg-emerald-50 p-3">
                <CheckCircle2 className="shrink-0 text-emerald-500" size={24} />
                <p className="text-sm font-semibold text-emerald-700">Punch Recorded</p>
              </div>
              <dl className="grid grid-cols-2 gap-y-3 text-sm">
                <dt className="text-slate-500">Punch Type</dt>
                <dd className="text-right font-medium">{punchMutation.data.punchType}</dd>

                <dt className="text-slate-500">Timestamp</dt>
                <dd className="text-right font-medium">{formatDate(punchMutation.data.punchTime, 'dd-MM-yyyy, hh:mm:ss a')}</dd>

                <dt className="text-slate-500">Geofence Status</dt>
                <dd className="text-right">
                  <Badge tone={punchMutation.data.isWithinGeofence ? 'success' : 'danger'}>
                    {punchMutation.data.isWithinGeofence ? 'Within geofence' : 'Outside geofence'}
                  </Badge>
                </dd>

                <dt className="text-slate-500">Validation Status</dt>
                <dd className="text-right">
                  <Badge tone={punchMutation.data.reviewStatus === 'VALID' ? 'success' : 'warning'}>
                    {punchMutation.data.reviewStatus.replace(/_/g, ' ')}
                  </Badge>
                </dd>
              </dl>
            </div>
          ) : (
            <p className="text-sm text-slate-500">Submit a punch to see the result here.</p>
          )}
        </Card>

        <RegisteredDevicesCard />
        </div>
      </div>

      <div className="mt-6">
        <AttendanceDetailTable />
      </div>
    </div>
  )
}
