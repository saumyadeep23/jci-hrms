import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { describeApiError } from '../../lib/apiError'
import { Modal } from '../common/Modal'
import { ErrorState, PrimaryButton, SecondaryButton } from '../common/ui'
import { PincodeAddressFields, type AddressValue } from './PincodeAddressFields'
import type { EmployeeAddressRequest, EmployeeAddressResponse } from '../../types/api'

function addressValueFrom(address: EmployeeAddressResponse | null): AddressValue {
  return {
    addressLine: address?.addressLine1 ?? '',
    city: address?.city ?? '',
    state: address?.state ?? '',
    district: address?.district ?? '',
    pinCode: address?.pinCode ?? '',
  }
}

/**
 * PIMS_SPEC.md Step 2 (Dynamic Address). Present and permanent addresses are
 * separate normalized rows now (EmployeeAddress, upserted by addressType) -
 * this modal issues one PUT per address type rather than a single combined
 * update.
 */
export function EditAddressModal({
  employeeId,
  presentAddress,
  permanentAddress,
  onClose,
}: {
  employeeId: number
  presentAddress: EmployeeAddressResponse | null
  permanentAddress: EmployeeAddressResponse | null
  onClose: () => void
}) {
  const queryClient = useQueryClient()

  const [present, setPresent] = useState<AddressValue>(() => addressValueFrom(presentAddress))
  const [permanentSameAsPresent, setPermanentSameAsPresent] = useState(
    () =>
      Boolean(presentAddress) &&
      presentAddress?.addressLine1 === permanentAddress?.addressLine1 &&
      presentAddress?.pinCode === permanentAddress?.pinCode,
  )
  const [permanent, setPermanent] = useState<AddressValue>(() => addressValueFrom(permanentAddress))

  const updateMutation = useMutation({
    mutationFn: async () => {
      const finalPermanent = permanentSameAsPresent ? present : permanent

      const presentPayload: EmployeeAddressRequest = {
        addressType: 'PRESENT',
        addressLine1: present.addressLine,
        city: present.city,
        district: present.district,
        state: present.state,
        pinCode: present.pinCode,
      }
      const permanentPayload: EmployeeAddressRequest = {
        addressType: 'PERMANENT',
        addressLine1: finalPermanent.addressLine,
        city: finalPermanent.city,
        district: finalPermanent.district,
        state: finalPermanent.state,
        pinCode: finalPermanent.pinCode,
      }

      await apiClient.put(`/v1/employees/${employeeId}/addresses`, presentPayload)
      await apiClient.put(`/v1/employees/${employeeId}/addresses`, permanentPayload)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['employee-addresses', employeeId] })
      onClose()
    },
  })

  return (
    <Modal title="Edit Address" onClose={onClose} maxWidthClassName="max-w-2xl">
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault()
          updateMutation.mutate()
        }}
      >
        <div>
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Present Address</p>
          <PincodeAddressFields value={present} onChange={(patch) => setPresent((prev) => ({ ...prev, ...patch }))} />
        </div>

        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={permanentSameAsPresent}
            onChange={(e) => setPermanentSameAsPresent(e.target.checked)}
          />
          Permanent address same as present
        </label>

        {!permanentSameAsPresent && (
          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Permanent Address</p>
            <PincodeAddressFields value={permanent} onChange={(patch) => setPermanent((prev) => ({ ...prev, ...patch }))} />
          </div>
        )}

        <div className="flex items-center justify-end gap-2 border-t border-slate-100 pt-3">
          <SecondaryButton type="button" onClick={onClose}>
            Cancel
          </SecondaryButton>
          <PrimaryButton type="submit" disabled={updateMutation.isPending}>
            Save Address
          </PrimaryButton>
        </div>

        {updateMutation.isError && <ErrorState message={describeApiError(updateMutation.error, 'Could not update the address.')} />}
      </form>
    </Modal>
  )
}
