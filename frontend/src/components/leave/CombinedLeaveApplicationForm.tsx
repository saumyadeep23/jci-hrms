import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { apiClient } from '../../api/client'
import { DateField } from '../common/DateField'
import { Card, ErrorState, PrimaryButton } from '../common/ui'
import type {
  CombinedLeaveApplicationRequest,
  CombinedLeaveApplicationResponse,
  HolidayResponse,
  LeaveSession,
  LeaveTypeResponse,
  Page,
} from '../../types/api'

type CombinedMode = 'contiguous' | 'same-day'
type RhPlacement = 'prefix' | 'suffix'

function addDays(iso: string, days: number): string {
  if (!iso) return ''
  const date = new Date(iso + 'T00:00:00')
  date.setDate(date.getDate() + days)
  return date.toISOString().slice(0, 10)
}

/**
 * ALMS Phase 3, Section 2 - dispatches to POST /api/leave-applications/combined
 * (CombinedLeaveApplicationService, ALMS Phase 2). The CL leg here is always
 * CL and the RH leg always RH, so "strictly prevent CL alongside EL/HPL/CCL"
 * is structurally satisfied by this form's shape - the real 422 case this
 * guards against is the CL leg landing contiguous with an existing APPROVED
 * EL/HPL/CCL application, which the backend rejects and this form surfaces
 * as an inline alert.
 */
