import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { apiClient } from '../../api/client'
import { describeApiError } from '../../lib/apiError'
import { DatePicker } from '../common/DatePicker'
import { ErrorState, LoadingState, SecondaryButton } from '../common/ui'
import type { PastServiceOrganizationType, PastServicePayScalePattern, PastServiceRecordRequest, PastServiceRecordResponse } from '../../types/api'

const ORGANIZATION_TYPES: PastServiceOrganizationType[] = [
  'CENTRAL_GOVT',
  'STATE_GOVT',
  'CENTRAL_PSU',
  'STATE_PSU',
  'AUTONOMOUS_BODY',
  'DEFENCE_ARMY_NAVY_AIRFORCE',
  'PRIVATE_SECTOR',
  'OTHER',
]
const PAY_SCALE_PATTERNS: PastServicePayScalePattern[] = ['IDA', 'CDA', 'CONSOLIDATED', 'OTHER']

function emptyRequest(): PastServiceRecordRequest {
  return {
    organizationName: '',
    organizationType: 'PRIVATE_SECTOR',
    designationHeld: '',
    fromDate: '',
    toDate: '',
    lastPayScalePattern: null,
    lastDrawnBasic: null,
    lastDrawnGross: null,
    qualifyingForPensionGratuity: false,
    qualifyingServiceOrderRef: '',
    reasonForLeaving: '',
    experienceCertificateS3Key: null,
    relievingNocDocumentS3Key: null,
  }
}

/** Live CRUD against EmployeePastServiceRecordController - mirrors EmployeeQualificationsPanel's per-row save/delete pattern. */
export function EmployeePastServicePanel({ employeeId }: { employeeId: number }) {
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState<PastServiceRecordRequest | null>(null)

  const listQuery = useQuery({
    queryKey: ['employee-past-service', employeeId],
    queryFn: async () => (await apiClient.get<PastServiceRecordResponse[]>(`/employees/${employeeId}/past-service-records`)).data,
  })

  const createMutation = useMutation({
    mutationFn: async (request: PastServiceRecordRequest) => apiClient.post(`/employees/${employeeId}/past-service-records`, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['employee-past-service', employeeId] })
      setDraft(null)
    },
  })
  const deleteMutation = useMutation({
    mutationFn: async (id: number) => apiClient.delete(`/employees/${employeeId}/past-service-records/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['employee-past-service', employeeId] }),
  })

  if (listQuery.isLoading) return <LoadingState label="Loading past service records..." />
  if (listQuery.isError) return <ErrorState message="Could not load past service records." />

  const rows = listQuery.data ?? []
  const formValid = draft && draft.organizationName.trim() !== '' && draft.designationHeld.trim() !== '' && draft.fromDate !== '' && draft.toDate !== ''

  return (
    <div className="space-y-3">
      {rows.length === 0 && !draft && <p className="text-sm text-slate-500">No past service records on file.</p>}

      {rows.map((row) => (
        <div key={row.id} className="rounded-md border border-slate-200 p-3">
          <div className="mb-1 flex items-center justify-between">
            <p className="text-sm font-medium text-slate-800">
              {row.organizationName} - {row.designationHeld}
            </p>
            <button
              type="button"
              onClick={() => deleteMutation.mutate(row.id)}
              className="rounded-md p-1 text-red-500 hover:bg-red-50"
              title="Delete"
            >
              <Trash2 size={14} />
            </button>
          </div>
          <p className="text-xs text-slate-500">
            {row.organizationType.replace(/_/g, ' ')} - {row.fromDate} to {row.toDate}
            {row.totalServiceDays != null ? ` (${row.totalServiceDays} days)` : ''}
          </p>
        </div>
      ))}

      {draft ? (
        <div className="rounded-md border border-slate-200 p-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Organization Name</label>
              <input
                required
                value={draft.organizationName}
                onChange={(e) => setDraft({ ...draft, organizationName: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Organization Type</label>
              <select
                value={draft.organizationType}
                onChange={(e) => setDraft({ ...draft, organizationType: e.target.value as PastServiceOrganizationType })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                {ORGANIZATION_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t.replace(/_/g, ' ')}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Designation Held</label>
              <input
                required
                value={draft.designationHeld}
                onChange={(e) => setDraft({ ...draft, designationHeld: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div />
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">From Date</label>
              <DatePicker value={draft.fromDate} onChange={(v) => setDraft({ ...draft, fromDate: v })} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">To Date</label>
              <DatePicker value={draft.toDate} onChange={(v) => setDraft({ ...draft, toDate: v })} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Last Pay Scale Pattern</label>
              <select
                value={draft.lastPayScalePattern ?? ''}
                onChange={(e) => setDraft({ ...draft, lastPayScalePattern: (e.target.value || null) as PastServicePayScalePattern | null })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">Not applicable</option>
                {PAY_SCALE_PATTERNS.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Last Drawn Basic</label>
              <input
                type="number"
                step="0.01"
                value={draft.lastDrawnBasic ?? ''}
                onChange={(e) => setDraft({ ...draft, lastDrawnBasic: e.target.value === '' ? null : Number(e.target.value) })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Reason for Leaving</label>
              <input
                value={draft.reasonForLeaving ?? ''}
                onChange={(e) => setDraft({ ...draft, reasonForLeaving: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <label className="flex items-center gap-2 self-end text-sm text-slate-700">
              <input
                type="checkbox"
                checked={draft.qualifyingForPensionGratuity}
                onChange={(e) => setDraft({ ...draft, qualifyingForPensionGratuity: e.target.checked })}
              />
              Qualifying for pension/gratuity
            </label>
          </div>
          <div className="mt-3 flex items-center justify-end gap-2">
            <SecondaryButton type="button" onClick={() => setDraft(null)}>
              Cancel
            </SecondaryButton>
            <button
              type="button"
              disabled={createMutation.isPending || !formValid}
              onClick={() => draft && createMutation.mutate(draft)}
              className="rounded-md bg-brand-forest px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
            >
              Save Record
            </button>
          </div>
          {createMutation.isError && <ErrorState message={describeApiError(createMutation.error, 'Could not save this record.')} />}
        </div>
      ) : (
        <SecondaryButton type="button" onClick={() => setDraft(emptyRequest())}>
          <Plus size={14} /> Add Past Service Record
        </SecondaryButton>
      )}
    </div>
  )
}
