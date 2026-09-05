import { useMemo, useState } from 'react'
import { FileWarning } from 'lucide-react'
import { Card, PageHeader } from '../../components/common/ui'

function currentFinancialYearOptions(): string[] {
  const now = new Date()
  const startYear = now.getMonth() >= 3 ? now.getFullYear() : now.getFullYear() - 1
  return Array.from({ length: 5 }, (_, i) => `${startYear - i}-${startYear - i + 1}`)
}

/**
 * There is no income-tax (TDS) computation module anywhere in the backend
 * (confirmed in Phase 11 - PayslipSummaryResponse.taxDeductions is
 * permanently zero because the only deduction head ever seeded is EPF_EE).
 * Section 10 exemptions, standard deduction, 80C, taxable income and a
 * quarterly TDS schedule all require tax-slab logic that doesn't exist -
 * this page is an honest placeholder rather than fabricated numbers.
 */
export function Form16Page() {
  const years = useMemo(() => currentFinancialYearOptions(), [])
  const [financialYear, setFinancialYear] = useState(years[0])

  return (
    <div>
      <PageHeader title="Form-16 Tax Certificate" description="Gross pay, exemptions, deductions & quarterly TDS schedule" />

      <Card className="mb-6">
        <label className="mb-1 block text-xs font-medium text-slate-600">Financial Year</label>
        <select
          value={financialYear}
          onChange={(e) => setFinancialYear(e.target.value)}
          className="w-48 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none"
        >
          {years.map((y) => (
            <option key={y} value={y}>
              {y}
            </option>
          ))}
        </select>
      </Card>

      <Card className="flex flex-col items-center gap-3 py-12 text-center">
        <FileWarning className="text-amber-500" size={32} />
        <p className="max-w-md text-sm font-medium text-slate-700">
          Form-16 is not available yet for {financialYear}.
        </p>
        <p className="max-w-md text-xs text-slate-500">
          This system has no income-tax computation module - there is no salary head, rate table, or endpoint
          anywhere in the backend for Section&nbsp;10 exemptions, the standard deduction, 80C limits, taxable
          income, or a quarterly TDS schedule. Building this page against real numbers requires that backend work
          first; fabricating figures here would be actively misleading on a tax document.
        </p>
      </Card>
    </div>
  )
}
