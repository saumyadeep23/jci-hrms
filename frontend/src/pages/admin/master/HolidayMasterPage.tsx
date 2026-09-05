import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, Pencil, Plus, Trash2, Upload, X } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { describeApiError } from '../../../lib/apiError'
import { formatDate } from '../../../lib/date'
import { DateField } from '../../../components/common/DateField'
import { Modal } from '../../../components/common/Modal'
import { useToast } from '../../../components/common/ToastProvider'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../../components/common/ui'
import type {
  HolidayBulkUploadResult,
  HolidayMasterCreateRequest,
  HolidayMasterRow,
  HolidayMasterUpdateRequest,
  HolidayType,
  StateOptionResponse,
} from '../../../types/api'

const ALL_MARKER = 'ALL'
const NATIONAL_LABEL = 'All India / Central'
const WEEKDAY_NAMES = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
const CSV_HEADER = 'holidayDate,holidayName,holidayType,stateCodes,isRestricted'
const CSV_TEMPLATE_SAMPLE =
  '2026-01-26,Republic Day,GAZETTED,ALL,false\n2026-10-02,Regional RH,RESTRICTED,WB;BR,true'

/** Parses a plain "yyyy-MM-dd" string as a LOCAL date (never through `new Date(string)`, which treats a date-only string as UTC midnight and can render as the previous day). */
function dayOfWeekLabel(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  return WEEKDAY_NAMES[new Date(y, (m ?? 1) - 1, d ?? 1).getDay()]
}

