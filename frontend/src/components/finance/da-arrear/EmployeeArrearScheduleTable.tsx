import { Fragment, useState } from 'react'
import { ChevronDown, ChevronRight, Download } from 'lucide-react'
import { Badge, Card, EmptyState, SecondaryButton } from '../../common/ui'
import type { IdaProjectionEmployeeResponse, PensionScheme } from '../../../types/api'

function formatInr(value: number): string {
  return `₹${value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function csvCell(value: string | number): string {
  const text = String(value)
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

function exportToCsv(employees: IdaProjectionEmployeeResponse[]) {
  const header = [
    'Emp Code', 'Name', 'Designation', 'Scheme', 'Month', 'Calendar Days', 'Paid Days', 'LWP Days',
    'Historical Basic', 'Old Rate %', 'New Rate %', 'Delta DA', 'Employee PF/NPS', 'Employer PF/NPS', 'Net Monthly Arrear',
  ]
  const lines: string[] = []
  for (const emp of employees) {
    for (const m of emp.monthlyBreakups) {
      const employeeShare = emp.pensionScheme === 'NPS' ? m.employeeNpsArrear : m.employeeCpfArrear
      const employerShare = emp.pensionScheme === 'NPS' ? m.employerNpsArrear : m.employerJcpfArrear
      lines.push(
        [
          emp.employeeCode, emp.fullName, emp.designation ?? '', emp.pensionScheme, m.monthLabel, m.totalDays,
          m.paidDays, (m.totalDays - m.paidDays).toFixed(1), m.actualBasicPay, m.oldDaRate, m.newDaRate, m.deltaDa,
          employeeShare, employerShare, m.netMonthlyArrear,
        ]
          .map(csvCell)
          .join(','),
      )
    }
  }
  const csv = [header.join(','), ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'ida-arrear-audit-schedule.csv'
  link.click()
  URL.revokeObjectURL(url)
}

function MonthlyAccordionRow({ employee }: { employee: IdaProjectionEmployeeResponse }) {
  return (
    <tr>
      <td colSpan={9} className="bg-slate-50 p-3">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[900px] border-collapse text-xs">
            <thead>
              <tr className="border-b border-slate-300 text-left text-slate-500">
                <th className="py-1.5 pr-2">Month</th>
                <th className="py-1.5 pr-2 text-right">Calendar Days</th>
                <th className="py-1.5 pr-2 text-right">Paid Days</th>
                <th className="py-1.5 pr-2 text-right">Historical Basic</th>
                <th className="py-1.5 pr-2 text-right">Old Rate %</th>
                <th className="py-1.5 pr-2 text-right">New Rate %</th>
                <th className="py-1.5 pr-2 text-right">Δ DA (Head 14)</th>
                <th className="py-1.5 pr-2 text-right">{employee.pensionScheme === 'NPS' ? 'Employee NPS' : 'Employee PF'}</th>
                <th className="py-1.5 pl-2 text-right">Net Monthly Arrear</th>
              </tr>
            </thead>
            <tbody>
              {employee.monthlyBreakups.map((m) => {
                const lwp = m.totalDays - m.paidDays
                const employeeShare = employee.pensionScheme === 'NPS' ? m.employeeNpsArrear : m.employeeCpfArrear
                return (
                  <tr key={`${m.salYear}-${m.salMonth}`} className="border-b border-slate-200">
                    <td className="py-1.5 pr-2 font-medium">{m.monthLabel}</td>
                    <td className="py-1.5 pr-2 text-right tabular-nums">{m.totalDays}</td>
                    <td className={`py-1.5 pr-2 text-right tabular-nums ${lwp > 0 ? 'font-semibold text-amber-700' : ''}`}>
                      {m.paidDays.toFixed(1)}
                      {lwp > 0 && <span className="ml-1 text-[10px] font-normal text-amber-600">(LWP {lwp.toFixed(1)})</span>}
                    </td>
                    <td className="py-1.5 pr-2 text-right tabular-nums">{formatInr(m.actualBasicPay)}</td>
                    <td className="py-1.5 pr-2 text-right tabular-nums">{m.oldDaRate.toFixed(2)}</td>
                    <td className="py-1.5 pr-2 text-right tabular-nums">{m.newDaRate.toFixed(2)}</td>
                    <td className="py-1.5 pr-2 text-right tabular-nums font-medium">{formatInr(m.deltaDa)}</td>
                    <td className="py-1.5 pr-2 text-right tabular-nums">{formatInr(employeeShare)}</td>
                    <td className="py-1.5 pl-2 text-right tabular-nums font-medium text-emerald-700">{formatInr(m.netMonthlyArrear)}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </td>
    </tr>
  )
}

/** Cadre filtering was dropped - IdaProjectionEmployeeResponse doesn't carry cadre (it isn't part of the DA arrear engine's own data model), so faking a filter over data that isn't there would be worse than not offering it. */
export function EmployeeArrearScheduleTable({ employees }: { employees: IdaProjectionEmployeeResponse[] }) {
  const [search, setSearch] = useState('')
  const [schemeFilter, setSchemeFilter] = useState<'ALL' | PensionScheme>('ALL')
  const [expanded, setExpanded] = useState<Set<number>>(new Set())

  const filtered = employees.filter((e) => {
    if (schemeFilter !== 'ALL' && e.pensionScheme !== schemeFilter) return false
    if (search.trim() === '') return true
    const q = search.trim().toLowerCase()
    return e.employeeCode.toLowerCase().includes(q) || e.fullName.toLowerCase().includes(q)
  })

  function toggle(employeeId: number) {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(employeeId)) next.delete(employeeId)
      else next.add(employeeId)
      return next
    })
  }

  return (
    <Card>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-slate-700">Employee Arrear Schedule</h2>
        <div className="flex flex-wrap items-center gap-2">
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search Emp Code / Name..."
            className="rounded-md border border-slate-300 px-3 py-1.5 text-xs"
          />
          <select
            value={schemeFilter}
            onChange={(e) => setSchemeFilter(e.target.value as 'ALL' | PensionScheme)}
            className="rounded-md border border-slate-300 px-2 py-1.5 text-xs"
          >
            <option value="ALL">All Schemes</option>
            <option value="CPF">CPF</option>
            <option value="NPS">NPS</option>
          </select>
          <SecondaryButton onClick={() => exportToCsv(filtered)} disabled={filtered.length === 0}>
            <Download size={14} /> Export Audit Schedules to Excel
          </SecondaryButton>
        </div>
      </div>

      {filtered.length === 0 && <EmptyState message="No employees match this filter." />}

      {filtered.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[1100px] border-collapse text-sm">
            <thead>
              <tr className="border-b border-slate-300 text-left text-slate-500">
                <th className="py-2 pr-3" />
                <th className="py-2 pr-3">Emp Code</th>
                <th className="py-2 pr-3">Name</th>
                <th className="py-2 pr-3">Designation</th>
                <th className="py-2 pr-3">Scheme</th>
                <th className="py-2 pr-3 text-right">Gross Arrears (Head 14)</th>
                <th className="py-2 pr-3 text-right">Deductions</th>
                <th className="py-2 pr-3 text-right">Encashment Arrear</th>
                <th className="py-2 pl-3 text-right">Net Arrear Payable</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((emp) => {
                const isOpen = expanded.has(emp.employeeId)
                const deductions = emp.pensionScheme === 'NPS' ? emp.totalEmployeeNpsArrear : emp.totalEmployeeCpfArrear
                return (
                  <Fragment key={emp.employeeId}>
                    <tr className="border-b border-slate-100">
                      <td className="py-2 pr-3">
                        <button type="button" onClick={() => toggle(emp.employeeId)} className="text-slate-500 hover:text-slate-700">
                          {isOpen ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                        </button>
                      </td>
                      <td className="py-2 pr-3 font-medium tabular-nums">{emp.employeeCode}</td>
                      <td className="py-2 pr-3">{emp.fullName}</td>
                      <td className="py-2 pr-3 text-slate-500">{emp.designation ?? '—'}</td>
                      <td className="py-2 pr-3">
                        <Badge tone={emp.pensionScheme === 'NPS' ? 'brand' : 'neutral'}>{emp.pensionScheme}</Badge>
                      </td>
                      <td className="py-2 pr-3 text-right tabular-nums">{formatInr(emp.totalGrossArrears)}</td>
                      <td className="py-2 pr-3 text-right tabular-nums">{formatInr(deductions)}</td>
                      <td className="py-2 pr-3 text-right tabular-nums">{formatInr(emp.totalLeaveEncashmentArrear)}</td>
                      <td className="py-2 pl-3 text-right tabular-nums font-semibold text-emerald-700">{formatInr(emp.totalNetArrearPayable)}</td>
                    </tr>
                    {isOpen && <MonthlyAccordionRow employee={emp} />}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}