export function CombinedLeaveApplicationForm({ employeeId, onSaved }: { employeeId: number; onSaved: () => void }) {
  const leaveTypesQuery = useQuery({
    queryKey: ['leave-types'],
    queryFn: async () => (await apiClient.get<Page<LeaveTypeResponse>>('/leave-types', { params: { size: 50 } })).data.content,
  })
  const clType = leaveTypesQuery.data?.find((lt) => lt.code === 'CL')
  const rhType = leaveTypesQuery.data?.find((lt) => lt.code === 'RH')

  const [mode, setMode] = useState<CombinedMode>('contiguous')
  const [placement, setPlacement] = useState<RhPlacement>('suffix')
  const [clStartDate, setClStartDate] = useState('')
  const [clEndDate, setClEndDate] = useState('')
  const [sameDayDate, setSameDayDate] = useState('')
  const [clSession, setClSession] = useState<LeaveSession>('FIRST_HALF')
  const [rhHolidayId, setRhHolidayId] = useState('')
  const [reason, setReason] = useState('')

  const rhDate =
    mode === 'same-day' ? sameDayDate : placement === 'prefix' ? addDays(clStartDate, -1) : addDays(clEndDate, 1)
  const rhSession: LeaveSession = mode === 'same-day' ? (clSession === 'FIRST_HALF' ? 'SECOND_HALF' : 'FIRST_HALF') : 'FULL_DAY'

  const holidaysQuery = useQuery({
    queryKey: ['holidays-restricted-for-combined'],
    queryFn: async () => (await apiClient.get<Page<HolidayResponse>>('/holidays', { params: { size: 300 } })).data.content,
    enabled: Boolean(rhDate),
  })
  const restrictedHolidayOptions = (holidaysQuery.data ?? []).filter((h) => h.holidayType === 'RESTRICTED')
  const matchingHoliday = restrictedHolidayOptions.find((h) => h.holidayDate === rhDate)

  const combineMutation = useMutation({
    mutationFn: async () => {
      const payload: CombinedLeaveApplicationRequest = {
        employeeId,
        clLeaveTypeId: clType!.id,
        clStartDate: mode === 'same-day' ? sameDayDate : clStartDate,
        clEndDate: mode === 'same-day' ? sameDayDate : clEndDate,
        clSession: mode === 'same-day' ? clSession : 'FULL_DAY',
        rhLeaveTypeId: rhType!.id,
        rhDate,
        rhSession,
        rhHolidayId: Number(rhHolidayId),
        reason,
      }
      return (await apiClient.post<CombinedLeaveApplicationResponse>('/leave-applications/combined', payload)).data
    },
    onSuccess: () => {
      setClStartDate('')
      setClEndDate('')
      setSameDayDate('')
      setRhHolidayId('')
      setReason('')
      onSaved()
    },
  })

  const canSubmit =
    Boolean(clType && rhType) &&
    Boolean(rhHolidayId) &&
    reason.trim().length > 0 &&
    (mode === 'same-day' ? Boolean(sameDayDate) : Boolean(clStartDate) && Boolean(clEndDate)) &&
    !combineMutation.isPending

  const is422 =
    isAxiosError(combineMutation.error) && combineMutation.error.response?.status === 422

  return (
    <Card>
      <h2 className="mb-3 text-sm font-semibold text-slate-700">Combined CL + RH Application</h2>

      <div className="mb-3 flex gap-2">
        {(['contiguous', 'same-day'] as CombinedMode[]).map((m) => (
          <button
            key={m}
            type="button"
            onClick={() => setMode(m)}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              mode === m ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'
            }`}
          >
            {m === 'contiguous' ? 'Contiguous Days (Prefix/Suffix)' : 'Same-Day Split Session'}
          </button>
        ))}
      </div>

      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault()
          combineMutation.mutate()
        }}
      >
        {mode === 'contiguous' ? (
          <>
            <div className="flex gap-2">
              {(['prefix', 'suffix'] as RhPlacement[]).map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => setPlacement(p)}
                  className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                    placement === p ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'
                  }`}
                >
                  {p === 'prefix' ? 'RH before CL' : 'RH after CL'}
                </button>
              ))}
            </div>
            <div className="grid grid-cols-2 gap-3">
              <DateField label="CL From Date" required value={clStartDate} onChange={setClStartDate} />
              <DateField label="CL To Date" required value={clEndDate} onChange={setClEndDate} />
            </div>
            {rhDate && <p className="text-xs text-slate-500">RH will be applied for {rhDate} ({placement === 'prefix' ? 'day before CL' : 'day after CL'}).</p>}
          </>
        ) : (
          <>
            <DateField label="Date" required value={sameDayDate} onChange={setSameDayDate} />
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div className="rounded-md border border-slate-200 p-2 text-center">
                <p className="text-xs text-slate-400">Session 1</p>
                <div className="mt-1 flex justify-center gap-1">
                  {(['FIRST_HALF', 'SECOND_HALF'] as LeaveSession[]).map((s) => (
                    <button
                      key={s}
                      type="button"
                      onClick={() => setClSession(s)}
                      className={`rounded-full px-2 py-0.5 text-xs ${clSession === s ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'}`}
                    >
                      {s === 'FIRST_HALF' ? '1st Half CL' : '2nd Half CL'}
                    </button>
                  ))}
                </div>
              </div>
              <div className="rounded-md border border-slate-200 p-2 text-center">
                <p className="text-xs text-slate-400">Session 2 (RH, opposite half)</p>
                <p className="mt-1.5 text-xs font-medium text-slate-700">{rhSession === 'FIRST_HALF' ? '1st Half RH' : '2nd Half RH'}</p>
              </div>
            </div>
          </>
        )}

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Restricted Holiday Being Observed</label>
          <select
            required
            value={rhHolidayId}
            onChange={(e) => setRhHolidayId(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">Select RH...</option>
            {restrictedHolidayOptions.map((h) => (
              <option key={h.id} value={h.id}>
                {h.name} ({h.holidayDate})
              </option>
            ))}
          </select>
          {rhDate && !matchingHoliday && (
            <p className="mt-1 text-xs text-amber-600">No restricted holiday is calendared on {rhDate} - double check the date.</p>
          )}
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Reason</label>
          <textarea
            required
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={2}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>

        <PrimaryButton type="submit" disabled={!canSubmit} className="w-full justify-center">
          Submit Combined Application
        </PrimaryButton>

        {combineMutation.isError && (
          <ErrorState
            message={
              is422
                ? 'This CL cannot be combined here - it falls contiguous with an existing approved EL/HPL/CCL application.'
                : 'Could not submit the combined application.'
            }
          />
        )}
      </form>
    </Card>
  )
}
