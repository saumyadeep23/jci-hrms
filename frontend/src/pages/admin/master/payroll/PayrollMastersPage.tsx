import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { TransportAllowanceTab } from './TransportAllowanceTab'
import { ProcurementAllowanceTab } from './ProcurementAllowanceTab'
import { PtaxSlabsTab } from './PtaxSlabsTab'
import { HraRatesTab } from './HraRatesTab'
import { SalaryStatutoryHeadsTab } from './SalaryStatutoryHeadsTab'
import { StatutoryParametersTab } from './StatutoryParametersTab'

const TABS = [
  { key: 'transport-allowances', label: 'Transport Allowance' },
  { key: 'procurement-allowances', label: 'Procurement Allowance' },
  { key: 'ptax-slabs', label: 'Professional Tax Slabs' },
  { key: 'hra-rates', label: 'HRA Rates' },
  { key: 'heads', label: 'Salary & Statutory Heads' },
  { key: 'statutory-parameters', label: 'Statutory Parameters' },
] as const

type TabKey = (typeof TABS)[number]['key']

function isTabKey(value: string | null): value is TabKey {
  return TABS.some((t) => t.key === value)
}

/** JCI Payroll Engine master configuration console - transport/procurement allowances, P-Tax slabs, HRA rates, the salary/statutory head catalogs, and statutory parameters. Initial tab can be deep-linked via ?tab=. */
export function PayrollMastersPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const requestedTab = searchParams.get('tab')
  const [tab, setTab] = useState<TabKey>(isTabKey(requestedTab) ? requestedTab : 'transport-allowances')

  function selectTab(next: TabKey) {
    setTab(next)
    setSearchParams({ tab: next }, { replace: true })
  }

  return (
    <div>
      <div className="mb-6 flex flex-wrap gap-2 border-b border-slate-200 pb-3">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => selectTab(t.key)}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${
              tab === t.key ? 'bg-brand-forest text-white' : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'transport-allowances' && <TransportAllowanceTab />}
      {tab === 'procurement-allowances' && <ProcurementAllowanceTab />}
      {tab === 'ptax-slabs' && <PtaxSlabsTab />}
      {tab === 'hra-rates' && <HraRatesTab />}
      {tab === 'heads' && <SalaryStatutoryHeadsTab />}
      {tab === 'statutory-parameters' && <StatutoryParametersTab />}
    </div>
  )
}
