import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { apiClient } from '../../api/client'
import { describeApiError } from '../../lib/apiError'
import { ErrorState, LoadingState, SecondaryButton } from '../common/ui'
import type { CourseType, DivisionClass, QualificationLevel, QualificationRequest, QualificationResponse } from '../../types/api'

const QUALIFICATION_LEVELS: QualificationLevel[] = [
  'TENTH_SECONDARY',
  'TWELFTH_HIGHER_SECONDARY',
  'DIPLOMA',
  'GRADUATION',
  'POST_GRADUATION',
  'DOCTORATE_PHD',
  'PROFESSIONAL_CERTIFICATION',
  'OTHER',
]
const DIVISION_CLASSES: DivisionClass[] = ['DISTINCTION', 'FIRST_CLASS', 'SECOND_CLASS', 'PASS_CLASS', 'GRADE_A', 'GRADE_B', 'GRADE_C']
const COURSE_TYPES: CourseType[] = ['FULL_TIME', 'PART_TIME', 'DISTANCE_CORRESPONDENCE', 'ONLINE']

function emptyRequest(): QualificationRequest {
  return {
    qualificationLevel: 'GRADUATION',
    degreeTitle: '',
    specialization: '',
    boardUniversity: '',
    institutionName: '',
    passingYear: new Date().getFullYear(),
    percentageCgpa: null,
    divisionClass: null,
    courseType: 'FULL_TIME',
    highestQualification: false,
    certificateDocumentS3Key: null,
  }
}

/**
 * Live CRUD against EmployeeQualificationController (already generic over
 * any employeeId, not draft-specific) - each row saves/deletes immediately,
 * independent of the modal's main "Save Changes" button.
 */
export function EmployeeQualificationsPanel({ employeeId }: { employeeId: number }) {
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState<QualificationRequest | null>(null)

  const listQuery = useQuery({
    queryKey: ['employee-qualifications', employeeId],
    queryFn: async () => (await apiClient.get<QualificationResponse[]>(`/employees/${employeeId}/qualifications`)).data,
  })

  const createMutation = useMutation({
    mutationFn: async (request: QualificationRequest) => apiClient.post(`/employees/${employeeId}/qualifications`, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['employee-qualifications', employeeId] })
      setDraft(null)
    },
  })
  const deleteMutation = useMutation({
    mutationFn: async (id: number) => apiClient.delete(`/employees/${employeeId}/qualifications/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['employee-qualifications', employeeId] }),
  })

  if (listQuery.isLoading) return <LoadingState label="Loading qualifications..." />
  if (listQuery.isError) return <ErrorState message="Could not load qualifications." />

  const rows = listQuery.data ?? []

  return (
    <div className="space-y-3">
      {rows.length === 0 && !draft && <p className="text-sm text-slate-500">No qualifications on record.</p>}

      {rows.map((row) => (
        <div key={row.id} className="rounded-md border border-slate-200 p-3">
          <div className="mb-1 flex items-center justify-between">
            <p className="text-sm font-medium text-slate-800">
              {row.qualificationLevel.replace(/_/g, ' ')} - {row.degreeTitle}
              {row.highestQualification && <span className="ml-2 text-[11px] font-normal text-brand-forest">(Highest)</span>}
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
            {row.boardUniversity} - {row.passingYear}
            {row.percentageCgpa != null ? ` - ${row.percentageCgpa}%` : ''}
            {row.verified ? ' - Verified' : ' - Not verified'}
          </p>
        </div>
      ))}

      {draft ? (
        <div className="rounded-md border border-slate-200 p-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Qualification Level</label>
              <select
                value={draft.qualificationLevel}
                onChange={(e) => setDraft({ ...draft, qualificationLevel: e.target.value as QualificationLevel })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                {QUALIFICATION_LEVELS.map((lvl) => (
                  <option key={lvl} value={lvl}>
                    {lvl.replace(/_/g, ' ')}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Degree / Title</label>
              <input
                required
                value={draft.degreeTitle}
                onChange={(e) => setDraft({ ...draft, degreeTitle: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Board / University</label>
              <input
                required
                value={draft.boardUniversity}
                onChange={(e) => setDraft({ ...draft, boardUniversity: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Institution</label>
              <input
                value={draft.institutionName ?? ''}
                onChange={(e) => setDraft({ ...draft, institutionName: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Passing Year</label>
              <input
                required
                type="number"
                min="1950"
                max="2100"
                value={draft.passingYear}
                onChange={(e) => setDraft({ ...draft, passingYear: Number(e.target.value) })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Percentage / CGPA</label>
              <input
                type="number"
                step="0.01"
                min="0"
                max="100"
                value={draft.percentageCgpa ?? ''}
                onChange={(e) => setDraft({ ...draft, percentageCgpa: e.target.value === '' ? null : Number(e.target.value) })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Division / Class</label>
              <select
                value={draft.divisionClass ?? ''}
                onChange={(e) => setDraft({ ...draft, divisionClass: (e.target.value || null) as DivisionClass | null })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">Not applicable</option>
                {DIVISION_CLASSES.map((d) => (
                  <option key={d} value={d}>
                    {d.replace(/_/g, ' ')}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Course Type</label>
              <select
                value={draft.courseType}
                onChange={(e) => setDraft({ ...draft, courseType: e.target.value as CourseType })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                {COURSE_TYPES.map((c) => (
                  <option key={c} value={c}>
                    {c.replace(/_/g, ' ')}
                  </option>
                ))}
              </select>
            </div>
            <label className="flex items-center gap-2 self-end text-sm text-slate-700">
              <input
                type="checkbox"
                checked={draft.highestQualification}
                onChange={(e) => setDraft({ ...draft, highestQualification: e.target.checked })}
              />
              Highest qualification
            </label>
          </div>
          <div className="mt-3 flex items-center justify-end gap-2">
            <SecondaryButton type="button" onClick={() => setDraft(null)}>
              Cancel
            </SecondaryButton>
            <button
              type="button"
              disabled={createMutation.isPending || draft.degreeTitle.trim() === '' || draft.boardUniversity.trim() === ''}
              onClick={() => createMutation.mutate(draft)}
              className="rounded-md bg-brand-forest px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
            >
              Save Qualification
            </button>
          </div>
          {createMutation.isError && <ErrorState message={describeApiError(createMutation.error, 'Could not save this qualification.')} />}
        </div>
      ) : (
        <SecondaryButton type="button" onClick={() => setDraft(emptyRequest())}>
          <Plus size={14} /> Add Qualification
        </SecondaryButton>
      )}
    </div>
  )
}
