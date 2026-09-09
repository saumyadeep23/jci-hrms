import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Banknote, CheckCircle2, HandCoins, XCircle } from 'lucide-react'
import { apiClient } from '../../../../api/client'
import { useToast } from '../../../../components/common/ToastProvider'
import { Modal } from '../../../../components/common/Modal'
import { DatePicker } from '../../../../components/common/DatePicker'
import { describeApiError } from '../../../../lib/apiError'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PrimaryButton, SecondaryButton } from '../../../../components/common/ui'
import type {
  CpfLoanApplicationResponse,
  CpfLoanApplicationStatus,
  CpfLoanSanctionRequest,
  CpfLoanSettlementMode,
  CpfLoanSettlementQuoteResponse,
  CpfLoanSettlementRequest,
  Page,
  RejectRemarksRequest,
} from '../../../../types/api'

const TABS: { key: string; label: string; status: CpfLoanApplicationStatus | null }[] = [
  { key: 'pending-sanction', label: 'Pending Sanction', status: 'APPLIED' },
  { key: 'pending-disbursement', label: 'Pending Disbursement', status: 'SANCTIONED' },
  { key: 'active-recoveries', label: 'Active Recoveries', status: 'DISBURSED' },
  { key: 'closed', label: 'Closed', status: 'CLOSED' },
]

const SETTLEMENT_MODES: CpfLoanSettlementMode[] = ['CASH', 'CHEQUE', 'DEMAND_DRAFT', 'NEFT', 'RTGS', 'OTHER']

function statusTone(status: CpfLoanApplicationStatus): 'warning' | 'success' | 'neutral' | 'danger' {
  switch (status) {
    case 'APPLIED': return 'warning'
    case 'SANCTIONED': return 'warning'
    case 'DISBURSED': return 'success'
    case 'REJECTED': return 'danger'
    default: return 'neutral'
  }
}

/** "DISBURSED (Principal Phase)" / "DISBURSED (Interest Phase)" / plain status otherwise - Head 30 then Head 31 payroll recovery, see CpfLedgerSyncService. */
function statusLabel(loan: CpfLoanApplicationResponse): string {
  if (loan.status === 'DISBURSED') {
    return loan.recoveryPhase === 'INTEREST' ? 'DISBURSED (Interest Phase)' : 'DISBURSED (Principal Phase)'
  }
  return loan.status
}

/** Base CPF rate + notified loan markup (typically 8.25% + 1.00% = 9.25%) - just a client-side preview; the backend resolves the real notified rate itself via CpfRateResolutionService and recomputes authoritatively. */
const PREVIEW_BASE_RATE = 8.25
const PREVIEW_LOAN_MARKUP = 1.0

