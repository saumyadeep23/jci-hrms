import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { useToast } from '../common/ToastProvider'
import { Modal } from '../common/Modal'
import { describeApiError } from '../../lib/apiError'
import { ErrorState, PrimaryButton, SecondaryButton } from '../common/ui'
import type { AttendanceRegularizationRequest, DailyAttendanceDetailResponse, RegularizationReasonCode } from '../../types/api'

export const REASON_OPTIONS: { value: RegularizationReasonCode; label: string }[] = [
  { value: 'FORGOT_PUNCH', label: 'Forgot to Punch' },
  { value: 'DEVICE_FAILURE', label: 'Device / App Failure' },
  { value: 'FIELD_DUTY', label: 'Local Official / Field Duty' },
  { value: 'GEOFENCE_ISSUE', label: 'Geofence Issue' },
  { value: 'SYSTEM_ERROR', label: 'System Error' },
  { value: 'OTHER', label: 'Other' },
]

/** Builds an IST-anchored ISO instant so the backend (Asia/Kolkata-assuming AttendanceAggregationService) interprets the corrected time the same way the muster grid displays it. */
function toIstInstant(dateIso: string, hhmm: string): string {
  return `${dateIso}T${hhmm}:00+05:30`
}

/** ALMS Phase 3, Section 4 - submits to POST /api/v1/attendance/regularization (AttendanceRegularizationService, ALMS Phase 2). */
export function RegularizationModal({
  employeeId,
  row,
  onClose,
}: {
  employeeId: number
  row: DailyAttendanceDetailResponse
  onClose: () => void
}) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [reasonCode, setReasonCode] = useState<RegularizationReasonCode>('FORGOT_PUNCH')
  const [correctedInTime, setCorrectedInTime] = useState('09:45')
  const [correctedOutTime, setCorrectedOutTime] = useState('18:15')
  const [remarks, setRemarks] = useState('')

  const submitMutation = useMutation({
    mutationFn: async () => {
      const payload: AttendanceRegularizationRequest = {
        employeeId,
        attendanceDate: row.date,
        reasonCode,
        remarks,
        correctedInTime: toIstInstant(row.date, correctedInTime),
        correctedOutTime: toIstInstant(row.date, correctedOutTime),
      }
      return (await apiClient.post('/v1/attendance/regularization', payload)).data
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'Regularization request submitted for HoD review.' })
      queryClient.invalidateQueries({ queryKey: ['attendance-regularization-mine'] })
      onClose()
    },
  })

  return (
    <Modal title={`Regularize ${row.date}`} onClose={onClose}>
      <div className="mb-3 grid grid-cols-2 gap-3 rounded-md bg-slate-50 p-3 text-sm">
        <div>
          <p className="text-xs text-slate-400">Original In-Time</p>
          <p className="font-medium text-slate-700">{row.inTime ?? '--:--:--'}</p>
        </div>
        <div>
          <p className="text-xs text-slate-400">Original Out-Time</p>
          <p className="font-medium text-slate-700">{row.outTime ?? '--:--:--'}</p>
        </div>
      </div>

      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault()
          submitMutation.mutate()
        }}
      >
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Reason Code</label>
          <select
            required
            value={reasonCode}
            onChange={(e) => setReasonCode(e.target.value as RegularizationReasonCode)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            {REASON_OPTIONS.map((r) => (
              <option key={r.value} value={r.value}>
                {r.label}
              </option>
            ))}
          </select>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Corrected In-Time</label>
            <input
              required
              type="time"
              value={correctedInTime}
              onChange={(e) => setCorrectedInTime(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Corrected Out-Time</label>
            <input
              required
              type="time"
              value={correctedOutTime}
              onChange={(e) => setCorrectedOutTime(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Justification</label>
          <textarea
            required
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            rows={3}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>

        <div className="flex gap-2">
          <SecondaryButton type="button" onClick={onClose} className="flex-1 justify-center">
            Cancel
          </SecondaryButton>
          <PrimaryButton type="submit" disabled={submitMutation.isPending} className="flex-1 justify-center">
            Submit Request
          </PrimaryButton>
        </div>

        {submitMutation.isError && (
          <ErrorState message={describeApiError(submitMutation.error, 'Could not submit the regularization request.')} />
        )}
      </form>
    </Modal>
  )
}
