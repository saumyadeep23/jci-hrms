import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { useToast } from '../../components/common/ToastProvider'
import { Modal } from '../../components/common/Modal'
import { ErrorState, PrimaryButton, SecondaryButton } from '../../components/common/ui'
import { describeApiError } from '../../lib/apiError'
import type { PayrollEditResponse, PayrollMonthlyRecordResponse } from '../../types/api'

const EDITABLE_HEADS = [
  { headCount: 1, label: 'BASIC (cascades DA/HRA/CPF/JCPF/P.Tax)' },
  { headCount: 27, label: 'CPF (Head 27)' },
  { headCount: 46, label: 'Co-operative Loan (Head 46)' },
  { headCount: 49, label: 'P.Tax (Head 49)' },
]

interface EditEmployeeSalaryModalProps {
  batchId: number
  record: PayrollMonthlyRecordResponse
  onClose: () => void
}

/** Edit one salary head on an employee's still-open (DRAFT/CALCULATED) reconciliation row - editing BASIC previews/cascades DA/HRA/CPF/JCPF/P.Tax before the officer confirms. */
export function EditEmployeeSalaryModal({ batchId, record, onClose }: EditEmployeeSalaryModalProps) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [headCount, setHeadCount] = useState(1)
  const [newAmount, setNewAmount] = useState('')
  const [changeReason, setChangeReason] = useState('')

  const parsedAmount = Number(newAmount)
  const amountValid = newAmount.trim() !== '' && Number.isFinite(parsedAmount) && parsedAmount >= 0

  const previewQuery = useQuery({
    queryKey: ['payroll-edit-preview', batchId, record.tranId, headCount, parsedAmount],
    queryFn: async () =>
      (
        await apiClient.get<PayrollEditResponse>(`/v1/payroll/batches/${batchId}/records/${record.tranId}/edit-preview`, {
          params: { headCount, newAmount: parsedAmount },
        })
      ).data,
    enabled: amountValid,
  })

  const editMutation = useMutation({
    mutationFn: async () =>
      (
        await apiClient.post<PayrollEditResponse>(`/v1/payroll/batches/${batchId}/records/${record.tranId}/edit`, {
          headCount,
          newAmount: parsedAmount,
          changeReason,
        })
      ).data,
    onSuccess: () => {
      show({ tone: 'success', message: 'Salary line updated and cascade applied.' })
      queryClient.invalidateQueries({ queryKey: ['payroll-batch-records', batchId] })
      onClose()
    },
  })

  const preview = previewQuery.data
  const canSubmit = amountValid && changeReason.trim().length > 0 && !editMutation.isPending

  return (
    <Modal title={`Edit Salary Line - ${record.employeeName} (${record.empCode})`} onClose={onClose} maxWidthClassName="max-w-xl">
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault()
          editMutation.mutate()
        }}
      >
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Salary Head</label>
            <select
              value={headCount}
              onChange={(e) => setHeadCount(Number(e.target.value))}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              {EDITABLE_HEADS.map((h) => (
                <option key={h.headCount} value={h.headCount}>{h.label}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">New Amount (₹)</label>
            <input
              type="number"
              min={0}
              step="0.01"
              value={newAmount}
              onChange={(e) => setNewAmount(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div className="sm:col-span-2">
            <label className="mb-1 block text-xs font-medium text-slate-600">Change Reason</label>
            <textarea
              value={changeReason}
              onChange={(e) => setChangeReason(e.target.value)}
              rows={2}
              placeholder="e.g. Correction per revised pay order"
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
        </div>

        <div className="rounded-md border border-slate-200 bg-slate-50 p-3 text-sm">
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Live Cascade Preview</p>
          {!amountValid && <p className="text-xs text-slate-400">Enter an amount to preview the cascade.</p>}
          {amountValid && previewQuery.isLoading && <p className="text-xs text-slate-400">Computing preview...</p>}
          {amountValid && previewQuery.isError && (
            <ErrorState message={describeApiError(previewQuery.error, 'Could not compute the preview.')} />
          )}
          {preview && (
            <>
              <table className="w-full text-xs">
                <thead className="text-slate-500">
                  <tr>
                    <th className="pb-1 text-left">Head</th>
                    <th className="pb-1 text-right">New Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {preview.changedHeads.map((line) => (
                    <tr key={line.headCount} className="border-t border-slate-100">
                      <td className="py-1">{line.shortName || line.description}</td>
                      <td className="py-1 text-right tabular-nums">{line.amount.toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <dl className="mt-2 grid grid-cols-3 gap-2 border-t border-slate-200 pt-2 text-xs">
                <div>
                  <dt className="text-slate-400">Gross</dt>
                  <dd className="font-semibold text-slate-700">{preview.grossAmount.toFixed(2)}</dd>
                </div>
                <div>
                  <dt className="text-slate-400">Deductions</dt>
                  <dd className="font-semibold text-slate-700">{preview.totalDeductions.toFixed(2)}</dd>
                </div>
                <div>
                  <dt className="text-slate-400">Net</dt>
                  <dd className="font-semibold text-brand-forest">{preview.netAmount.toFixed(2)}</dd>
                </div>
              </dl>
            </>
          )}
        </div>

        <div className="flex gap-2">
          <SecondaryButton type="button" onClick={onClose} className="flex-1 justify-center">
            Cancel
          </SecondaryButton>
          <PrimaryButton type="submit" disabled={!canSubmit} className="flex-1 justify-center">
            Save & Cascade
          </PrimaryButton>
        </div>

        {editMutation.isError && <ErrorState message={describeApiError(editMutation.error, 'Could not save the edit.')} />}
      </form>
    </Modal>
  )
}
