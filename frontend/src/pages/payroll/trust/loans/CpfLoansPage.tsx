import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { apiClient } from '../../../../api/client'
import { Card, PageHeader, PrimaryButton } from '../../../../components/common/ui'
import { ApplyCpfLoanModal } from './ApplyCpfLoanModal'
import { CpfLoansQueueTable } from './CpfLoansQueueTable'
import type { CpfLoanApplicationResponse, Page } from '../../../../types/api'

/** April-March FY label matching the backend's own convention (IncomingFundTransferService.financialYearFor). */
function currentFinancialYear(): string {
  const now = new Date()
  const startYear = now.getMonth() >= 3 ? now.getFullYear() : now.getFullYear() - 1
  return `${startYear}-${startYear + 1}`
}

/** Route: /payroll/trust/loans - Section 4 of the CPF Trust ingestion task. */
export function CpfLoansPage() {
  const [applying, setApplying] = useState(false)

  const disbursedQuery = useQuery({
    queryKey: ['cpf-loan-applications-metrics', 'DISBURSED'],
    queryFn: async () =>
      (await apiClient.get<Page<CpfLoanApplicationResponse>>('/v1/payroll/trust/loans/all', { params: { status: 'DISBURSED', size: 500 } })).data
        .content,
  })

  const finYear = currentFinancialYear()
  const activeLoansCount = disbursedQuery.data?.length ?? 0
  const totalDisbursedThisFy = useMemo(() => {
    return (disbursedQuery.data ?? [])
      .filter((loan) => loan.disbursedAt && isInFinancialYear(loan.disbursedAt, finYear))
      .reduce((sum, loan) => sum + loan.sanctionedAmount, 0)
  }, [disbursedQuery.data, finYear])
  const recoveriesDueThisMonth = useMemo(
    () => (disbursedQuery.data ?? []).reduce((sum, loan) => sum + loan.monthlyRecoveryPrincipal, 0),
    [disbursedQuery.data],
  )

  return (
    <div>
      <PageHeader
        title="CPF Loans & Advances"
        description="CPF Trust loan/withdrawal origination, sanction, and disbursement"
        actions={
          <PrimaryButton onClick={() => setApplying(true)}>
            <Plus size={15} /> Apply for Loan
          </PrimaryButton>
        }
      />

      <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <Card>
          <p className="text-xs text-slate-400">Active Loans</p>
          <p className="mt-1 text-2xl font-semibold text-slate-800">{activeLoansCount}</p>
        </Card>
        <Card>
          <p className="text-xs text-slate-400">Total Disbursed (FY {finYear})</p>
          <p className="mt-1 text-2xl font-semibold text-slate-800">{totalDisbursedThisFy.toFixed(2)}</p>
        </Card>
        <Card>
          <p className="text-xs text-slate-400">Recoveries Due This Month</p>
          <p className="mt-1 text-2xl font-semibold text-slate-800">{recoveriesDueThisMonth.toFixed(2)}</p>
        </Card>
      </div>

      <CpfLoansQueueTable />

      {applying && <ApplyCpfLoanModal onClose={() => setApplying(false)} />}
    </div>
  )
}

function isInFinancialYear(isoDateTime: string, finYear: string): boolean {
  const [startYearStr] = finYear.split('-')
  const startYear = Number(startYearStr)
  const date = new Date(isoDateTime)
  const fyStart = new Date(startYear, 3, 1)
  const fyEnd = new Date(startYear + 1, 2, 31, 23, 59, 59)
  return date >= fyStart && date <= fyEnd
}
