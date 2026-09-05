import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Download, Printer } from 'lucide-react'
import { apiClient } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { formatDate } from '../../lib/date'
import { RegularizationModal } from './RegularizationModal'
import { Card, EmptyState, ErrorState, LoadingState, SecondaryButton } from '../common/ui'
import type { AttendanceDetailStatus, DailyAttendanceDetailResponse, LeaveLedgerEntryResponse } from '../../types/api'

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

/** Mirrors AttendanceAggregationService.MONTHLY_CONCESSION_LIMIT on the backend. */
const MONTHLY_CONCESSION_LIMIT = 2

/** Mirrors DailyAttendance.toCoarseStatus() - which detail statuses collapse to a full PRESENT day. */
const COARSE_PRESENT_STATUSES: AttendanceDetailStatus[] = [
  'PRESENT', 'GRACE_APPLIED', 'LATE_SHORT_HOURS', 'REQUIRES_REGULARIZATION', 'UNAUTHORIZED_LATE', 'IN_PROGRESS',
]

/** Mirrors AttendanceAggregationService.CONCESSION_STATUSES - drives row highlighting and the "Grace / Concessions Utilized" stat card only, not button visibility (see REGULARIZABLE_STATUSES). */
const CONCESSION_STATUSES: AttendanceDetailStatus[] = ['REQUIRES_REGULARIZATION', 'UNAUTHORIZED_LATE']

/** Mirrors AttendanceRegularizationService.REGULARIZABLE_STATUSES - which days can actually be regularized: the concession statuses plus a genuinely missed punch (full or half day). */
const REGULARIZABLE_STATUSES: AttendanceDetailStatus[] = ['REQUIRES_REGULARIZATION', 'UNAUTHORIZED_LATE', 'ABSENT', 'HALF_DAY_ABSENT']

const DETAIL_STATUS_STYLES: Record<AttendanceDetailStatus, string> = {
  IN_PROGRESS: 'bg-sky-100 text-sky-800 border-sky-300',
  PRESENT: 'bg-emerald-100 text-emerald-800 border-emerald-300',
  GRACE_APPLIED: 'bg-teal-100 text-teal-800 border-teal-300',
  ON_LEAVE: 'bg-blue-100 text-blue-800 border-blue-300',
  HALF_DAY_PRESENT: 'bg-indigo-100 text-indigo-800 border-indigo-300',
  LATE_SHORT_HOURS: 'bg-amber-100 text-amber-800 border-amber-300',
  REQUIRES_REGULARIZATION: 'bg-amber-100 text-amber-800 border-amber-300',
  UNAUTHORIZED_LATE: 'bg-rose-100 text-rose-800 border-rose-300',
  ABSENT: 'bg-rose-100 text-rose-800 border-rose-300',
  // Not specified by the design spec - extended in the same spirit (amber for a
  // shortfall, rose for a full miss, slate for non-actionable calendar days).
  HALF_DAY_SHORT: 'bg-amber-100 text-amber-800 border-amber-300',
  HALF_DAY_ABSENT: 'bg-rose-100 text-rose-800 border-rose-300',
  HOLIDAY: 'bg-slate-100 text-slate-700 border-slate-300',
  WEEKOFF: 'bg-slate-100 text-slate-700 border-slate-300',
  ON_TOUR: 'bg-sky-100 text-sky-800 border-sky-300',
}

const DETAIL_STATUS_LABELS: Record<AttendanceDetailStatus, string> = {
  IN_PROGRESS: 'In Progress',
  PRESENT: 'Present',
  GRACE_APPLIED: 'Grace Applied',
  LATE_SHORT_HOURS: 'Late / Short Hours',
  REQUIRES_REGULARIZATION: 'Requires Regularization',
  UNAUTHORIZED_LATE: 'Unauthorized Late',
  HALF_DAY_PRESENT: 'Half Day Present',
  HALF_DAY_SHORT: 'Half Day Short',
  HALF_DAY_ABSENT: 'Half Day Absent',
  ON_LEAVE: 'On Leave',
  HOLIDAY: 'Holiday',
  WEEKOFF: 'Week Off',
  ABSENT: 'Absent',
  ON_TOUR: '✈️ On Tour / OD',
}

function DetailStatusBadge({ row }: { row: DailyAttendanceDetailResponse }) {
  const tooltip =
    row.detailStatus === 'ON_TOUR' && (row.tourDestination || row.tourRequestNumber)
      ? [row.tourDestination ? `Destination: ${row.tourDestination}` : null, row.tourRequestNumber ? `Order Ref: ${row.tourRequestNumber}` : null]
          .filter(Boolean)
          .join(' | ')
      : undefined
  return (
    <span
      title={tooltip}
      className={`inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium ${DETAIL_STATUS_STYLES[row.detailStatus]}`}
    >
      {DETAIL_STATUS_LABELS[row.detailStatus]}
    </span>
  )
}

function formatServiceHours(hours: number | null): string {
  return hours === null ? '—' : `${hours.toFixed(2)} Hrs`
}

function buildCsv(rows: DailyAttendanceDetailResponse[]): string {
  const header = 'Date,In-Time,Out-Time,Service Hours,Status,Remarks\n'
  const lines = rows
    .map((r) =>
      [
        formatDate(r.date),
        r.inTime ?? '',
        r.outTime ?? '',
        formatServiceHours(r.totalWorkingHours),
        DETAIL_STATUS_LABELS[r.detailStatus],
        (r.remarks ?? '').replace(/,/g, ';'),
      ].join(','),
    )
    .join('\n')
  return header + lines
}

