import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { describeApiError } from '../../../lib/apiError'
import { formatDate } from '../../../lib/date'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../../components/common/ui'
import { Modal } from '../../../components/common/Modal'
import type {
  CpfDisputeAdminResponse,
  CpfDisputeCategory,
  CpfDisputeHistoryEntryResponse,
  CpfDisputeStatus,
  CpfDisputeSummaryResponse,
  Page,
} from '../../../types/api'

const STATUS_OPTIONS: CpfDisputeStatus[] = ['OPEN', 'UNDER_REVIEW', 'CLARIFICATION_REQUIRED', 'RESOLVED', 'REJECTED', 'WITHDRAWN']
const CATEGORY_OPTIONS: CpfDisputeCategory[] = [
  'EMPLOYEE_CONTRIBUTION', 'EMPLOYER_CONTRIBUTION', 'EPS_CONTRIBUTION', 'VPF_CONTRIBUTION', 'INTEREST',
  'LOAN_SANCTION', 'LOAN_REPAYMENT', 'WITHDRAWAL', 'TRANSACTION_MISSING', 'BALANCE', 'TRANSACTION_DATE', 'OTHER',
]

function statusTone(status: CpfDisputeStatus): 'neutral' | 'success' | 'warning' | 'danger' | 'brand' {
  switch (status) {
    case 'RESOLVED':
      return 'success'
    case 'REJECTED':
      return 'danger'
    case 'WITHDRAWN':
      return 'neutral'
    default:
      return 'warning'
  }
}

/**
 * Route: /admin/cpf/disputes - Part 12 CPF/HR reviewer console. This app has no separate "CPF/HR Reviewer"
 * role distinct from CPF_ADMIN (matching CpfDisputeAdminController's own @PreAuthorize), so the same
 * CPF_TRUST_ROLES gate used by every other CPF Trust admin page is reused here. No action on this page ever
 * touches the underlying CPF ledger - resolving/rejecting a dispute only records a decision; a real
 * correction goes through the existing authorized CPF correction workflow elsewhere.
 */
