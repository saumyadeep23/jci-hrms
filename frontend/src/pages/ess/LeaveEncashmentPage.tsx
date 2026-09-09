import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, Circle } from 'lucide-react'
import { apiClient } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/common/ToastProvider'
import { describeApiError } from '../../lib/apiError'
import { formatDate } from '../../lib/date'
import { ElLedgerModal } from '../../components/leave/ElLedgerModal'
import { EncashmentApprovalHistoryTab } from '../../components/leave/EncashmentApprovalHistoryTab'
import { EncashmentSanctionMemoModal, type SanctionMemoData } from '../../components/leave/EncashmentSanctionMemoModal'
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
  EncashmentGateDecisionRequest,
  EncashmentType,
  LeaveEncashmentRequest,
  LeaveEncashmentResponse,
  LeaveEntitlementBalanceResponse,
} from '../../types/api'

const MIN_IN_SERVICE_DAYS = 15

function formatInr(value: number | null): string {
  return value != null ? `₹${value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '—'
}

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
] as const

function cycleLabel(year: number | null, month: number | null): string | null {
  return year != null && month != null ? `${MONTH_NAMES[month - 1]} ${year}` : null
}

/** "Queued for Monthly Payroll (Sept 2026)" before a run is finalized, "Disbursed in Payroll: Sept 2026 | Voucher #ELE-1" after. */
function PayrollStatusBadge({ app }: { app: LeaveEncashmentResponse }) {
  if (!app.payrollEligible) return null
  const cycle = cycleLabel(app.payrollCycleYear, app.payrollCycleMonth)
  if (app.payrollProcessed && cycle) {
    return (
      <Badge tone="success">
        Disbursed in Payroll: {cycle} | Voucher #ELE-{app.id}
      </Badge>
    )
  }
  if (cycle) {
    return <Badge tone="warning">Payroll Status: Queued for Monthly Payroll ({cycle})</Badge>
  }
  return <Badge tone="neutral">Payroll Status: Not Yet Queued</Badge>
}

function FinancialPill({ app }: { app: LeaveEncashmentResponse }) {
  if (app.grossAmount == null) return null
  return (
    <div className="mt-1.5 inline-flex flex-wrap items-center gap-1 rounded-full bg-emerald-50 px-3 py-1 text-xs text-emerald-700">
      <span className="font-semibold">Gross Amount: {formatInr(app.grossAmount)}</span>
      {app.currentBasicPay != null && <span>(Basic: {formatInr(app.currentBasicPay)}</span>}
      {app.daRateApplied != null && <span>| DA: {app.daRateApplied.toFixed(1)}%)</span>}
    </div>
  )
}

/** Amber child card rendered directly below a parent claim once checkForRetroactiveArrear() has computed a top-up owed on it. */
function ArrearChildCard({ app }: { app: LeaveEncashmentResponse }) {
  if (app.arrearAmount <= 0) return null
  const oldDa = app.daRateApplied ?? 0
  const newDa = oldDa + app.arrearDaRateDiff
  const arrearCycle = cycleLabel(app.arrearPayrollCycleYear, app.arrearPayrollCycleMonth)
  return (
    <div className="mt-2 ml-4 rounded-md border border-amber-200 bg-amber-50 p-2.5 text-xs">
      <p className="font-semibold text-amber-800">DA Arrear Adjustment (Linked to Claim #{app.id})</p>
      <p className="mt-0.5 text-amber-700">
        DA Revised from {oldDa.toFixed(1)}% to {newDa.toFixed(1)}% (+{app.arrearDaRateDiff.toFixed(1)}%)
      </p>
      <p className="text-amber-700">Arrear Payable: {formatInr(app.arrearAmount)}</p>
      <p className="mt-0.5">
        <Badge tone={app.arrearSettled ? 'success' : 'warning'}>
          Status: {app.arrearSettled ? `Disbursed${arrearCycle ? ` (${arrearCycle})` : ''}` : 'Pending Payroll Credit'}
        </Badge>
      </p>
    </div>
  )
}

type Gate = 'hr' | 'finance'

const STAGES = ['Submitted', 'HR Approval (Gate 1)', 'Finance Approval (Gate 2)', 'Ready for Payroll'] as const

function stageIndex(app: LeaveEncashmentResponse): number {
  if (app.payrollEligible) return 3
  if (app.hrApprovalStatus === 'APPROVED') return 2
  return 1
}

function Stepper({ app }: { app: LeaveEncashmentResponse }) {
  const rejected = app.hrApprovalStatus === 'REJECTED' || app.financeApprovalStatus === 'REJECTED'
  const current = stageIndex(app)
  return (
    <div className="flex items-center gap-1">
      {STAGES.map((label, i) => (
        <div key={label} className="flex items-center gap-1">
          <div className="flex flex-col items-center gap-0.5">
            {i < current || (i === current && !rejected && current === 3) ? (
              <CheckCircle2 size={16} className="text-emerald-500" />
            ) : rejected && i === current ? (
              <Circle size={16} className="text-red-500" />
            ) : (
              <Circle size={16} className={i <= current ? 'text-brand-forest' : 'text-slate-300'} />
            )}
            <span className="max-w-[80px] text-center text-[10px] leading-tight text-slate-500">{label}</span>
          </div>
          {i < STAGES.length - 1 && <div className={`h-px w-6 ${i < current ? 'bg-emerald-400' : 'bg-slate-200'}`} />}
        </div>
      ))}
    </div>
  )
}

function GateRemarks({ app }: { app: LeaveEncashmentResponse }) {
  return (
    <div className="mt-2 grid grid-cols-1 gap-2 text-xs sm:grid-cols-2">
      <div className="rounded-md bg-slate-50 p-2">
        <p className="font-medium text-slate-600">
          HR: <Badge tone={app.hrApprovalStatus === 'APPROVED' ? 'success' : app.hrApprovalStatus === 'REJECTED' ? 'danger' : 'warning'}>{app.hrApprovalStatus}</Badge>
        </p>
        {app.hrApprovedAt && <p className="text-slate-400">{formatDate(app.hrApprovedAt, 'dd-MM-yyyy hh:mm a')}</p>}
        {app.hrRemarks && <p className="mt-0.5 text-slate-500">{app.hrRemarks}</p>}
      </div>
      <div className="rounded-md bg-slate-50 p-2">
        <p className="font-medium text-slate-600">
          Finance:{' '}
          <Badge tone={app.financeApprovalStatus === 'APPROVED' ? 'success' : app.financeApprovalStatus === 'REJECTED' ? 'danger' : 'warning'}>
            {app.financeApprovalStatus}
          </Badge>
        </p>
        {app.financeApprovedAt && <p className="text-slate-400">{formatDate(app.financeApprovedAt, 'dd-MM-yyyy hh:mm a')}</p>}
        {app.financeRemarks && <p className="mt-0.5 text-slate-500">{app.financeRemarks}</p>}
      </div>
      {app.payrollEligible && app.serviceBookEntryId && (
        <p className="col-span-full flex items-center gap-1 rounded-md bg-emerald-50 px-2 py-1.5 text-emerald-700">
          <CheckCircle2 size={13} /> Digital service book entry #{app.serviceBookEntryId} recorded - payroll eligible.
        </p>
      )}
    </div>
  )
}

function RejectRemarksModal({ onClose, onConfirm, isPending }: { onClose: () => void; onConfirm: (remarks: string) => void; isPending: boolean }) {
  const [remarks, setRemarks] = useState('')
  return (
    <Modal title="Reject Encashment Claim" onClose={onClose} maxWidthClassName="max-w-md">
      <label className="mb-1 block text-xs font-medium text-slate-600">Remarks (required)</label>
      <textarea
        required
        autoFocus
        value={remarks}
        onChange={(e) => setRemarks(e.target.value)}
        rows={3}
        className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        placeholder="Reason for rejecting this claim..."
      />
      <div className="mt-4 flex justify-end gap-2">
        <SecondaryButton type="button" onClick={onClose}>
          Cancel
        </SecondaryButton>
        <PrimaryButton type="button" disabled={remarks.trim() === '' || isPending} onClick={() => onConfirm(remarks.trim())}>
          Confirm Rejection
        </PrimaryButton>
      </div>
    </Modal>
  )
}

function AdminReviewRow({
  app,
  onApprove,
  onReject,
  onViewLedger,
  actionPending,
}: {
  app: LeaveEncashmentResponse
  onApprove: () => void
  onReject: () => void
  onViewLedger: () => void
  actionPending: boolean
}) {
  return (
    <div className="mb-2 rounded-md border border-slate-200 p-3 text-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-medium text-slate-800">
            {app.employeeCode} · {app.fullName}
            {app.designation && <span className="font-normal text-slate-500"> · {app.designation}</span>}
          </p>
          <p className="mt-0.5 text-xs text-slate-500">
            Applied on {formatDate(app.applicationDate)} · {app.elDaysClaimed.toFixed(2)} days requested · Current Basic:{' '}
            {formatInr(app.currentBasicPay)}
          </p>
        </div>
        <div className="flex shrink-0 flex-wrap gap-2">
          <SecondaryButton onClick={onViewLedger}>View Ledger</SecondaryButton>
          <SecondaryButton onClick={onReject} disabled={actionPending}>
            Reject
          </SecondaryButton>
          <PrimaryButton onClick={onApprove} disabled={actionPending}>
            Approve
          </PrimaryButton>
        </div>
      </div>
    </div>
  )
}

/**
 * Finance (Gate 2) review row - the full CPSE emoluments breakdown, not just a bare basic-pay
 * figure, since Finance is the gate actually sanctioning a rupee amount, not just an eligibility
 * decision. daRateApplied/grossAmount are null for pre-V63 applications never finance-decided since -
 * falls back to a plain "no emoluments snapshot recorded" notice rather than showing zeros.
 */
function FinanceReviewRow({
  app,
  onApprove,
  onReject,
  onViewLedger,
  actionPending,
}: {
  app: LeaveEncashmentResponse
  onApprove: () => void
  onReject: () => void
  onViewLedger: () => void
  actionPending: boolean
}) {
  const hasEmoluments = app.currentBasicPay != null && app.daRateApplied != null
  const daAmount = hasEmoluments ? (app.currentBasicPay! * app.daRateApplied!) / 100 : null
  const totalEmoluments = hasEmoluments ? app.currentBasicPay! + daAmount! : null

  return (
    <div className="mb-2 rounded-md border border-slate-200 p-3 text-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-medium text-slate-800">
            {app.employeeCode} · {app.fullName}
            {app.designation && <span className="font-normal text-slate-500"> · {app.designation}</span>}
          </p>
          <p className="mt-0.5 text-xs text-slate-500">Applied on {formatDate(app.applicationDate)}</p>
        </div>
        <div className="flex shrink-0 flex-wrap gap-2">
          <SecondaryButton onClick={onViewLedger}>View Ledger</SecondaryButton>
          <SecondaryButton onClick={onReject} disabled={actionPending}>
            Reject
          </SecondaryButton>
          <PrimaryButton onClick={onApprove} disabled={actionPending}>
            Approve
          </PrimaryButton>
        </div>
      </div>

      {hasEmoluments ? (
        <div className="mt-3 grid grid-cols-2 gap-2 rounded-md bg-slate-50 p-3 text-xs sm:grid-cols-3">
          <div>
            <p className="text-slate-400">Basic Pay</p>
            <p className="font-semibold tabular-nums text-slate-800">{formatInr(app.currentBasicPay)}</p>
          </div>
          <div>
            <p className="text-slate-400">DA Rate</p>
            <p className="font-semibold tabular-nums text-slate-800">
              {app.daRateApplied!.toFixed(1)}% ({formatInr(daAmount)})
            </p>
          </div>
          <div>
            <p className="text-slate-400">Total Emoluments (Basic + DA)</p>
            <p className="font-semibold tabular-nums text-slate-800">{formatInr(totalEmoluments)}</p>
          </div>
          <div>
            <p className="text-slate-400">Days Requested</p>
            <p className="font-semibold tabular-nums text-slate-800">{app.elDaysClaimed.toFixed(2)} Days</p>
          </div>
          <div className="sm:col-span-2">
            <p className="text-slate-400">Total Encashable Payable</p>
            <p className="font-semibold tabular-nums text-emerald-700">{formatInr(app.grossAmount)}</p>
          </div>
          {!app.arrearSettled && app.arrearAmount > 0 && (
            <div className="col-span-full">
              <Badge tone="warning">Retroactive DA Arrear Pending: {formatInr(app.arrearAmount)}</Badge>
            </div>
          )}
        </div>
      ) : (
        <p className="mt-3 rounded-md bg-slate-50 p-2 text-xs text-slate-400">
          No emoluments snapshot recorded for this application (submitted before the emoluments engine was added).
        </p>
      )}
    </div>
  )
}

type EncashmentTab = 'claims' | 'history'

/** ALMS Phase 3, Section 3. */
export function LeaveEncashmentPage() {
  const { employeeId, hasRole } = useAuth()
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [elDays, setElDays] = useState('15.00')
  const [encashmentType] = useState<EncashmentType>('IN_SERVICE_EL')

  const entitlementQuery = useQuery({
    queryKey: ['leave-entitlement-balance-mine', new Date().getFullYear()],
    queryFn: async () =>
      (await apiClient.get<LeaveEntitlementBalanceResponse[]>('/v1/leave-entitlement-balance/mine')).data,
  })
  const el = entitlementQuery.data?.find((b) => b.leaveTypeCode === 'EL')
  const encashableAvailable = el?.encashableAvailable ?? 0

  const myApplicationsQuery = useQuery({
    queryKey: ['leave-encashment-mine'],
    queryFn: async () => (await apiClient.get<LeaveEncashmentResponse[]>('/v1/self-service/leave/encashment/mine')).data,
  })

  // Frontend mirror of LeaveEncashmentService.enforceOncePerCalendarYear(): a not-yet-rejected
  // IN_SERVICE_EL claim already applied for this calendar year blocks a second submission - this
  // is a UX safeguard only, the backend is the authority and still rejects a bypassed submission.
  const currentYear = new Date().getFullYear()
  const activeClaimThisYear = (myApplicationsQuery.data ?? []).find(
    (a) =>
      a.encashmentType === 'IN_SERVICE_EL' &&
      a.hrApprovalStatus !== 'REJECTED' &&
      a.financeApprovalStatus !== 'REJECTED' &&
      new Date(a.applicationDate).getFullYear() === currentYear,
  )

  const applyMutation = useMutation({
    mutationFn: async () => {
      const payload: LeaveEncashmentRequest = { employeeId: employeeId!, encashmentType, elDaysClaimed: Number(elDays) }
      return (await apiClient.post<LeaveEncashmentResponse>('/v1/self-service/leave/encashment', payload)).data
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'Encashment claim submitted.' })
      queryClient.invalidateQueries({ queryKey: ['leave-encashment-mine'] })
      queryClient.invalidateQueries({ queryKey: ['leave-entitlement-balance-mine'] })
    },
  })

  const canApply =
    employeeId !== null &&
    Number(elDays) >= MIN_IN_SERVICE_DAYS &&
    Number(elDays) <= encashableAvailable &&
    encashableAvailable >= MIN_IN_SERVICE_DAYS &&
    !activeClaimThisYear &&
    !applyMutation.isPending

  // ---- Admin review (HR Gate 1 / Finance Gate 2) ----
  const isHrAdmin = hasRole('HR_ADMIN', 'SUPER_ADMIN')
  const isFinanceAdmin = hasRole('FINANCE_ADMIN', 'SUPER_ADMIN')
  const [tab, setTab] = useState<EncashmentTab>('claims')
  const adminListQuery = useQuery({
    queryKey: ['leave-encashment-admin-list'],
    queryFn: async () => (await apiClient.get<LeaveEncashmentResponse[]>('/v1/admin/leave/encashment')).data,
    enabled: isHrAdmin || isFinanceAdmin,
  })

  const [ledgerTarget, setLedgerTarget] = useState<{ employeeId: number; label: string } | null>(null)
  const [rejectTarget, setRejectTarget] = useState<{ id: number; gate: Gate } | null>(null)
  const [memoTarget, setMemoTarget] = useState<SanctionMemoData | null>(null)

  function toMemoData(app: LeaveEncashmentResponse): SanctionMemoData {
    return {
      id: app.id,
      employeeCode: app.employeeCode,
      fullName: app.fullName,
      designation: app.designation,
      elDaysClaimed: app.elDaysClaimed,
      basicPay: app.currentBasicPay,
      daRateApplied: app.daRateApplied,
      grossAmount: app.grossAmount,
      arrearAmount: app.arrearAmount,
      arrearSettled: app.arrearSettled,
      sanctionDate: app.financeApprovedAt ?? app.createdAt,
    }
  }

  const hrApproveMutation = useMutation({
    mutationFn: async ({ id, approve, remarks }: { id: number; approve: boolean; remarks?: string }) => {
      const payload: EncashmentGateDecisionRequest = { approve, remarks: remarks ?? null }
      return (await apiClient.patch(`/v1/admin/leave/encashment/${id}/hr-approve`, payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['leave-encashment-admin-list'] })
      queryClient.invalidateQueries({ queryKey: ['leave-encashment-mine'] })
      setRejectTarget(null)
    },
  })

  const financeApproveMutation = useMutation({
    mutationFn: async ({ id, approve, remarks }: { id: number; approve: boolean; remarks?: string }) => {
      const payload: EncashmentGateDecisionRequest = { approve, remarks: remarks ?? null }
      return (await apiClient.patch(`/v1/admin/leave/encashment/${id}/finance-approve`, payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['leave-encashment-admin-list'] })
      queryClient.invalidateQueries({ queryKey: ['leave-encashment-mine'] })
      setRejectTarget(null)
    },
  })

  function confirmReject(remarks: string) {
    if (!rejectTarget) return
    if (rejectTarget.gate === 'hr') {
      hrApproveMutation.mutate({ id: rejectTarget.id, approve: false, remarks })
    } else {
      financeApproveMutation.mutate({ id: rejectTarget.id, approve: false, remarks })
    }
  }

  const hrPending = (adminListQuery.data ?? []).filter((a) => a.hrApprovalStatus === 'PENDING')
  const financePending = (adminListQuery.data ?? []).filter((a) => a.hrApprovalStatus === 'APPROVED' && a.financeApprovalStatus === 'PENDING')

  return (
    <div>
      <PageHeader title="In-Service EL Encashment" description="Encash Earned Leave against your encashable balance" />

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
            Past Sanctions / History
          </button>
        </div>
      )}

      {tab === 'history' ? (
        <EncashmentApprovalHistoryTab />
      ) : (
        <>
      <Card className="mb-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-xs text-slate-400">Available Encashable EL</p>
            <p className="text-2xl font-semibold text-slate-800">{entitlementQuery.isLoading ? '···' : encashableAvailable.toFixed(2)}</p>
          </div>
          <p className="max-w-sm text-xs text-slate-500">
            In-service encashment requires a minimum claim of {MIN_IN_SERVICE_DAYS.toFixed(1)} days against your encashable balance.
          </p>
        </div>
      </Card>

      <Card className="mb-6">
        <h2 className="mb-3 text-sm font-semibold text-slate-700">Submit New Claim</h2>
        {activeClaimThisYear && (
          <div className="mb-3 flex items-start gap-2 rounded-md border border-amber-300 bg-amber-50 p-3 text-xs text-amber-800">
            <AlertTriangle size={14} className="mt-0.5 shrink-0" />
            <span>
              In-service EL encashment policy allows only one encashment per calendar year. You have already submitted/availed a claim
              (#{activeClaimThisYear.id}) for {currentYear}.
            </span>
          </div>
        )}
        <form
          className="flex flex-wrap items-end gap-3"
          onSubmit={(e) => {
            e.preventDefault()
            applyMutation.mutate()
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">EL Days to Encash</label>
            <input
              type="number"
              min={MIN_IN_SERVICE_DAYS}
              step={0.5}
              max={encashableAvailable}
              value={elDays}
              onChange={(e) => setElDays(e.target.value)}
              disabled={Boolean(activeClaimThisYear)}
              className="w-32 rounded-md border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-50 disabled:text-slate-400"
            />
          </div>
          <PrimaryButton type="submit" disabled={!canApply}>
            Submit Claim
          </PrimaryButton>
        </form>
        {!activeClaimThisYear && encashableAvailable < MIN_IN_SERVICE_DAYS && (
          <p className="mt-2 text-xs font-medium text-amber-600">
            Your encashable balance ({encashableAvailable.toFixed(2)}) is below the {MIN_IN_SERVICE_DAYS.toFixed(1)}-day minimum - submission is disabled.
          </p>
        )}
        {applyMutation.isError && <div className="mt-2"><ErrorState message={describeApiError(applyMutation.error, 'Could not submit the claim.')} /></div>}
      </Card>

      <Card className="mb-6">
        <h2 className="mb-3 text-sm font-semibold text-slate-700">My Encashment Claims</h2>
        {myApplicationsQuery.isLoading && <LoadingState />}
        {myApplicationsQuery.data && myApplicationsQuery.data.length === 0 && <EmptyState message="No encashment claims yet." />}
        <div className="space-y-4">
          {myApplicationsQuery.data?.map((app) => (
            <div key={app.id}>
              <div className="rounded-md border border-slate-200 p-3">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <p className="text-sm font-medium text-slate-700">
                    #{app.id} · {app.elDaysClaimed.toFixed(2)} days · {app.encashmentType.replace(/_/g, ' ')}
                  </p>
                  <Stepper app={app} />
                </div>
                <FinancialPill app={app} />
                {app.payrollEligible && (
                  <div className="mt-1.5">
                    <PayrollStatusBadge app={app} />
                  </div>
                )}
                <GateRemarks app={app} />
                {app.grossAmount != null && (
                  <div className="mt-2">
                    <SecondaryButton onClick={() => setMemoTarget(toMemoData(app))}>Print / Download Sanction Memo</SecondaryButton>
                  </div>
                )}
              </div>
              <ArrearChildCard app={app} />
            </div>
          ))}
        </div>
      </Card>

      {(isHrAdmin || isFinanceAdmin) && (
        <Card>
          <h2 className="mb-3 text-sm font-semibold text-slate-700">Admin Review</h2>

          {isHrAdmin && (
            <div className="mb-4">
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">HR Approval (Gate 1)</p>
              {hrPending.length === 0 && <p className="text-xs text-slate-400">Nothing pending.</p>}
              {hrPending.map((app) => (
                <AdminReviewRow
                  key={app.id}
                  app={app}
                  actionPending={hrApproveMutation.isPending}
                  onViewLedger={() => setLedgerTarget({ employeeId: app.employeeId, label: `${app.employeeCode} · ${app.fullName}` })}
                  onReject={() => setRejectTarget({ id: app.id, gate: 'hr' })}
                  onApprove={() => hrApproveMutation.mutate({ id: app.id, approve: true })}
                />
              ))}
            </div>
          )}

          {isFinanceAdmin && (
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Finance Approval (Gate 2)</p>
              {financePending.length === 0 && <p className="text-xs text-slate-400">Nothing pending (or HR approval is still outstanding).</p>}
              {financePending.map((app) => (
                <FinanceReviewRow
                  key={app.id}
                  app={app}
                  actionPending={financeApproveMutation.isPending}
                  onViewLedger={() => setLedgerTarget({ employeeId: app.employeeId, label: `${app.employeeCode} · ${app.fullName}` })}
                  onReject={() => setRejectTarget({ id: app.id, gate: 'finance' })}
                  onApprove={() => financeApproveMutation.mutate({ id: app.id, approve: true })}
                />
              ))}
            </div>
          )}
        </Card>
      )}

      {ledgerTarget && (
        <ElLedgerModal employeeId={ledgerTarget.employeeId} employeeLabel={ledgerTarget.label} onClose={() => setLedgerTarget(null)} />
      )}

      {rejectTarget && (
        <RejectRemarksModal
          onClose={() => setRejectTarget(null)}
          onConfirm={confirmReject}
          isPending={rejectTarget.gate === 'hr' ? hrApproveMutation.isPending : financeApproveMutation.isPending}
        />
      )}

      {memoTarget && <EncashmentSanctionMemoModal app={memoTarget} onClose={() => setMemoTarget(null)} />}
        </>
      )}
    </div>
  )
}

