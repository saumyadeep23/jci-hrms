import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil } from 'lucide-react'
import { apiClient } from '../../../../api/client'
import { useToast } from '../../../../components/common/ToastProvider'
import { describeApiErrorList, getFieldErrors } from '../../../../lib/apiError'
import { Modal } from '../../../../components/common/Modal'
import { FormErrorBanner } from '../../../../components/common/FormErrorBanner'
import { Badge, Card, EmptyState, ErrorState, FieldError, LoadingState, PageHeader, PrimaryButton, errorInputClass } from '../../../../components/common/ui'
import type {
  SalaryHeadEffectType,
  SalaryHeadResponse,
  SalaryHeadUpdateRequest,
  StatutoryHeadResponse,
  StatutoryHeadUpdateRequest,
} from '../../../../types/api'

const EFFECT_TYPES: SalaryHeadEffectType[] = ['EARNING', 'DEDUCTION', 'NO_EFFECT']
const APPLICABLE_FOR_OPTIONS = ['REGULAR', 'CASUAL', 'BOTH']

function effectTone(effectType: SalaryHeadResponse['effectType']): 'success' | 'danger' | 'neutral' {
  if (effectType === 'EARNING') return 'success'
  if (effectType === 'DEDUCTION') return 'danger'
  return 'neutral'
}

type SalaryHeadFormState = {
  description: string
  shortName: string
  effectType: SalaryHeadEffectType
  isVariable: boolean
  applicableFor: string
  salSlipVis: string
  basicDependent: boolean
  refAccountCode: string
}

function salaryHeadFormFrom(head: SalaryHeadResponse): SalaryHeadFormState {
  return {
    description: head.description,
    shortName: head.shortName,
    effectType: head.effectType,
    isVariable: head.isVariable,
    applicableFor: head.applicableFor,
    salSlipVis: head.salSlipVis != null ? String(head.salSlipVis) : '',
    basicDependent: head.basicDependent,
    refAccountCode: head.refAccountCode ?? '',
  }
}

