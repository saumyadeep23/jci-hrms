import { useQuery } from '@tanstack/react-query'
import { Info } from 'lucide-react'
import { apiClient } from '../../api/client'
import { Card } from '../common/ui'
import type { LeaveBalanceResponse, LeaveEntitlementBalanceResponse } from '../../types/api'

function Chip({ label, value, tone }: { label: string; value: string; tone: 'brand' | 'success' | 'neutral' | 'warning' }) {
  const toneClass = {
    brand: 'bg-brand-forest/10 text-brand-forest border-brand-forest/20',
    success: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    neutral: 'bg-slate-100 text-slate-600 border-slate-200',
    warning: 'bg-amber-50 text-amber-700 border-amber-200',
  }[tone]
  return (
    <div className={`rounded-lg border px-3 py-2 ${toneClass}`}>
      <p className="text-[11px] uppercase tracking-wide opacity-70">{label}</p>
      <p className="mt-0.5 text-lg font-semibold tabular-nums">{value}</p>
    </div>
  )
}

/** ALMS Phase 3, Section 2's "Split Balance Display Card" - EL encashable/enjoyable split (LeaveEntitlementBalanceController), CL/HPL/RH from the existing /leave-balances/mine. */
export function LeaveBalanceSplitCard({ year }: { year: number }) {
  const entitlementQuery = useQuery({
    queryKey: ['leave-entitlement-balance-mine', year],
    queryFn: async () =>
      (await apiClient.get<LeaveEntitlementBalanceResponse[]>('/v1/leave-entitlement-balance/mine', { params: { year } })).data,
  })
  const balancesQuery = useQuery({
    queryKey: ['leave-balances-mine', year],
    queryFn: async () => (await apiClient.get<LeaveBalanceResponse[]>('/leave-balances/mine', { params: { year } })).data,
  })

  const el = entitlementQuery.data?.find((b) => b.leaveTypeCode === 'EL')
  const hpl = balancesQuery.data?.find((b) => b.leaveTypeCode === 'HPL')
  const cl = balancesQuery.data?.find((b) => b.leaveTypeCode === 'CL')
  const rh = balancesQuery.data?.find((b) => b.leaveTypeCode === 'RH')

  return (
    <Card>
      <h2 className="mb-3 text-sm font-semibold text-slate-700">Leave Balance Summary</h2>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        <Chip label="Total EL" value={el ? el.availableBalance.toFixed(2) : '—'} tone="brand" />
        <Chip label="Encashable EL" value={el ? el.encashableAvailable.toFixed(2) : '—'} tone="success" />
        <Chip label="Enjoyable EL" value={el ? el.enjoyableAvailable.toFixed(2) : '—'} tone="neutral" />
        <Chip label="HPL" value={hpl ? hpl.availableDays.toFixed(1) : '—'} tone="neutral" />
        <Chip label="Casual Leave (CL)" value={cl ? cl.availableDays.toFixed(1) : '—'} tone="neutral" />
        <Chip
          label="Restricted Holiday (RH)"
          value={rh ? `${rh.usedDays.toFixed(0)}/${rh.creditedDays.toFixed(0)} used` : '—'}
          tone="warning"
        />
      </div>
      {el && (
        <p className="mt-3 flex items-start gap-1.5 text-xs text-slate-500">
          <Info size={13} className="mt-0.5 shrink-0" />
          Enjoyable EL is debited first for physical leave to protect your encashable balance (Favorable Preservation).
        </p>
      )}
    </Card>
  )
}