function downloadCsv(rows: DailyAttendanceDetailResponse[], year: number, month: number) {
  const blob = new Blob([buildCsv(rows)], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `attendance-${year}-${String(month).padStart(2, '0')}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

/**
 * Authoritative counterpart to the old on-the-fly punch history: this calls
 * the aggregation engine (POST - it evaluates AND persists, not just reads)
 * rather than /attendance/my-history. Viewing this table actively
 * (re-)evaluates the selected month against the JCI circular's grace/
 * concession rules and CCS leave data every time - including, idempotently,
 * any auto-debit consequence (AttendanceLeaveDeductionService) - that's the
 * intended behavior, not a side effect to avoid.
 */
export function AttendanceDetailTable() {
  const { employeeId } = useAuth()
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [regularizing, setRegularizing] = useState<DailyAttendanceDetailResponse | null>(null)

  const yearOptions = Array.from({ length: 4 }, (_, i) => now.getFullYear() - i)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['attendance-aggregation', year, month],
    queryFn: async () =>
      (
        await apiClient.post<DailyAttendanceDetailResponse[]>('/attendance/aggregation/evaluate', null, {
          params: { year, month },
        })
      ).data,
  })

  const { data: ledgerEntries } = useQuery({
    queryKey: ['leave-ledger-entries-mine'],
    queryFn: async () => (await apiClient.get<LeaveLedgerEntryResponse[]>('/leave-ledger-entries')).data,
  })

  const monthPrefix = `${year}-${String(month).padStart(2, '0')}`
  const concessionsUsed = data?.filter((r) => CONCESSION_STATUSES.includes(r.detailStatus)).length ?? 0
  const presentDays = data?.filter((r) => COARSE_PRESENT_STATUSES.includes(r.detailStatus)).length ?? 0
  const leaveDebitsThisMonth = (ledgerEntries ?? [])
    .filter((e) => e.entryDate.startsWith(monthPrefix))
    .reduce((sum, e) => sum + Math.abs(e.deltaDays), 0)

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Card className="no-print">
          <p className="text-xs text-slate-400">Grace / Concessions Utilized</p>
          <p className="mt-1 text-2xl font-semibold text-slate-800">
            {concessionsUsed} <span className="text-base font-normal text-slate-400">/ {MONTHLY_CONCESSION_LIMIT}</span>
          </p>
        </Card>
        <Card className="no-print">
          <p className="text-xs text-slate-400">Total Present Days</p>
          <p className="mt-1 text-2xl font-semibold text-slate-800">{presentDays}</p>
        </Card>
        <Card className="no-print">
          <p className="text-xs text-slate-400">Leave Debits Recorded</p>
          <p className="mt-1 text-2xl font-semibold text-slate-800">
            {leaveDebitsThisMonth} <span className="text-base font-normal text-slate-400">days</span>
          </p>
        </Card>
      </div>

      <Card className="print-sheet">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3 no-print">
          <h2 className="text-sm font-semibold text-slate-700">Attendance History</h2>
          <div className="flex flex-wrap items-center gap-2">
            <select
              value={month}
              onChange={(e) => setMonth(Number(e.target.value))}
              className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
            >
              {MONTH_NAMES.map((name, i) => (
                <option key={name} value={i + 1}>
                  {name}
                </option>
              ))}
            </select>
            <select
              value={year}
              onChange={(e) => setYear(Number(e.target.value))}
              className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
            >
              {yearOptions.map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </select>
            <SecondaryButton onClick={() => data && downloadCsv(data, year, month)} disabled={!data || data.length === 0}>
              <Download size={14} /> CSV
            </SecondaryButton>
            <SecondaryButton onClick={() => window.print()} disabled={!data || data.length === 0}>
              <Printer size={14} /> Print
            </SecondaryButton>
          </div>
        </div>

        <p className="mb-3 hidden text-sm font-medium text-slate-600 print:block">
          Attendance Summary - {MONTH_NAMES[month - 1]} {year}
        </p>

        {isLoading && <LoadingState label="Evaluating attendance..." />}
        {isError && <ErrorState message="Could not evaluate attendance history." />}
        {data && data.length === 0 && <EmptyState message="This month hasn't started yet." />}

        {data && data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Date</th>
                  <th className="py-2 pr-3">In-Time</th>
                  <th className="py-2 pr-3">Out-Time</th>
                  <th className="py-2 pr-3 text-right">Service Hours</th>
                  <th className="py-2 pl-3">Status</th>
                  <th className="py-2 pl-3">Remarks</th>
                  <th className="py-2 pl-3 no-print">Action</th>
                </tr>
              </thead>
              <tbody>
                {data.map((row) => (
                  <tr
                    key={row.date}
                    className={`border-b border-slate-100 ${CONCESSION_STATUSES.includes(row.detailStatus) ? 'bg-amber-50/40' : ''}`}
                  >
                    <td className="py-2 pr-3 font-medium">{formatDate(row.date)}</td>
                    <td className="py-2 pr-3 tabular-nums">{row.inTime ?? '--:--:--'}</td>
                    <td className="py-2 pr-3 tabular-nums">{row.outTime ?? '--:--:--'}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{formatServiceHours(row.totalWorkingHours)}</td>
                    <td className="py-2 pl-3">
                      <DetailStatusBadge row={row} />
                    </td>
                    <td className="py-2 pl-3 text-xs text-slate-500">{row.remarks ?? '—'}</td>
                    <td className="py-2 pl-3 no-print">
                      {REGULARIZABLE_STATUSES.includes(row.detailStatus) && employeeId !== null && (
                        <button
                          type="button"
                          onClick={() => setRegularizing(row)}
                          className="rounded border border-emerald-300 bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-700 hover:bg-emerald-100"
                        >
                          Regularise
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {regularizing && employeeId !== null && (
        <RegularizationModal employeeId={employeeId} row={regularizing} onClose={() => setRegularizing(null)} />
      )}
    </div>
  )
}
