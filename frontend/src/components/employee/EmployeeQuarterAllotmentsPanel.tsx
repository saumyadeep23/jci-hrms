import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Home, Pencil, Plus } from 'lucide-react'
import { apiClient } from '../../api/client'
import { Badge, EmptyState, ErrorState, LoadingState, SecondaryButton } from '../common/ui'
import { EmployeeQuarterAllotmentFormModal } from './EmployeeQuarterAllotmentFormModal'
import type { QuarterAllotmentResponse, QuarterAllotmentStatus } from '../../types/api'

function rupees(amount: number): string {
  return `₹${amount.toLocaleString('en-IN')}`
}

function statusTone(status: QuarterAllotmentStatus): 'success' | 'neutral' | 'warning' {
  if (status === 'OCCUPIED') return 'success'
  if (status === 'VACATED' || status === 'SURRENDERED') return 'neutral'
  return 'warning'
}

/** Employee Company Accommodation (Onboarding/Edit Tab 9) - JCI leases/provides accommodation at an address (it owns no quarters), current and historical allotments live against EmployeeQuarterAllotmentController. */
export function EmployeeQuarterAllotmentsPanel({ employeeId }: { employeeId: number }) {
  const [modalState, setModalState] = useState<'closed' | 'adding' | QuarterAllotmentResponse>('closed')

  const listQuery = useQuery({
    queryKey: ['employee-quarter-allotments', employeeId],
    queryFn: async () => (await apiClient.get<QuarterAllotmentResponse[]>(`/v1/employees/${employeeId}/quarter-allotments`)).data,
  })

  if (listQuery.isLoading) return <LoadingState label="Loading accommodation allotments..." />
  if (listQuery.isError) return <ErrorState message="Could not load accommodation allotments." />

  const rows = listQuery.data ?? []
  const editing = modalState !== 'closed' && modalState !== 'adding' ? modalState : null

  return (
    <div className="space-y-3">
      <div className="flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
        <Home size={14} className="mt-0.5 shrink-0" />
        <span>Providing company-leased accommodation suppresses HRA (Head 9) to ₹0.00 during the occupancy period.</span>
      </div>

      {rows.length === 0 ? (
        <EmptyState message="No company accommodation allotted." />
      ) : (
        <div className="overflow-x-auto rounded-md border border-slate-200">
          <table className="w-full min-w-[760px] border-collapse text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50 text-left text-slate-500">
                <th className="px-3 py-2">Address</th>
                <th className="px-3 py-2 text-right">Monthly Recoveries</th>
                <th className="px-3 py-2">Allotted From</th>
                <th className="px-3 py-2">Vacated On</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id} className="border-b border-slate-100 last:border-b-0">
                  <td className="px-3 py-2">
                    <div className="font-medium">{row.addressLine1}</div>
                    <div className="text-xs text-slate-400">
                      {[row.city, row.stateCode, row.pincode].filter(Boolean).join(', ') || '—'}
                    </div>
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums">
                    {rupees(row.licenseFee + row.waterCharges + row.electricCharges)}
                  </td>
                  <td className="px-3 py-2">{row.allottedFrom}</td>
                  <td className="px-3 py-2">{row.vacatedOn ?? '—'}</td>
                  <td className="px-3 py-2">
                    <Badge tone={statusTone(row.status)}>{row.status}</Badge>
                  </td>
                  <td className="px-3 py-2 text-right">
                    <button
                      type="button"
                      onClick={() => setModalState(row)}
                      aria-label={`Edit allotment at ${row.addressLine1}`}
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

      <SecondaryButton type="button" onClick={() => setModalState('adding')}>
        <Plus size={14} /> Allot Accommodation
      </SecondaryButton>

      {(modalState === 'adding' || editing) && (
        <EmployeeQuarterAllotmentFormModal employeeId={employeeId} editing={editing} onClose={() => setModalState('closed')} />
      )}
    </div>
  )
}
