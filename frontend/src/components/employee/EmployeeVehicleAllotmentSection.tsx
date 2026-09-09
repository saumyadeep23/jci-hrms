import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Car } from 'lucide-react'
import { apiClient } from '../../api/client'
import { Badge, ErrorState, LoadingState, PrimaryButton, SecondaryButton } from '../common/ui'
import { VehicleAllotmentFormModal } from './VehicleAllotmentFormModal'
import { VehicleSurrenderModal } from './VehicleSurrenderModal'
import type { VehicleAllotmentResponse } from '../../types/api'

function rupees(amount: number): string {
  return `₹${amount.toLocaleString('en-IN')}`
}

/** Employment tab's "Official Vehicle Provided" sub-panel - the toggle state is derived from whether an ACTIVE allotment exists (kept in sync by EmployeeVehicleAllotmentService on the backend), not settable directly. */
export function EmployeeVehicleAllotmentSection({ employeeId }: { employeeId: number }) {
  const [modal, setModal] = useState<'closed' | 'allot' | 'surrender'>('closed')

  const listQuery = useQuery({
    queryKey: ['employee-vehicle-allotments', employeeId],
    queryFn: async () => (await apiClient.get<VehicleAllotmentResponse[]>(`/v1/employees/${employeeId}/vehicle-allotments`)).data,
  })

  if (listQuery.isLoading) return <LoadingState label="Loading vehicle allotments..." />
  if (listQuery.isError) return <ErrorState message="Could not load vehicle allotments." />

  const rows = listQuery.data ?? []
  const active = rows.find((r) => r.status === 'ACTIVE') ?? null
  const history = rows.filter((r) => r.status !== 'ACTIVE')

  return (
    <div className="rounded-md border border-slate-200 p-3">
      <div className="mb-3 flex items-center justify-between">
        <span className="flex items-center gap-2 text-sm font-medium text-slate-700">
          <Car size={16} /> Official Vehicle Provided
        </span>
        <Badge tone={active ? 'success' : 'neutral'}>{active ? 'Yes' : 'No'}</Badge>
      </div>

      <div className="mb-3 flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
        <span>Official vehicle assignment suppresses Transport Allowance (Head 10) to ₹0.00.</span>
      </div>

      {active && (
        <div className="mb-3 grid grid-cols-2 gap-2 rounded-md border border-slate-200 bg-slate-50 p-3 text-sm">
          <div>
            <p className="text-xs text-slate-500">Vehicle Reg No.</p>
            <p className="font-medium">{active.vehicleRegNo}</p>
          </div>
          <div>
            <p className="text-xs text-slate-500">Model</p>
            <p className="font-medium">{active.vehicleMakeModel ?? '—'}</p>
          </div>
          <div>
            <p className="text-xs text-slate-500">Allotted Date</p>
            <p className="font-medium">{active.allottedFrom}</p>
          </div>
          <div>
            <p className="text-xs text-slate-500">Monthly Recovery</p>
            <p className="font-medium tabular-nums">{active.deductionApplicable ? rupees(active.monthlyDeductionAmount) : '—'}</p>
          </div>
        </div>
      )}

      <div className="mb-3 flex gap-2">
        {!active && (
          <PrimaryButton type="button" onClick={() => setModal('allot')}>
            Allot Vehicle
          </PrimaryButton>
        )}
        {active && (
          <SecondaryButton type="button" onClick={() => setModal('surrender')}>
            Surrender Vehicle
          </SecondaryButton>
        )}
      </div>

      {history.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[520px] border-collapse text-xs">
            <thead>
              <tr className="border-b border-slate-200 text-left text-slate-500">
                <th className="py-1.5 pr-3">Reg No.</th>
                <th className="py-1.5 pr-3">Model</th>
                <th className="py-1.5 pr-3">Allotted</th>
                <th className="py-1.5 pr-3">Surrendered</th>
                <th className="py-1.5 pr-3">Status</th>
              </tr>
            </thead>
            <tbody>
              {history.map((row) => (
                <tr key={row.id} className="border-b border-slate-100 last:border-b-0">
                  <td className="py-1.5 pr-3">{row.vehicleRegNo}</td>
                  <td className="py-1.5 pr-3 text-slate-500">{row.vehicleMakeModel ?? '—'}</td>
                  <td className="py-1.5 pr-3">{row.allottedFrom}</td>
                  <td className="py-1.5 pr-3">{row.surrenderedOn ?? '—'}</td>
                  <td className="py-1.5 pr-3">{row.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {modal === 'allot' && <VehicleAllotmentFormModal employeeId={employeeId} onClose={() => setModal('closed')} />}
      {modal === 'surrender' && active && (
        <VehicleSurrenderModal employeeId={employeeId} allotment={active} onClose={() => setModal('closed')} />
      )}
    </div>
  )
}
