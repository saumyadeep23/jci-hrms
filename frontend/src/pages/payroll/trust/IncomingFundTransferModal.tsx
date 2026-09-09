import { useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { useToast } from '../../../components/common/ToastProvider'
import { Modal } from '../../../components/common/Modal'
import { DatePicker } from '../../../components/common/DatePicker'
import { EmployeePickerInput } from '../../../components/common/EmployeePickerInput'
import { ErrorState, PrimaryButton, SecondaryButton } from '../../../components/common/ui'
import { describeApiError } from '../../../lib/apiError'
import type {
  EmployeeResponse,
  IncomingFundTransferRequest,
  IncomingTransferPaymentMode,
  IncomingTransferType,
  PastServiceOrganizationType,
} from '../../../types/api'

/**
 * The original task spec's own dropdown option lists for sourceOrganizationType/transferType
 * ('CPSE'/'EXEMPTED_TRUST'/'EPFO_RPFC', 'PRIOR_SERVICE_ABSORPTION'/'INTER_CPSE_TRANSFER'/'DEPUTATION_CREDIT')
 * don't match PastServiceOrganizationType/IncomingTransferType's real enum values on the backend
 * (CENTRAL_GOVT/STATE_GOVT/CENTRAL_PSU/STATE_PSU/AUTONOMOUS_BODY/DEFENCE_ARMY_NAVY_AIRFORCE/PRIVATE_SECTOR/OTHER
 * and PF_ONLY/PENSION_ONLY/PF_AND_PENSION respectively) - these options use the real values instead, or
 * submitting would 400 on every request.
 */
const SOURCE_ORG_TYPES: { value: PastServiceOrganizationType; label: string }[] = [
  { value: 'CENTRAL_GOVT', label: 'Central Government' },
  { value: 'STATE_GOVT', label: 'State Government' },
  { value: 'CENTRAL_PSU', label: 'Central PSU' },
  { value: 'STATE_PSU', label: 'State PSU' },
  { value: 'AUTONOMOUS_BODY', label: 'Autonomous Body' },
  { value: 'DEFENCE_ARMY_NAVY_AIRFORCE', label: 'Defence (Army/Navy/Air Force)' },
  { value: 'PRIVATE_SECTOR', label: 'Private Sector' },
  { value: 'OTHER', label: 'Other' },
]
const TRANSFER_TYPES: { value: IncomingTransferType; label: string }[] = [
  { value: 'PF_ONLY', label: 'PF Only' },
  { value: 'PENSION_ONLY', label: 'Pension Only' },
  { value: 'PF_AND_PENSION', label: 'PF & Pension' },
]
const PAYMENT_MODES: { value: IncomingTransferPaymentMode; label: string }[] = [
  { value: 'NEFT', label: 'NEFT' },
  { value: 'RTGS', label: 'RTGS' },
  { value: 'CHEQUE', label: 'Cheque' },
  { value: 'DEMAND_DRAFT', label: 'Demand Draft' },
  { value: 'OTHER', label: 'Other' },
]
/** Short codes only - pensionScheme is a VARCHAR(10) column on the live table. */
const PENSION_SCHEMES = ['NPS', 'EPS-95', 'CPSE', 'NONE'] as const

type AmountField = 'eeCpfPrincipal' | 'eeCpfInterest' | 'erJcpfPrincipal' | 'erJcpfInterest' | 'vpfPrincipal' | 'vpfInterest'

interface FormState {
  employee: EmployeeResponse | null
  sourceOrganizationName: string
  sourceOrganizationType: PastServiceOrganizationType | ''
  transferType: IncomingTransferType | ''
  relievingDate: string
  jciJoiningDate: string
  paymentMode: IncomingTransferPaymentMode | ''
  instrumentOrUtrNo: string
  instrumentDate: string
  bankRealizationDate: string
  bankAccountCode: string
  amounts: Record<AmountField, string>
  pensionScheme: string
  pensionCorpusAmount: string
  pranOrPpoNo: string
  pastQualifyingServiceYears: string
  pastQualifyingServiceDays: string
  gratuityTransferredAmount: string
  gratuityServiceCounted: boolean
  sanctionOrderNo: string
  sanctionDate: string
  annexureKDocRef: string
}

const EMPTY_FORM: FormState = {
  employee: null,
  sourceOrganizationName: '',
  sourceOrganizationType: '',
  transferType: '',
  relievingDate: '',
  jciJoiningDate: '',
  paymentMode: '',
  instrumentOrUtrNo: '',
  instrumentDate: '',
  bankRealizationDate: '',
  bankAccountCode: '',
  amounts: { eeCpfPrincipal: '', eeCpfInterest: '', erJcpfPrincipal: '', erJcpfInterest: '', vpfPrincipal: '', vpfInterest: '' },
  pensionScheme: 'NONE',
  pensionCorpusAmount: '',
  pranOrPpoNo: '',
  pastQualifyingServiceYears: '',
  pastQualifyingServiceDays: '',
  gratuityTransferredAmount: '',
  gratuityServiceCounted: false,
  sanctionOrderNo: '',
  sanctionDate: '',
  annexureKDocRef: '',
}

const AMOUNT_FIELDS: { key: AmountField; label: string }[] = [
  { key: 'eeCpfPrincipal', label: 'EE Principal' },
  { key: 'eeCpfInterest', label: 'EE Interest' },
  { key: 'erJcpfPrincipal', label: 'ER Principal' },
  { key: 'erJcpfInterest', label: 'ER Interest' },
  { key: 'vpfPrincipal', label: 'VPF Principal' },
  { key: 'vpfInterest', label: 'VPF Interest' },
]

function toNumber(value: string): number {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

/** Section 3 - the Incoming Transfer-In ingestion form. POSTs to /v1/payroll/trust/incoming-transfers. */
export function IncomingFundTransferModal({ onClose }: { onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<FormState>(EMPTY_FORM)

  const totalCpfTransferred = useMemo(
    () => AMOUNT_FIELDS.reduce((sum, f) => sum + toNumber(form.amounts[f.key]), 0),
    [form.amounts],
  )

  const canSubmit =
    form.employee !== null &&
    form.sourceOrganizationName.trim() !== '' &&
    form.sourceOrganizationType !== '' &&
    form.transferType !== '' &&
    form.relievingDate !== '' &&
    form.jciJoiningDate !== '' &&
    form.paymentMode !== '' &&
    form.instrumentOrUtrNo.trim() !== '' &&
    form.instrumentDate !== '' &&
    form.bankRealizationDate !== '' &&
    form.bankAccountCode.trim() !== '' &&
    totalCpfTransferred > 0

  const submitMutation = useMutation({
    mutationFn: async () => {
      const payload: IncomingFundTransferRequest = {
        employeeId: form.employee!.id,
        sourceOrganizationName: form.sourceOrganizationName,
        sourceOrganizationType: form.sourceOrganizationType as PastServiceOrganizationType,
        transferType: form.transferType as IncomingTransferType,
        relievingDate: form.relievingDate,
        jciJoiningDate: form.jciJoiningDate,
        paymentMode: form.paymentMode as IncomingTransferPaymentMode,
        instrumentOrUtrNo: form.instrumentOrUtrNo,
        instrumentDate: form.instrumentDate,
        bankRealizationDate: form.bankRealizationDate,
        bankAccountCode: form.bankAccountCode,
        eeCpfPrincipal: toNumber(form.amounts.eeCpfPrincipal),
        eeCpfInterest: toNumber(form.amounts.eeCpfInterest),
        erJcpfPrincipal: toNumber(form.amounts.erJcpfPrincipal),
        erJcpfInterest: toNumber(form.amounts.erJcpfInterest),
        vpfPrincipal: toNumber(form.amounts.vpfPrincipal),
        vpfInterest: toNumber(form.amounts.vpfInterest),
        totalCpfTransferred,
        pensionScheme: form.pensionScheme,
        pensionCorpusAmount: form.pensionCorpusAmount ? toNumber(form.pensionCorpusAmount) : null,
        pranOrPpoNo: form.pranOrPpoNo || null,
        pastQualifyingServiceYears: form.pastQualifyingServiceYears ? toNumber(form.pastQualifyingServiceYears) : null,
        pastQualifyingServiceDays: form.pastQualifyingServiceDays ? toNumber(form.pastQualifyingServiceDays) : null,
        gratuityTransferredAmount: form.gratuityTransferredAmount ? toNumber(form.gratuityTransferredAmount) : null,
        gratuityServiceCounted: form.gratuityServiceCounted,
        annexureKDocRef: form.annexureKDocRef || null,
        sanctionOrderNo: form.sanctionOrderNo || null,
        sanctionDate: form.sanctionDate || null,
      }
      return (await apiClient.post('/v1/payroll/trust/incoming-transfers', payload)).data
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'Incoming fund transfer voucher submitted.' })
      queryClient.invalidateQueries({ queryKey: ['incoming-fund-transfers'] })
      onClose()
    },
  })

  function setAmount(key: AmountField, value: string) {
    setForm((prev) => ({ ...prev, amounts: { ...prev.amounts, [key]: value } }))
  }

  return (
    <Modal title="New Incoming Fund Transfer" onClose={onClose} maxWidthClassName="max-w-3xl">
      <form
        className="space-y-5"
        onSubmit={(e) => {
          e.preventDefault()
          submitMutation.mutate()
        }}
      >
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Employee</label>
          <EmployeePickerInput selected={form.employee} onSelect={(employee) => setForm((prev) => ({ ...prev, employee }))} />
        </div>

        <section>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Source Organization</h3>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <label className="mb-1 block text-xs font-medium text-slate-600">Organization Name</label>
              <input
                value={form.sourceOrganizationName}
                onChange={(e) => setForm({ ...form, sourceOrganizationName: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Organization Type</label>
              <select
                value={form.sourceOrganizationType}
                onChange={(e) => setForm({ ...form, sourceOrganizationType: e.target.value as PastServiceOrganizationType })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">Select...</option>
                {SOURCE_ORG_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>{t.label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Transfer Type</label>
              <select
                value={form.transferType}
                onChange={(e) => setForm({ ...form, transferType: e.target.value as IncomingTransferType })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">Select...</option>
                {TRANSFER_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>{t.label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Relieving Date</label>
              <DatePicker value={form.relievingDate} onChange={(v) => setForm({ ...form, relievingDate: v })} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">JCI Joining Date</label>
              <DatePicker value={form.jciJoiningDate} onChange={(v) => setForm({ ...form, jciJoiningDate: v })} />
            </div>
          </div>
        </section>

        <section>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Banking &amp; Remittance</h3>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Payment Mode</label>
              <select
                value={form.paymentMode}
                onChange={(e) => setForm({ ...form, paymentMode: e.target.value as IncomingTransferPaymentMode })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">Select...</option>
                {PAYMENT_MODES.map((m) => (
                  <option key={m.value} value={m.value}>{m.label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">UTR / Instrument No.</label>
              <input
                value={form.instrumentOrUtrNo}
                onChange={(e) => setForm({ ...form, instrumentOrUtrNo: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Instrument Date</label>
              <DatePicker value={form.instrumentDate} onChange={(v) => setForm({ ...form, instrumentDate: v })} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Bank Realization Date</label>
              <DatePicker value={form.bankRealizationDate} onChange={(v) => setForm({ ...form, bankRealizationDate: v })} />
            </div>
            <div className="sm:col-span-2">
              <label className="mb-1 block text-xs font-medium text-slate-600">Trust Bank Account</label>
              <input
                value={form.bankAccountCode}
                onChange={(e) => setForm({ ...form, bankAccountCode: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
          </div>
        </section>

        <section>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">CPF Component</h3>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {AMOUNT_FIELDS.map((f) => (
              <div key={f.key}>
                <label className="mb-1 block text-xs font-medium text-slate-600">{f.label}</label>
                <input
                  type="number"
                  min={0}
                  step="0.01"
                  value={form.amounts[f.key]}
                  onChange={(e) => setAmount(f.key, e.target.value)}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
            ))}
          </div>
          <div className="mt-3 rounded-md bg-slate-50 p-3 text-sm">
            <span className="text-slate-500">Total CPF Transferred: </span>
            <span className="font-semibold text-slate-800">{totalCpfTransferred.toFixed(2)}</span>
          </div>
        </section>

        <section>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Pension &amp; Gratuity</h3>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Pension Scheme</label>
              <select
                value={form.pensionScheme}
                onChange={(e) => setForm({ ...form, pensionScheme: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                {PENSION_SCHEMES.map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Pension Corpus Amount</label>
              <input
                type="number"
                min={0}
                step="0.01"
                value={form.pensionCorpusAmount}
                onChange={(e) => setForm({ ...form, pensionCorpusAmount: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">PRAN / PPO No.</label>
              <input
                value={form.pranOrPpoNo}
                onChange={(e) => setForm({ ...form, pranOrPpoNo: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Past Qualifying Service (Years)</label>
                <input
                  type="number"
                  min={0}
                  value={form.pastQualifyingServiceYears}
                  onChange={(e) => setForm({ ...form, pastQualifyingServiceYears: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">(Days)</label>
                <input
                  type="number"
                  min={0}
                  value={form.pastQualifyingServiceDays}
                  onChange={(e) => setForm({ ...form, pastQualifyingServiceDays: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Gratuity Transferred Amount</label>
              <input
                type="number"
                min={0}
                step="0.01"
                value={form.gratuityTransferredAmount}
                onChange={(e) => setForm({ ...form, gratuityTransferredAmount: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <label className="flex items-center gap-2 self-end pb-2 text-sm text-slate-600">
              <input
                type="checkbox"
                checked={form.gratuityServiceCounted}
                onChange={(e) => setForm({ ...form, gratuityServiceCounted: e.target.checked })}
              />
              Service Counts for Gratuity
            </label>
          </div>
        </section>

        <section>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Sanction &amp; Annexure-K</h3>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Sanction Order No.</label>
              <input
                value={form.sanctionOrderNo}
                onChange={(e) => setForm({ ...form, sanctionOrderNo: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Sanction Date</label>
              <DatePicker value={form.sanctionDate} onChange={(v) => setForm({ ...form, sanctionDate: v })} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Annexure-K Document Ref</label>
              <input
                value={form.annexureKDocRef}
                onChange={(e) => setForm({ ...form, annexureKDocRef: e.target.value })}
                placeholder="Doc reference / filing no."
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
              <p className="mt-1 text-[11px] text-slate-400">Reference only - no document management/upload store exists in this app yet.</p>
            </div>
          </div>
        </section>

        <div className="flex gap-2 border-t border-slate-100 pt-4">
          <SecondaryButton type="button" onClick={onClose} className="flex-1 justify-center">
            Cancel
          </SecondaryButton>
          <PrimaryButton type="submit" disabled={!canSubmit || submitMutation.isPending} className="flex-1 justify-center">
            Submit Voucher
          </PrimaryButton>
        </div>

        {submitMutation.isError && <ErrorState message={describeApiError(submitMutation.error, 'Could not submit the transfer voucher.')} />}
      </form>
    </Modal>
  )
}
