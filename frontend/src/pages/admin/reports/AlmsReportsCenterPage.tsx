import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Download, Lock, Unlock } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { formatDate } from '../../../lib/date'
import { DateField } from '../../../components/common/DateField'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader } from '../../../components/common/ui'
import type {
  CcsForm1Response,
  Circular53ComplianceRow,
  DpcResponse,
  EmployeeResponse,
  EncashmentRegisterRow,
  MusterRollRow,
  Page,
  PayrollCutoffFeedRow,
  RegionalOfficeResponse,
} from '../../../types/api'

type TabKey = 'muster' | 'payroll' | 'circular53' | 'ccsForm1' | 'encashment'
const TABS: { key: TabKey; label: string }[] = [
  { key: 'muster', label: 'Statutory Muster Roll (Form II)' },
  { key: 'payroll', label: '25th Payroll Cutoff Feed' },
  { key: 'circular53', label: 'Circular 53 Concession Compliance' },
  { key: 'ccsForm1', label: 'CCS Form 1 Leave Register' },
  { key: 'encashment', label: 'In-Service Encashment Register' },
]

const MUSTER_CODE_TONE: Record<string, string> = {
  P: 'bg-emerald-100 text-emerald-800',
  TR: 'bg-emerald-100 text-emerald-800',
  'HD-CL': 'bg-emerald-100 text-emerald-800',
  WO: 'bg-slate-200 text-slate-600',
  GH: 'bg-slate-200 text-slate-600',
  CL: 'bg-sky-100 text-sky-800',
  EL: 'bg-sky-100 text-sky-800',
  RH: 'bg-sky-100 text-sky-800',
  HPL: 'bg-sky-100 text-sky-800',
  COMM: 'bg-sky-100 text-sky-800',
  LWP: 'bg-red-100 text-red-700',
  ABS: 'bg-red-100 text-red-700',
}

/** Local Y/M/D -> "yyyy-MM-dd", with no pass through UTC (unlike `.toISOString()`, which reinterprets a local-midnight Date in UTC and can shift the calendar date by one day for any IST browser - this system is IST-only, see CLAUDE.md). */
function toIsoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/**
 * Chronological day-of-month columns for the muster roll's day matrix,
 * spanning the (up to two-month) cycle. Deliberately not derived from
 * `Object.keys(row.dailyPunches)` - those keys are bare day-of-month numbers
 * ("1".."31"), and JS always iterates numeric-string object keys in
 * ascending numeric order regardless of insertion order, which would
 * silently interleave the cycle's two months (e.g. 26-31 before 1-25)
 * instead of showing them in real chronological order.
 */
function musterCycleColumns(startIso: string, endIso: string): { label: string; iso: string }[] {
  const [sy, sm, sd] = startIso.split('-').map(Number)
  const [ey, em, ed] = endIso.split('-').map(Number)
  if (!sy || !sm || !sd || !ey || !em || !ed) return []
  const end = new Date(ey, em - 1, ed)
  const columns: { label: string; iso: string }[] = []
  for (let cursor = new Date(sy, sm - 1, sd); cursor <= end; cursor = new Date(cursor.getFullYear(), cursor.getMonth(), cursor.getDate() + 1)) {
    columns.push({ label: String(cursor.getDate()), iso: toIsoDate(cursor) })
  }
  return columns
}

/** 26th of the prior month through the 25th of the reference month - ALMS's payroll cutoff convention (V36 migration comment). On/after the 26th, the window shifts forward a month. */
function currentCycle(referenceDate = new Date()): { start: string; end: string } {
  const day = referenceDate.getDate()
  const cycleEnd = new Date(referenceDate.getFullYear(), referenceDate.getMonth() + (day >= 26 ? 1 : 0), 25)
  const cycleStart = new Date(cycleEnd.getFullYear(), cycleEnd.getMonth() - 1, 26)
  return { start: toIsoDate(cycleStart), end: toIsoDate(cycleEnd) }
}