function downloadCsvTemplate() {
  const blob = new Blob([CSV_HEADER + '\n' + CSV_TEMPLATE_SAMPLE], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'holiday-rh-gazette-template.csv'
  link.click()
  URL.revokeObjectURL(url)
}

interface FormState {
  holidayName: string
  holidayDate: string
  holidayType: HolidayType
  stateCodes: string[]
  description: string
}

const EMPTY_FORM: FormState = { holidayName: '', holidayDate: '', holidayType: 'GAZETTED', stateCodes: [], description: '' }

/**
 * ALMS State-wise Yearly Holiday & RH Management Publisher, restricted to
 * HR_ADMIN/SUPER_ADMIN at the route level (see App.tsx). Backend rows stay
 * single-state (POST fans a multi-state create out into one row per state,
 * see HolidayMasterService) - editing an existing row is therefore
 * single-state-scoped even though creating is multi-select.
 */
export function HolidayMasterPage() {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const now = new Date()
  const yearOptions = Array.from({ length: 4 }, (_, i) => now.getFullYear() - 1 + i)

  const [year, setYear] = useState(now.getFullYear())
  const [stateFilter, setStateFilter] = useState(ALL_MARKER)
  const [typeFilter, setTypeFilter] = useState<'ALL' | HolidayType>('ALL')
  const [modalMode, setModalMode] = useState<'create' | 'edit' | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [uploading, setUploading] = useState(false)
  const [uploadResult, setUploadResult] = useState<HolidayBulkUploadResult | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<HolidayMasterRow | null>(null)

  const statesQuery = useQuery({
    queryKey: ['master-states'],
    queryFn: async () => (await apiClient.get<StateOptionResponse[]>('/v1/master/states')).data,
  })

  const holidaysQuery = useQuery({
    queryKey: ['holiday-master', year, stateFilter, typeFilter],
    queryFn: async () =>
      (
        await apiClient.get<HolidayMasterRow[]>('/v1/master/holidays', {
          params: { year, stateCode: stateFilter, type: typeFilter === 'ALL' ? undefined : typeFilter },
        })
      ).data,
  })

  const createMutation = useMutation({
    mutationFn: async () => {
      const payload: HolidayMasterCreateRequest = {
        holidayName: form.holidayName,
        holidayDate: form.holidayDate,
        holidayType: form.holidayType,
        stateCodes: form.stateCodes,
        description: form.description || null,
      }
      await apiClient.post('/v1/master/holidays', payload)
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'Holiday(s) created.' })
      queryClient.invalidateQueries({ queryKey: ['holiday-master'] })
      closeModal()
    },
    onError: (error) => show({ tone: 'error', message: describeApiError(error, 'Could not create this holiday.') }),
  })

  const updateMutation = useMutation({
    mutationFn: async () => {
      if (editingId == null) return
      const payload: HolidayMasterUpdateRequest = {
        holidayName: form.holidayName,
        holidayDate: form.holidayDate,
        holidayType: form.holidayType,
        stateCode: form.stateCodes[0] ?? ALL_MARKER,
        description: form.description || null,
      }
      await apiClient.put(`/v1/master/holidays/${editingId}`, payload)
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'Holiday updated.' })
      queryClient.invalidateQueries({ queryKey: ['holiday-master'] })
      closeModal()
    },
    onError: (error) => show({ tone: 'error', message: describeApiError(error, 'Could not update this holiday.') }),
  })

  const deleteMutation = useMutation({
    mutationFn: async (id: number) => {
      await apiClient.delete(`/v1/master/holidays/${id}`)
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'Holiday deleted.' })
      queryClient.invalidateQueries({ queryKey: ['holiday-master'] })
      setDeleteTarget(null)
    },
    onError: (error) => {
      show({ tone: 'error', message: describeApiError(error, 'Could not delete this holiday - it may fall within a locked payroll cycle.') })
      setDeleteTarget(null)
    },
  })

  function openCreate() {
    setForm(EMPTY_FORM)
    setEditingId(null)
    setModalMode('create')
  }

  function openEdit(row: HolidayMasterRow) {
    setForm({
      holidayName: row.holidayName,
      holidayDate: row.holidayDate,
      holidayType: row.holidayType,
      stateCodes: [row.stateCode],
      description: row.description ?? '',
    })
    setEditingId(row.id)
    setModalMode('edit')
  }

  function closeModal() {
    setModalMode(null)
    setEditingId(null)
    setForm(EMPTY_FORM)
  }

  const allActiveCodes = useMemo(() => (statesQuery.data ?? []).map((s) => s.stateCode), [statesQuery.data])
  const allSelected = form.stateCodes.includes(ALL_MARKER) || (allActiveCodes.length > 0 && allActiveCodes.every((c) => form.stateCodes.includes(c)))

  function toggleState(code: string) {
    if (modalMode === 'edit') {
      setForm((prev) => ({ ...prev, stateCodes: [code] }))
      return
    }
    setForm((prev) => {
      const withoutAll = prev.stateCodes.filter((c) => c !== ALL_MARKER)
      return { ...prev, stateCodes: withoutAll.includes(code) ? withoutAll.filter((c) => c !== code) : [...withoutAll, code] }
    })
  }

  function toggleSelectAll() {
    if (modalMode === 'edit') {
      setForm((prev) => ({ ...prev, stateCodes: prev.stateCodes.includes(ALL_MARKER) ? [] : [ALL_MARKER] }))
      return
    }
    setForm((prev) => ({ ...prev, stateCodes: allSelected ? [] : [ALL_MARKER] }))
  }

  function removeChip(code: string) {
    setForm((prev) => ({ ...prev, stateCodes: prev.stateCodes.filter((c) => c !== code) }))
  }

  async function handleCsvFile(file: File) {
    setUploading(true)
    setUploadResult(null)
    try {
      const formData = new FormData()
      formData.append('file', file)
      const response = await apiClient.post<HolidayBulkUploadResult>('/v1/master/holidays/bulk-upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      setUploadResult(response.data)
      queryClient.invalidateQueries({ queryKey: ['holiday-master'] })
      show({
        tone: response.data.errors.length === 0 ? 'success' : 'error',
        message: `${response.data.createdRows} of ${response.data.totalRows} rows imported.`,
      })
    } catch (error) {
      show({ tone: 'error', message: describeApiError(error, 'CSV upload failed.') })
    } finally {
      setUploading(false)
    }
  }

  const canSubmit = form.holidayName.trim() !== '' && form.holidayDate !== '' && form.stateCodes.length > 0

  return (
    <div>
      <PageHeader title="Holiday & RH Calendars" description="Manage yearly Central &amp; State gazetted holidays and restricted holiday (RH) quotas" />

      <Card className="mb-4">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div className="flex flex-wrap items-end gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Year</label>
              <select value={year} onChange={(e) => setYear(Number(e.target.value))} className="rounded-md border border-slate-300 px-2 py-1.5 text-sm">
                {yearOptions.map((y) => (
                  <option key={y} value={y}>
                    {y}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">State</label>
              <select value={stateFilter} onChange={(e) => setStateFilter(e.target.value)} className="rounded-md border border-slate-300 px-2 py-1.5 text-sm">
                <option value={ALL_MARKER}>{NATIONAL_LABEL}</option>
                {(statesQuery.data ?? []).map((s) => (
                  <option key={s.stateCode} value={s.stateCode}>
                    {s.stateName}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Type</label>
              <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value as 'ALL' | HolidayType)} className="rounded-md border border-slate-300 px-2 py-1.5 text-sm">
                <option value="ALL">All</option>
                <option value="GAZETTED">Gazetted</option>
                <option value="RESTRICTED">Restricted Holiday (RH)</option>
              </select>
            </div>
          </div>

          <div className="flex flex-wrap gap-2">
            <SecondaryButton type="button" onClick={downloadCsvTemplate}>
              <Download size={14} /> Download Template
            </SecondaryButton>
            <label className="inline-flex cursor-pointer items-center gap-1.5 rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 shadow-sm hover:bg-slate-50">
              <Upload size={14} /> {uploading ? 'Uploading...' : 'Upload Annual Gazette CSV'}
              <input
                type="file"
                accept=".csv"
                disabled={uploading}
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0]
                  if (file) handleCsvFile(file)
                  e.target.value = ''
                }}
              />
            </label>
            <PrimaryButton type="button" onClick={openCreate}>
              <Plus size={14} /> Add Holiday / RH
            </PrimaryButton>
          </div>
        </div>

        {uploadResult && uploadResult.errors.length > 0 && (
          <div className="mt-3 rounded-md border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800">
            <p className="font-medium">{uploadResult.createdRows} of {uploadResult.totalRows} rows imported. Issues:</p>
            <ul className="mt-1 list-disc pl-4">
              {uploadResult.errors.map((err) => (
                <li key={err}>{err}</li>
              ))}
            </ul>
          </div>
        )}
      </Card>

      <Card>
        {holidaysQuery.isLoading && <LoadingState label="Loading holidays..." />}
        {holidaysQuery.isError && <ErrorState message="Could not load the holiday calendar." />}
        {holidaysQuery.data && holidaysQuery.data.length === 0 && <EmptyState message="No holidays configured for this filter." />}
        {holidaysQuery.data && holidaysQuery.data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[880px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Date</th>
                  <th className="py-2 pr-3">Day</th>
                  <th className="py-2 pr-3">Holiday Name</th>
                  <th className="py-2 pr-3">Category</th>
                  <th className="py-2 pr-3">State / Scope</th>
                  <th className="py-2 pl-3">Actions</th>
                </tr>
              </thead>
              <tbody>
                {holidaysQuery.data.map((row) => (
                  <tr key={row.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3 font-medium tabular-nums">{formatDate(row.holidayDate)}</td>
                    <td className="py-2 pr-3 text-slate-500">{dayOfWeekLabel(row.holidayDate)}</td>
                    <td className="py-2 pr-3">{row.holidayName}</td>
                    <td className="py-2 pr-3">
                      <span
                        className={`inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium ${
                          row.holidayType === 'GAZETTED'
                            ? 'border-emerald-300 bg-emerald-100 text-emerald-800'
                            : 'border-amber-300 bg-amber-100 text-amber-900'
                        }`}
                      >
                        {row.holidayType === 'GAZETTED' ? 'Gazetted' : 'Restricted (RH)'}
                      </span>
                    </td>
                    <td className="py-2 pr-3">
                      <Badge tone={row.stateCode === ALL_MARKER ? 'brand' : 'neutral'}>{row.stateName}</Badge>
                    </td>
                    <td className="py-2 pl-3">
                      <div className="flex gap-1.5">
                        <button
                          type="button"
                          onClick={() => openEdit(row)}
                          className="inline-flex items-center gap-1 rounded-md border border-slate-300 px-2 py-1 text-xs font-medium text-slate-600 hover:bg-slate-50"
                        >
                          <Pencil size={12} /> Edit
                        </button>
                        <button
                          type="button"
                          onClick={() => setDeleteTarget(row)}
                          className="inline-flex items-center gap-1 rounded-md border border-red-300 bg-red-50 px-2 py-1 text-xs font-medium text-red-700 hover:bg-red-100"
                        >
                          <Trash2 size={12} /> Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {modalMode && (
        <Modal title={modalMode === 'create' ? 'Add Holiday / RH' : 'Edit Holiday / RH'} onClose={closeModal}>
          <div className="space-y-4">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Holiday Name *</label>
              <input
                value={form.holidayName}
                onChange={(e) => setForm({ ...form, holidayName: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                placeholder="Republic Day"
              />
            </div>

            <DateField label="Date *" value={form.holidayDate} onChange={(v) => setForm({ ...form, holidayDate: v })} />

            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Category</label>
              <div className="flex gap-2">
                {(['GAZETTED', 'RESTRICTED'] as const).map((t) => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => setForm({ ...form, holidayType: t })}
                    className={`rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${
                      form.holidayType === t ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'
                    }`}
                  >
                    {t === 'GAZETTED' ? 'Gazetted' : 'Restricted Holiday (RH)'}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">
                {modalMode === 'create' ? 'Applicable States' : 'Applicable State'}
              </label>
              <label className="mb-2 flex items-center gap-2 rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium text-slate-700">
                <input type="checkbox" checked={allSelected} onChange={toggleSelectAll} />
                Select All / Central (All India)
              </label>

              {form.stateCodes.length > 0 && (
                <div className="mb-2 flex flex-wrap gap-1.5">
                  {form.stateCodes.map((code) => {
                    const label = code === ALL_MARKER ? NATIONAL_LABEL : (statesQuery.data ?? []).find((s) => s.stateCode === code)?.stateName ?? code
                    return (
                      <span key={code} className="inline-flex items-center gap-1 rounded-full bg-brand-forest/10 px-2.5 py-1 text-xs font-medium text-brand-forest">
                        {label}
                        <button type="button" onClick={() => removeChip(code)} aria-label={`Remove ${label}`}>
                          <X size={12} />
                        </button>
                      </span>
                    )
                  })}
                </div>
              )}

              <div className="grid max-h-40 grid-cols-2 gap-1 overflow-y-auto rounded-md border border-slate-200 p-2">
                {(statesQuery.data ?? []).map((s) => (
                  <label key={s.stateCode} className="flex items-center gap-2 px-1 py-1 text-sm text-slate-600">
                    <input
                      type={modalMode === 'edit' ? 'radio' : 'checkbox'}
                      checked={form.stateCodes.includes(s.stateCode)}
                      onChange={() => toggleState(s.stateCode)}
                    />
                    {s.stateName}
                  </label>
                ))}
              </div>
              {form.stateCodes.length === 0 && <p className="mt-1 text-xs font-medium text-red-600">Select at least one state, or Central/All India.</p>}
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Description / Reference Notes</label>
              <textarea
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                rows={2}
              />
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <SecondaryButton type="button" onClick={closeModal}>
                Cancel
              </SecondaryButton>
              <PrimaryButton
                type="button"
                onClick={() => (modalMode === 'create' ? createMutation.mutate() : updateMutation.mutate())}
                disabled={!canSubmit || createMutation.isPending || updateMutation.isPending}
              >
                {createMutation.isPending || updateMutation.isPending ? 'Saving...' : 'Save'}
              </PrimaryButton>
            </div>
          </div>
        </Modal>
      )}

      {deleteTarget && (
        <Modal title="Delete Holiday" onClose={() => setDeleteTarget(null)} maxWidthClassName="max-w-md">
          <p className="text-sm text-slate-600">
            Delete <span className="font-medium">{deleteTarget.holidayName}</span> on {formatDate(deleteTarget.holidayDate)} ({deleteTarget.stateName})?
          </p>
          <div className="mt-4 flex justify-end gap-2">
            <SecondaryButton type="button" onClick={() => setDeleteTarget(null)}>
              Cancel
            </SecondaryButton>
            <PrimaryButton type="button" onClick={() => deleteMutation.mutate(deleteTarget.id)} disabled={deleteMutation.isPending}>
              {deleteMutation.isPending ? 'Deleting...' : 'Delete'}
            </PrimaryButton>
          </div>
        </Modal>
      )}
    </div>
  )
}
