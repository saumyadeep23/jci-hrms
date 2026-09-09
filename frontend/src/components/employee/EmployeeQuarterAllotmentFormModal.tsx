import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { useToast } from '../common/ToastProvider'
import { describeApiErrorList, getFieldErrors } from '../../lib/apiError'
import { DatePicker } from '../common/DatePicker'
import { Modal } from '../common/Modal'
import { FormErrorBanner } from '../common/FormErrorBanner'
import { FieldError, PrimaryButton, errorInputClass } from '../common/ui'
import type {
  EmployeeAddressResponse,
  Page,
  QuarterAllotmentRequest,
  QuarterAllotmentResponse,
  QuarterAllotmentStatus,
  StateMasterResponse,
} from '../../types/api'

const STATUSES: QuarterAllotmentStatus[] = ['OCCUPIED', 'VACATED', 'SURRENDERED', 'CANCELLED']

type FormState = {
  allotmentOrderNo: string
  addressLine1: string
  addressLine2: string
  city: string
  stateCode: string
  pincode: string
  syncCurrentAddress: boolean
  licenseFee: string
  waterCharges: string
  electricCharges: string
  allottedFrom: string
  vacatedOn: string
  status: QuarterAllotmentStatus
  remarks: string
}

function emptyForm(): FormState {
  return {
    allotmentOrderNo: '',
    addressLine1: '',
    addressLine2: '',
    city: '',
    stateCode: '',
    pincode: '',
    syncCurrentAddress: true,
    licenseFee: '',
    waterCharges: '',
    electricCharges: '',
    allottedFrom: '',
    vacatedOn: '',
    status: 'OCCUPIED',
    remarks: '',
  }
}

function formFromRow(row: QuarterAllotmentResponse): FormState {
  return {
    allotmentOrderNo: row.allotmentOrderNo ?? '',
    addressLine1: row.addressLine1,
    addressLine2: row.addressLine2 ?? '',
    city: row.city ?? '',
    stateCode: row.stateCode ?? '',
    pincode: row.pincode ?? '',
    syncCurrentAddress: row.syncCurrentAddress,
    licenseFee: String(row.licenseFee),
    waterCharges: String(row.waterCharges),
    electricCharges: String(row.electricCharges),
    allottedFrom: row.allottedFrom,
    vacatedOn: row.vacatedOn ?? '',
    status: row.status,
    remarks: row.remarks ?? '',
  }
}

