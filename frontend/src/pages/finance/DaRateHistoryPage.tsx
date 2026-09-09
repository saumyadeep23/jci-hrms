import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, X } from 'lucide-react'
import { apiClient } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { formatDate } from '../../lib/date'
import { describeApiError } from '../../lib/apiError'
import { DateField } from '../../components/common/DateField'
import { DaArrearProjectionTab } from '../../components/finance/da-arrear/DaArrearProjectionTab'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton } from '../../components/common/ui'
import type { DaRateHistoryRequest, DaRateHistoryResponse, ScaleType } from '../../types/api'

type DaRateManagerTab = 'history' | 'arrear-projection'

const EMPTY_FORM = {
  scaleType: 'IDA' as ScaleType,
  effectiveFrom: '',
  daPercentage: '',
  orderNumber: '',
  orderDate: '',
  remarks: '',
}

/**
 * Backend only allows HR_ADMIN/SUPER_ADMIN to POST (DaRateHistoryController) -
 * the Add button is hidden for anyone else so a FINANCE_ADMIN (who can view
 * this page per the route's role gate) doesn't see an action that would 403.
 */
export function DaRateHistoryPage() {
  const { hasRole } = useAuth()
  const canCreate = hasRole('HR_ADMIN', 'SUPER_ADMIN')
  const queryClient = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  // Both tabs stay mounted (toggled via `hidden`, not conditional JSX) so an in-progress Tab 2
  // simulation survives switching to Tab 1 and back - see DaArrearProjectionTab's own comment.
  const [tab, setTab] = useState<DaRateManagerTab>('history')

  const { data, isLoading, isError } = useQuery({
    queryKey: ['da-rates'],
    queryFn: async () => (await apiClient.get<DaRateHistoryResponse[]>('/da-rates')).data,
  })

  const createMutation = useMutation({
    mutationFn: async () => {
      const payload: DaRateHistoryRequest = {
        scaleType: form.scaleType,
        effectiveFrom: form.effectiveFrom,
        daPercentage: Number(form.daPercentage),
        active: true,
        orderNumber: form.orderNumber || null,
        orderDate: form.orderDate || null,
        remarks: form.remarks || null,
      }
      return (await apiClient.post<DaRateHistoryResponse>('/da-rates', payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['da-rates'] })
      setModalOpen(false)
      setForm(EMPTY_FORM)
    },
  })

  return (
    <div>
      <PageHeader
        title="DA Rate Manager"
        description="Dearness Allowance rate timeline and CPSE IDA enhancement / arrear projection"
        actions={
          tab === 'history' &&
          canCreate && (
            <PrimaryButton onClick={() => setModalOpen(true)}>
              <Plus size={15} /> Add DA Rate
            </PrimaryButton>
          )
        }
      />

      <div className="mb-4 flex gap-2">
        <button
          type="button"
          onClick={() => setTab('history')}
          className={`rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${tab === 'history' ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'}`}
        >
          DA Rate Timeline &amp; History
        </button>
        <button
          type="button"
          onClick={() => setTab('arrear-projection')}
          className={`rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${tab === 'arrear-projection' ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'}`}
        >
          DA Enhancement &amp; Arrear Projection
        </button>
      </div>

      <div hidden={tab !== 'history'}>
        <Card>
          {isLoading && <LoadingState label="Loading DA rates..." />}
          {isError && <ErrorState message="Could not load DA rate history." />}
          {data && data.length === 0 && <EmptyState message="No DA rates recorded yet." />}

          {data && data.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[820px] border-collapse text-sm">
                <thead>
                  <tr className="border-b border-slate-300 text-left text-slate-500">
                    <th className="py-2 pr-3">Scale Type</th>
                    <th className="py-2 pr-3">Effective From</th>
                    <th className="py-2 pr-3">Effective To</th>
                    <th className="py-2 pr-3 text-right">Rate %</th>
                    <th className="py-2 pr-3">Order No</th>
                    <th className="py-2 pr-3">Order Date</th>
                    <th className="py-2 pl-3">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {data.map((rate) => (
                    <tr key={rate.id} className="border-b border-slate-100">
                      <td className="py-2 pr-3">
                        <Badge tone="brand">{rate.scaleType}</Badge>
                      </td>
                      <td className="py-2 pr-3 font-medium">{formatDate(rate.effectiveFrom)}</td>
                      <td className="py-2 pr-3">
                        {rate.effectiveTo ? formatDate(rate.effectiveTo) : <Badge tone="success">Current</Badge>}
                      </td>
                      <td className="py-2 pr-3 text-right tabular-nums">{rate.daPercentage.toFixed(2)}%</td>
                      <td className="py-2 pr-3 text-xs text-slate-500">{rate.orderNumber ?? '—'}</td>
                      <td className="py-2 pr-3 text-xs text-slate-500">{rate.orderDate ? formatDate(rate.orderDate) : '—'}</td>
                      <td className="py-2 pl-3">
                        <Badge tone={rate.active ? 'success' : 'neutral'}>{rate.active ? 'Active' : 'Inactive'}</Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>

      {/* Stays mounted even while hidden - see this component's own tab-state comment above. */}
      <div hidden={tab !== 'arrear-projection'}>
        <DaArrearProjectionTab onCommitted={() => setTab('history')} />
      </div>

      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white p-6 shadow-xl">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-lg font-semibold text-slate-800">Add DA Rate</h2>
              <button onClick={() => setModalOpen(false)} className="text-slate-400 hover:text-slate-600">
                <X size={20} />
              </button>
            </div>
            <form
              className="space-y-3"
              onSubmit={(e) => {
                e.preventDefault()
                createMutation.mutate()
              }}
            >
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Scale Type</label>
                <select
                  required
                  value={form.scaleType}
                  onChange={(e) => setForm({ ...form, scaleType: e.target.value as ScaleType })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                >
                  <option value="IDA">IDA - Industrial DA</option>
                  <option value="CDA">CDA - Central DA</option>
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <DateField
                  label="Effective From"
                  required
                  value={form.effectiveFrom}
                  onChange={(value) => setForm({ ...form, effectiveFrom: value })}
                />
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">Rate %</label>
                  <input
                    required
                    type="number"
                    step="0.01"
                    min="0"
                    value={form.daPercentage}
                    onChange={(e) => setForm({ ...form, daPercentage: e.target.value })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-600">Order Number</label>
                  <input
                    value={form.orderNumber}
                    onChange={(e) => setForm({ ...form, orderNumber: e.target.value })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                    placeholder="F.No.1(3)/2026-E.II"
                  />
                </div>
                <DateField
                  label="Order Date"
                  value={form.orderDate}
                  onChange={(value) => setForm({ ...form, orderDate: value })}
                />
              </div>

              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Remarks</label>
                <textarea
                  value={form.remarks}
                  onChange={(e) => setForm({ ...form, remarks: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  rows={2}
                />
              </div>

              <PrimaryButton type="submit" disabled={createMutation.isPending} className="w-full justify-center">
                Save DA Rate
              </PrimaryButton>
              {createMutation.isError && (
                <ErrorState message={describeApiError(createMutation.error, 'Could not save the DA rate.')} />
              )}
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
