import { Plus, Trash2 } from 'lucide-react'
import { SecondaryButton } from '../../../../components/common/ui'
import type { CourseType, DivisionClass, QualificationLevel } from '../../../../types/api'
import { emptyQualification, type LocalQualification } from '../onboardingTypes'
import { DocumentUploadField } from '../DocumentUploadField'

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

/** PIMS_SPEC.md Onboarding Step 4: Academic Qualifications (repeater + certificate upload). */
export function Step4Qualifications({ value, onChange }: { value: LocalQualification[]; onChange: (next: LocalQualification[]) => void }) {
  function updateRow(key: string, patch: Partial<LocalQualification>) {
    onChange(value.map((row) => (row.key === key ? { ...row, ...patch } : row)))
  }
  function removeRow(key: string) {
    onChange(value.filter((row) => row.key !== key))
  }

  return (
    <div className="space-y-4">
      {value.length === 0 && <p className="text-sm text-slate-500">No qualifications added yet.</p>}

      {value.map((row, index) => (
        <div key={row.key} className="rounded-md border border-slate-200 p-3">
          <div className="mb-2 flex items-center justify-between">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Qualification {index + 1}</p>
            <button type="button" onClick={() => removeRow(row.key)} className="rounded-md p-1 text-red-500 hover:bg-red-50" title="Remove">
              <Trash2 size={14} />
            </button>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Qualification Level</label>
              <select
                value={row.qualificationLevel}
                onChange={(e) => updateRow(row.key, { qualificationLevel: e.target.value as QualificationLevel })}
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
                value={row.degreeTitle}
                onChange={(e) => updateRow(row.key, { degreeTitle: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Specialization</label>
              <input
                value={row.specialization}
                onChange={(e) => updateRow(row.key, { specialization: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Board / University</label>
              <input
                required
                value={row.boardUniversity}
                onChange={(e) => updateRow(row.key, { boardUniversity: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Institution</label>
              <input
                value={row.institutionName}
                onChange={(e) => updateRow(row.key, { institutionName: e.target.value })}
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
                value={row.passingYear}
                onChange={(e) => updateRow(row.key, { passingYear: e.target.value })}
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
                value={row.percentageCgpa}
                onChange={(e) => updateRow(row.key, { percentageCgpa: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Division / Class</label>
              <select
                value={row.divisionClass}
                onChange={(e) => updateRow(row.key, { divisionClass: e.target.value as DivisionClass })}
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
                value={row.courseType}
                onChange={(e) => updateRow(row.key, { courseType: e.target.value as CourseType })}
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
                checked={row.highestQualification}
                onChange={(e) => updateRow(row.key, { highestQualification: e.target.checked })}
              />
              Highest qualification
            </label>
            <div className="col-span-2">
              <DocumentUploadField
                label="Certificate"
                category="QUALIFICATION"
                maxSizeLabel="5MB (PDF)"
                acceptHint=".pdf"
                currentFileName={row.certificateDocumentS3Key ? row.certificateDocumentS3Key.split('/').pop() : null}
                onUploaded={(res) => updateRow(row.key, { certificateDocumentS3Key: res.fileS3Key })}
              />
            </div>
          </div>
        </div>
      ))}

      <SecondaryButton onClick={() => onChange([...value, emptyQualification()])}>
        <Plus size={14} /> Add Qualification
      </SecondaryButton>
    </div>
  )
}
