import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Download, Printer } from 'lucide-react'
import { apiClient } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/common/ToastProvider'
import { describeApiError } from '../../lib/apiError'
import { formatDate } from '../../lib/date'
import { DatePicker } from '../../components/common/DatePicker'
import { Modal } from '../../components/common/Modal'
import {
  Badge,
  Card,
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  PrimaryButton,
  SecondaryButton,
} from '../../components/common/ui'
import type {
  CeaClaimBillPassRequest,
  CeaClaimResponse,
  CeaClaimStatus,
  CeaClaimSubmitRequest,
  CeaClaimType,
  CeaClaimVerifyRequest,
  CeaEligibilityStatus,
  EmployeeFamilyNomineeCompositeResponse,
} from '../../types/api'

function formatInr(value: number | null): string {
  return value != null ? `₹${value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '—'
}

const CLAIM_TYPE_LABELS: Record<CeaClaimType, string> = {
  CEA: 'Children Education Allowance (CEA)',
  HOSTEL_SUBSIDY: 'Hostel Subsidy',
}

const ELIGIBILITY_BADGE: Record<CeaEligibilityStatus, { label: string; className: string }> = {
  ELIGIBLE_STANDARD: { label: 'ELIGIBLE (STANDARD)', className: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  ELIGIBLE_DIVYANG: { label: 'ELIGIBLE (DIVYANG)', className: 'bg-blue-50 text-blue-700 border-blue-200' },
  INELIGIBLE_OVERAGE: { label: 'INELIGIBLE (OVERAGE)', className: 'bg-rose-50 text-rose-700 border-rose-200' },
}

function EligibilityBadge({ status }: { status: CeaEligibilityStatus | null }) {
  if (status === null) return <span className="text-xs text-slate-400">—</span>
  const { label, className } = ELIGIBILITY_BADGE[status]
  return <span className={`inline-block whitespace-nowrap rounded-full border px-2 py-0.5 text-[10px] font-semibold ${className}`}>{label}</span>
}

function STATUS_TONE(status: CeaClaimStatus): 'neutral' | 'success' | 'warning' | 'danger' | 'brand' {
  switch (status) {
    case 'DISBURSED':
      return 'success'
    case 'BILL_PASSED':
      return 'brand'
    case 'VERIFIED':
      return 'warning'
    case 'REJECTED':
      return 'danger'
    default:
      return 'neutral'
  }
}

function currentAcademicYear(): string {
  const now = new Date()
  const startYear = now.getMonth() >= 3 ? now.getFullYear() : now.getFullYear() - 1 // April onward = new academic year
  return `${startYear}-${startYear + 1}`
}

interface ClaimFormState {
  claimNo: string
  dependentId: string
  academicYear: string
  claimType: CeaClaimType
  schoolName: string
  schoolRegNo: string
  standardClass: string
  periodFrom: string
  periodTo: string
  claimedAmount: string
  supportingDocRef: string
}

function emptyForm(): ClaimFormState {
  const ay = currentAcademicYear()
  const startYear = Number(ay.split('-')[0])
  return {
    claimNo: '',
    dependentId: '',
    academicYear: ay,
    claimType: 'CEA',
    schoolName: '',
    schoolRegNo: '',
    standardClass: '',
    periodFrom: `${startYear}-04-01`,
    periodTo: `${startYear + 1}-03-31`,
    claimedAmount: '',
    supportingDocRef: '',
  }
}

/** HR verify (adjust admissible amount) or Finance pass-bill or either-gate reject - one shared modal shape, keyed by `mode`. */
type ReviewAction =
  | { mode: 'verify'; claim: CeaClaimResponse }
  | { mode: 'passBill'; claim: CeaClaimResponse }
  | { mode: 'reject'; claim: CeaClaimResponse }

function VerifyModal({ claim, onClose, onConfirm, isPending }: { claim: CeaClaimResponse; onClose: () => void; onConfirm: (amount: string) => void; isPending: boolean }) {
  const [amount, setAmount] = useState(String(claim.admissibleAmount))
  return (
    <Modal title={`Verify Claim ${claim.claimNo}`} onClose={onClose} maxWidthClassName="max-w-md">
      <p className="mb-3 text-xs text-slate-500">
        {claim.employeeCode} · {claim.fullName} · Dependent: {claim.dependentName}
      </p>
      <label className="mb-1 block text-xs font-medium text-slate-600">Admissible Amount</label>
      <input
        type="number"
        min={0}
        step={0.01}
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
      />
      <p className="mt-1 text-xs text-slate-400">Claimed: {formatInr(claim.claimedAmount)}. Leave unchanged to keep the amount computed at submission.</p>
      <div className="mt-4 flex justify-end gap-2">
        <SecondaryButton type="button" onClick={onClose}>
          Cancel
        </SecondaryButton>
        <PrimaryButton type="button" disabled={amount.trim() === '' || Number(amount) < 0 || isPending} onClick={() => onConfirm(amount)}>
          Confirm Verification
        </PrimaryButton>
      </div>
    </Modal>
  )
}

function PassBillModal({
  claim,
  onClose,
  onConfirm,
  isPending,
}: {
  claim: CeaClaimResponse
  onClose: () => void
  onConfirm: (data: CeaClaimBillPassRequest) => void
  isPending: boolean
}) {
  const today = new Date().toISOString().slice(0, 10)
  const [passedAmount, setPassedAmount] = useState(String(claim.admissibleAmount))
  const [billNo, setBillNo] = useState('')
  const [billDate, setBillDate] = useState(today)
  const [sanctionOrderNo, setSanctionOrderNo] = useState('')
  const [sanctionDate, setSanctionDate] = useState(today)

  const valid =
    passedAmount.trim() !== '' && Number(passedAmount) > 0 && billNo.trim() !== '' && billDate !== '' && sanctionOrderNo.trim() !== '' && sanctionDate !== ''

  return (
    <Modal title={`Pass Bill — Claim ${claim.claimNo}`} onClose={onClose} maxWidthClassName="max-w-lg">
      <p className="mb-3 text-xs text-slate-500">
        {claim.employeeCode} · {claim.fullName} · Dependent: {claim.dependentName} · Verified Admissible: {formatInr(claim.admissibleAmount)}
      </p>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Passed Amount</label>
          <input
            type="number"
            min={0.01}
            step={0.01}
            value={passedAmount}
            onChange={(e) => setPassedAmount(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Bill No.</label>
          <input value={billNo} onChange={(e) => setBillNo(e.target.value)} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Bill Date</label>
          <DatePicker value={billDate} onChange={setBillDate} />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Sanction Order No.</label>
          <input
            value={sanctionOrderNo}
            onChange={(e) => setSanctionOrderNo(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Sanction Date</label>
          <DatePicker value={sanctionDate} onChange={setSanctionDate} />
        </div>
      </div>
      <div className="mt-4 flex justify-end gap-2">
        <SecondaryButton type="button" onClick={onClose}>
          Cancel
        </SecondaryButton>
        <PrimaryButton
          type="button"
          disabled={!valid || isPending}
          onClick={() => onConfirm({ passedAmount: Number(passedAmount), billNo, billDate, sanctionOrderNo, sanctionDate })}
        >
          Confirm Bill Passing
        </PrimaryButton>
      </div>
    </Modal>
  )
}

function RejectModal({ claim, onClose, onConfirm, isPending }: { claim: CeaClaimResponse; onClose: () => void; onConfirm: (reason: string) => void; isPending: boolean }) {
  const [reason, setReason] = useState('')
  return (
    <Modal title={`Reject Claim ${claim.claimNo}`} onClose={onClose} maxWidthClassName="max-w-md">
      <label className="mb-1 block text-xs font-medium text-slate-600">Reason (required)</label>
      <textarea
        required
        autoFocus
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        rows={3}
        className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        placeholder="Reason for rejecting this claim..."
      />
      <div className="mt-4 flex justify-end gap-2">
        <SecondaryButton type="button" onClick={onClose}>
          Cancel
        </SecondaryButton>
        <PrimaryButton type="button" disabled={reason.trim() === '' || isPending} onClick={() => onConfirm(reason.trim())}>
          Confirm Rejection
        </PrimaryButton>
      </div>
    </Modal>
  )
}

function ClaimReviewRow({ claim, onVerify, onPassBill, onReject, actionPending }: {
  claim: CeaClaimResponse
  onVerify?: () => void
  onPassBill?: () => void
  onReject: () => void
  actionPending: boolean
}) {
  return (
    <div className="mb-2 rounded-md border border-slate-200 p-3 text-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-medium text-slate-800">
            {claim.employeeCode} · {claim.fullName}
            <span className="font-normal text-slate-500"> · Dependent: {claim.dependentName}</span>
          </p>
          <p className="mt-0.5 text-xs text-slate-500">
            Claim #{claim.claimNo} · {CLAIM_TYPE_LABELS[claim.claimType]} · AY {claim.academicYear} · {claim.schoolName} ({claim.standardClass})
          </p>
          <p className="mt-0.5 text-xs text-slate-500">
            Period: {formatDate(claim.periodFrom)} – {formatDate(claim.periodTo)} · Claimed: {formatInr(claim.claimedAmount)} · Admissible:{' '}
            {formatInr(claim.admissibleAmount)}
          </p>
        </div>
        <div className="flex shrink-0 flex-wrap gap-2">
          <SecondaryButton onClick={onReject} disabled={actionPending}>
            Reject
          </SecondaryButton>
          {onVerify && (
            <PrimaryButton onClick={onVerify} disabled={actionPending}>
              Verify
            </PrimaryButton>
          )}
          {onPassBill && (
            <PrimaryButton onClick={onPassBill} disabled={actionPending}>
              Pass Bill
            </PrimaryButton>
          )}
        </div>
      </div>
    </div>
  )
}

function csvCell(value: string | number | null): string {
  const text = value == null ? '' : String(value)
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

function exportHistoryToCsv(rows: CeaClaimResponse[]) {
  const header = [
    'Claim No', 'Employee Code', 'Employee Name', 'Dependent', 'Academic Year', 'Type', 'School', 'Claimed Amount',
    'Admissible Amount', 'Passed Amount', 'Status', 'Verified At', 'Bill No', 'Passed At', 'Rejection Reason',
  ]
  const lines = rows.map((r) =>
    [
      r.claimNo, r.employeeCode, r.fullName, r.dependentName, r.academicYear, CLAIM_TYPE_LABELS[r.claimType], r.schoolName,
      r.claimedAmount, r.admissibleAmount, r.passedAmount, r.claimStatus, r.verifiedAt ? formatDate(r.verifiedAt) : '',
      r.billNo, r.passedAt ? formatDate(r.passedAt) : '', r.rejectionReason,
    ]
      .map(csvCell)
      .join(','),
  )
  const csv = [header.join(','), ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'cea-claims-history.csv'
  link.click()
  URL.revokeObjectURL(url)
}

/** HR/Finance officers' full audit view - every claim across every employee, regardless of status. */
function CeaHistoryTab() {
  const [statusFilter, setStatusFilter] = useState<'ALL' | CeaClaimStatus>('ALL')

  const historyQuery = useQuery({
    queryKey: ['cea-claims-history'],
    queryFn: async () => (await apiClient.get<CeaClaimResponse[]>('/v1/admin/cea-claims')).data,
  })

  const rows = (historyQuery.data ?? []).filter((r) => statusFilter === 'ALL' || r.claimStatus === statusFilter)
  const statusOptions: (CeaClaimStatus | 'ALL')[] = ['ALL', 'SUBMITTED', 'VERIFIED', 'BILL_PASSED', 'DISBURSED', 'REJECTED']

  return (
    <Card>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-slate-700">History / Log — All CEA &amp; Hostel Subsidy Claims</h2>
        <div className="flex flex-wrap items-center gap-2">
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value as 'ALL' | CeaClaimStatus)}
            className="rounded-md border border-slate-300 px-2 py-1.5 text-xs"
          >
            {statusOptions.map((s) => (
              <option key={s} value={s}>
                {s === 'ALL' ? 'All Statuses' : s}
              </option>
            ))}
          </select>
          <SecondaryButton onClick={() => exportHistoryToCsv(rows)} disabled={rows.length === 0}>
            <Download size={14} /> Export to Excel
          </SecondaryButton>
          <SecondaryButton onClick={() => window.print()} disabled={rows.length === 0}>
            <Printer size={14} /> Print
          </SecondaryButton>
        </div>
      </div>

      {historyQuery.isLoading && <LoadingState label="Loading claim history..." />}
      {historyQuery.isError && <ErrorState message="Could not load CEA claim history." />}
      {historyQuery.isSuccess && rows.length === 0 && <EmptyState message="No claims for this filter." />}

      {rows.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[1200px] border-collapse text-xs">
            <thead>
              <tr className="border-b border-slate-300 text-left text-slate-500">
                <th className="py-2 pr-3">Claim No</th>
                <th className="py-2 pr-3">Employee</th>
                <th className="py-2 pr-3">Dependent</th>
                <th className="py-2 pr-3">Type / AY</th>
                <th className="py-2 pr-3 text-right">Claimed</th>
                <th className="py-2 pr-3 text-right">Admissible</th>
                <th className="py-2 pr-3 text-right">Passed</th>
                <th className="py-2 pr-3">Status</th>
                <th className="py-2 pl-3">Notes</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id} className="border-b border-slate-100">
                  <td className="py-1.5 pr-3 font-medium tabular-nums">{r.claimNo}</td>
                  <td className="py-1.5 pr-3">
                    {r.employeeCode} · {r.fullName}
                  </td>
                  <td className="py-1.5 pr-3">{r.dependentName}</td>
                  <td className="py-1.5 pr-3">
                    {CLAIM_TYPE_LABELS[r.claimType]}
                    <span className="block text-slate-400">{r.academicYear}</span>
                  </td>
                  <td className="py-1.5 pr-3 text-right tabular-nums">{formatInr(r.claimedAmount)}</td>
                  <td className="py-1.5 pr-3 text-right tabular-nums">{formatInr(r.admissibleAmount)}</td>
                  <td className="py-1.5 pr-3 text-right tabular-nums">{formatInr(r.passedAmount)}</td>
                  <td className="py-1.5 pr-3">
                    <Badge tone={STATUS_TONE(r.claimStatus)}>{r.claimStatus}</Badge>
                  </td>
                  <td className="py-1.5 pl-3 text-slate-500">{r.claimStatus === 'REJECTED' ? r.rejectionReason : r.billNo ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}

type CeaTab = 'claims' | 'history'

/** Children Education Allowance / Hostel Subsidy - employee submission, HR verification (Gate 1), Finance bill-passing (Gate 2), and an officer History/Log view. */
export function CeaClaimsPage() {
  const { employeeId, hasRole } = useAuth()
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<ClaimFormState>(emptyForm())
  const [tab, setTab] = useState<CeaTab>('claims')
  const [reviewTarget, setReviewTarget] = useState<ReviewAction | null>(null)

  const isHrAdmin = hasRole('HR_ADMIN', 'SUPER_ADMIN')
  const isFinanceAdmin = hasRole('FINANCE_ADMIN', 'SUPER_ADMIN')

  const familyQuery = useQuery({
    queryKey: ['employee-family-nominees', employeeId],
    queryFn: async () => (await apiClient.get<EmployeeFamilyNomineeCompositeResponse>(`/employees/${employeeId}/family-nominees`)).data,
    enabled: employeeId !== null,
  })
  const eligibleDependents = (familyQuery.data?.dependents ?? []).filter((d) => d.relationship === 'SON' || d.relationship === 'DAUGHTER')
  const selectedDependent = eligibleDependents.find((d) => String(d.id) === form.dependentId) ?? null

  const myClaimsQuery = useQuery({
    queryKey: ['cea-claims-mine'],
    queryFn: async () => (await apiClient.get<CeaClaimResponse[]>('/v1/self-service/cea-claims/my-claims')).data,
  })

  const submitMutation = useMutation({
    mutationFn: async () => {
      const payload: CeaClaimSubmitRequest = {
        claimNo: form.claimNo,
        dependentId: Number(form.dependentId),
        academicYear: form.academicYear,
        claimType: form.claimType,
        schoolName: form.schoolName,
        schoolRegNo: form.schoolRegNo || null,
        standardClass: form.standardClass,
        periodFrom: form.periodFrom,
        periodTo: form.periodTo,
        claimedAmount: Number(form.claimedAmount),
        supportingDocRef: form.supportingDocRef || null,
      }
      return (await apiClient.post<CeaClaimResponse>('/v1/self-service/cea-claims', payload)).data
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'CEA claim submitted.' })
      queryClient.invalidateQueries({ queryKey: ['cea-claims-mine'] })
      setForm(emptyForm())
    },
  })

  const canSubmit =
    employeeId !== null &&
    form.claimNo.trim() !== '' &&
    form.dependentId !== '' &&
    form.academicYear.trim() !== '' &&
    form.schoolName.trim() !== '' &&
    form.standardClass.trim() !== '' &&
    form.periodFrom !== '' &&
    form.periodTo !== '' &&
    Number(form.claimedAmount) > 0 &&
    selectedDependent?.ceaEligibility !== 'INELIGIBLE_OVERAGE' &&
    !submitMutation.isPending

  // ---- Admin review (HR Gate 1 / Finance Gate 2) ----
  const pendingVerificationQuery = useQuery({
    queryKey: ['cea-claims-pending-verification'],
    queryFn: async () => (await apiClient.get<CeaClaimResponse[]>('/v1/admin/cea-claims/pending-verification')).data,
    enabled: isHrAdmin,
  })
  const pendingBillPassingQuery = useQuery({
    queryKey: ['cea-claims-pending-bill-passing'],
    queryFn: async () => (await apiClient.get<CeaClaimResponse[]>('/v1/admin/cea-claims/pending-bill-passing')).data,
    enabled: isFinanceAdmin,
  })

  function invalidateAll() {
    queryClient.invalidateQueries({ queryKey: ['cea-claims-pending-verification'] })
    queryClient.invalidateQueries({ queryKey: ['cea-claims-pending-bill-passing'] })
    queryClient.invalidateQueries({ queryKey: ['cea-claims-mine'] })
    queryClient.invalidateQueries({ queryKey: ['cea-claims-history'] })
  }

  const verifyMutation = useMutation({
    mutationFn: async ({ id, request }: { id: number; request: CeaClaimVerifyRequest }) =>
      (await apiClient.put(`/v1/admin/cea-claims/${id}/verify`, request)).data,
    onSuccess: () => {
      show({ tone: 'success', message: 'Claim verified.' })
      invalidateAll()
      setReviewTarget(null)
    },
  })

  const passBillMutation = useMutation({
    mutationFn: async ({ id, request }: { id: number; request: CeaClaimBillPassRequest }) =>
      (await apiClient.put(`/v1/admin/cea-claims/${id}/pass-bill`, request)).data,
    onSuccess: () => {
      show({ tone: 'success', message: 'Bill passed — will be disbursed on the next payroll run.' })
      invalidateAll()
      setReviewTarget(null)
    },
  })

  const rejectMutation = useMutation({
    mutationFn: async ({ id, reason }: { id: number; reason: string }) =>
      (await apiClient.put(`/v1/admin/cea-claims/${id}/reject`, { reason })).data,
    onSuccess: () => {
      show({ tone: 'success', message: 'Claim rejected.' })
      invalidateAll()
      setReviewTarget(null)
    },
  })

  const reviewActionPending = verifyMutation.isPending || passBillMutation.isPending || rejectMutation.isPending

  return (
    <div>
      <PageHeader
        title="Children Education Allowance (CEA) / Hostel Subsidy"
        description="Submit and track reimbursement claims for a dependent son/daughter's education"
      />

      {(isHrAdmin || isFinanceAdmin) && (
        <div className="mb-4 flex gap-2">
          <button
            type="button"
            onClick={() => setTab('claims')}
            className={`rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${tab === 'claims' ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'}`}
          >
            Claims &amp; Review
          </button>
          <button
            type="button"
            onClick={() => setTab('history')}
            className={`rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${tab === 'history' ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'}`}
          >
            History / Log
          </button>
        </div>
      )}

      {tab === 'history' ? (
        <CeaHistoryTab />
      ) : (
        <>
          <Card className="mb-6">
            <h2 className="mb-3 text-sm font-semibold text-slate-700">Submit New Claim</h2>
            {eligibleDependents.length === 0 && !familyQuery.isLoading && (
              <div className="mb-3 flex items-start gap-2 rounded-md border border-amber-300 bg-amber-50 p-3 text-xs text-amber-800">
                <AlertTriangle size={14} className="mt-0.5 shrink-0" />
                <span>No son/daughter dependents found in your Family Register. Add one under Profile &gt; Family &amp; Nominees first.</span>
              </div>
            )}
            <form
              className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3"
              onSubmit={(e) => {
                e.preventDefault()
                submitMutation.mutate()
              }}
            >
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Claim / Bill No.</label>
                <input
                  value={form.claimNo}
                  onChange={(e) => setForm({ ...form, claimNo: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  placeholder="e.g. CEA/2026/001"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Dependent</label>
                <select
                  value={form.dependentId}
                  onChange={(e) => setForm({ ...form, dependentId: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                >
                  <option value="">Select dependent...</option>
                  {eligibleDependents.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.name} ({d.relationship})
                    </option>
                  ))}
                </select>
                {selectedDependent && (
                  <div className="mt-1">
                    <EligibilityBadge status={selectedDependent.ceaEligibility} />
                  </div>
                )}
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Claim Type</label>
                <select
                  value={form.claimType}
                  onChange={(e) => setForm({ ...form, claimType: e.target.value as CeaClaimType })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                >
                  <option value="CEA">Children Education Allowance</option>
                  <option value="HOSTEL_SUBSIDY">Hostel Subsidy</option>
                </select>
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Academic Year</label>
                <input
                  value={form.academicYear}
                  onChange={(e) => setForm({ ...form, academicYear: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  placeholder="2026-2027"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">School Name</label>
                <input
                  value={form.schoolName}
                  onChange={(e) => setForm({ ...form, schoolName: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">School Reg. No. (optional)</label>
                <input
                  value={form.schoolRegNo}
                  onChange={(e) => setForm({ ...form, schoolRegNo: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Standard / Class</label>
                <input
                  value={form.standardClass}
                  onChange={(e) => setForm({ ...form, standardClass: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  placeholder="e.g. V"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Period From</label>
                <DatePicker value={form.periodFrom} onChange={(v) => setForm({ ...form, periodFrom: v })} />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Period To</label>
                <DatePicker value={form.periodTo} onChange={(v) => setForm({ ...form, periodTo: v })} />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Claimed Amount</label>
                <input
                  type="number"
                  min={0.01}
                  step={0.01}
                  value={form.claimedAmount}
                  onChange={(e) => setForm({ ...form, claimedAmount: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Supporting Doc Ref. (optional)</label>
                <input
                  value={form.supportingDocRef}
                  onChange={(e) => setForm({ ...form, supportingDocRef: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
              <div className="flex items-end">
                <PrimaryButton type="submit" disabled={!canSubmit}>
                  Submit Claim
                </PrimaryButton>
              </div>
            </form>
            {selectedDependent?.ceaEligibility === 'INELIGIBLE_OVERAGE' && (
              <p className="mt-2 text-xs font-medium text-amber-600">
                {selectedDependent.name} is not currently CEA-eligible (age/dependent-status limit exceeded) - submission is disabled.
              </p>
            )}
            {submitMutation.isError && <div className="mt-2"><ErrorState message={describeApiError(submitMutation.error, 'Could not submit the claim.')} /></div>}
          </Card>

          <Card className="mb-6">
            <h2 className="mb-3 text-sm font-semibold text-slate-700">My Claims</h2>
            {myClaimsQuery.isLoading && <LoadingState />}
            {myClaimsQuery.data && myClaimsQuery.data.length === 0 && <EmptyState message="No CEA/Hostel Subsidy claims yet." />}
            <div className="space-y-3">
              {myClaimsQuery.data?.map((claim) => (
                <div key={claim.id} className="rounded-md border border-slate-200 p-3">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <p className="text-sm font-medium text-slate-700">
                      #{claim.claimNo} · {claim.dependentName} · {CLAIM_TYPE_LABELS[claim.claimType]} ({claim.academicYear})
                    </p>
                    <Badge tone={STATUS_TONE(claim.claimStatus)}>{claim.claimStatus}</Badge>
                  </div>
                  <p className="mt-1 text-xs text-slate-500">
                    Claimed: {formatInr(claim.claimedAmount)} · Admissible: {formatInr(claim.admissibleAmount)}
                    {claim.passedAmount != null && <> · Passed: {formatInr(claim.passedAmount)}</>}
                  </p>
                  {claim.isPayrollProcessed && <p className="mt-1 text-xs text-emerald-600">Disbursed via payroll (Head 22 - CEA).</p>}
                  {claim.claimStatus === 'REJECTED' && claim.rejectionReason && (
                    <p className="mt-1 rounded-md bg-red-50 p-2 text-xs text-red-700">Reason: {claim.rejectionReason}</p>
                  )}
                </div>
              ))}
            </div>
          </Card>

          {(isHrAdmin || isFinanceAdmin) && (
            <Card>
              <h2 className="mb-3 text-sm font-semibold text-slate-700">Admin Review</h2>

              {isHrAdmin && (
                <div className="mb-4">
                  <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">HR Verification (Gate 1)</p>
                  {pendingVerificationQuery.isLoading && <LoadingState />}
                  {(pendingVerificationQuery.data ?? []).length === 0 && !pendingVerificationQuery.isLoading && (
                    <p className="text-xs text-slate-400">Nothing pending.</p>
                  )}
                  {pendingVerificationQuery.data?.map((claim) => (
                    <ClaimReviewRow
                      key={claim.id}
                      claim={claim}
                      actionPending={reviewActionPending}
                      onVerify={() => setReviewTarget({ mode: 'verify', claim })}
                      onReject={() => setReviewTarget({ mode: 'reject', claim })}
                    />
                  ))}
                </div>
              )}

              {isFinanceAdmin && (
                <div>
                  <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Finance Bill Passing (Gate 2)</p>
                  {pendingBillPassingQuery.isLoading && <LoadingState />}
                  {(pendingBillPassingQuery.data ?? []).length === 0 && !pendingBillPassingQuery.isLoading && (
                    <p className="text-xs text-slate-400">Nothing pending (or HR verification is still outstanding).</p>
                  )}
                  {pendingBillPassingQuery.data?.map((claim) => (
                    <ClaimReviewRow
                      key={claim.id}
                      claim={claim}
                      actionPending={reviewActionPending}
                      onPassBill={() => setReviewTarget({ mode: 'passBill', claim })}
                      onReject={() => setReviewTarget({ mode: 'reject', claim })}
                    />
                  ))}
                </div>
              )}
            </Card>
          )}
        </>
      )}

      {reviewTarget?.mode === 'verify' && (
        <VerifyModal
          claim={reviewTarget.claim}
          onClose={() => setReviewTarget(null)}
          isPending={verifyMutation.isPending}
          onConfirm={(amount) =>
            verifyMutation.mutate({ id: reviewTarget.claim.id, request: { adjustedAdmissibleAmount: Number(amount) } })
          }
        />
      )}
      {reviewTarget?.mode === 'passBill' && (
        <PassBillModal
          claim={reviewTarget.claim}
          onClose={() => setReviewTarget(null)}
          isPending={passBillMutation.isPending}
          onConfirm={(request) => passBillMutation.mutate({ id: reviewTarget.claim.id, request })}
        />
      )}
      {reviewTarget?.mode === 'reject' && (
        <RejectModal
          claim={reviewTarget.claim}
          onClose={() => setReviewTarget(null)}
          isPending={rejectMutation.isPending}
          onConfirm={(reason) => rejectMutation.mutate({ id: reviewTarget.claim.id, reason })}
        />
      )}
    </div>
  )
}
