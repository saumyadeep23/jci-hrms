import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../../../api/client'
import { formatDate } from '../../../../lib/date'
import { DatePicker } from '../../../../components/common/DatePicker'
import { FieldError, errorInputClass } from '../../../../components/common/ui'
import type { BloodGroup, Gender, MaritalStatus, OnboardingPersonalDetailsRequest, Salutation, SocialCategory } from '../../../../types/api'
import type { LocalSocialProfile } from '../onboardingTypes'

const PAN_PATTERN = /^[A-Z]{5}[0-9]{4}[A-Z]$/
const AADHAAR_PATTERN = /^[0-9]{12}$/
const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})/
const MIN_AGE_YEARS = 18

function maskAadhaar(value: string): string {
  if (value.length !== 12) return ''
  return `XXXX-XXXX-${value.slice(8)}`
}

/** Built via the local-time Date constructor (not `new Date(isoString)`) to avoid the UTC-midnight day-shift a date-only string is prone to - see lib/date.ts. */
function retirementPreview(dateOfBirth: string): string | null {
  const match = ISO_DATE.exec(dateOfBirth)
  if (!match) return null
  const [, year, month, day] = match
  const retirement58 = new Date(Number(year) + 58, Number(month) - 1, Number(day))
  return formatDate(retirement58)
}

/** null when dateOfBirth is empty/unparseable (nothing to validate yet) - not a validation failure by itself, that's `required`'s job. */
function ageYears(dateOfBirth: string): number | null {
  const match = ISO_DATE.exec(dateOfBirth)
  if (!match) return null
  const [, year, month, day] = match
  const dob = new Date(Number(year), Number(month) - 1, Number(day))
  const today = new Date()
  let age = today.getFullYear() - dob.getFullYear()
  const hasHadBirthdayThisYear = today.getMonth() > dob.getMonth() || (today.getMonth() === dob.getMonth() && today.getDate() >= dob.getDate())
  if (!hasHadBirthdayThisYear) age -= 1
  return age
}

