import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { PlayCircle, UploadCloud } from 'lucide-react'
import { apiClient } from '../../api/client'
import { Badge, Card, ErrorState, LoadingState, PageHeader, PrimaryButton } from '../../components/common/ui'
import type { LegacyMigrationStatusResponse } from '../../types/api'

const SAMPLE_LEAVE_BALANCE_ROWS = JSON.stringify(
  { rows: [{ employeeCode: 'EMP-001', leaveTypeCode: 'EL', openingBalance: 12.0, asOnDate: '2026-01-01' }] },
  null,
  2,
)

export function LegacyMigrationPage() {
  const queryClient = useQueryClient()
  const [rowsJson, setRowsJson] = useState(SAMPLE_LEAVE_BALANCE_ROWS)
  const [hrAdminUsername, setHrAdminUsername] = useState('')
  const [stageError, setStageError] = useState<string | null>(null)

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['legacy-migration-status'],
    queryFn: async () => (await apiClient.get<LegacyMigrationStatusResponse>('/legacy-migration/status')).data,
  })

  const stageMutation = useMutation({
    mutationFn: async () => {
      const parsed = JSON.parse(rowsJson)
      return (await apiClient.post('/legacy-migration/stage/leave-balances', parsed)).data
    },
    onSuccess: () => {
      setStageError(null)
      queryClient.invalidateQueries({ queryKey: ['legacy-migration-status'] })
    },
    onError: (err) => setStageError(err instanceof Error ? err.message : 'Failed to stage rows - check the JSON shape.'),
  })

  const promoteMutation = useMutation({
    mutationFn: async () =>
      (await apiClient.post('/legacy-migration/validate-and-promote', { cutoffDate: null, hrAdminUsername })).data,
    onSuccess: () => refetch(),
  })

  return (
    <div>
      <PageHeader title="Legacy Data Migration" description="Stage historical rows, then validate & promote" />

      <Card className="mb-6">
        <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-700">
          <UploadCloud size={16} /> Stage Leave Balances
        </h2>
        <p className="mb-2 text-xs text-slate-400">
          Representative example for one staging category (leave balances). The backend also exposes
          stage/loans, stage/salary-history and stage/service-book with their own row shapes.
        </p>
        <textarea
          value={rowsJson}
          onChange={(e) => setRowsJson(e.target.value)}
          rows={6}
          className="w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-xs"
        />
        <PrimaryButton onClick={() => stageMutation.mutate()} disabled={stageMutation.isPending} className="mt-2">
          Stage Rows
        </PrimaryButton>
        {stageError && <div className="mt-2"><ErrorState message={stageError} /></div>}
      </Card>

      <Card className="mb-6">
        <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-700">
          <PlayCircle size={16} /> Validate & Promote
        </h2>
        <form
          className="flex flex-wrap items-end gap-3"
          onSubmit={(e) => {
            e.preventDefault()
            promoteMutation.mutate()
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">HR Admin Username</label>
            <input
              value={hrAdminUsername}
              onChange={(e) => setHrAdminUsername(e.target.value)}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
              placeholder="hr.admin"
            />
          </div>
          <PrimaryButton type="submit" disabled={promoteMutation.isPending || !hrAdminUsername}>
            Run Validation & Promotion
          </PrimaryButton>
        </form>
      </Card>

      <h2 className="mb-3 text-sm font-semibold text-slate-700">Status by Category</h2>
      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load migration status." />}

      <div className="grid gap-4 sm:grid-cols-2">
        {data?.categories.map((category) => (
          <Card key={category.category}>
            <div className="mb-2 flex items-center justify-between">
              <p className="font-medium text-slate-800">{category.category.replace(/_/g, ' ')}</p>
              <div className="flex gap-1.5">
                <Badge tone="neutral">{category.pendingCount} pending</Badge>
                <Badge tone="success">{category.promotedCount} promoted</Badge>
                <Badge tone="danger">{category.rejectedCount} rejected</Badge>
              </div>
            </div>
            {category.rejectedRows.length > 0 && (
              <ul className="max-h-40 space-y-1 overflow-y-auto rounded-md bg-red-50 p-2 text-xs text-red-700">
                {category.rejectedRows.map((row) => (
                  <li key={row.stagingId}>
                    <span className="font-medium">{row.identifier}:</span> {row.rejectionReason}
                  </li>
                ))}
              </ul>
            )}
          </Card>
        ))}
      </div>
    </div>
  )
}