/** Add/edit modal for one company accommodation allotment - also how it is vacated/surrendered (set Status + Vacation Date and save). Sync/Address fields overwrite the employee's PRESENT address on save when "Use / Sync with Employee Current Address" is on. */
export function EmployeeQuarterAllotmentFormModal({
  employeeId,
  editing,
  onClose,
}: {
  employeeId: number
  editing: QuarterAllotmentResponse | null
  onClose: () => void
}) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<FormState>(editing ? formFromRow(editing) : emptyForm())
  const isEdit = editing !== null
  const vacationDateEnabled = form.status !== 'OCCUPIED'

  const statesQuery = useQuery({
    queryKey: ['admin-masters-states-options'],
    queryFn: async () => (await apiClient.get<Page<StateMasterResponse>>('/v1/admin/masters/states', { params: { size: 500 } })).data,
  })

  const currentAddressQuery = useQuery({
    queryKey: ['employee-addresses', employeeId],
    queryFn: async () => (await apiClient.get<EmployeeAddressResponse[]>(`/v1/employees/${employeeId}/addresses`)).data,
    enabled: !isEdit,
  })

  function prefillFromCurrentAddress() {
    const present = (currentAddressQuery.data ?? []).find((a) => a.addressType === 'PRESENT')
    if (!present) return
    const matchedState = (statesQuery.data?.content ?? []).find((s) => s.stateName === present.state)
    setForm((prev) => ({
      ...prev,
      addressLine1: present.addressLine1,
      addressLine2: present.addressLine2 ?? '',
      city: present.city,
      pincode: present.pinCode,
      stateCode: matchedState?.stateCode ?? prev.stateCode,
    }))
  }

  function toggleSync(checked: boolean) {
    setForm((prev) => ({ ...prev, syncCurrentAddress: checked }))
    if (checked && form.addressLine1 === '') {
      prefillFromCurrentAddress()
    }
  }

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload: QuarterAllotmentRequest = {
        allotmentOrderNo: form.allotmentOrderNo || null,
        addressLine1: form.addressLine1,
        addressLine2: form.addressLine2 || null,
        city: form.city,
        stateCode: form.stateCode,
        pincode: form.pincode,
        syncCurrentAddress: form.syncCurrentAddress,
        licenseFee: Number(form.licenseFee),
        waterCharges: Number(form.waterCharges),
        electricCharges: Number(form.electricCharges),
        allottedFrom: form.allottedFrom,
        vacatedOn: vacationDateEnabled ? form.vacatedOn || null : null,
        status: form.status,
        remarks: form.remarks || null,
      }
      if (isEdit) {
        return (await apiClient.put(`/v1/employees/${employeeId}/quarter-allotments/${editing.id}`, payload)).data
      }
      return (await apiClient.post(`/v1/employees/${employeeId}/quarter-allotments`, payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['employee-quarter-allotments', employeeId] })
      if (form.syncCurrentAddress) {
        queryClient.invalidateQueries({ queryKey: ['employee-addresses', employeeId] })
      }
      show({ tone: 'success', message: isEdit ? 'Accommodation allotment updated.' : 'Accommodation allotted.' })
      onClose()
    },
  })
  const fieldErrors = getFieldErrors(saveMutation.error)

  const formValid =
    form.addressLine1.trim() !== '' &&
    form.city.trim() !== '' &&
    form.stateCode !== '' &&
    /^[1-9][0-9]{5}$/.test(form.pincode) &&
    form.allottedFrom !== '' &&
    form.licenseFee !== '' &&
    form.waterCharges !== '' &&
    form.electricCharges !== '' &&
    (!vacationDateEnabled || form.vacatedOn === '' || form.vacatedOn >= form.allottedFrom)

  return (
    <Modal title={isEdit ? 'Edit Accommodation Allotment' : 'Allot Company Accommodation'} onClose={onClose} maxWidthClassName="max-w-xl">
      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault()
          saveMutation.mutate()
        }}
      >
        {saveMutation.isError && (
          <FormErrorBanner title="Could not save" errors={describeApiErrorList(saveMutation.error, 'Could not save.')} />
        )}

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Allotment Order No.</label>
          <input
            value={form.allotmentOrderNo}
            onChange={(e) => setForm({ ...form, allotmentOrderNo: e.target.value })}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            placeholder="ORD/2026/001"
          />
        </div>

        {!isEdit && (
          <label className="flex items-center justify-between rounded-md border border-slate-200 p-3 text-sm text-slate-700">
            <span>Use / Sync with Employee Current Address</span>
            <input type="checkbox" role="switch" checked={form.syncCurrentAddress} onChange={(e) => toggleSync(e.target.checked)} />
          </label>
        )}
        {isEdit && (
          <label className="flex items-center justify-between rounded-md border border-slate-200 p-3 text-sm text-slate-700">
            <span>Keep synced with Employee Current Address</span>
            <input
              type="checkbox"
              role="switch"
              checked={form.syncCurrentAddress}
              onChange={(e) => setForm({ ...form, syncCurrentAddress: e.target.checked })}
            />
          </label>
        )}

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Address Line 1</label>
          <input
            required
            value={form.addressLine1}
            onChange={(e) => setForm({ ...form, addressLine1: e.target.value })}
            className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.addressLine1))}`}
          />
          <FieldError message={fieldErrors?.addressLine1} />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Address Line 2</label>
          <input
            value={form.addressLine2}
            onChange={(e) => setForm({ ...form, addressLine2: e.target.value })}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>

        <div className="grid grid-cols-3 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">City</label>
            <input
              required
              value={form.city}
              onChange={(e) => setForm({ ...form, city: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.city))}`}
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">State</label>
            <select
              required
              value={form.stateCode}
              onChange={(e) => setForm({ ...form, stateCode: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.stateCode))}`}
            >
              <option value="">Select state...</option>
              {(statesQuery.data?.content ?? []).map((s) => (
                <option key={s.id} value={s.stateCode}>
                  {s.stateName}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Pincode</label>
            <input
              required
              inputMode="numeric"
              maxLength={6}
              value={form.pincode}
              onChange={(e) => setForm({ ...form, pincode: e.target.value.replace(/\D/g, '').slice(0, 6) })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.pincode))}`}
            />
          </div>
        </div>

        <div>
          <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">Monthly Recoveries</p>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">License Fee / Rent (₹)</label>
              <input
                required
                type="number"
                min={0}
                step="0.01"
                value={form.licenseFee}
                onChange={(e) => setForm({ ...form, licenseFee: e.target.value })}
                className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.licenseFee))}`}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Water Charges (₹)</label>
              <input
                required
                type="number"
                min={0}
                step="0.01"
                value={form.waterCharges}
                onChange={(e) => setForm({ ...form, waterCharges: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Electric Charges (₹)</label>
              <input
                required
                type="number"
                min={0}
                step="0.01"
                value={form.electricCharges}
                onChange={(e) => setForm({ ...form, electricCharges: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Allotted From (Possession Date)</label>
            <DatePicker value={form.allottedFrom} onChange={(v) => setForm({ ...form, allottedFrom: v })} hasError={Boolean(fieldErrors?.allottedFrom)} />
            <FieldError message={fieldErrors?.allottedFrom} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Status</label>
            <select
              value={form.status}
              onChange={(e) => setForm({ ...form, status: e.target.value as QuarterAllotmentStatus })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Vacated On</label>
          <DatePicker value={form.vacatedOn} onChange={(v) => setForm({ ...form, vacatedOn: v })} disabled={!vacationDateEnabled} />
          {!vacationDateEnabled && <p className="mt-1 text-[11px] text-slate-400">Enabled once Status is Vacated, Surrendered, or Cancelled.</p>}
          {vacationDateEnabled && form.vacatedOn !== '' && form.vacatedOn < form.allottedFrom && (
            <p className="mt-1 text-xs text-rose-600">Must not be before Allotted From.</p>
          )}
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Remarks</label>
          <textarea
            value={form.remarks}
            onChange={(e) => setForm({ ...form, remarks: e.target.value })}
            rows={2}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>

        <PrimaryButton type="submit" disabled={saveMutation.isPending || !formValid} className="w-full justify-center">
          {isEdit ? 'Save Changes' : 'Allot Accommodation'}
        </PrimaryButton>
      </form>
    </Modal>
  )
}
