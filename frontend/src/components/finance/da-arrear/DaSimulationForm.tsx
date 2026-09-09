import { useQuery } from '@tanstack/react-query'
import { AlertTriangle } from 'lucide-react'
import { daArrearApi } from './daArrearApi'
import { DatePicker } from '../../common/DatePicker'
import { Badge, Card, PrimaryButton } from '../../common/ui'
import type { ScaleType } from '../../../types/api'

export interface DaSimulationFormState {
  scaleType: ScaleType
  newDaRate: string
  effectiveFrom: string
  drawalMonth: string
  drawalYear: string
  orderNumber: string
  orderDate: string
  remarks: string
}

export function emptySimulationForm(): DaSimulationFormState {
  const today = new Date()
  return {
    scaleType: 'IDA',
    newDaRate: '',
    effectiveFrom: '',
    drawalMonth: String(today.getMonth() + 1),
    drawalYear: String(today.getFullYear()),
    orderNumber: '',
    orderDate: '',
    remarks: '',
  }
}

const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
] as const

/** Mirrors IdaProjectionEngineService.countRetroMonths() exactly, for the live "Calculated Retro Period" badge. */
function retroPeriodLabel(effectiveFrom: string, drawalMonth: string, drawalYear: string): string | null {
  if (!effectiveFrom || !drawalMonth || !drawalYear) return null
  const from = new Date(effectiveFrom)
  if (Number.isNaN(from.getTime())) return null
  const fromKey = from.getFullYear() * 12 + from.getMonth()
  const lastRetroKey = Number(drawalYear) * 12 + (Number(drawalMonth) - 1) - 1
  const count = lastRetroKey - fromKey + 1
  if (count <= 0) return 'No retro months (order effective in or after the drawal month)'
  const fromLabel = `${MONTHS[from.getMonth()].slice(0, 3)} ${from.getFullYear()}`
  const lastRetroDate = new Date(Math.floor(lastRetroKey / 12), lastRetroKey % 12, 1)
  const toLabel = `${MONTHS[lastRetroDate.getMonth()].slice(0, 3)} ${lastRetroDate.getFullYear()}`
  return `Calculated Retro Period: ${count} Month${count > 1 ? 's' : ''} (${fromLabel} to ${toLabel})`
}

export function DaSimulationForm({
  form,
  onChange,
  onRunSimulation,
  isSimulating,
  disabled,
}: {
  form: DaSimulationFormState
  onChange: (form: DaSimulationFormState) => void
  onRunSimulation: () => void
  isSimulating: boolean
  disabled?: boolean
}) {
  const today = new Date().toISOString().slice(0, 10)
  const activeRateQuery = useQuery({
    queryKey: ['da-rate-current', form.scaleType, today],
    queryFn: () => daArrearApi.getActiveDaRate(form.scaleType, today),
  })

  const retroLabel = retroPeriodLabel(form.effectiveFrom, form.drawalMonth, form.drawalYear)
  const canSimulate =
    !disabled && form.newDaRate.trim() !== '' && Number(form.newDaRate) >= 0 && form.effectiveFrom !== '' && !isSimulating

  return (
    <Card>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-slate-700">DA Enhancement Order Details</h2>
        {activeRateQuery.data && (
          <Badge tone="brand">Current Active DA: {activeRateQuery.data.daPercentage.toFixed(2)}%</Badge>
        )}
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Scale Type</label>
          <select
            value={form.scaleType}
            onChange={(e) => onChange({ ...form, scaleType: e.target.value as ScaleType })}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="IDA">IDA - Industrial DA</option>
            <option value="CDA">CDA - Central DA</option>
          </select>
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Proposed New DA %</label>
          <input
            type="number"
            min={0}
            step={0.01}
            value={form.newDaRate}
            onChange={(e) => onChange({ ...form, newDaRate: e.target.value })}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Effective From</label>
          <DatePicker value={form.effectiveFrom} onChange={(v) => onChange({ ...form, effectiveFrom: v })} />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Target Drawal Month</label>
          <select
            value={form.drawalMonth}
            onChange={(e) => onChange({ ...form, drawalMonth: e.target.value })}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            {MONTHS.map((label, i) => (
              <option key={label} value={i + 1}>
                {label}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Target Drawal Year</label>
          <input
            type="number"
            value={form.drawalYear}
            onChange={(e) => onChange({ ...form, drawalYear: e.target.value })}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
      </div>

      {retroLabel && (
        <div className="mt-3">
          <Badge tone="warning">{retroLabel}</Badge>
        </div>
      )}

      <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">DPE Order Number (for Sanction Order)</label>
          <input
            value={form.orderNumber}
            onChange={(e) => onChange({ ...form, orderNumber: e.target.value })}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            placeholder="F.No.1(3)/2026-E.II"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">DPE Order Date</label>
          <DatePicker value={form.orderDate} onChange={(v) => onChange({ ...form, orderDate: v })} />
        </div>
        <div className="sm:col-span-2 lg:col-span-1">
          <label className="mb-1 block text-xs font-medium text-slate-600">Remarks</label>
          <input
            value={form.remarks}
            onChange={(e) => onChange({ ...form, remarks: e.target.value })}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
      </div>
      <p className="mt-1 flex items-start gap-1.5 text-xs text-slate-400">
        <AlertTriangle size={13} className="mt-0.5 shrink-0" />
        Order Number/Date/Remarks are only used when you Issue the Sanction Order (below) - they aren't required to run a simulation.
      </p>

      <div className="mt-4">
        <PrimaryButton onClick={onRunSimulation} disabled={!canSimulate}>
          Run Financial Simulation
        </PrimaryButton>
      </div>
    </Card>
  )
}
