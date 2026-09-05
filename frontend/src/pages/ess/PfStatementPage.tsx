import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Printer } from 'lucide-react'
import { isAxiosError } from 'axios'
import { apiClient } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { formatDate } from '../../lib/date'
import { Card, ErrorState, LoadingState, PageHeader, PrimaryButton } from '../../components/common/ui'
import { PrintableHeader } from '../../components/common/PrintableHeader'
import type { CpfBalanceLedgerResponse } from '../../types/api'

function currentFinancialYearOptions(): string[] {
  const now = new Date()
  const startYear = now.getMonth() >= 3 ? now.getFullYear() : now.getFullYear() - 1
  return Array.from({ length: 5 }, (_, i) => `${startYear - i}-${startYear - i + 1}`)
}

export function PfStatementPage() {
  const { employeeId } = useAuth()
  const years = useMemo(() => currentFinancialYearOptions(), [])
  const [financialYear, setFinancialYear] = useState(years[0])

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['cpf-ledger', employeeId],
    queryFn: async () => {
      const response = await apiClient.get<CpfBalanceLedgerResponse>(`/cpf-ledger/${employeeId}`)
      return response.data
    },
    enabled: employeeId !== null,
    retry: false,
  })

  const forbidden = isAxiosError(error) && error.response?.status === 403

  return (
    <div>
      <PageHeader
        title="Annual PF / CPF Statement"
        description="3-bucket ledger summary: Employee PF, Employer PF, Pension EPS"
        actions={
          data && (
            <PrimaryButton onClick={() => window.print()}>
              <Printer size={15} /> Download PF Statement PDF
            </PrimaryButton>
          )
        }
      />

      <Card className="mb-6 no-print">
        <label className="mb-1 block text-xs font-medium text-slate-600">Financial Year</label>
        <select
          value={financialYear}
          onChange={(e) => setFinancialYear(e.target.value)}
          className="w-48 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none"
        >
          {years.map((y) => (
            <option key={y} value={y}>
              {y}
            </option>
          ))}
        </select>
        <p className="mt-2 text-xs text-slate-400">
          The ledger below is a running balance, not sliced by financial year - there is no year-partitioned PF
          statement endpoint in the backend yet, only a live CpfBalanceLedger total.
        </p>
      </Card>

      {isLoading && <LoadingState label="Fetching CPF ledger..." />}

      {forbidden && (
        <ErrorState message="Your role cannot view CPF ledger balances yet - GET /api/cpf-ledger/{employeeId} is restricted to FINANCE_ADMIN, CPF_ADMIN, COOP_ADMIN or SUPER_ADMIN (Phase 10 RBAC never added a self-access carve-out for it)." />
      )}
      {isError && !forbidden && <ErrorState message="Could not load the CPF ledger." />}

      {data && (
        <Card className="print-sheet mx-auto max-w-2xl">
          <PrintableHeader documentTitle="Annual PF / CPF Statement" subtitle={`Financial Year ${financialYear}`} />

          <div className="mb-4 text-sm">
            <span className="text-slate-500">Employee:</span>{' '}
            <span className="font-medium">
              {data.employeeCode} (ID {data.employeeId})
            </span>
          </div>

          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-slate-300 text-left text-slate-500">
                <th className="py-1.5">Bucket</th>
                <th className="py-1.5 text-right">Closing Balance (₹)</th>
              </tr>
            </thead>
            <tbody>
              <tr className="border-b border-slate-100">
                <td className="py-1.5">Employee PF</td>
                <td className="py-1.5 text-right tabular-nums">{data.employeeFundBalance.toFixed(2)}</td>
              </tr>
              <tr className="border-b border-slate-100">
                <td className="py-1.5">Employer PF</td>
                <td className="py-1.5 text-right tabular-nums">{data.employerFundBalance.toFixed(2)}</td>
              </tr>
              <tr className="border-b border-slate-100">
                <td className="py-1.5">Pension (EPS)</td>
                <td className="py-1.5 text-right tabular-nums">{data.pensionFundBalance.toFixed(2)}</td>
              </tr>
            </tbody>
            <tfoot>
              <tr className="border-t-2 border-slate-300 text-base font-bold text-brand-forest">
                <td className="py-2">Total</td>
                <td className="py-2 text-right tabular-nums">
                  ₹{(data.employeeFundBalance + data.employerFundBalance + data.pensionFundBalance).toFixed(2)}
                </td>
              </tr>
            </tfoot>
          </table>

          <p className="mt-4 text-xs text-slate-400">
            Last diversion: {data.lastDiversionDate ? formatDate(data.lastDiversionDate) : 'none on record'} · Updated{' '}
            {formatDate(data.updatedAt, 'dd-MM-yyyy hh:mm a')}
          </p>
          <p className="mt-3 rounded-md bg-amber-50 p-2 text-xs text-amber-800">
            Opening balance and interest-posting line items are not modeled anywhere in the backend (CpfBalanceLedger
            only tracks current totals plus a diversions log) - this statement shows the closing 3-bucket balance only.
          </p>
        </Card>
      )}
    </div>
  )
}
