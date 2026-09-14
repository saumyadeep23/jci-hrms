import { AlertTriangle, ArrowDown, ArrowUp, ArrowUpDown, CheckCircle2 } from 'lucide-react'
import { formatDate } from '../../../../lib/date'
import { Badge, EmptyState, ErrorState, LoadingState } from '../../../../components/common/ui'
import type { CpfTrustMemberResponse, Page } from '../../../../types/api'
import { CpfActionsMenu } from './CpfActionsMenu'
import { CpfFinancialCell } from './CpfFinancialCell'
import { CpfPagination } from './CpfPagination'
import { formatInr, SETTLEMENT_STATUS_LABELS, settlementStatusTone } from './cpfMemberUtils'
import type { CpfMemberDrawerTab } from './CpfMemberDrawer'

export type CpfMemberSortColumn = 'cpfAcNo' | 'fullName' | 'status' | 'separationDate' | 'cpfBalance'
export interface CpfMemberSort {
  column: CpfMemberSortColumn
  direction: 'asc' | 'desc'
}

const COLUMNS: { key: CpfMemberSortColumn; label: string; sortable: boolean }[] = [
  { key: 'cpfAcNo', label: 'CPF A/C No.', sortable: true },
  { key: 'fullName', label: 'Member', sortable: true },
  { key: 'status', label: 'UAN', sortable: false },
  { key: 'status', label: 'Status', sortable: true },
  { key: 'cpfBalance', label: 'CPF Balance', sortable: true },
  { key: 'status', label: 'Accrued Interest', sortable: false },
  { key: 'status', label: 'Total Payable', sortable: false },
  { key: 'separationDate', label: 'Separation', sortable: true },
  { key: 'status', label: 'Settlement', sortable: false },
  { key: 'status', label: 'Due / Lag', sortable: false },
]

