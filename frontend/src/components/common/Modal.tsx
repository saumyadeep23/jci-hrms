import type { ReactNode } from 'react'
import { X } from 'lucide-react'

/** The overlay/panel/close-button shell already used identically across the admin CRUD pages, pulled out to avoid re-typing it each time. */
export function Modal({
  title,
  onClose,
  children,
  maxWidthClassName = 'max-w-lg',
}: {
  title: string
  onClose: () => void
  children: ReactNode
  maxWidthClassName?: string
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className={`max-h-[90vh] w-full ${maxWidthClassName} overflow-y-auto rounded-xl bg-white p-6 shadow-xl`}>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-800">{title}</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
            <X size={20} />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
