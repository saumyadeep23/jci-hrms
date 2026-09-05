import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { describeApiErrorList, getFieldErrors } from '../../lib/apiError'
import { formatDate } from '../../lib/date'
import { Modal } from '../common/Modal'
import { FormErrorBanner } from '../common/FormErrorBanner'
import { FieldError, PrimaryButton, SecondaryButton, errorInputClass } from '../common/ui'
import type { MinistryExtensionRequest, SuperannuationExtensionResponse } from '../../types/api'

/**
 * PIMS_SPEC.md operational-features task, Section 4: Director Ministry
 * Extension. extendedUptoDate is intentionally not collected here - the
 * backend always (re)computes it as the employee's 60th-birthday
 * last-day-of-month via the live sp_apply_director_ministry_extension
 * procedure, so asking for it here would just be theatre.
 */
export function MinistryExtensionModal({ employeeId, onClose }: { employeeId: number; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [orderNumber, setOrderNumber] = useState('')
  const [orderDate, setOrderDate] = useState('')
  const [result, setResult] = useState<SuperannuationExtensionResponse | null>(null)

  const mutation = useMutation({
    mutationFn: async () => {
      const payload: MinistryExtensionRequest = { orderNumber, orderDate, remarks: null }
      return (await apiClient.post<SuperannuationExtensionResponse>(
        `/v1/employees/${employeeId}/superannuation/ministry-extension`,
        payload,
      )).data
    },
    onSuccess: (response) => {
      setResult(response)
      queryClient.invalidateQueries({ queryKey: ['employee-360', employeeId] })
      queryClient.invalidateQueries({ queryKey: ['superannuation-calculation-preview', employeeId] })
    },
  })
  const fieldErrors = getFieldErrors(mutation.error)

  return (
    <Modal title="Apply Ministry Director Extension" onClose={onClose}>
      {result ? (
        <div className="space-y-2 text-sm">
          <p className="rounded-md border border-emerald-200 bg-emerald-50 p-3 text-emerald-700">
            Extension applied. New superannuation date: <strong>{formatDate(result.superannuationDate)}</strong> ({result.calculationBasis})
          </p>
          <SecondaryButton onClick={onClose} className="w-full justify-center">
            Close
          </SecondaryButton>
        </div>
      ) : (
        <form
          className="space-y-3"
          onSubmit={(e) => {
            e.preventDefault()
            mutation.mutate()
          }}
        >
          {mutation.isError && (
            <FormErrorBanner title="Could not apply the extension" errors={describeApiErrorList(mutation.error, 'Could not apply the extension.')} />
          )}
          <p className="text-xs text-slate-500">
            The extended superannuation date is computed automatically as the Director's 60th-birthday (last day of month) - it isn't
            entered manually here.
          </p>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Ministry Order Number</label>
            <input
              required
              value={orderNumber}
              onChange={(e) => setOrderNumber(e.target.value)}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.orderNumber))}`}
            />
            <FieldError message={fieldErrors?.orderNumber} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Order Date</label>
            <input
              required
              type="date"
              value={orderDate}
              onChange={(e) => setOrderDate(e.target.value)}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.orderDate))}`}
            />
            <FieldError message={fieldErrors?.orderDate} />
          </div>
          <PrimaryButton type="submit" disabled={mutation.isPending || !orderNumber || !orderDate} className="w-full justify-center">
            Apply Extension
          </PrimaryButton>
        </form>
      )}
    </Modal>
  )
}
