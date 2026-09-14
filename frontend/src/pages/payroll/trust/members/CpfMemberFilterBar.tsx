import { Search, X } from 'lucide-react'
import { Card } from '../../../../components/common/ui'
import { DatePicker } from '../../../../components/common/DatePicker'
import type { CpfMemberFilters, CpfMemberQuickFilter, CpfMemberSeparationPeriod, CpfMemberStatusFilter } from './cpfMemberTypes'
import { DEFAULT_FILTERS } from './cpfMemberTypes'
import type { CpfSettlementStatus } from '../../../../types/api'

const QUICK_FILTERS: { key: CpfMemberQuickFilter; label: string }[] = [
  { key: 'all', label: 'All' },
  { key: 'active', label: 'Active' },
  { key: 'separated', label: 'Separated' },
  { key: 'pending', label: 'Pending Settlement' },
  { key: 'overdue', label: 'Overdue' },
  { key: 'uanMissing', label: 'UAN Missing' },
]

const SETTLEMENT_OPTIONS: { value: CpfSettlementStatus | ''; label: string }[] = [
  { value: '', label: 'All' },
  { value: 'NOT_APPLICABLE', label: 'Not Applicable' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'IN_PROCESS', label: 'In Process' },
  { value: 'SETTLED', label: 'Settled' },
  { value: 'OVERDUE', label: 'Overdue' },
]

const SEPARATION_PERIOD_OPTIONS: { value: CpfMemberSeparationPeriod; label: string }[] = [
  { value: 'any', label: 'Any Date' },
  { value: '30d', label: 'Last 30 Days' },
  { value: '3m', label: 'Last 3 Months' },
  { value: '6m', label: 'Last 6 Months' },
  { value: '1y', label: 'Last 1 Year' },
  { value: 'custom', label: 'Custom Range' },
]

/** Section 3 - search + Status/Settlement/Separation-Period filters + quick-filter chips, all composing onto one CpfMemberFilters state so they combine (AND) rather than override each other. */
export function CpfMemberFilterBar({
  filters,
  onChange,
  activeQuickFilter,
  onQuickFilterSelect,
}: {
  filters: CpfMemberFilters
  onChange: (filters: CpfMemberFilters) => void
  activeQuickFilter: CpfMemberQuickFilter
  onQuickFilterSelect: (filter: CpfMemberQuickFilter) => void
}) {
  const hasActiveFilters =
    filters.search !== '' ||
    filters.status !== '' ||
    filters.settlementStatus !== '' ||
    filters.separationPeriod !== 'any' ||
    filters.uanMissing

  return (
    <Card className="mb-4">
      <div className="flex flex-wrap items-end gap-3">
        <div className="min-w-[260px] flex-1">
          <label className="mb-1 block text-xs font-medium text-slate-600">Search</label>
          <div className="relative">
            <Search size={14} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              value={filters.search}
              onChange={(e) => onChange({ ...filters, search: e.target.value })}
              placeholder="Search CPF A/C No., UAN or Member Name"
              className="w-full rounded-md border border-slate-300 py-2 pl-8 pr-3 text-sm focus:border-brand-forest focus:outline-none"
              aria-label="Search CPF A/C No., UAN or Member Name"
            />
          </div>
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Status</label>
          <select
            value={filters.status}
            onChange={(e) => onChange({ ...filters, status: e.target.value as CpfMemberStatusFilter })}
            className="w-36 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none"
          >
            <option value="">All</option>
            <option value="ACTIVE">Active</option>
            <option value="SEPARATED">Separated</option>
          </select>
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Settlement</label>
          <select
            value={filters.settlementStatus}
            onChange={(e) => onChange({ ...filters, settlementStatus: e.target.value as CpfSettlementStatus | '' })}
            className="w-40 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none"
          >
            {SETTLEMENT_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Separation Period</label>
          <select
            value={filters.separationPeriod}
            onChange={(e) => onChange({ ...filters, separationPeriod: e.target.value as CpfMemberSeparationPeriod })}
            className="w-44 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none"
          >
            {SEPARATION_PERIOD_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
        </div>

        {filters.separationPeriod === 'custom' && (
          <>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">From</label>
              <DatePicker value={filters.separationFrom} onChange={(v) => onChange({ ...filters, separationFrom: v })} className="w-36" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">To</label>
              <DatePicker value={filters.separationTo} onChange={(v) => onChange({ ...filters, separationTo: v })} className="w-36" />
            </div>
          </>
        )}

        {hasActiveFilters && (
          <button
            type="button"
            onClick={() => onChange(DEFAULT_FILTERS)}
            className="mb-0.5 inline-flex items-center gap-1 rounded-md px-2 py-2 text-xs font-medium text-slate-500 hover:bg-slate-100 hover:text-slate-700"
          >
            <X size={13} /> Clear Filters
          </button>
        )}
      </div>

      <div className="mt-3 flex flex-wrap gap-2 border-t border-slate-100 pt-3">
        {QUICK_FILTERS.map((chip) => (
          <button
            key={chip.key}
            type="button"
            onClick={() => onQuickFilterSelect(chip.key)}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              activeQuickFilter === chip.key ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            {chip.label}
          </button>
        ))}
      </div>
    </Card>
  )
}
