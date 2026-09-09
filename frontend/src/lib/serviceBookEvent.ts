import type { ServiceBookEventResponse } from '../types/api'

/** 15 -> "15", 15.5 -> "15.5", 15.25 -> "15.25" - trims the trailing zeros a raw decimal(5,2) carries without losing a genuine fraction. */
function formatDays(value: number): string {
  return Number.isInteger(value) ? String(value) : String(Number(value.toFixed(2)))
}

/** Dynamic "{days} Days Earned Leave Encashment" badge title for an EL encashment service-book event (sanction or arrear clearance); every other event type keeps its plain event-type label. Shared by ServiceBookPage.tsx (/service-book) and ServiceBookTab.tsx (employee directory modal) so the two views can never drift apart. */
export function serviceBookEventTitle(event: Pick<ServiceBookEventResponse, 'eventType' | 'daysEncashed'>): string {
  if (event.eventType === 'EARNED_LEAVE_ENCASHABLE' && event.daysEncashed != null) {
    return `${formatDays(event.daysEncashed)} Days Earned Leave Encashment`
  }
  return event.eventType.replace(/_/g, ' ')
}
