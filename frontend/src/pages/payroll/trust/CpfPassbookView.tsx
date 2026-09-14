import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { apiClient } from '../../../api/client'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader } from '../../../components/common/ui'
import { EmployeePickerInput } from '../../../components/common/EmployeePickerInput'
import type { CpfPassbookResponse, EmployeeResponse } from '../../../types/api'

function finYearOf(date: Date): string {
  const startYear = date.getMonth() >= 3 ? date.getFullYear() : date.getFullYear() - 1
  return `${startYear}-${startYear + 1}`
}

/** FYs the member could actually have ledger activity in - from their joining FY through the current FY, most recent first. Not narrowed to a separation date (not available on EmployeeResponse) - a separated member simply shows no activity for FYs after their exit. */
function financialYearOptionsFor(dateOfJoining: string): string[] {
  const joiningFinYear = finYearOf(new Date(dateOfJoining))
  const currentFinYear = finYearOf(new Date())
  const joiningStartYear = Number(joiningFinYear.split('-')[0])
  const currentStartYear = Number(currentFinYear.split('-')[0])
  const count = Math.max(1, currentStartYear - joiningStartYear + 1)
  return Array.from({ length: count }, (_, i) => {
    const startYear = currentStartYear - i
    return `${startYear}-${startYear + 1}`
  })
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
  const [finYear, setFinYear] = useState('')
  const years = useMemo(() => (employee ? financialYearOptionsFor(employee.dateOfJoining) : []), [employee])

  function selectEmployee(next: EmployeeResponse | null) {
    setEmployee(next)
    setFinYear(next ? financialYearOptionsFor(next.dateOfJoining)[0] : '')
  }

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['cpf-passbook', employee?.id, finYear],
    queryFn: async () =>
      (
        await apiClient.get<CpfPassbookResponse>(`/v1/payroll/trust/cpf/passbook/${employee!.id}`, {
          params: { finYear },
        })
      ).data,
    enabled: employee !== null && finYear !== '',
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
            <EmployeePickerInput selected={employee} onSelect={selectEmployee} />
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

          <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <SummaryCard label="Audited Ledger Balance" amount={data.ledgerBalance} />
            <SummaryCard label="Accrued Interest (FYTD)" amount={data.accruedInterestFytd} />
            <SummaryCard label="Effective Total Corpus" amount={data.effectiveTotalCorpus} emphasize />
            <SummaryCard label="Outstanding Refundable Loan Balance" amount={data.outstandingLoanBalance} />
          </div>

          <Card>
            {data.entries.length === 0 ? (
              <EmptyState message="No ledger activity for this financial year." />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[1400px] border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 text-left text-[11px] uppercase tracking-wide text-slate-400">
                      <th className="py-1 pr-3" rowSpan={2}>
                        Month
                        <br />
                        Year
                      </th>
                      <th className="py-1 pr-3" rowSpan={2}>
                        Type
                      </th>
                      <th className="py-1 pr-3 text-center" colSpan={3}>
                        Contribution to Fund
                      </th>
                      <th className="py-1 pr-3 text-center" colSpan={4}>
                        Diversion / Loan Sanction
                      </th>
                      <th className="py-1 pr-3 text-center" colSpan={2}>
                        Loan Repayment
                      </th>
                      <th className="py-1 pr-3 text-right" rowSpan={2}>
                        Running Balance
                        <br />
                        (Total Net Fund)
                      </th>
                    </tr>
                    <tr className="border-b border-slate-300 text-right text-slate-500">
                      <th className="py-1.5 pr-3">EMP</th>
                      <th className="py-1.5 pr-3">JCI</th>
                      <th className="py-1.5 pr-3">VPF</th>
                      <th className="py-1.5 pr-3">CPF</th>
                      <th className="py-1.5 pr-3">NRW EMP</th>
                      <th className="py-1.5 pr-3">NRW JCI</th>
                      <th className="py-1.5 pr-3">NRW VPF</th>
                      <th className="py-1.5 pr-3">Prin.</th>
                      <th className="py-1.5 pr-3">Int.</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.entries.map((e) => (
                      <tr key={e.id} className="border-b border-slate-100">
                        <td className="py-1.5 pr-3 text-left whitespace-nowrap">{e.displayPeriod}</td>
                        <td className="py-1.5 pr-3 text-left whitespace-nowrap">
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
                        <td className="py-1.5 pr-3 text-right tabular-nums">{e.sancCpfLoan.toFixed(2)}</td>
                        <td className="py-1.5 pr-3 text-right tabular-nums">{e.sancNrwEe.toFixed(2)}</td>
                        <td className="py-1.5 pr-3 text-right tabular-nums">{e.sancNrwEr.toFixed(2)}</td>
                        <td className="py-1.5 pr-3 text-right tabular-nums">{e.sancNrwVpf.toFixed(2)}</td>
                        <td className="py-1.5 pr-3 text-right tabular-nums">{e.loanRepayPrincipal.toFixed(2)}</td>
                        <td className="py-1.5 pr-3 text-right tabular-nums">{e.loanRepayInterest.toFixed(2)}</td>
                        <td className="py-1.5 pr-3 text-right tabular-nums font-medium">{e.runningTotalBalance.toFixed(2)}</td>
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
