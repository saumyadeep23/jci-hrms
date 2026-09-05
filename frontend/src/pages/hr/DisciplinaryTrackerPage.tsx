import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Gavel, Plus } from 'lucide-react'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../components/common/ui'
import type { DisciplinaryCaseResponse, DisciplinaryStageUpdateRequest, Page } from '../../types/api'

const CASE_TYPES = ['CONDUCT_RULES', 'FINANCIAL_IRREGULARITY', 'VIGILANCE', 'ABSENTEEISM']
const NEXT_STAGE: Record<string, string[]> = {
  INITIATED: ['CHARGE_SHEET_ISSUED', 'CLOSED'],
  CHARGE_SHEET_ISSUED: ['INQUIRY_IN_PROGRESS', 'CLOSED'],
  INQUIRY_IN_PROGRESS: ['REPORT_SUBMITTED', 'CLOSED'],
  REPORT_SUBMITTED: ['PENALTY_IMPOSED', 'EXONERATED'],
  PENALTY_IMPOSED: ['CLOSED'],
  EXONERATED: ['CLOSED'],
  CLOSED: [],
}

const STATUS_TONE: Record<string, 'neutral' | 'success' | 'warning' | 'danger'> = {
  INITIATED: 'neutral',
  CHARGE_SHEET_ISSUED: 'warning',
  INQUIRY_IN_PROGRESS: 'warning',
  REPORT_SUBMITTED: 'warning',
  PENALTY_IMPOSED: 'danger',
  EXONERATED: 'success',
  CLOSED: 'neutral',
}

export function DisciplinaryTrackerPage() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState({ caseNumber: '', employeeId: '', caseType: CASE_TYPES[0] })

  const { data, isLoading, isError } = useQuery({
    queryKey: ['disciplinary-cases'],
    queryFn: async () => (await apiClient.get<Page<DisciplinaryCaseResponse>>('/disciplinary', { params: { size: 50 } })).data,
  })

  const createMutation = useMutation({
    mutationFn: async () =>
      (await apiClient.post('/disciplinary', { ...form, employeeId: Number(form.employeeId) })).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['disciplinary-cases'] })
      setForm({ caseNumber: '', employeeId: '', caseType: CASE_TYPES[0] })
    },
  })

  const stageMutation = useMutation({
    mutationFn: async ({ id, body }: { id: number; body: DisciplinaryStageUpdateRequest }) =>
      (await apiClient.put(`/disciplinary/${id}/stage`, body)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['disciplinary-cases'] }),
  })

  function advance(caseItem: DisciplinaryCaseResponse, targetStatus: string) {
    const body: DisciplinaryStageUpdateRequest = { targetStatus }
    if (targetStatus === 'CHARGE_SHEET_ISSUED') body.chargeSheetDate = new Date().toISOString().slice(0, 10)
    if (targetStatus === 'INQUIRY_IN_PROGRESS') {
      const officerId = window.prompt('Inquiry officer employee ID?')
      if (!officerId) return
      body.inquiryOfficerId = Number(officerId)
    }
    if (targetStatus === 'PENALTY_IMPOSED') {
      const penaltyType = window.prompt('Penalty type (CENSURE, WITHHOLDING_INCREMENT, REDUCTION_IN_PAY_SCALE, RECOVERY_OF_LOSS, SUSPENSION, COMPULSORY_RETIREMENT, DISMISSAL)?')
      if (!penaltyType) return
      body.penaltyType = penaltyType
      body.penaltyEffectiveFrom = new Date().toISOString().slice(0, 10)
    }
    stageMutation.mutate({ id: caseItem.id, body })
  }

  return (
    <div>
      <PageHeader title="Disciplinary Case Tracker" />

      <Card className="mb-6">
        <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-700">
          <Gavel size={16} /> Initiate New Case
        </h2>
        <form
          className="flex flex-wrap items-end gap-3"
          onSubmit={(e) => {
            e.preventDefault()
            createMutation.mutate()
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Case Number</label>
            <input
              value={form.caseNumber}
              onChange={(e) => setForm({ ...form, caseNumber: e.target.value })}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
              placeholder="DC-2026-001"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Employee ID</label>
            <input
              value={form.employeeId}
              onChange={(e) => setForm({ ...form, employeeId: e.target.value })}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Case Type</label>
            <select
              value={form.caseType}
              onChange={(e) => setForm({ ...form, caseType: e.target.value })}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              {CASE_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t.replace(/_/g, ' ')}
                </option>
              ))}
            </select>
          </div>
          <PrimaryButton type="submit" disabled={createMutation.isPending}>
            <Plus size={15} /> Create Case
          </PrimaryButton>
        </form>
        {createMutation.isError && <ErrorState message="Could not create the case (case number may already be in use)." />}
      </Card>

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load disciplinary cases." />}
      {data && data.content.length === 0 && <EmptyState message="No disciplinary cases on record." />}

      <div className="space-y-3">
        {data?.content.map((item) => (
          <Card key={item.id}>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <p className="font-medium text-slate-800">
                  {item.caseNumber} · {item.employeeCode}
                </p>
                <p className="text-sm text-slate-500">{item.caseType.replace(/_/g, ' ')}</p>
              </div>
              <Badge tone={STATUS_TONE[item.status] ?? 'neutral'}>{item.status.replace(/_/g, ' ')}</Badge>
            </div>
            {item.penaltyType && (
              <p className="mt-2 text-xs text-slate-500">
                Penalty: {item.penaltyType.replace(/_/g, ' ')} ({formatDate(item.penaltyEffectiveFrom)} -{' '}
                {item.penaltyEffectiveTo ? formatDate(item.penaltyEffectiveTo) : 'ongoing'})
              </p>
            )}
            {NEXT_STAGE[item.status]?.length > 0 && (
              <div className="mt-3 flex flex-wrap gap-2">
                {NEXT_STAGE[item.status].map((target) => (
                  <SecondaryButton key={target} onClick={() => advance(item, target)} disabled={stageMutation.isPending}>
                    → {target.replace(/_/g, ' ')}
                  </SecondaryButton>
                ))}
              </div>
            )}
          </Card>
        ))}
      </div>
    </div>
  )
}
