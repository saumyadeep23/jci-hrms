import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2 } from 'lucide-react'
import { apiClient } from '../../../../api/client'
import { useToast } from '../../../../components/common/ToastProvider'
import { Modal } from '../../../../components/common/Modal'
import { describeApiError } from '../../../../lib/apiError'
import { ErrorState, FieldError, PrimaryButton, SecondaryButton, errorInputClass } from '../../../../components/common/ui'
import type { CpfTrustMemberResponse, UpdateUanRequest } from '../../../../types/api'

const UAN_PATTERN = /^[0-9]{12}$/

/** Section 15 - "Update UAN". Deliberately scoped to just CPF A/C No / Member Name / Current UAN / New UAN - no other employee fields, unlike the full EditEmployeeModal. */
export function UpdateUanDialog({ member, onClose }: { member: CpfTrustMemberResponse; onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [newUan, setNewUan] = useState('')
  const [confirming, setConfirming] = useState(false)
  const [touched, setTouched] = useState(false)

  const isValid = UAN_PATTERN.test(newUan)

  const mutation = useMutation({
    mutationFn: async () => {
      const payload: UpdateUanRequest = { uanNo: newUan }
      return (await apiClient.put(`/v1/payroll/trust/members/${member.employeeId}/uan`, payload)).data
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'UAN updated.' })
      queryClient.invalidateQueries({ queryKey: ['cpf-trust-members'] })
      queryClient.invalidateQueries({ queryKey: ['cpf-trust-members-summary'] })
      onClose()
    },
  })

  return (
    <Modal title="Update UAN" onClose={onClose}>
      {!confirming ? (
        <form
          className="space-y-3"
          onSubmit={(e) => {
            e.preventDefault()
            setTouched(true)
            if (isValid) setConfirming(true)
          }}
        >
          <div className="rounded-md bg-slate-50 p-3 text-sm">
            <p className="text-slate-800">{member.fullName}</p>
            <p className="text-xs text-slate-500">CPF A/C No. {member.cpfAcNo}</p>
          </div>

          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Current UAN</label>
            <input
              disabled
              value={member.uanNo ?? 'Not on record'}
              className="w-full rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-500"
            />
          </div>

          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">New UAN</label>
            <input
              autoFocus
              inputMode="numeric"
              value={newUan}
              onChange={(e) => setNewUan(e.target.value.replace(/\D/g, '').slice(0, 12))}
              onBlur={() => setTouched(true)}
              placeholder="12-digit UAN"
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none ${errorInputClass(touched && !isValid)}`}
            />
            <FieldError message={touched && !isValid ? 'UAN must be exactly 12 digits.' : undefined} />
          </div>

          <div className="flex gap-2 pt-1">
            <SecondaryButton type="button" onClick={onClose} className="flex-1 justify-center">
              Cancel
            </SecondaryButton>
            <PrimaryButton type="submit" disabled={!isValid} className="flex-1 justify-center">
              Continue
            </PrimaryButton>
          </div>
        </form>
      ) : (
        <div className="space-y-4">
          <p className="text-sm text-slate-600">
            Confirm updating the UAN for <span className="font-medium text-slate-800">{member.fullName}</span> ({member.cpfAcNo}) from{' '}
            <span className="font-medium">{member.uanNo ?? 'not on record'}</span> to <span className="font-medium">{newUan}</span>.
          </p>
          <div className="flex gap-2">
            <SecondaryButton onClick={() => setConfirming(false)} className="flex-1 justify-center" disabled={mutation.isPending}>
              Back
            </SecondaryButton>
            <PrimaryButton onClick={() => mutation.mutate()} disabled={mutation.isPending} className="flex-1 justify-center">
              <CheckCircle2 size={14} /> Confirm & Save
            </PrimaryButton>
          </div>
          {mutation.isError && <ErrorState message={describeApiError(mutation.error, 'Could not update the UAN.')} />}
        </div>
      )}
    </Modal>
  )
}
