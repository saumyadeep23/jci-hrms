import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import { apiClient } from '../../../../api/client'
import { Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton } from '../../../../components/common/ui'
import { ProcurementAllowanceFormModal } from './ProcurementAllowanceFormModal'
import type { ProcurementAllowanceResponse } from '../../../../types/api'

function rupees(amount: number): string {
  return `₹${amount.toLocaleString('en-IN')}`
}

/** Procurement Allowance Master - monthly allowance for field-cadre designations. */
export function ProcurementAllowanceTab() {
  const [editing, setEditing] = useState<ProcurementAllowanceResponse | null>(null)
  const [adding, setAdding] = useState(false)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['payroll-procurement-allowances'],
    queryFn: async () => (await apiClient.get<ProcurementAllowanceResponse[]>('/v1/payroll/masters/procurement-allowances')).data,
  })

  return (
    <div>
      <PageHeader
        title="Procurement Allowance Master"
        description="Monthly allowance for field-cadre designations engaged in procurement duties"
        actions={
          <PrimaryButton onClick={() => setAdding(true)}>
            <Plus size={15} /> Add Allowance
          </PrimaryButton>
        }
      />

      <Card>
        {isLoading && <LoadingState label="Loading procurement allowances..." />}
        {isError && <ErrorState message="Could not load procurement allowances." />}
        {data && data.length === 0 && <EmptyState message="No procurement allowances configured." />}

        {data && data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Designation</th>
                  <th className="py-2 pr-3 text-right">Monthly Allowance (₹)</th>
                  <th className="py-2 pr-3">Effective From</th>
                  <th className="py-2 pr-3">Effective To</th>
                  <th className="py-2 pl-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.map((rate) => (
                  <tr key={rate.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3 font-medium">{rate.designationTitle}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{rupees(rate.monthlyAllowance)}</td>
                    <td className="py-2 pr-3">{rate.effectiveFrom}</td>
                    <td className="py-2 pr-3">{rate.effectiveTo ?? '—'}</td>
                    <td className="py-2 pl-3 text-right">
                      <button
                        type="button"
                        onClick={() => setEditing(rate)}
                        aria-label={`Edit procurement allowance for ${rate.designationTitle}`}
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
        <ProcurementAllowanceFormModal
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
