import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Search } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { formatDate } from '../../../lib/date'
import { Badge, Card, EmptyState, ErrorState, LoadingState } from '../../../components/common/ui'
import { ReportExportButtons } from '../../../components/reports/ReportExportButtons'
import type { NecStatus, PimsReportFilter, SuspendedStaffReportResponse } from '../../../types/api'

const NEC_BADGE: Record<NecStatus, { label: string; tone: 'success' | 'danger' }> = {
  VERIFIED: { label: 'Verified - Payroll Clear', tone: 'success' },
  PENDING_VERIFICATION: { label: 'Pending Verification - Payroll Blocked', tone: 'danger' },
  NOT_SUBMITTED: { label: 'Missing - Payroll Blocked', tone: 'danger' },
}

function KpiCard({ label, value, tone }: { label: string; value: number; tone?: 'warning' | 'danger' }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3">
      <p className="text-xs text-slate-400">{label}</p>
      <p className={`mt-1 text-2xl font-semibold tabular-nums ${tone === 'danger' ? 'text-red-700' : tone === 'warning' ? 'text-amber-700' : 'text-slate-800'}`}>
        {value}
      </p>
    </div>
  )
}

/** PIMS Reporting Hub "Suspended Staff" tab. "View Dossier / Record NEC / Process Regularization" actions aren't wired here - SuspensionLifecycleService (submitAndVerifyNec, reviewSubsistenceAllowance, revokeAndRegularize) has no REST controller yet, only the service layer built this session; this tab is read-only reporting until that controller exists. */
export function SuspendedStaffTab() {
  const [subsistencePercentage, setSubsistencePercentage] = useState('')
  const [necStatus, setNecStatus] = useState('')
  const [station, setStation] = useState('')
  const [search, setSearch] = useState('')

  const { data, isLoading, isError } = useQuery({
    queryKey: ['suspended-staff', subsistencePercentage, necStatus, station, search],
    queryFn: async () =>
      (
        await apiClient.get<SuspendedStaffReportResponse>('/v1/reports/pims/suspended-staff', {
          params: {
            subsistencePercentage: subsistencePercentage || undefined,
            necStatus: necStatus || undefined,
            station: station || undefined,
            search: search || undefined,
          },
        })
      ).data,
  })

  const rows = data?.rows ?? []
  const filter: PimsReportFilter = { search: search || null }

  return (
    <div>
      <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <KpiCard label="Officers/Staff Under Suspension" value={data?.totalUnderSuspension ?? 0} />
        <KpiCard label="Pending NEC for Current Month" value={data?.pendingNecThisMonth ?? 0} tone="warning" />
        <KpiCard label="Overdue 90-Day Reviews" value={data?.pending90DayReviews ?? 0} tone="danger" />
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
            <select value={subsistencePercentage} onChange={(e) => setSubsistencePercentage(e.target.value)} className="rounded-md border border-slate-300 px-3 py-2 text-sm">
              <option value="">All Subsistence Rates</option>
              <option value="50.00">50%</option>
              <option value="75.00">75%</option>
              <option value="25.00">25%</option>
            </select>
            <select value={necStatus} onChange={(e) => setNecStatus(e.target.value)} className="rounded-md border border-slate-300 px-3 py-2 text-sm">
              <option value="">All NEC Statuses</option>
              <option value="VERIFIED">Verified</option>
              <option value="PENDING_VERIFICATION">Pending</option>
              <option value="NOT_SUBMITTED">Not Submitted</option>
            </select>
            <input
              value={station}
              onChange={(e) => setStation(e.target.value)}
              placeholder="Station..."
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <ReportExportButtons reportType="SUSPENDED_STAFF" filter={filter} />
        </div>
      </Card>

      <Card className="overflow-x-auto p-0">
        {isLoading && <LoadingState label="Loading suspended staff..." />}
        {isError && <ErrorState message="Could not load suspended staff." />}
        {rows.length === 0 && !isLoading && !isError && <EmptyState message="No suspension records match the current filters." />}
        {rows.length > 0 && (
          <table className="w-full min-w-[1100px] text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-2.5">Emp Code &amp; Name</th>
                <th className="px-4 py-2.5">HQ Station</th>
                <th className="px-4 py-2.5">Suspension Date</th>
                <th className="px-4 py-2.5 text-right">Days Elapsed</th>
                <th className="px-4 py-2.5 text-right">Subsistence %</th>
                <th className="px-4 py-2.5">90-Day Review</th>
                <th className="px-4 py-2.5">Current Month NEC</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.employeeId} className="border-t border-slate-100 hover:bg-slate-50">
                  <td className="px-4 py-2.5">
                    <span className="font-medium text-brand-forest">{r.empCode}</span>
                    <span className="block text-xs text-slate-500">{r.employeeName}</span>
                  </td>
                  <td className="px-4 py-2.5">{r.hqStation}</td>
                  <td className="px-4 py-2.5">{formatDate(r.effectiveFrom)}</td>
                  <td className="px-4 py-2.5 text-right tabular-nums">{r.daysUnderSuspension}</td>
                  <td className="px-4 py-2.5 text-right tabular-nums">{r.currentSubsistencePercentage.toFixed(2)}%</td>
                  <td className="px-4 py-2.5">
                    <Badge tone={r.isReviewOverdue ? 'danger' : 'success'}>{r.isReviewOverdue ? 'Overdue' : 'Reviewed / Not Due'}</Badge>
                  </td>
                  <td className="px-4 py-2.5">
                    <Badge tone={NEC_BADGE[r.currentMonthNecStatus].tone}>{NEC_BADGE[r.currentMonthNecStatus].label}</Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  )
}
