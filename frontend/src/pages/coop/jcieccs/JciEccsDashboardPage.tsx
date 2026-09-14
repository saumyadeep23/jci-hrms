import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiClient } from '../../../api/client'
import { Card, ErrorState, LoadingState, PageHeader } from '../../../components/common/ui'
import { describeApiError } from '../../../lib/apiError'
import type { JciEccsLoanResponse, JciEccsMemberResponse, JciEccsReconciliationResponse, JciEccsSettlementResponse } from '../../../types/api'

const inr = (n: number) => `₹${n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

function Kpi({ label, value, tone = 'default' }: { label: string; value: string; tone?: 'default' | 'danger' | 'warning' }) {
  return (
    <Card className="p-4">
      <p className="text-xs text-slate-500">{label}</p>
      <p
        className={`mt-1 text-xl font-semibold ${
          tone === 'danger' ? 'text-red-600' : tone === 'warning' ? 'text-amber-600' : 'text-slate-800'
        }`}
      >
        {value}
      </p>
    </Card>
  )
}

function QuickAction({ to, label }: { to: string; label: string }) {
  return (
    <Link
      to={to}
      className="rounded-md border border-slate-200 bg-white px-4 py-3 text-sm font-medium text-slate-700 shadow-sm hover:border-brand-forest hover:text-brand-forest"
    >
      {label}
    </Link>
  )
}

/** Route: /jcieccs - every figure here is derived from a real, already-existing API response (member
 * list, active loan list, unresolved reconciliation exceptions, settlement list) - never mocked, and
 * never a metric this page can't actually back with a live query (spec section 50). Thrift/payroll
 * "current month" collection figures are deliberately omitted - they need a specific payroll-run
 * selection, which belongs on the Payroll Demand Schedules / Reconciliation pages, not a guessed default
 * here. */
export function JciEccsDashboardPage() {
  const membersQuery = useQuery({
    queryKey: ['jcieccs-members'],
    queryFn: async () => (await apiClient.get<JciEccsMemberResponse[]>('/jcieccs/members')).data,
  })
  const loansQuery = useQuery({
    queryKey: ['jcieccs-loans', 'ACTIVE'],
    queryFn: async () => (await apiClient.get<JciEccsLoanResponse[]>('/jcieccs/loans', { params: { status: 'ACTIVE' } })).data,
  })
  const exceptionsQuery = useQuery({
    queryKey: ['jcieccs-reconciliation-exceptions'],
    queryFn: async () => (await apiClient.get<JciEccsReconciliationResponse[]>('/jcieccs/reconciliation')).data,
  })
  const settlementsQuery = useQuery({
    queryKey: ['jcieccs-settlements'],
    queryFn: async () => (await apiClient.get<JciEccsSettlementResponse[]>('/jcieccs/settlements')).data,
  })

  const isLoading = membersQuery.isLoading || loansQuery.isLoading || exceptionsQuery.isLoading || settlementsQuery.isLoading
  const isError = membersQuery.isError || loansQuery.isError || exceptionsQuery.isError || settlementsQuery.isError

  const activeMembers = (membersQuery.data ?? []).filter((m) => m.membershipStatus === 'ACTIVE').length
  const termLoans = (loansQuery.data ?? []).filter((l) => l.productCode === 'TERM')
  const emergencyLoans = (loansQuery.data ?? []).filter((l) => l.productCode === 'EMERGENCY')
  const teOutstanding = termLoans.reduce((sum, l) => sum + l.outstandingPrincipal, 0)
  const emOutstanding = emergencyLoans.reduce((sum, l) => sum + l.outstandingPrincipal, 0)
  const openExceptions = (exceptionsQuery.data ?? []).length
  const pendingSettlements = (settlementsQuery.data ?? []).filter(
    (s) => s.exitClearanceRequestId !== null && s.exitClearanceItemStatus !== 'CLEARED',
  ).length

  return (
    <div>
      <PageHeader title="JCI Employees' Co-operative Credit Society" description="Operational overview - membership, loans, recovery and settlement" />

      {isLoading && <LoadingState label="Loading dashboard..." />}
      {isError && <ErrorState message={describeApiError(membersQuery.error ?? loansQuery.error, 'Could not load dashboard data.')} />}

      {!isLoading && !isError && (
        <>
          <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
            <Kpi label="Active Members" value={String(activeMembers)} />
            <Kpi label="Active Term Loans" value={String(termLoans.length)} />
            <Kpi label="Active Emergency Loans" value={String(emergencyLoans.length)} />
            <Kpi label="Total TE Outstanding" value={inr(teOutstanding)} />
            <Kpi label="Total EM Outstanding" value={inr(emOutstanding)} />
            <Kpi label="Recovery Exceptions" value={String(openExceptions)} tone={openExceptions > 0 ? 'danger' : 'default'} />
            <Kpi
              label="Pending Settlement / No-Dues"
              value={String(pendingSettlements)}
              tone={pendingSettlements > 0 ? 'warning' : 'default'}
            />
          </div>

          <p className="mb-2 text-xs font-medium text-slate-600">Quick Actions</p>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6">
            <QuickAction to="/jcieccs/members/directory" label="View Members" />
            <QuickAction to="/jcieccs/loans/apply" label="New Loan" />
            <QuickAction to="/jcieccs/loans/cash-repayment" label="Cash Repayment" />
            <QuickAction to="/jcieccs/payroll-recovery/demands" label="Payroll Demand" />
            <QuickAction to="/jcieccs/payroll-recovery/reconciliation" label="Reconciliation" />
            <QuickAction to="/jcieccs/settlement/no-dues" label="Settlement Clearance" />
          </div>
        </>
      )}
    </div>
  )
}
