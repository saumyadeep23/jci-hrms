import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { Badge, EmptyState, ErrorState, LoadingState } from '../common/ui'
import { Modal } from '../common/Modal'
import type { LeaveEncashmentResponse, LeaveEntitlementBalanceResponse, LeaveLedgerEntryResponse, LeaveLedgerSource } from '../../types/api'

const SOURCE_LABELS: Record<LeaveLedgerSource, string> = {
  AUTO_LATE_DEDUCTION: 'Late Attendance Deduction',
  COMMUTED_LEAVE_HPL_DEBIT: 'Commuted Leave (HPL Debit)',
  BASELINE_TAKEON: 'Baseline Take-On',
  EL_SEMI_ANNUAL_ACCRUAL: 'Semi-Annual Accrual',
  EL_EOL_LAPSE_DEDUCTION: 'EOL Lapse Deduction',
  EL_ENCASHMENT_DEBIT: 'Encashment Debit',
  ATTENDANCE_PENALTY_REFUND: 'Attendance Penalty Refund',
  TRANSFER_JT_CONVERSION: 'Unavailed Joining Time (Transfer)',
  TERMINAL_ENCASHMENT: 'Terminal Settlement Encashment',
}

function SummaryStat({ label, value, tone }: { label: string; value: string; tone?: 'amber' }) {
  return (
    <div className={`rounded-md border px-3 py-2 ${tone === 'amber' ? 'border-amber-200 bg-amber-50' : 'border-slate-200 bg-slate-50'}`}>
      <p className={`text-[11px] uppercase tracking-wide ${tone === 'amber' ? 'text-amber-600' : 'text-slate-400'}`}>{label}</p>
      <p className={`mt-0.5 text-sm font-semibold tabular-nums ${tone === 'amber' ? 'text-amber-700' : 'text-slate-800'}`}>{value}</p>
    </div>
  )
}

/** An application still holding an encashable-EL reservation - HR gate pending, or HR approved but Finance gate still pending. */
function isUnderProcess(app: LeaveEncashmentResponse): boolean {
  return app.hrApprovalStatus === 'PENDING' || (app.hrApprovalStatus === 'APPROVED' && app.financeApprovalStatus === 'PENDING')
}

type Row =
  | { kind: 'ledger'; date: string; entry: LeaveLedgerEntryResponse }
  | { kind: 'hold'; date: string; application: LeaveEncashmentResponse }

/**
 * Read-only EL sub-ledger audit trail for one employee - opened from the
 * In-Service EL Encashment admin review queue's "View Ledger" action so a
 * Gate 1 (HR) or Gate 2 (Finance) reviewer can see the transactions behind a
 * pending claim before deciding it. "Balance After" is derived client-side
 * (running total seeded from the sub-ledger's own openingBalance, entries
 * applied oldest-first) since leave_ledger_entries only stores each delta,
 * not a running balance column. Still-pending encashment applications are
 * merged into the same chronological run as amber "hold" rows - they haven't
 * hit leave_ledger_entries yet (that only happens on Finance's final
 * sanction), but they've already reserved days out of encashableAvailable,
 * so the ledger's own ending balance would otherwise look 20 days too high
 * for as long as the claim stays undecided.
 */
