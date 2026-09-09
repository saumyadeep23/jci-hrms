import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { useToast } from '../common/ToastProvider'
import { describeApiErrorList, getFieldErrors } from '../../lib/apiError'
import { DatePicker } from '../common/DatePicker'
import { Modal } from '../common/Modal'
import { FormErrorBanner } from '../common/FormErrorBanner'
import { FieldError, PrimaryButton, errorInputClass } from '../common/ui'
import type { VehicleAllotmentRequest } from '../../types/api'

const DEFAULT_PERSONAL_USE_DEDUCTION = 2000

/** Allotment form for the Employment tab's "Official Vehicle Provided" sub-panel. */
export function VehicleAllotmentFormModal({ employeeId, onClose }: { employeeId: number; onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [allotmentOrderNo, setAllotmentOrderNo] = useState('')
  const [vehicleRegNo, setVehicleRegNo] = useState('')
  const [vehicleMakeModel, setVehicleMakeModel] = useState('')
  const [allottedFrom, setAllottedFrom] = useState('')
  const [driverProvided, setDriverProvided] = useState(true)
  const [personalUseAllowed, setPersonalUseAllowed] = useState(true)
  const [monthlyDeductionAmount, setMonthlyDeductionAmount] = useState(String(DEFAULT_PERSONAL_USE_DEDUCTION))

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload: VehicleAllotmentRequest = {
        allotmentOrderNo: allotmentOrderNo || null,
        vehicleRegNo,
        vehicleMakeModel: vehicleMakeModel || null,
        driverProvided,
        personalUseAllowed,
        deductionApplicable: personalUseAllowed,
        monthlyDeductionAmount: personalUseAllowed ? Number(monthlyDeductionAmount) : 0,
        allottedFrom,
        remarks: null,
      }
      return (await apiClient.post(`/v1/employees/${employeeId}/vehicle-allotments`, payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['employee-vehicle-allotments', employeeId] })
      queryClient.invalidateQueries({ queryKey: ['employee', employeeId] })
      show({ tone: 'success', message: `${vehicleRegNo} allotted.` })
      onClose()
    },
  })
  const fieldErrors = getFieldErrors(saveMutation.error)

  const formValid = vehicleRegNo.trim() !== '' && allottedFrom !== ''

  return (
    <Modal title="Allot Vehicle" onClose={onClose} maxWidthClassName="max-w-lg">
      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault()
          saveMutation.mutate()
        }}
      >
        {saveMutation.isError && (
          <FormErrorBanner title="Could not allot vehicle" errors={describeApiErrorList(saveMutation.error, 'Could not allot vehicle.')} />
        )}

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Vehicle Reg No.</label>
            <input
              required
              value={vehicleRegNo}
              onChange={(e) => setVehicleRegNo(e.target.value.toUpperCase())}
              placeholder="WB-06X-1234"
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm uppercase ${errorInputClass(Boolean(fieldErrors?.vehicleRegNo))}`}
            />
            <FieldError message={fieldErrors?.vehicleRegNo} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Make / Model</label>
            <input
              value={vehicleMakeModel}
              onChange={(e) => setVehicleMakeModel(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Allotment Order No.</label>
            <input
              value={allotmentOrderNo}
              onChange={(e) => setAllotmentOrderNo(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Allotted Date</label>
            <DatePicker value={allottedFrom} onChange={setAllottedFrom} hasError={Boolean(fieldErrors?.allottedFrom)} />
            <FieldError message={fieldErrors?.allottedFrom} />
          </div>
        </div>

        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" checked={driverProvided} onChange={(e) => setDriverProvided(e.target.checked)} />
          Driver Provided
        </label>

        <div className="rounded-md border border-slate-200 p-3">
          <label className="flex items-center justify-between text-sm text-slate-700">
            <span>Personal Use Recovery</span>
            <input
              type="checkbox"
              role="switch"
              checked={personalUseAllowed}
              onChange={(e) => {
                setPersonalUseAllowed(e.target.checked)
                if (e.target.checked && monthlyDeductionAmount === '') {
                  setMonthlyDeductionAmount(String(DEFAULT_PERSONAL_USE_DEDUCTION))
                }
              }}
            />
          </label>
          {personalUseAllowed && (
            <div className="mt-3">
              <label className="mb-1 block text-xs font-medium text-slate-600">Monthly Recovery Amount (₹)</label>
              <input
                type="number"
                min={0}
                step="0.01"
                value={monthlyDeductionAmount}
                onChange={(e) => setMonthlyDeductionAmount(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
          )}
        </div>

        <PrimaryButton type="submit" disabled={saveMutation.isPending || !formValid} className="w-full justify-center">
          Allot Vehicle
        </PrimaryButton>
      </form>
    </Modal>
  )
}
