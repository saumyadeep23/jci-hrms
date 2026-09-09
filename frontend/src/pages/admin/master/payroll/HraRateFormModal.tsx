import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../../../api/client'
import { useToast } from '../../../../components/common/ToastProvider'
import { describeApiErrorList, getFieldErrors } from '../../../../lib/apiError'
import { DatePicker } from '../../../../components/common/DatePicker'
import { Modal } from '../../../../components/common/Modal'
import { FormErrorBanner } from '../../../../components/common/FormErrorBanner'
import { FieldError, PrimaryButton, errorInputClass } from '../../../../components/common/ui'
import type { CityClass, PayrollHraRateRequest, PayrollHraRateResponse } from '../../../../types/api'

const CITY_CLASS_LABELS: Record<CityClass, string> = {
  X: 'X - Metros',
  Y: 'Y - Tier 2',
  Z: 'Z - Field/Rural/DPCs',
}

type FormState = {
  cityClass: CityClass
  ratePercentage: string
  minAmount: string
  effectiveFrom: string
  effectiveTo: string
  remarks: string
}

function emptyForm(): FormState {
  return { cityClass: 'X', ratePercentage: '', minAmount: '', effectiveFrom: '', effectiveTo: '', remarks: '' }
}

function formFromRow(row: PayrollHraRateResponse): FormState {
  return {
    cityClass: row.cityClass,
    ratePercentage: String(row.ratePercentage),
    minAmount: String(row.minAmount),
    effectiveFrom: row.effectiveFrom,
    effectiveTo: row.effectiveTo ?? '',
    remarks: row.remarks ?? '',
  }
}

/** Add/Edit modal for an HRA Rate Master slab - also how a slab is closed out (set Effective To and save). */
export function HraRateFormModal({ editing, onClose }: { editing: PayrollHraRateResponse | null; onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<FormState>(editing ? formFromRow(editing) : emptyForm())
  const isEdit = editing !== null

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload: PayrollHraRateRequest = {
        cityClass: form.cityClass,
        ratePercentage: Number(form.ratePercentage),
        minAmount: Number(form.minAmount),
        effectiveFrom: form.effectiveFrom,
        effectiveTo: form.effectiveTo || null,
        remarks: form.remarks || null,
      }
      if (isEdit) {
        return (await apiClient.put(`/v1/payroll/masters/hra-rates/${editing.id}`, payload)).data
      }
      return (await apiClient.post('/v1/payroll/masters/hra-rates', payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payroll-hra-rates'] })
      show({ tone: 'success', message: isEdit ? 'HRA rate updated.' : 'HRA rate created.' })
      onClose()
    },
  })
  const fieldErrors = getFieldErrors(saveMutation.error)

  const formValid =
    form.ratePercentage !== '' &&
    Number(form.ratePercentage) >= 0 &&
    Number(form.ratePercentage) <= 100 &&
    form.minAmount !== '' &&
    Number(form.minAmount) >= 0 &&
    form.effectiveFrom !== ''

  return (
    <Modal title={isEdit ? 'Edit HRA Rate' : 'Add HRA Rate'} onClose={onClose} maxWidthClassName="max-w-lg">
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
          <label className="mb-1 block text-xs font-medium text-slate-600">City Class</label>
          <select
            value={form.cityClass}
            onChange={(e) => setForm({ ...form, cityClass: e.target.value as CityClass })}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            {(['X', 'Y', 'Z'] as CityClass[]).map((cc) => (
              <option key={cc} value={cc}>
                {CITY_CLASS_LABELS[cc]}
              </option>
            ))}
          </select>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Rate Percentage (%)</label>
            <input
              required
              type="number"
              min={0}
              max={100}
              step={0.5}
              value={form.ratePercentage}
              onChange={(e) => setForm({ ...form, ratePercentage: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.ratePercentage))}`}
            />
            <FieldError message={fieldErrors?.ratePercentage} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Minimum Floor (₹)</label>
            <input
              required
              type="number"
              min={0}
              step="0.01"
              value={form.minAmount}
              onChange={(e) => setForm({ ...form, minAmount: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.minAmount))}`}
            />
            <FieldError message={fieldErrors?.minAmount} />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Effective From</label>
            <DatePicker value={form.effectiveFrom} onChange={(v) => setForm({ ...form, effectiveFrom: v })} hasError={Boolean(fieldErrors?.effectiveFrom)} />
            <FieldError message={fieldErrors?.effectiveFrom} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Effective To (optional)</label>
            <DatePicker value={form.effectiveTo} onChange={(v) => setForm({ ...form, effectiveTo: v })} />
          </div>
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Remarks</label>
          <textarea
            value={form.remarks}
            onChange={(e) => setForm({ ...form, remarks: e.target.value })}
            rows={3}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>

        <PrimaryButton type="submit" disabled={saveMutation.isPending || !formValid} className="w-full justify-center">
          {isEdit ? 'Save Changes' : 'Create Rate'}
        </PrimaryButton>
      </form>
    </Modal>
  )
}