export function ElLedgerModal({ employeeId, employeeLabel, onClose }: { employeeId: number; employeeLabel: string; onClose: () => void }) {
  const year = new Date().getFullYear()

  const balanceQuery = useQuery({
    queryKey: ['leave-entitlement-balance-by-employee', employeeId, year],
    queryFn: async () =>
      (await apiClient.get<LeaveEntitlementBalanceResponse[]>(`/v1/leave-entitlement-balance/by-employee/${employeeId}`, {
        params: { year },
      })).data,
  })
  const el = balanceQuery.data?.find((b) => b.leaveTypeCode === 'EL')

  const ledgerQuery = useQuery({
    queryKey: ['leave-ledger-entries-el', employeeId],
    queryFn: async () =>
      (await apiClient.get<LeaveLedgerEntryResponse[]>('/leave-ledger-entries', {
        params: { employeeId, leaveTypeCode: 'EL' },
      })).data,
  })

  const applicationsQuery = useQuery({
    queryKey: ['leave-encashment-by-employee', employeeId],
    queryFn: async () => (await apiClient.get<LeaveEncashmentResponse[]>(`/v1/admin/leave/encashment/by-employee/${employeeId}`)).data,
  })
  const underProcess = (applicationsQuery.data ?? []).filter(isUnderProcess)
  const reservedDays = underProcess.reduce((sum, app) => sum + app.elDaysClaimed, 0)

  const rows: Row[] = [
    ...(ledgerQuery.data ?? []).map((entry): Row => ({ kind: 'ledger', date: entry.entryDate, entry })),
    ...underProcess.map((application): Row => ({ kind: 'hold', date: application.applicationDate, application })),
  ].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())

  // Starts at 0, not el.openingBalance: the ledger's own chronologically-first entry for an
  // employee's EL is always the BASELINE_TAKEON row itself (LeaveBaselineTakeOnService writes
  // opening_balance onto the entitlement AND a matching ledger entry with the same delta at the
  // same time) - seeding at openingBalance would double-count that entry's own effect on top of
  // the balance it establishes. A hold row subtracts like a debit, since that's exactly what a
  // reservation does to available balance even before it becomes a real ledger entry.
  let running = 0
  const withRunningBalance = rows.map((row) => {
    running += row.kind === 'ledger' ? row.entry.deltaDays : -row.application.elDaysClaimed
    return { row, balanceAfter: running }
  })
  const mostRecentFirst = [...withRunningBalance].reverse()

  const isLoading = balanceQuery.isLoading || ledgerQuery.isLoading || applicationsQuery.isLoading
  const isError = balanceQuery.isError || ledgerQuery.isError || applicationsQuery.isError

  return (
    <Modal title={`EL Ledger - ${employeeLabel}`} onClose={onClose} maxWidthClassName="max-w-3xl">
      {isLoading && <LoadingState label="Loading EL ledger..." />}
      {isError && <ErrorState message="Could not load the EL ledger for this employee." />}

      {!isLoading && !isError && (
        <>
          <div className="mb-2 grid grid-cols-2 gap-2 sm:grid-cols-4">
            <SummaryStat label="Opening" value={el ? el.openingBalance.toFixed(2) : '—'} />
            <SummaryStat label="Accrued" value={el ? el.creditedDays.toFixed(2) : '—'} />
            <SummaryStat label="Availed (Physical)" value={el ? el.availedDays.toFixed(2) : '—'} />
            <SummaryStat label="Encashed" value={el ? el.encashedDays.toFixed(2) : '—'} />
            <SummaryStat label="Available (Enjoyable)" value={el ? el.enjoyableAvailable.toFixed(2) : '—'} />
            <SummaryStat label="Available (Encashable)" value={el ? el.encashableAvailable.toFixed(2) : '—'} />
            <SummaryStat label="Under Process (Hold)" value={reservedDays.toFixed(2)} tone="amber" />
            {/* Computed from the split, not trusted from el.availableBalance directly - same reasoning as LeaveBalanceSplitCard/LeaveApplicationForm's own totalEl derivation. */}
            <SummaryStat
              label="Total Available"
              value={el ? (el.encashableAvailable + el.enjoyableAvailable).toFixed(2) : '—'}
            />
          </div>
          {el && (
            <p className="mb-4 text-[11px] text-slate-400">
              Opening {el.openingBalance.toFixed(2)} - Availed {el.availedDays.toFixed(2)} - Encashed {el.encashedDays.toFixed(2)} - Under
              Process {reservedDays.toFixed(2)} = Total Available {(el.encashableAvailable + el.enjoyableAvailable).toFixed(2)}
            </p>
          )}

          {mostRecentFirst.length === 0 && <EmptyState message="No EL ledger entries recorded yet." />}
          {mostRecentFirst.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px] border-collapse text-xs">
                <thead>
                  <tr className="border-b border-slate-300 text-left text-slate-500">
                    <th className="py-2 pr-3">Entry Date</th>
                    <th className="py-2 pr-3">Source</th>
                    <th className="py-2 pr-3 text-right">Credit/Debit</th>
                    <th className="py-2 pr-3 text-right">Balance After</th>
                    <th className="py-2 pl-3">Remarks / Ref</th>
                  </tr>
                </thead>
                <tbody>
                  {mostRecentFirst.map(({ row, balanceAfter }) =>
                    row.kind === 'hold' ? (
                      <tr key={`hold-${row.application.id}`} className="border-b border-slate-100 bg-amber-50/50">
                        <td className="py-1.5 pr-3 tabular-nums">{formatDate(row.application.applicationDate)}</td>
                        <td className="py-1.5 pr-3">
                          In-Service Encashment (Claim #{row.application.id}){' '}
                          <Badge tone="warning">UNDER REVIEW</Badge>
                        </td>
                        <td className="py-1.5 pr-3 text-right tabular-nums font-medium text-amber-700">
                          -{row.application.elDaysClaimed.toFixed(2)} (Hold)
                        </td>
                        <td className="py-1.5 pr-3 text-right tabular-nums">{balanceAfter.toFixed(2)}</td>
                        <td className="py-1.5 pl-3 text-slate-500">Application pending sanction; days held from available balance</td>
                      </tr>
                    ) : (
                      <tr key={`entry-${row.entry.id}`} className="border-b border-slate-100">
                        <td className="py-1.5 pr-3 tabular-nums">{formatDate(row.entry.entryDate)}</td>
                        <td className="py-1.5 pr-3">{SOURCE_LABELS[row.entry.source] ?? row.entry.source}</td>
                        <td
                          className={`py-1.5 pr-3 text-right tabular-nums font-medium ${row.entry.deltaDays >= 0 ? 'text-emerald-600' : 'text-red-600'}`}
                        >
                          {row.entry.deltaDays >= 0 ? '+' : ''}
                          {row.entry.deltaDays.toFixed(2)}
                        </td>
                        <td className="py-1.5 pr-3 text-right tabular-nums">{balanceAfter.toFixed(2)}</td>
                        <td className="py-1.5 pl-3 text-slate-500">
                          {row.entry.description}
                          {row.entry.relatedLeaveApplicationId && ` (App #${row.entry.relatedLeaveApplicationId})`}
                          {row.entry.relatedDailyAttendanceId && ` (Attendance #${row.entry.relatedDailyAttendanceId})`}
                        </td>
                      </tr>
                    ),
                  )}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </Modal>
  )
}
