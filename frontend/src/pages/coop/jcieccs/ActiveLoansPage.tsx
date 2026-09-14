import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { Badge, ErrorState, LoadingState, PageHeader } from '../../../components/common/ui'
import { describeApiError } from '../../../lib/apiError'
import type { JciEccsLoanResponse, JciEccsLoanScheduleResponse } from '../../../types/api'

const inr = (n: number) => `₹${n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/** Route: /jcieccs/loans/active - GET /api/jcieccs/loans (Phase 4; previously no list-all endpoint
 * existed). Defaults to ACTIVE; click a row to load its installment schedule below. */
export function ActiveLoansPage() {
  const [loanId, setLoanId] = useState<number | null>(null)

  const loansQuery = useQuery({
    queryKey: ['jcieccs-loans', 'ACTIVE'],
    queryFn: async () => (await apiClient.get<JciEccsLoanResponse[]>('/jcieccs/loans', { params: { status: 'ACTIVE' } })).data,
  })

  const scheduleQuery = useQuery({
    queryKey: ['jcieccs-loan-schedule', loanId],
    queryFn: async () => (await apiClient.get<JciEccsLoanScheduleResponse[]>(`/jcieccs/loans/${loanId}/schedule`)).data,
    enabled: loanId !== null,
  })

  return (
    <div>
      <PageHeader title="Active Loans" description="Term (TE-) & Emergency (EM-) loans - click a row to view its installment schedule" />

      {loansQuery.isLoading && <LoadingState label="Loading active loans..." />}
      {loansQuery.isError && <ErrorState message={describeApiError(loansQuery.error, 'Could not load active loans.')} />}

      {loansQuery.data && (
        <div className="mb-6 overflow-x-auto rounded-md border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-3 py-2">Loan No</th>
                <th className="px-3 py-2">Type</th>
                <th className="px-3 py-2">Disbursement Date</th>
                <th className="px-3 py-2 text-right">Original Principal</th>
                <th className="px-3 py-2 text-right">Interest Rate</th>
                <th className="px-3 py-2 text-right">Tenure</th>
                <th className="px-3 py-2 text-right">Outstanding Principal</th>
                <th className="px-3 py-2">Status</th>
              </tr>
            </thead>
            <tbody>
              {loansQuery.data.map((l) => (
                <tr
                  key={l.id}
                  onClick={() => setLoanId(l.id)}
                  className={`cursor-pointer border-b border-slate-100 last:border-0 hover:bg-slate-50 ${loanId === l.id ? 'bg-brand-forest/5' : ''}`}
                >
                  <td className="px-3 py-2 font-medium text-slate-800">{l.loanIssueId}</td>
                  <td className="px-3 py-2">{l.productCode}</td>
                  <td className="px-3 py-2">{l.disbursementDate}</td>
                  <td className="px-3 py-2 text-right">{inr(l.sanctionedAmount)}</td>
                  <td className="px-3 py-2 text-right">{l.annualInterestRate.toFixed(2)}%</td>
                  <td className="px-3 py-2 text-right">{l.tenureMonths} mo</td>
                  <td className="px-3 py-2 text-right">{inr(l.outstandingPrincipal)}</td>
                  <td className="px-3 py-2">
                    <Badge tone={l.status === 'ACTIVE' ? 'success' : l.status === 'CLOSED' ? 'neutral' : 'warning'}>{l.status}</Badge>
                  </td>
                </tr>
              ))}
              {loansQuery.data.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-3 py-6 text-center text-sm text-slate-400">
                    No active loans.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {loanId && (
        <>
          <p className="mb-2 text-xs font-medium text-slate-600">Schedule for loan #{loanId}</p>
          {scheduleQuery.isLoading && <LoadingState label="Loading schedule..." />}
          {scheduleQuery.isError && <ErrorState message={describeApiError(scheduleQuery.error, "Could not load this loan's schedule.")} />}
          {scheduleQuery.data && (
            <div className="overflow-x-auto rounded-md border border-slate-200 bg-white">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                  <tr>
                    <th className="px-3 py-2">#</th>
                    <th className="px-3 py-2">Cycle</th>
                    <th className="px-3 py-2 text-right">Opening Principal</th>
                    <th className="px-3 py-2 text-right">Principal Due</th>
                    <th className="px-3 py-2 text-right">Interest Due</th>
                    <th className="px-3 py-2 text-right">Outstanding</th>
                    <th className="px-3 py-2">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {scheduleQuery.data.map((s) => (
                    <tr key={s.id} className="border-b border-slate-100 last:border-0">
                      <td className="px-3 py-2">{s.installmentNo}</td>
                      <td className="px-3 py-2">{s.cycleCode}</td>
                      <td className="px-3 py-2 text-right">{s.openingPrincipal.toFixed(2)}</td>
                      <td className="px-3 py-2 text-right">{s.principalDue}</td>
                      <td className="px-3 py-2 text-right">{s.interestDue.toFixed(2)}</td>
                      <td className="px-3 py-2 text-right">{s.principalOutstanding.toFixed(2)}</td>
                      <td className="px-3 py-2">
                        <Badge tone={s.status === 'PAID' ? 'success' : s.status === 'OVERDUE' ? 'danger' : 'neutral'}>{s.status}</Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  )
}
