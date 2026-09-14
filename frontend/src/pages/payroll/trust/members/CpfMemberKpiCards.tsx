import { AlertTriangle, Landmark, ShieldAlert, UserCheck, UserMinus, Users, Wallet } from 'lucide-react'
import type { CpfTrustMemberSummaryResponse } from '../../../../types/api'
import { formatInrCompact } from './cpfMemberUtils'
import type { CpfMemberQuickFilter } from './cpfMemberTypes'

interface KpiCardDef {
  key: CpfMemberQuickFilter
  label: string
  value: string
  icon: typeof Users
  attention?: boolean
}

/** Section 2 - the KPI summary strip. Every card is clickable and applies the matching table filter (onSelect); values always come from GET /members/summary (the full membership, independent of whatever the table's own search/filter currently has applied). */
export function CpfMemberKpiCards({
  summary,
  isLoading,
  active,
  onSelect,
}: {
  summary: CpfTrustMemberSummaryResponse | undefined
  isLoading: boolean
  active: CpfMemberQuickFilter
  onSelect: (filter: CpfMemberQuickFilter) => void
}) {
  const cards: KpiCardDef[] = [
    { key: 'all', label: 'Total CPF Members', value: summary ? summary.totalMembers.toLocaleString('en-IN') : '-', icon: Users },
    { key: 'active', label: 'Active Accounts', value: summary ? summary.activeAccounts.toLocaleString('en-IN') : '-', icon: UserCheck },
    { key: 'separated', label: 'Separated Members', value: summary ? summary.separatedMembers.toLocaleString('en-IN') : '-', icon: UserMinus },
    { key: 'pending', label: 'Pending Settlement', value: summary ? summary.pendingSettlement.toLocaleString('en-IN') : '-', icon: Landmark },
    { key: 'overdue', label: 'Overdue Settlement', value: summary ? summary.overdueSettlement.toLocaleString('en-IN') : '-', icon: AlertTriangle, attention: !!summary && summary.overdueSettlement > 0 },
    { key: 'uanMissing', label: 'UAN Missing', value: summary ? summary.uanMissing.toLocaleString('en-IN') : '-', icon: ShieldAlert, attention: !!summary && summary.uanMissing > 0 },
    { key: 'all', label: 'Total CPF Balance', value: summary ? formatInrCompact(summary.totalCpfBalance) : '-', icon: Wallet },
  ]

  return (
    <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-7">
      {cards.map((card, index) => {
        const isActive = active === card.key && card.key !== 'all'
        const Icon = card.icon
        return (
          <button
            key={card.label + index}
            type="button"
            disabled={isLoading}
            onClick={() => onSelect(card.key)}
            className={`rounded-xl border p-3 text-left shadow-sm transition-colors disabled:cursor-wait ${
              isActive
                ? 'border-brand-forest bg-brand-forest/5'
                : card.attention
                  ? 'border-red-200 bg-red-50/60 hover:bg-red-50'
                  : 'border-slate-200 bg-white hover:bg-slate-50'
            }`}
          >
            <div className="flex items-center justify-between">
              <p className={`text-xs ${card.attention ? 'font-medium text-red-700' : 'text-slate-400'}`}>{card.label}</p>
              <Icon size={14} className={card.attention ? 'text-red-500' : 'text-slate-300'} />
            </div>
            <p className={`mt-1 text-xl font-semibold tabular-nums sm:text-2xl ${card.attention ? 'text-red-700' : 'text-slate-800'}`}>
              {card.value}
            </p>
          </button>
        )
      })}
    </div>
  )
}
