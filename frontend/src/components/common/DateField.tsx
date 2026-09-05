import { useEffect, useRef, useState } from 'react'
import { Calendar, ChevronLeft, ChevronRight } from 'lucide-react'
import { formatDate } from '../../lib/date'

const DDMMYYYY = /^(\d{2})-(\d{2})-(\d{4})$/
const WEEKDAY_LABELS = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa']
const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

/** "31-02-2026" fails this - JS Date rolls invalid days over into the next month instead of rejecting them. */
function displayToIso(display: string): string | null {
  const match = DDMMYYYY.exec(display)
  if (!match) return null
  const [, dd, mm, yyyy] = match
  const day = Number(dd)
  const month = Number(mm)
  const year = Number(yyyy)
  const date = new Date(year, month - 1, day)
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return null
  return `${yyyy}-${mm}-${dd}`
}

function isoToParts(iso: string): { year: number; month: number; day: number } | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso)
  if (!match) return null
  return { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) }
}

/** Strips to digits and re-inserts dashes as the user types - tolerant of pasted text and backspacing through a dash. */
function autoFormat(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, 8)
  let out = digits.slice(0, 2)
  if (digits.length > 2) out += '-' + digits.slice(2, 4)
  if (digits.length > 4) out += '-' + digits.slice(4, 8)
  return out
}

function toIsoDate(year: number, month: number, day: number): string {
  return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

/**
 * A dd-MM-yyyy text field with a calendar popover, replacing the native
 * `<input type="date">` (whose display format is locale/browser-dependent).
 * External contract unchanged: `value`/`onChange` are plain ISO
 * "yyyy-MM-dd" strings, exactly like the native input this replaces - so no
 * caller needs to change its state shape.
 */
export function DateField({
  label,
  value,
  onChange,
  required,
  disabled,
  min,
  max,
  id,
  name,
  className = '',
}: {
  label: string
  value: string
  onChange: (value: string) => void
  required?: boolean
  disabled?: boolean
  min?: string
  max?: string
  id?: string
  name?: string
  className?: string
}) {
  const [text, setText] = useState(() => (value ? formatDate(value) : ''))
  // Adjusting state during render (React's documented pattern) instead of an
  // effect - avoids an extra render pass just to resync `text` when `value`
  // changes from outside (e.g. a calendar-day click elsewhere, or the parent
  // resetting the form after a save).
  const [prevValue, setPrevValue] = useState(value)
  if (value !== prevValue) {
    setPrevValue(value)
    setText(value ? formatDate(value) : '')
  }
  const [open, setOpen] = useState(false)
  const initialView = isoToParts(value) ?? { year: new Date().getFullYear(), month: new Date().getMonth() + 1, day: 1 }
  const [viewYear, setViewYear] = useState(initialView.year)
  const [viewMonth, setViewMonth] = useState(initialView.month)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [open])

  function handleTextChange(raw: string) {
    const formatted = autoFormat(raw)
    setText(formatted)
    const iso = displayToIso(formatted)
    if (iso) onChange(iso)
  }

  function openCalendar() {
    if (disabled) return
    const parts = isoToParts(value)
    if (parts) {
      setViewYear(parts.year)
      setViewMonth(parts.month)
    }
    setOpen(true)
  }

  function selectDay(day: number) {
    const iso = toIsoDate(viewYear, viewMonth, day)
    onChange(iso)
    setText(formatDate(iso))
    setOpen(false)
  }

  function goToPreviousMonth() {
    if (viewMonth === 1) {
      setViewMonth(12)
      setViewYear((y) => y - 1)
    } else {
      setViewMonth((m) => m - 1)
    }
  }

  function goToNextMonth() {
    if (viewMonth === 12) {
      setViewMonth(1)
      setViewYear((y) => y + 1)
    } else {
      setViewMonth((m) => m + 1)
    }
  }

  const daysInMonth = new Date(viewYear, viewMonth, 0).getDate()
  const firstWeekday = new Date(viewYear, viewMonth - 1, 1).getDay()
  const selectedIso = value || null

  return (
    <div className={`relative ${className}`} ref={containerRef}>
      <label htmlFor={id} className="mb-1 block text-xs font-medium text-slate-600">
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          name={name}
          required={required}
          disabled={disabled}
          type="text"
          inputMode="numeric"
          placeholder="dd-MM-yyyy"
          value={text}
          onChange={(e) => handleTextChange(e.target.value)}
          onFocus={openCalendar}
          className="w-full rounded-md border border-slate-300 px-3 py-2 pr-9 text-sm disabled:bg-slate-50"
        />
        <button
          type="button"
          disabled={disabled}
          onClick={() => (open ? setOpen(false) : openCalendar())}
          className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 disabled:opacity-40"
          aria-label="Open calendar"
        >
          <Calendar size={16} />
        </button>
      </div>

      {open && !disabled && (
        <div className="absolute z-20 mt-1 w-64 rounded-md border border-slate-200 bg-white p-2 shadow-lg">
          <div className="mb-2 flex items-center justify-between">
            <button type="button" onClick={goToPreviousMonth} className="rounded p-1 text-slate-500 hover:bg-slate-100" aria-label="Previous month">
              <ChevronLeft size={14} />
            </button>
            <span className="text-xs font-medium text-slate-700">
              {MONTH_NAMES[viewMonth - 1]} {viewYear}
            </span>
            <button type="button" onClick={goToNextMonth} className="rounded p-1 text-slate-500 hover:bg-slate-100" aria-label="Next month">
              <ChevronRight size={14} />
            </button>
          </div>
          <div className="grid grid-cols-7 gap-0.5 text-center text-[10px] text-slate-400">
            {WEEKDAY_LABELS.map((label) => (
              <div key={label}>{label}</div>
            ))}
          </div>
          <div className="grid grid-cols-7 gap-0.5">
            {Array.from({ length: firstWeekday }, (_, i) => (
              <div key={`blank-${i}`} />
            ))}
            {Array.from({ length: daysInMonth }, (_, i) => {
              const day = i + 1
              const iso = toIsoDate(viewYear, viewMonth, day)
              const isSelected = iso === selectedIso
              const isOutOfRange = (min && iso < min) || (max && iso > max)
              return (
                <button
                  key={day}
                  type="button"
                  disabled={Boolean(isOutOfRange)}
                  onClick={() => selectDay(day)}
                  className={`rounded py-1 text-xs ${
                    isSelected
                      ? 'bg-brand-forest text-white'
                      : isOutOfRange
                        ? 'cursor-not-allowed text-slate-300'
                        : 'text-slate-700 hover:bg-slate-100'
                  }`}
                >
                  {day}
                </button>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
