import { BrandMark } from './BrandMark'

/** Shared header banner for every printable document template (payslip, PF statement, Form-16, e-Service Book). */
export function PrintableHeader({ documentTitle, subtitle }: { documentTitle: string; subtitle?: string }) {
  return (
    <div className="mb-4 border-b-2 border-brand-forest pb-3">
      <BrandMark size={48} />
      <div className="mt-3 text-center">
        <h2 className="text-base font-bold uppercase tracking-wide text-brand-forest">{documentTitle}</h2>
        {subtitle && <p className="text-sm text-slate-600">{subtitle}</p>}
      </div>
    </div>
  )
}
