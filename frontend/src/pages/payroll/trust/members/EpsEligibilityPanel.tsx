import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, HelpCircle, XCircle } from 'lucide-react'
import { apiClient } from '../../../../api/client'
import { formatDate } from '../../../../lib/date'
import { describeApiError } from '../../../../lib/apiError'
import { Badge, ErrorState, LoadingState } from '../../../../components/common/ui'
import type { CpfEpsEligibilityResponse, CpfEpsEligibilityStatus } from '../../../../types/api'

const ELIGIBILITY_TONE: Record<CpfEpsEligibilityStatus, 'success' | 'danger' | 'warning'> = {
  ELIGIBLE: 'success',
  NOT_ELIGIBLE: 'danger',
  REVIEW_REQUIRED: 'warning',
}
const ELIGIBILITY_LABEL: Record<CpfEpsEligibilityStatus, string> = {
  ELIGIBLE: 'Eligible',
  NOT_ELIGIBLE: 'Not Eligible',
  REVIEW_REQUIRED: 'Review Required',
}
const ELIGIBILITY_ICON: Record<CpfEpsEligibilityStatus, typeof CheckCircle2> = {
  ELIGIBLE: CheckCircle2,
  NOT_ELIGIBLE: XCircle,
  REVIEW_REQUIRED: HelpCircle,
}

/** Section 16 - "Check EPS Pension Eligibility". A placeholder-rule check (see the backend CpfEpsEligibilityService's own javadoc), not a confirmed legal determination - the basis line always says so explicitly rather than presenting the result as authoritative. Presentational-only (no Modal chrome) so it can be reused both standalone (EpsEligibilityDialog) and embedded in the Member 360 drawer's EPS Eligibility tab. */
export function EpsEligibilityPanel({ employeeId }: { employeeId: number }) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['cpf-eps-eligibility', employeeId],
    queryFn: async () => (await apiClient.get<CpfEpsEligibilityResponse>(`/v1/payroll/trust/members/${employeeId}/eps-eligibility`)).data,
  })

  if (isLoading) return <LoadingState label="Checking EPS pension eligibility..." />
  if (isError) return <ErrorState message={describeApiError(error, 'Could not check EPS pension eligibility.')} />
  if (!data) return null

  const Icon = ELIGIBILITY_ICON[data.eligibility]

  return (
    <div className="space-y-4">
      <div className={`flex items-start gap-3 rounded-md border p-3 ${
        data.eligibility === 'ELIGIBLE' ? 'border-emerald-200 bg-emerald-50' : data.eligibility === 'NOT_ELIGIBLE' ? 'border-red-200 bg-red-50' : 'border-amber-200 bg-amber-50'
      }`}>
        <Icon size={20} className={data.eligibility === 'ELIGIBLE' ? 'text-emerald-600' : data.eligibility === 'NOT_ELIGIBLE' ? 'text-red-600' : 'text-amber-600'} />
        <div>
          <p className="flex items-center gap-2 font-semibold text-slate-800">
            Eligibility: <Badge tone={ELIGIBILITY_TONE[data.eligibility]}>{ELIGIBILITY_LABEL[data.eligibility]}</Badge>
          </p>
          <p className="mt-1 text-sm text-slate-600">{data.eligibilityBasis}</p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-sm">
        <Row label="Member Name" value={data.fullName} />
        <Row label="CPF A/C No." value={data.cpfAcNo} />
        <Row label="UAN" value={data.uanNo ?? 'Missing'} />
        <Row label="Date of Birth" value={formatDate(data.dateOfBirth)} />
        <Row label="Date of Joining" value={formatDate(data.dateOfJoining)} />
        <Row label="Date of Separation" value={data.dateOfSeparation ? formatDate(data.dateOfSeparation) : 'Still in service'} />
        <Row label="EPS Membership" value={data.epsMember ? 'Enrolled' : 'Not Enrolled'} />
        <Row label="Eligible Service" value={data.eligibleServiceLabel} />
        <Row label="Pensionable Service" value={data.pensionableServiceLabel} />
      </div>

      <div className="flex items-start gap-2 rounded-md bg-slate-50 p-2.5 text-xs text-slate-500">
        <AlertTriangle size={13} className="mt-0.5 shrink-0" />
        Configured placeholder rule (minimum pensionable service threshold), not a confirmed JCI CPF Trust/EPS
        scheme determination - verify against the Trust's own rules before acting on this.
      </div>
    </div>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-2 border-b border-slate-50 py-1">
      <span className="text-slate-400">{label}</span>
      <span className="text-right font-medium text-slate-700">{value}</span>
    </div>
  )
}
