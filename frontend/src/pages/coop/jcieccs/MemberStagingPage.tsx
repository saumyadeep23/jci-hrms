import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { Badge, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../../components/common/ui'
import { describeApiError } from '../../../lib/apiError'
import type { JciEccsMigrationStatusResponse, JciEccsStagingMemberRequest } from '../../../types/api'

const emptyRow: JciEccsStagingMemberRequest = {
  employeeCode: '',
  membershipCode: '',
  membershipDate: new Date().toISOString().slice(0, 10),
  shareBalance: 0,
  fundBalance: 0,
  securityBalance: 0,
  thriftMonthlyAmount: 0,
}

/**
 * Route: /jcieccs/members/staging - Historical Migration Service (JciEccsHistoricalMigrationService):
 * stage a legacy membership row (employee code + audited FY 2023-24 opening balances), then
 * Validate & Promote to turn every still-PENDING staged row into a real jcieccs_member. Re-running
 * Validate & Promote is safe - already PROMOTED/REJECTED rows are never re-selected.
 */
export function MemberStagingPage() {
  const queryClient = useQueryClient()
  const [row, setRow] = useState<JciEccsStagingMemberRequest>(emptyRow)

  const statusQuery = useQuery({
    queryKey: ['jcieccs-migration-status'],
    queryFn: async () => (await apiClient.get<JciEccsMigrationStatusResponse>('/jcieccs/migration/status')).data,
  })

  const stageMutation = useMutation({
    mutationFn: async () => apiClient.post('/jcieccs/migration/stage/members', [row]),
    onSuccess: () => {
      setRow(emptyRow)
      queryClient.invalidateQueries({ queryKey: ['jcieccs-migration-status'] })
    },
  })

  const promoteMutation = useMutation({
    mutationFn: async () => apiClient.post('/jcieccs/migration/validate-and-promote'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['jcieccs-migration-status'] }),
  })

  const status = statusQuery.data

  return (
    <div className="space-y-6">
      <PageHeader title="Migration / Staging" description="Stage legacy co-operative membership codes and FY 2023-24 opening balances, then validate and promote" />

      <div className="rounded-md border border-slate-200 bg-white p-4">
        <p className="mb-3 text-sm font-medium text-slate-700">Stage a membership row</p>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Employee Code</label>
            <input
              value={row.employeeCode}
              onChange={(e) => setRow({ ...row, employeeCode: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Membership Code</label>
            <input
              value={row.membershipCode}
              onChange={(e) => setRow({ ...row, membershipCode: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Membership Date</label>
            <input
              type="date"
              value={row.membershipDate}
              onChange={(e) => setRow({ ...row, membershipDate: e.target.value })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Share Balance</label>
            <input
              type="number"
              value={row.shareBalance ?? 0}
              onChange={(e) => setRow({ ...row, shareBalance: Number(e.target.value) })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Fund Balance</label>
            <input
              type="number"
              value={row.fundBalance ?? 0}
              onChange={(e) => setRow({ ...row, fundBalance: Number(e.target.value) })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Security Balance</label>
            <input
              type="number"
              value={row.securityBalance ?? 0}
              onChange={(e) => setRow({ ...row, securityBalance: Number(e.target.value) })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Thrift Monthly Amount</label>
            <input
              type="number"
              value={row.thriftMonthlyAmount ?? 0}
              onChange={(e) => setRow({ ...row, thriftMonthlyAmount: Number(e.target.value) })}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
        </div>

        <div className="mt-4 flex gap-2">
          <PrimaryButton
            disabled={!row.employeeCode || !row.membershipCode || stageMutation.isPending}
            onClick={() => stageMutation.mutate()}
          >
            {stageMutation.isPending ? 'Staging...' : 'Stage Row'}
          </PrimaryButton>
          <SecondaryButton disabled={promoteMutation.isPending} onClick={() => promoteMutation.mutate()}>
            {promoteMutation.isPending ? 'Promoting...' : 'Validate & Promote All Pending'}
          </SecondaryButton>
        </div>

        {stageMutation.isError && <ErrorState message={describeApiError(stageMutation.error, 'Could not stage this row.')} />}
        {promoteMutation.isError && <ErrorState message={describeApiError(promoteMutation.error, 'Could not run validate-and-promote.')} />}
      </div>

      <div>
        <p className="mb-2 text-sm font-medium text-slate-700">Migration Status</p>
        {statusQuery.isLoading && <LoadingState label="Loading status..." />}
        {statusQuery.isError && <ErrorState message={describeApiError(statusQuery.error, 'Could not load migration status.')} />}
        {status && (
          <div className="space-y-3">
            <div className="flex gap-3">
              <Badge tone="neutral">Pending: {status.pending}</Badge>
              <Badge tone="success">Promoted: {status.promoted}</Badge>
              <Badge tone="danger">Rejected: {status.rejected}</Badge>
            </div>
            {status.rejectedRows.length > 0 && (
              <div className="overflow-x-auto rounded-md border border-slate-200 bg-white">
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                    <tr>
                      <th className="px-3 py-2">Employee Code</th>
                      <th className="px-3 py-2">Membership Code</th>
                      <th className="px-3 py-2">Rejection Reason</th>
                    </tr>
                  </thead>
                  <tbody>
                    {status.rejectedRows.map((r) => (
                      <tr key={r.id} className="border-b border-slate-100 last:border-0">
                        <td className="px-3 py-2">{r.employeeCode}</td>
                        <td className="px-3 py-2">{r.membershipCode}</td>
                        <td className="px-3 py-2 text-red-600">{r.rejectionReason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
