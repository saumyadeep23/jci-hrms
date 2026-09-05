interface BrandMarkProps {
  size?: number
  showLegalName?: boolean
  className?: string
}

/**
 * The JCI emblem + legal name pairing used everywhere the spec calls for it:
 * navbar, mobile header, login splash, and printable document headers. The
 * PNG at /assets/jci-logo.png is a placeholder monogram (see
 * scripts/generate-branding.mjs) - swap that file for the real JCI artwork
 * when it's available; nothing else needs to change.
 */
export function BrandMark({ size = 40, showLegalName = true, className = '' }: BrandMarkProps) {
  return (
    <div className={`flex items-center gap-3 ${className}`}>
      <img
        src="/assets/jci-logo.png"
        alt="The Jute Corporation of India Limited emblem"
        width={size}
        height={size}
        className="rounded-full shadow-sm"
      />
      {showLegalName && (
        <div className="leading-tight">
          <p className="font-semibold text-brand-forest text-sm sm:text-base">
            THE JUTE CORPORATION OF INDIA LIMITED
          </p>
          <p className="text-[11px] sm:text-xs text-slate-500">(A Govt. of India Enterprise)</p>
        </div>
      )}
    </div>
  )
}
