import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { ErrorState, PageHeader, PrimaryButton } from '../../../components/common/ui'
import { describeApiError } from '../../../lib/apiError'
import type { JciEccsCollectionBatchResponse } from '../../../types/api'

/**
 * Route: /jcieccs/payroll-recovery/demands - POST /api/jcieccs/payroll/batches/{payrollRunId}/snapshot
 * (JciEccsCollectionSnapshotService). payrollRunId is the PayrollBatch id. Idempotent: a repeat call for
 * the same payrollRunId returns the already-locked snapshot without recalculating. Columns map directly
 * to the payroll salary heads this snapshot feeds: Thrift = head 47, Term Principal = head 52,
 * Term Interest = head 53, Emergency Principal = head 54, Emergency Interest = head 55.
 */
export function PayrollDemandsPage() {
  const [payrollRunId, setPayrollRunId] = useState('')

  const snapshotMutation = useMutation({
    mutationFn: async () => (await apiClient.post<JciEccsCollectionBatchResponse>(`/jcieccs/payroll/batches/${payrollRunId}/snapshot`)).data,
  })

  const batch = snapshotMutation.data

  return (
    <div>
      <PageHeader title="Monthly Demand Schedules" description="Request/review the locked JCIECCS collection snapshot for a payroll batch (heads 47, 52, 53, 54, 55)" />

      <div className="mb-4 flex items-end gap-2">
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Payroll Run ID (Payroll Batch ID)</label>
          <input
            value={payrollRunId}
            onChange={(e) => setPayrollRunId(e.target.value)}
            className="w-56 rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <PrimaryButton disabled={!payrollRunId || snapshotMutation.isPending} onClick={() => snapshotMutation.mutate()}>
          {snapshotMutation.isPending ? 'Requesting...' : 'Request / Review Snapshot'}
        </PrimaryButton>
      </div>

      {snapshotMutation.isError && <ErrorState message={describeApiError(snapshotMutation.error, 'Could not load/create this snapshot.')} />}

      {batch && (
        <div>
          <div className="mb-3 flex gap-4 text-sm text-slate-600">
            <span>
              Cycle: <strong>{batch.cycleCode}</strong>
            </span>
            <span>
              Status: <strong>{batch.batchStatus}</strong>
            </span>
            <span>
              Total Expected: <strong>₹{batch.totalExpectedAmount.toFixed(2)}</strong>
            </span>
          </div>
          <div className="overflow-x-auto rounded-md border border-slate-200 bg-white">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-3 py-2">Employee ID</th>
                  <th className="px-3 py-2 text-right">Thrift (Head 47)</th>
                  <th className="px-3 py-2 text-right">Term Principal (Head 52)</th>
                  <th className="px-3 py-2 text-right">Term Interest (Head 53)</th>
                  <th className="px-3 py-2 text-right">Emergency Principal (Head 54)</th>
                  <th className="px-3 py-2 text-right">Emergency Interest (Head 55)</th>
                </tr>
              </thead>
              <tbody>
                {batch.details.map((d) => (
                  <tr key={d.id} className="border-b border-slate-100 last:border-0">
                    <td className="px-3 py-2">{d.employeeId}</td>
                    <td className="px-3 py-2 text-right">{d.thriftAmount.toFixed(2)}</td>
                    <td className="px-3 py-2 text-right">{d.termPrincipal}</td>
                    <td className="px-3 py-2 text-right">{d.termInterest.toFixed(2)}</td>
                    <td className="px-3 py-2 text-right">{d.emergencyPrincipal}</td>
                    <td className="px-3 py-2 text-right">{d.emergencyInterest.toFixed(2)}</td>
                  </tr>
                ))}
                {batch.details.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-3 py-6 text-center text-sm text-slate-400">
                      No members have any JCIECCS dues this cycle.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}
