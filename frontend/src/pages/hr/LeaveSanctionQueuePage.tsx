import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { LeaveRoutingModal } from '../../components/leave/LeaveRoutingModal'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../components/common/ui'
import { Modal } from '../../components/common/Modal'
import type { EmployeeResponse, LeaveApplicationResponse, LeaveSanctionHistoryResponse, Page } from '../../types/api'

const STATUS_TONE: Record<string, 'neutral' | 'success' | 'warning' | 'danger'> = {
  DRAFT: 'neutral',
  PENDING_APPROVAL: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  CANCELLED: 'neutral',
}

const WORKFLOW_STAGE_TONE: Record<string, 'neutral' | 'success' | 'warning' | 'danger'> = {
  SUBMITTED: 'warning',
  RECOMMENDED: 'warning',
  SANCTIONED: 'success',
  REJECTED: 'danger',
  CANCELLED: 'neutral',
}

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

type Tab = 'pending' | 'history'

/** Recommend/Forward - reassigns the file to another employee's desk without deciding it (application stays PENDING_APPROVAL). */
function ForwardModal({ application, onClose }: { application: LeaveApplicationResponse; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [employeeId, setEmployeeId] = useState('')
  const [remarks, setRemarks] = useState('')

  const employeesQuery = useQuery({
    queryKey: ['employees-all-for-leave-forward'],
    queryFn: async () => (await apiClient.get<Page<EmployeeResponse>>('/employees', { params: { size: 500 } })).data.content,
  })
  const selected = (employeesQuery.data ?? []).find((e) => String(e.id) === employeeId)
  const matches = (employeesQuery.data ?? []).filter((e) => {
    const q = search.trim().toLowerCase()
    if (!q) return false
    return e.employeeCode.toLowerCase().includes(q) || e.fullName.toLowerCase().includes(q)
  })

  const forward = useMutation({
    mutationFn: async () =>
      (await apiClient.post(`/v1/leaves/${application.id}/forward`, {
        forwardedToEmployeeId: Number(employeeId),
        remarks: remarks.trim() || null,
      })).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['leave-applications-pending'] })
      onClose()
    },
  })

  return (
    <Modal title={`Forward / Recommend - App #${application.id}`} onClose={onClose}>
      <div className="space-y-3">
        <div className="relative">
          <label className="mb-1 block text-xs font-medium text-slate-600">Forward To</label>
          <input
            value={employeeId ? `${selected?.employeeCode} - ${selected?.fullName}` : search}
            onChange={(e) => {
              setSearch(e.target.value)
              setEmployeeId('')
            }}
            placeholder="Search by employee code or name..."
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
          {search && !employeeId && (
            <div className="absolute z-10 mt-1 max-h-48 w-full overflow-y-auto rounded-md border border-slate-200 bg-white shadow-lg">
              {matches.length === 0 && <p className="p-2 text-xs text-slate-400">No matches.</p>}
              {matches.slice(0, 20).map((emp) => (
                <button
                  key={emp.id}
                  type="button"
                  onClick={() => {
                    setEmployeeId(String(emp.id))
                    setSearch('')
                  }}
                  className="block w-full px-3 py-2 text-left text-sm hover:bg-slate-50"
                >
                  <span className="font-medium">{emp.employeeCode}</span> - {emp.fullName}
                  <span className="block text-xs text-slate-400">{emp.designationTitle}</span>
                </button>
              ))}
            </div>
          )}
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Remarks</label>
          <textarea
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            rows={3}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            placeholder="Optional remarks for the next desk..."
          />
        </div>
        {forward.isError && <ErrorState message="Could not forward this application." />}
        <div className="flex justify-end gap-2">
          <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
          <PrimaryButton onClick={() => forward.mutate()} disabled={!employeeId || forward.isPending}>
            Forward
          </PrimaryButton>
        </div>
      </div>
    </Modal>
  )
}

