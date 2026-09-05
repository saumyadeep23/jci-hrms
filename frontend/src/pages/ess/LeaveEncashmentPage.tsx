import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Circle } from 'lucide-react'
import { apiClient } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/common/ToastProvider'
import { describeApiError } from '../../lib/apiError'
import { formatDate } from '../../lib/date'
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
    !applyMutation.isPending

  // ---- Admin review (HR Gate 1 / Finance Gate 2) ----
  const isHrAdmin = hasRole('HR_ADMIN', 'SUPER_ADMIN')
  const isFinanceAdmin = hasRole('FINANCE_ADMIN', 'SUPER_ADMIN')
  const adminListQuery = useQuery({
    queryKey: ['leave-encashment-admin-list'],
    queryFn: async () => (await apiClient.get<LeaveEncashmentResponse[]>('/v1/admin/leave/encashment')).data,
    enabled: isHrAdmin || isFinanceAdmin,
  })

  const hrApproveMutation = useMutation({
    mutationFn: async ({ id, approve }: { id: number; approve: boolean }) => {
      const payload: EncashmentGateDecisionRequest = { approve }
      return (await apiClient.patch(`/v1/admin/leave/encashment/${id}/hr-approve`, payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['leave-encashment-admin-list'] })
      queryClient.invalidateQueries({ queryKey: ['leave-encashment-mine'] })
    },
  })

  const financeApproveMutation = useMutation({
    mutationFn: async ({ id, approve }: { id: number; approve: boolean }) => {
      const payload: EncashmentGateDecisionRequest = { approve }
      return (await apiClient.patch(`/v1/admin/leave/encashment/${id}/finance-approve`, payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['leave-encashment-admin-list'] })
      queryClient.invalidateQueries({ queryKey: ['leave-encashment-mine'] })
    },
  })

  const hrPending = (adminListQuery.data ?? []).filter((a) => a.hrApprovalStatus === 'PENDING')
  const financePending = (adminListQuery.data ?? []).filter((a) => a.hrApprovalStatus === 'APPROVED' && a.financeApprovalStatus === 'PENDING')

  return (
    <div>
      <PageHeader title="In-Service EL Encashment" description="Encash Earned Leave against your encashable balance" />

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
              className="w-32 rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <PrimaryButton type="submit" disabled={!canApply}>
            Submit Claim
          </PrimaryButton>
        </form>
        {encashableAvailable < MIN_IN_SERVICE_DAYS && (
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
            <div key={app.id} className="rounded-md border border-slate-200 p-3">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm font-medium text-slate-700">
                  #{app.id} · {app.elDaysClaimed.toFixed(2)} days · {app.encashmentType.replace(/_/g, ' ')}
                </p>
                <Stepper app={app} />
              </div>
              <GateRemarks app={app} />
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
                <div key={app.id} className="mb-2 flex flex-wrap items-center justify-between gap-2 rounded-md border border-slate-200 p-2 text-sm">
                  <span>
                    #{app.id} · {app.employeeCode} · {app.elDaysClaimed.toFixed(2)} days
                  </span>
                  <div className="flex gap-2">
                    <SecondaryButton onClick={() => hrApproveMutation.mutate({ id: app.id, approve: false })} disabled={hrApproveMutation.isPending}>
                      Reject
                    </SecondaryButton>
                    <PrimaryButton onClick={() => hrApproveMutation.mutate({ id: app.id, approve: true })} disabled={hrApproveMutation.isPending}>
                      Approve
                    </PrimaryButton>
                  </div>
                </div>
              ))}
            </div>
          )}

          {isFinanceAdmin && (
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Finance Approval (Gate 2)</p>
              {financePending.length === 0 && <p className="text-xs text-slate-400">Nothing pending (or HR approval is still outstanding).</p>}
              {financePending.map((app) => (
                <div key={app.id} className="mb-2 flex flex-wrap items-center justify-between gap-2 rounded-md border border-slate-200 p-2 text-sm">
                  <span>
                    #{app.id} · {app.employeeCode} · {app.elDaysClaimed.toFixed(2)} days
                  </span>
                  <div className="flex gap-2">
                    <SecondaryButton onClick={() => financeApproveMutation.mutate({ id: app.id, approve: false })} disabled={financeApproveMutation.isPending}>
                      Reject
                    </SecondaryButton>
                    <PrimaryButton onClick={() => financeApproveMutation.mutate({ id: app.id, approve: true })} disabled={financeApproveMutation.isPending}>
                      Approve
                    </PrimaryButton>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>
      )}
    </div>
  )
}

