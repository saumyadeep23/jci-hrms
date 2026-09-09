import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { useToast } from '../common/ToastProvider'
import { describeApiErrorList, getFieldErrors } from '../../lib/apiError'
import { DatePicker } from '../common/DatePicker'
import { Modal } from '../common/Modal'
import { FormErrorBanner } from '../common/FormErrorBanner'
import { FieldError, PrimaryButton } from '../common/ui'
import type { VehicleAllotmentResponse, VehicleAllotmentSurrenderRequest } from '../../types/api'

export function VehicleSurrenderModal({
  employeeId,
  allotment,
  onClose,
}: {
  employeeId: number
  allotment: VehicleAllotmentResponse
  onClose: () => void
}) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [surrenderedOn, setSurrenderedOn] = useState('')
  const [remarks, setRemarks] = useState('')

  const surrenderMutation = useMutation({
    mutationFn: async () => {
      const payload: VehicleAllotmentSurrenderRequest = { surrenderedOn, remarks: remarks || null }
      return (await apiClient.put(`/v1/employees/${employeeId}/vehicle-allotments/${allotment.id}/surrender`, payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['employee-vehicle-allotments', employeeId] })
      queryClient.invalidateQueries({ queryKey: ['employee', employeeId] })
      show({ tone: 'success', message: `${allotment.vehicleRegNo} surrendered.` })
      onClose()
    },
  })
  const fieldErrors = getFieldErrors(surrenderMutation.error)

  return (
    <Modal title={`Surrender ${allotment.vehicleRegNo}`} onClose={onClose} maxWidthClassName="max-w-sm">
      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault()
          surrenderMutation.mutate()
        }}
      >
        {surrenderMutation.isError && (
          <FormErrorBanner title="Could not surrender" errors={describeApiErrorList(surrenderMutation.error, 'Could not surrender.')} />
        )}

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Surrender Date</label>
          <DatePicker value={surrenderedOn} onChange={setSurrenderedOn} hasError={Boolean(fieldErrors?.surrenderedOn)} />
          <FieldError message={fieldErrors?.surrenderedOn} />
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

        <PrimaryButton type="submit" disabled={surrenderMutation.isPending || surrenderedOn === ''} className="w-full justify-center">
          Confirm Surrender
        </PrimaryButton>
      </form>
    </Modal>
  )
}
