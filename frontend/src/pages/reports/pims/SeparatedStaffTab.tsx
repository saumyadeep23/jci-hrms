import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Search, UserRound, Wallet } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { formatDate } from '../../../lib/date'
import { Badge, Card, EmptyState, ErrorState, LoadingState } from '../../../components/common/ui'
import { ReportExportButtons } from '../../../components/reports/ReportExportButtons'
import { Employee360Drawer } from '../../../components/reports/Employee360Drawer'
import { TerminalSettlementModal } from '../../../components/employee/TerminalSettlementModal'
import type { Page, PimsReportFilter, SeparatedEmployeeResponse, SeparationType } from '../../../types/api'

const SEPARATION_BADGE_TONE: Record<string, 'success' | 'neutral' | 'warning' | 'danger'> = {
  SUPERANNUATION: 'neutral',
  RESIGNATION: 'warning',
  VRS: 'neutral',
  DECEASED: 'danger',
  TERMINATED: 'danger',
}

function money(value: number | null): string {
  if (value === null) return '—'
  return `₹${value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

/** PIMS "Separated Staff" tab - PIMS_SPEC.md exit-lifecycle task. */
export function SeparatedStaffTab() {
  const [search, setSearch] = useState('')
  const [year, setYear] = useState('')
  const [profileEmployeeId, setProfileEmployeeId] = useState<number | null>(null)
  const [settlementEmployee, setSettlementEmployee] = useState<SeparatedEmployeeResponse | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['separated-staff', search],
    queryFn: async () =>
      (
        // No `status` param: SeparatedEmployeeDirectoryService's SQL already restricts to the 4
        // terminal statuses unconditionally - `status` there is for narrowing to ONE specific
        // status (e.g. "RETIRED"), not a SEPARATED/ALL toggle, so sending the literal string
        // "SEPARATED" made the query's exact-match filter reject every real row.
        await apiClient.get<Page<SeparatedEmployeeResponse>>('/v1/employees/separated', {
          params: { search: search || undefined, size: 500 },
        })
      ).data.content,
  })

  const years = useMemo(() => {
    const set = new Set((data ?? []).map((r) => r.separationDate?.slice(0, 4)).filter((y): y is string => Boolean(y)))
    return Array.from(set).sort().reverse()
  }, [data])

  const rows = useMemo(() => (year ? (data ?? []).filter((r) => r.separationDate?.startsWith(year)) : data ?? []), [data, year])

  const filter: PimsReportFilter = { search: search || null }

  return (
    <div>
      <Card className="mb-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-3">
            <div className="relative">
              <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search by Code or Name..."
                className="w-64 rounded-md border border-slate-300 py-2 pl-9 pr-3 text-sm"
              />
            </div>
            <select value={year} onChange={(e) => setYear(e.target.value)} className="rounded-md border border-slate-300 px-3 py-2 text-sm">
              <option value="">All Years</option>
              {years.map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </select>
          </div>
          <ReportExportButtons reportType="SEPARATED_STAFF" filter={filter} />
        </div>
      </Card>

      <Card className="overflow-x-auto p-0">
        {isLoading && <LoadingState label="Loading separated staff..." />}
        {isError && <ErrorState message="Could not load separated staff." />}
        {rows.length === 0 && !isLoading && !isError && <EmptyState message="No separated employees match the current search/filters." />}
        {rows.length > 0 && (
          <table className="w-full min-w-[1100px] text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-2.5">Emp Code</th>
                <th className="px-4 py-2.5">CPF A/C No</th>
                <th className="px-4 py-2.5">Full Name</th>
                <th className="px-4 py-2.5">Separation Reason</th>
                <th className="px-4 py-2.5">Release Date</th>
                <th className="px-4 py-2.5">Last Designation</th>
                <th className="px-4 py-2.5 text-right">Last Basic Pay</th>
                <th className="px-4 py-2.5">Clearance Status</th>
                <th className="px-4 py-2.5">Settlement Status</th>
                <th className="px-4 py-2.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id} className="border-t border-slate-100 hover:bg-slate-50">
                  <td className="px-4 py-2.5 font-medium text-brand-forest">{r.employeeCode}</td>
                  <td className="px-4 py-2.5">{r.cpfAcNo ?? '—'}</td>
                  <td className="px-4 py-2.5">{r.fullName}</td>
                  <td className="px-4 py-2.5">
                    {r.separationType ? <Badge tone={SEPARATION_BADGE_TONE[r.separationType] ?? 'neutral'}>{r.separationType}</Badge> : '—'}
                  </td>
                  <td className="px-4 py-2.5">{r.separationDate ? formatDate(r.separationDate) : '—'}</td>
                  <td className="px-4 py-2.5 text-slate-500">{r.lastDesignation ?? '—'}</td>
                  <td className="px-4 py-2.5 text-right tabular-nums">{money(r.lastBasicPay)}</td>
                  <td className="px-4 py-2.5 text-slate-500">{r.clearanceStatus ?? '—'}</td>
                  <td className="px-4 py-2.5 text-slate-500">{r.settlementStatus ?? '—'}</td>
                  <td className="px-4 py-2.5">
                    <div className="flex justify-end gap-1">
                      <button
                        type="button"
                        onClick={() => setProfileEmployeeId(r.id)}
                        title="View 360 Profile"
                        className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-slate-500 hover:bg-slate-100"
                      >
                        <UserRound size={13} /> Profile
                      </button>
                      <button
                        type="button"
                        disabled={!r.separationType || !r.separationDate}
                        title={!r.separationType || !r.separationDate ? 'No separation type/date on file - run Exit Formalities first' : 'Terminal Settlement Sheet'}
                        onClick={() => setSettlementEmployee(r)}
                        className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-brand-forest hover:bg-slate-100 disabled:opacity-30"
                      >
                        <Wallet size={13} /> Settlement
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      {profileEmployeeId !== null && <Employee360Drawer employeeId={profileEmployeeId} onClose={() => setProfileEmployeeId(null)} />}

      {settlementEmployee && settlementEmployee.separationType && settlementEmployee.separationDate && (
        <TerminalSettlementModal
          employeeId={settlementEmployee.id}
          employeeName={settlementEmployee.fullName}
          separationType={settlementEmployee.separationType as SeparationType}
          separationDate={settlementEmployee.separationDate}
          onClose={() => setSettlementEmployee(null)}
        />
      )}
    </div>
  )
}
