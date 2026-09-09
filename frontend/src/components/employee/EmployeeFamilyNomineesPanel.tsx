import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { apiClient } from '../../api/client'
import { describeApiError } from '../../lib/apiError'
import { useToast } from '../common/ToastProvider'
import { DatePicker } from '../common/DatePicker'
import { ErrorState, LoadingState, PrimaryButton, SecondaryButton, errorInputClass } from '../common/ui'
import type {
  CeaEligibilityStatus,
  CompositeDependentEntry,
  CompositeNomineeEntry,
  EmployeeFamilyNomineeCompositeRequest,
  EmployeeFamilyNomineeCompositeResponse,
  FamilyRelationshipType,
  Gender,
} from '../../types/api'

interface LocalDependentRow {
  /** The dependent's own id as a string for an existing row, or a UI-generated temp key for a row added this session - LocalNomineeRow.dependentClientKey references this, matching CompositeDependentEntry/CompositeNomineeEntry's own linking convention. */
  clientKey: string
  id: number | null
  name: string
  relationship: FamilyRelationshipType
  dateOfBirth: string
  gender: Gender | ''
  isDependent: boolean
  isCoveredMedical: boolean
  isDivyang: boolean
  disabilityPercentage: string
  isMultipleBirthSecondDelivery: boolean
}

interface LocalNomineeRow {
  id: number | null
  dependentClientKey: string
  sharePercentage: string
}

type NomineeTab = 'PF' | 'GRATUITY'

let tempKeySeq = 0
function nextTempKey(): string {
  tempKeySeq += 1
  return `new-${tempKeySeq}`
}

function emptyDependentRow(): LocalDependentRow {
  return {
    clientKey: nextTempKey(),
    id: null,
    name: '',
    relationship: 'SON',
    dateOfBirth: '',
    gender: '',
    isDependent: true,
    isCoveredMedical: false,
    isDivyang: false,
    disabilityPercentage: '',
    isMultipleBirthSecondDelivery: false,
  }
}

function emptyNomineeRow(): LocalNomineeRow {
  return { id: null, dependentClientKey: '', sharePercentage: '' }
}

function ageOn(dateOfBirth: string): number | null {
  if (!dateOfBirth) return null
  const dob = new Date(dateOfBirth)
  if (Number.isNaN(dob.getTime())) return null
  const today = new Date()
  let age = today.getFullYear() - dob.getFullYear()
  const monthDiff = today.getMonth() - dob.getMonth()
  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < dob.getDate())) {
    age -= 1
  }
  return age
}

/** Mirrors EmployeeDependent.computeCeaEligibility() exactly (see its own javadoc), so the badge updates live as the grid is edited instead of only after a save round-trip. */
function computeCeaEligibility(row: LocalDependentRow): CeaEligibilityStatus | null {
  if (row.relationship !== 'SON' && row.relationship !== 'DAUGHTER') return null
  const age = ageOn(row.dateOfBirth)
  if (age === null) return 'INELIGIBLE_OVERAGE'
  if (row.isDivyang && age <= 22) return 'ELIGIBLE_DIVYANG'
  if (row.isDependent && age <= 20) return 'ELIGIBLE_STANDARD'
  return 'INELIGIBLE_OVERAGE'
}