/** Edit modal for one Salary Head row - headCount stays read-only (it's the catalog's own primary key, not a display-order field). */
function SalaryHeadEditModal({ head, onClose }: { head: SalaryHeadResponse; onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<SalaryHeadFormState>(salaryHeadFormFrom(head))

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload: SalaryHeadUpdateRequest = {
        description: form.description,
        shortName: form.shortName,
        effectType: form.effectType,
        isVariable: form.isVariable,
        applicableFor: form.applicableFor,
        salSlipVis: Number(form.salSlipVis),
        basicDependent: form.basicDependent,
        refAccountCode: form.refAccountCode,
      }
      return (await apiClient.put(`/v1/payroll/masters/salary-heads/${head.headCount}`, payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payroll-salary-heads'] })
      show({ tone: 'success', message: `${form.shortName} updated.` })
      onClose()
    },
  })
  const fieldErrors = getFieldErrors(saveMutation.error)

  const formValid =
    form.description.trim() !== '' && form.shortName.trim() !== '' && form.refAccountCode.trim() !== '' && form.salSlipVis !== ''

  return (
    <Modal title={`Edit Salary Head #${head.headCount}`} onClose={onClose} maxWidthClassName="max-w-lg">
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
          <label className="mb-1 block text-xs font-medium text-slate-600">Head Count (read-only)</label>
          <input disabled value={head.headCount} className="w-full rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-500" />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Description</label>
          <input
            required
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
            className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.description))}`}
          />
          <FieldError message={fieldErrors?.description} />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Short Name</label>
            <input
              required
              value={form.shortName}
              onChange={(e) => setForm({ ...form, shortName: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.shortName))}`}
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Effect Type</label>
            <select
              value={form.effectType}
              onChange={(e) => setForm({ ...form, effectType: e.target.value as SalaryHeadEffectType })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              {EFFECT_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Applicable Cadres</label>
            <select
              value={form.applicableFor}
              onChange={(e) => setForm({ ...form, applicableFor: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              {APPLICABLE_FOR_OPTIONS.map((o) => (
                <option key={o} value={o}>
                  {o}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Payslip Display Order</label>
            <input
              required
              type="number"
              min={0}
              value={form.salSlipVis}
              onChange={(e) => setForm({ ...form, salSlipVis: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.salSlipVis))}`}
            />
          </div>

          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">GL Account Code</label>
            <input
              required
              value={form.refAccountCode}
              onChange={(e) => setForm({ ...form, refAccountCode: e.target.value })}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.refAccountCode))}`}
            />
          </div>
          <div className="flex flex-col justify-end gap-2 pb-2">
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" checked={form.isVariable} onChange={(e) => setForm({ ...form, isVariable: e.target.checked })} />
              Variable
            </label>
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={form.basicDependent}
                onChange={(e) => setForm({ ...form, basicDependent: e.target.checked })}
              />
              Basic-Dependent
            </label>
          </div>
        </div>

        <PrimaryButton type="submit" disabled={saveMutation.isPending || !formValid} className="w-full justify-center">
          Save Changes
        </PrimaryButton>
      </form>
    </Modal>
  )
}

/** Edit modal for one Statutory Head row - statHeadCount stays read-only. */
function StatutoryHeadEditModal({ head, onClose }: { head: StatutoryHeadResponse; onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [description, setDescription] = useState(head.statHeadDescr)
  const [shortName, setShortName] = useState(head.statHeadShortName)

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload: StatutoryHeadUpdateRequest = { description, shortName }
      return (await apiClient.put(`/v1/payroll/masters/statutory-heads/${head.statHeadCount}`, payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payroll-statutory-heads'] })
      show({ tone: 'success', message: `${shortName} updated.` })
      onClose()
    },
  })
  const fieldErrors = getFieldErrors(saveMutation.error)

  return (
    <Modal title={`Edit Statutory Head #${head.statHeadCount}`} onClose={onClose} maxWidthClassName="max-w-md">
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
          <label className="mb-1 block text-xs font-medium text-slate-600">Stat Head Count (read-only)</label>
          <input
            disabled
            value={head.statHeadCount}
            className="w-full rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-500"
          />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Description</label>
          <input
            required
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.description))}`}
          />
          <FieldError message={fieldErrors?.description} />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Short Name</label>
          <input
            required
            value={shortName}
            onChange={(e) => setShortName(e.target.value)}
            className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.shortName))}`}
          />
          <FieldError message={fieldErrors?.shortName} />
        </div>

        <PrimaryButton
          type="submit"
          disabled={saveMutation.isPending || description.trim() === '' || shortName.trim() === ''}
          className="w-full justify-center"
        >
          Save Changes
        </PrimaryButton>
      </form>
    </Modal>
  )
}

/** Salary & Statutory Heads Directory - searchable catalog of the 61 salary heads and 15 statutory heads, each editable via its own modal. */
export function SalaryStatutoryHeadsTab() {
  const [search, setSearch] = useState('')
  const [editingSalaryHead, setEditingSalaryHead] = useState<SalaryHeadResponse | null>(null)
  const [editingStatutoryHead, setEditingStatutoryHead] = useState<StatutoryHeadResponse | null>(null)

  const salaryHeadsQuery = useQuery({
    queryKey: ['payroll-salary-heads'],
    queryFn: async () => (await apiClient.get<SalaryHeadResponse[]>('/v1/payroll/masters/salary-heads')).data,
  })
  const statutoryHeadsQuery = useQuery({
    queryKey: ['payroll-statutory-heads'],
    queryFn: async () => (await apiClient.get<StatutoryHeadResponse[]>('/v1/payroll/masters/statutory-heads')).data,
  })

  const filteredSalaryHeads = useMemo(() => {
    const rows = salaryHeadsQuery.data ?? []
    const q = search.trim().toLowerCase()
    if (!q) return rows
    return rows.filter((r) => r.description.toLowerCase().includes(q) || r.shortName.toLowerCase().includes(q))
  }, [salaryHeadsQuery.data, search])

  const filteredStatutoryHeads = useMemo(() => {
    const rows = statutoryHeadsQuery.data ?? []
    const q = search.trim().toLowerCase()
    if (!q) return rows
    return rows.filter((r) => r.statHeadDescr.toLowerCase().includes(q) || r.statHeadShortName.toLowerCase().includes(q))
  }, [statutoryHeadsQuery.data, search])

  return (
    <div>
      <PageHeader title="Salary & Statutory Heads Directory" description="Catalog of 61 salary heads and 15 statutory heads" />

      <div className="mb-4">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by description or short name..."
          className="w-full max-w-sm rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>

      <Card className="mb-6">
        <h3 className="mb-3 text-sm font-semibold text-slate-700">Salary Heads ({salaryHeadsQuery.data?.length ?? 0})</h3>
        {salaryHeadsQuery.isLoading && <LoadingState label="Loading salary heads..." />}
        {salaryHeadsQuery.isError && <ErrorState message="Could not load salary heads." />}
        {salaryHeadsQuery.data && filteredSalaryHeads.length === 0 && <EmptyState message="No salary heads match your search." />}

        {salaryHeadsQuery.data && filteredSalaryHeads.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[860px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">#</th>
                  <th className="py-2 pr-3">Head</th>
                  <th className="py-2 pr-3">Effect</th>
                  <th className="py-2 pr-3">Applicable For</th>
                  <th className="py-2 pr-3">Variable</th>
                  <th className="py-2 pr-3">GL Account Code</th>
                  <th className="py-2 pr-3">Payslip Vis. Seq.</th>
                  <th className="py-2 pl-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredSalaryHeads.map((head) => (
                  <tr key={head.headCount} className="border-b border-slate-100">
                    <td className="py-2 pr-3 tabular-nums text-slate-500">{head.headCount}</td>
                    <td className="py-2 pr-3">
                      <div className="font-medium">{head.description}</div>
                      <div className="text-xs text-slate-400">{head.shortName}</div>
                    </td>
                    <td className="py-2 pr-3">
                      <Badge tone={effectTone(head.effectType)}>{head.effectType}</Badge>
                    </td>
                    <td className="py-2 pr-3 text-slate-500">{head.applicableFor}</td>
                    <td className="py-2 pr-3">{head.isVariable ? 'Yes' : 'No'}</td>
                    <td className="py-2 pr-3 text-slate-500">{head.refAccountCode ?? '—'}</td>
                    <td className="py-2 pr-3 tabular-nums text-slate-500">{head.salSlipVis ?? '—'}</td>
                    <td className="py-2 pl-3 text-right">
                      <button
                        type="button"
                        onClick={() => setEditingSalaryHead(head)}
                        aria-label={`Edit ${head.shortName}`}
                        className="inline-flex items-center justify-center rounded-md p-1.5 text-brand-forest hover:bg-slate-100"
                      >
                        <Pencil size={14} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card>
        <h3 className="mb-3 text-sm font-semibold text-slate-700">Statutory Heads ({statutoryHeadsQuery.data?.length ?? 0})</h3>
        {statutoryHeadsQuery.isLoading && <LoadingState label="Loading statutory heads..." />}
        {statutoryHeadsQuery.isError && <ErrorState message="Could not load statutory heads." />}
        {statutoryHeadsQuery.data && filteredStatutoryHeads.length === 0 && <EmptyState message="No statutory heads match your search." />}

        {statutoryHeadsQuery.data && filteredStatutoryHeads.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">#</th>
                  <th className="py-2 pr-3">Description</th>
                  <th className="py-2 pr-3">Short Name</th>
                  <th className="py-2 pl-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredStatutoryHeads.map((head) => (
                  <tr key={head.statHeadCount} className="border-b border-slate-100">
                    <td className="py-2 pr-3 tabular-nums text-slate-500">{head.statHeadCount}</td>
                    <td className="py-2 pr-3">{head.statHeadDescr}</td>
                    <td className="py-2 pr-3 text-slate-500">{head.statHeadShortName}</td>
                    <td className="py-2 pl-3 text-right">
                      <button
                        type="button"
                        onClick={() => setEditingStatutoryHead(head)}
                        aria-label={`Edit ${head.statHeadShortName}`}
                        className="inline-flex items-center justify-center rounded-md p-1.5 text-brand-forest hover:bg-slate-100"
                      >
                        <Pencil size={14} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {editingSalaryHead && <SalaryHeadEditModal head={editingSalaryHead} onClose={() => setEditingSalaryHead(null)} />}
      {editingStatutoryHead && <StatutoryHeadEditModal head={editingStatutoryHead} onClose={() => setEditingStatutoryHead(null)} />}
    </div>
  )
}
