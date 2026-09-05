import { useQuery } from '@tanstack/react-query'
import { CalendarClock, Info } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader } from '../../../components/common/ui'
import type { Page, ShiftMasterResponse } from '../../../types/api'

function formatTime(hhmmss: string): string {
  return hhmmss.slice(0, 5)
}

/**
 * Duty Rosters & Shifts. Per-employee/day roster assignment isn't built yet
 * (no backend endpoint or entity for it) - this page surfaces the real
 * Shift Master catalog (Master Data Console > Shift Master,
 * /api/v1/attendance/shifts) read-only as the honest current state, rather
 * than a static "coming soon" stub with no real data on it.
 */
export function DutyRosterPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['duty-roster-shifts'],
    queryFn: async () => (await apiClient.get<Page<ShiftMasterResponse>>('/v1/attendance/shifts', { params: { size: 50 } })).data.content,
  })

  const activeShifts = (data ?? []).filter((s) => s.active)

  return (
    <div>
      <PageHeader title="Duty Rosters & Shifts" description="Watchmen, Security, and General shift rotations" />

      <Card className="mb-4 flex items-start gap-3 border-brand-jute/40 bg-brand-jute/10">
        <Info className="mt-0.5 shrink-0 text-brand-forest" size={20} />
        <div>
          <p className="text-sm font-semibold text-brand-forest-dark">Roster assignment is coming in a future phase</p>
          <p className="mt-1 text-sm text-brand-forest-dark/80">
            This page currently shows the configured shift catalog (managed under Master Data Console &gt; Shift Master).
            Assigning specific employees to a shift on specific days isn't built yet.
          </p>
        </div>
      </Card>

      <Card>
        {isLoading && <LoadingState label="Loading shifts..." />}
        {isError && <ErrorState message="Could not load the shift catalog." />}
        {data && activeShifts.length === 0 && <EmptyState message="No active shifts configured yet." />}
        {data && activeShifts.length > 0 && (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {activeShifts.map((shift) => (
              <div key={shift.id} className="rounded-lg border border-slate-200 p-3">
                <div className="mb-2 flex items-center justify-between">
                  <span className="font-medium text-slate-700">{shift.shiftName}</span>
                  <Badge tone="brand">{shift.shiftCode}</Badge>
                </div>
                <p className="flex items-center gap-1.5 text-sm text-slate-500">
                  <CalendarClock size={14} />
                  {formatTime(shift.startTime)} &ndash; {formatTime(shift.endTime)}
                  {shift.crossesMidnight && <Badge tone="warning">Overnight</Badge>}
                </p>
                <p className="mt-1 text-xs text-slate-400">Grace period: {shift.gracePeriodMinutes} min</p>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  )
}
