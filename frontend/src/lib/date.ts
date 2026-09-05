const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})/

/**
 * Formats any date the backend hands us (LocalDate strings like
 * "2026-08-24", full ISO timestamps, or a Date instance) as `pattern`
 * (default "dd-MM-yyyy", the app-wide standard) for display. A pure
 * calendar-date string with the default pattern is parsed by splitting the
 * string directly rather than through `new Date(...)`, which treats a
 * date-only string as UTC midnight and can render as the previous day in a
 * browser whose local timezone is behind UTC; a pattern that also needs
 * time components (e.g. a punch timestamp) always goes through `Date`,
 * which is correct there since those values carry real time-of-day info.
 *
 * Supported tokens: dd, MM, yyyy, hh (12-hour), mm, ss, a (AM/PM).
 */
export function formatDate(value: string | Date | null | undefined, pattern = 'dd-MM-yyyy'): string {
  if (!value) return '--'

  if (typeof value === 'string' && pattern === 'dd-MM-yyyy') {
    const match = ISO_DATE.exec(value)
    if (match) {
      const [, year, month, day] = match
      return `${day}-${month}-${year}`
    }
  }

  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return '--'

  const day = date.getDate()
  const month = date.getMonth() + 1
  const year = date.getFullYear()
  const hours24 = date.getHours()
  const hours12 = hours24 % 12 === 0 ? 12 : hours24 % 12
  const minutes = date.getMinutes()
  const seconds = date.getSeconds()
  const meridiem = hours24 < 12 ? 'AM' : 'PM'
  const pad = (n: number) => String(n).padStart(2, '0')

  return pattern
    .replace('yyyy', String(year))
    .replace('dd', pad(day))
    .replace('MM', pad(month))
    .replace('hh', pad(hours12))
    .replace('mm', pad(minutes))
    .replace('ss', pad(seconds))
    .replace('a', meridiem)
}
