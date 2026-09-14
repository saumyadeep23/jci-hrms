import { useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { formatInr } from './cpfMemberUtils'

/** Section 6 - the compact expandable/stacked CPF Balance cell. Shows the total balance prominently; EE/VPF/ER only appear once expanded, so the table doesn't need three permanently-wide columns. */
export function CpfFinancialCell({ ee, vpf, er, total }: { ee: number; vpf: number; er: number; total: number }) {
  const [expanded, setExpanded] = useState(false)

  return (
    <div>
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation()
          setExpanded((v) => !v)
        }}
        className="flex items-center gap-1 font-medium tabular-nums text-slate-800 hover:text-brand-forest"
        aria-expanded={expanded}
        aria-label={expanded ? 'Collapse balance breakdown' : 'Expand balance breakdown'}
      >
        {expanded ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
        {formatInr(total)}
      </button>
      {expanded && (
        <div className="mt-1 space-y-0.5 pl-4 text-xs text-slate-500">
          <p>EE {formatInr(ee)}</p>
          <p>VPF {formatInr(vpf)}</p>
          <p>ER {formatInr(er)}</p>
        </div>
      )}
    </div>
  )
}
