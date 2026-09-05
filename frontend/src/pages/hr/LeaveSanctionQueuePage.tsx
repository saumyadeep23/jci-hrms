import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../components/common/ui'
import type { LeaveApplicationResponse, Page } from '../../types/api'

const STATUS_TONE: Record<string, 'neutral' | 'success' | 'warning' | 'danger'> = {
  DRAFT: 'neutral',
  PENDING_APPROVAL: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  CANCELLED: 'neutral',
}

export function LeaveSanctionQueuePage() {
  const queryClient = useQueryClient()

  const { data, isLoading, isError } = useQuery({
    queryKey: ['leave-applications'],
    queryFn: async () => (await apiClient.get<Page<LeaveApplicationResponse>>('/leave-applications', { params: { size: 50 } })).data,
  })

  const decide = useMutation({
    mutationFn: async ({ id, action }: { id: number; action: 'approve' | 'reject' }) =>
      (await apiClient.post(`/leave-applications/${id}/${action}`)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['leave-applications'] }),
  })

  return (
    <div>
      <PageHeader title="Leave Sanction Queue" description="Review and approve/reject pending leave applications" />

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load leave applications." />}
      {data && data.content.length === 0 && <EmptyState message="No leave applications yet." />}

      <div className="space-y-3">
        {data?.content.map((application) => (
          <Card key={application.id} className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="font-medium text-slate-800">
                {application.employeeCode} · {application.leaveTypeCode}
              </p>
              <p className="text-sm text-slate-500">
                {formatDate(application.startDate)} to {formatDate(application.endDate)} ({application.totalDays} days) -{' '}
                {application.reason}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <Badge tone={STATUS_TONE[application.status] ?? 'neutral'}>{application.status.replace(/_/g, ' ')}</Badge>
              {application.status === 'PENDING_APPROVAL' && (
                <>
                  <SecondaryButton onClick={() => decide.mutate({ id: application.id, action: 'reject' })} disabled={decide.isPending}>
                    Reject
                  </SecondaryButton>
                  <PrimaryButton onClick={() => decide.mutate({ id: application.id, action: 'approve' })} disabled={decide.isPending}>
                    Approve
                  </PrimaryButton>
                </>
              )}
            </div>
          </Card>
        ))}
      </div>
    </div>
  )
}
