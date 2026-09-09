import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../../../api/client'
import { useToast } from '../../../../components/common/ToastProvider'
import { Modal } from '../../../../components/common/Modal'
import { EmployeePickerInput } from '../../../../components/common/EmployeePickerInput'
import { Badge, ErrorState, PrimaryButton, SecondaryButton } from '../../../../components/common/ui'
import { describeApiError } from '../../../../lib/apiError'
import { CPF_LOAN_PURPOSES } from '../../../../types/api'
import type { CpfLoanApplicationRequest, CpfLoanEligibilityResponse, CpfLoanPurpose, CpfLoanType, EmployeeResponse } from '../../../../types/api'

const LOAN_TYPES: { value: CpfLoanType; label: string }[] = [
  { value: 'REFUNDABLE_LOAN', label: 'Refundable Loan' },
  { value: 'NON_REFUNDABLE_WITHDRAWAL', label: 'Non-Refundable Withdrawal' },
]
const MIN_TENOR = 12
const MAX_TENOR = 60

/** Section 4 - Apply for a CPF Trust loan/withdrawal, with a live eligibility card. POSTs to /v1/payroll/trust/loans/apply. */
export function ApplyCpfLoanModal({ onClose }: { onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [employee, setEmployee] = useState<EmployeeResponse | null>(null)
  const [loanType, setLoanType] = useState<CpfLoanType>('REFUNDABLE_LOAN')
  const [purpose, setPurpose] = useState<CpfLoanPurpose>('HOUSING')
  const [appliedAmount, setAppliedAmount] = useState('')
  const [totalInstallments, setTotalInstallments] = useState(24)
  const [reason, setReason] = useState('')

  const eligibilityQuery = useQuery({
    queryKey: ['cpf-loan-eligibility', employee?.id, loanType],
    queryFn: async () =>
      (
        await apiClient.get<CpfLoanEligibilityResponse>(`/v1/payroll/trust/loans/eligibility/${employee!.id}`, { params: { loanType } })
      ).data,
    enabled: employee !== null,
  })

  const projectedMonthlyDeduction = useMemo(() => {
    const amount = Number(appliedAmount)
    if (!Number.isFinite(amount) || amount <= 0 || totalInstallments <= 0) return 0
    return amount / totalInstallments
  }, [appliedAmount, totalInstallments])

  const eligibility = eligibilityQuery.data
  const amountExceedsCap = eligibility !== undefined && Number(appliedAmount) > (eligibility?.maxPermissibleAmount ?? 0)
  const blockedByActiveLoan = loanType === 'REFUNDABLE_LOAN' && eligibility?.activeLoanExists === true

  const canSubmit =
    employee !== null &&
    Number(appliedAmount) > 0 &&
    totalInstallments >= MIN_TENOR &&
    totalInstallments <= MAX_TENOR &&
    !amountExceedsCap &&
    !blockedByActiveLoan

  const submitMutation = useMutation({
    mutationFn: async () => {
      const payload: CpfLoanApplicationRequest = {
        employeeId: employee!.id,
        loanType,
        purpose,
        appliedAmount: Number(appliedAmount),
        totalInstallments,
        reason: reason || null,
      }
      return (await apiClient.post('/v1/payroll/trust/loans/apply', payload)).data
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'CPF loan application submitted.' })
      queryClient.invalidateQueries({ queryKey: ['cpf-loan-applications'] })
      onClose()
    },
  })

  return (
    <Modal title="Apply for CPF Loan / Withdrawal" onClose={onClose} maxWidthClassName="max-w-xl">
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault()
          submitMutation.mutate()
        }}
      >
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Employee</label>
          <EmployeePickerInput selected={employee} onSelect={setEmployee} />
        </div>

        {employee && (
          <div className="rounded-md border border-slate-200 bg-slate-50 p-3 text-sm">
            {eligibilityQuery.isLoading && <p className="text-xs text-slate-400">Checking eligibility...</p>}
            {eligibility && (
              <div className="space-y-1">
                <p>
                  <span className="text-slate-400">Total Accumulation (EE + VPF): </span>
                  <span className="font-medium text-slate-800">{eligibility.totalEligibleCorpus.toFixed(2)}</span>
                </p>
                <p>
                  <span className="text-slate-400">Maximum Permissible (75%): </span>
                  <span className="font-medium text-slate-800">{eligibility.maxPermissibleAmount.toFixed(2)}</span>
                </p>
                {loanType === 'REFUNDABLE_LOAN' && eligibility.activeLoanExists && (
                  <Badge tone="danger">Active loan outstanding: {eligibility.outstandingActiveLoanBalance.toFixed(2)} - a new refundable loan is blocked</Badge>
                )}
                <p className="text-[11px] text-slate-400">{eligibility.eligibilityReason}</p>
              </div>
            )}
          </div>
        )}

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Loan Type</label>
            <select
              value={loanType}
              onChange={(e) => setLoanType(e.target.value as CpfLoanType)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              {LOAN_TYPES.map((t) => (
                <option key={t.value} value={t.value}>{t.label}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Purpose</label>
            <select
              value={purpose}
              onChange={(e) => setPurpose(e.target.value as CpfLoanPurpose)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              {CPF_LOAN_PURPOSES.map((p) => (
                <option key={p} value={p}>{p.charAt(0) + p.slice(1).toLowerCase()}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Applied Amount</label>
            <input
              type="number"
              min={0}
              step="0.01"
              value={appliedAmount}
              onChange={(e) => setAppliedAmount(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            {amountExceedsCap && <p className="mt-1 text-[11px] text-red-600">Exceeds the maximum permissible amount.</p>}
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Tenor (installments, 12-60)</label>
            <input
              type="number"
              min={MIN_TENOR}
              max={MAX_TENOR}
              value={totalInstallments}
              onChange={(e) => setTotalInstallments(Number(e.target.value))}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div className="sm:col-span-2">
            <label className="mb-1 block text-xs font-medium text-slate-600">Reason / Justification</label>
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={2}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
        </div>

        <div className="rounded-md bg-slate-50 p-3 text-sm">
          <span className="text-slate-500">Projected Monthly Deduction: </span>
          <span className="font-semibold text-slate-800">{projectedMonthlyDeduction.toFixed(2)}</span>
        </div>

        <div className="flex gap-2">
          <SecondaryButton type="button" onClick={onClose} className="flex-1 justify-center">
            Cancel
          </SecondaryButton>
          <PrimaryButton type="submit" disabled={!canSubmit || submitMutation.isPending} className="flex-1 justify-center">
            Submit Application
          </PrimaryButton>
        </div>

        {submitMutation.isError && <ErrorState message={describeApiError(submitMutation.error, 'Could not submit the loan application.')} />}
      </form>
    </Modal>
  )
}
