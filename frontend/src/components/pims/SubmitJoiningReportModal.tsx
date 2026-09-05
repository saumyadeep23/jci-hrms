import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { MapPin } from 'lucide-react'
import { apiClient } from '../../api/client'
import { describeApiErrorList, getFieldErrors } from '../../lib/apiError'
import { formatDate } from '../../lib/date'
import { useGeolocation } from '../attendance/useGeolocation'
import { Modal } from '../common/Modal'
import { FormErrorBanner } from '../common/FormErrorBanner'
import { Badge, FieldError, PrimaryButton, SecondaryButton, errorInputClass } from '../common/ui'
import type { EmployeeMovementRecordResponse, JoiningReportRequest, SessionType } from '../../types/api'

/** IST session-cutoff estimate for the live indicator only - the actual session recorded on submit is evaluated server-side from the database clock (see JoiningReportService), not from this device's clock. */
function estimateIstSession(): { time: string; session: SessionType } {
  const istNow = new Date(Date.now() + (330 - new Date().getTimezoneOffset()) * 60_000)
  const hours = istNow.getUTCHours()
  const minutes = istNow.getUTCMinutes()
  const time = `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`
  return { time, session: hours < 13 ? 'FORENOON' : 'AFTERNOON' }
}

export function SubmitJoiningReportModal({
  movement,
  onClose,
  isResubmission = false,
}: {
  movement: EmployeeMovementRecordResponse
  onClose: () => void
  /** true when amending a CLARIFICATION_REQUESTED report - pre-fills the previous entries and hits the resubmit endpoint (PUT) instead of the initial-submission one (POST). */
  isResubmission?: boolean
}) {
  const queryClient = useQueryClient()
  const { position, error: geoError, loading: geoLoading, capture } = useGeolocation()
  const [joiningReportNo, setJoiningReportNo] = useState(isResubmission ? (movement.joiningReportNo ?? '') : '')
  const [remarks, setRemarks] = useState(isResubmission ? (movement.joiningRemarks ?? '') : '')
  const [clock, setClock] = useState(estimateIstSession())

  useEffect(() => {
    const timer = setInterval(() => setClock(estimateIstSession()), 30_000)
    return () => clearInterval(timer)
  }, [])

  const estimatedAvailedDays = movement.releaseDate
    ? Math.max(0, Math.round((Date.now() - new Date(movement.releaseDate).getTime()) / 86_400_000))
    : 0
  const estimatedElCredit =
    movement.transferNature === 'ADMINISTRATIVE' && movement.transferBenefitAdmissible
      ? Math.max(0, movement.admissibleJtDays - estimatedAvailedDays)
      : 0

  const submitMutation = useMutation({
    mutationFn: async () => {
      const payload: JoiningReportRequest = {
        joiningReportNo,
        latitude: position?.latitude ?? null,
        longitude: position?.longitude ?? null,
        accuracyMeters: position?.accuracy ?? null,
        remarks: remarks || null,
      }
      return isResubmission
        ? (await apiClient.put<EmployeeMovementRecordResponse>(`/v1/pims/movements/records/${movement.id}/joining-report/resubmit`, payload))
            .data
        : (await apiClient.post<EmployeeMovementRecordResponse>(`/v1/pims/movements/records/${movement.id}/joining-report`, payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['movement-records-mine'] })
      onClose()
    },
  })
  const fieldErrors = getFieldErrors(submitMutation.error)

  return (
    <Modal title={`${isResubmission ? 'Amend & Re-submit' : 'Submit'} Joining Report - ${movement.toOfficeName}`} onClose={onClose}>
      {isResubmission && movement.clarificationRemarks && (
        <div className="mb-4 rounded-md border border-amber-300 bg-amber-50 p-3 text-xs text-amber-800">
          <p className="font-semibold">Returned for Clarification</p>
          <p className="mt-1">{movement.clarificationRemarks}</p>
        </div>
      )}

      <div className="mb-4 rounded-md bg-slate-50 p-3 text-xs text-slate-600">
        Current device time (IST estimate): <span className="font-medium">{clock.time}</span> &rarr; Session:{' '}
        <Badge tone={clock.session === 'FORENOON' ? 'success' : 'brand'}>{clock.session === 'FORENOON' ? 'Forenoon (FN)' : 'Afternoon (AN)'}</Badge>
        <p className="mt-1 text-slate-400">The session actually recorded is evaluated by the server's own database clock at the moment you submit, not this estimate.</p>
      </div>

      <div className="mb-4 grid grid-cols-3 gap-2 rounded-md border border-slate-200 p-3 text-center text-xs">
        <div>
          <p className="text-slate-400">Admissible JT</p>
          <p className="text-base font-semibold text-slate-800">{movement.admissibleJtDays} Days</p>
        </div>
        <div>
          <p className="text-slate-400">Availed (est.)</p>
          <p className="text-base font-semibold text-slate-800">{estimatedAvailedDays} Days</p>
        </div>
        <div>
          <p className="text-slate-400">Est. EL Credit</p>
          <p className="text-base font-semibold text-emerald-700">{estimatedElCredit} Days</p>
        </div>
      </div>

      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault()
          submitMutation.mutate()
        }}
      >
        {submitMutation.isError && <FormErrorBanner title="Could not submit" errors={describeApiErrorList(submitMutation.error, 'Could not submit.')} />}

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Joining Report No.</label>
          <input
            required
            value={joiningReportNo}
            onChange={(e) => setJoiningReportNo(e.target.value)}
            className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.joiningReportNo))}`}
          />
          <FieldError message={fieldErrors?.joiningReportNo} />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Location (Soft GPS)</label>
          {position ? (
            <p className="flex items-center gap-1 text-xs text-emerald-700">
              <MapPin size={13} /> Captured ({position.latitude.toFixed(5)}, {position.longitude.toFixed(5)}, &plusmn;
              {Math.round(position.accuracy)}m)
            </p>
          ) : (
            <SecondaryButton type="button" onClick={capture} disabled={geoLoading}>
              <MapPin size={14} /> {geoLoading ? 'Locating...' : 'Capture Location'}
            </SecondaryButton>
          )}
          {geoError && <p className="mt-1 text-xs text-slate-400">{geoError} - you may still submit without it.</p>}
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Remarks</label>
          <textarea
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            rows={2}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>

        <p className="text-xs text-slate-400">Order: {movement.orderRefNo} &middot; Relieved {movement.releaseDate ? formatDate(movement.releaseDate) : '—'}</p>

        <PrimaryButton type="submit" disabled={submitMutation.isPending || joiningReportNo.trim() === ''} className="w-full justify-center">
          {isResubmission ? 'Re-submit Joining Report' : 'Submit Joining Report'}
        </PrimaryButton>
      </form>
    </Modal>
  )
}
