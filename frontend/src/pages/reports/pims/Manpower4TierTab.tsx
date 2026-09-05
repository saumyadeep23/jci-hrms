import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { Badge, Card, EmptyState, ErrorState, LoadingState } from '../../../components/common/ui'
import { KpiCard } from '../../../components/reports/KpiCard'
import { DepartmentFilter } from '../../../components/reports/DepartmentFilter'
import { ReportExportButtons } from '../../../components/reports/ReportExportButtons'
import type { Manpower4TierReportResponse, PimsReportFilter } from '../../../types/api'

const TIER_TONE: Record<string, 'brand' | 'success' | 'warning' | 'neutral'> = {
  REGULAR: 'brand',
  CASUAL: 'warning',
  CONTRACTUAL: 'success',
  OUTSOURCED: 'neutral',
}

/** PIMS_SPEC.md reports task: 4-Tier Manpower tab. */
export function Manpower4TierTab() {
  const [departmentId, setDepartmentId] = useState('')

  const { data, isLoading, isError } = useQuery({
    queryKey: ['pims-manpower-4tier', departmentId],
    queryFn: async () =>
      (
        await apiClient.get<Manpower4TierReportResponse>('/v1/reports/pims/manpower-4tier', {
          params: { departmentId: departmentId || undefined },
        })
      ).data,
  })

  const filter: PimsReportFilter = { departmentId: departmentId ? Number(departmentId) : null }

  return (
    <div>
      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
        <KpiCard label="Total Headcount" value={data?.totalHeadcount} tone="brand" />
        <KpiCard
          label="Total Outsourced Vendor Billing"
          value={data ? data.totalOutsourcedVendorBilling.toLocaleString('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }) : undefined}
          tone="neutral"
        />
        <KpiCard label="Tiers" value={data?.tiers.length} tone="neutral" />
      </div>

      <Card className="mb-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <DepartmentFilter value={departmentId} onChange={setDepartmentId} />
          <ReportExportButtons reportType="MANPOWER_4TIER" filter={filter} />
        </div>
      </Card>

      <Card>
        {isLoading && <LoadingState label="Loading 4-tier manpower report..." />}
        {isError && <ErrorState message="Could not load the manpower report." />}
        {data && data.tiers.length === 0 && <EmptyState message="No employment data found." />}
        {data && data.tiers.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Tier</th>
                  <th className="py-2 pr-3">Cost Basis</th>
                  <th className="py-2 pr-3 text-right">Headcount</th>
                  <th className="py-2 pr-3 text-right">Total Cost</th>
                  <th className="py-2 pl-3 text-right">Average Cost</th>
                </tr>
              </thead>
              <tbody>
                {data.tiers.map((t) => (
                  <tr key={t.employmentCategory} className="border-b border-slate-100">
                    <td className="py-2 pr-3">
                      <Badge tone={TIER_TONE[t.employmentCategory] ?? 'neutral'}>{t.employmentCategory}</Badge>
                    </td>
                    <td className="py-2 pr-3 text-xs text-slate-500">{t.costBasis}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{t.headcount}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{t.totalCost.toLocaleString('en-IN')}</td>
                    <td className="py-2 pl-3 text-right tabular-nums">{t.averageCost.toLocaleString('en-IN')}</td>
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
