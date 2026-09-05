import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { describeApiError } from '../../../lib/apiError'
import { Modal } from '../../../components/common/Modal'
import { useToast } from '../../../components/common/ToastProvider'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../../components/common/ui'
import type { EmploymentCategory, LeaveTypeMasterResponse, LeaveTypeMasterUpdateRequest } from '../../../types/api'

const ALL_CATEGORIES: EmploymentCategory[] = ['REGULAR', 'CASUAL', 'CONTRACTUAL', 'OUTSOURCED']
const CATEGORY_LABELS: Record<EmploymentCategory, string> = {
  REGULAR: 'Regular',
  CASUAL: 'Casual',
  CONTRACTUAL: 'Contractual',
  OUTSOURCED: 'Outsourced',
}

interface EditForm {
  maxAccumulationCap: string
  isEncashable: boolean
  eligibleCategories: EmploymentCategory[]
}

/**
 * ALMS operational gap #2 - restricted to HR_ADMIN/SUPER_ADMIN at the route
 * level (see App.tsx). Scoped to what /api/v1/master/leave-types exposes
 * (cap/encashable/cadre eligibility) - code, name, annual quota, and
 * active/inactive stay editable only through the pre-existing Leave Types
 * CRUD at /api/leave-types, not duplicated here.
 */
export function LeaveTypeMasterPage() {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<LeaveTypeMasterResponse | null>(null)
  const [form, setForm] = useState<EditForm>({ maxAccumulationCap: '', isEncashable: false, eligibleCategories: [] })

  const { data, isLoading, isError } = useQuery({
    queryKey: ['leave-type-master'],
    queryFn: async () => (await apiClient.get<LeaveTypeMasterResponse[]>('/v1/master/leave-types')).data,
  })

  const updateMutation = useMutation({
    mutationFn: async () => {
      if (!editing) return
      const payload: LeaveTypeMasterUpdateRequest = {
        maxAccumulationCap: form.maxAccumulationCap.trim() === '' ? null : Number(form.maxAccumulationCap),
        isEncashable: form.isEncashable,
        eligibleCategories: form.eligibleCategories,
      }
      await apiClient.put(`/v1/master/leave-types/${editing.id}`, payload)
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'Leave type updated.' })
      queryClient.invalidateQueries({ queryKey: ['leave-type-master'] })
      setEditing(null)
    },
    onError: (error) => show({ tone: 'error', message: describeApiError(error, 'Could not update this leave type.') }),
  })

  function openEdit(row: LeaveTypeMasterResponse) {
    setEditing(row)
    setForm({
      maxAccumulationCap: row.maxAccumulationCap != null ? String(row.maxAccumulationCap) : '',
      isEncashable: row.isEncashable,
      eligibleCategories: row.eligibleCategories,
    })
  }

  function toggleCategory(category: EmploymentCategory) {
    setForm((prev) => ({
      ...prev,
      eligibleCategories: prev.eligibleCategories.includes(category)
        ? prev.eligibleCategories.filter((c) => c !== category)
        : [...prev.eligibleCategories, category],
    }))
  }

  return (
    <div>
      <PageHeader title="Leave Types &amp; Cadre Eligibility" description="Accumulation caps, encashability, and which employment categories may apply for each leave type" />

      <Card>
        {isLoading && <LoadingState label="Loading leave types..." />}
        {isError && <ErrorState message="Could not load leave types." />}
        {data && data.length === 0 && <EmptyState message="No leave types configured." />}
        {data && data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Code</th>
                  <th className="py-2 pr-3">Name</th>
                  <th className="py-2 pr-3 text-right">Accumulation Cap</th>
                  <th className="py-2 pr-3">Encashable</th>
                  <th className="py-2 pr-3">Accumulative</th>
                  <th className="py-2 pr-3">Eligible Categories</th>
                  <th className="py-2 pl-3" />
                </tr>
              </thead>
              <tbody>
                {data.map((row) => (
                  <tr key={row.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3 font-medium">{row.code}</td>
                    <td className="py-2 pr-3">{row.name}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{row.maxAccumulationCap ?? '—'}</td>
                    <td className="py-2 pr-3">
                      <Badge tone={row.isEncashable ? 'success' : 'neutral'}>{row.isEncashable ? 'Yes' : 'No'}</Badge>
                    </td>
                    <td className="py-2 pr-3">
                      <Badge tone={row.isAccumulative ? 'success' : 'neutral'}>{row.isAccumulative ? 'Yes' : 'No'}</Badge>
                    </td>
                    <td className="py-2 pr-3">
                      <div className="flex flex-wrap gap-1">
                        {row.eligibleCategories.length === 0 && <span className="text-xs text-slate-400">None</span>}
                        {row.eligibleCategories.map((cat) => (
                          <Badge key={cat} tone="brand">
                            {CATEGORY_LABELS[cat]}
                          </Badge>
                        ))}
                      </div>
                    </td>
                    <td className="py-2 pl-3 text-right">
                      <button
                        type="button"
                        onClick={() => openEdit(row)}
                        className="inline-flex items-center gap-1 rounded-md border border-slate-300 px-2 py-1 text-xs font-medium text-slate-600 hover:bg-slate-50"
                      >
                        <Pencil size={12} /> Edit
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {editing && (
        <Modal title={`Edit ${editing.code} - ${editing.name}`} onClose={() => setEditing(null)}>
          <div className="space-y-4">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Max Accumulation Cap (days)</label>
              <input
                type="number"
                min="0"
                step="1"
                value={form.maxAccumulationCap}
                onChange={(e) => setForm({ ...form, maxAccumulationCap: e.target.value })}
                placeholder="Leave blank for no cap / no accumulation"
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>

            <label className="flex items-center gap-2 text-sm text-slate-600">
              <input
                type="checkbox"
                checked={form.isEncashable}
                onChange={(e) => setForm({ ...form, isEncashable: e.target.checked })}
              />
              Encashable
            </label>

            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Eligible Employment Categories</label>
              <div className="grid grid-cols-2 gap-2">
                {ALL_CATEGORIES.map((cat) => (
                  <label key={cat} className="flex items-center gap-2 rounded-md border border-slate-200 px-3 py-2 text-sm text-slate-600">
                    <input type="checkbox" checked={form.eligibleCategories.includes(cat)} onChange={() => toggleCategory(cat)} />
                    {CATEGORY_LABELS[cat]}
                  </label>
                ))}
              </div>
              {form.eligibleCategories.length === 0 && (
                <p className="mt-1 text-xs font-medium text-red-600">At least one category must be eligible.</p>
              )}
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <SecondaryButton type="button" onClick={() => setEditing(null)}>
                Cancel
              </SecondaryButton>
              <PrimaryButton
                type="button"
                onClick={() => updateMutation.mutate()}
                disabled={form.eligibleCategories.length === 0 || updateMutation.isPending}
              >
                {updateMutation.isPending ? 'Saving...' : 'Save'}
              </PrimaryButton>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
