import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Smartphone } from 'lucide-react'
import { apiClient } from '../../api/client'
import { describeApiError } from '../../lib/apiError'
import { formatDate } from '../../lib/date'
import { useToast } from '../common/ToastProvider'
import { Badge, Card, LoadingState, PrimaryButton } from '../common/ui'
import type { DeviceApprovalStatus, DeviceRegistrationRequest, DeviceType, RegisteredDeviceResponse } from '../../types/api'

const STATUS_TONE: Record<DeviceApprovalStatus, 'neutral' | 'success' | 'warning' | 'danger' | 'brand'> = {
  PENDING_APPROVAL: 'warning',
  APPROVED: 'success',
  REVOKED: 'danger',
}
const STATUS_LABELS: Record<DeviceApprovalStatus, string> = {
  PENDING_APPROVAL: 'Pending Approval',
  APPROVED: 'Approved',
  REVOKED: 'Revoked',
}
export const DEVICE_TYPE_LABELS: Record<DeviceType, string> = {
  ANDROID_MOBILE: 'Android',
  IOS_MOBILE: 'iOS',
  LAPTOP_DESKTOP_WEB: 'Laptop/Desktop (Web)',
  LAPTOP_DESKTOP_CLIENT: 'Laptop/Desktop (Client)',
  BIOMETRIC_TERMINAL: 'Biometric Terminal',
}

const DEVICE_IDENTIFIER_KEY = 'jci-hrms-device-identifier'

/** A stable per-browser identifier, persisted in localStorage - re-registering from the same browser reuses it, matching the backend's unique (employee, deviceIdentifier) constraint and its reset-to-pending-on-re-register behavior. */
function getOrCreateDeviceIdentifier(): string {
  let id = localStorage.getItem(DEVICE_IDENTIFIER_KEY)
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem(DEVICE_IDENTIFIER_KEY, id)
  }
  return id
}

/** Mobile OS vs Desktop/Browser, per UAT issue #5's "Register Current Device" requirement. */
function detectDeviceType(): DeviceType {
  const ua = navigator.userAgent
  if (/android/i.test(ua)) return 'ANDROID_MOBILE'
  if (/iphone|ipad|ipod/i.test(ua)) return 'IOS_MOBILE'
  return 'LAPTOP_DESKTOP_WEB'
}

function detectDeviceName(): string {
  const ua = navigator.userAgent
  if (/android/i.test(ua)) return 'Android Device'
  if (/iphone|ipad/i.test(ua)) return 'iOS Device'
  if (/windows/i.test(ua)) return 'Windows Browser'
  if (/mac os/i.test(ua)) return 'macOS Browser'
  return 'Web Browser'
}

/** Attendance & Facial Punch page's "My Registered Devices" card (ALMS operational gap #3, ESS side). */
export function RegisteredDevicesCard() {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [friendlyName, setFriendlyName] = useState('')

  const { data, isLoading, isError } = useQuery({
    queryKey: ['registered-devices-mine'],
    queryFn: async () => (await apiClient.get<RegisteredDeviceResponse[]>('/v1/attendance/devices/mine')).data,
  })

  const registerMutation = useMutation({
    mutationFn: async () => {
      const payload: DeviceRegistrationRequest = {
        deviceName: friendlyName.trim() || detectDeviceName(),
        deviceType: detectDeviceType(),
        deviceIdentifier: getOrCreateDeviceIdentifier(),
        platform: navigator.userAgent.slice(0, 100) || null,
      }
      await apiClient.post('/v1/attendance/devices/register', payload)
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'Device registered - awaiting admin approval.' })
      // Refreshes the table above immediately so the new PENDING_APPROVAL row is visible without a manual reload.
      queryClient.invalidateQueries({ queryKey: ['registered-devices-mine'] })
      setFriendlyName('')
    },
    onError: (error) => show({ tone: 'error', message: describeApiError(error, 'Could not register this device.') }),
  })

  const currentDeviceId = getOrCreateDeviceIdentifier()
  const currentDeviceRegistered = (data ?? []).some((d) => d.deviceIdentifier === currentDeviceId)

  return (
    <Card>
      <h2 className="mb-3 flex items-center gap-1.5 text-sm font-semibold text-slate-700">
        <Smartphone size={15} /> My Registered Devices
      </h2>

      {isLoading && <LoadingState label="Loading your devices..." />}
      {isError && <p className="text-sm text-red-600">Could not load your registered devices.</p>}

      {data && (
        <div className="space-y-2">
          {data.length === 0 && <p className="text-sm text-slate-500">No devices registered yet.</p>}
          {data.map((device) => (
            <div key={device.id} className="rounded-md border border-slate-200 px-3 py-2 text-sm">
              <div className="flex items-center justify-between">
                <p className="font-medium text-slate-700">{device.deviceName}</p>
                <Badge tone={STATUS_TONE[device.status]}>{STATUS_LABELS[device.status]}</Badge>
              </div>
              <p className="mt-0.5 text-xs text-slate-400">
                {DEVICE_TYPE_LABELS[device.deviceType]} · {device.deviceIdentifier.slice(0, 8)}... · Registered{' '}
                {formatDate(device.createdAt)}
              </p>
            </div>
          ))}
        </div>
      )}

      {!currentDeviceRegistered && (
        <input
          value={friendlyName}
          onChange={(e) => setFriendlyName(e.target.value)}
          placeholder={`Friendly name (optional, defaults to "${detectDeviceName()}")`}
          maxLength={150}
          className="mt-4 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      )}
      <PrimaryButton
        onClick={() => registerMutation.mutate()}
        disabled={registerMutation.isPending || currentDeviceRegistered}
        className="mt-2 w-full justify-center"
      >
        {currentDeviceRegistered ? 'This Device Is Already Registered' : registerMutation.isPending ? 'Registering...' : 'Register Current Device'}
      </PrimaryButton>
    </Card>
  )
}
