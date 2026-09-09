import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import { apiClient } from '../../api/client'
import { describeApiError, getFieldErrors } from '../../lib/apiError'
import { Modal } from '../../components/common/Modal'
import { useToast } from '../../components/common/ToastProvider'
import { Badge, Card, EmptyState, ErrorState, FieldError, LoadingState, PageHeader, PrimaryButton, errorInputClass } from '../../components/common/ui'
import type { Page, StateMasterRequest, StateMasterResponse, StateType } from '../../types/api'

const DEFAULT_REMOTE_ALLOWANCE_PERCENTAGE = 10.0

const EMPTY_FORM = {
  stateCode: '',
  stateName: '',
  stateType: 'STATE' as StateType,
  active: true,
  isRemoteArea: false,
  remoteAllowancePercentage: 0,
}

const STATE_TYPE_LABELS: Record<StateType, string> = {
  STATE: 'State',
  UNION_TERRITORY: 'Union Territory',
  NATIONAL_CAPITAL_TERRITORY: 'National Capital Territory',
}

/** SUPER_ADMIN-only (route-gated in App.tsx); backend also enforces this on every endpoint under /api/v1/admin/masters/states. */
export function StateMasterPage() {
  const queryClient = useQueryClient()
  const { show } = useToast()
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<StateMasterResponse | null>(null)
  const [form, setForm] = useState(EMPTY_FORM)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['state-masters'],
    queryFn: async () => (await apiClient.get<Page<StateMasterResponse>>('/v1/admin/masters/states', { params: { size: 100 } })).data,
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload: StateMasterRequest = { ...form }
      if (editing) {
        return (await apiClient.put<StateMasterResponse>(`/v1/admin/masters/states/${editing.id}`, payload)).data
      }
      return (await apiClient.post<StateMasterResponse>('/v1/admin/masters/states', payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['state-masters'] })
      show({ tone: 'success', message: editing ? 'State updated.' : 'State created.' })
      closeModal()
    },
  })
  const fieldErrors = getFieldErrors(saveMutation.error)

  function openCreate() {
    setEditing(null)
    setForm(EMPTY_FORM)
    setModalOpen(true)
  }

  function openEdit(state: StateMasterResponse) {
    setEditing(state)
    setForm({
      stateCode: state.stateCode,
      stateName: state.stateName,
      stateType: state.stateType,
      active: state.active,
      isRemoteArea: state.isRemoteArea,
      remoteAllowancePercentage: state.remoteAllowancePercentage,
    })
    setModalOpen(true)
  }

  function closeModal() {
    setModalOpen(false)
    setEditing(null)
    setForm(EMPTY_FORM)
  }

  /** Toggle ON pre-fills 10.00% (per spec); toggle OFF resets to 0.00 - the only value the backend accepts when isRemoteArea is false. */
  function toggleRemoteArea(checked: boolean) {
    setForm({
      ...form,
      isRemoteArea: checked,
      remoteAllowancePercentage: checked ? DEFAULT_REMOTE_ALLOWANCE_PERCENTAGE : 0,
    })
  }

  const remoteAllowanceInvalid = form.isRemoteArea && (form.remoteAllowancePercentage <= 0 || form.remoteAllowancePercentage > 100)
  const formValid =
    form.stateCode.trim().length >= 2 && form.stateCode.trim().length <= 10 && form.stateName.trim() !== '' && !remoteAllowanceInvalid

  return (
    <div>
      <PageHeader
        title="State Master"
        description="States and union territories used for employee addresses"
        actions={
          <PrimaryButton onClick={openCreate}>
            <Plus size={15} /> Add State
          </PrimaryButton>
        }
      />

      <Card>
        {isLoading && <LoadingState label="Loading states..." />}
        {isError && <ErrorState message="Could not load the state master." />}
        {data && data.content.length === 0 && <EmptyState message="No states recorded yet." />}

        {data && data.content.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[620px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Code</th>
                  <th className="py-2 pr-3">Name</th>
                  <th className="py-2 pr-3">Type</th>
                  <th className="py-2 pr-3">Remote Area</th>
                  <th className="py-2 pr-3">Status</th>
                  <th className="py-2 pl-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((state) => (
                  <tr key={state.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3 font-medium">{state.stateCode}</td>
                    <td className="py-2 pr-3">{state.stateName}</td>
                    <td className="py-2 pr-3 text-slate-500">{STATE_TYPE_LABELS[state.stateType]}</td>
                    <td className="py-2 pr-3">
                      <Badge tone={state.isRemoteArea ? 'brand' : 'neutral'}>
                        {state.isRemoteArea ? `Yes (${state.remoteAllowancePercentage.toFixed(2)}%)` : 'No'}
                      </Badge>
                    </td>
                    <td className="py-2 pr-3">
                      <Badge tone={state.active ? 'success' : 'neutral'}>{state.active ? 'Active' : 'Inactive'}</Badge>
                    </td>
                    <td className="py-2 pl-3 text-right">
                      <button
                        type="button"
                        onClick={() => openEdit(state)}
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
        <Modal title={editing ? `Edit ${editing.stateName}` : 'Add State'} onClose={closeModal}>
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault()
              saveMutation.mutate()
            }}
          >
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">State Code</label>
              <input
                required
                minLength={2}
                maxLength={10}
                value={form.stateCode}
                onChange={(e) => setForm({ ...form, stateCode: e.target.value.toUpperCase() })}
                className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm uppercase ${errorInputClass(Boolean(fieldErrors?.stateCode))}`}
                placeholder="WB"
              />
              <FieldError message={fieldErrors?.stateCode} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">State Name</label>
              <input
                required
                value={form.stateName}
                onChange={(e) => setForm({ ...form, stateName: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                placeholder="West Bengal"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Type</label>
              <select
                value={form.stateType}
                onChange={(e) => setForm({ ...form, stateType: e.target.value as StateType })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="STATE">State</option>
                <option value="UNION_TERRITORY">Union Territory</option>
                <option value="NATIONAL_CAPITAL_TERRITORY">National Capital Territory</option>
              </select>
            </div>

            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
              Active
            </label>

            <div className="rounded-md border border-slate-200 p-3">
              <label className="flex items-center justify-between text-sm text-slate-700">
                <span>Remote Area Allowance Applicable</span>
                <input
                  type="checkbox"
                  role="switch"
                  checked={form.isRemoteArea}
                  onChange={(e) => toggleRemoteArea(e.target.checked)}
                />
              </label>
              {form.isRemoteArea && (
                <div className="mt-3">
                  <label className="mb-1 block text-xs font-medium text-slate-600">Allowance %</label>
                  <input
                    required
                    type="number"
                    min={0.01}
                    max={100}
                    step={0.5}
                    value={form.remoteAllowancePercentage}
                    onChange={(e) => setForm({ ...form, remoteAllowancePercentage: Number(e.target.value) })}
                    className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(remoteAllowanceInvalid || Boolean(fieldErrors?.remoteAllowancePercentage))}`}
                  />
                  {remoteAllowanceInvalid && <p className="mt-1 text-xs text-rose-600">Must be greater than 0 and at most 100.</p>}
                  <FieldError message={fieldErrors?.remoteAllowancePercentage} />
                </div>
              )}
            </div>

            <PrimaryButton type="submit" disabled={saveMutation.isPending || !formValid} className="w-full justify-center">
              Save State
            </PrimaryButton>
            {saveMutation.isError && (
              <ErrorState message={describeApiError(saveMutation.error, 'Could not save the state.')} />
            )}
          </form>
        </Modal>
      )}
    </div>
  )
}
