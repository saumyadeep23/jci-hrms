import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { describeApiError } from '../../lib/apiError'
import { Modal } from '../common/Modal'
import { ErrorState, FieldError, PrimaryButton, SecondaryButton, errorInputClass } from '../common/ui'
import type { EmployeeBankAccountRequest, IfscLookupResponse } from '../../types/api'

const IFSC_PATTERN = /^[A-Z]{4}0[A-Z0-9]{6}$/
const DEBOUNCE_MS = 400

/**
 * Adds a new primary bank account for an employee (employee_bank_accounts, SCD Type-2 - V30) -
 * inserting an ACTIVE primary row automatically supersedes the previous one server-side (see
 * fn_sync_employee_bank_change's successor logic in EmployeeBankAccountService). Mirrors the
 * onboarding wizard's Step3Banking IFSC-lookup UX.
 */
export function AddBankAccountModal({ employeeId, onClose }: { employeeId: number; onClose: () => void }) {
  const queryClient = useQueryClient()

  const [bankName, setBankName] = useState('')
  const [bankBranch, setBankBranch] = useState('')
  const [bankAccountNumber, setBankAccountNumber] = useState('')
  const [reenterBankAccountNumber, setReenterBankAccountNumber] = useState('')
  const [bankIfsc, setBankIfsc] = useState('')
  const [debouncedIfsc, setDebouncedIfsc] = useState('')
  const appliedKeyRef = useRef<string | null>(null)

  useEffect(() => {
    const handle = setTimeout(() => setDebouncedIfsc(bankIfsc), DEBOUNCE_MS)
    return () => clearTimeout(handle)
  }, [bankIfsc])

  const formatValid = IFSC_PATTERN.test(debouncedIfsc)

  const { data: ifscData, isFetching } = useQuery({
    queryKey: ['ifsc-lookup', debouncedIfsc],
    queryFn: async () => (await apiClient.get<IfscLookupResponse>(`/v1/finance/ifsc/${debouncedIfsc}`)).data,
    enabled: formatValid,
  })

  useEffect(() => {
    if (!ifscData) return
    const key = `${ifscData.ifsc}:${ifscData.found}`
    if (appliedKeyRef.current === key) return
    appliedKeyRef.current = key
    if (ifscData.found) {
      setBankName((prev) => ifscData.bankName ?? prev)
      setBankBranch((prev) => ifscData.branch ?? prev)
    }
  }, [ifscData])

  const mismatch = reenterBankAccountNumber !== '' && bankAccountNumber !== reenterBankAccountNumber
  const showFormatError = bankIfsc.length === 11 && !IFSC_PATTERN.test(bankIfsc)
  const ifscError = showFormatError ? 'Invalid IFSC format (e.g. SBIN0001234).' : null
  const mismatchError = mismatch ? 'Account numbers do not match.' : null

  const addMutation = useMutation({
    mutationFn: async () => {
      const payload: EmployeeBankAccountRequest = {
        bankName,
        bankBranch,
        bankAccountNumber,
        reenterBankAccountNumber,
        bankIfsc,
      }
      await apiClient.post(`/v1/employees/${employeeId}/bank-accounts`, payload)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['employee-bank-accounts', employeeId] })
      onClose()
    },
  })

  const formValid =
    bankName.trim() !== '' && bankBranch.trim() !== '' && bankAccountNumber.trim() !== '' &&
    reenterBankAccountNumber === bankAccountNumber && IFSC_PATTERN.test(bankIfsc)

  return (
    <Modal title="Add Bank Account" onClose={onClose} maxWidthClassName="max-w-lg">
      <form
        className="grid grid-cols-2 gap-3"
        onSubmit={(e) => {
          e.preventDefault()
          addMutation.mutate()
        }}
      >
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">IFSC Code</label>
          <input
            required
            maxLength={11}
            value={bankIfsc}
            onChange={(e) => setBankIfsc(e.target.value.toUpperCase())}
            onBlur={() => setDebouncedIfsc(bankIfsc)}
            className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm uppercase ${errorInputClass(ifscError !== null)}`}
            placeholder="SBIN0001234"
          />
          {isFetching && <p className="mt-1 text-[11px] text-slate-400">Looking up IFSC...</p>}
          <FieldError message={ifscError} />
        </div>
        <div />

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Bank Name</label>
          <input
            required
            value={bankName}
            onChange={(e) => setBankName(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Branch</label>
          <input
            required
            value={bankBranch}
            onChange={(e) => setBankBranch(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Account Number</label>
          <input
            required
            value={bankAccountNumber}
            onChange={(e) => setBankAccountNumber(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Re-enter Account Number</label>
          <input
            required
            value={reenterBankAccountNumber}
            onChange={(e) => setReenterBankAccountNumber(e.target.value)}
            className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(mismatchError !== null)}`}
          />
          <FieldError message={mismatchError} />
        </div>

        <div className="col-span-2 flex items-center justify-end gap-2 border-t border-slate-100 pt-3">
          <SecondaryButton type="button" onClick={onClose}>
            Cancel
          </SecondaryButton>
          <PrimaryButton type="submit" disabled={addMutation.isPending || !formValid}>
            Save Bank Account
          </PrimaryButton>
        </div>

        {addMutation.isError && (
          <div className="col-span-2">
            <ErrorState message={describeApiError(addMutation.error, 'Could not add the bank account.')} />
          </div>
        )}
      </form>
    </Modal>
  )
}
