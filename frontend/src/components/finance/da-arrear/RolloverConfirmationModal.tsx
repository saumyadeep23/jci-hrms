import { AlertTriangle } from 'lucide-react'
import { Modal } from '../../common/Modal'
import { PrimaryButton, SecondaryButton } from '../../common/ui'
import type { IdaProjectionSummaryResponse } from '../../../types/api'

const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
] as const

function monthYear(month: number, year: number): string {
  return `${MONTHS[month - 1]} ${year}`
}

function formatInr(value: number): string {
  return `₹${value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

/**
 * Scenario A Cut-Off Interceptor. IdaProjectionController's POST .../check-rollover has already applied
 * the rollover (new expectedDrawalMonth/Year, expanded retroMonthsCount, re-simulated breakups) by the
 * time this modal is shown - "Cancel" here can't undo that, only decline to also commit the order. That's
 * intentional, not a shortcut: the underlying fact this interceptor reports (the original target month's
 * payroll is genuinely locked or the 25th cutoff has passed) is real and isn't something a rollback would
 * change - the batch would just be usable again next time with the same month re-detected as locked.
 */
export function RolloverConfirmationModal({
  before,
  after,
  onConfirm,
  onCancel,
  isPending,
}: {
  before: { expectedDrawalMonth: number; expectedDrawalYear: number; retroMonthsCount: number }
  after: IdaProjectionSummaryResponse
  onConfirm: () => void
  onCancel: () => void
  isPending: boolean
}) {
  return (
    <Modal title="Target Drawal Month Is Locked" onClose={onCancel} maxWidthClassName="max-w-lg">
      <div className="mb-4 flex items-start gap-2 rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800">
        <AlertTriangle size={16} className="mt-0.5 shrink-0" />
        <span>
          {monthYear(before.expectedDrawalMonth, before.expectedDrawalYear)}'s payroll is already locked (finalized/disbursed) or its
          25th-of-month cutoff has passed. The drawal has been rolled forward and the retro window expanded to cover the skipped month.
        </span>
      </div>

      <div className="grid grid-cols-2 gap-3 rounded-md bg-slate-50 p-3 text-sm">
        <div>
          <p className="text-xs text-slate-400">Drawal Month</p>
          <p className="font-medium text-slate-700 line-through">{monthYear(before.expectedDrawalMonth, before.expectedDrawalYear)}</p>
          <p className="font-semibold text-emerald-700">{monthYear(after.expectedDrawalMonth, after.expectedDrawalYear)}</p>
        </div>
        <div>
          <p className="text-xs text-slate-400">Retro Months Count</p>
          <p className="font-medium text-slate-700 line-through">{before.retroMonthsCount}</p>
          <p className="font-semibold text-emerald-700">{after.retroMonthsCount}</p>
        </div>
      </div>

      <div className="mt-3 grid grid-cols-1 gap-2 rounded-md bg-slate-50 p-3 text-sm sm:grid-cols-3">
        <div>
          <p className="text-xs text-slate-400">Updated Gross Arrear Outgo</p>
          <p className="font-semibold tabular-nums text-slate-800">{formatInr(after.totalArrearGrossOutgo)}</p>
        </div>
        <div>
          <p className="text-xs text-slate-400">Updated Net Arrear Outgo</p>
          <p className="font-semibold tabular-nums text-slate-800">{formatInr(after.totalArrearNetOutgo)}</p>
        </div>
        <div>
          <p className="text-xs text-slate-400">Updated Employer Cost Outgo</p>
          <p className="font-semibold tabular-nums text-slate-800">{formatInr(after.totalEmployerCostOutgo)}</p>
        </div>
      </div>

      <div className="mt-5 flex justify-end gap-2">
        <SecondaryButton type="button" onClick={onCancel} disabled={isPending}>
          Cancel
        </SecondaryButton>
        <PrimaryButton type="button" onClick={onConfirm} disabled={isPending}>
          Confirm Rollover &amp; Recompute
        </PrimaryButton>
      </div>
    </Modal>
  )
}
