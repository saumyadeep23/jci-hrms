import { AlertTriangle } from 'lucide-react'
import type { CpfTrustMemberSummaryResponse } from '../../../../types/api'

/** Section 4 - the attention strip immediately above the table. Numbers come from the same GET /members/summary as the KPI cards, so they always agree with each other. */
export function CpfMemberExceptionBanner({
  summary,
  onViewExceptions,
}: {
  summary: CpfTrustMemberSummaryResponse | undefined
  onViewExceptions: () => void
}) {
  if (!summary) return null
  const totalAttention = summary.pendingSettlement + summary.overdueSettlement + summary.uanMissing
  if (totalAttention === 0) return null

  return (
    <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3">
      <div className="flex items-center gap-2 text-sm text-amber-900">
        <AlertTriangle size={16} className="shrink-0 text-amber-600" />
        <span className="font-medium">{totalAttention.toLocaleString('en-IN')} member{totalAttention === 1 ? '' : 's'} require attention</span>
        <span className="hidden text-amber-700 sm:inline">
          — {summary.pendingSettlement.toLocaleString('en-IN')} Pending Settlement, {summary.overdueSettlement.toLocaleString('en-IN')} Overdue,{' '}
          {summary.uanMissing.toLocaleString('en-IN')} Missing UAN
        </span>
      </div>
      <button
        type="button"
        onClick={onViewExceptions}
        className="shrink-0 rounded-md border border-amber-300 bg-white px-3 py-1.5 text-xs font-medium text-amber-800 shadow-sm hover:bg-amber-100"
      >
        View Exceptions
      </button>
    </div>
  )
}
