import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { apiClient } from '../../api/client'
import { describeApiError } from '../../lib/apiError'
import { DatePicker } from '../common/DatePicker'
import { ErrorState, LoadingState, SecondaryButton } from '../common/ui'
import type {
  DependentRequest,
  DependentResponse,
  FamilyDetailsRequest,
  FamilyDetailsResponse,
  NomineeRequest,
  NomineeResponse,
} from '../../types/api'

const EMPTY_FAMILY: FamilyDetailsRequest = { fatherName: '', motherName: '', spouseName: '', spouseDob: '' }

function emptyDependent(employeeId: number): DependentRequest {
  return { employeeId, name: '', relationship: '', dateOfBirth: '', isDependent: true, isCoveredMedical: false }
}

function emptyNominee(employeeId: number): NomineeRequest {
  return { employeeId, name: '', relationship: '', sharePercentage: 100, nomineeFor: 'PF' }
}

/** Live CRUD against EmployeeFamilyController/EmployeeDependentController/EmployeeNomineeController. */
export function EmployeeFamilyNomineesPanel({ employeeId }: { employeeId: number }) {
  const queryClient = useQueryClient()

  const familyQuery = useQuery({
    queryKey: ['employee-family', employeeId],
    queryFn: async () => {
      const res = await apiClient.get<FamilyDetailsResponse>(`/employees/${employeeId}/family`, { validateStatus: (s) => s === 200 || s === 204 })
      return res.status === 200 ? res.data : null
    },
  })
  const [familyForm, setFamilyForm] = useState<FamilyDetailsRequest>(EMPTY_FAMILY)
  useEffect(() => {
    if (familyQuery.data) {
      setFamilyForm({
        fatherName: familyQuery.data.fatherName,
        motherName: familyQuery.data.motherName ?? '',
        spouseName: familyQuery.data.spouseName ?? '',
        spouseDob: familyQuery.data.spouseDob ?? '',
      })
    }
  }, [familyQuery.data])

  const familyMutation = useMutation({
    mutationFn: async () => apiClient.put(`/employees/${employeeId}/family`, familyForm),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['employee-family', employeeId] }),
  })

  const dependentsQuery = useQuery({
    queryKey: ['employee-dependents', employeeId],
    queryFn: async () => (await apiClient.get<DependentResponse[]>(`/employees/${employeeId}/dependents`)).data,
  })
  const [dependentDraft, setDependentDraft] = useState<DependentRequest | null>(null)
  const createDependent = useMutation({
    mutationFn: async (request: DependentRequest) => apiClient.post(`/employees/${employeeId}/dependents`, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['employee-dependents', employeeId] })
      setDependentDraft(null)
    },
  })
  const deleteDependent = useMutation({
    mutationFn: async (id: number) => apiClient.delete(`/employees/${employeeId}/dependents/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['employee-dependents', employeeId] }),
  })

  const nomineesQuery = useQuery({
    queryKey: ['employee-nominees', employeeId],
    queryFn: async () => (await apiClient.get<NomineeResponse[]>(`/employees/${employeeId}/nominees`)).data,
  })
  const [nomineeDraft, setNomineeDraft] = useState<NomineeRequest | null>(null)
  const createNominee = useMutation({
    mutationFn: async (request: NomineeRequest) => apiClient.post(`/employees/${employeeId}/nominees`, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['employee-nominees', employeeId] })
      setNomineeDraft(null)
    },
  })
  const deleteNominee = useMutation({
    mutationFn: async (id: number) => apiClient.delete(`/employees/${employeeId}/nominees/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['employee-nominees', employeeId] }),
  })

  if (familyQuery.isLoading || dependentsQuery.isLoading || nomineesQuery.isLoading) return <LoadingState label="Loading family details..." />

  return (
    <div className="space-y-6">
      <div>
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Family</p>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Father's Name</label>
            <input
              required
              value={familyForm.fatherName}
              onChange={(e) => setFamilyForm({ ...familyForm, fatherName: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Mother's Name</label>
            <input
              value={familyForm.motherName ?? ''}
              onChange={(e) => setFamilyForm({ ...familyForm, motherName: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Spouse's Name</label>
            <input
              value={familyForm.spouseName ?? ''}
              onChange={(e) => setFamilyForm({ ...familyForm, spouseName: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Spouse's Date of Birth</label>
            <DatePicker value={familyForm.spouseDob ?? ''} onChange={(v) => setFamilyForm({ ...familyForm, spouseDob: v })} />
          </div>
        </div>
        <div className="mt-2 flex justify-end">
          <button
            type="button"
            disabled={familyMutation.isPending || familyForm.fatherName.trim() === ''}
            onClick={() => familyMutation.mutate()}
            className="rounded-md bg-brand-forest px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
          >
            Save Family Details
          </button>
        </div>
        {familyMutation.isError && <ErrorState message={describeApiError(familyMutation.error, 'Could not save family details.')} />}
      </div>

      <div>
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Dependents</p>
        <div className="space-y-2">
          {(dependentsQuery.data ?? []).map((d) => (
            <div key={d.id} className="flex items-center justify-between rounded-md border border-slate-200 px-3 py-2">
              <p className="text-sm text-slate-700">
                {d.name} ({d.relationship}) {d.isCoveredMedical ? '- Medical Cover' : ''}
              </p>
              <button type="button" onClick={() => deleteDependent.mutate(d.id)} className="rounded-md p-1 text-red-500 hover:bg-red-50">
                <Trash2 size={14} />
              </button>
            </div>
          ))}
        </div>
        {dependentDraft ? (
          <div className="mt-2 grid grid-cols-2 gap-2 rounded-md border border-slate-200 p-3">
            <input
              placeholder="Name"
              value={dependentDraft.name}
              onChange={(e) => setDependentDraft({ ...dependentDraft, name: e.target.value })}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            <input
              placeholder="Relationship"
              value={dependentDraft.relationship}
              onChange={(e) => setDependentDraft({ ...dependentDraft, relationship: e.target.value })}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            <DatePicker value={dependentDraft.dateOfBirth ?? ''} onChange={(v) => setDependentDraft({ ...dependentDraft, dateOfBirth: v })} />
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={dependentDraft.isCoveredMedical}
                onChange={(e) => setDependentDraft({ ...dependentDraft, isCoveredMedical: e.target.checked })}
              />
              Covered under medical
            </label>
            <div className="col-span-2 flex justify-end gap-2">
              <SecondaryButton type="button" onClick={() => setDependentDraft(null)}>
                Cancel
              </SecondaryButton>
              <button
                type="button"
                disabled={createDependent.isPending || dependentDraft.name.trim() === '' || dependentDraft.relationship.trim() === ''}
                onClick={() => createDependent.mutate(dependentDraft)}
                className="rounded-md bg-brand-forest px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
              >
                Save Dependent
              </button>
            </div>
          </div>
        ) : (
          <SecondaryButton type="button" className="mt-2" onClick={() => setDependentDraft(emptyDependent(employeeId))}>
            <Plus size={14} /> Add Dependent
          </SecondaryButton>
        )}
      </div>

      <div>
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Nominees</p>
        <div className="space-y-2">
          {(nomineesQuery.data ?? []).map((n) => (
            <div key={n.id} className="flex items-center justify-between rounded-md border border-slate-200 px-3 py-2">
              <p className="text-sm text-slate-700">
                {n.name} ({n.relationship}) - {n.sharePercentage}% for {n.nomineeFor}
              </p>
              <button type="button" onClick={() => deleteNominee.mutate(n.id)} className="rounded-md p-1 text-red-500 hover:bg-red-50">
                <Trash2 size={14} />
              </button>
            </div>
          ))}
        </div>
        {nomineeDraft ? (
          <div className="mt-2 grid grid-cols-2 gap-2 rounded-md border border-slate-200 p-3">
            <input
              placeholder="Name"
              value={nomineeDraft.name}
              onChange={(e) => setNomineeDraft({ ...nomineeDraft, name: e.target.value })}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            <input
              placeholder="Relationship"
              value={nomineeDraft.relationship}
              onChange={(e) => setNomineeDraft({ ...nomineeDraft, relationship: e.target.value })}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            <input
              type="number"
              step="0.01"
              min="0"
              max="100"
              placeholder="Share %"
              value={nomineeDraft.sharePercentage}
              onChange={(e) => setNomineeDraft({ ...nomineeDraft, sharePercentage: Number(e.target.value) })}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            <input
              placeholder="Nominee For (e.g. PF, Gratuity, NPS)"
              value={nomineeDraft.nomineeFor}
              onChange={(e) => setNomineeDraft({ ...nomineeDraft, nomineeFor: e.target.value })}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            <div className="col-span-2 flex justify-end gap-2">
              <SecondaryButton type="button" onClick={() => setNomineeDraft(null)}>
                Cancel
              </SecondaryButton>
              <button
                type="button"
                disabled={createNominee.isPending || nomineeDraft.name.trim() === '' || nomineeDraft.relationship.trim() === ''}
                onClick={() => createNominee.mutate(nomineeDraft)}
                className="rounded-md bg-brand-forest px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
              >
                Save Nominee
              </button>
            </div>
          </div>
        ) : (
          <SecondaryButton type="button" className="mt-2" onClick={() => setNomineeDraft(emptyNominee(employeeId))}>
            <Plus size={14} /> Add Nominee
          </SecondaryButton>
        )}
      </div>
    </div>
  )
}
