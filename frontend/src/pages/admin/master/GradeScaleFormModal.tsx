import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { useToast } from '../../../components/common/ToastProvider'
import { describeApiErrorList, getFieldErrors } from '../../../lib/apiError'
import { DatePicker } from '../../../components/common/DatePicker'
import { Modal } from '../../../components/common/Modal'
import { FormErrorBanner } from '../../../components/common/FormErrorBanner'
import { FieldError, PrimaryButton, errorInputClass } from '../../../components/common/ui'
import type { Cadre, GradeScaleCreateRequest, GradeScaleMasterResponse, GradeScaleUpdateRequest } from '../../../types/api'

type FormState = {
  scaleCode: string
  cadre: Cadre
  hierarchyLevel: string
  boardLevel: boolean
  minimumBasic: string
  maximumBasic: string
  incrementRate: string
  effectiveDate: string
  contractualLumpsum: string
  outsourcedCtc: string
  active: boolean
}

function emptyForm(): FormState {
  return {
    scaleCode: '',
    cadre: 'EXECUTIVE',
    hierarchyLevel: '',
    boardLevel: false,
    minimumBasic: '',
    maximumBasic: '',
    incrementRate: '3.00',
    effectiveDate: '',
    contractualLumpsum: '',
    outsourcedCtc: '',
    active: true,
  }
}

function formFromRow(row: GradeScaleMasterResponse): FormState {
  return {
    scaleCode: row.scaleCode,
    cadre: row.cadre,
    hierarchyLevel: String(row.hierarchyLevel),
    boardLevel: row.boardLevel,
    minimumBasic: String(row.minimumBasic),
    maximumBasic: String(row.maximumBasic),
    incrementRate: String(row.incrementRate),
    // effectiveDate on the response is already dd-MM-yyyy; DatePicker needs ISO, so convert dd-MM-yyyy -> yyyy-MM-dd for the editable input.
    effectiveDate: isoFromDisplay(row.effectiveDate),
    contractualLumpsum: row.contractualLumpsum != null ? String(row.contractualLumpsum) : '',
    outsourcedCtc: row.outsourcedCtc != null ? String(row.outsourcedCtc) : '',
    active: row.active,
  }
}

function isoFromDisplay(ddMMyyyy: string): string {
  const m = /^(\d{2})-(\d{2})-(\d{4})$/.exec(ddMMyyyy)
  return m ? `${m[3]}-${m[2]}-${m[1]}` : ''
}