function SanctionModal({ loan, onClose }: { loan: CpfLoanApplicationResponse; onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [sanctionedAmount, setSanctionedAmount] = useState(String(loan.appliedAmount))
  const [sanctionOrderNo, setSanctionOrderNo] = useState('')
  const [sanctionDate, setSanctionDate] = useState('')
  const [totalInstallments, setTotalInstallments] = useState(loan.totalInstallments)
  const [interestInstallments, setInterestInstallments] = useState(loan.totalInstallments)

  const previewRate = PREVIEW_BASE_RATE + PREVIEW_LOAN_MARKUP
  const interestPreview = useMemo(() => {
    const amount = Number(sanctionedAmount)
    if (!Number.isFinite(amount) || amount <= 0 || totalInstallments <= 0 || interestInstallments <= 0) return null
    // (installments+1) * amount * rate / 2400 - the standard GPF/CPF advance interest formula, matching
    // CpfLoanApplicationService.computeTotalInterest() exactly.
    const totalInterest = ((totalInstallments + 1) * amount * previewRate) / 2400
    return { totalInterest, monthlyInterest: totalInterest / interestInstallments }
  }, [sanctionedAmount, totalInstallments, interestInstallments, previewRate])

  const mutation = useMutation({
    mutationFn: async () => {
      const payload: CpfLoanSanctionRequest = {
        sanctionedAmount: Number(sanctionedAmount),
        sanctionOrderNo,
        sanctionDate,
        totalInstallments,
        interestInstallments,
      }
      return (await apiClient.put(`/v1/payroll/trust/loans/${loan.id}/sanction`, payload)).data
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'Loan sanctioned.' })
      queryClient.invalidateQueries({ queryKey: ['cpf-loan-applications'] })
      onClose()
    },
  })

  return (
    <Modal title={`Sanction - ${loan.loanApplicationNo}`} onClose={onClose}>
      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault()
          mutation.mutate()
        }}
      >
        <p className="text-sm text-slate-600">
          {loan.employeeName} ({loan.employeeCode}) - Applied {loan.appliedAmount.toFixed(2)}
        </p>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Final Sanctioned Amount</label>
          <input
            required
            type="number"
            min={0}
            step="0.01"
            value={sanctionedAmount}
            onChange={(e) => setSanctionedAmount(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Sanction Order No.</label>
            <input
              required
              value={sanctionOrderNo}
              onChange={(e) => setSanctionOrderNo(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Sanction Date</label>
            <DatePicker value={sanctionDate} onChange={setSanctionDate} />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Principal Installments (Head 30)</label>
            <input
              required
              type="number"
              min={1}
              value={totalInstallments}
              onChange={(e) => setTotalInstallments(Number(e.target.value))}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Interest Installments (Head 31)</label>
            <input
              required
              type="number"
              min={1}
              value={interestInstallments}
              onChange={(e) => setInterestInstallments(Number(e.target.value))}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
        </div>

        <div className="rounded-md border border-slate-200 bg-slate-50 p-3 text-sm">
          <p className="text-xs font-medium text-slate-500">
            Interest Schedule @ {PREVIEW_BASE_RATE.toFixed(2)}% + {PREVIEW_LOAN_MARKUP.toFixed(2)}% = {previewRate.toFixed(2)}% p.a.
          </p>
          {interestPreview ? (
            <div className="mt-1 space-y-0.5">
              <p>
                <span className="text-slate-400">Total Interest: </span>
                <span className="font-medium text-slate-800">{interestPreview.totalInterest.toFixed(2)}</span>
              </p>
              <p>
                <span className="text-slate-400">Monthly Interest Recovery: </span>
                <span className="font-medium text-slate-800">{interestPreview.monthlyInterest.toFixed(2)}</span>
              </p>
            </div>
          ) : (
            <p className="mt-1 text-xs text-slate-400">Enter an amount and installment counts to preview the interest schedule.</p>
          )}
          <p className="mt-1 text-[11px] text-slate-400">
            Preview only - the backend resolves the actually-notified CPF rate for the sanction date's financial year and computes
            authoritatively.
          </p>
        </div>

        <div className="flex gap-2">
          <SecondaryButton type="button" onClick={onClose} className="flex-1 justify-center">
            Cancel
          </SecondaryButton>
          <PrimaryButton type="submit" disabled={mutation.isPending || !sanctionDate} className="flex-1 justify-center">
            <CheckCircle2 size={14} /> Confirm Sanction
          </PrimaryButton>
        </div>
        {mutation.isError && <ErrorState message={describeApiError(mutation.error, 'Could not sanction the loan.')} />}
      </form>
    </Modal>
  )
}

function DisburseModal({ loan, onClose }: { loan: CpfLoanApplicationResponse; onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: async () => (await apiClient.put(`/v1/payroll/trust/loans/${loan.id}/disburse`)).data,
    onSuccess: () => {
      show({ tone: 'success', message: 'Loan disbursed - CPF Trust ledger debited.' })
      queryClient.invalidateQueries({ queryKey: ['cpf-loan-applications'] })
      onClose()
    },
  })

  return (
    <Modal title={`Disburse - ${loan.loanApplicationNo}`} onClose={onClose}>
      <div className="mb-4 flex items-start gap-2 rounded-md bg-amber-50 p-3 text-sm text-amber-800">
        <AlertTriangle size={16} className="mt-0.5 shrink-0" />
        <p>
          This immediately debits {loan.sanctionedAmount.toFixed(2)} from {loan.employeeName}'s CPF Trust ledger and schedules monthly
          payroll deductions (Head 30) starting from the next payroll cycle. This cannot be undone from here.
        </p>
      </div>
      <div className="flex gap-2">
        <SecondaryButton onClick={onClose} className="flex-1 justify-center">
          Cancel
        </SecondaryButton>
        <PrimaryButton onClick={() => mutation.mutate()} disabled={mutation.isPending} className="flex-1 justify-center">
          <Banknote size={14} /> Confirm Disbursement
        </PrimaryButton>
      </div>
      {mutation.isError && <ErrorState message={describeApiError(mutation.error, 'Could not disburse the loan.')} />}
    </Modal>
  )
}

/** Records a direct, out-of-payroll cash/instrument settlement against a DISBURSED loan - fetches the live quote (with early-foreclosure interest rebate) up front. */
function SettleCashModal({ loan, onClose }: { loan: CpfLoanApplicationResponse; onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [principalPaid, setPrincipalPaid] = useState(String(loan.outstandingBalance))
  const [interestPaid, setInterestPaid] = useState('')
  const [settlementType, setSettlementType] = useState<CpfLoanSettlementMode>('CASH')
  const [instrumentOrChallanNo, setInstrumentOrChallanNo] = useState('')
  const [instrumentDate, setInstrumentDate] = useState('')
  const [bankRealizationDate, setBankRealizationDate] = useState('')
  const [trustBankAccountCode, setTrustBankAccountCode] = useState('')
  const [remarks, setRemarks] = useState('')

  const quoteQuery = useQuery({
    queryKey: ['cpf-loan-settlement-quote', loan.id],
    queryFn: async () => (await apiClient.get<CpfLoanSettlementQuoteResponse>(`/v1/payroll/trust/loans/${loan.id}/settlement-quote`)).data,
  })

  const quote = quoteQuery.data
  const willForecloseEarly = quote !== undefined && Number(principalPaid) >= quote.outstandingBalance && quote.outstandingBalance > 0

  const mutation = useMutation({
    mutationFn: async () => {
      const payload: CpfLoanSettlementRequest = {
        principalPaid: Number(principalPaid || 0),
        interestPaid: Number(interestPaid || 0),
        settlementType,
        instrumentOrChallanNo,
        instrumentDate,
        bankRealizationDate,
        trustBankAccountCode,
        remarks: remarks || null,
      }
      return (await apiClient.post(`/v1/payroll/trust/loans/${loan.id}/settle-cash`, payload)).data
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'CPF loan settlement recorded.' })
      queryClient.invalidateQueries({ queryKey: ['cpf-loan-applications'] })
      onClose()
    },
  })

  return (
    <Modal title={`Record Direct Cash Settlement - ${loan.loanApplicationNo}`} onClose={onClose} maxWidthClassName="max-w-xl">
      {quoteQuery.isLoading && <p className="text-xs text-slate-400">Fetching settlement quote...</p>}
      {quoteQuery.isError && <ErrorState message="Could not load the settlement quote." />}

      {quote && (
        <div className="mb-4 rounded-md border border-slate-200 bg-slate-50 p-3 text-sm">
          <p className="text-xs font-medium text-slate-500">Live Settlement Quote ({quote.elapsedMonths} elapsed month(s))</p>
          <div className="mt-1 grid grid-cols-2 gap-x-4 gap-y-0.5">
            <p><span className="text-slate-400">Outstanding Principal: </span><span className="font-medium">{quote.outstandingBalance.toFixed(2)}</span></p>
            <p><span className="text-slate-400">Outstanding Interest: </span><span className="font-medium">{quote.outstandingInterest.toFixed(2)}</span></p>
            <p><span className="text-slate-400">Original Projected Interest: </span><span className="font-medium">{quote.originalProjectedInterest.toFixed(2)}</span></p>
            <p><span className="text-slate-400">Recomputed Statutory Interest: </span><span className="font-medium">{quote.recomputedStatutoryInterest.toFixed(2)}</span></p>
          </div>
          <p className="mt-2 text-emerald-700">
            Interest Rebate (if fully foreclosed): <span className="font-semibold">{quote.interestRebateAmount.toFixed(2)}</span>
          </p>
          <p className="mt-1 text-brand-forest">
            Net Payoff to Fully Close Today: <span className="font-semibold">{quote.netPayoffAmount.toFixed(2)}</span>
          </p>
        </div>
      )}

      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault()
          mutation.mutate()
        }}
      >
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Principal Paid</label>
            <input
              required
              type="number"
              min={0}
              step="0.01"
              value={principalPaid}
              onChange={(e) => setPrincipalPaid(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            {willForecloseEarly && <p className="mt-1 text-[11px] text-emerald-700">Fully forecloses the loan - interest rebate applies.</p>}
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Interest Paid</label>
            <input
              required
              type="number"
              min={0}
              step="0.01"
              value={interestPaid}
              onChange={(e) => setInterestPaid(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Settlement Mode</label>
            <select
              value={settlementType}
              onChange={(e) => setSettlementType(e.target.value as CpfLoanSettlementMode)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              {SETTLEMENT_MODES.map((mode) => (
                <option key={mode} value={mode}>{mode.replace(/_/g, ' ')}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Instrument / Challan No.</label>
            <input
              required
              value={instrumentOrChallanNo}
              onChange={(e) => setInstrumentOrChallanNo(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Instrument Date</label>
            <DatePicker value={instrumentDate} onChange={setInstrumentDate} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Bank Realization Date</label>
            <DatePicker value={bankRealizationDate} onChange={setBankRealizationDate} />
          </div>
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Trust Bank Account Code</label>
          <input
            required
            value={trustBankAccountCode}
            onChange={(e) => setTrustBankAccountCode(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Remarks</label>
          <textarea
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            rows={2}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>

        <div className="flex gap-2">
          <SecondaryButton type="button" onClick={onClose} className="flex-1 justify-center">
            Cancel
          </SecondaryButton>
          <PrimaryButton
            type="submit"
            disabled={mutation.isPending || !instrumentDate || !bankRealizationDate}
            className="flex-1 justify-center"
          >
            <HandCoins size={14} /> Record Settlement
          </PrimaryButton>
        </div>
        {mutation.isError && <ErrorState message={describeApiError(mutation.error, 'Could not record the settlement.')} />}
      </form>
    </Modal>
  )
}

function RejectLoanModal({ loan, onClose }: { loan: CpfLoanApplicationResponse; onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [remarks, setRemarks] = useState('')

  const mutation = useMutation({
    mutationFn: async () => {
      const payload: RejectRemarksRequest = { remarks }
      return (await apiClient.put(`/v1/payroll/trust/loans/${loan.id}/reject`, payload)).data
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'Loan application rejected.' })
      queryClient.invalidateQueries({ queryKey: ['cpf-loan-applications'] })
      onClose()
    },
  })

  return (
    <Modal title={`Reject - ${loan.loanApplicationNo}`} onClose={onClose}>
      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault()
          if (remarks.trim().length === 0) return
          mutation.mutate()
        }}
      >
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">
            Rejection Reason <span className="text-red-500">*</span>
          </label>
          <textarea
            required
            autoFocus
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            rows={3}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <div className="flex gap-2">
          <SecondaryButton type="button" onClick={onClose} className="flex-1 justify-center">
            Cancel
          </SecondaryButton>
          <PrimaryButton type="submit" disabled={mutation.isPending || remarks.trim().length === 0} className="flex-1 justify-center bg-red-600 hover:bg-red-700">
            <XCircle size={14} /> Confirm Rejection
          </PrimaryButton>
        </div>
        {mutation.isError && <ErrorState message={describeApiError(mutation.error, 'Could not reject the loan.')} />}
      </form>
    </Modal>
  )
}

/** Section 4 - filterable CPF loan queue with Sanction/Disburse/Reject actions. */
export function CpfLoansQueueTable() {
  const [tab, setTab] = useState(TABS[0].key)
  const [sanctioning, setSanctioning] = useState<CpfLoanApplicationResponse | null>(null)
  const [disbursing, setDisbursing] = useState<CpfLoanApplicationResponse | null>(null)
  const [rejecting, setRejecting] = useState<CpfLoanApplicationResponse | null>(null)
  const [settling, setSettling] = useState<CpfLoanApplicationResponse | null>(null)

  const activeTab = TABS.find((t) => t.key === tab) ?? TABS[0]

  const { data, isLoading, isError } = useQuery({
    queryKey: ['cpf-loan-applications', activeTab.status],
    queryFn: async () =>
      (
        await apiClient.get<Page<CpfLoanApplicationResponse>>('/v1/payroll/trust/loans/all', {
          params: { status: activeTab.status ?? undefined, size: 50 },
        })
      ).data,
  })

  return (
    <div>
      <div className="mb-3 flex flex-wrap gap-2">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setTab(t.key)}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${
              tab === t.key ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load CPF loan applications." />}
      {data && data.content.length === 0 && <EmptyState message={`No loans in "${activeTab.label}".`} />}

      <div className="overflow-x-auto">
        <div className="min-w-[900px] space-y-2">
          {data?.content.map((loan) => (
            <Card key={loan.id} className="p-3">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="font-medium text-slate-800">
                    {loan.loanApplicationNo} <span className="font-normal text-slate-400">- {loan.employeeName} ({loan.employeeCode})</span>
                  </p>
                  <p className="mt-0.5 text-xs text-slate-500">
                    {loan.loanType.replace(/_/g, ' ')} · {loan.purpose} · Applied {loan.appliedAmount.toFixed(2)}
                    {loan.sanctionedAmount > 0 && ` · Sanctioned ${loan.sanctionedAmount.toFixed(2)}`}
                    {loan.totalInstallments > 0 && ` · ${loan.recoveredInstallments}/${loan.totalInstallments} installments`}
                  </p>
                  {loan.status === 'REJECTED' && loan.rejectionRemarks && (
                    <p className="mt-1 text-xs text-red-600">Rejected: {loan.rejectionRemarks}</p>
                  )}
                </div>
                <div className="flex items-center gap-2">
                  {loan.status === 'DISBURSED' && (
                    <span className="text-xs text-slate-500">Outstanding: <span className="font-medium text-slate-800">{loan.outstandingBalance.toFixed(2)}</span></span>
                  )}
                  <Badge tone={statusTone(loan.status)}>{statusLabel(loan)}</Badge>
                  {loan.status === 'APPLIED' && (
                    <>
                      <SecondaryButton onClick={() => setRejecting(loan)} className="border-red-300 text-red-600 hover:bg-red-50">
                        Reject
                      </SecondaryButton>
                      <PrimaryButton onClick={() => setSanctioning(loan)}>Sanction</PrimaryButton>
                    </>
                  )}
                  {loan.status === 'SANCTIONED' && <PrimaryButton onClick={() => setDisbursing(loan)}>Disburse</PrimaryButton>}
                  {loan.status === 'DISBURSED' && (
                    <SecondaryButton onClick={() => setSettling(loan)}>
                      <HandCoins size={14} /> Settle Cash
                    </SecondaryButton>
                  )}
                </div>
              </div>
            </Card>
          ))}
        </div>
      </div>

      {sanctioning && <SanctionModal loan={sanctioning} onClose={() => setSanctioning(null)} />}
      {disbursing && <DisburseModal loan={disbursing} onClose={() => setDisbursing(null)} />}
      {rejecting && <RejectLoanModal loan={rejecting} onClose={() => setRejecting(null)} />}
      {settling && <SettleCashModal loan={settling} onClose={() => setSettling(null)} />}
    </div>
  )
}
