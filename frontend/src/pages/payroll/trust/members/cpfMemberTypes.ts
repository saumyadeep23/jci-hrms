import type { CpfSettlementStatus } from '../../../../types/api'

/** The KPI cards / quick-filter chips all resolve to one of these - a single source of truth for "what filter is currently applied" that both drive and reflect the table's actual query params. */
export type CpfMemberQuickFilter = 'all' | 'active' | 'separated' | 'pending' | 'overdue' | 'uanMissing'

export type CpfMemberStatusFilter = '' | 'ACTIVE' | 'SEPARATED'
export type CpfMemberSeparationPeriod = 'any' | '30d' | '3m' | '6m' | '1y' | 'custom'

export interface CpfMemberFilters {
  search: string
  status: CpfMemberStatusFilter
  settlementStatus: CpfSettlementStatus | ''
  separationPeriod: CpfMemberSeparationPeriod
  separationFrom: string
  separationTo: string
  uanMissing: boolean
}

export const DEFAULT_FILTERS: CpfMemberFilters = {
  search: '',
  status: '',
  settlementStatus: '',
  separationPeriod: 'any',
  separationFrom: '',
  separationTo: '',
  uanMissing: false,
}

/** Resolves a named separation-period preset to an ISO [from, to] range as of "today" - "custom" is handled by the caller's own date pickers instead. */
export function resolveSeparationPeriod(period: CpfMemberSeparationPeriod): { from: string | undefined; to: string | undefined } {
  if (period === 'any' || period === 'custom') return { from: undefined, to: undefined }
  const to = new Date()
  const from = new Date()
  if (period === '30d') from.setDate(from.getDate() - 30)
  else if (period === '3m') from.setMonth(from.getMonth() - 3)
  else if (period === '6m') from.setMonth(from.getMonth() - 6)
  else if (period === '1y') from.setFullYear(from.getFullYear() - 1)
  return { from: toIsoDate(from), to: toIsoDate(to) }
}

function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10)
}
