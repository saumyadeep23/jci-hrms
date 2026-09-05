import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { Badge, Card, EmptyState, ErrorState, LoadingState } from '../../../components/common/ui'
import { KpiCard } from '../../../components/reports/KpiCard'
import { DepartmentFilter } from '../../../components/reports/DepartmentFilter'
import { ReportExportButtons } from '../../../components/reports/ReportExportButtons'
import type { PimsReportFilter, ReservationRosterReportResponse } from '../../../types/api'

/** PIMS_SPEC.md reports task: 100-Point Roster tab (representation-percentage summary - see ReservationRosterReportService's javadoc for scope). */
export function ReservationRosterTab() {
  const [departmentId, setDepartmentId] = useState('')

  const { data, isLoading, isError } = useQuery({
    queryKey: ['pims-reservation-roster', departmentId],
    queryFn: async () =>
      (
        await apiClient.get<ReservationRosterReportResponse>('/v1/reports/pims/reservation-roster', {
          params: { departmentId: departmentId || undefined },
        })
      ).data,
  })

  const filter: PimsReportFilter = { departmentId: departmentId ? Number(departmentId) : null }

  return (
    <div>
      <div className="mb-3 rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-500">
        Representation-percentage summary against DoPT/DPE model-roster targets, split by recruitment stream - not a vacancy-by-vacancy
        100-point register. Category data comes from each employee's social profile, which isn't yet captured during onboarding, so
        unrecorded employees default to "UR".
      </div>

      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
        <KpiCard label="Total Employees" value={data?.totalEmployees} tone="brand" />
        <KpiCard label="PwBD Count" value={data?.pwbdCount} tone="neutral" />
        <KpiCard label="PwBD %" value={data ? `${data.pwbdPercentage}% (target 4%)` : undefined} tone="neutral" />
      </div>

      <Card className="mb-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <DepartmentFilter value={departmentId} onChange={setDepartmentId} />
          <ReportExportButtons reportType="RESERVATION_ROSTER" filter={filter} />
        </div>
      </Card>

      <Card>
        {isLoading && <LoadingState label="Loading reservation roster..." />}
        {isError && <ErrorState message="Could not load the reservation roster." />}
        {data && data.rows.length === 0 && <EmptyState message="No data available." />}
        {data && data.rows.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Stream</th>
                  <th className="py-2 pr-3">Category</th>
                  <th className="py-2 pr-3 text-right">Count</th>
                  <th className="py-2 pr-3 text-right">Representation %</th>
                  <th className="py-2 pl-3 text-right">Statutory Target</th>
                </tr>
              </thead>
              <tbody>
                {data.rows.map((row) => {
                  const belowTarget = row.statutoryTargetPercentage != null && row.representationPercentage < row.statutoryTargetPercentage
                  return (
                    <tr key={`${row.recruitmentStream}-${row.socialCategory}`} className="border-b border-slate-100">
                      <td className="py-2 pr-3">
                        <Badge tone="neutral">{row.recruitmentStream.replace('_', ' ')}</Badge>
                      </td>
                      <td className="py-2 pr-3 font-medium">{row.socialCategory}</td>
                      <td className="py-2 pr-3 text-right tabular-nums">{row.count}</td>
                      <td className={`py-2 pr-3 text-right tabular-nums ${belowTarget ? 'text-red-600' : ''}`}>
                        {row.representationPercentage}%
                      </td>
                      <td className="py-2 pl-3 text-right tabular-nums text-slate-500">
                        {row.statutoryTargetPercentage != null ? `${row.statutoryTargetPercentage}%` : 'N/A'}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}
