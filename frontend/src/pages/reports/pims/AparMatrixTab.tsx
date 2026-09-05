import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { Badge, Card, EmptyState, ErrorState, LoadingState } from '../../../components/common/ui'
import { KpiCard } from '../../../components/reports/KpiCard'
import { DepartmentFilter } from '../../../components/reports/DepartmentFilter'
import { ReportExportButtons } from '../../../components/reports/ReportExportButtons'
import { Employee360Drawer } from '../../../components/reports/Employee360Drawer'
import type { AparMatrixReportResponse, PimsReportFilter } from '../../../types/api'

/** PIMS_SPEC.md reports task: APAR Matrix tab. */
export function AparMatrixTab() {
  const [departmentId, setDepartmentId] = useState('')
  const [drillThroughId, setDrillThroughId] = useState<number | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['pims-apar-matrix', departmentId],
    queryFn: async () =>
      (
        await apiClient.get<AparMatrixReportResponse>('/v1/reports/pims/apar-matrix', {
          params: { departmentId: departmentId || undefined },
        })
      ).data,
  })

  const filter: PimsReportFilter = { departmentId: departmentId ? Number(departmentId) : null }

  return (
    <div>
      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
        <KpiCard label="Total Postings" value={data?.rows.length} tone="brand" />
        <KpiCard label="Dual Charge >= 90 Days" value={data?.dualChargeOver90DaysCount} tone="warning" />
      </div>

      <Card className="mb-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <DepartmentFilter value={departmentId} onChange={setDepartmentId} />
          <ReportExportButtons reportType="APAR_MATRIX" filter={filter} />
        </div>
      </Card>

      <Card>
        {isLoading && <LoadingState label="Loading APAR matrix..." />}
        {isError && <ErrorState message="Could not load the APAR routing matrix." />}
        {data && data.rows.length === 0 && <EmptyState message="No active postings found." />}
        {data && data.rows.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[860px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Employee</th>
                  <th className="py-2 pr-3">Post</th>
                  <th className="py-2 pr-3">Type</th>
                  <th className="py-2 pr-3">Reporting Officer</th>
                  <th className="py-2 pr-3">Reviewing Officer</th>
                  <th className="py-2 pr-3">Accepting Officer</th>
                  <th className="py-2 pl-3">Dual Charge</th>
                </tr>
              </thead>
              <tbody>
                {data.rows.map((row) => (
                  <tr
                    key={row.appraiseeEmployeeId}
                    className="cursor-pointer border-b border-slate-100 hover:bg-slate-50"
                    onClick={() => setDrillThroughId(row.appraiseeEmployeeId)}
                  >
                    <td className="py-2 pr-3">
                      <span className="font-medium text-brand-forest">{row.appraiseeEmployeeCode}</span> {row.appraiseeName}
                    </td>
                    <td className="py-2 pr-3 text-slate-500">{row.postTitle}</td>
                    <td className="py-2 pr-3 text-xs text-slate-500">{row.assignmentType}</td>
                    <td className="py-2 pr-3">{row.reportingOfficerName ?? '—'}</td>
                    <td className="py-2 pr-3">{row.reviewingOfficerName ?? '—'}</td>
                    <td className="py-2 pr-3">{row.acceptingOfficerName ?? '—'}</td>
                    <td className="py-2 pl-3">
                      {row.dualChargeOver90Days ? <Badge tone="warning">Yes</Badge> : <Badge tone="neutral">No</Badge>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {drillThroughId && <Employee360Drawer employeeId={drillThroughId} onClose={() => setDrillThroughId(null)} />}
    </div>
  )
}
