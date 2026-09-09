import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../../../api/client'
import { useToast } from '../../../../components/common/ToastProvider'
import { describeApiErrorList, getFieldErrors } from '../../../../lib/apiError'
import { DatePicker } from '../../../../components/common/DatePicker'
import { Modal } from '../../../../components/common/Modal'
import { FormErrorBanner } from '../../../../components/common/FormErrorBanner'
import { FieldError, PrimaryButton, errorInputClass } from '../../../../components/common/ui'
import type { Page, PtaxSlabRequest, PtaxSlabResponse, StateMasterResponse } from '../../../../types/api'

const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

type FormState = {
  stateCode: string
  slabMin: string
  slabMax: string
  taxAmount: string
  specialMonth: string
  specialMonthTax: string
  effectiveFrom: string
}

function emptyForm(): FormState {
  return { stateCode: '', slabMin: '', slabMax: '', taxAmount: '', specialMonth: '', specialMonthTax: '', effectiveFrom: '' }
}

function formFromRow(row: PtaxSlabResponse): FormState {
  return {
    stateCode: row.stateCode,
    slabMin: String(row.slabMin),
    slabMax: row.slabMax != null ? String(row.slabMax) : '',
    taxAmount: String(row.taxAmount),
    specialMonth: row.specialMonth != null ? String(row.specialMonth) : '',
    specialMonthTax: row.specialMonthTax != null ? String(row.specialMonthTax) : '',
    effectiveFrom: row.effectiveFrom,
  }
}

/** Add/Edit modal for a Professional Tax slab - min/max bracket, standard monthly deduction, and an optional peak-surcharge month (e.g. February for WB, March for Assam/Odisha/Bihar). */
export function PtaxSlabFormModal({ editing, onClose }: { editing: PtaxSlabResponse | null; onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<FormState>(editing ? formFromRow(editing) : emptyForm())
  const isEdit = editing !== null

  const { data: states } = useQuery({
    queryKey: ['admin-masters-states-options'],
    queryFn: async () => (await apiClient.get<Page<StateMasterResponse>>('/v1/admin/masters/states', { params: { size: 500 } })).data,
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload: PtaxSlabRequest = {
        stateCode: form.stateCode,
        slabMin: Number(form.slabMin),
        slabMax: form.slabMax ? Number(form.slabMax) : null,
        taxAmount: Number(form.taxAmount),
        specialMonth: form.specialMonth ? Number(form.specialMonth) : null,
        specialMonthTax: form.specialMonthTax ? Number(form.specialMonthTax) : null,
        effectiveFrom: form.effectiveFrom,
      }
      if (isEdit) {
        return (await apiClient.put(`/v1/payroll/masters/ptax-slabs/${editing.id}`, payload)).data
      }
      return (await apiClient.post('/v1/payroll/masters/ptax-slabs', payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payroll-ptax-slabs'] })
      show({ tone: 'success', message: isEdit ? 'P-Tax slab updated.' : 'P-Tax slab created.' })
      onClose()
    },
  })
  const fieldErrors = getFieldErrors(saveMutation.error)

  const slabOrderInvalid = form.slabMax !== '' && Number(form.slabMax) <= Number(form.slabMin)
  const formValid =
    form.stateCode !== '' && form.slabMin !== '' && form.taxAmount !== '' && form.effectiveFrom !== '' && !slabOrderInvalid

  return (
    <Modal title={isEdit ? 'Edit P-Tax Slab' : 'Add P-Tax Slab'} onClose={onClose} maxWidthClassName="max-w-lg">
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
          <label className="mb-1 block text-xs font-medium text-slate-600">State</label>
          <select
            required
            disabled={isEdit}
            value={form.stateCode}
            onChange={(e) => setForm({ ...form, stateCode: e.target.value })}
            className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-50 disabled:text-slate-500 ${errorInputClass(Boolean(fieldErrors?.stateCode))}`}
          >
            <option value="">Select state...</option>
            {(states?.content ?? []).map((s) => (
              <option key={s.id} value={s.stateCode}>
                {s.stateName} ({s.stateCode})
              </option>
            ))}
          </select>
          <FieldError message={fieldErrors?.stateCode} />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Slab Min (₹)</label>
            <input
              required
              type="number"
              min={0}
              step="0.01"
              value={form.slabMin}
              onChange={(e) => setForm({ ...form, slabMin: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.slabMin))}`}
            />
            <FieldError message={fieldErrors?.slabMin} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Slab Max (₹, blank = no upper limit)</label>
            <input
              type="number"
              min={0}
              step="0.01"
              value={form.slabMax}
              onChange={(e) => setForm({ ...form, slabMax: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.slabMax) || slabOrderInvalid)}`}
            />
            {slabOrderInvalid && <p className="mt-1 text-xs text-rose-600">Must be greater than slab min.</p>}
            <FieldError message={fieldErrors?.slabMax} />
          </div>
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Standard Monthly Deduction (₹)</label>
          <input
            required
            type="number"
            min={0}
            step="0.01"
            value={form.taxAmount}
            onChange={(e) => setForm({ ...form, taxAmount: e.target.value })}
            className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.taxAmount))}`}
          />
          <FieldError message={fieldErrors?.taxAmount} />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Peak Surcharge Month</label>
            <select
              value={form.specialMonth}
              onChange={(e) => setForm({ ...form, specialMonth: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="">None</option>
              {MONTHS.map((name, idx) => (
                <option key={name} value={idx + 1}>
                  {name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Peak Month Deduction (₹)</label>
            <input
              type="number"
              min={0}
              step="0.01"
              disabled={!form.specialMonth}
              value={form.specialMonthTax}
              onChange={(e) => setForm({ ...form, specialMonthTax: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-50"
            />
          </div>
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Effective Date</label>
          <DatePicker value={form.effectiveFrom} onChange={(v) => setForm({ ...form, effectiveFrom: v })} hasError={Boolean(fieldErrors?.effectiveFrom)} />
          <FieldError message={fieldErrors?.effectiveFrom} />
        </div>

        <PrimaryButton type="submit" disabled={saveMutation.isPending || !formValid} className="w-full justify-center">
          {isEdit ? 'Save Changes' : 'Create Slab'}
        </PrimaryButton>
      </form>
    </Modal>
  )
}
