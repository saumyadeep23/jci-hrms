import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil } from 'lucide-react'
import { apiClient } from '../../../../api/client'
import { useToast } from '../../../../components/common/ToastProvider'
import { describeApiErrorList, getFieldErrors } from '../../../../lib/apiError'
import { DatePicker } from '../../../../components/common/DatePicker'
import { Modal } from '../../../../components/common/Modal'
import { FormErrorBanner } from '../../../../components/common/FormErrorBanner'
import { Badge, Card, EmptyState, ErrorState, FieldError, LoadingState, PageHeader, PrimaryButton, errorInputClass } from '../../../../components/common/ui'
import type { StatutoryParameterReviseRequest, StatutoryParameterResponse } from '../../../../types/api'

function formatValue(param: StatutoryParameterResponse): string {
  if (param.valType === 'PERCENTAGE') return `${param.paramValue}%`
  if (param.valType === 'AMOUNT') return `₹${param.paramValue.toLocaleString('en-IN')}`
  return String(param.paramValue)
}

function unitLabel(param: StatutoryParameterResponse): string {
  return param.valType === 'PERCENTAGE' ? '%' : param.valType === 'AMOUNT' ? '₹' : '—'
}

/** Revision drawer - sets effectiveTo = newEffectiveFrom - 1 day on the current row (server-side) and inserts the new version, so there is never a gap or overlap. */
function ReviseParameterModal({ param, onClose }: { param: StatutoryParameterResponse; onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [newValue, setNewValue] = useState(String(param.paramValue))
  const [newEffectiveFrom, setNewEffectiveFrom] = useState('')
  const [remarks, setRemarks] = useState('')

  const reviseMutation = useMutation({
    mutationFn: async () => {
      const payload: StatutoryParameterReviseRequest = {
        newValue: Number(newValue),
        newEffectiveFrom,
        remarks: remarks || null,
      }
      return (await apiClient.post(`/v1/payroll/masters/statutory-parameters/${param.paramKey}/revise`, payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payroll-statutory-parameters'] })
      show({ tone: 'success', message: `${param.paramName} revised.` })
      onClose()
    },
  })
  const fieldErrors = getFieldErrors(reviseMutation.error)

  const formValid = newValue !== '' && newEffectiveFrom !== '' && newEffectiveFrom > param.effectiveFrom

  return (
    <Modal title={`Revise ${param.paramName}`} onClose={onClose} maxWidthClassName="max-w-md">
      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault()
          reviseMutation.mutate()
        }}
      >
        {reviseMutation.isError && (
          <FormErrorBanner title="Could not revise" errors={describeApiErrorList(reviseMutation.error, 'Could not revise.')} />
        )}

        <p className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-600">
          Current: <strong>{formatValue(param)}</strong> effective since {param.effectiveFrom}. Revising closes that version out
          the day before the new effective date - no gap, no overlap.
        </p>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">New Value ({unitLabel(param)})</label>
          <input
            required
            type="number"
            step="0.0001"
            value={newValue}
            onChange={(e) => setNewValue(e.target.value)}
            className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.newValue))}`}
          />
          <FieldError message={fieldErrors?.newValue} />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">New Effective From</label>
          <DatePicker value={newEffectiveFrom} onChange={setNewEffectiveFrom} hasError={Boolean(fieldErrors?.newEffectiveFrom)} />
          {newEffectiveFrom !== '' && newEffectiveFrom <= param.effectiveFrom && (
            <p className="mt-1 text-xs text-rose-600">Must be after the current version's effective date ({param.effectiveFrom}).</p>
          )}
          <FieldError message={fieldErrors?.newEffectiveFrom} />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Remarks</label>
          <textarea
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            rows={2}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>

        <PrimaryButton type="submit" disabled={reviseMutation.isPending || !formValid} className="w-full justify-center">
          Revise
        </PrimaryButton>
      </form>
    </Modal>
  )
}

/** Statutory Parameters console - CPF/EPS/NPS/GIS/LWF rates and flat amounts, each independently versioned. */
export function StatutoryParametersTab() {
  const [revising, setRevising] = useState<StatutoryParameterResponse | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['payroll-statutory-parameters'],
    queryFn: async () => (await apiClient.get<StatutoryParameterResponse[]>('/v1/payroll/masters/statutory-parameters')).data,
  })

  return (
    <div>
      <PageHeader title="Statutory Parameters" description="Versioned CPF/EPS/NPS/GIS/LWF rates and flat deduction amounts" />

      <Card>
        {isLoading && <LoadingState label="Loading statutory parameters..." />}
        {isError && <ErrorState message="Could not load statutory parameters." />}
        {data && data.length === 0 && <EmptyState message="No statutory parameters configured." />}

        {data && data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Parameter</th>
                  <th className="py-2 pr-3">Key</th>
                  <th className="py-2 pr-3 text-right">Current Value</th>
                  <th className="py-2 pr-3">Unit</th>
                  <th className="py-2 pr-3">Effective From</th>
                  <th className="py-2 pr-3">Status</th>
                  <th className="py-2 pl-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.map((param) => (
                  <tr key={param.paramKey} className="border-b border-slate-100">
                    <td className="py-2 pr-3 font-medium">{param.paramName}</td>
                    <td className="py-2 pr-3 font-mono text-xs text-slate-500">{param.paramKey}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{formatValue(param)}</td>
                    <td className="py-2 pr-3 text-slate-500">{unitLabel(param)}</td>
                    <td className="py-2 pr-3">{param.effectiveFrom}</td>
                    <td className="py-2 pr-3">
                      <Badge tone="success">Active</Badge>
                    </td>
                    <td className="py-2 pl-3 text-right">
                      <button
                        type="button"
                        onClick={() => setRevising(param)}
                        aria-label={`Revise ${param.paramName}`}
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

      {revising && <ReviseParameterModal param={revising} onClose={() => setRevising(null)} />}
    </div>
  )
}
