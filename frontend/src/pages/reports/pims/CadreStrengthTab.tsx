import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { Badge, Card, EmptyState, ErrorState, LoadingState } from '../../../components/common/ui'
import { KpiCard } from '../../../components/reports/KpiCard'
import { DepartmentFilter } from '../../../components/reports/DepartmentFilter'
import { ReportExportButtons } from '../../../components/reports/ReportExportButtons'
import type { CadreStrengthReportResponse, DpcResponse, Page, PimsReportFilter, RegionalOfficeResponse } from '../../../types/api'

/** PIMS_SPEC.md reports task: Cadre Strength Statement tab. */
export function CadreStrengthTab() {
  const [departmentId, setDepartmentId] = useState('')
  const [roId, setRoId] = useState('')
  const [dpcId, setDpcId] = useState('')

  const regionalOfficesQuery = useQuery({
    queryKey: ['ro-masters-all'],
    queryFn: async () => (await apiClient.get<Page<RegionalOfficeResponse>>('/regional-offices', { params: { size: 200 } })).data.content,
  })
  const dpcsQuery = useQuery({
    queryKey: ['dpc-masters-for-ro', roId],
    queryFn: async () => (await apiClient.get<Page<DpcResponse>>('/dpcs', { params: { size: 500 } })).data.content,
    enabled: roId !== '',
  })
  const dpcOptions = (dpcsQuery.data ?? []).filter((d) => String(d.roId) === roId)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['pims-cadre-strength', departmentId, roId, dpcId],
    queryFn: async () =>
      (
        await apiClient.get<CadreStrengthReportResponse>('/v1/reports/pims/cadre-strength', {
          params: { departmentId: departmentId || undefined, roId: roId || undefined, dpcId: dpcId || undefined },
        })
      ).data,
  })

  const filter: PimsReportFilter = {
    departmentId: departmentId ? Number(departmentId) : null,
    roId: roId ? Number(roId) : null,
    dpcId: dpcId ? Number(dpcId) : null,
  }

  return (
    <div>
      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <KpiCard label="Sanctioned" value={data?.overall.sanctioned} tone="brand" />
        <KpiCard label="Occupied" value={data?.overall.occupied} tone="success" />
        <KpiCard label="Vacant" value={data?.overall.vacant} tone="warning" />
        <KpiCard label="Occupancy %" value={data ? `${data.overall.occupancyPercentage}%` : undefined} tone="neutral" />
      </div>

      <Card className="mb-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap gap-2">
            <DepartmentFilter value={departmentId} onChange={setDepartmentId} />
            <select
              value={roId}
              onChange={(e) => {
                setRoId(e.target.value)
                setDpcId('')
              }}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="">All Regional Offices</option>
              {(regionalOfficesQuery.data ?? []).map((ro) => (
                <option key={ro.id} value={ro.id}>
                  {ro.code} - {ro.name}
                </option>
              ))}
            </select>
            <select
              value={dpcId}
              onChange={(e) => setDpcId(e.target.value)}
              disabled={!roId}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-50"
            >
              <option value="">{roId ? 'All DPCs' : 'Select an RO first'}</option>
              {dpcOptions.map((dpc) => (
                <option key={dpc.id} value={dpc.id}>
                  {dpc.code} - {dpc.name}
                </option>
              ))}
            </select>
          </div>
          <ReportExportButtons reportType="CADRE_STRENGTH" filter={filter} />
        </div>
      </Card>

      <Card>
        {isLoading && <LoadingState label="Loading cadre strength..." />}
        {isError && <ErrorState message="Could not load the cadre strength statement." />}
        {data && data.byLocation.length === 0 && <EmptyState message="No posts found." />}
        {data && data.byLocation.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Location</th>
                  <th className="py-2 pr-3 text-right">Sanctioned</th>
                  <th className="py-2 pr-3 text-right">Occupied</th>
                  <th className="py-2 pr-3 text-right">Vacant</th>
                  <th className="py-2 pr-3 text-right">Frozen</th>
                  <th className="py-2 pl-3 text-right">Occupancy</th>
                </tr>
              </thead>
              <tbody>
                {data.byLocation.map((row) => (
                  <tr key={row.locationType} className="border-b border-slate-100">
                    <td className="py-2 pr-3">
                      <Badge tone="brand">{row.locationType.replace('_', ' ')}</Badge>
                    </td>
                    <td className="py-2 pr-3 text-right tabular-nums">{row.sanctioned}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{row.occupied}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{row.vacant}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{row.frozen}</td>
                    <td className="py-2 pl-3 text-right tabular-nums">{row.occupancyPercentage}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}
