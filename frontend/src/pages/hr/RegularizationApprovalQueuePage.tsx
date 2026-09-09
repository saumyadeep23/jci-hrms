import { useMemo, useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Search, XCircle } from 'lucide-react'
import { apiClient } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/common/ToastProvider'
import { formatDate } from '../../lib/date'
import { describeApiError } from '../../lib/apiError'
import { Modal } from '../../components/common/Modal'
import { DatePicker } from '../../components/common/DatePicker'
import { ApproverName } from '../../components/attendance/MyRegularizationRequestsModal'
import { Badge, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../components/common/ui'
import type { ApprovalStatus, AttendanceRegularizationResponse, RegularizationDecisionRequest } from '../../types/api'

const REASON_LABELS: Record<string, string> = {
  FORGOT_PUNCH: 'Forgot to Punch',
  DEVICE_FAILURE: 'Device / App Failure',
  FIELD_DUTY: 'Local Official / Field Duty',
  GEOFENCE_ISSUE: 'Geofence Issue',
  SYSTEM_ERROR: 'System Error',
  OTHER: 'Other',
}

const STATUS_TABS: { value: ApprovalStatus; label: string }[] = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
]

const MONTH_ABBR = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

function isThisMonth(isoDateTime: string): boolean {
  const now = new Date()
  const d = new Date(isoDateTime)
  return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth()
}

/** Compact "03 Sep, 09:52 AM" form for the grey Requested/Actual time boxes - formatDate's own token set has no named-month token, so this stays a small local helper rather than growing that app-wide utility's contract for one card. */
function formatShortDateTime(value: string | null): string {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '--'
  const day = String(date.getDate()).padStart(2, '0')
  const month = MONTH_ABBR[date.getMonth()]
  const hours24 = date.getHours()
  const hours12 = hours24 % 12 === 0 ? 12 : hours24 % 12
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const meridiem = hours24 < 12 ? 'AM' : 'PM'
  return `${day} ${month}, ${String(hours12).padStart(2, '0')}:${minutes} ${meridiem}`
}

/** Total span of the requested (corrected) in/out block, e.g. "8h 45m". "--" for a not-yet-meaningful or inverted range rather than a negative/garbage duration. */
function formatDuration(startIso: string, endIso: string): string {
  const start = new Date(startIso).getTime()
  const end = new Date(endIso).getTime()
  if (Number.isNaN(start) || Number.isNaN(end) || end <= start) return '--'
  const totalMinutes = Math.round((end - start) / 60000)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  return `${hours}h ${minutes}m`
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  return (parts[0][0] + (parts[1]?.[0] ?? '')).toUpperCase()
}

function Avatar({ name }: { name: string }) {
  return (
    <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand-forest/10 text-[11px] font-semibold text-brand-forest">
      {initials(name)}
    </span>
  )
}

/** Tighter-padded stand-in for the shared Card (p-4 sm:p-6) - appending an override className to Card wouldn't reliably beat its own p-4/sm:p-6 in the compiled stylesheet, so this is a plain equivalent with the padding this page actually wants. */
function TightCard({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`rounded-xl border border-slate-200 bg-white p-3 shadow-sm ${className}`}>{children}</div>
}

/**
 * P0 safety rail: a rejection must never go through without a reason - the employee is the one who
 * eats the consequence (their original LATE/ABSENT penalty stands), so a blank "Reject" click here
 * would silently deny them the chance to know why. Enforced client-side with `required` (the button
 * stays disabled below minLength) and the field itself is the same approverRemarks the backend already
 * persists onto the response - no new field, just a mandatory gate on the existing one.
 */
function RejectModal({
  request,
  onClose,
  onConfirm,
  isPending,
}: {
  request: AttendanceRegularizationResponse
  onClose: () => void
  onConfirm: (remarks: string) => void
  isPending: boolean
}) {
  const [remarks, setRemarks] = useState('')
  const trimmed = remarks.trim()

  return (
    <Modal title={`Reject Regularization - ${request.employeeCode}`} onClose={onClose}>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (trimmed.length === 0) return
          onConfirm(trimmed)
        }}
        className="space-y-3"
      >
        <p className="text-sm text-slate-600">
          {formatDate(request.attendanceDate)} · {REASON_LABELS[request.reasonCode] ?? request.reasonCode}
        </p>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">
            Rejection Reason <span className="text-red-500">*</span>
          </label>
          <textarea
            required
            autoFocus
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            rows={3}
            placeholder="Explain why this request is being rejected - the employee will see this."
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
          {remarks.length > 0 && trimmed.length === 0 && <p className="mt-1 text-xs text-red-600">A rejection reason is required.</p>}
        </div>
        <div className="flex gap-2">
          <SecondaryButton type="button" onClick={onClose} className="flex-1 justify-center">
            Cancel
          </SecondaryButton>
          <PrimaryButton type="submit" disabled={isPending || trimmed.length === 0} className="flex-1 justify-center bg-red-600 hover:bg-red-700">
            <XCircle size={14} /> Confirm Rejection
          </PrimaryButton>
        </div>
      </form>
    </Modal>
  )
}

