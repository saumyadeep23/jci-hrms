import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, X } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { describeApiError } from '../../../lib/apiError'
import { formatDate } from '../../../lib/date'
import { DEVICE_TYPE_LABELS } from '../../../components/attendance/RegisteredDevicesCard'
import { useToast } from '../../../components/common/ToastProvider'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader } from '../../../components/common/ui'
import type { DeviceApprovalStatus, DeviceStatusUpdateRequest, RegisteredDeviceResponse } from '../../../types/api'

const STATUS_TONE: Record<DeviceApprovalStatus, 'neutral' | 'success' | 'warning' | 'danger' | 'brand'> = {
  PENDING_APPROVAL: 'warning',
  APPROVED: 'success',
  REVOKED: 'danger',
}
const STATUS_LABELS: Record<DeviceApprovalStatus, string> = {
  PENDING_APPROVAL: 'Pending Approval',
  APPROVED: 'Approved',
  REVOKED: 'Revoked',
}

const TABS = [
  { key: 'pending', label: 'Pending Approvals' },
  { key: 'all', label: 'All Registered Devices' },
] as const
type TabKey = (typeof TABS)[number]['key']

/** ALMS operational gap #3 - admin approve/revoke queue for employee-registered attendance devices, restricted to HR_ADMIN/SUPER_ADMIN at the route level (see App.tsx). */
export function DeviceMasterPage() {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<TabKey>('pending')
  const [actingOnId, setActingOnId] = useState<number | null>(null)
  const [statusFilter, setStatusFilter] = useState<'ALL' | DeviceApprovalStatus>('ALL')
  const [search, setSearch] = useState('')

  const { data, isLoading, isError } = useQuery({
    queryKey: ['registered-devices-all'],
    queryFn: async () => (await apiClient.get<RegisteredDeviceResponse[]>('/v1/attendance/devices')).data,
  })

  const statusMutation = useMutation({
    mutationFn: async ({ id, status }: { id: number; status: DeviceApprovalStatus }) => {
      const payload: DeviceStatusUpdateRequest = { status }
      await apiClient.patch(`/v1/attendance/devices/${id}/status`, payload)
    },
    onMutate: ({ id }) => setActingOnId(id),
    onSuccess: (_data, { status }) => {
      show({ tone: 'success', message: status === 'APPROVED' ? 'Device approved.' : 'Device request rejected.' })
      queryClient.invalidateQueries({ queryKey: ['registered-devices-all'] })
    },
    onError: (error) => show({ tone: 'error', message: describeApiError(error, 'Could not update this device.') }),
    onSettled: () => setActingOnId(null),
  })

  const pending = (data ?? []).filter((d) => d.status === 'PENDING_APPROVAL')

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    return (data ?? []).filter((d) => {
      if (statusFilter !== 'ALL' && d.status !== statusFilter) return false
      if (q === '') return true
      return (
        d.employeeCode.toLowerCase().includes(q) ||
        d.employeeName.toLowerCase().includes(q) ||
        d.deviceName.toLowerCase().includes(q) ||
        d.officeName.toLowerCase().includes(q)
      )
    })
  }, [data, statusFilter, search])

  return (
    <div>
      <PageHeader title="Registered Devices" description="Employee-registered mobile, laptop/desktop, and biometric-terminal devices for attendance punching" />

      <div className="mb-4 flex gap-2">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setTab(t.key)}
            className={`inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${
              tab === t.key ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'
            }`}
          >
            {t.label}
            {t.key === 'pending' && pending.length > 0 && (
              <span
                className={`inline-flex h-5 min-w-[1.25rem] items-center justify-center rounded-full px-1 text-[11px] font-semibold ${
                  tab === 'pending' ? 'bg-white text-brand-forest' : 'bg-amber-500 text-white'
                }`}
              >
                {pending.length}
              </span>
            )}
          </button>
        ))}
      </div>

      {isLoading && <LoadingState label="Loading registered devices..." />}
      {isError && <ErrorState message="Could not load registered devices." />}

      {data && tab === 'pending' && (
        <Card>
          {pending.length === 0 && <EmptyState message="No pending device requests." />}
          {pending.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[900px] border-collapse text-sm">
                <thead>
                  <tr className="border-b border-slate-300 text-left text-slate-500">
                    <th className="py-2 pr-3">Employee</th>
                    <th className="py-2 pr-3">Office</th>
                    <th className="py-2 pr-3">Device Type</th>
                    <th className="py-2 pr-3">Platform/OS</th>
                    <th className="py-2 pr-3">Requested</th>
                    <th className="py-2 pl-3">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {pending.map((row) => (
                    <tr key={row.id} className="border-b border-slate-100">
                      <td className="py-2 pr-3 font-medium">
                        {row.employeeCode} - {row.employeeName}
                      </td>
                      <td className="py-2 pr-3 text-slate-500">{row.officeName}</td>
                      <td className="py-2 pr-3 text-xs text-slate-500">{DEVICE_TYPE_LABELS[row.deviceType]}</td>
                      <td className="py-2 pr-3 text-xs text-slate-500">{row.platform ?? '—'}</td>
                      <td className="py-2 pr-3 text-slate-500">{formatDate(row.createdAt)}</td>
                      <td className="py-2 pl-3">
                        <div className="flex gap-1.5">
                          <button
                            type="button"
                            disabled={actingOnId === row.id}
                            onClick={() => statusMutation.mutate({ id: row.id, status: 'APPROVED' })}
                            className="inline-flex items-center gap-1 rounded-md border border-emerald-300 bg-emerald-50 px-2 py-1 text-xs font-medium text-emerald-700 hover:bg-emerald-100 disabled:opacity-50"
                          >
                            <Check size={12} /> Approve
                          </button>
                          <button
                            type="button"
                            disabled={actingOnId === row.id}
                            onClick={() => statusMutation.mutate({ id: row.id, status: 'REVOKED' })}
                            className="inline-flex items-center gap-1 rounded-md border border-red-300 bg-red-50 px-2 py-1 text-xs font-medium text-red-700 hover:bg-red-100 disabled:opacity-50"
                          >
                            <X size={12} /> Reject
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}

      {data && tab === 'all' && (
        <Card>
          <div className="mb-4 flex flex-wrap items-center gap-2">
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by employee, device, or office..."
              className="min-w-[220px] flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as 'ALL' | DeviceApprovalStatus)}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="ALL">All Statuses</option>
              <option value="APPROVED">Approved</option>
              <option value="REVOKED">Revoked</option>
              <option value="PENDING_APPROVAL">Pending Approval</option>
            </select>
          </div>

          {filtered.length === 0 && <EmptyState message="No devices match this filter." />}
          {filtered.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[960px] border-collapse text-sm">
                <thead>
                  <tr className="border-b border-slate-300 text-left text-slate-500">
                    <th className="py-2 pr-3">Employee</th>
                    <th className="py-2 pr-3">Office</th>
                    <th className="py-2 pr-3">Device</th>
                    <th className="py-2 pr-3">Type</th>
                    <th className="py-2 pr-3">Identifier</th>
                    <th className="py-2 pr-3">Registered</th>
                    <th className="py-2 pr-3">Status</th>
                    <th className="py-2 pl-3">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((row) => (
                    <tr key={row.id} className="border-b border-slate-100">
                      <td className="py-2 pr-3 font-medium">
                        {row.employeeCode} - {row.employeeName}
                      </td>
                      <td className="py-2 pr-3 text-slate-500">{row.officeName}</td>
                      <td className="py-2 pr-3">{row.deviceName}</td>
                      <td className="py-2 pr-3 text-xs text-slate-500">{DEVICE_TYPE_LABELS[row.deviceType]}</td>
                      <td className="py-2 pr-3 font-mono text-xs text-slate-500">{row.deviceIdentifier}</td>
                      <td className="py-2 pr-3 text-slate-500">{formatDate(row.createdAt)}</td>
                      <td className="py-2 pr-3">
                        <Badge tone={STATUS_TONE[row.status]}>{STATUS_LABELS[row.status]}</Badge>
                      </td>
                      <td className="py-2 pl-3">
                        <div className="flex gap-1.5">
                          {row.status !== 'APPROVED' && (
                            <button
                              type="button"
                              disabled={actingOnId === row.id}
                              onClick={() => statusMutation.mutate({ id: row.id, status: 'APPROVED' })}
                              className="inline-flex items-center gap-1 rounded-md border border-emerald-300 bg-emerald-50 px-2 py-1 text-xs font-medium text-emerald-700 hover:bg-emerald-100 disabled:opacity-50"
                            >
                              <Check size={12} /> Approve
                            </button>
                          )}
                          {row.status !== 'REVOKED' && (
                            <button
                              type="button"
                              disabled={actingOnId === row.id}
                              onClick={() => statusMutation.mutate({ id: row.id, status: 'REVOKED' })}
                              className="inline-flex items-center gap-1 rounded-md border border-red-300 bg-red-50 px-2 py-1 text-xs font-medium text-red-700 hover:bg-red-100 disabled:opacity-50"
                            >
                              <X size={12} /> Revoke
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}
    </div>
  )
}
