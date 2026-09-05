import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Search } from 'lucide-react'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton } from '../../components/common/ui'
import { JsonDiff } from '../../components/common/JsonDiff'
import type { AuditLogResponse, Page } from '../../types/api'

const ACTION_TONE: Record<string, 'success' | 'brand' | 'danger'> = {
  CREATE: 'success',
  UPDATE: 'brand',
  DELETE: 'danger',
}

export function AuditLogViewerPage() {
  const [filters, setFilters] = useState({ entityName: '', entityId: '', performedBy: '', fromDate: '', toDate: '' })
  const [appliedFilters, setAppliedFilters] = useState(filters)
  const [expandedId, setExpandedId] = useState<number | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['audit-logs', appliedFilters],
    queryFn: async () => {
      const params: Record<string, string> = { size: '50' }
      if (appliedFilters.entityName) params.entityName = appliedFilters.entityName
      if (appliedFilters.entityId) params.entityId = appliedFilters.entityId
      if (appliedFilters.performedBy) params.performedBy = appliedFilters.performedBy
      if (appliedFilters.fromDate) params.fromDate = appliedFilters.fromDate
      if (appliedFilters.toDate) params.toDate = appliedFilters.toDate
      return (await apiClient.get<Page<AuditLogResponse>>('/audit-logs', { params })).data
    },
  })

  return (
    <div>
      <PageHeader title="Audit Log Trail" description="Filterable audit history with a before/after JSON diff inspector" />

      <Card className="mb-6">
        <form
          className="grid grid-cols-2 gap-3 sm:grid-cols-5"
          onSubmit={(e) => {
            e.preventDefault()
            setAppliedFilters(filters)
          }}
        >
          <FilterField label="Entity Name" value={filters.entityName} onChange={(v) => setFilters({ ...filters, entityName: v })} placeholder="Employee" />
          <FilterField label="Entity ID" value={filters.entityId} onChange={(v) => setFilters({ ...filters, entityId: v })} />
          <FilterField label="Performed By" value={filters.performedBy} onChange={(v) => setFilters({ ...filters, performedBy: v })} />
          <FilterField label="From" type="date" value={filters.fromDate} onChange={(v) => setFilters({ ...filters, fromDate: v })} />
          <FilterField label="To" type="date" value={filters.toDate} onChange={(v) => setFilters({ ...filters, toDate: v })} />
          <PrimaryButton type="submit" className="col-span-2 justify-center sm:col-span-1">
            <Search size={14} /> Filter
          </PrimaryButton>
        </form>
      </Card>

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load audit logs." />}
      {data && data.content.length === 0 && <EmptyState message="No audit log entries match these filters." />}

      <div className="space-y-2">
        {data?.content.map((entry) => (
          <Card key={entry.id} className="cursor-pointer" >
            <div onClick={() => setExpandedId(expandedId === entry.id ? null : entry.id)}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-medium text-slate-800">
                  {entry.entityName} #{entry.entityId}
                </p>
                <div className="flex items-center gap-2">
                  <Badge tone={ACTION_TONE[entry.action] ?? 'neutral'}>{entry.action}</Badge>
                  <span className="text-xs text-slate-400">{formatDate(entry.createdAt, 'dd-MM-yyyy hh:mm a')}</span>
                </div>
              </div>
              <p className="text-xs text-slate-500">
                {entry.actingUsername ?? 'unknown'} · {entry.clientIp ?? 'no IP recorded'}
              </p>
            </div>
            {expandedId === entry.id && (
              <div className="mt-3 border-t border-slate-100 pt-3">
                <JsonDiff before={entry.beforeState} after={entry.afterState} />
              </div>
            )}
          </Card>
        ))}
      </div>
    </div>
  )
}

function FilterField({
  label,
  value,
  onChange,
  type = 'text',
  placeholder,
}: {
  label: string
  value: string
  onChange: (v: string) => void
  type?: string
  placeholder?: string
}) {
  return (
    <div>
      <label className="mb-1 block text-xs font-medium text-slate-600">{label}</label>
      <input
        type={type}
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm"
      />
      {type === 'date' && value && <p className="mt-1 text-[11px] text-slate-400">{formatDate(value)}</p>}
    </div>
  )
}
