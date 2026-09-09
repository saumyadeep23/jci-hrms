import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import { apiClient } from '../../../../api/client'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton } from '../../../../components/common/ui'
import { TransportAllowanceFormModal } from './TransportAllowanceFormModal'
import type { CityClass, TransportAllowanceResponse } from '../../../../types/api'

function rupees(amount: number): string {
  return `₹${amount.toLocaleString('en-IN')}`
}

/** Transport Allowance Master - rate by grade scale + city class (X/Y/Z). No designation column: see TransportAllowanceResponse's own comment. */
export function TransportAllowanceTab() {
  const [cityFilter, setCityFilter] = useState<CityClass | 'ALL'>('ALL')
  const [editing, setEditing] = useState<TransportAllowanceResponse | null>(null)
  const [adding, setAdding] = useState(false)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['payroll-transport-allowances'],
    queryFn: async () => (await apiClient.get<TransportAllowanceResponse[]>('/v1/payroll/masters/transport-allowances')).data,
  })

  const filtered = useMemo(() => {
    const rows = data ?? []
    return cityFilter === 'ALL' ? rows : rows.filter((r) => r.cityClass === cityFilter)
  }, [data, cityFilter])

  return (
    <div>
      <PageHeader
        title="Transport Allowance Master"
        description="Monthly transport allowance by grade scale and city class"
        actions={
          <PrimaryButton onClick={() => setAdding(true)}>
            <Plus size={15} /> Add Rate
          </PrimaryButton>
        }
      />

      <Card>
        <div className="mb-4 flex items-center gap-2 text-xs text-slate-500">
          <span>City class</span>
          <select
            value={cityFilter}
            onChange={(e) => setCityFilter(e.target.value as CityClass | 'ALL')}
            className="rounded-md border border-slate-300 px-2 py-1"
          >
            <option value="ALL">All</option>
            <option value="X">X</option>
            <option value="Y">Y</option>
            <option value="Z">Z</option>
          </select>
        </div>

        {isLoading && <LoadingState label="Loading transport allowance rates..." />}
        {isError && <ErrorState message="Could not load transport allowance rates." />}
        {data && filtered.length === 0 && <EmptyState message="No transport allowance rates found." />}

        {data && filtered.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Grade Scale</th>
                  <th className="py-2 pr-3">City Class</th>
                  <th className="py-2 pr-3 text-right">Base Rate (₹/month)</th>
                  <th className="py-2 pr-3">Effective From</th>
                  <th className="py-2 pl-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((rate) => (
                  <tr key={rate.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3 font-medium">{rate.scaleCode ?? '—'}</td>
                    <td className="py-2 pr-3">
                      <Badge tone="neutral">Class {rate.cityClass}</Badge>
                    </td>
                    <td className="py-2 pr-3 text-right tabular-nums">{rupees(rate.baseRate)}</td>
                    <td className="py-2 pr-3">{rate.effectiveFrom}</td>
                    <td className="py-2 pl-3 text-right">
                      <button
                        type="button"
                        onClick={() => setEditing(rate)}
                        aria-label={`Edit rate for ${rate.scaleCode} class ${rate.cityClass}`}
                        className="inline-flex items-center justify-center rounded-md p-1.5 text-brand-forest hover:bg-slate-100"
                      >
                        <Pencil size={14} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {(adding || editing) && (
        <TransportAllowanceFormModal
          editing={editing}
          onClose={() => {
            setAdding(false)
            setEditing(null)
          }}
        />
      )}
    </div>
  )
}
