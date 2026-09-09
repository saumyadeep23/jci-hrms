import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { Modal } from '../common/Modal'
import { Badge, EmptyState, ErrorState, LoadingState } from '../common/ui'
import { REASON_OPTIONS } from './RegularizationModal'
import type { AttendanceRegularizationResponse, ApprovalStatus, EmployeeResponse } from '../../types/api'

const REASON_LABELS: Record<string, string> = Object.fromEntries(REASON_OPTIONS.map((r) => [r.value, r.label]))

const STATUS_TONE: Record<ApprovalStatus, 'success' | 'warning' | 'danger'> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
}

/** Resolves a designated approver's display name via GET /api/v1/employees/{id} - AttendanceRegularizationResponse only carries the numeric designatedApproverId, there's no batch name-lookup endpoint, so this fetches per unique approver rather than fabricating a name (same "flag rather than fabricate" precedent as RegularizationApprovalQueuePage). */
export function ApproverName({ approverId }: { approverId: number }) {
  const { data } = useQuery({
    queryKey: ['employee-brief', approverId],
    queryFn: async () => (await apiClient.get<EmployeeResponse>(`/employees/${approverId}`)).data,
  })
  return <>{data?.fullName ?? `Employee #${approverId}`}</>
}

/** ALMS Phase 3, Section 5 - the employee's own regularization request log, sourced from GET /v1/attendance/regularization/mine (already fetched by AttendanceDetailTable as myRegularizations, but this modal re-fetches on its own query key so it works standalone / stays fresh on open). */
export function MyRegularizationRequestsModal({ onClose }: { onClose: () => void }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['attendance-regularization-mine'],
    queryFn: async () => (await apiClient.get<AttendanceRegularizationResponse[]>('/v1/attendance/regularization/mine')).data,
  })

  const sorted = [...(data ?? [])].sort((a, b) => b.attendanceDate.localeCompare(a.attendanceDate))

  return (
    <Modal title="My Regularization Requests" onClose={onClose} maxWidthClassName="max-w-2xl">
      {isLoading && <LoadingState label="Loading your requests..." />}
      {isError && <ErrorState message="Could not load your regularization requests." />}
      {data && data.length === 0 && <EmptyState message="You haven't submitted any regularization requests." />}

      {sorted.length > 0 && (
        <div className="max-h-[65vh] space-y-3 overflow-y-auto pr-1">
          {sorted.map((req) => (
            <div key={req.id} className="rounded-lg border border-slate-200 p-3">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="text-sm font-medium text-slate-800">Attendance Date: {formatDate(req.attendanceDate)}</p>
                  <p className="text-xs text-slate-400">Requested on {formatDate(req.createdAt, 'dd-MM-yyyy hh:mm a')}</p>
                </div>
                <Badge tone={STATUS_TONE[req.approvalStatus]}>{req.approvalStatus}</Badge>
              </div>

              <div className="mt-2 grid grid-cols-2 gap-3 rounded-md bg-slate-50 p-2 text-xs sm:grid-cols-4">
                <div>
                  <p className="text-slate-400">Reason</p>
                  <p className="font-medium text-slate-700">{REASON_LABELS[req.reasonCode] ?? req.reasonCode}</p>
                </div>
                <div>
                  <p className="text-slate-400">Requested In</p>
                  <p className="font-medium text-slate-700">{formatDate(req.correctedInTime, 'dd-MM-yyyy hh:mm a')}</p>
                </div>
                <div>
                  <p className="text-slate-400">Requested Out</p>
                  <p className="font-medium text-slate-700">{formatDate(req.correctedOutTime, 'dd-MM-yyyy hh:mm a')}</p>
                </div>
                <div>
                  <p className="text-slate-400">Approver</p>
                  <p className="font-medium text-slate-700">
                    {req.designatedApproverId !== null ? <ApproverName approverId={req.designatedApproverId} /> : '—'}
                  </p>
                </div>
              </div>

              {req.remarks && (
                <p className="mt-2 text-xs text-slate-600">
                  <span className="font-medium text-slate-500">Your Justification: </span>
                  {req.remarks}
                </p>
              )}

              {req.approvalStatus === 'REJECTED' && req.approverRemarks && (
                <p className="mt-2 rounded-md bg-red-50 p-2 text-xs text-red-700">
                  <span className="font-medium">Rejection Reason: </span>
                  {req.approverRemarks}
                </p>
              )}
              {req.approvalStatus === 'APPROVED' && req.approverRemarks && (
                <p className="mt-2 text-xs text-slate-600">
                  <span className="font-medium text-slate-500">Approver Remarks: </span>
                  {req.approverRemarks}
                </p>
              )}
            </div>
          ))}
        </div>
      )}
    </Modal>
  )
}