/** Section 5/18/19 - the redesigned CPF-centric table: desktop full table (sticky header, sortable columns), a card list for mobile (never a squeezed table). Row click opens the Member 360 drawer; interactive controls inside a row (financial-cell expand toggle, actions menu) stop propagation so they don't also trigger it. */
export function CpfMembersTable({
  page,
  isLoading,
  isError,
  sort,
  onSortChange,
  onRowClick,
  onViewLedger,
  onUpdateUan,
  onCheckEpsEligibility,
  onPageChange,
  onPageSizeChange,
}: {
  page: Page<CpfTrustMemberResponse> | undefined
  isLoading: boolean
  isError: boolean
  sort: CpfMemberSort
  onSortChange: (sort: CpfMemberSort) => void
  onRowClick: (member: CpfTrustMemberResponse, tab?: CpfMemberDrawerTab) => void
  onViewLedger: (member: CpfTrustMemberResponse) => void
  onUpdateUan: (member: CpfTrustMemberResponse) => void
  onCheckEpsEligibility: (member: CpfTrustMemberResponse) => void
  onPageChange: (page: number) => void
  onPageSizeChange: (size: number) => void
}) {
  function toggleSort(column: CpfMemberSortColumn) {
    if (sort.column === column) {
      onSortChange({ column, direction: sort.direction === 'asc' ? 'desc' : 'asc' })
    } else {
      onSortChange({ column, direction: 'asc' })
    }
  }

  if (isLoading) return <LoadingState label="Loading CPF Trust members..." />
  if (isError) return <ErrorState message="Could not load the CPF Trust members' list." />
  if (!page || page.content.length === 0) return <EmptyState message="No members match these filters." />

  return (
    <div>
      {/* Desktop / tablet table */}
      <div className="hidden overflow-x-auto sm:block">
        <table className="w-full min-w-[1100px] border-collapse text-sm">
          <thead className="sticky top-0 z-[1] bg-white">
            <tr className="border-b border-slate-300 text-left text-slate-500">
              {COLUMNS.map((col, i) => (
                <SortableHeader key={col.label + i} label={col.label} column={col.sortable ? col.key : undefined} sort={sort} onToggle={toggleSort} />
              ))}
              <th className="py-2 pl-3 text-right">Action</th>
            </tr>
          </thead>
          <tbody>
            {page.content.map((member) => (
              <tr
                key={member.employeeId}
                tabIndex={0}
                role="button"
                aria-label={`View ${member.fullName}'s CPF member details`}
                onClick={() => onRowClick(member)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') onRowClick(member)
                }}
                className="cursor-pointer border-b border-slate-100 hover:bg-slate-50 focus:bg-slate-50 focus:outline-none"
              >
                <td className="py-2.5 pr-3 font-medium text-slate-800">{member.cpfAcNo}</td>
                <td className="py-2.5 pr-3">
                  <p className="text-slate-800">{member.fullName}</p>
                  <p className="text-xs text-slate-400">{member.employeeCode}</p>
                </td>
                <td className="py-2.5 pr-3">
                  {member.uanNo ? (
                    <span className="inline-flex items-center gap-1 text-xs text-slate-600">
                      <CheckCircle2 size={12} className="text-emerald-500" /> {member.uanNo}
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1 text-xs font-medium text-amber-700">
                      <AlertTriangle size={12} /> UAN Missing
                    </span>
                  )}
                </td>
                <td className="py-2.5 pr-3">
                  <Badge tone={member.isSeparated ? 'neutral' : 'success'}>{member.status.replaceAll('_', ' ')}</Badge>
                </td>
                <td className="py-2.5 pr-3">
                  <CpfFinancialCell ee={member.eeBalance} vpf={member.vpfBalance} er={member.erBalance} total={member.cpfBalance} />
                </td>
                <td className="py-2.5 pr-3 tabular-nums text-slate-600">{formatInr(member.accruedInterest)}</td>
                <td className="py-2.5 pr-3 font-semibold tabular-nums text-brand-forest">{formatInr(member.totalPayable)}</td>
                <td className="py-2.5 pr-3 text-xs text-slate-500">
                  {member.separationDate ? formatDate(member.separationDate) : '—'}
                </td>
                <td className="py-2.5 pr-3">
                  {member.isSeparated ? (
                    <Badge tone={settlementStatusTone(member.settlementStatus)}>{SETTLEMENT_STATUS_LABELS[member.settlementStatus]}</Badge>
                  ) : (
                    <span className="text-xs text-slate-400">Not Applicable</span>
                  )}
                </td>
                <td className="py-2.5 pr-3 text-xs">
                  {!member.isSeparated ? (
                    <span className="text-slate-400">—</span>
                  ) : member.settlementStatus === 'OVERDUE' ? (
                    <span className="font-medium text-red-600">🔴 {member.settlementLagLabel}</span>
                  ) : member.settlementStatus === 'SETTLED' ? (
                    <span className="text-emerald-700">{member.settlementLagLabel}</span>
                  ) : (
                    <span className="text-slate-600">{member.settlementLagLabel}</span>
                  )}
                </td>
                <td className="py-2.5 pl-3 text-right">
                  <CpfActionsMenu
                    member={member}
                    onViewLedger={() => onViewLedger(member)}
                    onUpdateUan={() => onUpdateUan(member)}
                    onCheckEpsEligibility={() => onCheckEpsEligibility(member)}
                    onOpenDrawer={(tab) => onRowClick(member, tab)}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Mobile card list */}
      <div className="space-y-2 sm:hidden">
        {page.content.map((member) => (
          <div
            key={member.employeeId}
            role="button"
            tabIndex={0}
            onClick={() => onRowClick(member)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') onRowClick(member)
            }}
            className="rounded-lg border border-slate-200 bg-white p-3"
          >
            <div className="flex items-start justify-between gap-2">
              <div>
                <p className="font-medium text-slate-800">{member.cpfAcNo}</p>
                <p className="text-sm text-slate-600">{member.fullName}</p>
              </div>
              <CpfActionsMenu
                member={member}
                onViewLedger={() => onViewLedger(member)}
                onUpdateUan={() => onUpdateUan(member)}
                onCheckEpsEligibility={() => onCheckEpsEligibility(member)}
                onOpenDrawer={(tab) => onRowClick(member, tab)}
              />
            </div>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <Badge tone={member.isSeparated ? 'neutral' : 'success'}>{member.status.replaceAll('_', ' ')}</Badge>
              {member.isSeparated && (
                <Badge tone={settlementStatusTone(member.settlementStatus)}>{SETTLEMENT_STATUS_LABELS[member.settlementStatus]}</Badge>
              )}
              {!member.uanNo && <Badge tone="warning">UAN Missing</Badge>}
            </div>
            <div className="mt-2 flex items-center justify-between text-sm">
              <span className="text-slate-500">Total Payable</span>
              <span className="font-semibold tabular-nums text-brand-forest">{formatInr(member.totalPayable)}</span>
            </div>
            {member.isSeparated && (
              <div className="mt-1 flex items-center justify-between text-xs">
                <span className="text-slate-400">Due / Lag</span>
                <span className={member.settlementStatus === 'OVERDUE' ? 'font-medium text-red-600' : 'text-slate-600'}>
                  {member.settlementStatus === 'OVERDUE' ? `🔴 ${member.settlementLagLabel}` : member.settlementLagLabel}
                </span>
              </div>
            )}
            <details className="mt-2 text-xs text-slate-500" onClick={(e) => e.stopPropagation()}>
              <summary className="cursor-pointer text-slate-400">More details</summary>
              <div className="mt-1.5 space-y-1">
                <p>EE {formatInr(member.eeBalance)} · VPF {formatInr(member.vpfBalance)} · ER {formatInr(member.erBalance)}</p>
                <p>Accrued Interest {formatInr(member.accruedInterest)}</p>
                <p>UAN: {member.uanNo ?? 'Missing'}</p>
                {member.separationDate && <p>Separation: {formatDate(member.separationDate)}</p>}
              </div>
            </details>
          </div>
        ))}
      </div>

      <CpfPagination
        page={page.number}
        pageSize={page.size}
        totalElements={page.totalElements}
        totalPages={page.totalPages}
        onPageChange={onPageChange}
        onPageSizeChange={onPageSizeChange}
      />
    </div>
  )
}

function SortableHeader({
  label,
  column,
  sort,
  onToggle,
}: {
  label: string
  column?: CpfMemberSortColumn
  sort: CpfMemberSort
  onToggle: (column: CpfMemberSortColumn) => void
}) {
  if (!column) {
    return <th className="py-2 pr-3">{label}</th>
  }
  const isActive = sort.column === column
  const Icon = isActive ? (sort.direction === 'asc' ? ArrowUp : ArrowDown) : ArrowUpDown
  return (
    <th className="py-2 pr-3">
      <button
        type="button"
        onClick={() => onToggle(column)}
        className={`inline-flex items-center gap-1 hover:text-slate-700 ${isActive ? 'font-semibold text-slate-700' : ''}`}
      >
        {label}
        <Icon size={12} className={isActive ? 'text-brand-forest' : 'text-slate-300'} />
      </button>
    </th>
  )
}
