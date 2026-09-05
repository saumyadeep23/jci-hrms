import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, PartyPopper } from 'lucide-react'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { Card, ErrorState, LoadingState } from '../common/ui'
import type { HolidayCalendarResponse, HolidayResponse } from '../../types/api'

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]
const WEEKDAY_LABELS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']

interface DayCell {
  day: number
  isWeekend: boolean
  holiday: HolidayResponse | null
}

function buildMonthCells(year: number, month: number, holidays: HolidayResponse[]): (DayCell | null)[] {
  const holidayByDay = new Map<number, HolidayResponse>()
  for (const holiday of holidays) {
    const day = Number(holiday.holidayDate.slice(8, 10))
    // A gazetted holiday wins the cell over a restricted one if both somehow land on the same day.
    const existing = holidayByDay.get(day)
    if (!existing || (existing.holidayType === 'RESTRICTED' && holiday.holidayType === 'GAZETTED')) {
      holidayByDay.set(day, holiday)
    }
  }

  const daysInMonth = new Date(year, month, 0).getDate()
  const firstWeekday = new Date(year, month - 1, 1).getDay()

  const cells: (DayCell | null)[] = Array.from({ length: firstWeekday }, () => null)
  for (let day = 1; day <= daysInMonth; day++) {
    const weekday = new Date(year, month - 1, day).getDay()
    cells.push({ day, isWeekend: weekday === 0 || weekday === 6, holiday: holidayByDay.get(day) ?? null })
  }
  return cells
}

/** Full name, date, and state applicability for the hover tooltip - the day cell itself only has room to truncate the name. */
function holidayTooltip(holiday: HolidayResponse): string {
  const scope = holiday.state ? holiday.state : 'All India / Central'
  return `${holiday.name} - ${formatDate(holiday.holidayDate)} (${scope})`
}

function DayBadge({ cell }: { cell: DayCell }) {
  if (cell.holiday?.holidayType === 'GAZETTED') {
    return (
      <div
        title={holidayTooltip(cell.holiday)}
        className="flex h-full w-full flex-col items-center justify-center rounded-md border border-emerald-300 bg-emerald-100 text-emerald-800"
      >
        <span className="text-sm font-semibold">{cell.day}</span>
        <span className="truncate px-0.5 text-[9px] leading-tight">{cell.holiday.name}</span>
      </div>
    )
  }
  if (cell.holiday?.holidayType === 'RESTRICTED') {
    return (
      <div
        title={`RH: ${holidayTooltip(cell.holiday)}`}
        className="flex h-full w-full flex-col items-center justify-center rounded-md border border-amber-300 bg-amber-100 text-amber-900"
      >
        <span className="text-sm font-semibold">{cell.day}</span>
        <span className="truncate px-0.5 text-[9px] font-medium leading-tight">{cell.holiday.name}</span>
      </div>
    )
  }
  if (cell.isWeekend) {
    return (
      <div className="flex h-full w-full items-center justify-center rounded-md border border-slate-200 bg-slate-100 text-slate-500">
        <span className="text-sm">{cell.day}</span>
      </div>
    )
  }
  return (
    <div className="flex h-full w-full items-center justify-center rounded-md text-slate-700">
      <span className="text-sm">{cell.day}</span>
    </div>
  )
}

/**
 * Dashboard widget for GET /holidays/my-calendar - shows the current
 * employee's location-resolved holiday calendar (national + their
 * RO/DPC/HO state's gazetted holidays + restricted holidays), plus
 * client-computed Saturday/Sunday weekly-off highlighting.
 */
export function HolidayCalendarCard() {
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1) // 1-12

  const { data, isLoading, isError } = useQuery({
    queryKey: ['holiday-my-calendar', year, month],
    queryFn: async () =>
      (await apiClient.get<HolidayCalendarResponse>('/holidays/my-calendar', { params: { year, month } })).data,
  })

  const cells = useMemo(() => buildMonthCells(year, month, data?.holidays ?? []), [year, month, data])

  function goToPreviousMonth() {
    if (month === 1) {
      setMonth(12)
      setYear((y) => y - 1)
    } else {
      setMonth((m) => m - 1)
    }
  }

  function goToNextMonth() {
    if (month === 12) {
      setMonth(1)
      setYear((y) => y + 1)
    } else {
      setMonth((m) => m + 1)
    }
  }

  return (
    <Card>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-slate-700">Holiday Calendar</h2>
        <div className="flex items-center gap-2">
          <button onClick={goToPreviousMonth} className="rounded-md border border-slate-200 p-1 text-slate-500 hover:bg-slate-50" aria-label="Previous month">
            <ChevronLeft size={16} />
          </button>
          <span className="w-32 text-center text-sm font-medium text-slate-600">
            {MONTH_NAMES[month - 1]} {year}
          </span>
          <button onClick={goToNextMonth} className="rounded-md border border-slate-200 p-1 text-slate-500 hover:bg-slate-50" aria-label="Next month">
            <ChevronRight size={16} />
          </button>
        </div>
      </div>

      {isLoading && <LoadingState label="Loading holiday calendar..." />}
      {isError && <ErrorState message="Could not load your holiday calendar." />}

      {data && (
        <>
          <p className="mb-1 text-xs text-slate-500">
            Showing holiday calendar for your posting: <span className="font-medium text-slate-700">{data.officeLabel}</span>
          </p>
          {data.upcomingHolidayName && data.upcomingHolidayDate && (
            <p className="mb-3 flex items-center gap-1.5 text-xs font-medium text-brand-forest">
              <PartyPopper size={13} />
              Upcoming Holiday: {data.upcomingHolidayName} on {formatDate(data.upcomingHolidayDate)}
            </p>
          )}

          <div className="mb-1 grid grid-cols-7 gap-1 text-center text-[11px] text-slate-400">
            {WEEKDAY_LABELS.map((label) => (
              <div key={label}>{label}</div>
            ))}
          </div>
          <div className="grid grid-cols-7 gap-1">
            {cells.map((cell, idx) => (
              <div key={idx} className="aspect-square">
                {cell && <DayBadge cell={cell} />}
              </div>
            ))}
          </div>

          <div className="mt-3 flex flex-wrap gap-3 text-[11px] text-slate-500">
            <span className="flex items-center gap-1">
              <span className="h-2.5 w-2.5 rounded-sm border border-emerald-300 bg-emerald-100" /> Gazetted Holiday
            </span>
            <span className="flex items-center gap-1">
              <span className="h-2.5 w-2.5 rounded-sm border border-amber-300 bg-amber-100" /> Restricted Holiday (RH)
            </span>
            <span className="flex items-center gap-1">
              <span className="h-2.5 w-2.5 rounded-sm border border-slate-200 bg-slate-100" /> Weekly Off
            </span>
          </div>
        </>
      )}
    </Card>
  )
}
