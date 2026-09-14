import { useEffect, useRef, useState } from 'react'
import { MoreVertical, UserCog } from 'lucide-react'
import type { JciEccsMemberResponse } from '../../../types/api'

/** Per-row action menu for the Member Directory - single action today (Change Status), left as a
 * dropdown rather than a bare button so future governance actions have a home without a table redesign. */
export function MemberActionsMenu({ member, onChangeStatus }: { member: JciEccsMemberResponse; onChangeStatus: () => void }) {
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
        aria-label={`Actions for ${member.membershipCode}`}
        className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
      >
        <MoreVertical size={16} />
      </button>

      {open && (
        <div role="menu" className="absolute right-0 z-20 mt-1 w-48 rounded-md border border-slate-200 bg-white py-1 text-sm shadow-lg">
          <button
            type="button"
            role="menuitem"
            onClick={(e) => {
              e.stopPropagation()
              setOpen(false)
              onChangeStatus()
            }}
            className="flex w-full items-center gap-2 px-3 py-2 text-left text-slate-700 hover:bg-slate-50"
          >
            <UserCog size={14} className="shrink-0 text-slate-400" />
            Change Status
          </button>
        </div>
      )}
    </div>
  )
}
