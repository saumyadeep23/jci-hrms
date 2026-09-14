import type { CpfSettlementStatus, CpfTrustMemberResponse } from '../../../../types/api'

/** Indian-grouping currency string, e.g. 540690 -> "5,40,690" (no paise - this module's tables/cards never need sub-rupee precision). */
export function formatInr(amount: number): string {
  return `₹${Math.round(amount).toLocaleString('en-IN')}`
}

/** ₹XX.XX Cr / ₹XX.XX L compact form for the "Total CPF Balance" KPI card - a plain formatInr() of a crore-scale sum is unreadable at a glance. */
export function formatInrCompact(amount: number): string {
  const abs = Math.abs(amount)
  if (abs >= 1_00_00_000) return `₹${(amount / 1_00_00_000).toFixed(2)} Cr`
  if (abs >= 1_00_000) return `₹${(amount / 1_00_000).toFixed(2)} L`
  return formatInr(amount)
}

export const SETTLEMENT_STATUS_LABELS: Record<CpfSettlementStatus, string> = {
  NOT_APPLICABLE: 'Not Applicable',
  PENDING: 'Pending',
  IN_PROCESS: 'In Process',
  OVERDUE: 'Overdue',
  SETTLED: 'Settled',
}

export function settlementStatusTone(status: CpfSettlementStatus): 'neutral' | 'success' | 'warning' | 'danger' {
  switch (status) {
    case 'SETTLED':
      return 'success'
    case 'OVERDUE':
      return 'danger'
    case 'PENDING':
    case 'IN_PROCESS':
      return 'warning'
    default:
      return 'neutral'
  }
}

/** April-March FY label ("2026-2027") matching the app-wide convention (see CpfLoansPage's own copy of this same convention). */
export function currentFinancialYear(): string {
  const now = new Date()
  const startYear = now.getMonth() >= 3 ? now.getFullYear() : now.getFullYear() - 1
  return `${startYear}-${startYear + 1}`
}

function csvCell(value: string | number | null | undefined): string {
  const text = value === null || value === undefined ? '' : String(value)
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

/** Client-side CSV export of the currently loaded/filtered rows - there is no dedicated CPF-Trust report type in the backend's PIMS export engine yet, so this exports what the table already has rather than adding a new server-side report for one table. */
export function exportMembersCsv(rows: CpfTrustMemberResponse[], filename = 'cpf-trust-members.csv') {
  const headers = [
    'CPF A/C No', 'Member Name', 'Employee Code', 'UAN', 'Status', 'EE Balance', 'VPF Balance', 'ER Balance',
    'CPF Balance', 'Accrued Interest', 'Total Payable', 'Separation Date', 'Settlement Status',
    'Settlement Due Date', 'Settlement Date', 'Due / Lag',
  ]
  const lines = [headers.join(',')]
  for (const row of rows) {
    lines.push([
      csvCell(row.cpfAcNo), csvCell(row.fullName), csvCell(row.employeeCode), csvCell(row.uanNo ?? 'MISSING'),
      csvCell(row.status), csvCell(row.eeBalance), csvCell(row.vpfBalance), csvCell(row.erBalance),
      csvCell(row.cpfBalance), csvCell(row.accruedInterest), csvCell(row.totalPayable), csvCell(row.separationDate),
      csvCell(SETTLEMENT_STATUS_LABELS[row.settlementStatus]), csvCell(row.settlementDueDate), csvCell(row.settlementDate),
      csvCell(row.settlementLagLabel),
    ].join(','))
  }
  const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
