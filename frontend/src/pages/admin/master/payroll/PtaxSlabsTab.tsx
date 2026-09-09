import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import { apiClient } from '../../../../api/client'
import { Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton } from '../../../../components/common/ui'
import { PtaxSlabFormModal } from './PtaxSlabFormModal'
import type { PtaxSlabResponse } from '../../../../types/api'

const MONTH_NAMES = [
  '', 'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

function rupees(amount: number): string {
  return `₹${amount.toLocaleString('en-IN')}`
}

/** Professional Tax Slabs Master - grouped by state, each with its own bracket ladder and optional peak-surcharge month. */
export function PtaxSlabsTab() {
  const [editing, setEditing] = useState<PtaxSlabResponse | null>(null)
  const [adding, setAdding] = useState(false)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['payroll-ptax-slabs'],
    queryFn: async () => (await apiClient.get<PtaxSlabResponse[]>('/v1/payroll/masters/ptax-slabs')).data,
  })

  const byState = useMemo(() => {
    const groups = new Map<string, PtaxSlabResponse[]>()
    for (const slab of data ?? []) {
      const list = groups.get(slab.stateCode) ?? []
      list.push(slab)
      groups.set(slab.stateCode, list)
    }
    for (const list of groups.values()) {
      list.sort((a, b) => a.slabMin - b.slabMin)
    }
    return [...groups.entries()].sort(([a], [b]) => a.localeCompare(b))
  }, [data])

  return (
    <div>
      <PageHeader
        title="Professional Tax Slabs Master"
        description="P-Tax brackets by state, with an optional peak-surcharge month"
        actions={
          <PrimaryButton onClick={() => setAdding(true)}>
            <Plus size={15} /> Add Slab
          </PrimaryButton>
        }
      />

      {isLoading && (
        <Card>
          <LoadingState label="Loading P-Tax slabs..." />
        </Card>
      )}
      {isError && (
        <Card>
          <ErrorState message="Could not load P-Tax slabs." />
        </Card>
      )}
      {data && byState.length === 0 && (
        <Card>
          <EmptyState message="No P-Tax slabs configured." />
        </Card>
      )}

      {byState.map(([stateCode, slabs]) => (
        <Card key={stateCode} className="mb-4">
          <h3 className="mb-3 text-sm font-semibold text-slate-700">{stateCode}</h3>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Bracket</th>
                  <th className="py-2 pr-3 text-right">Monthly Deduction</th>
                  <th className="py-2 pr-3">Peak Month</th>
                  <th className="py-2 pr-3 text-right">Peak Deduction</th>
                  <th className="py-2 pl-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {slabs.map((slab) => (
                  <tr key={slab.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3 tabular-nums">
                      {rupees(slab.slabMin)} - {slab.slabMax != null ? rupees(slab.slabMax) : 'above'}
                    </td>
                    <td className="py-2 pr-3 text-right tabular-nums">{rupees(slab.taxAmount)}</td>
                    <td className="py-2 pr-3">{slab.specialMonth != null ? MONTH_NAMES[slab.specialMonth] : '—'}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{slab.specialMonthTax != null ? rupees(slab.specialMonthTax) : '—'}</td>
                    <td className="py-2 pl-3 text-right">
                      <button
                        type="button"
                        onClick={() => setEditing(slab)}
                        aria-label={`Edit ${stateCode} slab starting at ${slab.slabMin}`}
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
        </Card>
      ))}

      {(adding || editing) && (
        <PtaxSlabFormModal
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
