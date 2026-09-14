import { Fragment, useMemo, useState } from 'react'
import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { ChevronDown, ChevronLeft, ChevronRight, ChevronUp, HelpCircle, MessageSquareWarning, Printer } from 'lucide-react'
import { apiClient } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../components/common/ui'
import { PrintableHeader } from '../../components/common/PrintableHeader'
import { RaiseDisputeModal } from './RaiseDisputeModal'
import type {
  CpfDisputeResponse,
  CpfDisputeStatus,
  CpfLedgerEntryType,
  CpfPassbookSummaryResponse,
  CpfPassbookTransactionDetailResponse,
  CpfPassbookTransactionSummaryResponse,
  Page,
} from '../../types/api'

const PAGE_SIZE = 10

function currentFinancialYearOptions(): string[] {
  const now = new Date()
  const startYear = now.getMonth() >= 3 ? now.getFullYear() : now.getFullYear() - 1
  return Array.from({ length: 6 }, (_, i) => `${startYear - i}-${startYear - i + 1}`)
}

function disputeBadgeTone(status: CpfDisputeStatus): 'neutral' | 'success' | 'warning' | 'danger' | 'brand' {
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

function SummaryCard({ label, amount, emphasize = false, hint }: { label: string; amount: number; emphasize?: boolean; hint?: string }) {
  return (
    <Card className={emphasize ? 'border-brand-forest/40' : ''}>
      <div className="flex items-center gap-1 text-xs text-slate-500">
        {label}
        {hint && (
          <span title={hint}>
            <HelpCircle size={12} className="text-slate-300" />
          </span>
        )}
      </div>
      <div className={`mt-1 text-xl font-semibold tabular-nums sm:text-2xl ${emphasize ? 'text-brand-forest' : 'text-slate-800'}`}>
        ₹{amount.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
      </div>
    </Card>
  )
}

function ContributionRow({ label, amount, hint }: { label: string; amount: number; hint?: string }) {
  return (
    <div>
      <div className="flex items-center gap-1 text-xs text-slate-500">
        {label}
        {hint && (
          <span title={hint}>
            <HelpCircle size={11} className="text-slate-300" />
          </span>
        )}
      </div>
      <div className="mt-0.5 text-sm font-medium tabular-nums text-slate-800">₹{amount.toFixed(2)}</div>
    </div>
  )
}

function TransactionDetailPanel({ transactionId }: { transactionId: number }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['cpf-passbook-transaction-detail', transactionId],
    queryFn: async () =>
      (await apiClient.get<CpfPassbookTransactionDetailResponse>(`/v1/ess/cpf/passbook/transactions/${transactionId}`)).data,
  })

  if (isLoading) return <LoadingState label="Loading transaction detail..." />
  if (isError || !data) return <ErrorState message="Could not load this transaction's details." />

  const hasContribution = data.contribution.total !== 0
  const hasAdjustment =
    data.adjustment.employeeDebit !== 0 ||
    data.adjustment.employerDebit !== 0 ||
    data.adjustment.vpfDebit !== 0 ||
    data.adjustment.loanSanctioned !== 0 ||
    data.adjustment.loanPrincipalRepaid !== 0 ||
    data.adjustment.loanInterestRepaid !== 0 ||
    data.adjustment.nonRefundableWithdrawal !== 0

  return (
    <div className="grid grid-cols-1 gap-4 border-t border-slate-100 pt-4 sm:grid-cols-2 lg:grid-cols-4">
      {hasContribution && (
        <div className="space-y-2">
          <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">Contribution</div>
          <ContributionRow label="Employee CPF" amount={data.contribution.employeeCpf} />
          <ContributionRow label="Employer CPF" amount={data.contribution.employerCpf} />
          <ContributionRow label="EPS (Pension)" amount={data.contribution.eps} hint="Employer's Pension Fund share for this month - a separate statutory account, not part of the CPF Trust corpus balance above." />
          <ContributionRow label="VPF" amount={data.contribution.vpf} />
        </div>
      )}
      {hasAdjustment && (
        <div className="space-y-2">
          <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">Diversion / Loan</div>
          {data.adjustment.loanSanctioned !== 0 && <ContributionRow label="Loan Sanctioned" amount={data.adjustment.loanSanctioned} />}
          {data.adjustment.loanPrincipalRepaid !== 0 && <ContributionRow label="Principal Repaid" amount={data.adjustment.loanPrincipalRepaid} />}
          {data.adjustment.loanInterestRepaid !== 0 && <ContributionRow label="Interest Repaid" amount={data.adjustment.loanInterestRepaid} />}
          {data.adjustment.nonRefundableWithdrawal !== 0 && <ContributionRow label="Non-Refundable Withdrawal" amount={data.adjustment.nonRefundableWithdrawal} />}
        </div>
      )}
      <div className="space-y-2">
        <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">Running Balance After</div>
        <ContributionRow label="Employee" amount={data.balance.employeeBalance} />
        <ContributionRow label="Employer" amount={data.balance.employerBalance} />
        <ContributionRow label="VPF" amount={data.balance.vpfBalance} />
        <ContributionRow label="Total" amount={data.balance.totalBalance} />
      </div>
      <div className="space-y-2">
        <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">Source</div>
        <div className="text-sm text-slate-700">{data.source}</div>
        {data.referenceDocNo && <div className="text-xs text-slate-500">Ref: {data.referenceDocNo}</div>}
        {data.isProvisionalRate && <div className="text-xs text-amber-700">Provisional rate applied ({data.rateApplied}%, FY {data.rateSourceFinYear})</div>}
        {data.dispute && (
          <div className="pt-1">
            <Badge tone={disputeBadgeTone(data.dispute.status)}>{data.dispute.disputeNumber} · {data.dispute.status.replaceAll('_', ' ')}</Badge>
          </div>
        )}
      </div>
    </div>
  )
}