function downloadBlob(data: BlobPart, filename: string, mime: string) {
  const blob = new Blob([data], { type: mime })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

/** ALMS Enterprise Reporting and Analytics Workbench (Phase 4), /admin/reports/alms - guarded by HR_ADMIN/FINANCE_ADMIN/SUPER_ADMIN at the route level (see App.tsx). */
export function AlmsReportsCenterPage() {
  const cycle = useMemo(() => currentCycle(), [])
  const [startDate, setStartDate] = useState(cycle.start)
  const [endDate, setEndDate] = useState(cycle.end)
  const [officeId, setOfficeId] = useState('')
  const [cadre, setCadre] = useState('')
  const [search, setSearch] = useState('')
  const [tab, setTab] = useState<TabKey>('muster')
  const [selectedEmployeeId, setSelectedEmployeeId] = useState('')
  const [ccsYear, setCcsYear] = useState(new Date().getFullYear());

  const yearMonth = startDate.slice(0, 7)
  const officeIdNum = officeId ? Number(officeId) : undefined

  const rosQuery = useQuery({
    queryKey: ['ros-for-alms-reports'],
    queryFn: async () => (await apiClient.get<Page<RegionalOfficeResponse>>('/regional-offices', { params: { size: 200 } })).data.content,
  })
  const dpcsQuery = useQuery({
    queryKey: ['dpcs-for-alms-reports'],
    queryFn: async () => (await apiClient.get<Page<DpcResponse>>('/dpcs', { params: { size: 200 } })).data.content,
  })
  const employeesQuery = useQuery({
    queryKey: ['employees-for-alms-reports'],
    queryFn: async () => (await apiClient.get<Page<EmployeeResponse>>('/employees', { params: { size: 500 } })).data.content,
  })

  const musterQuery = useQuery({
    queryKey: ['alms-muster-roll', startDate, endDate, officeIdNum, cadre],
    queryFn: async () =>
      (
        await apiClient.get<Page<MusterRollRow>>('/v1/reports/alms/muster-roll', {
          params: { startDate, endDate, officeId: officeIdNum, cadre: cadre || undefined, size: 200 },
        })
      ).data,
    enabled: tab === 'muster',
  })

  const payrollQuery = useQuery({
    queryKey: ['alms-payroll-feed', startDate, endDate, officeIdNum],
    queryFn: async () =>
      (
        await apiClient.get<PayrollCutoffFeedRow[]>('/v1/reports/alms/payroll-feed', {
          params: { periodStart: startDate, periodEnd: endDate, officeId: officeIdNum },
        })
      ).data,
    enabled: tab === 'payroll' || tab === 'muster', // also drives the KPI ribbon's cutoff-status chip
  })

  const circular53Query = useQuery({
    queryKey: ['alms-circular53', yearMonth, officeIdNum],
    queryFn: async () =>
      (
        await apiClient.get<Circular53ComplianceRow[]>('/v1/reports/alms/circular53-concessions', {
          params: { yearMonth, officeId: officeIdNum },
        })
      ).data,
    enabled: tab === 'circular53' || tab === 'muster',
  })

  const ccsForm1Query = useQuery({
    queryKey: ['alms-ccs-form1', selectedEmployeeId, ccsYear],
    queryFn: async () =>
      (await apiClient.get<CcsForm1Response>('/v1/reports/alms/ccs-form1', { params: { employeeId: selectedEmployeeId, year: ccsYear } })).data,
    enabled: tab === 'ccsForm1' && Boolean(selectedEmployeeId),
  })

  const encashmentQuery = useQuery({
    queryKey: ['alms-encashment-register', startDate, endDate],
    queryFn: async () =>
      (
        await apiClient.get<EncashmentRegisterRow[]>('/v1/reports/alms/encashment-register', {
          params: { fromDate: startDate, toDate: endDate },
        })
      ).data,
    enabled: tab === 'encashment' || tab === 'muster',
  })

  const offices = [
    ...(rosQuery.data ?? []).map((ro) => ({ id: ro.id, label: `RO - ${ro.code} - ${ro.name}` })),
    ...(dpcsQuery.data ?? []).map((dpc) => ({ id: dpc.id, label: `DPC - ${dpc.code} - ${dpc.name}` })),
  ]

  const filteredMuster = (musterQuery.data?.content ?? []).filter((r) => matchesSearch(r.employeeCode, r.employeeName, search))
  const musterColumns = useMemo(() => musterCycleColumns(startDate, endDate), [startDate, endDate])

  // ---- KPI ribbon ----
  const headcount = filteredMuster.length
  const avgAttendancePct =
    headcount > 0
      ? (filteredMuster.reduce((sum, r) => sum + (r.totalCycleDays > 0 ? r.presentDays / r.totalCycleDays : 0), 0) / headcount) * 100
      : 0
  const lateConcessionStrikes = (circular53Query.data ?? []).reduce((sum, r) => sum + r.concessionLateCount + r.concessionEarlyCount, 0)
  const approvedEncashmentDays = (encashmentQuery.data ?? []).reduce((sum, r) => sum + r.daysClaimed, 0)
  const cutoffLocked = (payrollQuery.data ?? []).some((r) => r.isLocked)

  function exportReport(format: 'XLSX' | 'CSV') {
    const reportTypeMap: Record<TabKey, string> = {
      muster: 'MUSTER_ROLL',
      payroll: 'PAYROLL_FEED',
      circular53: 'CIRCULAR53',
      ccsForm1: 'CCS_FORM1',
      encashment: 'ENCASHMENT_REGISTER',
    }
    const params: Record<string, string | number> = { reportType: reportTypeMap[tab], format }
    if (tab === 'muster' || tab === 'payroll' || tab === 'encashment') {
      params.startDate = startDate
      params.endDate = endDate
    }
    if ((tab === 'muster' || tab === 'payroll' || tab === 'circular53') && officeIdNum) params.officeId = officeIdNum
    if (tab === 'muster' && cadre) params.cadre = cadre
    if (tab === 'circular53') params.yearMonth = yearMonth
    if (tab === 'ccsForm1') {
      if (!selectedEmployeeId) return
      params.employeeId = selectedEmployeeId
      params.year = ccsYear
    }

    apiClient
      .get('/v1/reports/alms/export', { params, responseType: 'blob' })
      .then((response) => {
        const ext = format === 'XLSX' ? 'xlsx' : 'csv'
        const mime = format === 'XLSX' ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' : 'text/csv'
        downloadBlob(response.data, `${reportTypeMap[tab].toLowerCase()}-report.${ext}`, mime)
      })
  }

  return (
    <div>
      <PageHeader title="ALMS Reporting & Analytics Workbench" description="Statutory attendance/leave reports for payroll, audit, and compliance" />

      {/* Parameter ribbon */}
      <Card className="mb-4">
        <div className="flex flex-wrap items-end gap-3">
          <DateField label="Cycle Start" value={startDate} onChange={setStartDate} className="w-36" />
          <DateField label="Cycle End" value={endDate} onChange={setEndDate} className="w-36" />
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Office</label>
            <select value={officeId} onChange={(e) => setOfficeId(e.target.value)} className="rounded-md border border-slate-300 px-2 py-1.5 text-sm">
              <option value="">All Offices</option>
              {offices.map((o) => (
                <option key={o.label} value={o.id}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Cadre</label>
            <select value={cadre} onChange={(e) => setCadre(e.target.value)} className="rounded-md border border-slate-300 px-2 py-1.5 text-sm">
              <option value="">All</option>
              <option value="REGULAR">Regular</option>
              <option value="CASUAL">Casual</option>
              <option value="CONTRACTUAL">Contractual</option>
              <option value="OUTSOURCED">Outsourced</option>
            </select>
          </div>
          <div className="flex-1 min-w-[180px]">
            <label className="mb-1 block text-xs font-medium text-slate-600">Search</label>
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Employee code or name..."
              className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm"
            />
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => exportReport('XLSX')}
              className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              <Download size={14} /> Excel
            </button>
            <button
              type="button"
              onClick={() => exportReport('CSV')}
              className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              <Download size={14} /> CSV
            </button>
          </div>
        </div>
      </Card>

      {/* KPI ribbon */}
      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-5">
        <Card><p className="text-xs text-slate-400">Total Headcount</p><p className="mt-1 text-xl font-semibold text-slate-800">{headcount}</p></Card>
        <Card><p className="text-xs text-slate-400">Avg. Physical Attendance</p><p className="mt-1 text-xl font-semibold text-slate-800">{avgAttendancePct.toFixed(1)}%</p></Card>
        <Card><p className="text-xs text-slate-400">Late Concession Strikes</p><p className="mt-1 text-xl font-semibold text-slate-800">{lateConcessionStrikes}</p></Card>
        <Card><p className="text-xs text-slate-400">Approved In-Service EL Days</p><p className="mt-1 text-xl font-semibold text-slate-800">{approvedEncashmentDays.toFixed(2)}</p></Card>
        <Card>
          <p className="text-xs text-slate-400">Cutoff Status</p>
          <p className="mt-1 flex items-center gap-1.5 text-xl font-semibold text-slate-800">
            {cutoffLocked ? <Lock size={16} className="text-red-500" /> : <Unlock size={16} className="text-emerald-500" />}
            {cutoffLocked ? 'Locked' : 'Open'}
          </p>
        </Card>
      </div>

      <div className="mb-4 flex flex-wrap gap-2">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setTab(t.key)}
            className={`rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${tab === t.key ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'muster' && (
        <Card className="print-sheet">
          {musterQuery.isLoading && <LoadingState label="Generating muster roll..." />}
          {musterQuery.isError && <ErrorState message="Could not generate the muster roll." />}
          {musterQuery.data && filteredMuster.length === 0 && <EmptyState message="No records for this cycle/filter." />}
          {filteredMuster.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[1200px] border-collapse text-xs">
                <thead>
                  <tr className="border-b border-slate-300 text-left text-slate-500">
                    <th className="sticky left-0 bg-white py-2 pr-3">Employee</th>
                    <th className="py-2 pr-3">Designation</th>
                    <th className="py-2 pr-3">Office</th>
                    {musterColumns.map((col) => (
                      <th key={col.iso} className="py-2 px-1 text-center" title={formatDate(col.iso)}>{col.label}</th>
                    ))}
                    <th className="py-2 pl-3 text-right">Present</th>
                    <th className="py-2 pl-3 text-right">LWP</th>
                    <th className="py-2 pl-3 text-right">Penalty</th>
                    <th className="py-2 pl-3 text-right">Net Payable</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredMuster.map((row) => (
                    <tr key={row.employeeId} className="border-b border-slate-100">
                      <td className="sticky left-0 bg-white py-1.5 pr-3 font-medium">{row.employeeCode} - {row.employeeName}</td>
                      <td className="py-1.5 pr-3 text-slate-500">{row.designation}</td>
                      <td className="py-1.5 pr-3 text-slate-500">{row.officeName}</td>
                      {musterColumns.map((col) => {
                        const code = row.dailyPunches[col.label]
                        return (
                          <td key={col.iso} className="py-1 px-1 text-center" title={formatDate(col.iso)}>
                            <span className={`inline-flex h-5 w-8 items-center justify-center rounded ${MUSTER_CODE_TONE[code] ?? 'bg-slate-100 text-slate-600'}`}>{code}</span>
                          </td>
                        )
                      })}
                      <td className="py-1.5 pl-3 text-right tabular-nums">{row.presentDays}</td>
                      <td className="py-1.5 pl-3 text-right tabular-nums">{row.unpaidLwpDays}</td>
                      <td className="py-1.5 pl-3 text-right tabular-nums">{row.penaltyDeductionDays}</td>
                      <td className="py-1.5 pl-3 text-right font-medium tabular-nums">{row.netPayableDays}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}

      {tab === 'payroll' && (
        <Card>
          {payrollQuery.isLoading && <LoadingState />}
          {payrollQuery.isError && <ErrorState message="Could not generate the payroll feed." />}
          {payrollQuery.data && payrollQuery.data.length === 0 && <EmptyState message="No records for this cycle." />}
          {payrollQuery.data && payrollQuery.data.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[900px] border-collapse text-sm">
                <thead>
                  <tr className="border-b border-slate-300 text-left text-slate-500">
                    <th className="py-2 pr-3">Employee</th>
                    <th className="py-2 pr-3 text-right">Cycle Days</th>
                    <th className="py-2 pr-3 text-right">Payable Days</th>
                    <th className="py-2 pr-3 text-right">LWP</th>
                    <th className="py-2 pr-3 text-right">Absent</th>
                    <th className="py-2 pr-3 text-right">Penalty</th>
                    <th className="py-2 pr-3 text-right">Approved In-Service EL</th>
                    <th className="py-2 pl-3">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {payrollQuery.data
                    .filter((r) => matchesSearch(r.employeeCode, r.employeeName, search))
                    .map((row) => (
                      <tr key={row.employeeId} className="border-b border-slate-100">
                        <td className="py-2 pr-3 font-medium">{row.employeeCode} - {row.employeeName}</td>
                        <td className="py-2 pr-3 text-right tabular-nums">{row.totalCycleDays}</td>
                        <td className="py-2 pr-3 text-right tabular-nums">{row.payableDays}</td>
                        <td className="py-2 pr-3 text-right tabular-nums">{row.lwpDays}</td>
                        <td className="py-2 pr-3 text-right tabular-nums">{row.absentDays}</td>
                        <td className="py-2 pr-3 text-right tabular-nums">{row.penaltyDays}</td>
                        <td className="py-2 pr-3 text-right tabular-nums">{row.approvedInServiceElDays}</td>
                        <td className="py-2 pl-3">
                          <Badge tone={row.isLocked ? 'danger' : 'success'}>{row.isLocked ? 'Locked' : 'Open'}</Badge>
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}

      {tab === 'circular53' && (
        <Card>
          {circular53Query.isLoading && <LoadingState />}
          {circular53Query.isError && <ErrorState message="Could not generate the compliance report." />}
          {circular53Query.data && circular53Query.data.length === 0 && <EmptyState message="No records for this month." />}
          {circular53Query.data && circular53Query.data.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[900px] border-collapse text-sm">
                <thead>
                  <tr className="border-b border-slate-300 text-left text-slate-500">
                    <th className="py-2 pr-3">Employee</th>
                    <th className="py-2 pr-3">Office</th>
                    <th className="py-2 pr-3 text-right">Flex Grace</th>
                    <th className="py-2 pr-3 text-right">Late Concessions</th>
                    <th className="py-2 pr-3 text-right">Early Concessions</th>
                    <th className="py-2 pr-3 text-right">3rd Strike</th>
                    <th className="py-2 pl-3 text-right">Penalty Days</th>
                  </tr>
                </thead>
                <tbody>
                  {circular53Query.data
                    .filter((r) => matchesSearch(r.employeeCode, r.employeeName, search))
                    .map((row) => (
                      <tr key={row.employeeId} className="border-b border-slate-100">
                        <td className="py-2 pr-3 font-medium">{row.employeeCode} - {row.employeeName}</td>
                        <td className="py-2 pr-3 text-slate-500">{row.officeName}</td>
                        <td className="py-2 pr-3 text-right tabular-nums">{row.flexGraceCount}</td>
                        <td className="py-2 pr-3 text-right tabular-nums">{row.concessionLateCount}</td>
                        <td className="py-2 pr-3 text-right tabular-nums">{row.concessionEarlyCount}</td>
                        <td className="py-2 pr-3 text-right tabular-nums">
                          {row.thirdStrikeUnregularizedCount > 0 ? <Badge tone="danger">{row.thirdStrikeUnregularizedCount}</Badge> : 0}
                        </td>
                        <td className="py-2 pl-3 text-right tabular-nums">{row.penaltyLeaveDebited}</td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}

      {tab === 'ccsForm1' && (
        <Card>
          <div className="mb-4 flex flex-wrap items-end gap-3">
            <div className="min-w-[240px]">
              <label className="mb-1 block text-xs font-medium text-slate-600">Employee</label>
              <select
                value={selectedEmployeeId}
                onChange={(e) => setSelectedEmployeeId(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm"
              >
                <option value="">Select employee...</option>
                {(employeesQuery.data ?? []).map((emp) => (
                  <option key={emp.id} value={emp.id}>
                    {emp.employeeCode} - {emp.fullName}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Year</label>
              <input
                type="number"
                value={ccsYear}
                onChange={(e) => setCcsYear(Number(e.target.value))}
                className="w-24 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
              />
            </div>
          </div>

          {!selectedEmployeeId && <EmptyState message="Select an employee to view their CCS Form 1." />}
          {ccsForm1Query.isLoading && <LoadingState />}
          {ccsForm1Query.isError && <ErrorState message="Could not generate CCS Form 1 for this employee/year." />}
          {ccsForm1Query.data && (
            <div>
              <div className="mb-4 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
                <div className="rounded-md bg-slate-50 p-2"><p className="text-xs text-slate-400">Opening Enjoyable</p><p className="font-semibold">{ccsForm1Query.data.openingEnjoyable.toFixed(2)}</p></div>
                <div className="rounded-md bg-slate-50 p-2"><p className="text-xs text-slate-400">Opening Encashable</p><p className="font-semibold">{ccsForm1Query.data.openingEncashable.toFixed(2)}</p></div>
                <div className="rounded-md bg-slate-50 p-2"><p className="text-xs text-slate-400">Advance Credit Enjoyable</p><p className="font-semibold">{ccsForm1Query.data.advanceCreditEnjoyable.toFixed(2)}</p></div>
                <div className="rounded-md bg-slate-50 p-2"><p className="text-xs text-slate-400">Advance Credit Encashable</p><p className="font-semibold">{ccsForm1Query.data.advanceCreditEncashable.toFixed(2)}</p></div>
                <div className="rounded-md bg-slate-50 p-2"><p className="text-xs text-slate-400">EOL Deduction</p><p className="font-semibold">{ccsForm1Query.data.eolDeduction.toFixed(2)}</p></div>
                <div className="rounded-md bg-emerald-50 p-2"><p className="text-xs text-slate-400">Closing Enjoyable</p><p className="font-semibold">{ccsForm1Query.data.closingEnjoyable.toFixed(2)}</p></div>
                <div className="rounded-md bg-emerald-50 p-2"><p className="text-xs text-slate-400">Closing Encashable</p><p className="font-semibold">{ccsForm1Query.data.closingEncashable.toFixed(2)}</p></div>
                <div className="rounded-md bg-emerald-50 p-2"><p className="text-xs text-slate-400">Total Balance</p><p className="font-semibold">{ccsForm1Query.data.totalBalance.toFixed(2)}</p></div>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full min-w-[720px] border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-slate-300 text-left text-slate-500">
                      <th className="py-2 pr-3">From</th>
                      <th className="py-2 pr-3">To</th>
                      <th className="py-2 pr-3">Type</th>
                      <th className="py-2 pr-3 text-right">Enjoyable Debited</th>
                      <th className="py-2 pr-3 text-right">Encashable Debited</th>
                      <th className="py-2 pl-3">Order Ref</th>
                    </tr>
                  </thead>
                  <tbody>
                    {ccsForm1Query.data.availedTransactions.map((t, i) => (
                      <tr key={i} className="border-b border-slate-100">
                        <td className="py-2 pr-3">{formatDate(t.fromDate)}</td>
                        <td className="py-2 pr-3">{formatDate(t.toDate)}</td>
                        <td className="py-2 pr-3">{t.type}</td>
                        <td className="py-2 pr-3 text-right tabular-nums">{t.enjoyableDebited.toFixed(2)}</td>
                        <td className="py-2 pr-3 text-right tabular-nums">{t.encashableDebited.toFixed(2)}</td>
                        <td className="py-2 pl-3 text-xs text-slate-500">{t.orderRef ?? '—'}</td>
                      </tr>
                    ))}
                    {ccsForm1Query.data.availedTransactions.length === 0 && (
                      <tr><td colSpan={6} className="py-4 text-center text-slate-400">No EL transactions this year.</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </Card>
      )}

      {tab === 'encashment' && (
        <Card>
          {encashmentQuery.isLoading && <LoadingState />}
          {encashmentQuery.isError && <ErrorState message="Could not generate the encashment register." />}
          {encashmentQuery.data && encashmentQuery.data.length === 0 && <EmptyState message="No dual-approved encashments in this range." />}
          {encashmentQuery.data && encashmentQuery.data.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[1000px] border-collapse text-sm">
                <thead>
                  <tr className="border-b border-slate-300 text-left text-slate-500">
                    <th className="py-2 pr-3">App #</th>
                    <th className="py-2 pr-3">Employee</th>
                    <th className="py-2 pr-3">Type</th>
                    <th className="py-2 pr-3">Tenure</th>
                    <th className="py-2 pr-3 text-right">Days</th>
                    <th className="py-2 pr-3">HR Approved</th>
                    <th className="py-2 pr-3">Finance Approved</th>
                    <th className="py-2 pr-3">Payroll</th>
                    <th className="py-2 pr-3">Service Book</th>
                    <th className="py-2 pl-3 text-right">Est. Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {encashmentQuery.data
                    .filter((r) => matchesSearch(r.employeeCode, r.employeeName, search))
                    .map((row) => (
                      <tr key={row.applicationNumber} className="border-b border-slate-100">
                        <td className="py-2 pr-3 font-medium">#{row.applicationNumber}</td>
                        <td className="py-2 pr-3">{row.employeeCode} - {row.employeeName}</td>
                        <td className="py-2 pr-3 text-xs text-slate-500">{row.encashmentType.replace(/_/g, ' ')}</td>
                        <td className="py-2 pr-3 text-xs text-slate-500">{row.qualifyingTenure}</td>
                        <td className="py-2 pr-3 text-right tabular-nums">{row.daysClaimed.toFixed(2)}</td>
                        <td className="py-2 pr-3 text-xs text-slate-500">{row.hrApprovedBy ?? '—'}</td>
                        <td className="py-2 pr-3 text-xs text-slate-500">{row.financeApprovedBy ?? '—'}</td>
                        <td className="py-2 pr-3"><Badge tone={row.isPayrollEligible ? 'success' : 'warning'}>{row.isPayrollEligible ? 'Eligible' : 'Pending'}</Badge></td>
                        <td className="py-2 pr-3 text-xs text-slate-500">{row.serviceBookFolio ? `#${row.serviceBookFolio}` : '—'}</td>
                        <td className="py-2 pl-3 text-right tabular-nums">{row.estimatedAmount != null ? row.estimatedAmount.toLocaleString('en-IN') : '—'}</td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}
    </div>
  )
}

function matchesSearch(code: string, name: string, query: string): boolean {
  if (!query.trim()) return true
  const q = query.trim().toLowerCase()
  return code.toLowerCase().includes(q) || name.toLowerCase().includes(q)
}
