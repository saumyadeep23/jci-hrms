import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Calculator, CheckCircle2, RotateCcw, ShieldAlert } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { useAuth } from '../../../auth/AuthContext'
import { describeApiError } from '../../../lib/apiError'
import { notifyToast } from '../../../lib/toastBridge'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../../components/common/ui'
import type {
  CpfAnnualInterestRunResponse,
  CpfInterestCalculateRequest,
  CpfInterestCalculationPreviewResponse,
  CpfInterestFinancialYearStatusResponse,
  CpfInterestMemberBreakdown,
  CpfInterestRunScope,
} from '../../../types/api'

const RUNS_BASE = '/v1/payroll/trust/interest/runs'

function inr(value: number): string {
  return `₹${value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function statusTone(status: CpfInterestFinancialYearStatusResponse['status']): 'neutral' | 'success' | 'warning' | 'danger' | 'brand' {
  switch (status) {
    case 'POSTED':
      return 'success'
    case 'CALCULATED':
      return 'brand'
    case 'REVERSED':
      return 'warning'
    case 'FAILED':
      return 'danger'
    default:
      return 'neutral'
  }
}

/**
 * CPF Interest Management - the admin-controlled Calculate -> Approve/Post -> Reverse -> Recalculate
 * workflow for year-end ANNUAL_INTEREST (CpfInterestRunController/CpfInterestRunService). Distinct from
 * CpfInterestRateEntryPage (the notification master this module's rate is resolved from - not editable
 * here) and from the generic /audit-logs viewer (every calculate/post/reverse transition on a run is
 * captured there automatically, since CpfAnnualInterestRun is Auditable - not duplicated as a bespoke log
 * on this page).
 *
 * Calculate/Post/Reverse are all narrowed to CPF_ADMIN/SUPER_ADMIN on the backend (same convention as
 * CpfInterestRateEntryPage's own canCreate gate) - FINANCE_ADMIN can view this whole page but the action
 * buttons are hidden rather than left to 403 at click time.
 */
export function CpfInterestManagementPage() {
  const { hasRole } = useAuth()
  const canAct = hasRole('CPF_ADMIN', 'SUPER_ADMIN')
  const queryClient = useQueryClient()

  const [calculateFor, setCalculateFor] = useState<CpfInterestFinancialYearStatusResponse | null>(null)
  const [selectedRunId, setSelectedRunId] = useState<number | null>(null)
  const [reversingRunId, setReversingRunId] = useState<number | null>(null)
  const [memberLookupId, setMemberLookupId] = useState('')
  const [memberLookupSubmitted, setMemberLookupSubmitted] = useState<number | null>(null)

  const yearsQuery = useQuery({
    queryKey: ['cpf-interest-years'],
    queryFn: async () => (await apiClient.get<CpfInterestFinancialYearStatusResponse[]>(`${RUNS_BASE}/years`)).data,
  })

  const runsQuery = useQuery({
    queryKey: ['cpf-interest-runs'],
    queryFn: async () => (await apiClient.get<CpfAnnualInterestRunResponse[]>(RUNS_BASE)).data,
  })

  function invalidateAll() {
    queryClient.invalidateQueries({ queryKey: ['cpf-interest-years'] })
    queryClient.invalidateQueries({ queryKey: ['cpf-interest-runs'] })
  }

  return (
    <div>
      <PageHeader
        title="CPF Interest Management"
        description="Calculate, review, post, and reverse CPF annual interest by financial year or member - the same CpfInterestCalculator formula every passbook projection and settlement uses."
      />

      <Card className="mb-6">
        <h2 className="mb-3 text-sm font-semibold text-slate-700">Financial Year Dashboard</h2>
        {yearsQuery.isLoading && <LoadingState label="Loading financial years..." />}
        {yearsQuery.isError && <ErrorState message={describeApiError(yearsQuery.error, 'Failed to load financial years.')} />}
        {yearsQuery.data && yearsQuery.data.length === 0 && <EmptyState message="No CPF ledger data exists yet." />}
        {yearsQuery.data && yearsQuery.data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[900px] text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                  <th className="py-2 pr-3">Financial Year</th>
                  <th className="py-2 pr-3">Rate</th>
                  <th className="py-2 pr-3">Status</th>
                  <th className="py-2 pr-3">Members</th>
                  <th className="py-2 pr-3">Total Interest</th>
                  <th className="py-2 pr-3">Note</th>
                  <th className="py-2 pr-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {yearsQuery.data.map((fy) => (
                  <tr key={fy.finYear} className="border-b border-slate-100 last:border-0">
                    <td className="py-2 pr-3 font-medium text-slate-800">{fy.finYear}</td>
                    <td className="py-2 pr-3">{fy.configuredRate != null ? `${fy.configuredRate}%` : <span className="text-slate-400">Not configured</span>}</td>
                    <td className="py-2 pr-3">
                      <Badge tone={statusTone(fy.status)}>{fy.status ?? 'NOT_CALCULATED'}</Badge>
                    </td>
                    <td className="py-2 pr-3">{fy.membersProcessed.toLocaleString('en-IN')}</td>
                    <td className="py-2 pr-3">{inr(fy.totalInterestPosted)}</td>
                    <td className="py-2 pr-3 text-xs text-slate-500">{fy.dependencyBlockedReason ?? '—'}</td>
                    <td className="py-2 pr-3 text-right">
                      <div className="flex justify-end gap-2">
                        {canAct && fy.status == null && fy.configuredRate != null && (
                          <SecondaryButton onClick={() => setCalculateFor(fy)}>
                            <Calculator size={14} /> Calculate
                          </SecondaryButton>
                        )}
                        {fy.activeRunId != null && (
                          <SecondaryButton onClick={() => setSelectedRunId(fy.activeRunId)}>View</SecondaryButton>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card className="mb-6">
        <h2 className="mb-3 text-sm font-semibold text-slate-700">Interest Runs</h2>
        {runsQuery.isLoading && <LoadingState label="Loading runs..." />}
        {runsQuery.isError && <ErrorState message={describeApiError(runsQuery.error, 'Failed to load interest runs.')} />}
        {runsQuery.data && runsQuery.data.length === 0 && <EmptyState message="No interest has been calculated yet." />}
        {runsQuery.data && runsQuery.data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[900px] text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                  <th className="py-2 pr-3">Run</th>
                  <th className="py-2 pr-3">FY</th>
                  <th className="py-2 pr-3">Scope</th>
                  <th className="py-2 pr-3">Status</th>
                  <th className="py-2 pr-3">Total Interest</th>
                  <th className="py-2 pr-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {runsQuery.data.map((run) => (
                  <tr key={run.id} className="border-b border-slate-100 last:border-0">
                    <td className="py-2 pr-3 font-medium text-slate-800">#{run.id}</td>
                    <td className="py-2 pr-3">{run.finYear}</td>
                    <td className="py-2 pr-3">{run.scope === 'SELECTED_MEMBER' ? `Member #${run.memberEmployeeId}` : 'All Members'}</td>
                    <td className="py-2 pr-3">
                      <Badge tone={statusTone(run.status)}>{run.status}</Badge>
                      {run.dataReviewRequired && (
                        <span className="ml-2 inline-flex items-center gap-1 text-xs font-medium text-amber-600">
                          <ShieldAlert size={12} /> Data review
                        </span>
                      )}
                    </td>
                    <td className="py-2 pr-3">{inr(run.totalInterestCreditedEe + run.totalInterestCreditedEr + run.totalInterestCreditedVpf)}</td>
                    <td className="py-2 pr-3 text-right">
                      <div className="flex justify-end gap-2">
                        <SecondaryButton onClick={() => setSelectedRunId(run.id)}>View</SecondaryButton>
                        {canAct && run.status === 'POSTED' && (
                          <SecondaryButton onClick={() => setReversingRunId(run.id)}>
                            <RotateCcw size={14} /> Reverse
                          </SecondaryButton>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card>
        <h2 className="mb-3 text-sm font-semibold text-slate-700">Member Interest</h2>
        <form
          className="mb-4 flex flex-wrap items-end gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            const id = Number(memberLookupId)
            if (Number.isFinite(id) && id > 0) setMemberLookupSubmitted(id)
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Employee ID</label>
            <input
              className="w-40 rounded-md border border-slate-300 px-3 py-2 text-sm"
              value={memberLookupId}
              onChange={(e) => setMemberLookupId(e.target.value)}
              placeholder="e.g. 108"
            />
          </div>
          <PrimaryButton type="submit">Search</PrimaryButton>
        </form>
        {memberLookupSubmitted != null && <MemberInterestHistory employeeId={memberLookupSubmitted} />}
      </Card>

      {calculateFor && (
        <CalculateInterestModal
          fy={calculateFor}
          onClose={() => setCalculateFor(null)}
          onCalculated={(runId) => {
            setCalculateFor(null)
            invalidateAll()
            setSelectedRunId(runId)
          }}
        />
      )}

      {selectedRunId != null && (
        <RunDetailModal
          runId={selectedRunId}
          canAct={canAct}
          onClose={() => setSelectedRunId(null)}
          onPosted={() => {
            invalidateAll()
          }}
          onReverseRequested={() => {
            setReversingRunId(selectedRunId)
          }}
        />
      )}

      {reversingRunId != null && (
        <ReverseRunModal
          runId={reversingRunId}
          onClose={() => setReversingRunId(null)}
          onReversed={() => {
            setReversingRunId(null)
            setSelectedRunId(null)
            invalidateAll()
          }}
        />
      )}
    </div>
  )
}

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="max-h-[85vh] w-full max-w-3xl overflow-y-auto rounded-xl bg-white p-6 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-semibold text-slate-800">{title}</h3>
          <button className="text-sm text-slate-500 hover:text-slate-700" onClick={onClose}>
            Close
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

function CalculateInterestModal({
  fy,
  onClose,
  onCalculated,
}: {
  fy: CpfInterestFinancialYearStatusResponse
  onClose: () => void
  onCalculated: (runId: number) => void
}) {
  const [scope, setScope] = useState<CpfInterestRunScope>('ALL_MEMBERS')
  const [employeeId, setEmployeeId] = useState('')
  const [interestOrderNo, setInterestOrderNo] = useState('')
  const [interestOrderDate, setInterestOrderDate] = useState('')

  const calculateMutation = useMutation({
    mutationFn: async () => {
      const body: CpfInterestCalculateRequest = {
        finYear: fy.finYear,
        scope,
        employeeId: scope === 'SELECTED_MEMBER' ? Number(employeeId) : null,
        interestOrderNo,
        interestOrderDate,
      }
      return (await apiClient.post<CpfInterestCalculationPreviewResponse>(`${RUNS_BASE}/calculate`, body)).data
    },
    onSuccess: (preview) => {
      notifyToast({ tone: 'success', title: 'Calculation complete', message: `${preview.memberCount} member(s), total statutory interest ${inr(preview.totalStatutoryInterest)}.` })
      onCalculated(preview.runId)
    },
    onError: (error) => {
      notifyToast({ tone: 'error', title: 'Calculation failed', message: describeApiError(error, 'Could not calculate interest.') })
    },
  })

  return (
    <Modal title={`Calculate CPF Interest - FY ${fy.finYear}`} onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-slate-600">
          Notified rate: <span className="font-medium">{fy.configuredRate}%</span> (read-only, resolved from Interest Rate Configuration)
        </p>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Calculation Scope</label>
          <div className="flex gap-4 text-sm">
            <label className="flex items-center gap-2">
              <input type="radio" checked={scope === 'ALL_MEMBERS'} onChange={() => setScope('ALL_MEMBERS')} /> All Eligible Members
            </label>
            <label className="flex items-center gap-2">
              <input type="radio" checked={scope === 'SELECTED_MEMBER'} onChange={() => setScope('SELECTED_MEMBER')} /> Selected Member
            </label>
          </div>
        </div>

        {scope === 'SELECTED_MEMBER' && (
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Employee ID</label>
            <input className="w-48 rounded-md border border-slate-300 px-3 py-2 text-sm" value={employeeId} onChange={(e) => setEmployeeId(e.target.value)} />
          </div>
        )}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Interest Order No.</label>
            <input className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" value={interestOrderNo} onChange={(e) => setInterestOrderNo(e.target.value)} />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Interest Order Date</label>
            <input type="date" className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" value={interestOrderDate} onChange={(e) => setInterestOrderDate(e.target.value)} />
          </div>
        </div>

        {fy.dependencyBlockedReason && (
          <p className="flex items-center gap-2 rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-700">
            <AlertTriangle size={14} /> {fy.dependencyBlockedReason}
          </p>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
          <PrimaryButton
            disabled={!interestOrderNo || !interestOrderDate || (scope === 'SELECTED_MEMBER' && !employeeId) || calculateMutation.isPending}
            onClick={() => calculateMutation.mutate()}
          >
            {calculateMutation.isPending ? 'Calculating...' : 'Calculate Preview'}
          </PrimaryButton>
        </div>
      </div>
    </Modal>
  )
}

function RunDetailModal({
  runId,
  canAct,
  onClose,
  onPosted,
  onReverseRequested,
}: {
  runId: number
  canAct: boolean
  onClose: () => void
  onPosted: () => void
  onReverseRequested: () => void
}) {
  const [acknowledgeDataReview, setAcknowledgeDataReview] = useState(false)

  const runQuery = useQuery({
    queryKey: ['cpf-interest-run', runId],
    queryFn: async () => (await apiClient.get<CpfAnnualInterestRunResponse>(`${RUNS_BASE}/${runId}`)).data,
  })
  const membersQuery = useQuery({
    queryKey: ['cpf-interest-run-members', runId],
    queryFn: async () => (await apiClient.get<CpfInterestMemberBreakdown[]>(`${RUNS_BASE}/${runId}/members`)).data,
  })

  const postMutation = useMutation({
    mutationFn: async () => (await apiClient.post<CpfAnnualInterestRunResponse>(`${RUNS_BASE}/${runId}/post`, { acknowledgeDataReview })).data,
    onSuccess: () => {
      notifyToast({ tone: 'success', title: 'Interest posted', message: `Run #${runId} posted successfully.` })
      onPosted()
      runQuery.refetch()
    },
    onError: (error) => {
      notifyToast({ tone: 'error', title: 'Posting failed', message: describeApiError(error, 'Could not post interest.') })
    },
  })

  const run = runQuery.data

  return (
    <Modal title={`Interest Run #${runId}`} onClose={onClose}>
      {runQuery.isLoading && <LoadingState />}
      {run && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
            <div>
              <div className="text-xs text-slate-500">Financial Year</div>
              <div className="font-medium">{run.finYear}</div>
            </div>
            <div>
              <div className="text-xs text-slate-500">Scope</div>
              <div className="font-medium">{run.scope === 'SELECTED_MEMBER' ? `Member #${run.memberEmployeeId}` : 'All Members'}</div>
            </div>
            <div>
              <div className="text-xs text-slate-500">Status</div>
              <Badge tone={statusTone(run.status)}>{run.status}</Badge>
            </div>
            <div>
              <div className="text-xs text-slate-500">Rate</div>
              <div className="font-medium">{run.declaredInterestRate}%</div>
            </div>
          </div>

          {run.dataReviewRequired && (
            <p className="flex items-center gap-2 rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-700">
              <ShieldAlert size={14} /> One or more members require data review before posting - see the breakdown below.
            </p>
          )}

          <div className="overflow-x-auto">
            <table className="w-full min-w-[700px] text-left text-xs">
              <thead>
                <tr className="border-b border-slate-200 uppercase tracking-wide text-slate-500">
                  <th className="py-2 pr-2">CPF A/C</th>
                  <th className="py-2 pr-2">Employee</th>
                  <th className="py-2 pr-2">Opening</th>
                  <th className="py-2 pr-2">Contrib.</th>
                  <th className="py-2 pr-2">Withdrawals</th>
                  <th className="py-2 pr-2">EE Int.</th>
                  <th className="py-2 pr-2">ER Int.</th>
                  <th className="py-2 pr-2">VPF Int.</th>
                  <th className="py-2 pr-2">Total Int.</th>
                  <th className="py-2 pr-2">Closing (proj.)</th>
                </tr>
              </thead>
              <tbody>
                {(membersQuery.data ?? []).map((m) => (
                  <tr key={m.employeeId} className="border-b border-slate-100 last:border-0">
                    <td className="py-1.5 pr-2">{m.employeeCode}</td>
                    <td className="py-1.5 pr-2">
                      {m.employeeName}
                      {(m.dataReviewRequired || m.legacyAnomalyWarning) && (
                        <div className="mt-0.5 flex items-center gap-1 text-amber-600" title={m.dataReviewReason ?? m.legacyAnomalyMessage ?? ''}>
                          <AlertTriangle size={11} /> <span>{m.dataReviewRequired ? 'Data review' : 'Legacy anomaly'}</span>
                        </div>
                      )}
                    </td>
                    <td className="py-1.5 pr-2">{inr(m.openingBalance)}</td>
                    <td className="py-1.5 pr-2">{inr(m.totalContributions)}</td>
                    <td className="py-1.5 pr-2">{inr(m.totalWithdrawals)}</td>
                    <td className="py-1.5 pr-2">{inr(m.eeInterest)}</td>
                    <td className="py-1.5 pr-2">{inr(m.erInterest)}</td>
                    <td className="py-1.5 pr-2">{inr(m.vpfInterest)}</td>
                    <td className="py-1.5 pr-2 font-medium">{inr(m.totalInterest)}</td>
                    <td className="py-1.5 pr-2">{inr(m.projectedClosingBalance)}</td>
                  </tr>
                ))}
                {membersQuery.data && membersQuery.data.length === 0 && (
                  <tr>
                    <td colSpan={10} className="py-4 text-center text-slate-400">
                      No interest-bearing members in this run.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {canAct && run.status === 'CALCULATED' && (
            <div className="space-y-2 border-t border-slate-200 pt-3">
              {run.dataReviewRequired && (
                <label className="flex items-center gap-2 text-xs text-slate-600">
                  <input type="checkbox" checked={acknowledgeDataReview} onChange={(e) => setAcknowledgeDataReview(e.target.checked)} />
                  I have reviewed the flagged member(s) and confirm this run should be posted.
                </label>
              )}
              <div className="flex justify-end gap-2">
                <PrimaryButton
                  disabled={(run.dataReviewRequired && !acknowledgeDataReview) || postMutation.isPending}
                  onClick={() => postMutation.mutate()}
                >
                  <CheckCircle2 size={14} /> {postMutation.isPending ? 'Posting...' : 'Approve & Post Interest'}
                </PrimaryButton>
              </div>
            </div>
          )}

          {canAct && run.status === 'POSTED' && (
            <div className="flex justify-end border-t border-slate-200 pt-3">
              <SecondaryButton onClick={onReverseRequested}>
                <RotateCcw size={14} /> Reverse Interest
              </SecondaryButton>
            </div>
          )}
        </div>
      )}
    </Modal>
  )
}

function ReverseRunModal({ runId, onClose, onReversed }: { runId: number; onClose: () => void; onReversed: () => void }) {
  const [reason, setReason] = useState('')

  const reverseMutation = useMutation({
    mutationFn: async () => (await apiClient.post<CpfAnnualInterestRunResponse>(`${RUNS_BASE}/${runId}/reverse`, { reason })).data,
    onSuccess: () => {
      notifyToast({ tone: 'success', title: 'Interest reversed', message: `Run #${runId} reversed. Original entries are preserved.` })
      onReversed()
    },
    onError: (error) => {
      notifyToast({ tone: 'error', title: 'Reversal failed', message: describeApiError(error, 'Could not reverse interest.') })
    },
  })

  return (
    <Modal title={`Reverse Interest Run #${runId}`} onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-slate-600">
          This creates an ANNUAL_INTEREST_REVERSAL entry offsetting exactly what this run posted. The original ANNUAL_INTEREST entries are
          never deleted.
        </p>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Reason for reversal</label>
          <textarea className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
        </div>
        <div className="flex justify-end gap-2">
          <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
          <PrimaryButton disabled={!reason.trim() || reverseMutation.isPending} onClick={() => reverseMutation.mutate()} className="bg-rose-600 hover:bg-rose-700">
            {reverseMutation.isPending ? 'Reversing...' : 'Confirm Reversal'}
          </PrimaryButton>
        </div>
      </div>
    </Modal>
  )
}

function MemberInterestHistory({ employeeId }: { employeeId: number }) {
  const historyQuery = useQuery({
    queryKey: ['cpf-interest-member-history', employeeId],
    queryFn: async () => (await apiClient.get<CpfAnnualInterestRunResponse[]>(`${RUNS_BASE}/member/${employeeId}`)).data,
  })

  if (historyQuery.isLoading) return <LoadingState label="Loading member interest history..." />
  if (historyQuery.isError) return <ErrorState message={describeApiError(historyQuery.error, 'Failed to load member interest history.')} />
  if (!historyQuery.data || historyQuery.data.length === 0) {
    return <EmptyState message="No member-scoped interest runs found for this employee (all-members runs aren't listed here individually)." />
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[600px] text-left text-sm">
        <thead>
          <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
            <th className="py-2 pr-3">FY</th>
            <th className="py-2 pr-3">Status</th>
            <th className="py-2 pr-3">Total Interest</th>
            <th className="py-2 pr-3">Posted</th>
          </tr>
        </thead>
        <tbody>
          {historyQuery.data.map((run) => (
            <tr key={run.id} className="border-b border-slate-100 last:border-0">
              <td className="py-2 pr-3">{run.finYear}</td>
              <td className="py-2 pr-3">
                <Badge tone={statusTone(run.status)}>{run.status}</Badge>
              </td>
              <td className="py-2 pr-3">{inr(run.totalInterestCreditedEe + run.totalInterestCreditedEr + run.totalInterestCreditedVpf)}</td>
              <td className="py-2 pr-3">{run.postedAt ? new Date(run.postedAt).toLocaleDateString('en-IN') : '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
