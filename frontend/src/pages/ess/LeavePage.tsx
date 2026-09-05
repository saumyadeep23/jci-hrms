import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { formatDate } from '../../lib/date'
import { LeaveApplicationForm } from '../../components/leave/LeaveApplicationForm'
import { CombinedLeaveApplicationForm } from '../../components/leave/CombinedLeaveApplicationForm'
import { LeaveBalanceSplitCard } from '../../components/leave/LeaveBalanceSplitCard'
import { Badge, Card, ErrorState, LoadingState, PageHeader, SecondaryButton } from '../../components/common/ui'
import type { EmployeeResponse, LeaveApplicationResponse } from '../../types/api'

type ApplicationMode = 'standard' | 'combined'

const STATUS_TONE: Record<string, 'neutral' | 'success' | 'warning' | 'danger'> = {
  DRAFT: 'neutral',
  PENDING_APPROVAL: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  CANCELLED: 'neutral',
}

/**
 * LeaveApplicationController.list() is HR_ADMIN/SUPER_ADMIN only (no
 * self-filtered "my applications" list), so this page can create/edit/
 * submit/cancel and look an application up by ID (all self-accessible), but
 * cannot show a full application history - that needs a self-scoped list
 * endpoint that doesn't exist yet.
 *
 * A DRAFT application looked up here renders as a fully editable
 * LeaveApplicationForm (Save Draft -> PUT, Submit -> PUT then
 * POST .../submit); any other status renders as the read-only summary
 * below, since LeaveApplicationService.update() only permits editing a
 * DRAFT.
 */
export function LeavePage() {
  const { employeeId } = useAuth()
  const queryClient = useQueryClient()
  const [applicationMode, setApplicationMode] = useState<ApplicationMode>('standard')

  const profile = useQuery({
    queryKey: ['employee', employeeId],
    queryFn: async () => (await apiClient.get<EmployeeResponse>(`/employees/${employeeId}`)).data,
    enabled: employeeId !== null,
  })

  // ---- look up an existing application ----
  const [lookupId, setLookupId] = useState('')
  const [activeId, setActiveId] = useState<number | null>(null)

  const lookup = useQuery({
    queryKey: ['leave-application', activeId],
    queryFn: async () => (await apiClient.get<LeaveApplicationResponse>(`/leave-applications/${activeId}`)).data,
    enabled: activeId !== null,
  })

  const cancelMutation = useMutation({
    mutationFn: async (id: number) => (await apiClient.post<LeaveApplicationResponse>(`/leave-applications/${id}/cancel`)).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['leave-application', activeId] })
      queryClient.invalidateQueries({ queryKey: ['leave-balances-mine'] })
    },
  })

  function onLookedUpDraftSaved() {
    queryClient.invalidateQueries({ queryKey: ['leave-application', activeId] })
  }

  function onNewApplicationSaved(saved: LeaveApplicationResponse) {
    setLookupId(String(saved.id))
    setActiveId(saved.id)
  }

  return (
    <div>
      <PageHeader title="Leave" description="Apply for leave and track application status" />

      {profile.data && (
        <Card className="mb-6">
          <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
            <div>
              <p className="text-xs text-slate-400">Employee ID</p>
              <p className="font-medium text-slate-700">{profile.data.employeeCode}</p>
            </div>
            <div>
              <p className="text-xs text-slate-400">Name</p>
              <p className="font-medium text-slate-700">
                {profile.data.firstName} {profile.data.lastName}
              </p>
            </div>
            <div>
              <p className="text-xs text-slate-400">Designation</p>
              <p className="font-medium text-slate-700">{profile.data.designationTitle}</p>
            </div>
            <div>
              <p className="text-xs text-slate-400">Department</p>
              <p className="font-medium text-slate-700">{profile.data.departmentName}</p>
            </div>
          </div>
        </Card>
      )}

      {employeeId !== null && (
        <div className="mb-6">
          <LeaveBalanceSplitCard year={new Date().getFullYear()} />
        </div>
      )}

      <div className="mb-4 flex gap-2">
        {(['standard', 'combined'] as ApplicationMode[]).map((m) => (
          <button
            key={m}
            type="button"
            onClick={() => setApplicationMode(m)}
            className={`rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${
              applicationMode === m ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'
            }`}
          >
            {m === 'standard' ? 'Standard Leave' : 'Combined CL + RH'}
          </button>
        ))}
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        {employeeId === null ? (
          <Card>
            <LoadingState label="Resolving your employee record..." />
          </Card>
        ) : applicationMode === 'standard' ? (
          <LeaveApplicationForm key="create" mode="create" employeeId={employeeId} onSaved={onNewApplicationSaved} />
        ) : (
          <CombinedLeaveApplicationForm
            key="combined"
            employeeId={employeeId}
            onSaved={() => queryClient.invalidateQueries({ queryKey: ['leave-balances-mine'] })}
          />
        )}

        <div className="space-y-6">
          <Card>
            <h2 className="mb-3 text-sm font-semibold text-slate-700">Look Up Application</h2>
            <form
              className="flex gap-2"
              onSubmit={(e) => {
                e.preventDefault()
                const parsed = Number(lookupId)
                if (!Number.isNaN(parsed) && parsed > 0) setActiveId(parsed)
              }}
            >
              <input
                value={lookupId}
                onChange={(e) => setLookupId(e.target.value)}
                className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm"
                placeholder="Application ID"
              />
              <SecondaryButton type="submit">Load</SecondaryButton>
            </form>

            {lookup.isLoading && (
              <div className="mt-4">
                <LoadingState />
              </div>
            )}
            {lookup.isError && (
              <div className="mt-4">
                <ErrorState message="Could not load that application." />
              </div>
            )}
          </Card>

          {lookup.data && lookup.data.status === 'DRAFT' && employeeId !== null && (
            <LeaveApplicationForm
              key={`edit-${lookup.data.id}`}
              mode="edit"
              employeeId={employeeId}
              application={lookup.data}
              onSaved={onLookedUpDraftSaved}
            />
          )}

          {lookup.data && lookup.data.status !== 'DRAFT' && (
            <Card>
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <p className="font-medium text-slate-700">
                    {lookup.data.leaveTypeCode}
                    {lookup.data.leaveSession !== 'FULL_DAY' && ` (${lookup.data.leaveSession === 'FIRST_HALF' ? '1st Half' : '2nd Half'})`}
                    {' · '}
                    {formatDate(lookup.data.startDate)} to {formatDate(lookup.data.endDate)}
                  </p>
                  <Badge tone={STATUS_TONE[lookup.data.status] ?? 'neutral'}>{lookup.data.status.replace(/_/g, ' ')}</Badge>
                </div>
                <p className="text-sm text-slate-500">{lookup.data.reason}</p>
                {lookup.data.status === 'PENDING_APPROVAL' && (
                  <SecondaryButton onClick={() => cancelMutation.mutate(lookup.data!.id)} disabled={cancelMutation.isPending}>
                    Cancel
                  </SecondaryButton>
                )}
              </div>
            </Card>
          )}
        </div>
      </div>
    </div>
  )
}
