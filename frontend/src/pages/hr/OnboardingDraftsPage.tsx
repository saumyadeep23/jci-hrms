import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Plus, Trash2 } from 'lucide-react'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { useToast } from '../../components/common/ToastProvider'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../components/common/ui'
import type { OnboardingDraftResponse, OnboardingStatus, Page } from '../../types/api'

const STATUS_TONE: Record<OnboardingStatus, 'brand' | 'success' | 'neutral'> = {
  IN_PROGRESS: 'brand',
  SUBMITTED: 'success',
  CANCELLED: 'neutral',
}

/** PIMS_SPEC.md Section 5: Draft Resumption List Page. */
export function OnboardingDraftsPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { show } = useToast()

  const { data, isLoading, isError } = useQuery({
    queryKey: ['onboarding-drafts'],
    queryFn: async () =>
      (await apiClient.get<Page<OnboardingDraftResponse>>('/onboarding/drafts', { params: { status: 'IN_PROGRESS', size: 100 } })).data,
  })

  const deleteMutation = useMutation({
    mutationFn: async (draftId: number) => {
      await apiClient.delete(`/v1/onboarding/drafts/${draftId}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['onboarding-drafts'] })
      show({ tone: 'success', message: 'Draft deleted.' })
    },
  })

  function handleDelete(draft: OnboardingDraftResponse) {
    if (window.confirm(`Permanently delete draft ${draft.draftCode}? This cannot be undone.`)) {
      deleteMutation.mutate(draft.id)
    }
  }

  return (
    <div>
      <PageHeader
        title="Onboarding Drafts"
        description="Resume an in-progress employee onboarding, or start a new one"
        actions={
          <PrimaryButton onClick={() => navigate('/onboarding/new')}>
            <Plus size={15} /> New Onboarding
          </PrimaryButton>
        }
      />

      <Card>
        {isLoading && <LoadingState label="Loading drafts..." />}
        {isError && <ErrorState message="Could not load onboarding drafts." />}
        {data && data.content.length === 0 && <EmptyState message="No in-progress onboarding drafts." />}

        {data && data.content.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Draft Code</th>
                  <th className="py-2 pr-3">Candidate</th>
                  <th className="py-2 pr-3">Completion</th>
                  <th className="py-2 pr-3">Status</th>
                  <th className="py-2 pr-3">Created</th>
                  <th className="py-2 pr-3">Last Updated</th>
                  <th className="py-2 pl-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((draft) => (
                  <tr key={draft.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3 font-medium">{draft.draftCode}</td>
                    <td className="py-2 pr-3">
                      {draft.personal ? `${draft.personal.firstName} ${draft.personal.lastName}` : <span className="text-slate-400">Not yet named</span>}
                    </td>
                    <td className="py-2 pr-3">
                      <div className="flex items-center gap-2">
                        <div className="h-2 w-24 overflow-hidden rounded-full bg-slate-100">
                          <div className="h-full bg-brand-forest" style={{ width: `${draft.completionPercentage}%` }} />
                        </div>
                        <span className="text-xs text-slate-500">{draft.completionPercentage}%</span>
                      </div>
                    </td>
                    <td className="py-2 pr-3">
                      <Badge tone={STATUS_TONE[draft.status]}>{draft.status.replace('_', ' ')}</Badge>
                    </td>
                    <td className="py-2 pr-3 text-xs text-slate-500">{formatDate(draft.createdAt, 'dd-MM-yyyy hh:mm a')}</td>
                    <td className="py-2 pr-3 text-xs text-slate-500">{formatDate(draft.updatedAt, 'dd-MM-yyyy hh:mm a')}</td>
                    <td className="py-2 pl-3 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <SecondaryButton onClick={() => navigate(`/onboarding/new?draftId=${draft.id}`)}>Resume</SecondaryButton>
                        <button
                          type="button"
                          onClick={() => handleDelete(draft)}
                          disabled={deleteMutation.isPending}
                          title="Delete draft permanently"
                          className="rounded-md p-1.5 text-rose-500 hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          <Trash2 size={15} />
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
    </div>
  )
}
