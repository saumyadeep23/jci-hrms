import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { Badge, Card, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../../components/common/ui'
import { describeApiError } from '../../../lib/apiError'
import type { JciEccsSettlementResponse } from '../../../types/api'

const inr = (n: number) => `₹${n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/** Route: /jcieccs/settlement/no-dues - GET/POST /api/jcieccs/settlements/{employeeId}. The JCIECCS
 * cooperative liability position for an employee's separation, integrated with the existing HR
 * exit-clearance workflow (ExitClearanceItem, department JCIECCS) as its 8th department - clearing here
 * clears that item via the same PENDING/CLEARED workflow every other department already uses, never a
 * second settlement engine. setoffAmount is always 0 - no approved JCIECCS set-off rule exists yet
 * (REQUIRES_BUSINESS_CONFIRMATION), so net liability is always the gross outstanding loan liability;
 * share/fund/security/thrift are shown alongside it for a human to apply the actual bylaws manually. */
export function SeparationClearancePage() {
  const [employeeIdInput, setEmployeeIdInput] = useState('')
  const [employeeId, setEmployeeId] = useState<string | null>(null)
  const [remarks, setRemarks] = useState('')
  const queryClient = useQueryClient()

  const settlementQuery = useQuery({
    queryKey: ['jcieccs-settlement', employeeId],
    queryFn: async () => (await apiClient.get<JciEccsSettlementResponse>(`/jcieccs/settlements/${employeeId}`)).data,
    enabled: employeeId !== null,
  })

  const recalculateMutation = useMutation({
    mutationFn: async () => (await apiClient.post<JciEccsSettlementResponse>(`/jcieccs/settlements/${employeeId}/calculate`)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['jcieccs-settlement', employeeId] }),
  })

  const clearMutation = useMutation({
    mutationFn: async () =>
      (await apiClient.post<JciEccsSettlementResponse>(`/jcieccs/settlements/${employeeId}/clear`, { remarks })).data,
    onSuccess: () => {
      setRemarks('')
      queryClient.invalidateQueries({ queryKey: ['jcieccs-settlement', employeeId] })
    },
  })

  const s = settlementQuery.data
  const isCleared = s?.exitClearanceItemStatus === 'CLEARED'
  const canClear = s !== undefined && s.netLiability === 0 && !isCleared

  return (
    <div>
      <PageHeader
        title="Separation Clearance"
        description="JCIECCS no-dues position at separation - integrated with the existing exit clearance checklist, never a duplicate engine"
      />

      <div className="mb-4 flex items-end gap-2">
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Employee ID</label>
          <input
            value={employeeIdInput}
            onChange={(e) => setEmployeeIdInput(e.target.value)}
            className="w-40 rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <PrimaryButton disabled={!employeeIdInput} onClick={() => setEmployeeId(employeeIdInput)}>
          Load Position
        </PrimaryButton>
        {employeeId && (
          <SecondaryButton disabled={recalculateMutation.isPending} onClick={() => recalculateMutation.mutate()}>
            {recalculateMutation.isPending ? 'Recalculating...' : 'Recalculate'}
          </SecondaryButton>
        )}
      </div>

      {settlementQuery.isLoading && <LoadingState label="Loading settlement position..." />}
      {settlementQuery.isError && (
        <ErrorState message={describeApiError(settlementQuery.error, 'No JCIECCS membership found for this employee.')} />
      )}

      {s && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <Card>
            <div className="mb-3 flex items-center justify-between">
              <p className="text-sm font-semibold text-slate-800">Assets / Balances</p>
              {s.stale && <Badge tone="warning">Stale - position changed since last clearance decision</Badge>}
            </div>
            <dl className="space-y-1 text-sm">
              <div className="flex justify-between">
                <dt className="text-slate-500">Share</dt>
                <dd className="font-medium text-slate-800">{inr(s.shareBalance)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-500">Fund</dt>
                <dd className="font-medium text-slate-800">{inr(s.fundBalance)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-500">Security</dt>
                <dd className="font-medium text-slate-800">{inr(s.securityBalance)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-500">Thrift</dt>
                <dd className="font-medium text-slate-800">{inr(s.thriftBalance)}</dd>
              </div>
            </dl>
            <p className="mt-3 text-[11px] text-slate-400">
              No approved JCIECCS set-off rule exists to net these balances against outstanding dues - they are shown for manual
              application of the actual bylaws only, never auto-applied.
            </p>
          </Card>

          <Card>
            <p className="mb-3 text-sm font-semibold text-slate-800">Liabilities</p>
            <dl className="space-y-1 text-sm">
              <div className="flex justify-between">
                <dt className="text-slate-500">TE Principal Outstanding</dt>
                <dd className="font-medium text-slate-800">{inr(s.termPrincipalOutstanding)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-500">TE Interest Outstanding</dt>
                <dd className="font-medium text-slate-800">{inr(s.termInterestOutstanding)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-500">EM Principal Outstanding</dt>
                <dd className="font-medium text-slate-800">{inr(s.emergencyPrincipalOutstanding)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-500">EM Interest Outstanding</dt>
                <dd className="font-medium text-slate-800">{inr(s.emergencyInterestOutstanding)}</dd>
              </div>
              <div className="flex justify-between border-t border-slate-200 pt-1">
                <dt className="font-semibold text-slate-700">Net Liability</dt>
                <dd className={`font-semibold ${s.netLiability > 0 ? 'text-red-600' : 'text-emerald-700'}`}>{inr(s.netLiability)}</dd>
              </div>
            </dl>

            <div className="mt-4 border-t border-slate-200 pt-3">
              {isCleared ? (
                <Badge tone="success">JCIECCS clearance: CLEARED</Badge>
              ) : (
                <>
                  <p className="mb-2 text-xs text-slate-500">
                    {s.exitClearanceRequestId
                      ? s.netLiability > 0
                        ? 'Clearance is blocked while an outstanding liability remains.'
                        : 'No outstanding liability - ready to clear.'
                      : 'No open exit clearance request found for this employee.'}
                  </p>
                  {s.exitClearanceRequestId && (
                    <>
                      <textarea
                        value={remarks}
                        onChange={(e) => setRemarks(e.target.value)}
                        placeholder="Remarks (required)"
                        className="mb-2 w-full rounded-md border border-slate-300 px-2 py-1 text-sm"
                        rows={2}
                      />
                      <PrimaryButton
                        type="button"
                        disabled={!canClear || !remarks.trim() || clearMutation.isPending}
                        onClick={() => clearMutation.mutate()}
                      >
                        {clearMutation.isPending ? 'Clearing...' : 'Clear JCIECCS No-Dues'}
                      </PrimaryButton>
                    </>
                  )}
                </>
              )}
              {clearMutation.isError && (
                <div className="mt-2">
                  <ErrorState message={describeApiError(clearMutation.error, 'Could not clear no-dues.')} />
                </div>
              )}
            </div>
          </Card>
        </div>
      )}
    </div>
  )
}
