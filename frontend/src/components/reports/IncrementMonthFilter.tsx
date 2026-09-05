const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

/** Increment Due List's "Increment Month" dropdown - filters by the employee's date-of-joining anniversary month. */
export function IncrementMonthFilter({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return (
    <select value={value} onChange={(e) => onChange(e.target.value)} className="rounded-md border border-slate-300 px-3 py-2 text-sm">
      <option value="">All Months</option>
      {MONTHS.map((label, index) => (
        <option key={label} value={index + 1}>
          {label}
        </option>
      ))}
    </select>
  )
}
