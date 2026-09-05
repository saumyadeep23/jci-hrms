/** Shared KPI ribbon tile for the PIMS reporting hub's tabs. */
export function KpiCard({
  label,
  value,
  tone = 'brand',
}: {
  label: string
  value: string | number | undefined
  tone?: 'brand' | 'success' | 'warning' | 'neutral' | 'danger'
}) {
  const tones: Record<string, string> = {
    brand: 'border-brand-forest/30 bg-brand-forest/5',
    success: 'border-emerald-200 bg-emerald-50',
    warning: 'border-brand-jute/40 bg-brand-jute/10',
    neutral: 'border-slate-200 bg-slate-50',
    danger: 'border-red-200 bg-red-50',
  }
  return (
    <div className={`rounded-xl border p-4 ${tones[tone]}`}>
      <p className="text-xs font-medium text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-slate-800">{value ?? '—'}</p>
    </div>
  )
}
