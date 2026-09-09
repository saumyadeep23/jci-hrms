import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Download, Printer } from 'lucide-react'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { Badge, Card, EmptyState, ErrorState, LoadingState, SecondaryButton } from '../common/ui'
import { ElLedgerModal } from './ElLedgerModal'
import { EncashmentSanctionMemoModal, type SanctionMemoData } from './EncashmentSanctionMemoModal'
import type { LeaveEncashmentHistoryResponse } from '../../types/api'

function toMemoData(r: LeaveEncashmentHistoryResponse): SanctionMemoData {
  return {
    id: r.id,
    employeeCode: r.employeeCode,
    fullName: r.fullName,
    designation: r.designation,
    elDaysClaimed: r.elDaysClaimed,
    basicPay: r.basicPay,
    daRateApplied: r.daRateApplied,
    grossAmount: r.grossAmount,
    arrearAmount: r.arrearAmount,
    arrearSettled: r.arrearSettled,
    sanctionDate: r.financeApprovedAt ?? new Date().toISOString(),
  }
}

const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
] as const

function formatInr(value: number | null): string {
  return value != null ? `₹${value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '—'
}

function csvCell(value: string | number | null): string {
  const text = value == null ? '' : String(value)
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

function exportToCsv(rows: LeaveEncashmentHistoryResponse[], year: number, month: number) {
  const header = [
    'Sanction Ref', 'Employee Code', 'Employee Name', 'Designation', 'Days Encashed', 'Basic Pay', 'DA %',
    'Total Disbursal Amount', 'HR Clearance Date', 'Finance Sanction Date', 'Status',
  ]
  const lines = rows.map((r) =>
    [
      r.voucherRefNo, r.employeeCode, r.fullName, r.designation, r.elDaysClaimed, r.basicPay, r.daRateApplied,
      (r.grossAmount ?? 0) + r.arrearAmount, r.hrApprovedAt ? formatDate(r.hrApprovedAt) : '',
      r.financeApprovedAt ? formatDate(r.financeApprovedAt) : '', r.status,
    ]
      .map(csvCell)
      .join(','),
  )
  const csv = [header.join(','), ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `el-encashment-sanctions-${year}-${month === 0 ? 'all' : String(month).padStart(2, '0')}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

/** ALMS EL Encashment - HR/Finance audit view of past (finalized) sanctions, filterable by month/year. */
export function EncashmentApprovalHistoryTab() {
  const currentYear = new Date().getFullYear()
  const [year, setYear] = useState(currentYear)
  const [month, setMonth] = useState(0) // 0 = All Months
  const [ledgerTarget, setLedgerTarget] = useState<{ employeeId: number; label: string } | null>(null)
  const [memoTarget, setMemoTarget] = useState<SanctionMemoData | null>(null)

  const historyQuery = useQuery({
    queryKey: ['leave-encashment-history', year, month],
    queryFn: async () =>
      (await apiClient.get<LeaveEncashmentHistoryResponse[]>('/v1/admin/leave/encashment/history', {
        params: { year, month: month === 0 ? undefined : month },
      })).data,
  })

  const rows = historyQuery.data ?? []
  const yearOptions = Array.from({ length: 5 }, (_, i) => currentYear - i)

  return (
    <Card>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-slate-700">Past Sanctions / History</h2>
        <div className="flex flex-wrap items-center gap-2">
          <select
            value={year}
            onChange={(e) => setYear(Number(e.target.value))}
            className="rounded-md border border-slate-300 px-2 py-1.5 text-xs"
          >
            {yearOptions.map((y) => (
              <option key={y} value={y}>
                {y}
              </option>
            ))}
          </select>
          <select
            value={month}
            onChange={(e) => setMonth(Number(e.target.value))}
            className="rounded-md border border-slate-300 px-2 py-1.5 text-xs"
          >
            <option value={0}>All Months</option>
            {MONTHS.map((label, i) => (
              <option key={label} value={i + 1}>
                {label}
              </option>
            ))}
          </select>
          <SecondaryButton onClick={() => exportToCsv(rows, year, month)} disabled={rows.length === 0}>
            <Download size={14} /> Export to Excel
          </SecondaryButton>
          <SecondaryButton onClick={() => window.print()} disabled={rows.length === 0}>
            <Printer size={14} /> Print
          </SecondaryButton>
        </div>
      </div>

      {historyQuery.isLoading && <LoadingState label="Loading sanction history..." />}
      {historyQuery.isError && <ErrorState message="Could not load the sanction history." />}
      {historyQuery.isSuccess && rows.length === 0 && <EmptyState message="No finalized sanctions for this period." />}

      {rows.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[1100px] border-collapse text-xs">
            <thead>
              <tr className="border-b border-slate-300 text-left text-slate-500">
                <th className="py-2 pr-3">Sanction Ref / App #</th>
                <th className="py-2 pr-3">Employee</th>
                <th className="py-2 pr-3">Designation</th>
                <th className="py-2 pr-3 text-right">Days Encashed</th>
                <th className="py-2 pr-3 text-right">Basic Pay</th>
                <th className="py-2 pr-3 text-right">DA %</th>
                <th className="py-2 pr-3 text-right">Total Disbursal</th>
                <th className="py-2 pr-3">HR Clearance</th>
                <th className="py-2 pr-3">Finance Sanction</th>
                <th className="py-2 pr-3">Status</th>
                <th className="py-2 pl-3">Action</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id} className="border-b border-slate-100">
                  <td className="py-1.5 pr-3 font-medium tabular-nums">
                    {r.voucherRefNo}
                    {!r.arrearSettled && r.arrearAmount > 0 && (
                      <Badge tone="warning" className="ml-1">
                        Arrear Pending
                      </Badge>
                    )}
                  </td>
                  <td className="py-1.5 pr-3">
                    {r.employeeCode} · {r.fullName}
                  </td>
                  <td className="py-1.5 pr-3">{r.designation ?? '—'}</td>
                  <td className="py-1.5 pr-3 text-right tabular-nums">{r.elDaysClaimed.toFixed(2)}</td>
                  <td className="py-1.5 pr-3 text-right tabular-nums">{formatInr(r.basicPay)}</td>
                  <td className="py-1.5 pr-3 text-right tabular-nums">{r.daRateApplied != null ? `${r.daRateApplied.toFixed(1)}%` : '—'}</td>
                  <td className="py-1.5 pr-3 text-right tabular-nums font-medium">
                    {formatInr(r.grossAmount != null ? r.grossAmount + r.arrearAmount : null)}
                  </td>
                  <td className="py-1.5 pr-3">{r.hrApprovedAt ? formatDate(r.hrApprovedAt) : '—'}</td>
                  <td className="py-1.5 pr-3">
                    {r.financeApprovedAt ? formatDate(r.financeApprovedAt) : '—'}
                    {r.financeApprovedByName && <span className="block text-slate-400">{r.financeApprovedByName}</span>}
                  </td>
                  <td className="py-1.5 pr-3">
                    <Badge tone={r.status === 'SANCTIONED' ? 'success' : 'danger'}>{r.status}</Badge>
                  </td>
                  <td className="py-1.5 pl-3">
                    <div className="flex flex-wrap gap-1.5">
                      <SecondaryButton onClick={() => setLedgerTarget({ employeeId: r.employeeId, label: `${r.employeeCode} · ${r.fullName}` })}>
                        View Ledger
                      </SecondaryButton>
                      {r.status === 'SANCTIONED' && r.grossAmount != null && (
                        <SecondaryButton onClick={() => setMemoTarget(toMemoData(r))}>Sanction Memo</SecondaryButton>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {ledgerTarget && (
        <ElLedgerModal employeeId={ledgerTarget.employeeId} employeeLabel={ledgerTarget.label} onClose={() => setLedgerTarget(null)} />
      )}

      {memoTarget && <EncashmentSanctionMemoModal app={memoTarget} onClose={() => setMemoTarget(null)} />}
    </Card>
  )
}
