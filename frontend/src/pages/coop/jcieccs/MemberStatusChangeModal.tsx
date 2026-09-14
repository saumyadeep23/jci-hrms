import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { useToast } from '../../../components/common/ToastProvider'
import { Modal } from '../../../components/common/Modal'
import { ErrorState, PrimaryButton, SecondaryButton } from '../../../components/common/ui'
import { describeApiError } from '../../../lib/apiError'
import type { JciEccsMemberResponse, JciEccsMembershipStatus, JciEccsMemberStatusChangeRequest } from '../../../types/api'

const STATUS_TARGET_OPTIONS: { value: JciEccsMembershipStatus; label: string }[] = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'SUSPENDED', label: 'Suspended' },
  { value: 'CLOSED', label: 'Closed / Cessation (Resigned / Superannuated)' },
]

const today = () => new Date().toISOString().slice(0, 10)

/**
 * Change-membership-status confirmation modal - PUT /api/jcieccs/members/{id}/status
 * (JciEccsMemberController.changeStatus). Remarks are required in this UI whenever the target status
 * is SUSPENDED or CLOSED (Bye-laws 15/16); the backend enforces the same rule independently, this is
 * just earlier feedback.
 */
export function MemberStatusChangeModal({ member, onClose }: { member: JciEccsMemberResponse; onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<JciEccsMembershipStatus>(member.membershipStatus)
  const [effectiveDate, setEffectiveDate] = useState(today())
  const [remarks, setRemarks] = useState('')

  const remarksRequired = status === 'SUSPENDED' || status === 'CLOSED'
  const canSubmit = status !== member.membershipStatus && (!remarksRequired || remarks.trim().length > 0)

  const submitMutation = useMutation({
    mutationFn: async () => {
      const payload: JciEccsMemberStatusChangeRequest = { status, effectiveDate, remarks: remarks.trim() || null }
      return (await apiClient.put(`/jcieccs/members/${member.id}/status`, payload)).data
    },
    onSuccess: () => {
      show({ tone: 'success', message: `Membership ${member.membershipCode} status updated to ${status}.` })
      queryClient.invalidateQueries({ queryKey: ['jcieccs-members'] })
      onClose()
    },
  })

  return (
    <Modal title={`Change Membership Status - ${member.membershipCode}`} onClose={onClose} maxWidthClassName="max-w-lg">
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault()
          submitMutation.mutate()
        }}
      >
        <div className="rounded-md border border-slate-200 bg-slate-50 p-3 text-sm text-slate-600">
          <span className="text-slate-400">Member: </span>
          <span className="font-medium text-slate-800">{member.memberName ?? '--'}</span>
          <span className="text-slate-400"> · Current status: </span>
          <span className="font-medium text-slate-800">{member.membershipStatus}</span>
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Target Status</label>
          <select
            value={status}
            onChange={(e) => setStatus(e.target.value as JciEccsMembershipStatus)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            {STATUS_TARGET_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Effective Date</label>
          <input
            type="date"
            value={effectiveDate}
            onChange={(e) => setEffectiveDate(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">
            Remarks / Reason {remarksRequired ? '(required for suspension or cessation - Bye-laws 15/16)' : '(optional)'}
          </label>
          <textarea
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            rows={3}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>

        <div className="flex gap-2">
          <SecondaryButton type="button" onClick={onClose} className="flex-1 justify-center">
            Cancel
          </SecondaryButton>
          <PrimaryButton type="submit" disabled={!canSubmit || submitMutation.isPending} className="flex-1 justify-center">
            {submitMutation.isPending ? 'Saving...' : 'Confirm Status Change'}
          </PrimaryButton>
        </div>

        {submitMutation.isError && <ErrorState message={describeApiError(submitMutation.error, 'Could not update membership status.')} />}
      </form>
    </Modal>
  )
}
