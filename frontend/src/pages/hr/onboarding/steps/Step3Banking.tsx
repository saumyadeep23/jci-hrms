import { FieldError, errorInputClass } from '../../../../components/common/ui'
import { useIfscLookup } from '../../../../hooks/useIfscLookup'
import type { LocalBanking } from '../onboardingTypes'
import { DocumentUploadField } from '../DocumentUploadField'

/** PIMS_SPEC.md Onboarding Step 3: Dynamic Banking (IFSC API on blur, cancelled-cheque upload, SCD-2 on the backend). */
export function Step3Banking({ value, onChange }: { value: LocalBanking; onChange: (next: LocalBanking) => void }) {
  const { isFetching, error: ifscError, setDebouncedIfsc } = useIfscLookup(value.bankIfsc, (bankName, branch) => {
    onChange({ ...value, bankName: bankName ?? value.bankName, bankBranch: branch ?? value.bankBranch })
  })

  const mismatch = value.reenterBankAccountNumber !== '' && value.bankAccountNumber !== value.reenterBankAccountNumber
  const mismatchError = mismatch ? 'Account numbers do not match.' : null

  return (
    <div className="grid grid-cols-2 gap-3">
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">IFSC Code</label>
        <input
          required
          maxLength={11}
          value={value.bankIfsc}
          onChange={(e) => onChange({ ...value, bankIfsc: e.target.value.toUpperCase() })}
          onBlur={() => setDebouncedIfsc(value.bankIfsc)}
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
          value={value.bankName}
          onChange={(e) => onChange({ ...value, bankName: e.target.value })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Branch</label>
        <input
          required
          value={value.bankBranch}
          onChange={(e) => onChange({ ...value, bankBranch: e.target.value })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Account Number</label>
        <input
          required
          value={value.bankAccountNumber}
          onChange={(e) => onChange({ ...value, bankAccountNumber: e.target.value })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Re-enter Account Number</label>
        <input
          required
          value={value.reenterBankAccountNumber}
          onChange={(e) => onChange({ ...value, reenterBankAccountNumber: e.target.value })}
          className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(mismatchError !== null)}`}
        />
        <FieldError message={mismatchError} />
      </div>

      <div className="col-span-2">
        <DocumentUploadField
          label="Cancelled Cheque / Bank Proof"
          category="BANK_PROOF"
          maxSizeLabel="2MB (PDF/JPG/PNG)"
          acceptHint=".pdf,.jpg,.jpeg,.png"
          currentFileName={value.cancelledChequeS3Key ? value.cancelledChequeS3Key.split('/').pop() : null}
          onUploaded={(res) => onChange({ ...value, cancelledChequeS3Key: res.fileS3Key })}
        />
      </div>
    </div>
  )
}
