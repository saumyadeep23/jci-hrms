import { Fragment, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ChevronDown, ChevronRight, Search } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { formatDate } from '../../../lib/date'
import { Badge, Card, EmptyState, ErrorState, LoadingState } from '../../../components/common/ui'
import { ReportExportButtons } from '../../../components/reports/ReportExportButtons'
import type { DeputationDirection, DeputedStaffReportDto, DeputedStaffReportResponse, PimsReportFilter } from '../../../types/api'

const DIRECTION_LABEL: Record<DeputationDirection, string> = {
  DEPUTATION_OUT: 'Deputed Out',
  DEPUTATION_IN: 'Borrowed In',
}

const PAY_OPTION_LABEL: Record<string, string> = {
  PARENT_CADRE_BASIC_PLUS_DEP_ALLOWANCE: 'Parent Cadre Pay + Dep. Allowance',
  FOREIGN_POST_PAY_SCALE: 'Foreign Post Pay Scale',
}

function money(value: number): string {
  return `₹${value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function KpiCard({ label, value, tone }: { label: string; value: number; tone?: 'warning' }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3">
      <p className="text-xs text-slate-400">{label}</p>
      <p className={`mt-1 text-2xl font-semibold tabular-nums ${tone === 'warning' ? 'text-amber-700' : 'text-slate-800'}`}>{value}</p>
    </div>
  )
}

function DetailRow({ row }: { row: DeputedStaffReportDto }) {
  return (
    <tr>
      <td colSpan={9} className="bg-slate-50 p-3">
        <div className="grid grid-cols-2 gap-3 text-xs sm:grid-cols-4">
          <div>
            <p className="text-slate-400">Pay Option</p>
            <p className="font-medium text-slate-700">{PAY_OPTION_LABEL[row.payOption] ?? row.payOption}</p>
          </div>
          <div>
            <p className="text-slate-400">Extension Valid Up To</p>
            <p className="font-medium text-slate-700">{row.extensionValidUpTo ? formatDate(row.extensionValidUpTo) : '—'}</p>
          </div>
          <div>
            <p className="text-slate-400">LSPC Borne By</p>
            <p className="font-medium text-slate-700">{row.lspcApplicable ? row.lspcBorneBy.replace('_', ' ') : 'Not Applicable'}</p>
          </div>
          <div>
            <p className="text-slate-400">LSPC Monthly Rate</p>
            <p className="font-medium text-slate-700">{row.lspcApplicable ? money(row.lspcMonthlyRate) : '—'}</p>
          </div>
        </div>
      </td>
    </tr>
  )
}

/** PIMS Reporting Hub "Deputed Staff" tab. */
export function DeputedStaffTab() {
  const [direction, setDirection] = useState<'ALL' | DeputationDirection>('ALL')
  const [organizationType, setOrganizationType] = useState('')
  const [station, setStation] = useState('')
  const [search, setSearch] = useState('')
  const [expanded, setExpanded] = useState<Set<number>>(new Set())

  const { data, isLoading, isError } = useQuery({
    queryKey: ['deputed-staff', direction, organizationType, station, search],
    queryFn: async () =>
      (
        await apiClient.get<DeputedStaffReportResponse>('/v1/reports/pims/deputed-staff', {
          params: {
            direction: direction === 'ALL' ? undefined : direction,
            organizationType: organizationType || undefined,
            station: station || undefined,
            search: search || undefined,
          },
        })
      ).data,
  })

  const rows = data?.rows ?? []
  const filter: PimsReportFilter = { search: search || null }

  function toggle(employeeId: number) {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(employeeId)) next.delete(employeeId)
      else next.add(employeeId)
      return next
    })
  }

  return (
    <div>
      <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <KpiCard label="Active Deputation Out" value={data?.totalDeputedOut ?? 0} />
        <KpiCard label="Active Deputation In" value={data?.totalDeputedIn ?? 0} />
        <KpiCard label="Repatriations Due in 90 Days" value={data?.dueForRepatriationThisQuarter ?? 0} tone="warning" />
      </div>

      <Card className="mb-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-3">
            <div className="relative">
              <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search by Code or Name..."
                className="w-56 rounded-md border border-slate-300 py-2 pl-9 pr-3 text-sm"
              />
            </div>
            <select
              value={direction}
              onChange={(e) => setDirection(e.target.value as 'ALL' | DeputationDirection)}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="ALL">All Directions</option>
              <option value="DEPUTATION_OUT">Deputed Out</option>
              <option value="DEPUTATION_IN">Borrowed In</option>
            </select>
            <input
              value={organizationType}
              onChange={(e) => setOrganizationType(e.target.value)}
              placeholder="Organization Type..."
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            <input
              value={station}
              onChange={(e) => setStation(e.target.value)}
              placeholder="Station..."
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <ReportExportButtons reportType="DEPUTED_STAFF" filter={filter} />
        </div>
      </Card>

      <Card className="overflow-x-auto p-0">
        {isLoading && <LoadingState label="Loading deputed staff..." />}
        {isError && <ErrorState message="Could not load deputed staff." />}
        {rows.length === 0 && !isLoading && !isError && <EmptyState message="No deputation records match the current filters." />}
        {rows.length > 0 && (
          <table className="w-full min-w-[1200px] text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-2.5" />
                <th className="px-4 py-2.5">Emp Code &amp; Name</th>
                <th className="px-4 py-2.5">Direction</th>
                <th className="px-4 py-2.5">Organization</th>
                <th className="px-4 py-2.5">Station</th>
                <th className="px-4 py-2.5">Period</th>
                <th className="px-4 py-2.5">Pay Option</th>
                <th className="px-4 py-2.5 text-right">Dep. Allowance</th>
                <th className="px-4 py-2.5">LSPC</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <Fragment key={r.employeeId}>
                  <tr className="border-t border-slate-100 hover:bg-slate-50">
                    <td className="px-4 py-2.5">
                      <button type="button" onClick={() => toggle(r.employeeId)} className="text-slate-500 hover:text-slate-700">
                        {expanded.has(r.employeeId) ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
                      </button>
                    </td>
                    <td className="px-4 py-2.5">
                      <span className="font-medium text-brand-forest">{r.empCode}</span>
                      <span className="block text-xs text-slate-500">{r.employeeName}</span>
                    </td>
                    <td className="px-4 py-2.5">
                      <Badge tone={r.deputationDirection === 'DEPUTATION_OUT' ? 'warning' : 'brand'}>{DIRECTION_LABEL[r.deputationDirection]}</Badge>
                    </td>
                    <td className="px-4 py-2.5">
                      {r.organizationName}
                      <span className="block text-xs text-slate-400">{r.organizationType}</span>
                    </td>
                    <td className="px-4 py-2.5">
                      {r.postingStation}
                      {r.isSameStation && <span className="ml-1 text-xs text-slate-400">(Same Station)</span>}
                    </td>
                    <td className="px-4 py-2.5 text-xs">
                      {formatDate(r.periodFrom)} – {formatDate(r.periodTo)}
                    </td>
                    <td className="px-4 py-2.5 text-xs text-slate-500">{PAY_OPTION_LABEL[r.payOption] ?? r.payOption}</td>
                    <td className="px-4 py-2.5 text-right tabular-nums">
                      {r.deputationAllowanceRate > 0 ? (
                        <>
                          {r.deputationAllowanceRate.toFixed(2)}%
                          <span className="block text-xs text-slate-400">Cap {money(r.deputationAllowanceCap)}</span>
                        </>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td className="px-4 py-2.5">
                      <Badge tone={r.lspcApplicable ? 'success' : 'neutral'}>{r.lspcApplicable ? 'Applicable' : 'N/A'}</Badge>
                    </td>
                  </tr>
                  {expanded.has(r.employeeId) && <DetailRow row={r} />}
                </Fragment>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  )
}
