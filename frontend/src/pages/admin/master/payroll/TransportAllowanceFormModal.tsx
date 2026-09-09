import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../../../api/client'
import { useToast } from '../../../../components/common/ToastProvider'
import { describeApiErrorList, getFieldErrors } from '../../../../lib/apiError'
import { DatePicker } from '../../../../components/common/DatePicker'
import { Modal } from '../../../../components/common/Modal'
import { FormErrorBanner } from '../../../../components/common/FormErrorBanner'
import { FieldError, PrimaryButton, errorInputClass } from '../../../../components/common/ui'
import type { CityClass, GradeScaleMasterResponse, TransportAllowanceRequest, TransportAllowanceResponse } from '../../../../types/api'

type FormState = {
  gradeScaleId: string
  cityClass: CityClass
  baseRate: string
  effectiveFrom: string
}

function emptyForm(): FormState {
  return { gradeScaleId: '', cityClass: 'X', baseRate: '', effectiveFrom: '' }
}

function formFromRow(row: TransportAllowanceResponse): FormState {
  return {
    gradeScaleId: row.gradeScaleId != null ? String(row.gradeScaleId) : '',
    cityClass: row.cityClass,
    baseRate: String(row.baseRate),
    effectiveFrom: row.effectiveFrom,
  }
}

/** Add/Edit modal for Transport Allowance Master rows. */
export function TransportAllowanceFormModal({ editing, onClose }: { editing: TransportAllowanceResponse | null; onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<FormState>(editing ? formFromRow(editing) : emptyForm())
  const isEdit = editing !== null

  const { data: gradeScales } = useQuery({
    queryKey: ['grade-scales'],
    queryFn: async () => (await apiClient.get<GradeScaleMasterResponse[]>('/v1/masters/grade-scales')).data,
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload: TransportAllowanceRequest = {
        gradeScaleId: Number(form.gradeScaleId),
        cityClass: form.cityClass,
        baseRate: Number(form.baseRate),
        effectiveFrom: form.effectiveFrom,
      }
      if (isEdit) {
        return (await apiClient.put(`/v1/payroll/masters/transport-allowances/${editing.id}`, payload)).data
      }
      return (await apiClient.post('/v1/payroll/masters/transport-allowances', payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payroll-transport-allowances'] })
      show({ tone: 'success', message: isEdit ? 'Transport allowance rate updated.' : 'Transport allowance rate created.' })
      onClose()
    },
  })
  const fieldErrors = getFieldErrors(saveMutation.error)

  const formValid = form.gradeScaleId !== '' && form.baseRate !== '' && Number(form.baseRate) >= 0 && form.effectiveFrom !== ''

  return (
    <Modal title={isEdit ? 'Edit Transport Allowance Rate' : 'Add Transport Allowance Rate'} onClose={onClose} maxWidthClassName="max-w-lg">
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
          <label className="mb-1 block text-xs font-medium text-slate-600">Grade Scale</label>
          <select
            required
            value={form.gradeScaleId}
            onChange={(e) => setForm({ ...form, gradeScaleId: e.target.value })}
            className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.gradeScaleId))}`}
          >
            <option value="">Select grade scale...</option>
            {(gradeScales ?? []).map((gs) => (
              <option key={gs.id} value={gs.id}>
                {gs.scaleCode} ({gs.cadre})
              </option>
            ))}
          </select>
          <FieldError message={fieldErrors?.gradeScaleId} />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">City Class</label>
          <div className="flex gap-2">
            {(['X', 'Y', 'Z'] as CityClass[]).map((cc) => (
              <button
                key={cc}
                type="button"
                onClick={() => setForm({ ...form, cityClass: cc })}
                className={`flex-1 rounded-md border px-3 py-2 text-sm font-medium ${
                  form.cityClass === cc ? 'border-brand-forest bg-brand-forest text-white' : 'border-slate-300 text-slate-600 hover:bg-slate-50'
                }`}
              >
                Class {cc}
              </button>
            ))}
          </div>
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Base Rate (₹/month)</label>
          <input
            required
            type="number"
            min={0}
            step="0.01"
            value={form.baseRate}
            onChange={(e) => setForm({ ...form, baseRate: e.target.value })}
            className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.baseRate))}`}
          />
          <FieldError message={fieldErrors?.baseRate} />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Effective Date</label>
          <DatePicker value={form.effectiveFrom} onChange={(v) => setForm({ ...form, effectiveFrom: v })} hasError={Boolean(fieldErrors?.effectiveFrom)} />
          <FieldError message={fieldErrors?.effectiveFrom} />
        </div>

        <PrimaryButton type="submit" disabled={saveMutation.isPending || !formValid} className="w-full justify-center">
          {isEdit ? 'Save Changes' : 'Create Rate'}
        </PrimaryButton>
      </form>
    </Modal>
  )
}