export function CpfDisputeAdminPage() {
  const [filters, setFilters] = useState<{ disputeNumber: string; category: CpfDisputeCategory | ''; status: CpfDisputeStatus | '' }>({
    disputeNumber: '',
    category: '',
    status: '',
  })
  const [page, setPage] = useState(0)
  const [selectedId, setSelectedId] = useState<number | null>(null)

  const dashboardQuery = useQuery({
    queryKey: ['cpf-dispute-dashboard-counts'],
    queryFn: async () => (await apiClient.get<Record<CpfDisputeStatus, number>>('/v1/payroll/trust/cpf/disputes/dashboard-counts')).data,
  })

  const listQuery = useQuery({
    queryKey: ['cpf-disputes-admin', filters, page],
    queryFn: async () =>
      (
        await apiClient.get<Page<CpfDisputeSummaryResponse>>('/v1/payroll/trust/cpf/disputes', {
          params: {
            disputeNumber: filters.disputeNumber || undefined,
            category: filters.category || undefined,
            status: filters.status || undefined,
            page,
            size: 20,
          },
        })
      ).data,
    placeholderData: (previous) => previous,
  })

  function updateFilters(next: typeof filters) {
    setFilters(next)
    setPage(0)
  }

  return (
    <div>
      <PageHeader title="CPF Transaction Disputes" description="Review and resolve disputes employees raise against posted CPF Trust ledger transactions." />

      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        {STATUS_OPTIONS.map((status) => (
          <Card key={status} className="p-3">
            <div className="text-[11px] text-slate-400">{status.replaceAll('_', ' ')}</div>
            <div className="mt-1 text-xl font-semibold text-slate-800">{dashboardQuery.data?.[status] ?? '-'}</div>
          </Card>
        ))}
      </div>

      <Card className="mb-4">
        <div className="flex flex-wrap items-end gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Dispute Number</label>
            <input
              value={filters.disputeNumber}
              onChange={(e) => updateFilters({ ...filters, disputeNumber: e.target.value })}
              placeholder="CPF-DSP-..."
              className="w-44 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Category</label>
            <select
              value={filters.category}
              onChange={(e) => updateFilters({ ...filters, category: e.target.value as CpfDisputeCategory | '' })}
              className="w-48 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none"
            >
              <option value="">All categories</option>
              {CATEGORY_OPTIONS.map((c) => (
                <option key={c} value={c}>
                  {c.replaceAll('_', ' ')}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Status</label>
            <select
              value={filters.status}
              onChange={(e) => updateFilters({ ...filters, status: e.target.value as CpfDisputeStatus | '' })}
              className="w-44 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none"
            >
              <option value="">All statuses</option>
              {STATUS_OPTIONS.map((s) => (
                <option key={s} value={s}>
                  {s.replaceAll('_', ' ')}
                </option>
              ))}
            </select>
          </div>
        </div>
      </Card>

      <Card>
        {listQuery.isLoading && <LoadingState label="Loading disputes..." />}
        {listQuery.isError && <ErrorState message="Could not load the dispute queue." />}
        {listQuery.data && listQuery.data.content.length === 0 && <EmptyState message="No disputes match these filters." />}
        {listQuery.data && listQuery.data.content.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-left text-[11px] uppercase tracking-wide text-slate-400">
                  <th className="py-2 pr-3">Dispute #</th>
                  <th className="py-2 pr-3">Employee</th>
                  <th className="py-2 pr-3">Category</th>
                  <th className="py-2 pr-3">Raised</th>
                  <th className="py-2 pr-3">Status</th>
                  <th className="py-2 pr-3" />
                </tr>
              </thead>
              <tbody>
                {listQuery.data.content.map((d) => (
                  <tr key={d.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3 font-medium">{d.disputeNumber}</td>
                    <td className="py-2 pr-3">
                      {d.employeeName} ({d.employeeCode})
                    </td>
                    <td className="py-2 pr-3">{d.disputeCategory.replaceAll('_', ' ')}</td>
                    <td className="py-2 pr-3 whitespace-nowrap">{formatDate(d.raisedAt, 'dd-MM-yyyy hh:mm a')}</td>
                    <td className="py-2 pr-3">
                      <Badge tone={statusTone(d.status)}>{d.status.replaceAll('_', ' ')}</Badge>
                    </td>
                    <td className="py-2 pr-3 text-right">
                      <button type="button" onClick={() => setSelectedId(d.id)} className="text-xs font-medium text-brand-forest hover:underline">
                        Review
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            <div className="mt-4 flex items-center justify-between text-xs text-slate-500">
              <span>
                Page {listQuery.data.number + 1} of {Math.max(1, listQuery.data.totalPages)} · {listQuery.data.totalElements} dispute
                {listQuery.data.totalElements === 1 ? '' : 's'}
              </span>
              <div className="flex items-center gap-2">
                <SecondaryButton onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={listQuery.data.first}>
                  Previous
                </SecondaryButton>
                <SecondaryButton onClick={() => setPage((p) => p + 1)} disabled={listQuery.data.last}>
                  Next
                </SecondaryButton>
              </div>
            </div>
          </div>
        )}
      </Card>

      {selectedId !== null && <DisputeReviewModal disputeId={selectedId} onClose={() => setSelectedId(null)} />}
    </div>
  )
}

function DisputeReviewModal({ disputeId, onClose }: { disputeId: number; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [remarks, setRemarks] = useState('')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const detailQuery = useQuery({
    queryKey: ['cpf-dispute-admin-detail', disputeId],
    queryFn: async () => (await apiClient.get<CpfDisputeAdminResponse>(`/v1/payroll/trust/cpf/disputes/${disputeId}`)).data,
  })

  const historyQuery = useQuery({
    queryKey: ['cpf-dispute-history', disputeId],
    queryFn: async () => (await apiClient.get<CpfDisputeHistoryEntryResponse[]>(`/v1/payroll/trust/cpf/disputes/${disputeId}/history`)).data,
  })

  function invalidateAfterAction() {
    queryClient.invalidateQueries({ queryKey: ['cpf-dispute-admin-detail', disputeId] })
    queryClient.invalidateQueries({ queryKey: ['cpf-dispute-history', disputeId] })
    queryClient.invalidateQueries({ queryKey: ['cpf-disputes-admin'] })
    queryClient.invalidateQueries({ queryKey: ['cpf-dispute-dashboard-counts'] })
  }

  function runAction(path: string, body: Record<string, unknown>) {
    setErrorMessage(null)
    actionMutation.mutate({ path, body })
  }

  const actionMutation = useMutation({
    mutationFn: async ({ path, body }: { path: string; body: Record<string, unknown> }) =>
      (await apiClient.post(`/v1/payroll/trust/cpf/disputes/${disputeId}/${path}`, body)).data,
    onSuccess: () => {
      setRemarks('')
      invalidateAfterAction()
    },
    onError: (error) => setErrorMessage(describeApiError(error, 'Could not complete this action - the dispute may have been updated by someone else.')),
  })

  const dispute = detailQuery.data
  const expectedVersion = dispute?.version

  return (
    <Modal title={dispute ? `Dispute ${dispute.disputeNumber}` : 'Loading dispute...'} onClose={onClose} maxWidthClassName="max-w-2xl">
      {detailQuery.isLoading && <LoadingState label="Loading dispute..." />}
      {detailQuery.isError && <ErrorState message="Could not load this dispute." />}

      {dispute && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3 text-sm">
            <div>
              <span className="text-xs text-slate-400">Employee</span>
              <div>{dispute.employeeName} ({dispute.employeeCode})</div>
            </div>
            <div>
              <span className="text-xs text-slate-400">Category</span>
              <div>{dispute.disputeCategory.replaceAll('_', ' ')}</div>
            </div>
            <div>
              <span className="text-xs text-slate-400">Status</span>
              <div>
                <Badge tone={statusTone(dispute.status)}>{dispute.status.replaceAll('_', ' ')}</Badge>
              </div>
            </div>
            <div>
              <span className="text-xs text-slate-400">Raised</span>
              <div>{formatDate(dispute.raisedAt, 'dd-MM-yyyy hh:mm a')}</div>
            </div>
          </div>

          <div className="rounded-md bg-slate-50 p-3 text-sm">
            <div className="text-xs text-slate-400">Employee's remarks</div>
            <p className="mt-1 whitespace-pre-wrap">{dispute.employeeRemarks}</p>
            {dispute.attachmentOriginalFilename && (
              <div className="mt-2 text-xs text-slate-500">Attachment: {dispute.attachmentOriginalFilename}</div>
            )}
          </div>

          {dispute.clarificationRequest && (
            <div className="rounded-md border border-amber-100 bg-amber-50 p-3 text-sm">
              <div className="text-xs text-amber-600">Clarification requested</div>
              <p className="mt-1 whitespace-pre-wrap">{dispute.clarificationRequest}</p>
              {dispute.employeeResponse && (
                <>
                  <div className="mt-2 text-xs text-amber-600">Employee's response</div>
                  <p className="mt-1 whitespace-pre-wrap">{dispute.employeeResponse}</p>
                </>
              )}
            </div>
          )}

          {dispute.resolutionRemarks && (
            <div className="rounded-md border border-slate-100 p-3 text-sm">
              <div className="text-xs text-slate-400">Decision remarks</div>
              <p className="mt-1 whitespace-pre-wrap">{dispute.resolutionRemarks}</p>
            </div>
          )}

          {!['RESOLVED', 'REJECTED', 'WITHDRAWN'].includes(dispute.status) && (
            <div className="space-y-2 border-t border-slate-100 pt-3">
              <label className="mb-1 block text-xs font-medium text-slate-600">Remarks for the next action</label>
              <textarea
                value={remarks}
                onChange={(e) => setRemarks(e.target.value)}
                rows={3}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none"
                placeholder="Required for clarification request, resolve, or reject..."
              />
              {errorMessage && <p className="text-xs text-red-600">{errorMessage}</p>}
              <div className="flex flex-wrap gap-2">
                {dispute.status === 'OPEN' && (
                  <PrimaryButton onClick={() => runAction('start-review', { expectedVersion })} disabled={actionMutation.isPending}>
                    Start Review
                  </PrimaryButton>
                )}
                {dispute.status === 'UNDER_REVIEW' && (
                  <>
                    <SecondaryButton
                      onClick={() => runAction('request-clarification', { remarks, expectedVersion })}
                      disabled={remarks.trim().length === 0 || actionMutation.isPending}
                    >
                      Request Clarification
                    </SecondaryButton>
                    <PrimaryButton
                      onClick={() => runAction('resolve', { remarks, expectedVersion })}
                      disabled={remarks.trim().length === 0 || actionMutation.isPending}
                    >
                      Resolve
                    </PrimaryButton>
                    <SecondaryButton
                      onClick={() => runAction('reject', { remarks, expectedVersion })}
                      disabled={remarks.trim().length === 0 || actionMutation.isPending}
                      className="border-rose-200 text-rose-700 hover:bg-rose-50"
                    >
                      Reject
                    </SecondaryButton>
                  </>
                )}
              </div>
            </div>
          )}

          {historyQuery.data && historyQuery.data.length > 0 && (
            <div className="border-t border-slate-100 pt-3">
              <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">History</h3>
              <div className="space-y-1.5 text-xs text-slate-500">
                {historyQuery.data.map((h, i) => (
                  <div key={i} className="flex items-center justify-between">
                    <span>
                      {h.action} {h.performedBy ? `by ${h.performedBy}` : ''}
                    </span>
                    <span>{formatDate(h.timestamp, 'dd-MM-yyyy hh:mm a')}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </Modal>
  )
}
