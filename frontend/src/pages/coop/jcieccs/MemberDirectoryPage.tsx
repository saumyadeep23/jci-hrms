import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { formatDate } from '../../../lib/date'
import { Badge, ErrorState, LoadingState, PageHeader } from '../../../components/common/ui'
import { describeApiError } from '../../../lib/apiError'
import { MemberActionsMenu } from './MemberActionsMenu'
import { MemberStatusChangeModal } from './MemberStatusChangeModal'
import type { JciEccsMemberResponse, JciEccsMembershipStatus } from '../../../types/api'

const STATUS_OPTIONS: { value: JciEccsMembershipStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'SUSPENDED', label: 'Suspended' },
  { value: 'CLOSED', label: 'Closed / Cessation' },
]

/** Route: /jcieccs/members/directory - GET /api/jcieccs/members (JciEccsMemberController), enriched
 * server-side with the employee's full name and place of posting (Regional Office / DPC, joined via
 * employee_id) - Bye-law 5 confines society membership to Head Office-posted employees, so posting is
 * shown directly in the listing for eligibility auditing. */
export function MemberDirectoryPage() {
  const [status, setStatus] = useState<JciEccsMembershipStatus | 'ALL'>('ALL')
  const [statusChangeTarget, setStatusChangeTarget] = useState<JciEccsMemberResponse | null>(null)

  const membersQuery = useQuery({
    queryKey: ['jcieccs-members'],
    queryFn: async () => (await apiClient.get<JciEccsMemberResponse[]>('/jcieccs/members')).data,
  })

  const filtered = useMemo(
    () => (membersQuery.data ?? []).filter((m) => status === 'ALL' || m.membershipStatus === status),
    [membersQuery.data, status],
  )

  return (
    <div>
      <PageHeader title="Member Directory" description="JCIECCS co-operative membership roll - active, suspended, and closed members" />

      <div className="mb-4 flex gap-2">
        {STATUS_OPTIONS.map((opt) => (
          <button
            key={opt.value}
            onClick={() => setStatus(opt.value)}
            className={`rounded-md border px-3 py-1.5 text-xs font-medium ${
              status === opt.value ? 'border-brand-forest bg-brand-forest text-white' : 'border-slate-300 bg-white text-slate-600'
            }`}
          >
            {opt.label}
          </button>
        ))}
      </div>

      {membersQuery.isLoading && <LoadingState label="Loading members..." />}
      {membersQuery.isError && <ErrorState message={describeApiError(membersQuery.error, 'Could not load JCIECCS members.')} />}

      {membersQuery.data && (
        <div className="overflow-x-auto rounded-md border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-3 py-2">Membership Code</th>
                <th className="px-3 py-2">Member Name</th>
                <th className="px-3 py-2">Employee ID</th>
                <th className="px-3 py-2">Place of Posting</th>
                <th className="px-3 py-2">Membership Date</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2 text-right">Share</th>
                <th className="px-3 py-2">Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((m) => (
                <tr key={m.id} className="border-b border-slate-100 last:border-0">
                  <td className="px-3 py-2 font-medium text-slate-800">{m.membershipCode}</td>
                  <td className="px-3 py-2 text-slate-700">{m.memberName ?? '--'}</td>
                  <td className="px-3 py-2 text-slate-500">{m.employeeId}</td>
                  <td className="px-3 py-2 text-slate-500">{m.placeOfPosting ?? '--'}</td>
                  <td className="px-3 py-2 text-slate-500">{formatDate(m.membershipDate)}</td>
                  <td className="px-3 py-2">
                    <Badge tone={m.membershipStatus === 'ACTIVE' ? 'success' : m.membershipStatus === 'SUSPENDED' ? 'warning' : 'neutral'}>
                      {m.membershipStatus}
                    </Badge>
                  </td>
                  <td className="px-3 py-2 text-right">{m.shareBalance.toFixed(2)}</td>
                  <td className="px-3 py-2">
                    <MemberActionsMenu member={m} onChangeStatus={() => setStatusChangeTarget(m)} />
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-3 py-6 text-center text-sm text-slate-400">
                    No members match this filter.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {statusChangeTarget && <MemberStatusChangeModal member={statusChangeTarget} onClose={() => setStatusChangeTarget(null)} />}
    </div>
  )
}
