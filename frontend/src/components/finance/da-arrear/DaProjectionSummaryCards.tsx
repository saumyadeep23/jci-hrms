import { Badge, Card } from '../../common/ui'
import type { IdaProjectionEmployeeResponse, IdaProjectionSummaryResponse } from '../../../types/api'

function formatInr(value: number): string {
  return `₹${value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function sumBy(employees: IdaProjectionEmployeeResponse[], selector: (e: IdaProjectionEmployeeResponse) => number): number {
  return employees.reduce((total, e) => total + selector(e), 0)
}

function Kpi({ label, value, tone }: { label: string; value: string; tone?: 'success' | 'warning' }) {
  return (
    <div className="rounded-lg border border-slate-200 p-3">
      <p className="text-xs text-slate-400">{label}</p>
      <p className={`mt-1 text-xl font-semibold tabular-nums ${tone === 'success' ? 'text-emerald-700' : tone === 'warning' ? 'text-amber-700' : 'text-slate-800'}`}>
        {value}
      </p>
    </div>
  )
}

/**
 * Head-wise breakdown row shape: [label, head/stat reference, amount-or-null]. Arrear_PENSION (Stat 13)
 * has no row of real data behind it - this engine only ever computes CPF (employee+employer/JCPF) or
 * NPS (employee+employer) per employee, matching PayrollBatchComputationService's own two-scheme model;
 * the separate legacy Pension Fund track (Stat 4/13) isn't part of either. Shown as an explicit
 * "not computed" row rather than a fabricated zero, so it doesn't read as "confirmed nil liability".
 */
export function DaProjectionSummaryCards({
  batch,
  employees,
}: {
  batch: IdaProjectionSummaryResponse
  employees: IdaProjectionEmployeeResponse[]
}) {
  const totalArrDa = sumBy(employees, (e) => e.totalGrossArrears)
  const totalArrCpf = sumBy(employees, (e) => e.totalEmployeeCpfArrear)
  const totalJcpf = sumBy(employees, (e) => e.totalEmployerJcpfArrear)
  const totalENps = sumBy(employees, (e) => e.totalEmployeeNpsArrear)
  const totalJNps = sumBy(employees, (e) => e.totalEmployerNpsArrear)
  const totalEncashmentArrear = sumBy(employees, (e) => e.totalLeaveEncashmentArrear)

  return (
    <Card className="mb-6">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-slate-700">
          Projection #{batch.projectionCode} · {batch.scaleType} {batch.oldDaRate.toFixed(2)}% → {batch.newDaRate.toFixed(2)}%
        </h2>
        <Badge tone={batch.status === 'ORDER_COMMITTED' ? 'success' : 'neutral'}>{batch.status.replace('_', ' ')}</Badge>
      </div>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Kpi label="Covered Headcount" value={batch.totalActiveEmployees.toLocaleString('en-IN')} />
        <Kpi label="Monthly Recurring Gross Δ" value={`+${formatInr(batch.totalMonthlyGrossDelta)}/mo`} tone="success" />
        <Kpi label="Monthly Employer Cost Δ" value={`+${formatInr(batch.totalMonthlyEmployerCostDelta)}/mo`} tone="warning" />
        <Kpi label="Total Retrospective Arrear Outgo" value={formatInr(batch.totalArrearGrossOutgo)} tone="warning" />
      </div>

      <h3 className="mb-2 mt-5 text-xs font-semibold uppercase tracking-wide text-slate-500">Head-wise Liability Breakdown</h3>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[520px] border-collapse text-sm">
          <thead>
            <tr className="border-b border-slate-300 text-left text-slate-500">
              <th className="py-2 pr-3">Head / Stat Reference</th>
              <th className="py-2 pl-3 text-right">Amount</th>
            </tr>
          </thead>
          <tbody>
            <tr className="border-b border-slate-100">
              <td className="py-1.5 pr-3">Gross DA Arrear (Head 14 - ARR_DA)</td>
              <td className="py-1.5 pl-3 text-right tabular-nums font-medium">{formatInr(totalArrDa)}</td>
            </tr>
            <tr className="border-b border-slate-100">
              <td className="py-1.5 pr-3">Employee CPF Arrear (Head 29 / Stat 11 - ARR_CPF)</td>
              <td className="py-1.5 pl-3 text-right tabular-nums">{formatInr(totalArrCpf)}</td>
            </tr>
            <tr className="border-b border-slate-100">
              <td className="py-1.5 pr-3">Employer JCPF Arrear (Stat 12 - Arrear_JCPF)</td>
              <td className="py-1.5 pl-3 text-right tabular-nums">{formatInr(totalJcpf)}</td>
            </tr>
            <tr className="border-b border-slate-100 text-slate-400">
              <td className="py-1.5 pr-3">Arrear Pension Fund (Stat 13 - Arrear_PENSION)</td>
              <td className="py-1.5 pl-3 text-right italic">Not computed by this engine</td>
            </tr>
            <tr className="border-b border-slate-100">
              <td className="py-1.5 pr-3">Employee NPS Arrear (Head 62 / Stat 14 - E_NPS)</td>
              <td className="py-1.5 pl-3 text-right tabular-nums">{formatInr(totalENps)}</td>
            </tr>
            <tr className="border-b border-slate-100">
              <td className="py-1.5 pr-3">Employer NPS Arrear (Stat 15 - J_NPS)</td>
              <td className="py-1.5 pl-3 text-right tabular-nums">{formatInr(totalJNps)}</td>
            </tr>
            <tr>
              <td className="py-1.5 pr-3">
                Leave Encashment DA Arrear
                <span className="block text-xs text-slate-400">Top-up on already-settled encashments - not the base Head 20 (ENCASH_AMT) amount itself</span>
              </td>
              <td className="py-1.5 pl-3 text-right tabular-nums">{formatInr(totalEncashmentArrear)}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </Card>
  )
}