/**
 * ALMS Phase 3, Section 4 - the HoD queue.
 *
 * HR_ADMIN/SUPER_ADMIN see every request organization-wide (GET .../all) since acting on their own
 * designated queue alone hides everyone else's - but they can only Approve/Reject a row where they are
 * themselves the designatedApproverId; every other row renders read-only with the real approver's name,
 * matching AttendanceRegularizationService.approve()'s server-side check (no role-based override there).
 */
export function RegularizationApprovalQueuePage() {
  const { show } = useToast()
  const { employeeId, hasRole } = useAuth()
  const queryClient = useQueryClient()
  const [remarksById, setRemarksById] = useState<Record<number, string>>({})
  const [statusFilter, setStatusFilter] = useState<ApprovalStatus>('PENDING')
  const [rejecting, setRejecting] = useState<AttendanceRegularizationResponse | null>(null)
  const [search, setSearch] = useState('')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')

  const isAdmin = hasRole('HR_ADMIN', 'SUPER_ADMIN')
  const QUERY_KEY = ['attendance-regularization-queue', isAdmin ? 'all' : 'mine-as-approver']

  const { data, isLoading, isError } = useQuery({
    queryKey: QUERY_KEY,
    queryFn: async () =>
      (
        await apiClient.get<AttendanceRegularizationResponse[]>(
          isAdmin ? '/v1/attendance/regularization/all' : '/v1/attendance/regularization/mine-as-approver',
        )
      ).data,
  })

  const metrics = useMemo(() => {
    const all = data ?? []
    return {
      pending: all.filter((r) => r.approvalStatus === 'PENDING').length,
      approvedThisMonth: all.filter((r) => r.approvalStatus === 'APPROVED' && r.approvedAt && isThisMonth(r.approvedAt)).length,
      rejectedThisMonth: all.filter((r) => r.approvalStatus === 'REJECTED' && r.approvedAt && isThisMonth(r.approvedAt)).length,
    }
  }, [data])

  const filtered = (data ?? [])
    .filter((r) => r.approvalStatus === statusFilter)
    .filter((r) => {
      const q = search.trim().toLowerCase()
      if (!q) return true
      return r.employeeName.toLowerCase().includes(q) || r.employeeCode.toLowerCase().includes(q)
    })
    .filter((r) => (!dateFrom || r.attendanceDate >= dateFrom) && (!dateTo || r.attendanceDate <= dateTo))
    .sort((a, b) => b.attendanceDate.localeCompare(a.attendanceDate))

  const hasActiveFilters = search.trim().length > 0 || dateFrom.length > 0 || dateTo.length > 0

  const decideMutation = useMutation({
    mutationFn: async ({ id, approve, remarks }: { id: number; approve: boolean; remarks: string | null }) => {
      const payload: RegularizationDecisionRequest = { approve, remarks }
      return (await apiClient.patch<AttendanceRegularizationResponse>(`/v1/attendance/regularization/${id}/approve`, payload)).data
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEY })
      show({
        tone: 'success',
        message: variables.approve
          ? 'Regularization approved. If a 0.5-day penalty was previously auto-debited for this day, it has been refunded automatically.'
          : 'Regularization rejected.',
      })
      setRejecting(null)
    },
    onError: (error) => {
      show({ tone: 'error', message: describeApiError(error, 'Could not record the decision.') })
    },
  })

  return (
    <div>
      <PageHeader
        title="Regularization Approval Queue"
        description={
          isAdmin
            ? 'Organization-wide view (HR_ADMIN/SUPER_ADMIN). You can only act on requests where you are the designated approver - others render read-only below.'
            : 'Review pending attendance regularization requests from your team'
        }
      />

      <div className="mb-3 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <TightCard>
          <p className="text-xs text-slate-400">Pending Approvals</p>
          <p className="mt-1 text-2xl font-semibold text-slate-800">{metrics.pending}</p>
        </TightCard>
        <TightCard>
          <p className="text-xs text-slate-400">Approved This Month</p>
          <p className="mt-1 text-2xl font-semibold text-slate-800">{metrics.approvedThisMonth}</p>
        </TightCard>
        <TightCard>
          <p className="text-xs text-slate-400">Rejected This Month</p>
          <p className="mt-1 text-2xl font-semibold text-slate-800">{metrics.rejectedThisMonth}</p>
        </TightCard>
      </div>

      <div className="mb-3 flex flex-wrap items-end gap-3">
        <div className="relative">
          <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by Name or ID..."
            className="w-64 rounded-md border border-slate-300 py-2 pl-9 pr-3 text-sm"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-500">From</label>
          <DatePicker value={dateFrom} onChange={setDateFrom} className="w-36" />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-500">To</label>
          <DatePicker value={dateTo} onChange={setDateTo} min={dateFrom || undefined} className="w-36" />
        </div>
        {hasActiveFilters && (
          <button
            type="button"
            onClick={() => {
              setSearch('')
              setDateFrom('')
              setDateTo('')
            }}
            className="pb-2 text-xs font-medium text-slate-500 hover:text-slate-700 hover:underline"
          >
            Clear filters
          </button>
        )}
      </div>

      <div className="mb-3 flex gap-2">
        {STATUS_TABS.map((tab) => (
          <button
            key={tab.value}
            type="button"
            onClick={() => setStatusFilter(tab.value)}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${
              statusFilter === tab.value ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            {tab.label}
            {tab.value === 'PENDING' && metrics.pending > 0 && (
              <span className="ml-1.5 rounded-full bg-white/20 px-1.5 text-xs">{metrics.pending}</span>
            )}
          </button>
        ))}
      </div>

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load the approval queue." />}
      {data && filtered.length === 0 && (
        <EmptyState
          message={
            hasActiveFilters
              ? 'No requests match the current search/date filters.'
              : statusFilter === 'PENDING'
                ? 'No pending regularization requests.'
                : `No ${statusFilter.toLowerCase()} requests.`
          }
        />
      )}

      <div className="space-y-2">
        {filtered.map((req) => {
          const canAct = req.designatedApproverId !== null && req.designatedApproverId === employeeId
          return (
            <TightCard key={req.id}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex items-start gap-2">
                  <Avatar name={req.employeeName} />
                  <div>
                    <p className="font-medium text-slate-800">
                      {req.employeeName} <span className="font-normal text-slate-500">({req.employeeCode})</span> •{' '}
                      {formatDate(req.attendanceDate)}
                    </p>
                    <div className="mt-1 flex flex-wrap items-center gap-2">
                      <Badge tone="warning">{REASON_LABELS[req.reasonCode] ?? req.reasonCode}</Badge>
                      {isAdmin && (
                        <span className="text-xs text-slate-400">
                          Approver: {req.designatedApproverId !== null ? <ApproverName approverId={req.designatedApproverId} /> : 'Unassigned'}
                          {canAct && <span className="ml-1 font-medium text-brand-forest">(you)</span>}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
                <div className="rounded-md bg-slate-50 p-2 text-xs">
                  <div className="grid grid-cols-2 gap-x-4 gap-y-1">
                    <p className="font-semibold text-slate-500">Requested</p>
                    <p className="font-semibold text-slate-500">Actual</p>
                    <div>
                      <p className="text-slate-400">In</p>
                      <p className="font-medium text-slate-700">{formatShortDateTime(req.correctedInTime)}</p>
                    </div>
                    <div>
                      <p className="text-slate-400">In</p>
                      <p className="font-medium text-slate-700">{formatShortDateTime(req.actualInTime)}</p>
                    </div>
                    <div>
                      <p className="text-slate-400">Out</p>
                      <p className="font-medium text-slate-700">{formatShortDateTime(req.correctedOutTime)}</p>
                    </div>
                    <div>
                      <p className="text-slate-400">Out</p>
                      <p className="font-medium text-slate-700">{formatShortDateTime(req.actualOutTime)}</p>
                    </div>
                  </div>
                  <p className="mt-1.5 text-right font-medium text-slate-500">
                    Total: <span className="text-slate-700">{formatDuration(req.correctedInTime, req.correctedOutTime)}</span>
                  </p>
                </div>
              </div>
              {req.remarks && (
                <p className="mt-2 text-sm text-slate-600">
                  <span className="font-semibold text-slate-700">Employee Note: </span>
                  {req.remarks}
                </p>
              )}

              {req.approvalStatus === 'PENDING' ? (
                canAct ? (
                  <>
                    <div className="mt-3">
                      <label className="mb-1 block text-xs font-medium text-slate-600">Approver Remarks (optional on approval)</label>
                      <textarea
                        value={remarksById[req.id] ?? ''}
                        onChange={(e) => setRemarksById((prev) => ({ ...prev, [req.id]: e.target.value }))}
                        rows={2}
                        className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                      />
                    </div>

                    <div className="mt-3 flex justify-end gap-2">
                      <SecondaryButton
                        onClick={() => setRejecting(req)}
                        disabled={decideMutation.isPending}
                        className="border-red-300 text-red-600 hover:bg-red-50"
                      >
                        <XCircle size={14} /> Reject
                      </SecondaryButton>
                      <PrimaryButton
                        onClick={() => decideMutation.mutate({ id: req.id, approve: true, remarks: remarksById[req.id] ?? null })}
                        disabled={decideMutation.isPending}
                      >
                        <CheckCircle2 size={14} /> Approve
                      </PrimaryButton>
                    </div>
                  </>
                ) : (
                  <p className="mt-3 text-xs italic text-slate-400">
                    View only - only the designated approver can act on this request.
                  </p>
                )
              ) : (
                req.approverRemarks && (
                  <p className="mt-2 text-xs text-slate-600">
                    <span className="font-medium text-slate-500">Approver Remarks: </span>
                    {req.approverRemarks}
                  </p>
                )
              )}
            </TightCard>
          )
        })}
      </div>

      {rejecting && (
        <RejectModal
          request={rejecting}
          onClose={() => setRejecting(null)}
          isPending={decideMutation.isPending}
          onConfirm={(remarks) => decideMutation.mutate({ id: rejecting.id, approve: false, remarks })}
        />
      )}
    </div>
  )
}