/** Shared by Sanction (remarks optional) and Reject (remarks mandatory, enforced both here and server-side). */
function DecisionModal({
  application,
  action,
  onClose,
}: {
  application: LeaveApplicationResponse
  action: 'sanction' | 'reject'
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [remarks, setRemarks] = useState('')
  const remarksRequired = action === 'reject'

  const decide = useMutation({
    mutationFn: async () =>
      (await apiClient.post(`/v1/leaves/${application.id}/${action}`, { remarks: remarks.trim() || null })).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['leave-applications-pending'] })
      onClose()
    },
  })

  return (
    <Modal title={`${action === 'sanction' ? 'Sanction' : 'Reject'} - App #${application.id}`} onClose={onClose}>
      <div className="space-y-3">
        <p className="text-sm text-slate-600">
          {application.employeeCode} · {application.leaveTypeCode} · {formatDate(application.startDate)} to{' '}
          {formatDate(application.endDate)} ({application.totalDays} days)
        </p>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">
            Remarks {remarksRequired && <span className="text-rose-600">*</span>}
          </label>
          <textarea
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            rows={3}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            placeholder={remarksRequired ? 'Reason for rejection (mandatory)...' : 'Optional remarks...'}
          />
        </div>
        {decide.isError && <ErrorState message={`Could not ${action} this application.`} />}
        <div className="flex justify-end gap-2">
          <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
          <PrimaryButton onClick={() => decide.mutate()} disabled={(remarksRequired && !remarks.trim()) || decide.isPending}>
            {action === 'sanction' ? 'Sanction' : 'Reject'}
          </PrimaryButton>
        </div>
      </div>
    </Modal>
  )
}

function PendingSanctionsTab({ onViewHistory }: { onViewHistory: (id: number) => void }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['leave-applications-pending'],
    queryFn: async () => (await apiClient.get<Page<LeaveApplicationResponse>>('/leave-applications', { params: { size: 50 } })).data,
  })

  const [forwardTarget, setForwardTarget] = useState<LeaveApplicationResponse | null>(null)
  const [decisionTarget, setDecisionTarget] = useState<{ application: LeaveApplicationResponse; action: 'sanction' | 'reject' } | null>(null)

  const pending = (data?.content ?? []).filter((a) => a.status === 'PENDING_APPROVAL')

  return (
    <>
      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load leave applications." />}
      {!isLoading && !isError && pending.length === 0 && <EmptyState message="No applications awaiting sanction." />}

      <div className="space-y-3">
        {pending.map((application) => (
          <Card key={application.id} className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="font-medium text-slate-800">
                #{application.id} · {application.employeeCode} · {application.leaveTypeCode}
              </p>
              <p className="text-sm text-slate-500">
                {formatDate(application.startDate)} to {formatDate(application.endDate)} ({application.totalDays} days) -{' '}
                {application.reason}
              </p>
              {application.currentAssignedToName && (
                <p className="mt-1 text-xs text-slate-400">
                  Currently with {application.currentAssignedToName}
                  {application.currentAssignedToDesignation ? ` (${application.currentAssignedToDesignation})` : ''}
                </p>
              )}
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone={WORKFLOW_STAGE_TONE[application.workflowStage] ?? 'neutral'}>
                {application.workflowStage.replace(/_/g, ' ')}
              </Badge>
              <SecondaryButton onClick={() => onViewHistory(application.id)}>View History</SecondaryButton>
              <SecondaryButton onClick={() => setForwardTarget(application)}>Forward</SecondaryButton>
              <SecondaryButton onClick={() => setDecisionTarget({ application, action: 'reject' })}>Reject</SecondaryButton>
              <PrimaryButton onClick={() => setDecisionTarget({ application, action: 'sanction' })}>Sanction</PrimaryButton>
            </div>
          </Card>
        ))}
      </div>

      {forwardTarget && <ForwardModal application={forwardTarget} onClose={() => setForwardTarget(null)} />}
      {decisionTarget && (
        <DecisionModal application={decisionTarget.application} action={decisionTarget.action} onClose={() => setDecisionTarget(null)} />
      )}
    </>
  )
}

