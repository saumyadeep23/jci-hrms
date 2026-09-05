import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2 } from 'lucide-react'
import { apiClient } from '../../api/client'
import { useToast } from '../../components/common/ToastProvider'
import { formatDate } from '../../lib/date'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../components/common/ui'
import type { AttendanceRegularizationResponse, RegularizationDecisionRequest } from '../../types/api'

const REASON_LABELS: Record<string, string> = {
  FORGOT_PUNCH: 'Forgot to Punch',
  DEVICE_FAILURE: 'Device / App Failure',
  FIELD_DUTY: 'Local Official / Field Duty',
  GEOFENCE_ISSUE: 'Geofence Issue',
  SYSTEM_ERROR: 'System Error',
  OTHER: 'Other',
}

/**
 * ALMS Phase 3, Section 4 - the HoD queue. AttendanceRegularizationResponse
 * doesn't carry the day's original punch timestamps (only the requested
 * corrected times), so this shows corrected time + reason + justification
 * rather than a true original-vs-requested comparison - there is no
 * admin/approver endpoint to look up another employee's raw punches by date
 * yet. Flagged rather than fabricated.
 */
export function RegularizationApprovalQueuePage() {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [remarksById, setRemarksById] = useState<Record<number, string>>({})

  const { data, isLoading, isError } = useQuery({
    queryKey: ['attendance-regularization-pending-approval'],
    queryFn: async () => (await apiClient.get<AttendanceRegularizationResponse[]>('/v1/attendance/regularization/pending-approval')).data,
  })

  const decideMutation = useMutation({
    mutationFn: async ({ id, approve }: { id: number; approve: boolean }) => {
      const payload: RegularizationDecisionRequest = { approve, remarks: remarksById[id] ?? null }
      return (await apiClient.patch<AttendanceRegularizationResponse>(`/v1/attendance/regularization/${id}/approve`, payload)).data
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['attendance-regularization-pending-approval'] })
      show({
        tone: 'success',
        message: variables.approve
          ? 'Regularization approved. If a 0.5-day penalty was previously auto-debited for this day, it has been refunded automatically.'
          : 'Regularization rejected.',
      })
    },
  })

  return (
    <div>
      <PageHeader title="Regularization Approval Queue" description="Review pending attendance regularization requests from your team" />

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load the approval queue." />}
      {data && data.length === 0 && <EmptyState message="No pending regularization requests." />}

      <div className="space-y-3">
        {data?.map((req) => (
          <Card key={req.id}>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="font-medium text-slate-800">
                  {req.employeeCode} · {formatDate(req.attendanceDate)}
                </p>
                <Badge tone="warning">{REASON_LABELS[req.reasonCode] ?? req.reasonCode}</Badge>
              </div>
              <div className="grid grid-cols-2 gap-3 rounded-md bg-slate-50 p-2 text-xs">
                <div>
                  <p className="text-slate-400">Requested In</p>
                  <p className="font-medium text-slate-700">{formatDate(req.correctedInTime, 'dd-MM-yyyy hh:mm a')}</p>
                </div>
                <div>
                  <p className="text-slate-400">Requested Out</p>
                  <p className="font-medium text-slate-700">{formatDate(req.correctedOutTime, 'dd-MM-yyyy hh:mm a')}</p>
                </div>
              </div>
            </div>
            {req.remarks && <p className="mt-2 text-sm text-slate-600">{req.remarks}</p>}

            <div className="mt-3">
              <label className="mb-1 block text-xs font-medium text-slate-600">Approver Remarks</label>
              <textarea
                value={remarksById[req.id] ?? ''}
                onChange={(e) => setRemarksById((prev) => ({ ...prev, [req.id]: e.target.value }))}
                rows={2}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>

            <div className="mt-3 flex justify-end gap-2">
              <SecondaryButton onClick={() => decideMutation.mutate({ id: req.id, approve: false })} disabled={decideMutation.isPending}>
                Reject
              </SecondaryButton>
              <PrimaryButton onClick={() => decideMutation.mutate({ id: req.id, approve: true })} disabled={decideMutation.isPending}>
                <CheckCircle2 size={14} /> Approve
              </PrimaryButton>
            </div>
          </Card>
        ))}
      </div>
    </div>
  )
}
