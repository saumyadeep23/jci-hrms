import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Badge, Card, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../components/common/ui'
import type { EmployeeAparResponse } from '../../types/api'

export function AparSelfAppraisalPage() {
  const queryClient = useQueryClient()
  const [aparId, setAparId] = useState('')
  const [activeId, setActiveId] = useState<number | null>(null)
  const [selfText, setSelfText] = useState('')
  const [representationText, setRepresentationText] = useState('')

  const { data, isLoading, isError } = useQuery({
    queryKey: ['apar', activeId],
    queryFn: async () => (await apiClient.get<EmployeeAparResponse>(`/apar/${activeId}`)).data,
    enabled: activeId !== null,
  })

  const submitSelfAppraisal = useMutation({
    mutationFn: async () =>
      (await apiClient.post<EmployeeAparResponse>(`/apar/${activeId}/self-appraisal`, { selfAppraisalText: selfText })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['apar', activeId] }),
  })

  const submitRepresentation = useMutation({
    mutationFn: async () =>
      (await apiClient.post<EmployeeAparResponse>(`/apar/${activeId}/representation`, { representationText })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['apar', activeId] }),
  })

  return (
    <div>
      <PageHeader title="APAR Self-Appraisal" description="Annual Performance Appraisal Report" />

      <Card className="mb-6">
        <form
          className="flex flex-wrap items-end gap-3"
          onSubmit={(e) => {
            e.preventDefault()
            const parsed = Number(aparId)
            if (!Number.isNaN(parsed) && parsed > 0) setActiveId(parsed)
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">APAR ID</label>
            <input
              value={aparId}
              onChange={(e) => setAparId(e.target.value)}
              className="w-40 rounded-md border border-slate-300 px-3 py-2 text-sm"
              placeholder="e.g. 4"
            />
          </div>
          <PrimaryButton type="submit">Load</PrimaryButton>
        </form>
      </Card>

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load that APAR (it may not be yours)." />}

      {data && (
        <Card>
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-700">Cycle {data.aparCycleYear}</h2>
            <Badge tone="brand">{data.status.replace(/_/g, ' ')}</Badge>
          </div>

          {data.status === 'DRAFT' ? (
            <div className="space-y-2">
              <label className="block text-xs font-medium text-slate-600">Self-Appraisal</label>
              <textarea
                value={selfText}
                onChange={(e) => setSelfText(e.target.value)}
                rows={6}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                placeholder="Summarize your key achievements and contributions this cycle..."
              />
              <PrimaryButton onClick={() => submitSelfAppraisal.mutate()} disabled={submitSelfAppraisal.isPending || !selfText.trim()}>
                Submit Self-Appraisal
              </PrimaryButton>
            </div>
          ) : (
            <div className="space-y-3 text-sm">
              <div>
                <p className="text-xs text-slate-400">Self-Appraisal</p>
                <p className="text-slate-700">{data.selfAppraisalText ?? '—'}</p>
              </div>
              {data.reportingScore !== null && (
                <div>
                  <p className="text-xs text-slate-400">Reporting Officer Score</p>
                  <p className="text-slate-700">{data.reportingScore} - {data.reportingRemarks}</p>
                </div>
              )}
              {data.reviewingScore !== null && (
                <div>
                  <p className="text-xs text-slate-400">Reviewing Officer Score</p>
                  <p className="text-slate-700">{data.reviewingScore} - {data.reviewingRemarks}</p>
                </div>
              )}
              {data.finalGrading && (
                <div>
                  <p className="text-xs text-slate-400">Final Grading</p>
                  <p className="text-slate-700">{data.finalGrading} ({data.finalScore})</p>
                </div>
              )}
            </div>
          )}

          {data.status === 'DISCLOSED' && (
            <div className="mt-5 space-y-2 border-t border-slate-100 pt-4">
              <label className="block text-xs font-medium text-slate-600">Representation (if you disagree with the assessment)</label>
              <textarea
                value={representationText}
                onChange={(e) => setRepresentationText(e.target.value)}
                rows={3}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
              <SecondaryButton onClick={() => submitRepresentation.mutate()} disabled={submitRepresentation.isPending || !representationText.trim()}>
                Submit Representation
              </SecondaryButton>
            </div>
          )}
        </Card>
      )}
    </div>
  )
}
