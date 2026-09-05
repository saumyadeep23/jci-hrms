import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Badge, Card, ErrorState, LoadingState, PageHeader, SecondaryButton } from '../../components/common/ui'
import type { EmployeeLoanResponse } from '../../types/api'

interface AmortizationRow {
  month: number
  emi: number
  principal: number
  interest: number
  balance: number
}

function computeSchedule(principal: number, annualRatePercent: number, tenureMonths: number): AmortizationRow[] {
  const monthlyRate = annualRatePercent / 12 / 100
  const emi =
    monthlyRate === 0
      ? principal / tenureMonths
      : (principal * monthlyRate * (1 + monthlyRate) ** tenureMonths) / ((1 + monthlyRate) ** tenureMonths - 1)

  const rows: AmortizationRow[] = []
  let balance = principal
  for (let month = 1; month <= tenureMonths; month++) {
    const interest = balance * monthlyRate
    const principalComponent = Math.min(emi - interest, balance)
    balance = Math.max(0, balance - principalComponent)
    rows.push({ month, emi, principal: principalComponent, interest, balance })
  }
  return rows
}

/**
 * LoanController.sanction() is FINANCE_ADMIN/CPF_ADMIN/COOP_ADMIN/SUPER_ADMIN
 * only (Phase 10, per spec) - there is no self-service loan request path.
 * getById is self-or-admin, so "view my loan" works; there's no self-filtered
 * list either, so it's looked up by account/loan ID.
 */
export function LoansPage() {
  const [principal, setPrincipal] = useState(50000)
  const [rate, setRate] = useState(9)
  const [tenure, setTenure] = useState(12)
  const [loanId, setLoanId] = useState('')
  const [activeLoanId, setActiveLoanId] = useState<number | null>(null)

  const schedule = useMemo(() => computeSchedule(principal, rate, tenure), [principal, rate, tenure])
  const totalInterest = schedule.reduce((sum, row) => sum + row.interest, 0)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['loan', activeLoanId],
    queryFn: async () => (await apiClient.get<EmployeeLoanResponse>(`/loans/${activeLoanId}`)).data,
    enabled: activeLoanId !== null,
  })

  return (
    <div>
      <PageHeader title="Loans & EMI Calculator" />

      <Card className="mb-6 bg-amber-50">
        <p className="text-xs text-amber-800">
          Loan sanctioning is restricted to Finance/CPF/Co-op admins in this system - employees cannot self-request a
          loan through the API. This page offers the reducing-balance EMI calculator (fully client-side) and lets you
          look up a loan you already have by its ID.
        </p>
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <h2 className="mb-3 text-sm font-semibold text-slate-700">Reducing-Balance EMI Calculator</h2>
          <div className="grid grid-cols-3 gap-3">
            <NumberField label="Principal (₹)" value={principal} onChange={setPrincipal} />
            <NumberField label="Annual Rate (%)" value={rate} onChange={setRate} step={0.1} />
            <NumberField label="Tenure (months)" value={tenure} onChange={setTenure} />
          </div>

          <div className="mt-4 grid grid-cols-2 gap-3 rounded-lg bg-slate-50 p-3 text-sm">
            <div>
              <p className="text-slate-400">Monthly EMI</p>
              <p className="text-lg font-semibold text-brand-forest">₹{schedule[0]?.emi.toFixed(2) ?? '0.00'}</p>
            </div>
            <div>
              <p className="text-slate-400">Total Interest</p>
              <p className="text-lg font-semibold text-slate-700">₹{totalInterest.toFixed(2)}</p>
            </div>
          </div>

          <div className="mt-4 max-h-72 overflow-y-auto rounded-lg border border-slate-100">
            <table className="w-full text-xs">
              <thead className="sticky top-0 bg-slate-50 text-slate-500">
                <tr>
                  <th className="px-2 py-1.5 text-left">#</th>
                  <th className="px-2 py-1.5 text-right">Principal</th>
                  <th className="px-2 py-1.5 text-right">Interest</th>
                  <th className="px-2 py-1.5 text-right">Balance</th>
                </tr>
              </thead>
              <tbody>
                {schedule.map((row) => (
                  <tr key={row.month} className="border-t border-slate-100">
                    <td className="px-2 py-1">{row.month}</td>
                    <td className="px-2 py-1 text-right tabular-nums">{row.principal.toFixed(2)}</td>
                    <td className="px-2 py-1 text-right tabular-nums">{row.interest.toFixed(2)}</td>
                    <td className="px-2 py-1 text-right tabular-nums">{row.balance.toFixed(2)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>

        <Card>
          <h2 className="mb-3 text-sm font-semibold text-slate-700">View My Loan</h2>
          <form
            className="mb-4 flex gap-2"
            onSubmit={(e) => {
              e.preventDefault()
              const parsed = Number(loanId)
              if (!Number.isNaN(parsed) && parsed > 0) setActiveLoanId(parsed)
            }}
          >
            <input
              value={loanId}
              onChange={(e) => setLoanId(e.target.value)}
              className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm"
              placeholder="Loan ID"
            />
            <SecondaryButton type="submit">Load</SecondaryButton>
          </form>

          {isLoading && <LoadingState />}
          {isError && <ErrorState message="Could not load that loan (it may not be yours, or it doesn't exist)." />}
          {data && (
            <div className="space-y-2 text-sm">
              <div className="flex items-center justify-between">
                <p className="font-medium">{data.loanAccountNumber}</p>
                <Badge tone={data.status === 'ACTIVE' ? 'brand' : 'neutral'}>{data.status}</Badge>
              </div>
              <dl className="grid grid-cols-2 gap-y-1 text-slate-500">
                <dt>Type</dt>
                <dd className="text-right text-slate-700">{data.loanTypeName}</dd>
                <dt>Principal</dt>
                <dd className="text-right text-slate-700">₹{data.principalAmount.toFixed(2)}</dd>
                <dt>Outstanding</dt>
                <dd className="text-right text-slate-700">₹{data.outstandingPrincipal.toFixed(2)}</dd>
                <dt>Installments left</dt>
                <dd className="text-right text-slate-700">
                  {data.remainingInstallments} / {data.totalInstallments}
                </dd>
              </dl>
            </div>
          )}
        </Card>
      </div>
    </div>
  )
}

function NumberField({
  label,
  value,
  onChange,
  step = 1,
}: {
  label: string
  value: number
  onChange: (v: number) => void
  step?: number
}) {
  return (
    <div>
      <label className="mb-1 block text-xs font-medium text-slate-600">{label}</label>
      <input
        type="number"
        step={step}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm"
      />
    </div>
  )
}
