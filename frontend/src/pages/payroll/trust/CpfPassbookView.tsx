import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { apiClient } from '../../../api/client'
import { formatDate } from '../../../lib/date'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader } from '../../../components/common/ui'
import { EmployeePickerInput } from '../../../components/common/EmployeePickerInput'
import type { CpfPassbookResponse, EmployeeResponse } from '../../../types/api'

function currentFinancialYearOptions(): string[] {
  const now = new Date()
  const startYear = now.getMonth() >= 3 ? now.getFullYear() : now.getFullYear() - 1
  return Array.from({ length: 5 }, (_, i) => `${startYear - i}-${startYear - i + 1}`)
}

function SummaryCard({ label, amount, emphasize = false }: { label: string; amount: number; emphasize?: boolean }) {
  return (
    <Card className={emphasize ? 'border-brand-forest/40' : ''}>
      <div className="text-xs text-slate-500">{label}</div>
      <div className={`mt-1 text-2xl font-semibold tabular-nums ${emphasize ? 'text-brand-forest' : 'text-slate-800'}`}>
        ₹{amount.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
      </div>
    </Card>
  )
}

/** Route: /payroll/trust/passbook - GET /api/v1/payroll/trust/cpf/passbook/{employeeId}?finYear=, including CpfTrustPassbookService's dynamic shadow-accrual projection and Para 60(2) provisional-rate notice. */
export function CpfPassbookView() {
  const [employee, setEmployee] = useState<EmployeeResponse | null>(null)
  const years = useMemo(() => currentFinancialYearOptions(), [])
  const [finYear, setFinYear] = useState(years[0])

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['cpf-passbook', employee?.id, finYear],
    queryFn: async () =>
      (
        await apiClient.get<CpfPassbookResponse>(`/v1/payroll/trust/cpf/passbook/${employee!.id}`, {
          params: { finYear },
        })
      ).data,
    enabled: employee !== null,
    retry: false,
  })

  const forbidden = isAxiosError(error) && error.response?.status === 403

  return (
    <div>
      <PageHeader
        title="CPF Passbook"
        description="Member CPF Trust ledger, with an in-year interest projection shown live on every read (nothing is posted until the annual run or an exit settlement crystallizes it)."
      />

      <Card className="mb-6">
        <div className="flex flex-wrap items-end gap-4">
          <div className="min-w-[280px] flex-1">
            <label className="mb-1 block text-xs font-medium text-slate-600">Employee</label>
            <EmployeePickerInput selected={employee} onSelect={setEmployee} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Financial Year</label>
            <select
              value={finYear}
              onChange={(e) => setFinYear(e.target.value)}
              className="w-40 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none"
            >
              {years.map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </select>
          </div>
        </div>
      </Card>

      {!employee && <EmptyState message="Select an employee to view their CPF Trust passbook." />}
      {employee && isLoading && <LoadingState label="Loading passbook..." />}
      {forbidden && (
        <ErrorState message="Your role cannot view CPF Trust passbooks - this is restricted to FINANCE_ADMIN, CPF_ADMIN or SUPER_ADMIN." />
      )}
      {isError && !forbidden && <ErrorState message="Could not load the CPF passbook." />}

      {data && (
        <>
          {data.isProvisionalRate && (
            <div className="mb-4 rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
              ⚠️ Notice under Para 60(2), EPF Scheme: The interest rate for FY {data.finYear} has not been notified
              yet. Calculations reflect the provisional rate of {data.rateApplied}% p.a. (notified for FY{' '}
              {data.rateSourceFinYear}).
            </div>
          )}

          <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
            <SummaryCard label="Audited Ledger Balance" amount={data.ledgerBalance} />
            <SummaryCard label="Accrued Interest (FYTD)" amount={data.accruedInterestFytd} />
            <SummaryCard label="Effective Total Corpus" amount={data.effectiveTotalCorpus} emphasize />
          </div>

          <Card>
            {data.entries.length === 0 ? (
              <EmptyState message="No ledger activity for this financial year." />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[900px] border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-slate-300 text-left text-slate-500">
                      <th className="py-1.5 pr-3">Value Date</th>
                      <th className="py-1.5 pr-3">Type</th>
                      <th className="py-1.5 pr-3 text-right">EE Credit</th>
                      <th className="py-1.5 pr-3 text-right">ER Credit</th>
                      <th className="py-1.5 pr-3 text-right">VPF Credit</th>
                      <th className="py-1.5 pr-3 text-right">Interest</th>
                      <th className="py-1.5 pr-3 text-right">Running Total</th>
                      <th className="py-1.5">Remarks</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.entries.map((e) => (
                      <tr key={e.id} className="border-b border-slate-100">
                        <td className="py-1.5 pr-3">{formatDate(e.valueDate)}</td>
                        <td className="py-1.5 pr-3">
                          <span>{e.entryType.replaceAll('_', ' ')}</span>
                          {e.entryType === 'INTERIM_SETTLEMENT_INTEREST' && (
                            <span
                              className="ml-2 inline-block"
                              title={`Rate: ${e.rateApplied?.toFixed(2) ?? '-'}% (Provisional: ${e.isProvisionalRate ? 'Yes' : 'No'})`}
                            >
                              <Badge tone="neutral" className="bg-blue-100 text-blue-700">
                                Interim Interest
                              </Badge>
                            </span>
                          )}
                        </td>
                        <td className="py-1.5 pr-3 text-right tabular-nums">{e.eeShareCredit.toFixed(2)}</td>
                        <td className="py-1.5 pr-3 text-right tabular-nums">{e.erShareCredit.toFixed(2)}</td>
                        <td className="py-1.5 pr-3 text-right tabular-nums">{e.vpfCredit.toFixed(2)}</td>
                        <td className="py-1.5 pr-3 text-right tabular-nums">{e.interestCredit.toFixed(2)}</td>
                        <td className="py-1.5 pr-3 text-right tabular-nums font-medium">{e.runningTotalBalance.toFixed(2)}</td>
                        <td className="py-1.5 text-xs text-slate-500">{e.remarks ?? '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </>
      )}
    </div>
  )
}
