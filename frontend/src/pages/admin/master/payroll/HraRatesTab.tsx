import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import { apiClient } from '../../../../api/client'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton } from '../../../../components/common/ui'
import { HraRateFormModal } from './HraRateFormModal'
import type { CityClass, PayrollHraRateResponse } from '../../../../types/api'

function rupees(amount: number): string {
  return `₹${amount.toLocaleString('en-IN')}`
}

function cityClassTone(cityClass: CityClass): 'brand' | 'success' | 'neutral' {
  if (cityClass === 'X') return 'brand'
  if (cityClass === 'Y') return 'success'
  return 'neutral'
}

/** HRA Rate Master - HRA percentage + minimum floor by city class (X/Y/Z), versioned by effective date range. */
export function HraRatesTab() {
  const [editing, setEditing] = useState<PayrollHraRateResponse | null>(null)
  const [adding, setAdding] = useState(false)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['payroll-hra-rates'],
    queryFn: async () => (await apiClient.get<PayrollHraRateResponse[]>('/v1/payroll/masters/hra-rates')).data,
  })

  return (
    <div>
      <PageHeader
        title="HRA Rate Master"
        description="House Rent Allowance percentage and minimum floor amount by city class"
        actions={
          <PrimaryButton onClick={() => setAdding(true)}>
            <Plus size={15} /> Add HRA Rate
          </PrimaryButton>
        }
      />

      <Card>
        {isLoading && <LoadingState label="Loading HRA rates..." />}
        {isError && <ErrorState message="Could not load HRA rates." />}
        {data && data.length === 0 && <EmptyState message="No HRA rates configured." />}

        {data && data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">City Class</th>
                  <th className="py-2 pr-3 text-right">Rate</th>
                  <th className="py-2 pr-3 text-right">Minimum Floor (₹)</th>
                  <th className="py-2 pr-3">Effective From</th>
                  <th className="py-2 pr-3">Effective To</th>
                  <th className="py-2 pr-3">Remarks</th>
                  <th className="py-2 pl-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.map((rate) => (
                  <tr key={rate.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3">
                      <Badge tone={cityClassTone(rate.cityClass)}>Class {rate.cityClass}</Badge>
                    </td>
                    <td className="py-2 pr-3 text-right tabular-nums">{rate.ratePercentage}%</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{rupees(rate.minAmount)}</td>
                    <td className="py-2 pr-3">{rate.effectiveFrom}</td>
                    <td className="py-2 pr-3">
                      {rate.effectiveTo ?? <Badge tone="success">Current</Badge>}
                    </td>
                    <td className="py-2 pr-3 text-slate-500">{rate.remarks ?? '—'}</td>
                    <td className="py-2 pl-3 text-right">
                      <button
                        type="button"
                        onClick={() => setEditing(rate)}
                        aria-label={`Edit HRA rate for class ${rate.cityClass} effective ${rate.effectiveFrom}`}
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
        <HraRateFormModal
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
