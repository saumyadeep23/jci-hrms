import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { useDebouncedValue } from '../../lib/useDebouncedValue'
import { DateField } from '../common/DateField'
import { Badge, Card, ErrorState, PrimaryButton, SecondaryButton } from '../common/ui'
import type {
  LeaveApplicationPreviewRequest,
  LeaveApplicationPreviewResponse,
  LeaveApplicationRequest,
  LeaveApplicationResponse,
  LeaveBalanceResponse,
  LeaveEntitlementBalanceResponse,
  LeaveSession,
  LeaveTypeResponse,
  Page,
} from '../../types/api'

const EL_CODE = 'EL'

const HALF_DAY_SESSIONS: { value: LeaveSession; label: string }[] = [
  { value: 'FULL_DAY', label: 'Full Day' },
  { value: 'FIRST_HALF', label: '1st Half' },
  { value: 'SECOND_HALF', label: '2nd Half' },
]

/** EL/HPL only need a supporting document past this many debitable days (medical-grounds threshold). */
const MEDICAL_ATTACHMENT_THRESHOLD_DAYS = 3

function attachmentRequiredFor(leaveType: LeaveTypeResponse | undefined, debitableDays: number | null): boolean {
  if (!leaveType) return false
  if (leaveType.code === 'COMMUTED' || leaveType.code === 'MATERNITY' || leaveType.code === 'PATERNITY') return true
  if (leaveType.code === 'EL' || leaveType.code === 'HPL') {
    return (debitableDays ?? 0) > MEDICAL_ATTACHMENT_THRESHOLD_DAYS
  }
  return false
}

/**
 * Save Draft / Submit Application lifecycle (SRS v6.1 item 5), shared by
 * both the "New Leave Application" card (mode="create") and the "Look Up
 * Application" panel's DRAFT-edit view (mode="edit") in LeavePage.tsx.
 *
 * - Save Draft never touches the balance-reservation path (create()/update()
 *   are documented on the backend as having no balance impact).
 * - Submit Application is balance-gated (SRS item 4): it reserves balance
 *   server-side (POST .../submit), so it's disabled client-side whenever
 *   the previewed debitableDays exceeds the selected leave type's current
 *   balance, mirroring the backend's own check (which now returns 422 via
 *   InsufficientLeaveBalanceException if this client-side gate is ever
 *   bypassed).
 */
