import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../../../api/client'
import { useToast } from '../../../../components/common/ToastProvider'
import { describeApiErrorList, getFieldErrors } from '../../../../lib/apiError'
import { DatePicker } from '../../../../components/common/DatePicker'
import { Modal } from '../../../../components/common/Modal'
import { FormErrorBanner } from '../../../../components/common/FormErrorBanner'
import { FieldError, PrimaryButton, errorInputClass } from '../../../../components/common/ui'
import type { DesignationResponse, Page, ProcurementAllowanceRequest, ProcurementAllowanceResponse } from '../../../../types/api'

type FormState = {
  designationId: string
  monthlyAllowance: string
  effectiveFrom: string
}

function emptyForm(): FormState {
  return { designationId: '', monthlyAllowance: '', effectiveFrom: '' }
}

function formFromRow(row: ProcurementAllowanceResponse): FormState {
  return {
    designationId: String(row.designationId),
    monthlyAllowance: String(row.monthlyAllowance),
    effectiveFrom: row.effectiveFrom,
  }
}

/** Add/Edit modal for Procurement Allowance Master rows - designation search dropdown + monthly amount. */
export function ProcurementAllowanceFormModal({ editing, onClose }: { editing: ProcurementAllowanceResponse | null; onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<FormState>(editing ? formFromRow(editing) : emptyForm())
  const [designationSearch, setDesignationSearch] = useState('')
  const isEdit = editing !== null

  const { data: designations } = useQuery({
    queryKey: ['admin-masters-designations-options'],
    queryFn: async () => (await apiClient.get<Page<DesignationResponse>>('/v1/admin/masters/designations', { params: { size: 500 } })).data,
  })

  const filteredDesignations = (designations?.content ?? []).filter((d) =>
    d.title.toLowerCase().includes(designationSearch.trim().toLowerCase()),
  )

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload: ProcurementAllowanceRequest = {
        designationId: Number(form.designationId),
        monthlyAllowance: Number(form.monthlyAllowance),
        effectiveFrom: form.effectiveFrom,
      }
      if (isEdit) {
        return (await apiClient.put(`/v1/payroll/masters/procurement-allowances/${editing.id}`, payload)).data
      }
      return (await apiClient.post('/v1/payroll/masters/procurement-allowances', payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payroll-procurement-allowances'] })
      show({ tone: 'success', message: isEdit ? 'Procurement allowance updated.' : 'Procurement allowance created.' })
      onClose()
    },
  })
  const fieldErrors = getFieldErrors(saveMutation.error)

  const formValid = form.designationId !== '' && form.monthlyAllowance !== '' && Number(form.monthlyAllowance) >= 0 && form.effectiveFrom !== ''

  return (
    <Modal title={isEdit ? 'Edit Procurement Allowance' : 'Add Procurement Allowance'} onClose={onClose} maxWidthClassName="max-w-lg">
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
          <label className="mb-1 block text-xs font-medium text-slate-600">Designation</label>
          <input
            placeholder="Search designation..."
            value={designationSearch}
            onChange={(e) => setDesignationSearch(e.target.value)}
            className="mb-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
          <select
            required
            size={5}
            value={form.designationId}
            onChange={(e) => setForm({ ...form, designationId: e.target.value })}
            className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.designationId))}`}
          >
            {filteredDesignations.map((d) => (
              <option key={d.id} value={d.id}>
                {d.title}
              </option>
            ))}
          </select>
          <FieldError message={fieldErrors?.designationId} />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Monthly Allowance (₹)</label>
          <input
            required
            type="number"
            min={0}
            step="0.01"
            value={form.monthlyAllowance}
            onChange={(e) => setForm({ ...form, monthlyAllowance: e.target.value })}
            className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.monthlyAllowance))}`}
          />
          <FieldError message={fieldErrors?.monthlyAllowance} />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Effective Date</label>
          <DatePicker value={form.effectiveFrom} onChange={(v) => setForm({ ...form, effectiveFrom: v })} hasError={Boolean(fieldErrors?.effectiveFrom)} />
          <FieldError message={fieldErrors?.effectiveFrom} />
        </div>

        <PrimaryButton type="submit" disabled={saveMutation.isPending || !formValid} className="w-full justify-center">
          {isEdit ? 'Save Changes' : 'Create Allowance'}
        </PrimaryButton>
      </form>
    </Modal>
  )
}