/** PIMS_SPEC.md Onboarding Step 1: Personal & Bio-Data. */
export function Step1Personal({
  value,
  onChange,
  socialProfile,
  onSocialProfileChange,
}: {
  value: OnboardingPersonalDetailsRequest
  onChange: (next: OnboardingPersonalDetailsRequest) => void
  socialProfile: LocalSocialProfile
  onSocialProfileChange: (next: LocalSocialProfile) => void
}) {
  const preview = retirementPreview(value.dateOfBirth)
  const age = ageYears(value.dateOfBirth)
  const dobError = age !== null && age < MIN_AGE_YEARS ? `Must be at least ${MIN_AGE_YEARS} years old (currently ${age}).` : null
  const panError = value.panNumber !== '' && !PAN_PATTERN.test(value.panNumber) ? 'Invalid PAN format (e.g. ABCDE1234F).' : null

  // Placeholder only, never auto-filled into the actual value: the real
  // number is resolved fresh server-side at draft finalize time (see
  // EmployeeService.generateNextCpfAcNo()'s javadoc), so a value fetched
  // here could go stale by submit time and collide with one assigned to
  // someone else in the meantime. Left blank, the server auto-assigns.
  const nextCpfAcNoQuery = useQuery({
    queryKey: ['employees', 'next-cpf-ac-no'],
    queryFn: async () => (await apiClient.get<{ nextCpfAcNo: string }>('/v1/employees/next-cpf-ac-no')).data.nextCpfAcNo,
    staleTime: 0,
  })

  return (
    <div className="space-y-4">
    <div className="grid grid-cols-2 gap-3">
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Salutation</label>
        <select
          value={value.salutation}
          onChange={(e) => onChange({ ...value, salutation: e.target.value as Salutation })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        >
          <option value="MR">Mr</option>
          <option value="MS">Ms</option>
          <option value="MRS">Mrs</option>
          <option value="DR">Dr</option>
        </select>
      </div>
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Gender</label>
        <select
          value={value.gender}
          onChange={(e) => onChange({ ...value, gender: e.target.value as Gender })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        >
          <option value="MALE">Male</option>
          <option value="FEMALE">Female</option>
          <option value="OTHER">Other</option>
          <option value="PREFER_NOT_TO_SAY">Prefer not to say</option>
        </select>
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">First Name</label>
        <input
          required
          value={value.firstName}
          onChange={(e) => onChange({ ...value, firstName: e.target.value })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Middle Name</label>
        <input
          value={value.middleName ?? ''}
          onChange={(e) => onChange({ ...value, middleName: e.target.value })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Last Name</label>
        <input
          required
          value={value.lastName}
          onChange={(e) => onChange({ ...value, lastName: e.target.value })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Date of Birth</label>
        <DatePicker value={value.dateOfBirth} onChange={(v) => onChange({ ...value, dateOfBirth: v })} hasError={dobError !== null} />
        <FieldError message={dobError} />
        {!dobError && preview && (
          <p className="mt-1 text-[11px] text-slate-400">
            Indicative superannuation (58 yrs, Regular cadre): {preview}. Directors and service extensions may differ - final date is
            computed at activation.
          </p>
        )}
      </div>
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Marital Status</label>
        <select
          value={value.maritalStatus}
          onChange={(e) => onChange({ ...value, maritalStatus: e.target.value as MaritalStatus })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        >
          <option value="SINGLE">Single</option>
          <option value="MARRIED">Married</option>
          <option value="WIDOWED">Widowed</option>
          <option value="DIVORCED">Divorced</option>
          <option value="OTHER">Other</option>
        </select>
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Blood Group</label>
        <select
          value={value.bloodGroup ?? ''}
          onChange={(e) => onChange({ ...value, bloodGroup: (e.target.value || null) as BloodGroup | null })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        >
          <option value="">Unknown</option>
          {(['A_POSITIVE', 'A_NEGATIVE', 'B_POSITIVE', 'B_NEGATIVE', 'AB_POSITIVE', 'AB_NEGATIVE', 'O_POSITIVE', 'O_NEGATIVE'] as const).map(
            (bg) => (
              <option key={bg} value={bg}>
                {bg.replace('_', ' ')}
              </option>
            ),
          )}
        </select>
      </div>
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Nationality</label>
        <input
          required
          value={value.nationality}
          onChange={(e) => onChange({ ...value, nationality: e.target.value })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Mother Tongue</label>
        <input
          value={value.motherTongue ?? ''}
          onChange={(e) => onChange({ ...value, motherTongue: e.target.value })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">PAN Number</label>
        <input
          required
          maxLength={10}
          value={value.panNumber}
          onChange={(e) => onChange({ ...value, panNumber: e.target.value.toUpperCase() })}
          className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm uppercase ${errorInputClass(panError !== null)}`}
          placeholder="ABCDE1234F"
        />
        <FieldError message={panError} />
      </div>
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">CPF A/C No.</label>
        <input
          maxLength={20}
          value={value.cpfAcNo}
          onChange={(e) => onChange({ ...value, cpfAcNo: e.target.value })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          placeholder={nextCpfAcNoQuery.data ? `e.g. ${nextCpfAcNoQuery.data} (leave blank to auto-assign)` : 'Leave blank to auto-assign'}
        />
        <p className="mt-1 text-[11px] text-slate-400">Only fill this in for an existing legacy PF ledger number - otherwise the next number is assigned automatically.</p>
      </div>
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">UAN (Universal Account Number)</label>
        <input
          maxLength={12}
          inputMode="numeric"
          value={value.uanNo ?? ''}
          onChange={(e) => onChange({ ...value, uanNo: e.target.value.replace(/\D/g, '').slice(0, 12) })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          placeholder="e.g. 101234567890"
        />
      </div>
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Aadhaar Number</label>
        <input
          maxLength={12}
          inputMode="numeric"
          value={value.aadhaarNumber ?? ''}
          onChange={(e) => onChange({ ...value, aadhaarNumber: e.target.value.replace(/\D/g, '').slice(0, 12) })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
        {value.aadhaarNumber && AADHAAR_PATTERN.test(value.aadhaarNumber) && (
          <p className="mt-1 text-[11px] text-slate-400">Will be stored masked as {maskAadhaar(value.aadhaarNumber)}</p>
        )}
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Personal Email</label>
        <input
          required
          type="email"
          value={value.personalEmail}
          onChange={(e) => onChange({ ...value, personalEmail: e.target.value })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Official Email (@jcimail.in)</label>
        <input
          type="email"
          value={value.officialEmail ?? ''}
          onChange={(e) => onChange({ ...value, officialEmail: e.target.value })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Phone</label>
        <input
          required
          maxLength={10}
          inputMode="numeric"
          value={value.phone}
          onChange={(e) => onChange({ ...value, phone: e.target.value.replace(/\D/g, '').slice(0, 10) })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Official Mobile</label>
        <input
          value={value.officialMobile ?? ''}
          onChange={(e) => onChange({ ...value, officialMobile: e.target.value })}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>
    </div>

    <div className="rounded-md border border-slate-200 p-3">
      <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
        Reservation / Social Profile (optional)
      </p>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Social Category</label>
          <select
            value={socialProfile.socialCategory}
            onChange={(e) => onSocialProfileChange({ ...socialProfile, socialCategory: e.target.value as SocialCategory })}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="GEN">General</option>
            <option value="SC">SC</option>
            <option value="ST">ST</option>
            <option value="OBC_NCL">OBC (Non-Creamy Layer)</option>
            <option value="EWS">EWS</option>
            <option value="OTHER">Other</option>
          </select>
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Sub-Caste / Community</label>
          <input
            value={socialProfile.subCasteCommunity}
            onChange={(e) => onSocialProfileChange({ ...socialProfile, subCasteCommunity: e.target.value })}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={socialProfile.isPwbd}
            onChange={(e) => onSocialProfileChange({ ...socialProfile, isPwbd: e.target.checked })}
          />
          Person with Benchmark Disability (PwBD)
        </label>
        {socialProfile.isPwbd && (
          <>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Disability Type</label>
              <input
                value={socialProfile.disabilityType}
                onChange={(e) => onSocialProfileChange({ ...socialProfile, disabilityType: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Disability Percentage</label>
              <input
                type="number"
                min="0"
                max="100"
                value={socialProfile.disabilityPercentage}
                onChange={(e) => onSocialProfileChange({ ...socialProfile, disabilityPercentage: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
          </>
        )}
      </div>
    </div>
    </div>
  )
}
