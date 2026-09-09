import { useQuery } from '@tanstack/react-query'
import { History } from 'lucide-react'
import { apiClient } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { formatDate } from '../../lib/date'
import { serviceBookEventTitle } from '../../lib/serviceBookEvent'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader } from '../../components/common/ui'
import type { ServiceBookEventResponse } from '../../types/api'

/** Chronological pill tone per event type - the movement-lifecycle events (TRANSFER_RELEASE/TRANSFER_JOINING/TRANSFER_BENEFIT_EL_CREDIT/PAY_FIXATION) plus PROMOTION get a distinct color; anything else (legacy free-text included) falls back to neutral. */
function eventTone(eventType: string): 'neutral' | 'success' | 'warning' | 'danger' | 'brand' {
  switch (eventType) {
    case 'PROMOTION':
      return 'brand'
    case 'TRANSFER_RELEASE':
      return 'warning'
    case 'TRANSFER_JOINING':
    case 'TRANSFER_BENEFIT_EL_CREDIT':
      return 'success'
    case 'PAY_FIXATION':
      return 'brand'
    default:
      return 'neutral'
  }
}

export function ServiceBookPage() {
  const { employeeId } = useAuth()

  const { data, isLoading, isError } = useQuery({
    queryKey: ['service-book', employeeId],
    queryFn: async () => (await apiClient.get<ServiceBookEventResponse[]>(`/service-book/${employeeId}/timeline`)).data,
    enabled: employeeId !== null,
  })

  return (
    <div>
      <PageHeader title="e-Service Book" description="Chronological career event timeline" />

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load your service book." />}
      {data && data.length === 0 && <EmptyState message="No service book events recorded yet." />}

      {data && data.length > 0 && (
        <ol className="relative space-y-6 border-l-2 border-brand-jute/50 pl-6">
          {data.map((event) => (
            <li key={event.id} className="relative">
              <span className="absolute -left-[31px] top-1 flex h-4 w-4 items-center justify-center rounded-full bg-brand-forest ring-4 ring-white">
                <History size={9} className="text-white" />
              </span>
              <Card>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <Badge tone={eventTone(event.eventType)}>{serviceBookEventTitle(event)}</Badge>
                  <div className="flex items-center gap-2">
                    {event.isMigrated && <Badge tone="neutral">Migrated</Badge>}
                    <span className="text-xs text-slate-400">{formatDate(event.eventDate)}</span>
                  </div>
                </div>
                {/* The full narrative in remarks (e.g. the EL encashment sanction breakdown) supersedes the
                    generic eventDescription placeholder ("Leave Account / Encashments") when both are present. */}
                {event.remarks ? (
                  <p className="mt-1 text-sm text-slate-600">{event.remarks}</p>
                ) : (
                  event.eventDescription && <p className="mt-1 text-sm text-slate-600">{event.eventDescription}</p>
                )}
                <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-slate-500 sm:grid-cols-4">
                  {event.orderNumber && (
                    <div>
                      <dt className="inline text-slate-400">Order: </dt>
                      <dd className="inline">{event.orderNumber}</dd>
                    </div>
                  )}
                  {event.departmentName && (
                    <div>
                      <dt className="inline text-slate-400">Dept: </dt>
                      <dd className="inline">{event.departmentName}</dd>
                    </div>
                  )}
                  {event.designationTitle && (
                    <div>
                      <dt className="inline text-slate-400">Designation: </dt>
                      <dd className="inline">{event.designationTitle}</dd>
                    </div>
                  )}
                  {event.basicPay !== null && (
                    <div>
                      <dt className="inline text-slate-400">Basic Pay: </dt>
                      <dd className="inline">₹{event.basicPay.toFixed(2)}</dd>
                    </div>
                  )}
                </dl>
              </Card>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}
