import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { Badge, Card, ErrorState, LoadingState, PageHeader } from '../../../components/common/ui'
import { describeApiError } from '../../../lib/apiError'
import type { JciEccsRecoveryResponse, JciEccsRecoverySource } from '../../../types/api'

const inr = (n: number) => `₹${n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

function sourceTone(source: JciEccsRecoverySource): 'brand' | 'success' | 'warning' {
  if (source === 'REVERSAL') return 'warning'
  if (source === 'CASH') return 'success'
  return 'brand'
}

/** Route: /jcieccs/payroll-recovery/audit-trail - GET /api/jcieccs/recoveries. The priority-cascade
 * allocation history (Thrift -> Term Interest -> Emergency Interest -> Term Principal -> Emergency
 * Principal): every recovery event (payroll debit, cash deposit, or reversal) with its component-level
 * allocation breakdown in the exact sequence the allocation cascade applied it. Reversed recoveries are
 * never hidden - they show alongside the original they compensate, linked by reversalOfRecoveryId. */
export function AuditTrailPage() {
  const [memberId, setMemberId] = useState('')
  const [loanId, setLoanId] = useState('')
  const [appliedMemberId, setAppliedMemberId] = useState<string | null>(null)
  const [appliedLoanId, setAppliedLoanId] = useState<string | null>(null)

  const recoveriesQuery = useQuery({
    queryKey: ['jcieccs-recoveries', appliedMemberId, appliedLoanId],
    queryFn: async () =>
      (
        await apiClient.get<JciEccsRecoveryResponse[]>('/jcieccs/recoveries', {
          params: {
            memberId: appliedMemberId || undefined,
            loanId: appliedLoanId || undefined,
          },
        })
      ).data,
  })

  const recoveries = recoveriesQuery.data ?? []

  return (
    <div>
      <PageHeader
        title="Priority Cascade Audit Trail"
        description="Recovery -> Allocation -> Posting: how each actually-recovered amount was split across Thrift, Interest and Principal"
      />

      <div className="mb-4 flex flex-wrap items-end gap-2">
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Member ID (optional)</label>
          <input
            value={memberId}
            onChange={(e) => setMemberId(e.target.value)}
            className="w-40 rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Loan ID (optional)</label>
          <input
            value={loanId}
            onChange={(e) => setLoanId(e.target.value)}
            className="w-40 rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <button
          type="button"
          onClick={() => {
            setAppliedMemberId(memberId || null)
            setAppliedLoanId(loanId || null)
          }}
          className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          Filter
        </button>
        <p className="text-[11px] text-slate-400">Leave both blank to see the most recent 100 recoveries across the whole module.</p>
      </div>

      {recoveriesQuery.isLoading && <LoadingState label="Loading recovery trail..." />}
      {recoveriesQuery.isError && <ErrorState message={describeApiError(recoveriesQuery.error, 'Could not load the recovery trail.')} />}

      <div className="space-y-3">
        {recoveries.map((r) => (
          <Card key={r.id}>
            <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <Badge tone={sourceTone(r.source)}>{r.source}</Badge>
                <Badge tone={r.status === 'POSTED' ? 'success' : r.status === 'REVERSED' ? 'neutral' : 'warning'}>{r.status}</Badge>
                <span className="text-sm font-medium text-slate-800">{r.membershipCode}</span>
                <span className="text-[11px] text-slate-400">Employee #{r.employeeId}</span>
                {r.loanIssueId && <span className="text-[11px] text-slate-400">Loan {r.loanIssueId}</span>}
              </div>
              <div className="text-right">
                <p className="text-sm font-semibold text-slate-800">{inr(r.grossAmount)}</p>
                <p className="text-[11px] text-slate-400">{new Date(r.createdAt).toLocaleString('en-IN')}</p>
              </div>
            </div>
            {r.reversalOfRecoveryId && (
              <p className="mb-2 text-[11px] text-amber-700">Reverses recovery #{r.reversalOfRecoveryId}</p>
            )}
            <table className="w-full text-left text-[11px]">
              <thead>
                <tr className="text-slate-400">
                  <th className="pb-1 pr-2 font-medium">#</th>
                  <th className="pb-1 pr-2 font-medium">Component</th>
                  <th className="pb-1 pr-2 font-medium">Expected</th>
                  <th className="pb-1 font-medium">Allocated</th>
                </tr>
              </thead>
              <tbody>
                {r.allocations.map((a) => (
                  <tr key={a.sequence} className="border-t border-slate-100">
                    <td className="py-1 pr-2 text-slate-500">{a.sequence}</td>
                    <td className="py-1 pr-2 text-slate-700">{a.component}</td>
                    <td className="py-1 pr-2 text-slate-600">{inr(a.expectedAmount)}</td>
                    <td className="py-1 font-medium text-slate-800">{inr(a.allocatedAmount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
        ))}

        {!recoveriesQuery.isLoading && recoveries.length === 0 && !recoveriesQuery.isError && (
          <div className="rounded-md border border-dashed border-slate-300 bg-slate-50 p-8 text-center text-sm text-slate-500">
            No recoveries found for this filter.
          </div>
        )}
      </div>
    </div>
  )
}
