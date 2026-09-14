import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Download, Printer } from 'lucide-react'
import { apiClient } from '../../../../api/client'
import { describeApiError } from '../../../../lib/apiError'
import { Modal } from '../../../../components/common/Modal'
import { Badge, EmptyState, ErrorState, LoadingState, SecondaryButton } from '../../../../components/common/ui'
import type { CpfPassbookResponse, CpfTrustMemberResponse } from '../../../../types/api'
import { formatInr } from './cpfMemberUtils'

function financialYearOptions(): string[] {
  const now = new Date()
  const startYear = now.getMonth() >= 3 ? now.getFullYear() : now.getFullYear() - 1
  return Array.from({ length: 6 }, (_, i) => `${startYear - i}-${startYear - i + 1}`)
}

/** Section 14 - "View Ledger", as a drawer (Modal) rather than a page navigation, reusing the exact same endpoint CpfPassbookView.tsx (the standalone /payroll/trust/passbook page) already reads - GET /cpf/passbook/{employeeId}?finYear=. */
export function CpfLedgerDrawer({ member, onClose }: { member: CpfTrustMemberResponse; onClose: () => void }) {
  const years = useMemo(() => financialYearOptions(), [])
  const [finYear, setFinYear] = useState(years[0])

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['cpf-passbook', member.employeeId, finYear],
    queryFn: async () =>
      (await apiClient.get<CpfPassbookResponse>(`/v1/payroll/trust/cpf/passbook/${member.employeeId}`, { params: { finYear } })).data,
  })

  function exportLedgerCsv() {
    if (!data) return
    const headers = ['Month/Year', 'Type', 'EE', 'VPF', 'ER', 'Interest', 'Debit', 'Credit', 'Running Balance']
    const lines = [headers.join(',')]
    for (const e of data.entries) {
      lines.push([
        e.displayPeriod, e.entryType, e.eeShareCredit, e.vpfCredit, e.erShareCredit, e.interestCredit,
        e.totalDebit, e.totalCredit, e.runningTotalBalance,
      ].join(','))
    }
    const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${member.cpfAcNo}-ledger-${finYear}.csv`
    link.click()
    URL.revokeObjectURL(url)
  }

  return (
    <Modal title={`CPF Ledger - ${member.fullName} (${member.cpfAcNo})`} onClose={onClose} maxWidthClassName="max-w-4xl">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <label className="text-xs font-medium text-slate-600">Financial Year</label>
          <select
            value={finYear}
            onChange={(e) => setFinYear(e.target.value)}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm focus:border-brand-forest focus:outline-none"
          >
            {years.map((y) => (
              <option key={y} value={y}>{y}</option>
            ))}
          </select>
        </div>
        <div className="flex gap-2">
          <SecondaryButton onClick={exportLedgerCsv} disabled={!data || data.entries.length === 0}>
            <Download size={14} /> Export Ledger
          </SecondaryButton>
          <SecondaryButton onClick={() => window.print()}>
            <Printer size={14} /> Print
          </SecondaryButton>
        </div>
      </div>

      {isLoading && <LoadingState label="Loading ledger..." />}
      {isError && <ErrorState message={describeApiError(error, 'Could not load the CPF ledger.')} />}

      {data && (
        <>
          {data.isProvisionalRate && (
            <div className="mb-3 rounded-md border border-amber-200 bg-amber-50 p-2.5 text-xs text-amber-800">
              Notice under Para 60(2), EPF Scheme: FY {data.finYear}'s interest rate isn't notified yet. Figures reflect the provisional
              rate of {data.rateApplied}% p.a. (notified for FY {data.rateSourceFinYear}).
            </div>
          )}

          <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
            <SummaryTile label="Opening / Ledger Balance" value={data.ledgerBalance} />
            <SummaryTile label="Accrued Interest (FYTD)" value={data.accruedInterestFytd} />
            <SummaryTile label="Effective Total Corpus" value={data.effectiveTotalCorpus} emphasize />
          </div>

          {data.entries.length === 0 ? (
            <EmptyState message="No ledger activity for this financial year." />
          ) : (
            <div className="max-h-[50vh] overflow-auto rounded-md border border-slate-200">
              <table className="w-full min-w-[820px] border-collapse text-sm">
                <thead className="sticky top-0 bg-slate-50">
                  <tr className="border-b border-slate-300 text-left text-slate-500">
                    <th className="py-2 px-3">Month/Year</th>
                    <th className="py-2 px-3">Transaction Type</th>
                    <th className="py-2 px-3 text-right">EE</th>
                    <th className="py-2 px-3 text-right">VPF</th>
                    <th className="py-2 px-3 text-right">ER</th>
                    <th className="py-2 px-3 text-right">Interest</th>
                    <th className="py-2 px-3 text-right">Debit</th>
                    <th className="py-2 px-3 text-right">Credit</th>
                    <th className="py-2 px-3 text-right">Running Balance</th>
                  </tr>
                </thead>
                <tbody>
                  {data.entries.map((e) => (
                    <tr key={e.id} className="border-b border-slate-100">
                      <td className="py-1.5 px-3 whitespace-nowrap">{e.displayPeriod}</td>
                      <td className="py-1.5 px-3">
                        {e.entryType.replaceAll('_', ' ')}
                        {e.entryType === 'INTERIM_SETTLEMENT_INTEREST' && (
                          <Badge tone="neutral" className="ml-2 bg-blue-100 text-blue-700">Interim Interest</Badge>
                        )}
                      </td>
                      <td className="py-1.5 px-3 text-right tabular-nums">{e.eeShareCredit.toFixed(2)}</td>
                      <td className="py-1.5 px-3 text-right tabular-nums">{e.vpfCredit.toFixed(2)}</td>
                      <td className="py-1.5 px-3 text-right tabular-nums">{e.erShareCredit.toFixed(2)}</td>
                      <td className="py-1.5 px-3 text-right tabular-nums">{e.interestCredit.toFixed(2)}</td>
                      <td className="py-1.5 px-3 text-right tabular-nums text-red-600">{e.totalDebit > 0 ? e.totalDebit.toFixed(2) : '-'}</td>
                      <td className="py-1.5 px-3 text-right tabular-nums text-emerald-700">{e.totalCredit > 0 ? e.totalCredit.toFixed(2) : '-'}</td>
                      <td className="py-1.5 px-3 text-right font-medium tabular-nums">{e.runningTotalBalance.toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </Modal>
  )
}

function SummaryTile({ label, value, emphasize = false }: { label: string; value: number; emphasize?: boolean }) {
  return (
    <div className={`rounded-md border p-3 ${emphasize ? 'border-brand-forest/40 bg-brand-forest/5' : 'border-slate-200'}`}>
      <p className="text-xs text-slate-500">{label}</p>
      <p className={`mt-1 text-lg font-semibold tabular-nums ${emphasize ? 'text-brand-forest' : 'text-slate-800'}`}>{formatInr(value)}</p>
    </div>
  )
}
