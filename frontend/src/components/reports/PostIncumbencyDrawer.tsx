import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { Modal } from '../common/Modal'
import { Badge, EmptyState, ErrorState, LoadingState } from '../common/ui'
import type { PostIncumbencyResponse } from '../../types/api'

/** Reporting hub drill-through: a read-only Post Incumbency ledger (full status-change actions live on /admin/posts). */
export function PostIncumbencyDrawer({ postId, postCode, onClose }: { postId: number; postCode: string; onClose: () => void }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['post-incumbency-history-drillthrough', postId],
    queryFn: async () => (await apiClient.get<PostIncumbencyResponse[]>(`/v1/posts/${postId}/incumbency-history`)).data,
  })

  return (
    <Modal title={`${postCode} - Incumbency Ledger`} onClose={onClose} maxWidthClassName="max-w-xl">
      {isLoading && <LoadingState label="Loading history..." />}
      {isError && <ErrorState message="Could not load incumbency history." />}
      {data && data.length === 0 && <EmptyState message="No incumbency records yet." />}
      {data && data.length > 0 && (
        <ul className="space-y-2">
          {data.map((entry) => (
            <li key={entry.id} className="flex items-center justify-between rounded-md border border-slate-200 p-3 text-sm">
              <div>
                <p className="font-medium text-slate-700">
                  {entry.employeeCode}
                  {entry.active && (
                    <Badge tone="success" className="ml-2">
                      Current
                    </Badge>
                  )}
                </p>
                <p className="text-xs text-slate-500">
                  {entry.assignmentType} - {formatDate(entry.startDate)} to {entry.endDate ? formatDate(entry.endDate) : 'present'}
                  {entry.orderReference && ` - Order: ${entry.orderReference}`}
                </p>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  )
}