export function LeaveApplicationForm({
  employeeId,
  mode,
  application,
  onSaved,
}: {
  employeeId: number
  mode: 'create' | 'edit'
  /** Required for mode="edit" - the caller guarantees application.status === 'DRAFT'. */
  application?: LeaveApplicationResponse
  onSaved: (application: LeaveApplicationResponse) => void
}) {
  const queryClient = useQueryClient()

  const leaveTypesQuery = useQuery({
    queryKey: ['leave-types'],
    queryFn: async () => (await apiClient.get<Page<LeaveTypeResponse>>('/leave-types', { params: { size: 50 } })).data.content,
  })
  const activeLeaveTypes = leaveTypesQuery.data?.filter((lt) => lt.active) ?? []

  const [leaveTypeId, setLeaveTypeId] = useState(application ? String(application.leaveTypeId) : '')
  const [startDate, setStartDate] = useState(application?.startDate ?? '')
  const [endDate, setEndDate] = useState(application?.endDate ?? '')
  const [leaveSession, setLeaveSession] = useState<LeaveSession>(application?.leaveSession ?? 'FULL_DAY')
  const [reason, setReason] = useState(application?.reason ?? '')
  const [attachment, setAttachment] = useState<File | null>(null)

  const selectedLeaveType = activeLeaveTypes.find((lt) => String(lt.id) === leaveTypeId)
  const isCl = selectedLeaveType?.code === 'CL'
  const isEl = selectedLeaveType?.code === EL_CODE
  const effectiveLeaveSession: LeaveSession = isCl ? leaveSession : 'FULL_DAY'
  const effectiveEndDate = effectiveLeaveSession === 'FULL_DAY' ? endDate : startDate

  const balanceYear = startDate ? new Date(startDate).getFullYear() : new Date().getFullYear()
  const balancesQuery = useQuery({
    queryKey: ['leave-balances-mine', balanceYear],
    queryFn: async () => (await apiClient.get<LeaveBalanceResponse[]>('/leave-balances/mine', { params: { year: balanceYear } })).data,
    enabled: !isEl,
  })
  const selectedBalance = balancesQuery.data?.find((b) => String(b.leaveTypeId) === leaveTypeId)

  // EL's "current balance" is NOT the generic leave_balances row (that ledger
  // is never debited/credited for EL at all - see LeaveApplicationService,
  // which only touches it for non-EL types - so it holds a stale, unrelated
  // number for EL). The real EL balance, split into encashable (preserved,
  // never usable for taking physical leave) and enjoyable (debited first for
  // physical leave - see LeaveBalanceSplitCard's own note), lives in the
  // leave_entitlement_balance sub-ledger instead.
  const elEntitlementQuery = useQuery({
    queryKey: ['leave-entitlement-balance-mine', balanceYear],
    queryFn: async () =>
      (await apiClient.get<LeaveEntitlementBalanceResponse[]>('/v1/leave-entitlement-balance/mine', { params: { year: balanceYear } })).data,
    enabled: isEl,
  })
  const selectedElBalance = elEntitlementQuery.data?.find((b) => String(b.leaveTypeId) === leaveTypeId)
  // Computed from the split rather than trusted from selectedElBalance.availableBalance directly -
  // see LeaveBalanceSplitCard's identical reasoning.
  const selectedElTotalAvailable = selectedElBalance
    ? Number(selectedElBalance.encashableAvailable || 0) + Number(selectedElBalance.enjoyableAvailable || 0)
    : undefined
  const balanceLoading = isEl ? elEntitlementQuery.isLoading : balancesQuery.isLoading
  const effectiveAvailableDays = isEl ? selectedElBalance?.enjoyableAvailable : selectedBalance?.availableDays

  const debounced = useDebouncedValue({ leaveTypeId, startDate, endDate: effectiveEndDate, leaveSession: effectiveLeaveSession }, 400)
  const previewReady = Boolean(debounced.leaveTypeId && debounced.startDate && debounced.endDate)
  const isStale =
    debounced.leaveTypeId !== leaveTypeId ||
    debounced.startDate !== startDate ||
    debounced.endDate !== effectiveEndDate ||
    debounced.leaveSession !== effectiveLeaveSession

  const previewQuery = useQuery({
    queryKey: ['leave-application-preview', employeeId, debounced.leaveTypeId, debounced.startDate, debounced.endDate, debounced.leaveSession],
    queryFn: async () => {
      const payload: LeaveApplicationPreviewRequest = {
        employeeId,
        leaveTypeId: Number(debounced.leaveTypeId),
        startDate: debounced.startDate,
        endDate: debounced.endDate,
        leaveSession: debounced.leaveSession,
      }
      return (await apiClient.post<LeaveApplicationPreviewResponse>('/leave-applications/preview', payload)).data
    },
    enabled: previewReady,
  })

  const debitableDays = previewQuery.data?.debitableDays ?? null
  const attachmentRequired = attachmentRequiredFor(selectedLeaveType, debitableDays)
  const previewValid = previewReady && !previewQuery.isFetching && !isStale && previewQuery.data?.valid === true
  const overBalance =
    previewValid && debitableDays !== null && effectiveAvailableDays !== undefined && debitableDays > effectiveAvailableDays

  function buildPayload(): LeaveApplicationRequest {
    return {
      employeeId,
      leaveTypeId: Number(leaveTypeId),
      startDate,
      endDate: effectiveEndDate,
      // The server-confirmed figure from the preview - guaranteed to pass re-validation.
      totalDays: previewQuery.data!.debitableDays!,
      reason,
      leaveSession: effectiveLeaveSession,
    }
  }

  function resetForm() {
    setLeaveTypeId('')
    setStartDate('')
    setEndDate('')
    setLeaveSession('FULL_DAY')
    setReason('')
    setAttachment(null)
  }

  function afterSave(saved: LeaveApplicationResponse) {
    queryClient.invalidateQueries({ queryKey: ['leave-balances-mine'] })
    if (mode === 'create') resetForm()
    onSaved(saved)
  }

  const saveDraftMutation = useMutation({
    mutationFn: async () => {
      const payload = buildPayload()
      if (mode === 'edit' && application) {
        return (await apiClient.put<LeaveApplicationResponse>(`/leave-applications/${application.id}`, payload)).data
      }
      return (await apiClient.post<LeaveApplicationResponse>('/leave-applications', payload)).data
    },
    onSuccess: afterSave,
  })

  const submitMutation = useMutation({
    mutationFn: async () => {
      const payload = buildPayload()
      const id =
        mode === 'edit' && application
          ? (await apiClient.put<LeaveApplicationResponse>(`/leave-applications/${application.id}`, payload)).data.id
          : (await apiClient.post<LeaveApplicationResponse>('/leave-applications', payload)).data.id
      return (await apiClient.post<LeaveApplicationResponse>(`/leave-applications/${id}/submit`)).data
    },
    onSuccess: afterSave,
  })

  const cancelMutation = useMutation({
    mutationFn: async () => (await apiClient.post<LeaveApplicationResponse>(`/leave-applications/${application!.id}/cancel`)).data,
    onSuccess: afterSave,
  })

  const canSave = previewReady && !isStale && !previewQuery.isFetching && previewQuery.data?.valid === true && reason.trim().length > 0
  const canSubmit = canSave && !overBalance && !submitMutation.isPending

  return (
    <Card>
      <h2 className="mb-3 text-sm font-semibold text-slate-700">
        {mode === 'edit' ? `Edit Draft Application #${application?.id}` : 'New Leave Application'}
      </h2>
      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault()
          saveDraftMutation.mutate()
        }}
      >
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Leave Type</label>
          <select
            required
            value={leaveTypeId}
            onChange={(e) => setLeaveTypeId(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">Select leave type...</option>
            {activeLeaveTypes
              .filter((lt) => lt.code !== 'LWP') // LWP is a system-generated marker, not something an employee applies for
              .map((lt) => (
                <option key={lt.id} value={lt.id}>
                  {lt.code} - {lt.name}
                </option>
              ))}
          </select>
          {leaveTypeId && (
            <div className="mt-1.5">
              {balanceLoading ? (
                <span className="text-xs text-slate-400">Checking balance...</span>
              ) : isEl && selectedElBalance ? (
                <Badge tone={selectedElBalance.enjoyableAvailable > 0 ? 'success' : 'danger'}>
                  Current Balance: {selectedElBalance.enjoyableAvailable} Days (Enjoyable) / {selectedElTotalAvailable} Days (Total)
                </Badge>
              ) : !isEl && selectedBalance ? (
                <Badge tone={selectedBalance.availableDays > 0 ? 'success' : 'danger'}>
                  Current Balance: {selectedBalance.availableDays} Days
                </Badge>
              ) : (
                <Badge tone="neutral">No balance on record for {balanceYear}</Badge>
              )}
            </div>
          )}
        </div>

        {isCl && (
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Session</label>
            <div className="flex gap-2">
              {HALF_DAY_SESSIONS.map((session) => (
                <button
                  key={session.value}
                  type="button"
                  onClick={() => setLeaveSession(session.value)}
                  className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                    leaveSession === session.value ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'
                  }`}
                >
                  {session.label}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="grid grid-cols-2 gap-3">
          <DateField
            label="From Date"
            required
            value={startDate}
            onChange={(value) => {
              setStartDate(value)
              if (isCl) setEndDate(value) // a half-day CL is always a single day
            }}
          />
          <DateField
            label="To Date"
            required
            value={effectiveEndDate}
            disabled={effectiveLeaveSession !== 'FULL_DAY'}
            onChange={setEndDate}
          />
        </div>

        {previewReady && (
          <div className="rounded-md bg-slate-50 p-3 text-sm">
            {previewQuery.isFetching || isStale ? (
              <p className="text-xs text-slate-400">Checking CCS rules...</p>
            ) : previewQuery.data ? (
              <div className="flex items-center justify-between">
                <span className="text-slate-500">
                  Total Calendar Days: <span className="font-medium text-slate-700">{previewQuery.data.calendarDays}</span>
                </span>
                <span className="text-slate-500">
                  Total Debitable Days: <span className="font-medium text-slate-700">{previewQuery.data.debitableDays ?? '—'}</span>
                </span>
              </div>
            ) : null}
          </div>
        )}

        {previewReady && !previewQuery.isFetching && !isStale && previewQuery.data?.valid === false && (
          <ErrorState message={previewQuery.data.message ?? 'This application violates a CCS leave rule.'} />
        )}

        {overBalance && effectiveAvailableDays !== undefined && debitableDays !== null && (
          <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-xs font-medium text-red-700">
            Insufficient leave balance: Available {effectiveAvailableDays} days, Requested {debitableDays} days
          </div>
        )}

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Reason</label>
          <textarea
            required
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            rows={2}
          />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">
            Supporting Attachment {attachmentRequired && <span className="text-red-600">*</span>}
          </label>
          <input
            type="file"
            onChange={(e) => setAttachment(e.target.files?.[0] ?? null)}
            className="w-full rounded-md border border-slate-300 px-3 py-1.5 text-xs file:mr-2 file:rounded file:border-0 file:bg-slate-100 file:px-2 file:py-1 file:text-xs"
          />
          <p className="mt-1 text-[11px] text-slate-400">
            {attachment && `Selected: ${attachment.name} · `}
            {attachmentRequired
              ? 'Required for this leave type/duration, but there is no document-storage endpoint in the backend yet - the selected file is not actually uploaded anywhere.'
              : 'Optional.'}
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <SecondaryButton type="submit" disabled={!canSave || saveDraftMutation.isPending} className="flex-1 justify-center">
            Save Draft
          </SecondaryButton>
          <PrimaryButton disabled={!canSubmit} onClick={() => submitMutation.mutate()} className="flex-1 justify-center">
            Submit Application
          </PrimaryButton>
          {mode === 'edit' && (
            <SecondaryButton type="button" onClick={() => cancelMutation.mutate()} disabled={cancelMutation.isPending}>
              Cancel
            </SecondaryButton>
          )}
        </div>
        {(saveDraftMutation.isError || submitMutation.isError) && (
          <ErrorState message="Could not save the leave application." />
        )}
      </form>
    </Card>
  )
}
