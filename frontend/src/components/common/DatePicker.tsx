import { useRef, useState } from 'react'
import { CalendarDays } from 'lucide-react'
import { errorInputClass } from './ui'

export interface DatePickerProps {
  /** ISO yyyy-MM-dd, or '' - same shape a native <input type="date"> would hold, so this drops into existing form state unchanged. */
  value: string
  /** Always emits ISO yyyy-MM-dd, or '' when cleared/incomplete. */
  onChange: (isoValue: string) => void
  min?: string
  max?: string
  placeholder?: string
  disabled?: boolean
  hasError?: boolean
  className?: string
}

function isoToDisplay(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso)
  return m ? `${m[3]}-${m[2]}-${m[1]}` : ''
}

function maskDigitsToDisplay(digits: string): string {
  const d = digits.slice(0, 8)
  return [d.slice(0, 2), d.slice(2, 4), d.slice(4, 8)].filter(Boolean).join('-')
}

/** null for anything that isn't a complete, calendar-valid dd-MM-yyyy (e.g. 31-02-2026). */
function displayToIso(display: string): string | null {
  const m = /^(\d{2})-(\d{2})-(\d{4})$/.exec(display)
  if (!m) return null
  const [, dd, mm, yyyy] = m
  const date = new Date(Number(yyyy), Number(mm) - 1, Number(dd))
  if (date.getFullYear() !== Number(yyyy) || date.getMonth() !== Number(mm) - 1 || date.getDate() !== Number(dd)) return null
  return `${yyyy}-${mm}-${dd}`
}

/**
 * The app-wide dd-MM-yyyy date field (UAT issue: a bare <input type="date">
 * renders in the browser's OS locale - e.g. MM/DD/YYYY for a US-locale
 * browser - not this app's dd-MM-yyyy standard). Typing digits auto-inserts
 * the dashes; the calendar icon opens the native OS date picker (backed by
 * a visually hidden <input type="date">) for point-and-click selection. Both
 * paths always emit/accept plain ISO yyyy-MM-dd, matching formatDate()'s
 * display convention and every existing form's yyyy-MM-dd state shape.
 */
export function DatePicker({ value, onChange, min, max, placeholder = 'dd-MM-yyyy', disabled, hasError, className = '' }: DatePickerProps) {
  const [text, setText] = useState(() => isoToDisplay(value))
  // Adjust-state-during-render (React's documented pattern for a controlled prop
  // driving internal draft state) instead of a useEffect - resets the visible
  // text whenever the parent changes `value` from outside (e.g. a form reset),
  // without the extra render/lint warning a sync effect would cost here.
  const [lastValue, setLastValue] = useState(value)
  if (value !== lastValue) {
    setLastValue(value)
    setText(isoToDisplay(value))
  }
  const nativeRef = useRef<HTMLInputElement>(null)

  function handleTextChange(raw: string) {
    const masked = maskDigitsToDisplay(raw.replace(/\D/g, ''))
    setText(masked)
    if (masked === '') {
      onChange('')
      return
    }
    const iso = displayToIso(masked)
    if (iso) onChange(iso)
  }

  function openPicker() {
    const el = nativeRef.current
    if (!el || disabled) return
    if (typeof el.showPicker === 'function') {
      try {
        el.showPicker()
        return
      } catch {
        // Some browsers (e.g. when not user-activated) reject showPicker() - fall back below.
      }
    }
    el.focus()
    el.click()
  }

  return (
    <div className={`relative ${className}`}>
      <input
        type="text"
        inputMode="numeric"
        value={text}
        placeholder={placeholder}
        disabled={disabled}
        onChange={(e) => handleTextChange(e.target.value)}
        className={`w-full rounded-md border border-slate-300 px-3 py-2 pr-9 text-sm disabled:bg-slate-50 ${errorInputClass(Boolean(hasError))}`}
      />
      <button
        type="button"
        tabIndex={-1}
        disabled={disabled}
        onClick={openPicker}
        aria-label="Open calendar"
        className="absolute inset-y-0 right-0 flex items-center px-2 text-slate-400 hover:text-slate-600 disabled:opacity-50"
      >
        <CalendarDays size={15} />
      </button>
      <input
        ref={nativeRef}
        type="date"
        tabIndex={-1}
        aria-hidden="true"
        value={value}
        min={min}
        max={max}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
        className="pointer-events-none absolute inset-0 h-full w-full opacity-0"
      />
    </div>
  )
}
