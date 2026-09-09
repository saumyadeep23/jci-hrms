import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { PageHeader } from '../../components/common/ui'
import { CadreStrengthTab } from './pims/CadreStrengthTab'
import { SuperannuationTab } from './pims/SuperannuationTab'
import { ReservationRosterTab } from './pims/ReservationRosterTab'
import { Manpower4TierTab } from './pims/Manpower4TierTab'
import { AparMatrixTab } from './pims/AparMatrixTab'
import { IncrementDueListTab } from './pims/IncrementDueListTab'
import { AdHocBuilderTab } from './pims/AdHocBuilderTab'
import { SeparatedStaffTab } from './pims/SeparatedStaffTab'
import { DeputedStaffTab } from './pims/DeputedStaffTab'
import { SuspendedStaffTab } from './pims/SuspendedStaffTab'

const TABS = [
  { key: 'cadre-strength', label: 'Cadre Strength' },
  { key: 'superannuation', label: 'Superannuation' },
  { key: 'reservation-roster', label: 'Reservation Roster' },
  { key: 'manpower-4tier', label: '4-Tier Manpower' },
  { key: 'apar-matrix', label: 'APAR Matrix' },
  { key: 'increment-due-list', label: 'Increment Due List' },
  { key: 'separated-staff', label: 'Separated Staff' },
  { key: 'deputed-staff', label: 'Deputed Staff' },
  { key: 'suspended-staff', label: 'Suspended Staff' },
  { key: 'ad-hoc-builder', label: 'Ad-Hoc Builder' },
] as const
type TabKey = (typeof TABS)[number]['key']
const TAB_KEYS: readonly string[] = TABS.map((t) => t.key)

function isTabKey(value: string | null): value is TabKey {
  return value !== null && TAB_KEYS.includes(value)
}

/** PIMS_SPEC.md reports task, Section 4: the tabbed PIMS Reporting Hub. */
export function PimsReportsHubPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const requestedTab = searchParams.get('tab')
  const [tab, setTab] = useState<TabKey>(isTabKey(requestedTab) ? requestedTab : 'cadre-strength')

  function selectTab(next: TabKey) {
    setTab(next)
    setSearchParams({ tab: next }, { replace: true })
  }

  return (
    <div>
      <PageHeader title="PIMS Reporting Hub" description="Cadre strength, superannuation, reservation, manpower, APAR, and increment analytics" />

      <div className="mb-6 flex flex-wrap gap-1 overflow-x-auto border-b border-slate-200 pb-px">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => selectTab(t.key)}
            className={`whitespace-nowrap rounded-t-md border-b-2 px-3 py-2 text-sm font-medium transition-colors ${
              tab === t.key ? 'border-brand-forest text-brand-forest' : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'cadre-strength' && <CadreStrengthTab />}
      {tab === 'superannuation' && <SuperannuationTab />}
      {tab === 'reservation-roster' && <ReservationRosterTab />}
      {tab === 'manpower-4tier' && <Manpower4TierTab />}
      {tab === 'apar-matrix' && <AparMatrixTab />}
      {tab === 'increment-due-list' && <IncrementDueListTab />}
      {tab === 'separated-staff' && <SeparatedStaffTab />}
      {tab === 'deputed-staff' && <DeputedStaffTab />}
      {tab === 'suspended-staff' && <SuspendedStaffTab />}
      {tab === 'ad-hoc-builder' && <AdHocBuilderTab />}
    </div>
  )
}
