import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { DateField } from '../../components/common/DateField'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../components/common/ui'
import type { AparCycleResponse, Page } from '../../types/api'

export function AparCycleManagementPage() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState({ cycleYear: '', startDate: '', endDate: '' })

  const { data, isLoading, isError } = useQuery({
    queryKey: ['apar-cycles'],
    queryFn: async () => (await apiClient.get<Page<AparCycleResponse>>('/apar/cycles', { params: { size: 20 } })).data,
  })

  const createMutation = useMutation({
    mutationFn: async () => (await apiClient.post('/apar/cycles', form)).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['apar-cycles'] })
      setForm({ cycleYear: '', startDate: '', endDate: '' })
    },
  })

  const advanceMutation = useMutation({
    mutationFn: async (id: number) => (await apiClient.post(`/apar/cycles/${id}/advance`)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['apar-cycles'] }),
  })

  return (
    <div>
      <PageHeader title="APAR Cycle Management" />

      <Card className="mb-6">
        <h2 className="mb-3 text-sm font-semibold text-slate-700">Start a New Cycle</h2>
        <form
          className="flex flex-wrap items-end gap-3"
          onSubmit={(e) => {
            e.preventDefault()
            createMutation.mutate()
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Cycle Year</label>
            <input
              value={form.cycleYear}
              onChange={(e) => setForm({ ...form, cycleYear: e.target.value })}
              placeholder="2025-2026"
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <DateField label="Start Date" value={form.startDate} onChange={(value) => setForm({ ...form, startDate: value })} />
          <DateField label="End Date" value={form.endDate} onChange={(value) => setForm({ ...form, endDate: value })} />
          <PrimaryButton type="submit" disabled={createMutation.isPending}>
            <Plus size={15} /> Create Cycle
          </PrimaryButton>
        </form>
        {createMutation.isError && <ErrorState message="Could not create the cycle (year may already exist)." />}
      </Card>

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load APAR cycles." />}
      {data && data.content.length === 0 && <EmptyState message="No APAR cycles yet." />}

      <div className="space-y-3">
        {data?.content.map((cycle) => (
          <Card key={cycle.id} className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="font-medium text-slate-800">{cycle.cycleYear}</p>
              <p className="text-sm text-slate-500">
                {formatDate(cycle.startDate)} to {formatDate(cycle.endDate)}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <Badge tone="brand">{cycle.status.replace(/_/g, ' ')}</Badge>
              {cycle.status !== 'CLOSED' && (
                <SecondaryButton onClick={() => advanceMutation.mutate(cycle.id)} disabled={advanceMutation.isPending}>
                  Advance Stage
                </SecondaryButton>
              )}
            </div>
          </Card>
        ))}
      </div>
    </div>
  )
}
