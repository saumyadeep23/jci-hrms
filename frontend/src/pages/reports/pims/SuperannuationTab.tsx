import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { LogOut } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { formatDate } from '../../../lib/date'
import { Badge, Card, EmptyState, ErrorState, LoadingState } from '../../../components/common/ui'
import { KpiCard } from '../../../components/reports/KpiCard'
import { DepartmentFilter } from '../../../components/reports/DepartmentFilter'
import { ReportExportButtons } from '../../../components/reports/ReportExportButtons'
import { Employee360Drawer } from '../../../components/reports/Employee360Drawer'
import { ExitClearanceModal } from '../../../components/employee/ExitClearanceModal'
import type { PimsReportFilter, SuperannuationReportResponse } from '../../../types/api'

/** PIMS_SPEC.md reports task: 58/60-Yr Superannuation Forecast tab. */
export function SuperannuationTab() {
  const [departmentId, setDepartmentId] = useState('')
  const [months, setMonths] = useState(60)
  const [drillThroughId, setDrillThroughId] = useState<number | null>(null)
  const [exitFormalitiesEmployee, setExitFormalitiesEmployee] = useState<{ id: number; name: string } | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['pims-superannuation', departmentId, months],
    queryFn: async () =>
      (
        await apiClient.get<SuperannuationReportResponse>('/v1/reports/pims/superannuation', {
          params: { departmentId: departmentId || undefined, months },
        })
      ).data,
  })

  const filter: PimsReportFilter = { departmentId: departmentId ? Number(departmentId) : null, months }

  return (
    <div>
      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-5">
        {(data?.windowSummary ?? []).map((w) => (
          <button key={w.months} type="button" onClick={() => setMonths(w.months)}>
            <KpiCard label={`Within ${w.months}mo`} value={w.count} tone={months === w.months ? 'brand' : 'neutral'} />
          </button>
        ))}
      </div>

      <Card className="mb-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <DepartmentFilter value={departmentId} onChange={setDepartmentId} />
          <ReportExportButtons reportType="SUPERANNUATION" filter={filter} />
        </div>
      </Card>

      <Card>
        {isLoading && <LoadingState label="Loading superannuation forecast..." />}
        {isError && <ErrorState message="Could not load the superannuation forecast." />}
        {data && data.entries.length === 0 && <EmptyState message="No employees due for superannuation in this window." />}
        {data && data.entries.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Employee Code</th>
                  <th className="py-2 pr-3">Name</th>
                  <th className="py-2 pr-3">Department</th>
                  <th className="py-2 pr-3">Designation</th>
                  <th className="py-2 pr-3">Superannuation Date</th>
                  <th className="py-2 pr-3 text-right">Months Left</th>
                  <th className="py-2 pl-3">Type</th>
                  <th className="py-2 pl-3"></th>
                </tr>
              </thead>
              <tbody>
                {data.entries.map((e) => (
                  <tr
                    key={e.employeeId}
                    className="cursor-pointer border-b border-slate-100 hover:bg-slate-50"
                    onClick={() => setDrillThroughId(e.employeeId)}
                  >
                    <td className="py-2 pr-3 font-medium text-brand-forest">{e.employeeCode}</td>
                    <td className="py-2 pr-3">{e.employeeName}</td>
                    <td className="py-2 pr-3 text-slate-500">{e.departmentName ?? '—'}</td>
                    <td className="py-2 pr-3 text-slate-500">{e.designationTitle ?? '—'}</td>
                    <td className="py-2 pr-3">{formatDate(e.superannuationDate)}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{e.monthsRemaining}</td>
                    <td className="py-2 pl-3">
                      {e.isBoardDirector ? <Badge tone="warning">Director (60y)</Badge> : <Badge tone="neutral">Regular (58y)</Badge>}
                    </td>
                    <td className="py-2 pl-3">
                      <button
                        type="button"
                        onClick={(ev) => {
                          ev.stopPropagation()
                          setExitFormalitiesEmployee({ id: e.employeeId, name: e.employeeName })
                        }}
                        title="Exit Formalities"
                        className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-slate-500 hover:bg-slate-100"
                      >
                        <LogOut size={13} /> Exit Formalities
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {drillThroughId && <Employee360Drawer employeeId={drillThroughId} onClose={() => setDrillThroughId(null)} />}

      {exitFormalitiesEmployee && (
        <ExitClearanceModal
          employeeId={exitFormalitiesEmployee.id}
          employeeName={exitFormalitiesEmployee.name}
          onClose={() => setExitFormalitiesEmployee(null)}
        />
      )}
    </div>
  )
}
