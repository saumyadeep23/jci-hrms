import { Home } from 'lucide-react'

/**
 * Company Accommodation / Quarters Allotment - informational only during onboarding. Quarter
 * allotments (EmployeeQuarterAllotmentController) are keyed by a real employee_id, which doesn't
 * exist until this draft is finalized, so this step cannot collect allotment data itself - it exists
 * so the tab is discoverable during onboarding, pointing HR to where the real form lives once the
 * employee record exists: the "Company Accommodation" tab in Employee Edit (EditEmployeeModal /
 * EmployeeQuarterAllotmentsPanel).
 */
export function Step9CompanyAccommodation() {
  return (
    <div className="space-y-3">
      <div className="flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
        <Home size={16} className="mt-0.5 shrink-0" />
        <span>Providing company-leased accommodation suppresses HRA (Head 9) to ₹0.00 during the occupancy period.</span>
      </div>
      <p className="text-sm text-slate-600">
        Company accommodation can only be allotted once this employee's record exists. Finish and submit onboarding first, then
        open <strong>Employee Edit &rarr; Company Accommodation</strong> to record an allotment order, address, and monthly
        recoveries.
      </p>
    </div>
  )
}
