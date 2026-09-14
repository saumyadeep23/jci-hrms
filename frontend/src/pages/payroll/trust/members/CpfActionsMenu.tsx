import { useEffect, useRef, useState } from 'react'
import { BookOpen, ChevronDown, Download, History, Landmark, Link2, MoreVertical, ShieldCheck, User } from 'lucide-react'
import type { CpfTrustMemberResponse } from '../../../../types/api'
import { exportMembersCsv } from './cpfMemberUtils'
import type { CpfMemberDrawerTab } from './CpfMemberDrawer'

/** Section 13 - the compact per-row action menu. No generic HR actions here (edit employee, view profile outside CPF context, etc.) - only the three primary CPF actions plus the drawer-tab shortcuts the spec calls out. */
export function CpfActionsMenu({
  member,
  onViewLedger,
  onUpdateUan,
  onCheckEpsEligibility,
  onOpenDrawer,
}: {
  member: CpfTrustMemberResponse
  onViewLedger: () => void
  onUpdateUan: () => void
  onCheckEpsEligibility: () => void
  onOpenDrawer: (tab: CpfMemberDrawerTab) => void
}) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    function handleClick(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) setOpen(false)
    }
    function handleEscape(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handleClick)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [open])

  function act(fn: () => void) {
    return (e: React.MouseEvent) => {
      e.stopPropagation()
      setOpen(false)
      fn()
    }
  }

  return (
    <div className="relative inline-block text-left" ref={containerRef}>
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation()
          setOpen((v) => !v)
        }}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Actions for ${member.fullName}`}
        className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
      >
        <MoreVertical size={16} />
      </button>

      {open && (
        <div
          role="menu"
          className="absolute right-0 z-20 mt-1 w-56 rounded-md border border-slate-200 bg-white py-1 text-sm shadow-lg"
        >
          <MenuItem icon={BookOpen} label="View Ledger" onClick={act(onViewLedger)} />
          <MenuItem icon={Link2} label="Update UAN" onClick={act(onUpdateUan)} />
          <MenuItem icon={Landmark} label="Check EPS Pension Eligibility" onClick={act(onCheckEpsEligibility)} />
          <div className="my-1 border-t border-slate-100" />
          <MenuItem icon={User} label="View Member Details" onClick={act(() => onOpenDrawer('overview'))} />
          <MenuItem icon={Download} label="Download CPF Statement" onClick={act(() => exportMembersCsv([member], `${member.cpfAcNo}-cpf-statement.csv`))} />
          <MenuItem icon={History} label="View Settlement History" onClick={act(() => onOpenDrawer('settlement'))} />
          <MenuItem icon={ShieldCheck} label="View Audit Trail" onClick={act(() => onOpenDrawer('audit'))} />
        </div>
      )}
    </div>
  )
}

function MenuItem({ icon: Icon, label, onClick }: { icon: typeof ChevronDown; label: string; onClick: (e: React.MouseEvent) => void }) {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onClick}
      className="flex w-full items-center gap-2 px-3 py-2 text-left text-slate-700 hover:bg-slate-50"
    >
      <Icon size={14} className="shrink-0 text-slate-400" />
      {label}
    </button>
  )
}
