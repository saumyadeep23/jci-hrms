import { AlertTriangle, Plus, Trash2 } from 'lucide-react'
import { DatePicker } from '../../../../components/common/DatePicker'
import { SecondaryButton, errorInputClass } from '../../../../components/common/ui'
import { emptyDependent, emptyNominee, type LocalFamily, type LocalSocialProfile } from '../onboardingTypes'

/** PIMS_SPEC.md Onboarding Step 7: Family, Dependents & Nominees (share % must sum to exactly 100 per nomineeFor group). */
export function Step7Family({
  value,
  onChange,
  socialProfile,
  onSocialProfileChange,
}: {
  value: LocalFamily
  onChange: (next: LocalFamily) => void
  socialProfile: LocalSocialProfile
  onSocialProfileChange: (next: LocalSocialProfile) => void
}) {
  const shareTotals = new Map<string, number>()
  for (const nominee of value.nominees) {
    const amount = Number(nominee.sharePercentage) || 0
    shareTotals.set(nominee.nomineeFor, (shareTotals.get(nominee.nomineeFor) ?? 0) + amount)
  }

  function updateDependent(key: string, patch: Partial<(typeof value.dependents)[number]>) {
    onChange({ ...value, dependents: value.dependents.map((row) => (row.key === key ? { ...row, ...patch } : row)) })
  }
  function removeDependent(key: string) {
    onChange({ ...value, dependents: value.dependents.filter((row) => row.key !== key) })
  }
  function updateNominee(key: string, patch: Partial<(typeof value.nominees)[number]>) {
    onChange({ ...value, nominees: value.nominees.map((row) => (row.key === key ? { ...row, ...patch } : row)) })
  }
  function removeNominee(key: string) {
    onChange({ ...value, nominees: value.nominees.filter((row) => row.key !== key) })
  }

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Father's Name</label>
          <input
            required
            value={value.fatherName}
            onChange={(e) => onChange({ ...value, fatherName: e.target.value })}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Mother's Name</label>
          <input
            value={value.motherName}
            onChange={(e) => onChange({ ...value, motherName: e.target.value })}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Spouse Name</label>
          <input
            value={value.spouseName}
            onChange={(e) => onChange({ ...value, spouseName: e.target.value })}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Spouse Date of Birth</label>
          <DatePicker value={value.spouseDob} onChange={(v) => onChange({ ...value, spouseDob: v })} />
        </div>
      </div>

      <div className="flex flex-wrap gap-4 rounded-md border border-slate-200 p-3">
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={socialProfile.isExServiceman}
            onChange={(e) => onSocialProfileChange({ ...socialProfile, isExServiceman: e.target.checked })}
          />
          Ex-Serviceman
        </label>
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={socialProfile.isSportsQuota}
            onChange={(e) => onSocialProfileChange({ ...socialProfile, isSportsQuota: e.target.checked })}
          />
          Sports Quota
        </label>
      </div>

      <div>
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Dependents</p>
        <div className="space-y-2">
          {value.dependents.map((row) => (
            <div key={row.key} className="grid grid-cols-6 items-center gap-2 rounded-md border border-slate-200 p-2">
              <input
                placeholder="Name"
                value={row.name}
                onChange={(e) => updateDependent(row.key, { name: e.target.value })}
                className="col-span-2 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
              />
              <select
                value={row.relationship}
                onChange={(e) => updateDependent(row.key, { relationship: e.target.value })}
                className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
              >
                <option value="FATHER">Father</option>
                <option value="MOTHER">Mother</option>
                <option value="SPOUSE">Spouse</option>
                <option value="SON">Son</option>
                <option value="DAUGHTER">Daughter</option>
              </select>
              <DatePicker value={row.dateOfBirth} onChange={(v) => updateDependent(row.key, { dateOfBirth: v })} />
              <label className="flex items-center gap-1 text-xs text-slate-600">
                <input type="checkbox" checked={row.isCoveredMedical} onChange={(e) => updateDependent(row.key, { isCoveredMedical: e.target.checked })} />
                Medical
              </label>
              <button type="button" onClick={() => removeDependent(row.key)} className="justify-self-end rounded-md p-1.5 text-red-500 hover:bg-red-50">
                <Trash2 size={14} />
              </button>
            </div>
          ))}
        </div>
        <SecondaryButton onClick={() => onChange({ ...value, dependents: [...value.dependents, emptyDependent()] })} className="mt-2">
          <Plus size={14} /> Add Dependent
        </SecondaryButton>
      </div>

      <div>
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Nominees</p>
        <div className="space-y-2">
          {value.nominees.map((row) => (
            <div key={row.key} className="grid grid-cols-6 items-center gap-2 rounded-md border border-slate-200 p-2">
              <input
                placeholder="Name"
                value={row.name}
                onChange={(e) => updateNominee(row.key, { name: e.target.value })}
                className="col-span-2 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
              />
              <select
                value={row.relationship}
                onChange={(e) => updateNominee(row.key, { relationship: e.target.value })}
                className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
              >
                <option value="FATHER">Father</option>
                <option value="MOTHER">Mother</option>
                <option value="SPOUSE">Spouse</option>
                <option value="SON">Son</option>
                <option value="DAUGHTER">Daughter</option>
              </select>
              <select
                value={row.nomineeFor}
                onChange={(e) => updateNominee(row.key, { nomineeFor: e.target.value })}
                className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
              >
                <option value="PF">PF</option>
                <option value="GRATUITY">Gratuity</option>
              </select>
              <input
                type="number"
                step="0.01"
                min="0"
                max="100"
                placeholder="Share %"
                value={row.sharePercentage}
                onChange={(e) => updateNominee(row.key, { sharePercentage: e.target.value })}
                className={`rounded-md border border-slate-300 px-2 py-1.5 text-sm ${errorInputClass((shareTotals.get(row.nomineeFor) ?? 0) !== 100)}`}
              />
              <button type="button" onClick={() => removeNominee(row.key)} className="justify-self-end rounded-md p-1.5 text-red-500 hover:bg-red-50">
                <Trash2 size={14} />
              </button>
            </div>
          ))}
        </div>
        <SecondaryButton onClick={() => onChange({ ...value, nominees: [...value.nominees, emptyNominee()] })} className="mt-2">
          <Plus size={14} /> Add Nominee
        </SecondaryButton>

        {shareTotals.size > 0 && (
          <div className="mt-3 space-y-1">
            {Array.from(shareTotals.entries()).map(([group, total]) =>
              total === 100 ? (
                <p key={group} className="text-xs text-emerald-600">
                  {group}: {total}% ✓
                </p>
              ) : (
                <p key={group} className="flex items-center gap-1 text-xs font-medium text-rose-600">
                  <AlertTriangle size={12} className="shrink-0" />
                  {group}: {total}% (must sum to exactly 100%)
                </p>
              ),
            )}
          </div>
        )}
      </div>
    </div>
  )
}
