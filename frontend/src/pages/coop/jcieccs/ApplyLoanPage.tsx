import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { EmployeePickerInput } from '../../../components/common/EmployeePickerInput'
import { ErrorState, PageHeader, PrimaryButton } from '../../../components/common/ui'
import { describeApiError } from '../../../lib/apiError'
import type { EmployeeResponse, JciEccsLoanCreateRequest, JciEccsLoanProductCode, JciEccsLoanResponse } from '../../../types/api'

const today = () => new Date().toISOString().slice(0, 10)

/** Route: /jcieccs/loans/apply - POST /api/jcieccs/loans (JciEccsLoanController.createLoan). Records an
 * already-sanctioned-and-disbursed loan in one step - there is no separate sanction/disburse step in
 * this module. */
export function ApplyLoanPage() {
  const [employee, setEmployee] = useState<EmployeeResponse | null>(null)
  const [productCode, setProductCode] = useState<JciEccsLoanProductCode>('TERM')
  const [sanctionedAmount, setSanctionedAmount] = useState('')
  const [tenureMonths, setTenureMonths] = useState('')
  const [disbursementDate, setDisbursementDate] = useState(today())

  const submitMutation = useMutation({
    mutationFn: async () => {
      const payload: JciEccsLoanCreateRequest = {
        employeeCode: employee!.employeeCode,
        productCode,
        sanctionedAmount: Number(sanctionedAmount),
        tenureMonths: tenureMonths ? Number(tenureMonths) : null,
        applicationDate: disbursementDate,
        sanctionDate: disbursementDate,
        disbursementDate,
      }
      return (await apiClient.post<JciEccsLoanResponse>('/jcieccs/loans', payload)).data
    },
  })

  const canSubmit = employee !== null && Number(sanctionedAmount) > 0

  return (
    <div>
      <PageHeader title="New Loan Application & Disbursal" description="Term (TE-) or Emergency (EM-) loan - sanctioned and disbursed in one step" />

      <form
        className="max-w-xl space-y-4 rounded-md border border-slate-200 bg-white p-4"
        onSubmit={(e) => {
          e.preventDefault()
          submitMutation.mutate()
        }}
      >
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Member (Employee)</label>
          <EmployeePickerInput selected={employee} onSelect={setEmployee} />
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Product</label>
            <select
              value={productCode}
              onChange={(e) => setProductCode(e.target.value as JciEccsLoanProductCode)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="TERM">Term Loan (max ₹5,00,000 / 60 months)</option>
              <option value="EMERGENCY">Emergency Loan (max ₹70,000 / 10 months)</option>
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Sanctioned Amount</label>
            <input
              type="number"
              min={0}
              step="0.01"
              value={sanctionedAmount}
              onChange={(e) => setSanctionedAmount(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Tenure (months, optional - defaults to product max)</label>
            <input
              type="number"
              min={1}
              value={tenureMonths}
              onChange={(e) => setTenureMonths(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Disbursement Date</label>
            <input
              type="date"
              value={disbursementDate}
              onChange={(e) => setDisbursementDate(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
        </div>

        <PrimaryButton type="submit" disabled={!canSubmit || submitMutation.isPending} className="w-full justify-center">
          {submitMutation.isPending ? 'Submitting...' : 'Disburse Loan'}
        </PrimaryButton>

        {submitMutation.isError && <ErrorState message={describeApiError(submitMutation.error, 'Could not create this loan.')} />}
        {submitMutation.isSuccess && (
          <div className="rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
            Loan <strong>{submitMutation.data.loanIssueId}</strong> disbursed - monthly principal
            installment ₹{submitMutation.data.monthlyPrincipalInstallment}, disbursement cycle{' '}
            {submitMutation.data.disbursementCycleCode}.
          </div>
        )}
      </form>
    </div>
  )
}