const CEA_BADGE: Record<CeaEligibilityStatus, { label: string; className: string }> = {
  ELIGIBLE_STANDARD: { label: 'ELIGIBLE (STANDARD)', className: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  ELIGIBLE_DIVYANG: { label: 'ELIGIBLE (DIVYANG)', className: 'bg-blue-50 text-blue-700 border-blue-200' },
  INELIGIBLE_OVERAGE: { label: 'INELIGIBLE (OVERAGE)', className: 'bg-rose-50 text-rose-700 border-rose-200' },
}

function CeaBadge({ status }: { status: CeaEligibilityStatus | null }) {
  if (status === null) return <span className="text-xs text-slate-400">—</span>
  const { label, className } = CEA_BADGE[status]
  return <span className={`inline-block whitespace-nowrap rounded-full border px-2 py-0.5 text-[10px] font-semibold ${className}`}>{label}</span>
}

const RELATIONSHIP_OPTIONS: { value: FamilyRelationshipType; label: string }[] = [
  { value: 'FATHER', label: 'Father' },
  { value: 'MOTHER', label: 'Mother' },
  { value: 'SPOUSE', label: 'Spouse' },
  { value: 'SON', label: 'Son' },
  { value: 'DAUGHTER', label: 'Daughter' },
]

function relationshipLabel(value: FamilyRelationshipType): string {
  return RELATIONSHIP_OPTIONS.find((o) => o.value === value)?.label ?? value
}

/** Government single-relationship rule: at most one Father, one Mother, one Spouse per employee - enforced again server-side (EmployeeFamilyNomineeCompositeService). Son/Daughter have no such limit. */
const SINGLE_INSTANCE_RELATIONSHIPS: FamilyRelationshipType[] = ['FATHER', 'MOTHER', 'SPOUSE']

/** Hides a single-instance relationship from THIS row's dropdown once another row already has it - keeps it as the current row's own selected value if that's what it already is, and it reappears the instant that other row is deleted or changed (this is just a render-time filter over live `dependents` state, not separately tracked). */
function relationshipOptionsFor(row: LocalDependentRow, dependents: LocalDependentRow[]) {
  const takenElsewhere = new Set(
    dependents
      .filter((d) => d.clientKey !== row.clientKey && SINGLE_INSTANCE_RELATIONSHIPS.includes(d.relationship))
      .map((d) => d.relationship),
  )
  return RELATIONSHIP_OPTIONS.filter((opt) => opt.value === row.relationship || !takenElsewhere.has(opt.value))
}

/** Rounded to 2dp before comparison so floating-point drift (e.g. 33.33+33.33+33.34) never falsely fails the exactly-100% check. */
function sumShares(rows: LocalNomineeRow[]): number {
  const raw = rows.reduce((total, row) => total + (Number(row.sharePercentage) || 0), 0)
  return Math.round(raw * 100) / 100
}

/**
 * Unified Family & Dependent Register + PF/Gratuity Nomination Master, saved as one composite
 * request via EmployeeFamilyNomineeCompositeController - replaces the old three separate
 * save-family/save-dependent/save-nominee actions. Nominee rows select a Family Register member by
 * clientKey (see LocalDependentRow's own comment) rather than re-typing name/relationship/DOB, which
 * works even for a family member added earlier in the same editing session and not yet persisted.
 */
export function EmployeeFamilyNomineesPanel({
  employeeId,
  onDirtyChange,
}: {
  employeeId: number
  /** Lets the parent Edit Employee Modal block its own, differently-scoped "Save Changes" button while this tab has unsaved edits - see EditEmployeeModal's own comment for the exact bug this prevents. */
  onDirtyChange?: (dirty: boolean) => void
}) {
  const queryClient = useQueryClient()
  const { show } = useToast()
  const queryKey = ['employee-family-nominees', employeeId]
  const baselineRef = useRef<string | null>(null)
  const skipNextDirtyCheckRef = useRef(false)
  const [isDirty, setIsDirty] = useState(false)

  const compositeQuery = useQuery({
    queryKey,
    queryFn: async () => (await apiClient.get<EmployeeFamilyNomineeCompositeResponse>(`/employees/${employeeId}/family-nominees`)).data,
  })

  const [dependents, setDependents] = useState<LocalDependentRow[]>([])
  const [pfNominees, setPfNominees] = useState<LocalNomineeRow[]>([])
  const [gratuityNominees, setGratuityNominees] = useState<LocalNomineeRow[]>([])
  const [activeTab, setActiveTab] = useState<NomineeTab>('PF')

  useEffect(() => {
    const data = compositeQuery.data
    if (!data) return
    setDependents(
      data.dependents.map((d) => ({
        clientKey: String(d.id),
        id: d.id,
        name: d.name,
        relationship: d.relationship,
        dateOfBirth: d.dateOfBirth ?? '',
        gender: d.gender ?? '',
        isDependent: d.isDependent,
        isCoveredMedical: d.isCoveredMedical,
        isDivyang: d.isDivyang,
        disabilityPercentage: d.disabilityPercentage != null ? String(d.disabilityPercentage) : '',
        isMultipleBirthSecondDelivery: d.isMultipleBirthSecondDelivery,
      })),
    )
    const toLocalNominee = (n: (typeof data.pfNominees)[number]): LocalNomineeRow => ({
      id: n.id,
      dependentClientKey: n.dependentId != null ? String(n.dependentId) : '',
      sharePercentage: String(n.sharePercentage),
    })
    setPfNominees(data.pfNominees.map(toLocalNominee))
    setGratuityNominees(data.gratuityNominees.map(toLocalNominee))
    // The state updates above land on the NEXT render, not this one - the dirty-check effect below
    // (which runs after every render) picks this flag up then and treats that render's values as the
    // new clean baseline, rather than comparing them against the old baseline as if the user had typed them.
    skipNextDirtyCheckRef.current = true
  }, [compositeQuery.data])

  useEffect(() => {
    const snapshot = JSON.stringify({ dependents, pfNominees, gratuityNominees })
    if (skipNextDirtyCheckRef.current) {
      baselineRef.current = snapshot
      skipNextDirtyCheckRef.current = false
      setIsDirty(false)
      onDirtyChange?.(false)
      return
    }
    const dirty = baselineRef.current !== null && snapshot !== baselineRef.current
    setIsDirty(dirty)
    onDirtyChange?.(dirty)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dependents, pfNominees, gratuityNominees])

  // Tell the parent this tab is clean again if it unmounts mid-edit (e.g. the user switches tabs) -
  // an unsaved edit is about to be discarded either way (this panel is remounted fresh from the server
  // next time), so the parent's outer "Save Changes" button shouldn't stay blocked afterward.
  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange])

  const saveMutation = useMutation({
    mutationFn: async () => {
      const toDependentEntry = (d: LocalDependentRow): CompositeDependentEntry => ({
        clientKey: d.clientKey,
        id: d.id,
        name: d.name,
        relationship: d.relationship,
        dateOfBirth: d.dateOfBirth,
        gender: d.gender || null,
        isDependent: d.isDependent,
        isCoveredMedical: d.isCoveredMedical,
        isDivyang: d.isDivyang,
        disabilityPercentage: d.disabilityPercentage !== '' ? Number(d.disabilityPercentage) : null,
        isMultipleBirthSecondDelivery: d.isMultipleBirthSecondDelivery,
      })
      const toNomineeEntry = (n: LocalNomineeRow): CompositeNomineeEntry => ({
        id: n.id,
        dependentClientKey: n.dependentClientKey,
        sharePercentage: Number(n.sharePercentage),
      })
      const body: EmployeeFamilyNomineeCompositeRequest = {
        dependents: dependents.map(toDependentEntry),
        pfNominees: pfNominees.map(toNomineeEntry),
        gratuityNominees: gratuityNominees.map(toNomineeEntry),
      }
      return apiClient.put(`/employees/${employeeId}/family-nominees`, body)
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'Family & Nominees saved.' })
      // Marks the current (just-saved) values as the new clean baseline immediately, rather than
      // waiting for the invalidated query's refetch round-trip to come back before the parent's outer
      // "Save Changes" button unblocks.
      baselineRef.current = JSON.stringify({ dependents, pfNominees, gratuityNominees })
      setIsDirty(false)
      onDirtyChange?.(false)
      queryClient.invalidateQueries({ queryKey })
    },
  })

  function updateDependent(clientKey: string, patch: Partial<LocalDependentRow>) {
    setDependents((rows) => rows.map((row) => (row.clientKey === clientKey ? { ...row, ...patch } : row)))
  }
  function removeDependent(clientKey: string) {
    setDependents((rows) => rows.filter((row) => row.clientKey !== clientKey))
    // A removed family member can no longer be nominated for anything.
    setPfNominees((rows) => rows.filter((row) => row.dependentClientKey !== clientKey))
    setGratuityNominees((rows) => rows.filter((row) => row.dependentClientKey !== clientKey))
  }

  function nomineeSetterFor(tab: NomineeTab) {
    return tab === 'PF' ? setPfNominees : setGratuityNominees
  }
  function updateNominee(tab: NomineeTab, index: number, patch: Partial<LocalNomineeRow>) {
    nomineeSetterFor(tab)((rows) => rows.map((row, i) => (i === index ? { ...row, ...patch } : row)))
  }
  function removeNominee(tab: NomineeTab, index: number) {
    nomineeSetterFor(tab)((rows) => rows.filter((_, i) => i !== index))
  }

  if (compositeQuery.isLoading) return <LoadingState label="Loading family details..." />
  if (compositeQuery.isError) return <ErrorState message={describeApiError(compositeQuery.error, 'Could not load family details.')} />

  const pfTotal = sumShares(pfNominees)
  const gratuityTotal = sumShares(gratuityNominees)
  const pfInvalid = pfNominees.length > 0 && pfTotal !== 100
  const gratuityInvalid = gratuityNominees.length > 0 && gratuityTotal !== 100
  const hasIncompleteDependent = dependents.some((d) => d.name.trim() === '' || d.dateOfBirth === '')
  const hasUnlinkedNominee = [...pfNominees, ...gratuityNominees].some((n) => n.dependentClientKey === '')
  // Father's Name was a required top-of-tab input before that section was removed as redundant with
  // this same register - the requirement now maps to "at least one FATHER row", enforced here and
  // again server-side (EmployeeFamilyNomineeCompositeService.upsertFamily()).
  const hasFather = dependents.some((d) => d.relationship === 'FATHER')
  const canSave = hasFather && !pfInvalid && !gratuityInvalid && !hasIncompleteDependent && !hasUnlinkedNominee && !saveMutation.isPending

  const activeNominees = activeTab === 'PF' ? pfNominees : gratuityNominees
  const activeTotal = activeTab === 'PF' ? pfTotal : gratuityTotal
  const activeInvalid = activeTab === 'PF' ? pfInvalid : gratuityInvalid

  return (
    <div className="space-y-6">
      <div>
        <div className="mb-2 flex items-baseline justify-between">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Family &amp; Dependent Register</p>
          {!hasFather && (
            <p className="text-xs font-medium text-rose-600">A Father entry is required.</p>
          )}
        </div>
        <div className="overflow-x-auto rounded-md border border-slate-200">
          <table className="w-full min-w-[960px] border-collapse text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50 text-left text-xs text-slate-500">
                <th className="p-2">Name</th>
                <th className="p-2">Relationship</th>
                <th className="p-2">DOB</th>
                <th className="p-2">Gender</th>
                <th className="p-2">Dependent</th>
                <th className="p-2">Medical Cover</th>
                <th className="p-2">Divyang / PwD</th>
                <th className="p-2">Twin</th>
                <th className="p-2">CEA Eligibility</th>
                <th className="p-2" />
              </tr>
            </thead>
            <tbody>
              {dependents.length === 0 && (
                <tr>
                  <td colSpan={10} className="p-3 text-center text-xs text-slate-400">
                    No family members added yet.
                  </td>
                </tr>
              )}
              {dependents.map((row) => (
                <tr key={row.clientKey} className="border-b border-slate-100 align-top">
                  <td className="p-2">
                    <input
                      value={row.name}
                      onChange={(e) => updateDependent(row.clientKey, { name: e.target.value })}
                      className={`w-32 rounded-md border border-slate-300 px-2 py-1.5 text-sm ${errorInputClass(row.name.trim() === '')}`}
                    />
                  </td>
                  <td className="p-2">
                    <select
                      value={row.relationship}
                      onChange={(e) => updateDependent(row.clientKey, { relationship: e.target.value as FamilyRelationshipType })}
                      className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                    >
                      {relationshipOptionsFor(row, dependents).map((opt) => (
                        <option key={opt.value} value={opt.value}>
                          {opt.label}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className="p-2">
                    <DatePicker value={row.dateOfBirth} onChange={(v) => updateDependent(row.clientKey, { dateOfBirth: v })} hasError={row.dateOfBirth === ''} />
                  </td>
                  <td className="p-2">
                    <select
                      value={row.gender}
                      onChange={(e) => updateDependent(row.clientKey, { gender: e.target.value as Gender | '' })}
                      className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                    >
                      <option value="">—</option>
                      <option value="MALE">Male</option>
                      <option value="FEMALE">Female</option>
                      <option value="OTHER">Other</option>
                      <option value="PREFER_NOT_TO_SAY">Prefer not to say</option>
                    </select>
                  </td>
                  <td className="p-2 text-center">
                    <input type="checkbox" checked={row.isDependent} onChange={(e) => updateDependent(row.clientKey, { isDependent: e.target.checked })} />
                  </td>
                  <td className="p-2 text-center">
                    <input
                      type="checkbox"
                      checked={row.isCoveredMedical}
                      onChange={(e) => updateDependent(row.clientKey, { isCoveredMedical: e.target.checked })}
                    />
                  </td>
                  <td className="p-2">
                    <div className="flex items-center gap-1">
                      <input type="checkbox" checked={row.isDivyang} onChange={(e) => updateDependent(row.clientKey, { isDivyang: e.target.checked })} />
                      {row.isDivyang && (
                        <input
                          type="number"
                          min="0"
                          max="100"
                          step="0.01"
                          placeholder="%"
                          value={row.disabilityPercentage}
                          onChange={(e) => updateDependent(row.clientKey, { disabilityPercentage: e.target.value })}
                          className="w-16 rounded-md border border-slate-300 px-1.5 py-1 text-xs"
                        />
                      )}
                    </div>
                  </td>
                  <td className="p-2 text-center">
                    <input
                      type="checkbox"
                      checked={row.isMultipleBirthSecondDelivery}
                      onChange={(e) => updateDependent(row.clientKey, { isMultipleBirthSecondDelivery: e.target.checked })}
                    />
                  </td>
                  <td className="p-2">
                    <CeaBadge status={computeCeaEligibility(row)} />
                  </td>
                  <td className="p-2">
                    <button type="button" onClick={() => removeDependent(row.clientKey)} className="rounded-md p-1 text-red-500 hover:bg-red-50">
                      <Trash2 size={14} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <SecondaryButton className="mt-2" onClick={() => setDependents((rows) => [...rows, emptyDependentRow()])}>
          <Plus size={14} /> Add Family Member
        </SecondaryButton>
      </div>

      <div>
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Nomination Master</p>
        <div className="mb-3 flex gap-1 border-b border-slate-200">
          {(['PF', 'GRATUITY'] as const).map((tab) => (
            <button
              key={tab}
              type="button"
              onClick={() => setActiveTab(tab)}
              className={`px-3 py-1.5 text-sm font-medium ${
                activeTab === tab ? 'border-b-2 border-brand-forest text-brand-forest' : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              {tab === 'PF' ? 'Provident Fund (PF)' : 'Gratuity'}
            </button>
          ))}
        </div>

        <div className="overflow-x-auto rounded-md border border-slate-200">
          <table className="w-full min-w-[600px] border-collapse text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50 text-left text-xs text-slate-500">
                <th className="p-2">Nominee Name</th>
                <th className="p-2">Relationship</th>
                <th className="p-2">Date of Birth</th>
                <th className="p-2">Share %</th>
                <th className="p-2" />
              </tr>
            </thead>
            <tbody>
              {activeNominees.length === 0 && (
                <tr>
                  <td colSpan={5} className="p-3 text-center text-xs text-slate-400">
                    No {activeTab === 'PF' ? 'PF' : 'Gratuity'} nominees added yet.
                  </td>
                </tr>
              )}
              {activeNominees.map((row, index) => {
                const linked = dependents.find((d) => d.clientKey === row.dependentClientKey)
                return (
                  <tr key={row.id ?? `${activeTab}-new-${index}`} className="border-b border-slate-100">
                    <td className="p-2">
                      <select
                        value={row.dependentClientKey}
                        onChange={(e) => updateNominee(activeTab, index, { dependentClientKey: e.target.value })}
                        className={`rounded-md border border-slate-300 px-2 py-1.5 text-sm ${errorInputClass(row.dependentClientKey === '')}`}
                      >
                        <option value="">-- Select from Family Register --</option>
                        {dependents.map((d) => (
                          <option key={d.clientKey} value={d.clientKey}>
                            {d.name || '(unnamed)'}
                          </option>
                        ))}
                      </select>
                    </td>
                    {/* Auto-populated, read-only - never re-typed, always derived from the selected Family Register row. */}
                    <td className="p-2 text-slate-500">{linked ? relationshipLabel(linked.relationship) : '—'}</td>
                    <td className="p-2 text-slate-500">{linked?.dateOfBirth || '—'}</td>
                    <td className="p-2">
                      <input
                        type="number"
                        step="0.01"
                        min="0"
                        max="100"
                        value={row.sharePercentage}
                        onChange={(e) => updateNominee(activeTab, index, { sharePercentage: e.target.value })}
                        className={`w-24 rounded-md border border-slate-300 px-2 py-1.5 text-sm ${errorInputClass(activeInvalid)}`}
                      />
                    </td>
                    <td className="p-2">
                      <button type="button" onClick={() => removeNominee(activeTab, index)} className="rounded-md p-1 text-red-500 hover:bg-red-50">
                        <Trash2 size={14} />
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        <SecondaryButton className="mt-2" onClick={() => nomineeSetterFor(activeTab)((rows) => [...rows, emptyNomineeRow()])}>
          <Plus size={14} /> Add {activeTab === 'PF' ? 'PF' : 'Gratuity'} Nominee
        </SecondaryButton>
        {activeNominees.length > 0 && (
          <p className={`mt-2 text-xs font-medium ${activeInvalid ? 'text-rose-600' : 'text-emerald-600'}`}>
            Total: {activeTotal}% {activeInvalid ? '(must sum to exactly 100%)' : '✓'}
          </p>
        )}
      </div>

      {saveMutation.isError && <ErrorState message={describeApiError(saveMutation.error, 'Could not save family details.')} />}
      <div className="flex items-center justify-end gap-3">
        {isDirty && !saveMutation.isPending && (
          <p className="text-xs font-medium text-amber-600">
            Unsaved changes - the modal's main "Save Changes" button below does not save this tab.
          </p>
        )}
        <PrimaryButton type="button" disabled={!canSave} onClick={() => saveMutation.mutate()}>
          {saveMutation.isPending ? 'Saving...' : 'Save Family & Nominees'}
        </PrimaryButton>
      </div>
    </div>
  )
}
