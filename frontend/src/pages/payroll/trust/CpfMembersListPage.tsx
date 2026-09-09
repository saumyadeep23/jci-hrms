import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { formatDate } from '../../../lib/date'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader } from '../../../components/common/ui'
import type { CpfTrustMemberResponse, EmployeeStatus, Page } from '../../../types/api'

const PAGE_SIZE = 25

const STATUS_OPTIONS: { value: EmployeeStatus | ''; label: string }[] = [
  { value: '', label: 'All Statuses' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'ON_PROBATION', label: 'On Probation' },
  { value: 'ON_LEAVE', label: 'On Leave' },
  { value: 'SUSPENDED', label: 'Suspended' },
  { value: 'RETIRED', label: 'Retired' },
  { value: 'RESIGNED', label: 'Resigned' },
  { value: 'DECEASED', label: 'Deceased' },
  { value: 'INACTIVE', label: 'Inactive' },
  { value: 'TERMINATED', label: 'Terminated' },
]

/**
 * Route: /payroll/trust/members - GET /api/v1/payroll/trust/members. Primary identifiers are CPF A/C No
 * and UAN, not name/code (those are still shown, but as supporting context). pendingSettlement surfaces
 * this module's core operating problem directly: CPF settlement routinely trails an employee's actual
 * separation date, sometimes by months - separated members without a DISBURSED terminal settlement show up
 * here, and settlementLagDays quantifies the gap once settlement does land.
 */
export function CpfMembersListPage() {
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [status, setStatus] = useState<EmployeeStatus | ''>('')
  const [pendingSettlementOnly, setPendingSettlementOnly] = useState(false)
  const [page, setPage] = useState(0)

  useEffect(() => {
    const handle = setTimeout(() => {
      setDebouncedSearch(search)
      setPage(0)
    }, 300)
    return () => clearTimeout(handle)
  }, [search])

  const { data, isLoading, isError } = useQuery({
    queryKey: ['cpf-trust-members', debouncedSearch, status, pendingSettlementOnly, page],
    queryFn: async () =>
      (
        await apiClient.get<Page<CpfTrustMemberResponse>>('/v1/payroll/trust/members', {
          params: {
            search: debouncedSearch || undefined,
            status: status || undefined,
            pendingSettlement: pendingSettlementOnly,
            page,
            size: PAGE_SIZE,
          },
        })
      ).data,
  })

  const totalElements = data?.totalElements ?? 0
  const totalPages = data?.totalPages ?? 0

  return (
    <div>
      <PageHeader
        title="CPF Trust Members' List"
        description="Every CPF Trust member, keyed by CPF A/C No and UAN - separated members without a disbursed settlement (or settled long after separation) are flagged directly."
      />

      <Card className="mb-6">
        <div className="flex flex-wrap items-end gap-4">
          <div className="min-w-[240px] flex-1">
            <label className="mb-1 block text-xs font-medium text-slate-600">Search</label>
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="CPF A/C No, UAN, code, or name..."
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Status</label>
            <select
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as EmployeeStatus | '')
                setPage(0)
              }}
              className="w-44 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none"
            >
              {STATUS_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>
          <label className="flex items-center gap-2 pb-2 text-sm text-slate-600">
            <input
              type="checkbox"
              checked={pendingSettlementOnly}
              onChange={(e) => {
                setPendingSettlementOnly(e.target.checked)
                setPage(0)
              }}
              className="rounded border-slate-300"
            />
            Pending settlement only
          </label>
        </div>
      </Card>

      <Card>
        {isLoading && <LoadingState label="Loading CPF Trust members..." />}
        {isError && <ErrorState message="Could not load the CPF Trust members' list." />}
        {data && data.content.length === 0 && <EmptyState message="No members match these filters." />}

        {data && data.content.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[1100px] border-collapse text-sm">
                <thead>
                  <tr className="border-b border-slate-300 text-left text-slate-500">
                    <th className="py-2 pr-3">CPF A/C No</th>
                    <th className="py-2 pr-3">UAN</th>
                    <th className="py-2 pr-3">Name</th>
                    <th className="py-2 pr-3">Code</th>
                    <th className="py-2 pr-3">Department</th>
                    <th className="py-2 pr-3">Status</th>
                    <th className="py-2 pr-3">Separation Date</th>
                    <th className="py-2 pr-3">Settlement Status</th>
                    <th className="py-2 pr-3">Settlement Date</th>
                    <th className="py-2 pl-3 text-right">Lag (days)</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((member) => (
                    <tr key={member.employeeId} className="border-b border-slate-100">
                      <td className="py-2 pr-3 font-medium text-slate-800">{member.cpfAcNo}</td>
                      <td className="py-2 pr-3 tabular-nums">{member.uanNo ?? '—'}</td>
                      <td className="py-2 pr-3">{member.fullName}</td>
                      <td className="py-2 pr-3 text-xs text-slate-500">{member.employeeCode}</td>
                      <td className="py-2 pr-3 text-xs text-slate-500">{member.departmentName ?? '—'}</td>
                      <td className="py-2 pr-3">
                        <Badge tone={member.status === 'ACTIVE' ? 'success' : 'neutral'}>{member.status}</Badge>
                      </td>
                      <td className="py-2 pr-3 text-xs text-slate-500">
                        {member.separationDate ? formatDate(member.separationDate) : '—'}
                      </td>
                      <td className="py-2 pr-3">
                        {member.cpfSettlementStatus ? (
                          <Badge tone={member.cpfSettlementStatus === 'DISBURSED' ? 'success' : 'warning'}>
                            {member.cpfSettlementStatus}
                          </Badge>
                        ) : member.separationDate ? (
                          <span className="inline-flex items-center gap-1 text-xs font-medium text-amber-700">
                            <AlertTriangle size={12} /> Not started
                          </span>
                        ) : (
                          '—'
                        )}
                      </td>
                      <td className="py-2 pr-3 text-xs text-slate-500">
                        {member.cpfSettlementDate ? formatDate(member.cpfSettlementDate) : '—'}
                      </td>
                      <td className="py-2 pl-3 text-right tabular-nums">
                        {member.settlementLagDays !== null ? (
                          <span className={member.settlementLagDays > 30 ? 'font-semibold text-red-600' : 'text-slate-600'}>
                            {member.settlementLagDays}
                          </span>
                        ) : (
                          '—'
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="mt-4 flex items-center justify-between text-xs text-slate-500">
              <span>
                Page {page + 1} of {Math.max(totalPages, 1)} · {totalElements} member{totalElements === 1 ? '' : 's'}
              </span>
              <div className="flex gap-2">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="rounded-md border border-slate-300 px-3 py-1.5 disabled:opacity-40"
                >
                  Previous
                </button>
                <button
                  type="button"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                  className="rounded-md border border-slate-300 px-3 py-1.5 disabled:opacity-40"
                >
                  Next
                </button>
              </div>
            </div>
          </>
        )}
      </Card>
    </div>
  )
}