/** Route: /pf-statement - GET /api/v1/ess/cpf/passbook/... - employeeId always derived from the JWT server-side; every figure shown here (including EPS) is read from CpfSelfServicePassbookController's authoritative response, never computed in this component. */
export function PfStatementPage() {
  const { employeeId } = useAuth()
  const queryClient = useQueryClient()
  const years = useMemo(() => currentFinancialYearOptions(), [])
  const [finYear, setFinYear] = useState(years[0])
  const [typeFilter, setTypeFilter] = useState<CpfLedgerEntryType | ''>('')
  const [page, setPage] = useState(0)
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [disputeTarget, setDisputeTarget] = useState<CpfPassbookTransactionSummaryResponse | null>(null)
  const [showMyDisputes, setShowMyDisputes] = useState(false)

  function changeFinYear(next: string) {
    setFinYear(next)
    setPage(0)
    setExpandedId(null)
  }

  const summaryQuery = useQuery({
    queryKey: ['cpf-passbook-summary', employeeId, finYear],
    queryFn: async () => (await apiClient.get<CpfPassbookSummaryResponse>('/v1/ess/cpf/passbook/summary', { params: { finYear } })).data,
    enabled: employeeId !== null,
    retry: false,
  })

  const transactionsQuery = useQuery({
    queryKey: ['cpf-passbook-transactions', employeeId, finYear, typeFilter, page],
    queryFn: async () =>
      (
        await apiClient.get<Page<CpfPassbookTransactionSummaryResponse>>('/v1/ess/cpf/passbook/transactions', {
          params: { finYear, type: typeFilter || undefined, page, size: PAGE_SIZE },
        })
      ).data,
    enabled: employeeId !== null,
    retry: false,
    placeholderData: (previous) => previous,
  })

  const myDisputesQuery = useQuery({
    queryKey: ['cpf-my-disputes', employeeId],
    queryFn: async () => (await apiClient.get<CpfDisputeResponse[]>('/v1/ess/cpf/disputes')).data,
    enabled: employeeId !== null && showMyDisputes,
  })

  const withdrawMutation = useMutation({
    mutationFn: async (disputeId: number) => (await apiClient.post(`/v1/ess/cpf/disputes/${disputeId}/withdraw`)).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cpf-my-disputes'] })
      queryClient.invalidateQueries({ queryKey: ['cpf-passbook-transactions'] })
    },
  })

  const forbidden = isAxiosError(summaryQuery.error) && summaryQuery.error.response?.status === 403
  const summary = summaryQuery.data
  const transactions = transactionsQuery.data

  return (
    <div>
      <PageHeader
        title="CPF Passbook"
        description="Your CPF Trust ledger, EPS contribution, and running balance - straight from the authoritative CPF Trust records."
        actions={
          summary && (
            <PrimaryButton onClick={() => window.print()}>
              <Printer size={15} /> Print
            </PrimaryButton>
          )
        }
      />

      <Card className="mb-6 no-print">
        <div className="flex flex-wrap items-end gap-4">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Financial Year</label>
            <select
              value={finYear}
              onChange={(e) => changeFinYear(e.target.value)}
              className="w-40 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none"
            >
              {years.map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </select>
          </div>
          <SecondaryButton onClick={() => setShowMyDisputes((v) => !v)}>
            <MessageSquareWarning size={15} /> {showMyDisputes ? 'Hide' : 'View'} My Disputes
          </SecondaryButton>
        </div>
      </Card>

      {showMyDisputes && (
        <Card className="mb-6 no-print">
          <h2 className="mb-3 text-sm font-semibold text-slate-700">My Disputes</h2>
          {myDisputesQuery.isLoading && <LoadingState label="Loading your disputes..." />}
          {myDisputesQuery.data && myDisputesQuery.data.length === 0 && <EmptyState message="You haven't raised any CPF transaction disputes." />}
          {myDisputesQuery.data && myDisputesQuery.data.length > 0 && (
            <div className="space-y-2">
              {myDisputesQuery.data.map((d) => (
                <div key={d.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-slate-100 p-2 text-sm">
                  <div>
                    <span className="font-medium">{d.disputeNumber}</span>{' '}
                    <span className="text-slate-500">· {d.disputeCategory.replaceAll('_', ' ')}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge tone={disputeBadgeTone(d.status)}>{d.status.replaceAll('_', ' ')}</Badge>
                    {d.status === 'OPEN' && (
                      <SecondaryButton onClick={() => withdrawMutation.mutate(d.id)} disabled={withdrawMutation.isPending}>
                        Withdraw
                      </SecondaryButton>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>
      )}

      {summaryQuery.isLoading && <LoadingState label="Loading your CPF passbook..." />}
      {forbidden && <ErrorState message="Your account has no employee record linked - the CPF passbook can't be resolved." />}
      {summaryQuery.isError && !forbidden && <ErrorState message="Could not load your CPF passbook." />}

      {summary && (
        <>
          <Card className="print-sheet mb-6">
            <PrintableHeader documentTitle="CPF Passbook" subtitle={`Financial Year ${summary.finYear}`} />

            {summary.isProvisionalRate && (
              <div className="mb-4 rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
                Notice under Para 60(2), EPF Scheme: the interest rate for FY {summary.finYear} has not been notified
                yet. Figures reflect the provisional rate of {summary.rateApplied}% p.a. (notified for FY{' '}
                {summary.rateSourceFinYear}).
              </div>
            )}

            <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <SummaryCard label="Audited Ledger Balance" amount={summary.auditedBalance} />
              <SummaryCard label="Accrued Interest (FYTD)" amount={summary.accruedInterest} />
              <SummaryCard label="Effective Total Corpus" amount={summary.effectiveCorpus} emphasize />
              <SummaryCard label="Outstanding Refundable Loan" amount={summary.outstandingLoan} />
            </div>

            <div className="rounded-lg border border-slate-100 p-4">
              <h3 className="mb-3 text-xs font-semibold uppercase tracking-wide text-slate-400">
                Contribution Summary - FY {summary.finYear}
              </h3>
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-5">
                <ContributionRow label="Employee CPF" amount={summary.contributionSummary.employeeContribution} />
                <ContributionRow label="Employer CPF" amount={summary.contributionSummary.employerContribution} />
                <ContributionRow
                  label="EPS (Pension)"
                  amount={summary.contributionSummary.epsContribution}
                  hint="Employer's Pension Fund contribution, credited to a separate statutory pension account - not part of the CPF Trust corpus balance shown above."
                />
                <ContributionRow label="VPF" amount={summary.contributionSummary.vpfContribution} />
                <ContributionRow label="Total" amount={summary.contributionSummary.totalContribution} />
              </div>
            </div>
          </Card>

          <Card className="no-print">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-sm font-semibold text-slate-700">Transactions</h2>
              <select
                value={typeFilter}
                onChange={(e) => {
                  setTypeFilter(e.target.value as CpfLedgerEntryType | '')
                  setPage(0)
                }}
                className="rounded-md border border-slate-300 px-2 py-1.5 text-xs focus:border-brand-forest focus:outline-none"
              >
                <option value="">All transaction types</option>
                <option value="PAYROLL_MONTHLY">Monthly Payroll Contribution</option>
                <option value="DA_ARREAR">DA Arrear</option>
                <option value="LOAN_WITHDRAWAL">Loan Withdrawal</option>
                <option value="LOAN_REPAYMENT">Loan Repayment</option>
                <option value="TRANSFER_IN">Incoming Fund Transfer</option>
                <option value="ANNUAL_INTEREST">Annual Interest</option>
                <option value="ANNUAL_INTEREST_REVERSAL">Interest Reversal</option>
                <option value="INTERIM_SETTLEMENT_INTEREST">Interim Settlement Interest</option>
                <option value="FINAL_SETTLEMENT">Final Settlement</option>
              </select>
            </div>

            {transactionsQuery.isLoading && <LoadingState label="Loading transactions..." />}
            {transactionsQuery.isError && <ErrorState message="Could not load your CPF transactions." />}
            {transactions && transactions.content.length === 0 && (
              <EmptyState message="No CPF transactions for this financial year." />
            )}

            {transactions && transactions.content.length > 0 && (
              <>
                {/* Desktop: compact table, no horizontal scroll needed */}
                <div className="hidden sm:block">
                  <table className="w-full border-collapse text-sm">
                    <thead>
                      <tr className="border-b border-slate-200 text-left text-[11px] uppercase tracking-wide text-slate-400">
                        <th className="py-2 pr-3">Period</th>
                        <th className="py-2 pr-3">Type</th>
                        <th className="py-2 pr-3 text-right">Amount</th>
                        <th className="py-2 pr-3">Dispute</th>
                        <th className="py-2 pr-3 text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {transactions.content.map((t) => (
                        <Fragment key={t.id}>
                          <tr className="border-b border-slate-100">
                            <td className="py-2 pr-3 whitespace-nowrap">{t.displayPeriod}</td>
                            <td className="py-2 pr-3 whitespace-nowrap">{t.transactionType.replaceAll('_', ' ')}</td>
                            <td className={`py-2 pr-3 text-right tabular-nums font-medium ${t.isCredit ? 'text-emerald-700' : 'text-rose-700'}`}>
                              {t.isCredit ? '+' : '-'}₹{t.displayAmount.toFixed(2)}
                            </td>
                            <td className="py-2 pr-3">
                              {t.dispute ? (
                                <Badge tone={disputeBadgeTone(t.dispute.status)}>{t.dispute.status.replaceAll('_', ' ')}</Badge>
                              ) : (
                                <span className="text-slate-300">-</span>
                              )}
                            </td>
                            <td className="py-2 pr-3 text-right">
                              <div className="flex items-center justify-end gap-2">
                                {!t.dispute && (
                                  <button
                                    type="button"
                                    onClick={() => setDisputeTarget(t)}
                                    className="text-xs font-medium text-brand-forest hover:underline"
                                  >
                                    Raise Dispute
                                  </button>
                                )}
                                <button
                                  type="button"
                                  onClick={() => setExpandedId(expandedId === t.id ? null : t.id)}
                                  className="rounded-md border border-slate-200 p-1 text-slate-500 hover:bg-slate-50"
                                  aria-label="Toggle details"
                                >
                                  {expandedId === t.id ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                                </button>
                              </div>
                            </td>
                          </tr>
                          {expandedId === t.id && (
                            <tr>
                              <td colSpan={5} className="bg-slate-50/60 px-2 py-3">
                                <TransactionDetailPanel transactionId={t.id} />
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* Mobile: cards */}
                <div className="space-y-3 sm:hidden">
                  {transactions.content.map((t) => (
                    <div key={t.id} className="rounded-lg border border-slate-200 p-3">
                      <div className="flex items-center justify-between">
                        <div className="text-sm font-medium text-slate-800">{t.displayPeriod}</div>
                        <div className={`text-sm font-semibold tabular-nums ${t.isCredit ? 'text-emerald-700' : 'text-rose-700'}`}>
                          {t.isCredit ? '+' : '-'}₹{t.displayAmount.toFixed(2)}
                        </div>
                      </div>
                      <div className="mt-1 text-xs text-slate-500">{t.transactionType.replaceAll('_', ' ')}</div>
                      {t.dispute && (
                        <div className="mt-2">
                          <Badge tone={disputeBadgeTone(t.dispute.status)}>{t.dispute.status.replaceAll('_', ' ')}</Badge>
                        </div>
                      )}
                      <div className="mt-3 flex items-center justify-between">
                        {!t.dispute ? (
                          <button type="button" onClick={() => setDisputeTarget(t)} className="text-xs font-medium text-brand-forest hover:underline">
                            Raise Dispute
                          </button>
                        ) : (
                          <span />
                        )}
                        <button
                          type="button"
                          onClick={() => setExpandedId(expandedId === t.id ? null : t.id)}
                          className="flex items-center gap-1 text-xs text-slate-500"
                        >
                          {expandedId === t.id ? 'Hide details' : 'View details'}
                          {expandedId === t.id ? <ChevronUp size={13} /> : <ChevronDown size={13} />}
                        </button>
                      </div>
                      {expandedId === t.id && (
                        <div className="mt-3">
                          <TransactionDetailPanel transactionId={t.id} />
                        </div>
                      )}
                    </div>
                  ))}
                </div>

                <div className="mt-4 flex items-center justify-between text-xs text-slate-500">
                  <span>
                    Page {transactions.number + 1} of {Math.max(1, transactions.totalPages)} · {transactions.totalElements} transaction
                    {transactions.totalElements === 1 ? '' : 's'}
                  </span>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      disabled={transactions.first}
                      onClick={() => setPage((p) => Math.max(0, p - 1))}
                      className="rounded-md border border-slate-300 p-1.5 disabled:opacity-40"
                      aria-label="Previous page"
                    >
                      <ChevronLeft size={13} />
                    </button>
                    <button
                      type="button"
                      disabled={transactions.last}
                      onClick={() => setPage((p) => p + 1)}
                      className="rounded-md border border-slate-300 p-1.5 disabled:opacity-40"
                      aria-label="Next page"
                    >
                      <ChevronRight size={13} />
                    </button>
                  </div>
                </div>
              </>
            )}
          </Card>
        </>
      )}

      {disputeTarget && <RaiseDisputeModal transaction={disputeTarget} onClose={() => setDisputeTarget(null)} />}
    </div>
  )
}
