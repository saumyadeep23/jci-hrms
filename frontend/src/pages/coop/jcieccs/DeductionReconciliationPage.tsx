import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { Badge, Card, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../../components/common/ui'
import { describeApiError } from '../../../lib/apiError'
import type {
  JciEccsReconciliationReasonCode,
  JciEccsReconciliationResolutionAction,
  JciEccsReconciliationResponse,
  JciEccsReconciliationStatus,
  JciEccsReconciliationSummaryResponse,
} from '../../../types/api'

const inr = (n: number) => `₹${n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

function statusTone(status: JciEccsReconciliationStatus): 'success' | 'warning' | 'danger' | 'neutral' | 'brand' {
  switch (status) {
    case 'MATCHED':
      return 'success'
    case 'PARTIAL':
    case 'POSTING_PENDING':
      return 'warning'
    case 'NOT_RECOVERED':
    case 'POSTING_MISMATCH':
    case 'ERROR':
      return 'danger'
    case 'OVER_RECOVERED':
    case 'LOCKED_SNAPSHOT_CONFLICT':
      return 'brand'
    default:
      return 'neutral'
  }
}

const RESOLUTION_ACTIONS: JciEccsReconciliationResolutionAction[] = [
  'ACKNOWLEDGE',
  'MARK_RESOLVED',
  'REQUEST_PAYROLL_CORRECTION',
  'REVERSE_RECOVERY',
  'NO_ACTION_REQUIRED',
]

function ResolveRow({ row, payrollRunId }: { row: JciEccsReconciliationResponse; payrollRunId: string }) {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [action, setAction] = useState<JciEccsReconciliationResolutionAction>('ACKNOWLEDGE')
  const [remarks, setRemarks] = useState('')

  const resolveMutation = useMutation({
    mutationFn: async () =>
      (await apiClient.post(`/jcieccs/reconciliation/${row.id}/resolve`, { action, remarks })).data,
    onSuccess: () => {
      setOpen(false)
      setRemarks('')
      queryClient.invalidateQueries({ queryKey: ['jcieccs-reconciliation', payrollRunId] })
    },
  })

  if (row.resolved) {
    return (
      <div className="text-[11px] text-slate-400">
        {row.resolutionAction} by #{row.resolvedBy} - {row.resolutionRemarks}
      </div>
    )
  }

  if (!open) {
    return (
      <SecondaryButton type="button" onClick={() => setOpen(true)}>
        Resolve
      </SecondaryButton>
    )
  }

  return (
    <div className="space-y-2">
      <select
        value={action}
        onChange={(e) => setAction(e.target.value as JciEccsReconciliationResolutionAction)}
        className="w-full rounded-md border border-slate-300 px-2 py-1 text-xs"
      >
        {RESOLUTION_ACTIONS.map((a) => (
          <option key={a} value={a}>
            {a}
          </option>
        ))}
      </select>
      <textarea
        value={remarks}
        onChange={(e) => setRemarks(e.target.value)}
        placeholder="Remarks (required)"
        className="w-full rounded-md border border-slate-300 px-2 py-1 text-xs"
        rows={2}
      />
      <div className="flex gap-2">
        <PrimaryButton type="button" disabled={!remarks.trim() || resolveMutation.isPending} onClick={() => resolveMutation.mutate()}>
          {resolveMutation.isPending ? 'Saving...' : 'Confirm'}
        </PrimaryButton>
        <SecondaryButton type="button" onClick={() => setOpen(false)}>
          Cancel
        </SecondaryButton>
      </div>
      {resolveMutation.isError && <ErrorState message={describeApiError(resolveMutation.error, 'Could not resolve this exception.')} />}
    </div>
  )
}

/** Route: /jcieccs/payroll-recovery/reconciliation - the genuine three-way reconciliation (DEMAND vs
 * ACTUAL RECOVERY vs LEDGER POSTING), replacing the earlier page that only echoed the locked snapshot's
 * own totals. Detects exceptions; never repairs them - resolution is an explicit, remarked action, and
 * REVERSE_RECOVERY is the only one that changes financial state (via the existing, already-audited
 * JciEccsRecoveryService.reverseRecovery). */
export function DeductionReconciliationPage() {
  const [payrollRunIdInput, setPayrollRunIdInput] = useState('')
  const [payrollRunId, setPayrollRunId] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const rowsQuery = useQuery({
    queryKey: ['jcieccs-reconciliation', payrollRunId],
    queryFn: async () =>
      (await apiClient.get<JciEccsReconciliationResponse[]>(`/jcieccs/reconciliation/payroll-runs/${payrollRunId}`)).data,
    enabled: payrollRunId !== null,
  })

  const runMutation = useMutation({
    mutationFn: async () =>
      (await apiClient.post<JciEccsReconciliationSummaryResponse>(`/jcieccs/reconciliation/payroll-runs/${payrollRunId}/run`)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['jcieccs-reconciliation', payrollRunId] }),
  })

  const rows = rowsQuery.data ?? []
  const totals = rows.reduce(
    (acc, r) => ({
      expected: acc.expected + r.expectedAmount,
      actual: acc.actual + r.actualAmount,
      posted: acc.posted + r.postedAmount,
      matched: acc.matched + (r.status === 'MATCHED' ? 1 : 0),
      partial: acc.partial + (r.status === 'PARTIAL' ? 1 : 0),
      exceptions: acc.exceptions + (r.status !== 'MATCHED' && !r.resolved ? 1 : 0),
    }),
    { expected: 0, actual: 0, posted: 0, matched: 0, partial: 0, exceptions: 0 },
  )

  return (
    <div>
      <PageHeader
        title="Deduction Reconciliation"
        description="Three-way comparison: what was demanded, what Payroll actually recovered, and what was posted to the JCIECCS ledger"
      />

      <div className="mb-4 flex items-end gap-2">
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Payroll Run ID (Payroll Batch ID)</label>
          <input
            value={payrollRunIdInput}
            onChange={(e) => setPayrollRunIdInput(e.target.value)}
            className="w-56 rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <PrimaryButton disabled={!payrollRunIdInput} onClick={() => setPayrollRunId(payrollRunIdInput)}>
          Load
        </PrimaryButton>
        <SecondaryButton disabled={!payrollRunId || runMutation.isPending} onClick={() => runMutation.mutate()}>
          {runMutation.isPending ? 'Reconciling...' : 'Run Reconciliation'}
        </SecondaryButton>
      </div>

      {runMutation.isError && <ErrorState message={describeApiError(runMutation.error, 'Could not run reconciliation.')} />}
      {rowsQuery.isLoading && <LoadingState label="Loading reconciliation..." />}
      {rowsQuery.isError && <ErrorState message={describeApiError(rowsQuery.error, 'No reconciliation data for this payroll run yet.')} />}

      {payrollRunId && rows.length > 0 && (
        <>
          <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
            <Card className="p-3">
              <p className="text-[11px] text-slate-400">Expected</p>
              <p className="text-sm font-semibold text-slate-800">{inr(totals.expected)}</p>
            </Card>
            <Card className="p-3">
              <p className="text-[11px] text-slate-400">Actually Recovered</p>
              <p className="text-sm font-semibold text-slate-800">{inr(totals.actual)}</p>
            </Card>
            <Card className="p-3">
              <p className="text-[11px] text-slate-400">Posted to Ledger</p>
              <p className="text-sm font-semibold text-slate-800">{inr(totals.posted)}</p>
            </Card>
            <Card className="p-3">
              <p className="text-[11px] text-slate-400">Matched Lines</p>
              <p className="text-sm font-semibold text-emerald-700">{totals.matched}</p>
            </Card>
            <Card className="p-3">
              <p className="text-[11px] text-slate-400">Partial Recoveries</p>
              <p className="text-sm font-semibold text-amber-700">{totals.partial}</p>
            </Card>
            <Card className="p-3">
              <p className="text-[11px] text-slate-400">Open Exceptions</p>
              <p className="text-sm font-semibold text-red-700">{totals.exceptions}</p>
            </Card>
          </div>

          <div className="overflow-x-auto rounded-md border border-slate-200 bg-white">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-3 py-2">Member</th>
                  <th className="px-3 py-2">Component</th>
                  <th className="px-3 py-2 text-right">Demand</th>
                  <th className="px-3 py-2 text-right">Actual</th>
                  <th className="px-3 py-2 text-right">Posted</th>
                  <th className="px-3 py-2 text-right">Variance</th>
                  <th className="px-3 py-2">Status</th>
                  <th className="px-3 py-2">Reason</th>
                  <th className="px-3 py-2">Action</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.id} className="border-b border-slate-100 align-top last:border-0">
                    <td className="px-3 py-2">
                      <div className="font-medium text-slate-800">{r.membershipCode}</div>
                      <div className="text-[11px] text-slate-400">Employee #{r.employeeId}</div>
                    </td>
                    <td className="px-3 py-2 text-slate-600">{r.component}</td>
                    <td className="px-3 py-2 text-right">{inr(r.expectedAmount)}</td>
                    <td className="px-3 py-2 text-right">{inr(r.actualAmount)}</td>
                    <td className="px-3 py-2 text-right">{inr(r.postedAmount)}</td>
                    <td className={`px-3 py-2 text-right ${r.varianceAmount !== 0 ? 'text-red-600' : 'text-slate-400'}`}>
                      {inr(r.varianceAmount)}
                    </td>
                    <td className="px-3 py-2">
                      <Badge tone={statusTone(r.status)}>{r.status}</Badge>
                    </td>
                    <td className="px-3 py-2 text-[11px] text-slate-500">{r.reasonCode !== 'UNKNOWN' ? formatReason(r.reasonCode) : '-'}</td>
                    <td className="px-3 py-2">
                      <ResolveRow row={r} payrollRunId={payrollRunId} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {payrollRunId && !rowsQuery.isLoading && rows.length === 0 && !rowsQuery.isError && (
        <div className="rounded-md border border-dashed border-slate-300 bg-slate-50 p-8 text-center text-sm text-slate-500">
          No reconciliation rows yet for this payroll run - click "Run Reconciliation" once a debit confirmation has been posted.
        </div>
      )}
    </div>
  )
}

function formatReason(reason: JciEccsReconciliationReasonCode): string {
  return reason.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())
}