function SanctionHistoryTab({ onViewHistory }: { onViewHistory: (id: number) => void }) {
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState<number | ''>('')
  const [status, setStatus] = useState<'ALL' | 'APPROVED' | 'REJECTED'>('ALL')

  const { data, isLoading, isError } = useQuery({
    queryKey: ['leave-sanctions-history', year, month, status],
    queryFn: async () =>
      (await apiClient.get<LeaveSanctionHistoryResponse[]>('/v1/leaves/sanctions/history', {
        params: { year, month: month || undefined, status },
      })).data,
  })

  return (
    <>
      <Card className="mb-4">
        <div className="flex flex-wrap items-end gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Year</label>
            <input
              type="number"
              value={year}
              onChange={(e) => setYear(Number(e.target.value))}
              className="w-28 rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Month</label>
            <select
              value={month}
              onChange={(e) => setMonth(e.target.value ? Number(e.target.value) : '')}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="">Whole Year</option>
              {MONTH_NAMES.map((name, idx) => (
                <option key={name} value={idx + 1}>
                  {name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Status</label>
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value as typeof status)}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="ALL">All</option>
              <option value="APPROVED">Sanctioned</option>
              <option value="REJECTED">Rejected</option>
            </select>
          </div>
        </div>
      </Card>

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load sanction history." />}
      {!isLoading && !isError && (data ?? []).length === 0 && <EmptyState message="No sanctioned/rejected applications in this period." />}

      {!isLoading && !isError && (data ?? []).length > 0 && (
        <Card className="overflow-x-auto">
          <table className="w-full min-w-[900px] border-collapse text-xs">
            <thead>
              <tr className="border-b border-slate-300 text-left text-slate-500">
                <th className="py-2 pr-3">App ID</th>
                <th className="py-2 pr-3">Employee</th>
                <th className="py-2 pr-3">Leave Type</th>
                <th className="py-2 pr-3">Dates</th>
                <th className="py-2 pr-3 text-right">Days</th>
                <th className="py-2 pr-3">Forwarded By</th>
                <th className="py-2 pr-3">Sanctioned By</th>
                <th className="py-2 pr-3">Sanctioned Date</th>
                <th className="py-2 pr-3">Status</th>
                <th className="py-2 pl-3" />
              </tr>
            </thead>
            <tbody>
              {(data ?? []).map((row) => (
                <tr key={row.id} className="border-b border-slate-100">
                  <td className="py-1.5 pr-3 tabular-nums">#{row.id}</td>
                  <td className="py-1.5 pr-3">
                    {row.employeeCode} - {row.employeeName}
                  </td>
                  <td className="py-1.5 pr-3">{row.leaveTypeCode}</td>
                  <td className="py-1.5 pr-3 tabular-nums">
                    {formatDate(row.startDate)} to {formatDate(row.endDate)}
                  </td>
                  <td className="py-1.5 pr-3 text-right tabular-nums">{row.totalDays}</td>
                  <td className="py-1.5 pr-3">{row.forwardedByName ?? '--'}</td>
                  <td className="py-1.5 pr-3">{row.sanctionedByName ?? '--'}</td>
                  <td className="py-1.5 pr-3 tabular-nums">{formatDate(row.decidedAt)}</td>
                  <td className="py-1.5 pr-3">
                    <Badge tone={STATUS_TONE[row.status] ?? 'neutral'}>{row.status.replace(/_/g, ' ')}</Badge>
                  </td>
                  <td className="py-1.5 pl-3">
                    <SecondaryButton onClick={() => onViewHistory(row.id)}>View Audit Trail</SecondaryButton>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </>
  )
}

export function LeaveSanctionQueuePage() {
  const [tab, setTab] = useState<Tab>('pending')
  const [routingModalId, setRoutingModalId] = useState<number | null>(null)

  return (
    <div>
      <PageHeader title="Leave Sanctions" description="Forward, sanction, or reject leave applications, and review the sanction history register" />

      <div className="mb-4 flex gap-2">
        {(['pending', 'history'] as Tab[]).map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => setTab(t)}
            className={`rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${
              tab === t ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'
            }`}
          >
            {t === 'pending' ? 'Pending Sanctions' : 'Sanction History'}
          </button>
        ))}
      </div>

      {tab === 'pending' ? (
        <PendingSanctionsTab onViewHistory={setRoutingModalId} />
      ) : (
        <SanctionHistoryTab onViewHistory={setRoutingModalId} />
      )}

      {routingModalId !== null && <LeaveRoutingModal applicationId={routingModalId} onClose={() => setRoutingModalId(null)} />}
    </div>
  )
}
