import { useQuery } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { apiClient } from '../../../../api/client'
import { DatePicker } from '../../../../components/common/DatePicker'
import { SecondaryButton } from '../../../../components/common/ui'
import type {
  DepartmentResponse,
  DesignationResponse,
  EmploymentCategory,
  GradeScaleMasterResponse,
  IncrementCycle,
  Page,
  PostMasterResponse,
  RecruitmentMode,
  SalaryBreakdownHeadType,
  VendorMasterResponse,
} from '../../../../types/api'
import { emptyCtcItem, type LocalEmployment } from '../onboardingTypes'
import { PensionSchemeSection } from '../../../../components/employee/PensionSchemeSection'

const RECRUITMENT_MODES: RecruitmentMode[] = ['DIRECT_RECRUITMENT', 'PROMOTION', 'DEPUTATION', 'COMPASSIONATE', 'ABSORPTION']
const HEAD_TYPES: SalaryBreakdownHeadType[] = ['EARNING', 'DEDUCTION', 'EMPLOYER_STATUTORY', 'VENDOR_FEE']

/** PIMS_SPEC.md Onboarding Step 6: 4-Tier Employment Category & Post Assignment. */
export function Step6Employment({ value, onChange }: { value: LocalEmployment; onChange: (next: LocalEmployment) => void }) {
  const departmentsQuery = useQuery({
    queryKey: ['departments-all'],
    queryFn: async () => (await apiClient.get<Page<DepartmentResponse>>('/departments', { params: { size: 200 } })).data.content,
  })
  const designationsQuery = useQuery({
    queryKey: ['designations-all'],
    queryFn: async () => (await apiClient.get<Page<DesignationResponse>>('/designations', { params: { size: 200 } })).data.content,
  })
  const vacantPostsQuery = useQuery({
    queryKey: ['posts-vacant'],
    queryFn: async () => (await apiClient.get<Page<PostMasterResponse>>('/v1/posts/vacant', { params: { size: 500 } })).data.content,
    enabled: value.employmentCategory === 'REGULAR',
  })
  const vendorsQuery = useQuery({
    queryKey: ['vendors-all'],
    queryFn: async () => (await apiClient.get<Page<VendorMasterResponse>>('/v1/admin/masters/vendors', { params: { size: 200 } })).data.content,
    enabled: value.employmentCategory === 'OUTSOURCED',
  })
  const gradeScalesQuery = useQuery({
    queryKey: ['grade-scales'],
    queryFn: async () => (await apiClient.get<GradeScaleMasterResponse[]>('/v1/masters/grade-scales')).data,
    enabled: value.employmentCategory !== 'CASUAL',
  })
  const selectedScale = (gradeScalesQuery.data ?? []).find((s) => s.scaleCode === value.scaleCode) ?? null

  /** Selecting a grade scale pre-fills its Basic Pay / Contractual Lumpsum / Outsourced CTC - always overridable afterwards, never re-applied on a later change. */
  function selectScale(scaleCode: string) {
    const scale = (gradeScalesQuery.data ?? []).find((s) => s.scaleCode === scaleCode) ?? null
    const patch: Partial<LocalEmployment> = { scaleCode }
    if (scale) {
      if (value.employmentCategory === 'REGULAR') patch.regularBasicPay = String(scale.minimumBasic)
      if (value.employmentCategory === 'CONTRACTUAL' && scale.contractualLumpsum != null) patch.fixedLumpSumMonthly = String(scale.contractualLumpsum)
      if (value.employmentCategory === 'OUTSOURCED' && scale.outsourcedCtc != null) patch.monthlyCtc = String(scale.outsourcedCtc)
    }
    onChange({ ...value, ...patch })
  }

  function updateCtcRow(key: string, patch: Partial<(typeof value.ctcBreakdown)[number]>) {
    onChange({ ...value, ctcBreakdown: value.ctcBreakdown.map((row) => (row.key === key ? { ...row, ...patch } : row)) })
  }
  function removeCtcRow(key: string) {
    onChange({ ...value, ctcBreakdown: value.ctcBreakdown.filter((row) => row.key !== key) })
  }

  return (
    <div className="space-y-5">
      <div>
        <label className="mb-1 block text-xs font-medium text-slate-600">Employment Category</label>
        <div className="flex flex-wrap gap-2">
          {(['REGULAR', 'CASUAL', 'CONTRACTUAL', 'OUTSOURCED'] as EmploymentCategory[]).map((cat) => (
            <button
              key={cat}
              type="button"
              onClick={() => onChange({ ...value, employmentCategory: cat })}
              className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                value.employmentCategory === cat ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'
              }`}
            >
              {cat}
            </button>
          ))}
        </div>
      </div>

      {value.employmentCategory === 'REGULAR' && (
        <div className="grid grid-cols-2 gap-3">
          <div className="col-span-2">
            <label className="mb-1 block text-xs font-medium text-slate-600">Vacant Sanctioned Post</label>
            <select
              required
              value={value.postId}
              onChange={(e) => onChange({ ...value, postId: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="">Select a vacant, budgeted post...</option>
              {(vacantPostsQuery.data ?? []).map((p) => (
                <option key={p.id} value={p.id}>
                  {p.postCode} - {p.title} ({p.departmentName} / {p.designationTitle})
                </option>
              ))}
            </select>
          </div>
          <div className="col-span-2">
            <label className="mb-1 block text-xs font-medium text-slate-600">Grade Scale</label>
            <select
              required
              value={value.scaleCode}
              onChange={(e) => selectScale(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="">Select...</option>
              {(gradeScalesQuery.data ?? []).map((s) => (
                <option key={s.scaleCode} value={s.scaleCode}>
                  {s.scaleCode} - {s.cadre}
                </option>
              ))}
            </select>
            {selectedScale && (
              <p className="mt-1 text-[11px] text-slate-400">IDA Scale range: {selectedScale.idaScaleLabel}</p>
            )}
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Basic Pay</label>
            <input
              required
              type="number"
              step="0.01"
              value={value.regularBasicPay}
              onChange={(e) => onChange({ ...value, regularBasicPay: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            {selectedScale && value.regularBasicPay !== '' &&
              (Number(value.regularBasicPay) < selectedScale.minimumBasic || Number(value.regularBasicPay) > selectedScale.maximumBasic) && (
                <p className="mt-1 text-xs text-red-600">
                  Must be between ₹{selectedScale.minimumBasic.toLocaleString('en-IN')} and ₹{selectedScale.maximumBasic.toLocaleString('en-IN')} for {selectedScale.scaleCode}.
                </p>
              )}
            {selectedScale && (
              <p className="mt-1 text-[11px] text-slate-400">Annual increment: {selectedScale.incrementRate.toFixed(2)}% (per {selectedScale.scaleCode}).</p>
            )}
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Increment Cycle</label>
            <select
              value={value.incrementCycle}
              onChange={(e) => onChange({ ...value, incrementCycle: e.target.value as IncrementCycle })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="JULY">July</option>
              <option value="JANUARY">January</option>
            </select>
          </div>
        </div>
      )}

      {value.employmentCategory !== 'REGULAR' && (
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Department</label>
            <select
              required
              value={value.departmentId}
              onChange={(e) => onChange({ ...value, departmentId: e.target.value })}
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
              value={value.designationId}
              onChange={(e) => onChange({ ...value, designationId: e.target.value })}
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
        </div>
      )}

      {value.employmentCategory === 'CASUAL' && (
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Daily Wage Rate</label>
            <input
              required
              type="number"
              step="0.01"
              value={value.dailyWageRate}
              onChange={(e) => onChange({ ...value, dailyWageRate: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Wage Revision Order No.</label>
            <input
              value={value.wageRevisionOrderNo}
              onChange={(e) => onChange({ ...value, wageRevisionOrderNo: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
        </div>
      )}

      {value.employmentCategory === 'CONTRACTUAL' && (
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Benchmark Scale</label>
            <select
              value={value.scaleCode}
              onChange={(e) => selectScale(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="">Select...</option>
              {(gradeScalesQuery.data ?? []).map((s) => (
                <option key={s.scaleCode} value={s.scaleCode}>
                  {s.scaleCode} - {s.cadre}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Monthly Consolidated Lumpsum</label>
            <input
              required
              type="number"
              step="0.01"
              value={value.fixedLumpSumMonthly}
              onChange={(e) => onChange({ ...value, fixedLumpSumMonthly: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            {selectedScale?.contractualLumpsum != null && (
              <p className="mt-1 text-[11px] text-slate-400">
                Base lumpsum for {selectedScale.scaleCode}: ₹{selectedScale.contractualLumpsum.toLocaleString('en-IN')} (overridable).
              </p>
            )}
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Contract Ref Order</label>
            <input
              value={value.contractRefOrder}
              onChange={(e) => onChange({ ...value, contractRefOrder: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Contract Start</label>
            <DatePicker value={value.contractStartDate} onChange={(v) => onChange({ ...value, contractStartDate: v })} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Contract End</label>
            <DatePicker value={value.contractEndDate} onChange={(v) => onChange({ ...value, contractEndDate: v })} />
          </div>
        </div>
      )}

      {value.employmentCategory === 'OUTSOURCED' && (
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Benchmark Scale</label>
              <select
                value={value.scaleCode}
                onChange={(e) => selectScale(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">Select...</option>
                {(gradeScalesQuery.data ?? []).map((s) => (
                  <option key={s.scaleCode} value={s.scaleCode}>
                    {s.scaleCode} - {s.cadre}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Vendor</label>
              <select
                required
                value={value.vendorId}
                onChange={(e) => onChange({ ...value, vendorId: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">Select...</option>
                {(vendorsQuery.data ?? []).map((v) => (
                  <option key={v.id} value={v.id}>
                    {v.vendorCode} - {v.vendorName}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Agency Employee ID</label>
              <input
                value={value.agencyEmployeeId}
                onChange={(e) => onChange({ ...value, agencyEmployeeId: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Outsourced Monthly CTC</label>
              <input
                required
                type="number"
                step="0.01"
                value={value.monthlyCtc}
                onChange={(e) => onChange({ ...value, monthlyCtc: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
              {selectedScale?.outsourcedCtc != null && (
                <p className="mt-1 text-[11px] text-slate-400">
                  Base CTC for {selectedScale.scaleCode}: ₹{selectedScale.outsourcedCtc.toLocaleString('en-IN')} (overridable).
                </p>
              )}
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Billing Rate (Monthly)</label>
              <input
                type="number"
                step="0.01"
                value={value.billingRateMonthly}
                onChange={(e) => onChange({ ...value, billingRateMonthly: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Contract Start</label>
              <DatePicker value={value.contractStartDate} onChange={(v) => onChange({ ...value, contractStartDate: v })} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Contract End</label>
              <DatePicker value={value.contractEndDate} onChange={(v) => onChange({ ...value, contractEndDate: v })} />
            </div>
          </div>

          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">CTC Breakdown</p>
            <div className="space-y-2">
              {value.ctcBreakdown.map((row) => (
                <div key={row.key} className="grid grid-cols-5 gap-2">
                  <input
                    placeholder="Head Code"
                    value={row.headCode}
                    onChange={(e) => updateCtcRow(row.key, { headCode: e.target.value })}
                    className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                  />
                  <input
                    placeholder="Head Name"
                    value={row.headName}
                    onChange={(e) => updateCtcRow(row.key, { headName: e.target.value })}
                    className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                  />
                  <select
                    value={row.headType}
                    onChange={(e) => updateCtcRow(row.key, { headType: e.target.value as SalaryBreakdownHeadType })}
                    className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                  >
                    {HEAD_TYPES.map((h) => (
                      <option key={h} value={h}>
                        {h}
                      </option>
                    ))}
                  </select>
                  <input
                    type="number"
                    step="0.01"
                    placeholder="Amount"
                    value={row.amount}
                    onChange={(e) => updateCtcRow(row.key, { amount: e.target.value })}
                    className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                  />
                  <button type="button" onClick={() => removeCtcRow(row.key)} className="rounded-md p-1.5 text-red-500 hover:bg-red-50">
                    <Trash2 size={14} />
                  </button>
                </div>
              ))}
            </div>
            <SecondaryButton onClick={() => onChange({ ...value, ctcBreakdown: [...value.ctcBreakdown, emptyCtcItem()] })} className="mt-2">
              <Plus size={14} /> Add CTC Head
            </SecondaryButton>
          </div>
        </div>
      )}

      <PensionSchemeSection
        value={{
          isNpsEligible: value.isNpsEligible,
          isEpsEligible: value.isEpsEligible,
          isEpsHigherPensionEligible: value.isEpsHigherPensionEligible,
          pranNumber: value.pranNumber,
        }}
        onChange={(patch) => onChange({ ...value, ...patch })}
      />

      <div className="rounded-md border border-slate-200 p-3">
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Recruitment Metadata</p>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Date of Joining (PSU)</label>
            <DatePicker value={value.dateOfJoiningPsu} onChange={(v) => onChange({ ...value, dateOfJoiningPsu: v })} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Recruitment Year</label>
            <input
              required
              type="number"
              min="1950"
              max="2100"
              value={value.recruitmentYear}
              onChange={(e) => onChange({ ...value, recruitmentYear: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Recruitment Mode</label>
            <select
              value={value.recruitmentMode}
              onChange={(e) => onChange({ ...value, recruitmentMode: e.target.value as RecruitmentMode })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              {RECRUITMENT_MODES.map((m) => (
                <option key={m} value={m}>
                  {m.replace(/_/g, ' ')}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Selection Method</label>
            <input
              required
              value={value.selectionMethod}
              onChange={(e) => onChange({ ...value, selectionMethod: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Recruitment Agency</label>
            <input
              value={value.recruitmentAgency}
              onChange={(e) => onChange({ ...value, recruitmentAgency: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Advertisement No.</label>
            <input
              value={value.advertisementNo}
              onChange={(e) => onChange({ ...value, advertisementNo: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Appointment Letter No.</label>
            <input
              required
              value={value.appointmentLetterNo}
              onChange={(e) => onChange({ ...value, appointmentLetterNo: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Appointment Letter Date</label>
            <DatePicker value={value.appointmentLetterDate} onChange={(v) => onChange({ ...value, appointmentLetterDate: v })} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Offer Letter Date</label>
            <DatePicker value={value.offerLetterDate} onChange={(v) => onChange({ ...value, offerLetterDate: v })} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Joining Letter Date</label>
            <DatePicker value={value.joiningLetterDate} onChange={(v) => onChange({ ...value, joiningLetterDate: v })} />
          </div>
        </div>
      </div>
    </div>
  )
}
