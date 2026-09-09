import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle } from 'lucide-react'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { useToast } from '../common/ToastProvider'
import { describeApiError, describeApiErrorList, getFieldErrors } from '../../lib/apiError'
import { useIfscLookup } from '../../hooks/useIfscLookup'
import { DatePicker } from '../common/DatePicker'
import { Modal } from '../common/Modal'
import { FormErrorBanner } from '../common/FormErrorBanner'
import { Badge, ErrorState, FieldError, LoadingState, PrimaryButton, errorInputClass } from '../common/ui'
import { PincodeAddressFields, EMPTY_ADDRESS, type AddressValue } from './PincodeAddressFields'
import { DocumentUploadField } from '../../pages/hr/onboarding/DocumentUploadField'
import { PensionSchemeSection } from './PensionSchemeSection'
import { EmployeeVehicleAllotmentSection } from './EmployeeVehicleAllotmentSection'
import { EmployeeQualificationsPanel } from './EmployeeQualificationsPanel'
import { EmployeePastServicePanel } from './EmployeePastServicePanel'
import { EmployeeFamilyNomineesPanel } from './EmployeeFamilyNomineesPanel'
import { EmployeeQuarterAllotmentsPanel } from './EmployeeQuarterAllotmentsPanel'
import type {
  BloodGroup,
  DepartmentResponse,
  DesignationResponse,
  DpcResponse,
  EmployeeAddressRequest,
  EmployeeAddressResponse,
  EmployeeBankAccountRequest,
  EmployeeBankAccountResponse,
  EmployeeRequest,
  EmployeeResponse,
  EmployeeStatus,
  Gender,
  MaritalStatus,
  Page,
  PostMasterResponse,
  RegionalOfficeResponse,
  Salutation,
  SocialCategory,
  SocialProfileRequest,
  SocialProfileResponse,
} from '../../types/api'

const TABS = [
  'Personal & Bio-Data',
  'Employment & Post',
  'Contact',
  'Bank Account',
  'Address',
  'Qualifications',
  'Past Service',
  'Family & Nominees',
  'Company Accommodation',
] as const
type Tab = (typeof TABS)[number]

const PLACEHOLDER_PHONE_PATTERN = /^9{10}$|^900000/

/** dd-MM-yyyy/ISO parsed via the local-time Date constructor (never `new Date(isoString)`) so a date-only value never shifts a day under a UTC-behind timezone - see lib/date.ts's own javadoc for why. */
function isoToLocalDate(iso: string): Date | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso)
  if (!m) return null
  return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]))
}

