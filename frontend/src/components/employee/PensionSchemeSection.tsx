import { AlertTriangle } from 'lucide-react'

export interface PensionSchemeValue {
  isNpsEligible: boolean
  isEpsEligible: boolean
  /** Only ever meaningful (and only ever true) when isEpsEligible is also true - forced false whenever EPS is turned off, both here and server-side (EmployeeService.applyOptionalFields). */
  isEpsHigherPensionEligible: boolean
  pranNumber: string
}

const PRAN_PATTERN = /^[0-9]{12}$/

/**
 * Dual pension schemes (NPS / EPS standard / EPS higher pension) - shared
 * between the onboarding wizard's Step 6 (Employment & Post) and Edit
 * Employee's Employment & Post tab, since both need identical toggle-and-
 * cascade behaviour: turning EPS off immediately clears (and hides) Higher
 * Pension, mirroring the backend's own force-clear rule.
 */
export function PensionSchemeSection({ value, onChange }: { value: PensionSchemeValue; onChange: (patch: Partial<PensionSchemeValue>) => void }) {
  const pranMissing = value.isNpsEligible && value.pranNumber.trim() === ''
  const pranInvalid = value.pranNumber !== '' && !PRAN_PATTERN.test(value.pranNumber)

  return (
    <div className="rounded-md border border-slate-200 p-3">
      <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Pension &amp; Retirement Schemes</p>

      <div className="space-y-3">
        <div>
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={value.isNpsEligible}
              onChange={(e) => onChange({ isNpsEligible: e.target.checked })}
            />
            Eligible for NPS (CPSE Defined Contribution Pension Scheme)
          </label>
          <p className="ml-6 mt-0.5 text-[11px] text-slate-400">All regular employees participate in the CPSE Superannuation Pension Scheme.</p>

          {value.isNpsEligible && (
            <div className="ml-6 mt-2 max-w-xs">
              <label className="mb-1 block text-xs font-medium text-slate-600">PRAN (Permanent Retirement Account Number)</label>
              <input
                inputMode="numeric"
                maxLength={12}
                value={value.pranNumber}
                onChange={(e) => onChange({ pranNumber: e.target.value.replace(/\D/g, '').slice(0, 12) })}
                placeholder="12-digit PRAN"
                className={`w-full rounded-md border px-3 py-2 text-sm ${
                  pranMissing || pranInvalid ? 'border-amber-400 bg-amber-50' : 'border-slate-300'
                }`}
              />
              {pranMissing && (
                <p className="mt-1 flex items-center gap-1 text-[11px] text-amber-600">
                  <AlertTriangle size={12} /> PRAN not yet on record - update once allotted.
                </p>
              )}
              {!pranMissing && pranInvalid && <p className="mt-1 text-[11px] text-rose-600">PRAN must be exactly 12 digits.</p>}
            </div>
          )}
        </div>

        <div>
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={value.isEpsEligible}
              onChange={(e) => onChange({ isEpsEligible: e.target.checked, isEpsHigherPensionEligible: e.target.checked && value.isEpsHigherPensionEligible })}
            />
            Eligible for EPS (Employees' Pension Scheme - EPFO)
          </label>

          {value.isEpsEligible && (
            <div className="ml-6 mt-2">
              <label className="flex items-center gap-2 text-sm text-slate-700">
                <input
                  type="checkbox"
                  checked={value.isEpsHigherPensionEligible}
                  onChange={(e) => onChange({ isEpsHigherPensionEligible: e.target.checked })}
                />
                Eligible for EPS Higher Pension (Joint Option on Actual Wages)
              </label>
              <p className="ml-6 mt-0.5 text-[11px] text-slate-400">
                Calculates 8.33% EPS contribution on actual Basic + DA instead of the standard ₹15,000 statutory wage ceiling.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
