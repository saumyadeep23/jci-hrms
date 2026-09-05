import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { Pencil, Plus } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { describeApiError } from '../../../lib/apiError'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader } from '../../../components/common/ui'
import { GradeScaleFormModal } from './GradeScaleFormModal'
import type { GradeScaleMasterResponse } from '../../../types/api'

function rupees(amount: number | null): string {
  return amount != null ? `₹${amount.toLocaleString('en-IN')}` : '—'
}

function cadreTone(cadre: string): 'brand' | 'success' | 'neutral' {
  if (cadre === 'BOARD') return 'brand'
  if (cadre === 'EXECUTIVE') return 'success'
  return 'neutral'
}

/**
 * New, additive Board/Executive/Staff grade-scale master (15 rows, E9-E0/S5-S1) - the Master Data
 * Console's single "Grade & Pay Scales" tab. The pre-existing PayScaleController/PayScale system
 * (pay_scale_master) is left running unchanged underneath payroll/LPC/etc. - see GradeScaleMaster
 * .java's javadoc - this tab is a UI consolidation only, not a data migration.
 */
export function GradeScalesTab() {
  const [editing, setEditing] = useState<GradeScaleMasterResponse | null>(null)
  const [adding, setAdding] = useState(false)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['grade-scales'],
    queryFn: async () => (await apiClient.get<GradeScaleMasterResponse[]>('/v1/masters/grade-scales')).data,
  })

  useEffect(() => {
    if (!error) return
    // eslint-disable-next-line no-console
    console.error(
      '[GradeScalesTab] Failed to load /v1/masters/grade-scales:',
      describeApiError(error, 'Unknown error'),
      isAxiosError(error) ? { status: error.response?.status, data: error.response?.data } : error,
    )
  }, [error])

  return (
    <div>
      <PageHeader
        title="Grade Scale Master"
        description="Board / Executive / Staff cadre hierarchy (E9-E0, S5-S1)"
        actions={
          <button
            type="button"
            onClick={() => setAdding(true)}
            className="bg-emerald-800 hover:bg-emerald-900 text-white text-xs font-semibold px-4 py-2 rounded-lg flex items-center gap-1.5"
          >
            <Plus size={14} />
            Add Grade Scale
          </button>
        }
      />

      <Card>
        {isLoading && <LoadingState label="Loading grade scales..." />}
        {isError && <ErrorState message="Could not load grade scales." />}
        {data && data.length === 0 && <EmptyState message="No grade scales configured." />}

        {data && data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[920px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Role</th>
                  <th className="py-2 pr-3">Scale</th>
                  <th className="py-2 pr-3">Hierarchy Rank</th>
                  <th className="py-2 pr-3">Regular Scale (as per IDA Pay pattern)</th>
                  <th className="py-2 pr-3 text-right">Contractual Lumpsum (₹)</th>
                  <th className="py-2 pr-3 text-right">Outsourced CTC (₹)</th>
                  <th className="py-2 pr-3">Status</th>
                  <th className="py-2 pl-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.map((grade) => (
                  <tr key={grade.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3">
                      <Badge tone={cadreTone(grade.cadre)}>{grade.cadre}</Badge>
                    </td>
                    <td className="py-2 pr-3 font-medium">{grade.scaleCode}</td>
                    <td className="py-2 pr-3">L-{grade.hierarchyLevel}</td>
                    <td className="py-2 pr-3">{grade.idaScaleLabel}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{rupees(grade.contractualLumpsum)}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{rupees(grade.outsourcedCtc)}</td>
                    <td className="py-2 pr-3">
                      <Badge tone={grade.active ? 'success' : 'neutral'}>{grade.active ? 'Active' : 'Inactive'}</Badge>
                    </td>
                    <td className="py-2 pl-3 text-right">
                      <button
                        type="button"
                        onClick={() => setEditing(grade)}
                        aria-label={`Edit ${grade.scaleCode}`}
                        className="inline-flex items-center justify-center rounded-md p-1.5 text-brand-forest hover:bg-slate-100"
                      >
                        <Pencil size={14} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {(adding || editing) && (
        <GradeScaleFormModal
          editing={editing}
          onClose={() => {
            setAdding(false)
            setEditing(null)
          }}
        />
      )}
    </div>
  )
}
