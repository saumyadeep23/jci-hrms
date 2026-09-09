import { formatDate } from '../../lib/date'
import { Modal } from '../common/Modal'
import { PrimaryButton } from '../common/ui'

/** Shared shape the memo needs - both LeaveEncashmentResponse (claims page) and LeaveEncashmentHistoryResponse (admin history tab) map into this. */
export interface SanctionMemoData {
  id: number
  employeeCode: string
  fullName: string
  designation: string | null
  elDaysClaimed: number
  basicPay: number | null
  daRateApplied: number | null
  grossAmount: number | null
  arrearAmount: number
  arrearSettled: boolean
  sanctionDate: string
}

function formatInr(value: number | null | undefined): string {
  return value != null ? `Rs. ${value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '—'
}

/**
 * Printable Office Memorandum for a finance-approved EL encashment claim. Only ever rendered for a
 * claim that already has an emoluments snapshot (basicPay + daRateApplied + grossAmount) - the
 * caller is expected to gate the "Sanction Memo" button on that, same as FinanceReviewRow does.
 */
export function EncashmentSanctionMemoModal({ app, onClose }: { app: SanctionMemoData; onClose: () => void }) {
  const sanctionRef = `ELE-${app.id}`
  const sanctionDate = app.sanctionDate
  const year = new Date(sanctionDate).getFullYear()
  const basicPay = app.basicPay
  const daRate = app.daRateApplied
  const daAmount = basicPay != null && daRate != null ? (basicPay * daRate) / 100 : null
  const monthlyEmoluments = basicPay != null && daAmount != null ? basicPay + daAmount : null
  const dailyRate = monthlyEmoluments != null ? monthlyEmoluments / 30 : null

  return (
    <Modal title="Sanction Memo" onClose={onClose} maxWidthClassName="max-w-2xl">
      <div id="sanction-memo-print-area" className="space-y-4 text-sm text-slate-800">
        <div className="border-b border-slate-300 pb-3 text-center">
          <p className="text-base font-bold uppercase">The Jute Corporation of India Limited</p>
          <p className="text-xs text-slate-500">(A Government of India Enterprise)</p>
        </div>

        <div className="flex justify-between text-xs">
          <span>
            Memo No: JCI/HR/EL-ENCASH/{year}/{app.id}
          </span>
          <span>Date: {formatDate(sanctionDate)}</span>
        </div>

        <p>
          <span className="font-semibold">Subject:</span> Sanction of encashment of Earned Leave under CPSE Leave Rules.
        </p>

        <p>
          Sanction of the Competent Authority is hereby accorded for encashment of {app.elDaysClaimed.toFixed(2)} days Earned Leave in
          respect of Shri/Smt <span className="font-semibold">{app.fullName}</span>, {app.designation ?? '—'} (Emp ID:{' '}
          {app.employeeCode}).
        </p>

        <table className="w-full border-collapse border border-slate-300 text-xs">
          <tbody>
            <tr className="border border-slate-300">
              <td className="border border-slate-300 px-2 py-1.5">1. Basic Pay</td>
              <td className="border border-slate-300 px-2 py-1.5 text-right tabular-nums">{formatInr(basicPay)}</td>
            </tr>
            <tr>
              <td className="border border-slate-300 px-2 py-1.5">2. DA ({daRate != null ? daRate.toFixed(1) : '—'}%)</td>
              <td className="border border-slate-300 px-2 py-1.5 text-right tabular-nums">{formatInr(daAmount)}</td>
            </tr>
            <tr>
              <td className="border border-slate-300 px-2 py-1.5">3. Monthly Emoluments (1+2)</td>
              <td className="border border-slate-300 px-2 py-1.5 text-right tabular-nums">{formatInr(monthlyEmoluments)}</td>
            </tr>
            <tr>
              <td className="border border-slate-300 px-2 py-1.5">4. Daily Emoluments (Emoluments / 30)</td>
              <td className="border border-slate-300 px-2 py-1.5 text-right tabular-nums">{formatInr(dailyRate)}</td>
            </tr>
            <tr className="font-semibold">
              <td className="border border-slate-300 px-2 py-1.5">
                5. Total Encashment Sanctioned ({app.elDaysClaimed.toFixed(2)} x Daily Rate)
              </td>
              <td className="border border-slate-300 px-2 py-1.5 text-right tabular-nums">{formatInr(app.grossAmount)}</td>
            </tr>
            {!app.arrearSettled && app.arrearAmount > 0 && (
              <tr className="text-amber-700">
                <td className="border border-slate-300 px-2 py-1.5">Retroactive DA Arrear (pending payroll credit)</td>
                <td className="border border-slate-300 px-2 py-1.5 text-right tabular-nums">{formatInr(app.arrearAmount)}</td>
              </tr>
            )}
          </tbody>
        </table>

        <div className="grid grid-cols-2 gap-4 pt-8 text-xs">
          <div className="border-t border-slate-400 pt-1 text-center">HR Sanctioning Authority</div>
          <div className="border-t border-slate-400 pt-1 text-center">Accounts Officer</div>
        </div>

        <div className="border-t border-slate-300 pt-3 text-xs text-slate-600">
          <p className="font-semibold">Copy to:</p>
          <ol className="list-decimal pl-4">
            <li>Incumbent Officer</li>
            <li>Finance/Payroll Section for disbursement</li>
            <li>e-Service Book dossier</li>
            <li>Office Copy</li>
          </ol>
        </div>
      </div>

      <div className="mt-6 flex justify-end gap-2 print:hidden">
        <PrimaryButton
          type="button"
          onClick={() => {
            const printContents = document.getElementById('sanction-memo-print-area')?.outerHTML ?? ''
            const printWindow = window.open('', '_blank', 'width=800,height=900')
            if (!printWindow) return
            printWindow.document.write(
              `<html><head><title>Sanction Memo ${sanctionRef}</title><style>
                body { font-family: sans-serif; padding: 24px; color: #1e293b; }
                table { width: 100%; border-collapse: collapse; }
                td { border: 1px solid #cbd5e1; padding: 6px 8px; }
                .text-right { text-align: right; }
                .font-semibold { font-weight: 600; }
              </style></head><body>${printContents}</body></html>`,
            )
            printWindow.document.close()
            printWindow.focus()
            printWindow.print()
          }}
        >
          Print / Download Sanction Memo
        </PrimaryButton>
      </div>
    </Modal>
  )
}
