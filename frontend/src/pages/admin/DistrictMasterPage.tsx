import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import { apiClient } from '../../api/client'
import { describeApiError } from '../../lib/apiError'
import { Modal } from '../../components/common/Modal'
import { useToast } from '../../components/common/ToastProvider'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton } from '../../components/common/ui'
import type { DistrictMasterRequest, DistrictMasterResponse, Page, StateMasterResponse } from '../../types/api'

const EMPTY_FORM = { districtCode: '', districtName: '', stateId: '', active: true }

/** SUPER_ADMIN-only (route-gated in App.tsx); backend also enforces this on every endpoint under /api/v1/admin/masters/districts. */
export function DistrictMasterPage() {
  const queryClient = useQueryClient()
  const { show } = useToast()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<DistrictMasterResponse | null>(null)
  const [filterStateId, setFilterStateId] = useState('')
  const [form, setForm] = useState(EMPTY_FORM)

  const statesQuery = useQuery({
    queryKey: ['state-masters-all'],
    queryFn: async () => (await apiClient.get<Page<StateMasterResponse>>('/v1/admin/masters/states', { params: { size: 100 } })).data.content,
  })
  const states = statesQuery.data ?? []

  const { data, isLoading, isError } = useQuery({
    queryKey: ['district-masters', filterStateId],
    queryFn: async () =>
      (
        await apiClient.get<Page<DistrictMasterResponse>>('/v1/admin/masters/districts', {
          params: { size: 200, ...(filterStateId ? { stateId: filterStateId } : {}) },
        })
      ).data,
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload: DistrictMasterRequest = { ...form }
      if (editing) {
        return (await apiClient.put<DistrictMasterResponse>(`/v1/admin/masters/districts/${editing.id}`, payload)).data
      }
      return (await apiClient.post<DistrictMasterResponse>('/v1/admin/masters/districts', payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['district-masters'] })
      show({ tone: 'success', message: editing ? 'District updated.' : 'District created.' })
      closeModal()
    },
  })

  function openCreate() {
    setEditing(null)
    setForm(EMPTY_FORM)
    setModalOpen(true)
  }

  function openEdit(district: DistrictMasterResponse) {
    setEditing(district)
    setForm({ districtCode: district.districtCode, districtName: district.districtName, stateId: district.stateId, active: district.active })
    setModalOpen(true)
  }

  function closeModal() {
    setModalOpen(false)
    setEditing(null)
    setForm(EMPTY_FORM)
  }

  return (
    <div>
      <PageHeader
        title="District Master"
        description="Districts, scoped to a state, used for employee addresses"
        actions={
          <PrimaryButton onClick={openCreate} disabled={states.length === 0}>
            <Plus size={15} /> Add District
          </PrimaryButton>
        }
      />

      <Card>
        <div className="mb-3 flex items-center gap-2">
          <label className="text-xs font-medium text-slate-600">Filter by state:</label>
          <select
            value={filterStateId}
            onChange={(e) => setFilterStateId(e.target.value)}
            className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
          >
            <option value="">All states</option>
            {states.map((state) => (
              <option key={state.id} value={state.id}>
                {state.stateName}
              </option>
            ))}
          </select>
        </div>

        {isLoading && <LoadingState label="Loading districts..." />}
        {isError && <ErrorState message="Could not load the district master." />}
        {data && data.content.length === 0 && <EmptyState message="No districts recorded yet." />}

        {data && data.content.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[680px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Code</th>
                  <th className="py-2 pr-3">Name</th>
                  <th className="py-2 pr-3">State</th>
                  <th className="py-2 pr-3">Status</th>
                  <th className="py-2 pl-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((district) => (
                  <tr key={district.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3 font-medium">{district.districtCode}</td>
                    <td className="py-2 pr-3">{district.districtName}</td>
                    <td className="py-2 pr-3 text-slate-500">{district.stateName}</td>
                    <td className="py-2 pr-3">
                      <Badge tone={district.active ? 'success' : 'neutral'}>{district.active ? 'Active' : 'Inactive'}</Badge>
                    </td>
                    <td className="py-2 pl-3 text-right">
                      <button
                        type="button"
                        onClick={() => openEdit(district)}
                        className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
                        title="Edit"
                      >
                        <Pencil size={15} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {modalOpen && (
        <Modal title={editing ? `Edit ${editing.districtName}` : 'Add District'} onClose={closeModal}>
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault()
              saveMutation.mutate()
            }}
          >
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">State</label>
              <select
                required
                value={form.stateId}
                onChange={(e) => setForm({ ...form, stateId: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">Select state...</option>
                {states.map((state) => (
                  <option key={state.id} value={state.id}>
                    {state.stateName}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">District Code</label>
              <input
                required
                maxLength={10}
                value={form.districtCode}
                onChange={(e) => setForm({ ...form, districtCode: e.target.value.toUpperCase() })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                placeholder="WB-KOL"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">District Name</label>
              <input
                required
                value={form.districtName}
                onChange={(e) => setForm({ ...form, districtName: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                placeholder="Kolkata"
              />
            </div>
            <PrimaryButton type="submit" disabled={saveMutation.isPending} className="w-full justify-center">
              Save District
            </PrimaryButton>
            {saveMutation.isError && (
              <ErrorState message={describeApiError(saveMutation.error, 'Could not save the district.')} />
            )}
          </form>
        </Modal>
      )}
    </div>
  )
}
