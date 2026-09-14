import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { Card, ErrorState, PageHeader, PrimaryButton } from '../../../components/common/ui'
import { describeApiError } from '../../../lib/apiError'
import type { JciEccsLoanResponse, JciEccsRestructureRequest, JciEccsTopUpRequest } from '../../../types/api'

const today = () => new Date().toISOString().slice(0, 10)

/** Route: /jcieccs/loans/restructure - POST /{loanId}/restructure and POST /{loanId}/top-up
 * (JciEccsRestructureController). Both archive the current loan and generate a new one; top-up is
 * additionally gated server-side on >= 60.00% of the loan's own sanctioned amount already repaid and
 * the product's ceiling - this page surfaces whatever rejection reason the API returns rather than
 * duplicating that business rule client-side. */
export function RestructureTopUpPage() {
  return (
    <div className="space-y-6">
      <PageHeader title="Restructure & Top-up" description="Archive the current loan and generate a new schedule against the outstanding balance (or an increased one, for top-up)" />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <RestructureCard />
        <TopUpCard />
      </div>
    </div>
  )
}

function RestructureCard() {
  const [loanId, setLoanId] = useState('')
  const [tenureMonths, setTenureMonths] = useState('')
  const [effectiveDate, setEffectiveDate] = useState(today())

  const mutation = useMutation({
    mutationFn: async () => {
      const payload: JciEccsRestructureRequest = { tenureMonths: Number(tenureMonths), effectiveDate }
      return (await apiClient.post<JciEccsLoanResponse>(`/jcieccs/loans/${loanId}/restructure`, payload)).data
    },
  })

  return (
    <Card>
      <p className="mb-3 text-sm font-medium text-slate-700">Restructure</p>
      <div className="space-y-3">
        <Field label="Loan ID" value={loanId} onChange={setLoanId} type="number" />
        <Field label="New Tenure (months)" value={tenureMonths} onChange={setTenureMonths} type="number" />
        <Field label="Effective Date" value={effectiveDate} onChange={setEffectiveDate} type="date" />
        <PrimaryButton
          className="w-full justify-center"
          disabled={!loanId || !tenureMonths || mutation.isPending}
          onClick={() => mutation.mutate()}
        >
          {mutation.isPending ? 'Restructuring...' : 'Restructure Loan'}
        </PrimaryButton>
        {mutation.isError && <ErrorState message={describeApiError(mutation.error, 'Could not restructure this loan.')} />}
        {mutation.isSuccess && (
          <div className="rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
            New loan <strong>{mutation.data.loanIssueId}</strong> created (parent: loan #{mutation.data.parentLoanId}),
            sanctioned ₹{mutation.data.sanctionedAmount.toFixed(2)}.
          </div>
        )}
      </div>
    </Card>
  )
}

function TopUpCard() {
  const [loanId, setLoanId] = useState('')
  const [topUpAmount, setTopUpAmount] = useState('')
  const [tenureMonths, setTenureMonths] = useState('')
  const [effectiveDate, setEffectiveDate] = useState(today())

  const mutation = useMutation({
    mutationFn: async () => {
      const payload: JciEccsTopUpRequest = { topUpAmount: Number(topUpAmount), tenureMonths: Number(tenureMonths), effectiveDate }
      return (await apiClient.post<JciEccsLoanResponse>(`/jcieccs/loans/${loanId}/top-up`, payload)).data
    },
  })

  return (
    <Card>
      <p className="mb-3 text-sm font-medium text-slate-700">Top-up</p>
      <p className="mb-3 text-xs text-slate-400">
        Allowed only once ≥ 60.00% of the loan's own sanctioned amount is repaid, and only for products with
        top-up enabled (Term Loan). The 60.00% boundary and product ceiling are enforced server-side.
      </p>
      <div className="space-y-3">
        <Field label="Loan ID" value={loanId} onChange={setLoanId} type="number" />
        <Field label="Top-up Amount" value={topUpAmount} onChange={setTopUpAmount} type="number" />
        <Field label="New Tenure (months)" value={tenureMonths} onChange={setTenureMonths} type="number" />
        <Field label="Effective Date" value={effectiveDate} onChange={setEffectiveDate} type="date" />
        <PrimaryButton
          className="w-full justify-center"
          disabled={!loanId || !topUpAmount || !tenureMonths || mutation.isPending}
          onClick={() => mutation.mutate()}
        >
          {mutation.isPending ? 'Topping up...' : 'Top Up Loan'}
        </PrimaryButton>
        {mutation.isError && <ErrorState message={describeApiError(mutation.error, 'Could not top up this loan.')} />}
        {mutation.isSuccess && (
          <div className="rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
            New loan <strong>{mutation.data.loanIssueId}</strong> created, sanctioned ₹{mutation.data.sanctionedAmount.toFixed(2)}.
          </div>
        )}
      </div>
    </Card>
  )
}

function Field({
  label,
  value,
  onChange,
  type,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  type: 'text' | 'number' | 'date'
}) {
  return (
    <div>
      <label className="mb-1 block text-xs font-medium text-slate-600">{label}</label>
      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
      />
    </div>
  )
}