function toIso(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** Mirrors SuperannuationCalculator.lastDayRuleAnniversary (Java) - last day of the birth month at `age`, or the preceding month if born on the 1st. */
function lastDayRuleAnniversary(dob: Date, age: number): Date {
  const targetYear = dob.getFullYear() + age
  if (dob.getDate() === 1) {
    return new Date(targetYear, dob.getMonth(), 0)
  }
  return new Date(targetYear, dob.getMonth() + 1, 0)
}

/** doj + 5 years - 1 day - mirrors SuperannuationCalculator's Director term-end rule. */
function directorTermEnd(doj: Date): Date {
  const d = new Date(doj.getFullYear() + 5, doj.getMonth(), doj.getDate())
  d.setDate(d.getDate() - 1)
  return d
}

interface SuperannuationPreview {
  date: string
  isDirector: boolean
}

/**
 * Live, client-side mirror of SuperannuationCalculator.calculateSuperannuationDate (Java) /
 * fn_calculate_jci_superannuation_date (V31/V53/V54 DB trigger) - REGULAR staff retire at 58, Board
 * Directors at 60 or 5 years from date of joining (whichever is earlier). "Director" is read off the
 * selected designation's categoryType (the same signal the DB trigger now falls back to for a direct
 * designation edit like this one - see V54) or its grade-scale cadre (BOARD) as a secondary signal.
 * This is a preview only - the persisted, authoritative date still comes from the DB trigger once
 * the employee is actually saved.
 */
function calculateSuperannuation(
  dobIso: string,
  dojIso: string,
  designationId: string,
  designations: DesignationResponse[],
): SuperannuationPreview | null {
  const dob = isoToLocalDate(dobIso)
  if (!dob) return null

  const designation = designations.find((d) => String(d.id) === designationId)
  const isDirector = designation?.categoryType === 'Director' || designation?.cadre === 'BOARD'

  const ageBasedDate = lastDayRuleAnniversary(dob, isDirector ? 60 : 58)
  if (!isDirector) {
    return { date: toIso(ageBasedDate), isDirector: false }
  }

  const doj = dojIso ? isoToLocalDate(dojIso) : null
  if (!doj) {
    return { date: toIso(ageBasedDate), isDirector: true }
  }
  const termEndDate = directorTermEnd(doj)
  return { date: toIso(termEndDate < ageBasedDate ? termEndDate : ageBasedDate), isDirector: true }
}

type EmployeeForm = {
  salutation: Salutation
  firstName: string
  middleName: string
  lastName: string
  gender: Gender
  dateOfBirth: string
  maritalStatus: MaritalStatus
  bloodGroup: BloodGroup | ''
  nationality: string
  motherTongue: string
  panNumber: string
  cpfAcNo: string
  uanNo: string
  aadhaarNumber: string
  personalEmail: string
  officialEmail: string
  phone: string
  officialMobile: string
  dateOfJoining: string
  departmentId: string
  designationId: string
  roId: string
  dpcId: string
  status: EmployeeStatus
  geofenceExempted: boolean
  /** Sanctioned Post (post_master) - '' means no post assignment. Selecting one auto-syncs departmentId/designationId/roId to the post's own values. */
  postId: string
  isNpsEligible: boolean
  isEpsEligible: boolean
  isEpsHigherPensionEligible: boolean
  pranNumber: string
}

type BankForm = {
  bankName: string
  bankBranch: string
  bankAccountNumber: string
  reenterBankAccountNumber: string
  bankIfsc: string
  cancelledChequeS3Key: string | null
}

const EMPTY_BANK_FORM: BankForm = {
  bankName: '',
  bankBranch: '',
  bankAccountNumber: '',
  reenterBankAccountNumber: '',
  bankIfsc: '',
  cancelledChequeS3Key: null,
}

function employeeFormFrom(employee: EmployeeResponse): EmployeeForm {
  return {
    salutation: employee.salutation,
    firstName: employee.firstName,
    middleName: employee.middleName ?? '',
    lastName: employee.lastName,
    gender: employee.gender,
    dateOfBirth: employee.dateOfBirth,
    maritalStatus: employee.maritalStatus,
    bloodGroup: employee.bloodGroup ?? '',
    nationality: employee.nationality,
    motherTongue: employee.motherTongue ?? '',
    panNumber: employee.panNumber,
    cpfAcNo: employee.cpfAcNo ?? '',
    uanNo: employee.uanNo ?? '',
    aadhaarNumber: '',
    personalEmail: employee.personalEmail,
    officialEmail: employee.officialEmail ?? '',
    phone: employee.phone,
    officialMobile: employee.officialMobile ?? '',
    dateOfJoining: employee.dateOfJoining,
    departmentId: String(employee.departmentId),
    designationId: String(employee.designationId),
    roId: employee.roId != null ? String(employee.roId) : '',
    dpcId: employee.dpcId != null ? String(employee.dpcId) : '',
    status: employee.status,
    geofenceExempted: employee.geofenceExempted,
    postId: employee.postId != null ? String(employee.postId) : '',
    isNpsEligible: employee.isNpsEligible,
    isEpsEligible: employee.isEpsEligible,
    isEpsHigherPensionEligible: employee.isEpsHigherPensionEligible,
    pranNumber: employee.pranNumber ?? '',
  }
}

function bankFormFrom(account: EmployeeBankAccountResponse | null): BankForm {
  if (!account) return EMPTY_BANK_FORM
  return {
    bankName: account.bankName,
    bankBranch: account.bankBranch,
    bankAccountNumber: account.bankAccountNumber,
    reenterBankAccountNumber: account.bankAccountNumber,
    bankIfsc: account.bankIfsc,
    cancelledChequeS3Key: null,
  }
}

function addressValueFrom(address: EmployeeAddressResponse | undefined): AddressValue {
  if (!address) return EMPTY_ADDRESS
  return {
    addressLine: address.addressLine1,
    addressLine2: address.addressLine2 ?? '',
    policeStation: address.policeStation ?? '',
    city: address.city,
    state: address.state,
    district: address.district,
    pinCode: address.pinCode,
  }
}

/** Wrapper that loads everything the tabbed form needs (employee, addresses, bank accounts, department/designation/RO/DPC option lists) before rendering the form - keeps every tab's initial state built from one consistent snapshot, so switching tabs never re-fetches or resets what's already been typed. */
export function EditEmployeeModal({ employeeId, onClose }: { employeeId: number; onClose: () => void }) {
  const employeeQuery = useQuery({
    queryKey: ['employee', employeeId],
    queryFn: async () => (await apiClient.get<EmployeeResponse>(`/employees/${employeeId}`)).data,
  })
  const addressesQuery = useQuery({
    queryKey: ['employee-addresses', employeeId],
    queryFn: async () => (await apiClient.get<EmployeeAddressResponse[]>(`/v1/employees/${employeeId}/addresses`)).data,
  })
  const bankAccountsQuery = useQuery({
    queryKey: ['employee-bank-accounts', employeeId],
    queryFn: async () => (await apiClient.get<EmployeeBankAccountResponse[]>(`/v1/employees/${employeeId}/bank-accounts`)).data,
  })

  const isLoading = employeeQuery.isLoading || addressesQuery.isLoading || bankAccountsQuery.isLoading
  const isError = employeeQuery.isError || addressesQuery.isError || bankAccountsQuery.isError

  if (isLoading || isError || !employeeQuery.data) {
    return (
      <Modal title="Edit Employee" onClose={onClose}>
        {isLoading && <LoadingState label="Loading employee..." />}
        {isError && <p className="text-sm text-rose-600">Could not load this employee's full record.</p>}
      </Modal>
    )
  }

  return (
    <EditEmployeeForm
      employee={employeeQuery.data}
      addresses={addressesQuery.data ?? []}
      bankAccounts={bankAccountsQuery.data ?? []}
      onClose={onClose}
    />
  )
}

function EditEmployeeForm({
  employee,
  addresses,
  bankAccounts,
  onClose,
}: {
  employee: EmployeeResponse
  addresses: EmployeeAddressResponse[]
  bankAccounts: EmployeeBankAccountResponse[]
  onClose: () => void
}) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('Personal & Bio-Data')
  // Family & Nominees saves independently via its own button/endpoint, not through this form's
  // submit - the bug this guards against: a user fills that tab, clicks THIS button (identically
  // labeled "Save Changes" and far more prominent - full-width, bottom of the modal, on every tab)
  // instead of the panel's own, gets a false-positive "Employee updated." toast, and the modal closes
  // with the family data never sent anywhere. Blocking this button while that tab has unsaved edits
  // forces the user through the panel's own save first, so nothing gets silently discarded.
  const [familyTabDirty, setFamilyTabDirty] = useState(false)

  const presentAddress = addresses.find((a) => a.addressType === 'PRESENT')
  const permanentAddress = addresses.find((a) => a.addressType === 'PERMANENT')
  const primaryBankAccount = bankAccounts.find((a) => a.primaryDisbursal) ?? bankAccounts[0] ?? null

  const [form, setForm] = useState<EmployeeForm>(() => employeeFormFrom(employee))
  const [permanent, setPermanent] = useState<AddressValue>(() => addressValueFrom(permanentAddress))
  const [presentSameAsPermanent, setPresentSameAsPermanent] = useState(
    () => Boolean(permanentAddress) && presentAddress?.addressLine1 === permanentAddress?.addressLine1 && presentAddress?.pinCode === permanentAddress?.pinCode,
  )
  const [present, setPresent] = useState<AddressValue>(() => addressValueFrom(presentAddress))
  const [bankForm, setBankForm] = useState<BankForm>(() => bankFormFrom(primaryBankAccount))
  const initialBankForm = useMemo(() => bankFormFrom(primaryBankAccount), [primaryBankAccount])

  const departmentsQuery = useQuery({
    queryKey: ['departments-all'],
    queryFn: async () => (await apiClient.get<Page<DepartmentResponse>>('/departments', { params: { size: 200 } })).data.content,
  })
  const designationsQuery = useQuery({
    queryKey: ['designations-all'],
    queryFn: async () => (await apiClient.get<Page<DesignationResponse>>('/designations', { params: { size: 200 } })).data.content,
  })
  const officesQuery = useQuery({
    queryKey: ['regional-offices-all'],
    queryFn: async () => (await apiClient.get<Page<RegionalOfficeResponse>>('/regional-offices', { params: { size: 500 } })).data.content,
  })
  const dpcsQuery = useQuery({
    queryKey: ['dpcs-all'],
    queryFn: async () => (await apiClient.get<Page<DpcResponse>>('/dpcs', { params: { size: 500 } })).data.content,
  })
  const availablePostsQuery = useQuery({
    queryKey: ['posts-available-for-assignment', employee.id],
    queryFn: async () =>
      (
        await apiClient.get<Page<PostMasterResponse>>('/v1/posts/available-for-assignment', { params: { employeeId: employee.id, size: 500 } })
      ).data.content,
  })

  const socialProfileQuery = useQuery({
    queryKey: ['employee-social-profile', employee.id],
    queryFn: async () => {
      const res = await apiClient.get<SocialProfileResponse>(`/employees/${employee.id}/social-profile`, {
        validateStatus: (s) => s === 200 || s === 204,
      })
      return res.status === 200 ? res.data : null
    },
  })
  const [socialForm, setSocialForm] = useState<SocialProfileRequest>({
    socialCategory: 'GEN',
    subCasteCommunity: '',
    isPwbd: false,
    disabilityType: '',
    disabilityPercentage: null,
  })
  useEffect(() => {
    if (socialProfileQuery.data) {
      setSocialForm({
        socialCategory: socialProfileQuery.data.socialCategory,
        subCasteCommunity: socialProfileQuery.data.subCasteCommunity ?? '',
        isPwbd: socialProfileQuery.data.isPwbd,
        disabilityType: socialProfileQuery.data.disabilityType ?? '',
        disabilityPercentage: socialProfileQuery.data.disabilityPercentage ?? null,
      })
    }
  }, [socialProfileQuery.data])
  const socialProfileMutation = useMutation({
    mutationFn: async () => apiClient.put(`/employees/${employee.id}/social-profile`, socialForm),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['employee-social-profile', employee.id] }),
  })

  const superannuationPreview = useMemo(
    () => calculateSuperannuation(form.dateOfBirth, form.dateOfJoining, form.designationId, designationsQuery.data ?? []),
    [form.dateOfBirth, form.dateOfJoining, form.designationId, designationsQuery.data],
  )

  const isPlaceholderPhone = PLACEHOLDER_PHONE_PATTERN.test(form.phone)
  const isMissingOfficialEmail = form.officialEmail.trim() === ''

  const { isFetching: isIfscFetching, error: ifscError, setDebouncedIfsc } = useIfscLookup(bankForm.bankIfsc, (bankName, branch) => {
    setBankForm((prev) => ({ ...prev, bankName: bankName ?? prev.bankName, bankBranch: branch ?? prev.bankBranch }))
  })

  const bankFormDirty =
    bankForm.bankName !== initialBankForm.bankName ||
    bankForm.bankBranch !== initialBankForm.bankBranch ||
    bankForm.bankAccountNumber !== initialBankForm.bankAccountNumber ||
    bankForm.bankIfsc !== initialBankForm.bankIfsc

  const updateMutation = useMutation({
    mutationFn: async () => {
      const employeePayload: EmployeeRequest = {
        salutation: form.salutation,
        firstName: form.firstName,
        middleName: form.middleName || null,
        lastName: form.lastName,
        gender: form.gender,
        dateOfBirth: form.dateOfBirth,
        maritalStatus: form.maritalStatus,
        bloodGroup: form.bloodGroup || null,
        nationality: form.nationality,
        motherTongue: form.motherTongue || null,
        panNumber: form.panNumber,
        cpfAcNo: form.cpfAcNo,
        uanNo: form.uanNo || null,
        aadhaarNumber: form.aadhaarNumber || null,
        personalEmail: form.personalEmail,
        officialEmail: form.officialEmail || null,
        phone: form.phone,
        officialMobile: form.officialMobile || null,
        dateOfJoining: form.dateOfJoining,
        departmentId: Number(form.departmentId),
        designationId: Number(form.designationId),
        roId: form.roId ? Number(form.roId) : null,
        dpcId: form.dpcId ? Number(form.dpcId) : null,
        status: form.status,
        geofenceExempted: form.geofenceExempted,
        postId: form.postId ? Number(form.postId) : null,
        isNpsEligible: form.isNpsEligible,
        isEpsEligible: form.isEpsEligible,
        isEpsHigherPensionEligible: form.isEpsEligible ? form.isEpsHigherPensionEligible : false,
        pranNumber: form.pranNumber || null,
      }

      const finalPresent = presentSameAsPermanent ? permanent : present
      const permanentPayload: EmployeeAddressRequest = {
        addressType: 'PERMANENT',
        addressLine1: permanent.addressLine,
        addressLine2: permanent.addressLine2 || null,
        policeStation: permanent.policeStation || null,
        city: permanent.city,
        district: permanent.district,
        state: permanent.state,
        pinCode: permanent.pinCode,
      }
      const presentPayload: EmployeeAddressRequest = {
        addressType: 'PRESENT',
        addressLine1: finalPresent.addressLine,
        addressLine2: finalPresent.addressLine2 || null,
        policeStation: finalPresent.policeStation || null,
        city: finalPresent.city,
        district: finalPresent.district,
        state: finalPresent.state,
        pinCode: finalPresent.pinCode,
      }

      await Promise.all([
        apiClient.put(`/employees/${employee.id}`, employeePayload),
        apiClient.put(`/v1/employees/${employee.id}/addresses`, permanentPayload),
        apiClient.put(`/v1/employees/${employee.id}/addresses`, presentPayload),
        bankFormDirty
          ? apiClient.post(`/v1/employees/${employee.id}/bank-accounts`, {
              bankName: bankForm.bankName,
              bankBranch: bankForm.bankBranch,
              bankAccountNumber: bankForm.bankAccountNumber,
              reenterBankAccountNumber: bankForm.reenterBankAccountNumber,
              bankIfsc: bankForm.bankIfsc,
              cancelledChequeS3Key: bankForm.cancelledChequeS3Key,
            } satisfies EmployeeBankAccountRequest)
          : Promise.resolve(),
      ])
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['employees'] })
      queryClient.invalidateQueries({ queryKey: ['employee', employee.id] })
      queryClient.invalidateQueries({ queryKey: ['employee-360', employee.id] })
      queryClient.invalidateQueries({ queryKey: ['employee-addresses', employee.id] })
      queryClient.invalidateQueries({ queryKey: ['employee-bank-accounts', employee.id] })
      queryClient.invalidateQueries({ queryKey: ['posts-available-for-assignment', employee.id] })
      queryClient.invalidateQueries({ queryKey: ['posts-vacant'] })
      show({ tone: 'success', message: 'Employee updated.' })
      onClose()
    },
  })
  const fieldErrors = getFieldErrors(updateMutation.error)

  const bankMismatch = bankFormDirty && bankForm.bankAccountNumber !== bankForm.reenterBankAccountNumber

  const formValid =
    form.firstName.trim() !== '' && form.lastName.trim() !== '' && form.dateOfBirth !== '' &&
    form.dateOfJoining !== '' && form.panNumber.trim() !== '' && form.cpfAcNo.trim() !== '' && form.personalEmail.trim() !== '' &&
    form.phone.trim() !== '' && form.departmentId !== '' && form.designationId !== '' && !bankMismatch

  return (
    <Modal title={`Edit ${employee.fullName}`} onClose={onClose} maxWidthClassName="max-w-4xl">
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (familyTabDirty) {
            show({ tone: 'error', message: "You have unsaved Family & Nominees changes - click that tab's own \"Save Family & Nominees\" button first." })
            return
          }
          updateMutation.mutate()
        }}
      >
        {updateMutation.isError && (
          <FormErrorBanner title="Could not save" errors={describeApiErrorList(updateMutation.error, 'Could not save.')} />
        )}

        <div className="mb-4 flex flex-wrap gap-2 border-b border-slate-100 pb-3">
          {TABS.map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => setTab(t)}
              className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                tab === t ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'
              }`}
            >
              {t}
            </button>
          ))}
        </div>

        {tab === 'Personal & Bio-Data' && (
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Salutation</label>
              <select
                value={form.salutation}
                onChange={(e) => setForm({ ...form, salutation: e.target.value as Salutation })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="MR">Mr</option>
                <option value="MS">Ms</option>
                <option value="MRS">Mrs</option>
                <option value="DR">Dr</option>
              </select>
            </div>
            <div />
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">First Name</label>
              <input
                required
                value={form.firstName}
                onChange={(e) => setForm({ ...form, firstName: e.target.value })}
                className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.firstName))}`}
              />
              <FieldError message={fieldErrors?.firstName} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Middle Name</label>
              <input
                value={form.middleName}
                onChange={(e) => setForm({ ...form, middleName: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Last Name</label>
              <input
                required
                value={form.lastName}
                onChange={(e) => setForm({ ...form, lastName: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Gender</label>
              <select
                value={form.gender}
                onChange={(e) => setForm({ ...form, gender: e.target.value as Gender })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="MALE">Male</option>
                <option value="FEMALE">Female</option>
                <option value="OTHER">Other</option>
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Marital Status</label>
              <select
                value={form.maritalStatus}
                onChange={(e) => setForm({ ...form, maritalStatus: e.target.value as MaritalStatus })}
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
                value={form.bloodGroup}
                onChange={(e) => setForm({ ...form, bloodGroup: (e.target.value || '') as BloodGroup | '' })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">Unknown</option>
                {(['A_POSITIVE', 'A_NEGATIVE', 'B_POSITIVE', 'B_NEGATIVE', 'AB_POSITIVE', 'AB_NEGATIVE', 'O_POSITIVE', 'O_NEGATIVE'] as const).map((bg) => (
                  <option key={bg} value={bg}>
                    {bg.replace('_', ' ')}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Nationality</label>
              <input
                required
                value={form.nationality}
                onChange={(e) => setForm({ ...form, nationality: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Date of Birth</label>
              <DatePicker value={form.dateOfBirth} onChange={(v) => setForm({ ...form, dateOfBirth: v })} hasError={Boolean(fieldErrors?.dateOfBirth)} />
              <FieldError message={fieldErrors?.dateOfBirth} />
              {superannuationPreview && (
                <Badge tone={superannuationPreview.isDirector ? 'warning' : 'brand'} className="mt-1.5 inline-block">
                  {superannuationPreview.isDirector
                    ? `Tenure / Superannuation: ${formatDate(superannuationPreview.date)} (5-Year Term / Age 60 Cap)`
                    : `Projected Superannuation: ${formatDate(superannuationPreview.date)} (Age 58 Rule)`}
                </Badge>
              )}
            </div>
            <div />

            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">PAN Number</label>
              <input
                required
                maxLength={10}
                value={form.panNumber}
                onChange={(e) => setForm({ ...form, panNumber: e.target.value.toUpperCase() })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm uppercase"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">CPF A/C No.</label>
              <input
                required
                maxLength={20}
                value={form.cpfAcNo}
                onChange={(e) => setForm({ ...form, cpfAcNo: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">UAN (Universal Account Number)</label>
              <input
                value={form.uanNo}
                inputMode="numeric"
                maxLength={12}
                placeholder="e.g. 101234567890"
                onChange={(e) => setForm({ ...form, uanNo: e.target.value.replace(/\D/g, '').slice(0, 12) })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Aadhaar Reference Number</label>
              <input
                value={form.aadhaarNumber}
                inputMode="numeric"
                maxLength={12}
                placeholder={employee.aadhaarRefNumber ?? 'Not on record'}
                onChange={(e) => setForm({ ...form, aadhaarNumber: e.target.value.replace(/\D/g, '').slice(0, 12) })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
              <p className="mt-1 text-[11px] text-slate-400">
                On record: {employee.aadhaarRefNumber ?? 'none'}. Enter a new 12-digit Aadhaar only to replace it.
              </p>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Mother Tongue</label>
              <input
                value={form.motherTongue}
                onChange={(e) => setForm({ ...form, motherTongue: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>

            <div className="col-span-2 mt-2 rounded-md border border-slate-200 p-3">
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Social Category / Reservation</p>
              {socialProfileQuery.isLoading ? (
                <LoadingState label="Loading..." />
              ) : (
                <>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="mb-1 block text-xs font-medium text-slate-600">Social Category</label>
                      <select
                        value={socialForm.socialCategory}
                        onChange={(e) => setSocialForm({ ...socialForm, socialCategory: e.target.value as SocialCategory })}
                        className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                      >
                        {(['GEN', 'SC', 'ST', 'OBC_NCL', 'EWS', 'OTHER'] as SocialCategory[]).map((c) => (
                          <option key={c} value={c}>
                            {c.replace('_', '-')}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-slate-600">Sub-Caste / Community</label>
                      <input
                        value={socialForm.subCasteCommunity ?? ''}
                        onChange={(e) => setSocialForm({ ...socialForm, subCasteCommunity: e.target.value })}
                        className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                      />
                    </div>
                    <label className="flex items-center gap-2 text-sm text-slate-700">
                      <input
                        type="checkbox"
                        checked={socialForm.isPwbd}
                        onChange={(e) => setSocialForm({ ...socialForm, isPwbd: e.target.checked })}
                      />
                      Person with Benchmark Disability (PwBD)
                    </label>
                    <div />
                    {socialForm.isPwbd && (
                      <>
                        <div>
                          <label className="mb-1 block text-xs font-medium text-slate-600">Disability Type</label>
                          <input
                            value={socialForm.disabilityType ?? ''}
                            onChange={(e) => setSocialForm({ ...socialForm, disabilityType: e.target.value })}
                            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                          />
                        </div>
                        <div>
                          <label className="mb-1 block text-xs font-medium text-slate-600">Disability Percentage</label>
                          <input
                            type="number"
                            min="0"
                            max="100"
                            value={socialForm.disabilityPercentage ?? ''}
                            onChange={(e) =>
                              setSocialForm({ ...socialForm, disabilityPercentage: e.target.value === '' ? null : Number(e.target.value) })
                            }
                            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                          />
                        </div>
                      </>
                    )}
                  </div>
                  <div className="mt-2 flex justify-end">
                    <button
                      type="button"
                      disabled={socialProfileMutation.isPending}
                      onClick={() => socialProfileMutation.mutate()}
                      className="rounded-md bg-brand-forest px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
                    >
                      Save Social Profile
                    </button>
                  </div>
                  {socialProfileMutation.isError && (
                    <ErrorState message={describeApiError(socialProfileMutation.error, 'Could not save the social profile.')} />
                  )}
                </>
              )}
            </div>
          </div>
        )}

        {tab === 'Employment & Post' && (
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Employment Type</label>
              <p className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600">
                {employee.employmentCategory ?? 'Not set'}
              </p>
              <p className="mt-1 text-[11px] text-slate-400">Change via the Promotion or Contract/Deployment Renewal workflow, not here.</p>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Sanctioned Post</label>
              <select
                value={form.postId}
                onChange={(e) => {
                  const postId = e.target.value
                  const post = (availablePostsQuery.data ?? []).find((p) => String(p.id) === postId)
                  setForm({
                    ...form,
                    postId,
                    departmentId: post ? String(post.departmentId) : form.departmentId,
                    designationId: post ? String(post.designationId) : form.designationId,
                    roId: post?.roId != null ? String(post.roId) : form.roId,
                  })
                }}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">No post assigned</option>
                {(availablePostsQuery.data ?? []).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.postCode} - {p.title} ({p.departmentName} / {p.designationTitle})
                  </option>
                ))}
              </select>
              <p className="mt-1 text-[11px] text-slate-400">Selecting a post syncs Department/Designation/Regional Office below to match it.</p>
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Department</label>
              <select
                required
                value={form.departmentId}
                onChange={(e) => setForm({ ...form, departmentId: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">Select...</option>
                {(departmentsQuery.data ?? []).map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Designation</label>
              <select
                required
                value={form.designationId}
                onChange={(e) => setForm({ ...form, designationId: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">Select...</option>
                {(designationsQuery.data ?? []).map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.title}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Regional Office</label>
              <select
                value={form.roId}
                onChange={(e) => setForm({ ...form, roId: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">None</option>
                {(officesQuery.data ?? []).map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.code} - {o.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">DPC Unit</label>
              <select
                value={form.dpcId}
                onChange={(e) => setForm({ ...form, dpcId: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">None</option>
                {(dpcsQuery.data ?? []).map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.code} - {d.name}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Date of Joining</label>
              <DatePicker value={form.dateOfJoining} onChange={(v) => setForm({ ...form, dateOfJoining: v })} hasError={Boolean(fieldErrors?.dateOfJoining)} />
              <FieldError message={fieldErrors?.dateOfJoining} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Status</label>
              <select
                value={form.status}
                onChange={(e) => setForm({ ...form, status: e.target.value as EmployeeStatus })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
                <option value="TERMINATED">Terminated</option>
              </select>
            </div>

            <label className="col-span-2 flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={form.geofenceExempted}
                onChange={(e) => setForm({ ...form, geofenceExempted: e.target.checked })}
              />
              Geofence Exempted
            </label>

            <div className="col-span-2">
              <PensionSchemeSection
                value={{
                  isNpsEligible: form.isNpsEligible,
                  isEpsEligible: form.isEpsEligible,
                  isEpsHigherPensionEligible: form.isEpsHigherPensionEligible,
                  pranNumber: form.pranNumber,
                }}
                onChange={(patch) => setForm({ ...form, ...patch })}
              />
            </div>

            <div className="col-span-2">
              <EmployeeVehicleAllotmentSection employeeId={employee.id} />
            </div>
          </div>
        )}

        {tab === 'Contact' && (
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Personal Email</label>
              <input
                required
                type="email"
                value={form.personalEmail}
                onChange={(e) => setForm({ ...form, personalEmail: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Official Email</label>
              <input
                type="email"
                value={form.officialEmail}
                onChange={(e) => setForm({ ...form, officialEmail: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
              {isMissingOfficialEmail && (
                <p className="mt-1 flex items-center gap-1 text-[11px] text-amber-600">
                  <AlertTriangle size={12} /> Not yet assigned - update once the @jcimail.in account is provisioned.
                </p>
              )}
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Phone / Primary Mobile</label>
              <input
                required
                maxLength={10}
                value={form.phone}
                onChange={(e) => setForm({ ...form, phone: e.target.value.replace(/\D/g, '').slice(0, 10) })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
              {isPlaceholderPhone && (
                <p className="mt-1 flex items-center gap-1 text-[11px] text-amber-600">
                  <AlertTriangle size={12} /> Looks like a placeholder number - confirm with the employee and update.
                </p>
              )}
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Official Mobile</label>
              <input
                value={form.officialMobile}
                onChange={(e) => setForm({ ...form, officialMobile: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
          </div>
        )}

        {tab === 'Bank Account' && (
          <div className="grid grid-cols-2 gap-3">
            {!bankFormDirty && primaryBankAccount && (
              <p className="col-span-2 text-xs text-slate-400">
                Showing the current primary account. Changing any field below records it as a new primary account
                (the existing one is kept in history).
              </p>
            )}
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">IFSC Code</label>
              <input
                maxLength={11}
                value={bankForm.bankIfsc}
                onChange={(e) => setBankForm({ ...bankForm, bankIfsc: e.target.value.toUpperCase() })}
                onBlur={() => setDebouncedIfsc(bankForm.bankIfsc)}
                className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm uppercase ${errorInputClass(ifscError !== null)}`}
                placeholder="SBIN0001234"
              />
              {isIfscFetching && <p className="mt-1 text-[11px] text-slate-400">Looking up IFSC...</p>}
              <FieldError message={ifscError} />
            </div>
            <div />
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Bank Name</label>
              <input
                value={bankForm.bankName}
                onChange={(e) => setBankForm({ ...bankForm, bankName: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Branch</label>
              <input
                value={bankForm.bankBranch}
                onChange={(e) => setBankForm({ ...bankForm, bankBranch: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Account Number</label>
              <input
                value={bankForm.bankAccountNumber}
                onChange={(e) => setBankForm({ ...bankForm, bankAccountNumber: e.target.value, reenterBankAccountNumber: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            {bankMismatch && (
              <p className="col-span-2 text-xs text-rose-600">Account number changed inconsistently - re-enter it once, cleanly, in the field above.</p>
            )}
            <div className="col-span-2">
              <DocumentUploadField
                label="Cancelled Cheque / Bank Proof"
                category="BANK_PROOF"
                maxSizeLabel="2MB (PDF/JPG/PNG)"
                acceptHint=".pdf,.jpg,.jpeg,.png"
                currentFileName={bankForm.cancelledChequeS3Key ? bankForm.cancelledChequeS3Key.split('/').pop() : null}
                onUploaded={(res) => setBankForm({ ...bankForm, cancelledChequeS3Key: res.fileS3Key })}
              />
            </div>
          </div>
        )}

        {tab === 'Address' && (
          <div className="space-y-4">
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Permanent Address</p>
              <PincodeAddressFields value={permanent} onChange={(patch) => setPermanent((prev) => ({ ...prev, ...patch }))} />
            </div>

            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" checked={presentSameAsPermanent} onChange={(e) => setPresentSameAsPermanent(e.target.checked)} />
              Present address same as permanent
            </label>

            {!presentSameAsPermanent && (
              <div>
                <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Present Address</p>
                <PincodeAddressFields value={present} onChange={(patch) => setPresent((prev) => ({ ...prev, ...patch }))} />
              </div>
            )}
          </div>
        )}

        {tab === 'Qualifications' && <EmployeeQualificationsPanel employeeId={employee.id} />}
        {tab === 'Past Service' && <EmployeePastServicePanel employeeId={employee.id} />}
        {tab === 'Family & Nominees' && <EmployeeFamilyNomineesPanel employeeId={employee.id} onDirtyChange={setFamilyTabDirty} />}
        {tab === 'Company Accommodation' && <EmployeeQuarterAllotmentsPanel employeeId={employee.id} />}

        {familyTabDirty && (
          <p className="mt-3 flex items-center gap-1.5 text-xs font-medium text-amber-600">
            <AlertTriangle size={13} /> Unsaved Family &amp; Nominees changes - use that tab's own "Save Family &amp; Nominees" button before saving here.
          </p>
        )}
        <PrimaryButton
          type="submit"
          disabled={updateMutation.isPending || !formValid || familyTabDirty}
          className="mt-2 w-full justify-center"
        >
          Save Changes
        </PrimaryButton>
      </form>
    </Modal>
  )
}