/** Add/Edit modal for Grade Scale Master rows - POSTs to create, PUTs by scaleCode to edit (scaleCode itself is fixed once created). */
export function GradeScaleFormModal({ editing, onClose }: { editing: GradeScaleMasterResponse | null; onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<FormState>(editing ? formFromRow(editing) : emptyForm())
  const isEdit = editing !== null

  const saveMutation = useMutation({
    mutationFn: async () => {
      const shared: GradeScaleUpdateRequest = {
        cadre: form.cadre,
        hierarchyLevel: Number(form.hierarchyLevel),
        boardLevel: form.boardLevel,
        minimumBasic: Number(form.minimumBasic),
        maximumBasic: Number(form.maximumBasic),
        incrementRate: form.incrementRate ? Number(form.incrementRate) : null,
        effectiveDate: form.effectiveDate,
        contractualLumpsum: form.contractualLumpsum ? Number(form.contractualLumpsum) : null,
        outsourcedCtc: form.outsourcedCtc ? Number(form.outsourcedCtc) : null,
        active: form.active,
      }
      if (isEdit) {
        return (await apiClient.put(`/v1/masters/grade-scales/${editing.scaleCode}`, shared)).data
      }
      const payload: GradeScaleCreateRequest = { scaleCode: form.scaleCode, ...shared }
      return (await apiClient.post('/v1/masters/grade-scales', payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['grade-scales'] })
      show({ tone: 'success', message: isEdit ? `${editing.scaleCode} updated.` : `${form.scaleCode} created.` })
      onClose()
    },
  })
  const fieldErrors = getFieldErrors(saveMutation.error)

  const formValid =
    form.scaleCode.trim() !== '' &&
    form.hierarchyLevel !== '' &&
    Number(form.hierarchyLevel) >= 1 &&
    Number(form.hierarchyLevel) <= 15 &&
    form.minimumBasic !== '' &&
    form.maximumBasic !== '' &&
    Number(form.maximumBasic) >= Number(form.minimumBasic) &&
    form.effectiveDate !== ''

  const idaPreview =
    form.minimumBasic && form.maximumBasic
      ? `IDA ${Number(form.minimumBasic).toLocaleString('en-IN')}-${Number(form.maximumBasic).toLocaleString('en-IN')}`
      : '—'

  return (
    <Modal title={isEdit ? `Edit Grade Scale - ${editing.scaleCode}` : 'Add Grade Scale'} onClose={onClose} maxWidthClassName="max-w-xl">
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

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Scale Code</label>
            <input
              required
              disabled={isEdit}
              value={form.scaleCode}
              onChange={(e) => setForm({ ...form, scaleCode: e.target.value.toUpperCase() })}
              placeholder="E10"
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm uppercase disabled:bg-slate-50 disabled:text-slate-500 ${errorInputClass(Boolean(fieldErrors?.scaleCode))}`}
            />
            <FieldError message={fieldErrors?.scaleCode} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Role</label>
            <select
              value={form.cadre}
              onChange={(e) => setForm({ ...form, cadre: e.target.value as Cadre })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="BOARD">Board</option>
              <option value="EXECUTIVE">Executive</option>
              <option value="STAFF">Staff</option>
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Hierarchy Rank (1-15)</label>
            <input
              required
              type="number"
              min={1}
              max={15}
              value={form.hierarchyLevel}
              onChange={(e) => setForm({ ...form, hierarchyLevel: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.hierarchyLevel))}`}
            />
            <FieldError message={fieldErrors?.hierarchyLevel} />
          </div>
          <label className="flex items-center gap-2 pt-6 text-sm text-slate-600">
            <input type="checkbox" checked={form.boardLevel} onChange={(e) => setForm({ ...form, boardLevel: e.target.checked })} />
            Is Board Level
          </label>

          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Minimum Basic (₹)</label>
            <input
              required
              type="number"
              step="0.01"
              value={form.minimumBasic}
              onChange={(e) => setForm({ ...form, minimumBasic: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.minimumBasic))}`}
            />
            <FieldError message={fieldErrors?.minimumBasic} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Maximum Basic (₹)</label>
            <input
              required
              type="number"
              step="0.01"
              value={form.maximumBasic}
              onChange={(e) => setForm({ ...form, maximumBasic: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.maximumBasic))}`}
            />
            {form.minimumBasic !== '' && form.maximumBasic !== '' && Number(form.maximumBasic) < Number(form.minimumBasic) && (
              <p className="mt-1 text-xs text-red-600">Must not be less than minimum basic.</p>
            )}
            <FieldError message={fieldErrors?.maximumBasic} />
          </div>

          <div className="col-span-2">
            <label className="mb-1 block text-xs font-medium text-slate-600">Regular Scale (as per IDA Pay pattern)</label>
            <p className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600">{idaPreview}</p>
            <p className="mt-1 text-xs text-slate-400">Computed automatically from Minimum/Maximum Basic - not directly editable.</p>
          </div>

          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Increment Rate (%)</label>
            <input
              type="number"
              step="0.01"
              value={form.incrementRate}
              onChange={(e) => setForm({ ...form, incrementRate: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Effective Date</label>
            <DatePicker value={form.effectiveDate} onChange={(v) => setForm({ ...form, effectiveDate: v })} hasError={Boolean(fieldErrors?.effectiveDate)} />
            <FieldError message={fieldErrors?.effectiveDate} />
          </div>

          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Base Contractual Lumpsum (₹)</label>
            <input
              type="number"
              step="0.01"
              value={form.contractualLumpsum}
              onChange={(e) => setForm({ ...form, contractualLumpsum: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Base Outsourced CTC (₹)</label>
            <input
              type="number"
              step="0.01"
              value={form.outsourcedCtc}
              onChange={(e) => setForm({ ...form, outsourcedCtc: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>

          <label className="col-span-2 flex items-center gap-2 text-sm text-slate-600">
            <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
            Active
          </label>
        </div>

        <PrimaryButton type="submit" disabled={saveMutation.isPending || !formValid} className="w-full justify-center">
          {isEdit ? 'Save Changes' : 'Create Grade Scale'}
        </PrimaryButton>
      </form>
    </Modal>
  )
}
