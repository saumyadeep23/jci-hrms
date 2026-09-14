import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { ErrorState, PageHeader, PrimaryButton } from '../../../components/common/ui'
import { describeApiError } from '../../../lib/apiError'
import type { JciEccsCashRepaymentRequest, JciEccsLoanResponse } from '../../../types/api'

const today = () => new Date().toISOString().slice(0, 10)

/** Route: /jcieccs/loans/cash-repayment - POST /{loanId}/repayments, source = CASH
 * (JciEccsLoanController.postRepayment). An outside-payroll counter deposit; the backend transactionally
 * recalculates the loan's remaining schedule tenure against the new, lower outstanding balance.
 * idempotencyKey (JCIECCS Lifecycle Engine Phase 1) is generated once per form session and only
 * regenerated after a successful post - a double-click/network-retry of the SAME submission reuses the
 * same key (backend returns the original result instead of posting twice); a genuinely new deposit after
 * a successful one gets a fresh key. */
export function CashRepaymentPage() {
  const [loanId, setLoanId] = useState('')
  const [principalAmount, setPrincipalAmount] = useState('0')
  const [interestAmount, setInterestAmount] = useState('0')
  const [repaymentDate, setRepaymentDate] = useState(today())
  const [referenceId, setReferenceId] = useState('')
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID())

  const submitMutation = useMutation({
    mutationFn: async () => {
      const payload: JciEccsCashRepaymentRequest = {
        principalAmount: Number(principalAmount),
        interestAmount: Number(interestAmount),
        repaymentDate,
        referenceId: referenceId || null,
        idempotencyKey,
      }
      return (await apiClient.post<JciEccsLoanResponse>(`/jcieccs/loans/${loanId}/repayments`, payload)).data
    },
    onSuccess: () => setIdempotencyKey(crypto.randomUUID()),
  })

  const canSubmit = loanId !== '' && (Number(principalAmount) > 0 || Number(interestAmount) > 0)

  return (
    <div>
      <PageHeader title="Cash Repayments" description="Out-of-payroll counter deposit against an active loan" />

      <form
        className="max-w-lg space-y-4 rounded-md border border-slate-200 bg-white p-4"
        onSubmit={(e) => {
          e.preventDefault()
          submitMutation.mutate()
        }}
      >
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Loan ID</label>
          <input
            type="number"
            value={loanId}
            onChange={(e) => setLoanId(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Principal Amount</label>
            <input
              type="number"
              min={0}
              step="0.01"
              value={principalAmount}
              onChange={(e) => setPrincipalAmount(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Interest Amount</label>
            <input
              type="number"
              min={0}
              step="0.01"
              value={interestAmount}
              onChange={(e) => setInterestAmount(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Repayment Date</label>
            <input
              type="date"
              value={repaymentDate}
              onChange={(e) => setRepaymentDate(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Reference / Receipt No.</label>
            <input
              value={referenceId}
              onChange={(e) => setReferenceId(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
        </div>

        <PrimaryButton type="submit" disabled={!canSubmit || submitMutation.isPending} className="w-full justify-center">
          {submitMutation.isPending ? 'Posting...' : 'Post Cash Deposit'}
        </PrimaryButton>

        {submitMutation.isError && <ErrorState message={describeApiError(submitMutation.error, 'Could not post this repayment.')} />}
        {submitMutation.isSuccess && (
          <div className="rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
            Posted. Loan <strong>{submitMutation.data.loanIssueId}</strong> outstanding principal is now ₹
            {submitMutation.data.outstandingPrincipal.toFixed(2)} (status: {submitMutation.data.status}).
          </div>
        )}
      </form>
    </div>
  )
}
