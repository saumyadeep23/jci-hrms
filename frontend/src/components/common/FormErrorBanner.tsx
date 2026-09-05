import { AlertTriangle } from 'lucide-react'

export interface FormErrorBannerProps {
  title?: string
  errors: string[] | Record<string, string>
}

/**
 * Standardized form-level error banner (PIMS_SPEC.md error-handling task,
 * Section 3) - replaces the ad-hoc `<ErrorState message={describeApiError(...)} />`
 * pattern scattered across submit handlers with one consistent rose-themed
 * summary, listing every individual error (field-level or otherwise) as its
 * own line rather than one run-on sentence.
 */
export function FormErrorBanner({ title = 'Please fix the following', errors }: FormErrorBannerProps) {
  const items = Array.isArray(errors) ? errors : Object.entries(errors).map(([field, message]) => `${field}: ${message}`)
  if (items.length === 0) return null

  return (
    <div className="rounded-md border border-rose-200 bg-rose-50/60 p-3">
      <div className="flex items-center gap-2">
        <AlertTriangle size={16} className="shrink-0 text-rose-600" />
        <p className="text-sm font-semibold text-rose-700">{title}</p>
      </div>
      <ul className="mt-1.5 list-disc space-y-0.5 pl-6 text-sm text-rose-600">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </div>
  )
}
