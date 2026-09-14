import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { describeApiError } from '../../lib/apiError'
import { Modal } from '../../components/common/Modal'
import { PrimaryButton, SecondaryButton } from '../../components/common/ui'
import { DocumentUploadField } from '../hr/onboarding/DocumentUploadField'
import type { CpfDisputeCategory, CpfDisputeCreateRequest, CpfPassbookTransactionSummaryResponse, DocumentUploadResponse } from '../../types/api'

const CATEGORY_OPTIONS: { value: CpfDisputeCategory; label: string }[] = [
  { value: 'EMPLOYEE_CONTRIBUTION', label: 'Employee (EE) Contribution' },
  { value: 'EMPLOYER_CONTRIBUTION', label: 'Employer (ER) Contribution' },
  { value: 'EPS_CONTRIBUTION', label: 'EPS Contribution' },
  { value: 'VPF_CONTRIBUTION', label: 'VPF Contribution' },
  { value: 'INTEREST', label: 'Interest Credited' },
  { value: 'LOAN_SANCTION', label: 'Loan Sanction' },
  { value: 'LOAN_REPAYMENT', label: 'Loan Repayment' },
  { value: 'WITHDRAWAL', label: 'Withdrawal' },
  { value: 'TRANSACTION_MISSING', label: 'A Transaction Is Missing' },
  { value: 'BALANCE', label: 'Running Balance' },
  { value: 'TRANSACTION_DATE', label: 'Transaction Date' },
  { value: 'OTHER', label: 'Other' },
]

/**
 * Part 9/27 - raises a dispute against one already-posted CPF ledger transaction. This form can never
 * change the transaction itself: it only ever sends a category + remarks (+ an optional attachment
 * uploaded separately through the existing secure /v1/documents/upload pipeline) to
 * POST /api/v1/ess/cpf/disputes. Any real correction must go through the existing authorized CPF
 * correction workflow elsewhere in the app, never through this form.
 */
export function RaiseDisputeModal({
  transaction,
  onClose,
}: {
  transaction: CpfPassbookTransactionSummaryResponse
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [category, setCategory] = useState<CpfDisputeCategory>('EMPLOYEE_CONTRIBUTION')
  const [remarks, setRemarks] = useState('')
  const [attachment, setAttachment] = useState<DocumentUploadResponse | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const raiseMutation = useMutation({
    mutationFn: async () => {
      const payload: CpfDisputeCreateRequest = {
        cpfLedgerTransactionId: transaction.id,
        disputeCategory: category,
        employeeRemarks: remarks.trim(),
        attachmentS3Key: attachment?.fileS3Key ?? null,
        attachmentOriginalFilename: attachment?.originalFileName ?? null,
      }
      return (await apiClient.post('/v1/ess/cpf/disputes', payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cpf-passbook-transactions'] })
      queryClient.invalidateQueries({ queryKey: ['cpf-my-disputes'] })
      onClose()
    },
    onError: (error) => setErrorMessage(describeApiError(error, 'Could not raise this dispute. Please try again.')),
  })

  return (
    <Modal title="Raise a Dispute" onClose={onClose}>
      <div className="mb-4 rounded-md bg-slate-50 p-3 text-xs text-slate-600">
        <div>
          <span className="text-slate-400">Transaction:</span> {transaction.displayPeriod} ·{' '}
          {transaction.transactionType.replaceAll('_', ' ')}
        </div>
        <div>
          <span className="text-slate-400">Amount:</span> ₹{transaction.displayAmount.toFixed(2)}{' '}
          {transaction.isCredit ? 'credited' : 'debited'}
        </div>
        <p className="mt-2 text-slate-400">
          Raising a dispute never changes this transaction, its amount, or its balance - it only sends this
          entry for review. A confirmed correction, if any, is applied separately by the CPF Trust office.
        </p>
      </div>

      <div className="space-y-4">
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">What is this dispute about?</label>
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value as CpfDisputeCategory)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none"
          >
            {CATEGORY_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Describe the issue</label>
          <textarea
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            rows={4}
            maxLength={4000}
            placeholder="Explain what looks incorrect about this transaction..."
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none"
          />
        </div>

        <DocumentUploadField
          label="Supporting document (optional)"
          category="CPF_DISPUTE_ATTACHMENT"
          maxSizeLabel="5 MB"
          acceptHint=".pdf,.jpg,.jpeg,.png"
          currentFileName={attachment?.originalFileName}
          onUploaded={setAttachment}
        />

        {errorMessage && <p className="text-xs text-red-600">{errorMessage}</p>}

        <div className="flex justify-end gap-2 pt-2">
          <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
          <PrimaryButton
            onClick={() => raiseMutation.mutate()}
            disabled={remarks.trim().length === 0 || raiseMutation.isPending}
          >
            {raiseMutation.isPending ? 'Submitting...' : 'Submit Dispute'}
          </PrimaryButton>
        </div>
      </div>
    </Modal>
  )
}
