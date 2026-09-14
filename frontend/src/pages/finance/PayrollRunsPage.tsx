import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { apiClient } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/common/ToastProvider'
import { describeApiError } from '../../lib/apiError'
import { Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton } from '../../components/common/ui'
import type { Page, PayrollBatchResponse, PayrollBatchType, PayrollMonthlyRecordResponse } from '../../types/api'
import { PayrollBatchCard } from './PayrollBatchCard'
import { EditEmployeeSalaryModal } from './EditEmployeeSalaryModal'
import { PayrollReportsPanel } from './PayrollReportsPanel'

type Tab = 'reconciliation' | 'reports'

export function PayrollRunsPage() {
  const queryClient = useQueryClient()
  const { show } = useToast()
  const { employeeId } = useAuth()
  const [form, setForm] = useState({ cycleYear: new Date().getFullYear(), cycleMonth: new Date().getMonth() + 1, batchType: 'REGULAR' as PayrollBatchType })
  const [selectedBatchId, setSelectedBatchId] = useState<number | null>(null)
  const [tab, setTab] = useState<Tab>('reconciliation')
  const [expandedRecord, setExpandedRecord] = useState<PayrollMonthlyRecordResponse | null>(null)
  const [editingRecord, setEditingRecord] = useState<PayrollMonthlyRecordResponse | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['payroll-batches'],
    queryFn: async () => (await apiClient.get<Page<PayrollBatchResponse>>('/v1/payroll/batches', { params: { size: 24 } })).data,
  })

  const { data: records } = useQuery({
    queryKey: ['payroll-batch-records', selectedBatchId],
    queryFn: async () =>
      (
        await apiClient.get<Page<PayrollMonthlyRecordResponse>>(`/v1/payroll/batches/${selectedBatchId}/records`, { params: { size: 200 } })
      ).data,
    enabled: selectedBatchId !== null,
  })

  const createMutation = useMutation({
    mutationFn: async () => (await apiClient.post('/v1/payroll/batches', form)).data,
    onSuccess: () => {
      show({ tone: 'success', message: 'Payroll batch created.' })
      queryClient.invalidateQueries({ queryKey: ['payroll-batches'] })
    },
  })

  const computeMutation = useMutation({
    mutationFn: async (id: number) => (await apiClient.post(`/v1/payroll/batches/${id}/calculate`)).data,
    onSuccess: () => {
      show({ tone: 'success', message: 'Batch computed.' })
      queryClient.invalidateQueries({ queryKey: ['payroll-batches'] })
      queryClient.invalidateQueries({ queryKey: ['payroll-batch-records', selectedBatchId] })
    },
  })

  const finalizeMutation = useMutation({
    mutationFn: async (id: number) =>
      (await apiClient.post(`/v1/payroll/batches/${id}/finalize`, null, { params: { finalizedByEmployeeId: employeeId } })).data,
    onSuccess: () => {
      show({ tone: 'success', message: 'Batch finalized.' })
      queryClient.invalidateQueries({ queryKey: ['payroll-batches'] })
    },
  })

  const disburseMutation = useMutation({
    mutationFn: async (id: number) =>
      (await apiClient.post(`/v1/payroll/batches/${id}/disburse`, null, { params: { disbursedByEmployeeId: employeeId } })).data,
    onSuccess: () => {
      show({ tone: 'success', message: 'Batch disbursed - CPF Trust ledger synced and ESS pay slips are now visible.' })
      queryClient.invalidateQueries({ queryKey: ['payroll-batches'] })
    },
  })

  const selectedBatch = data?.content.find((b) => b.id === selectedBatchId) ?? null
  const editable = selectedBatch?.status === 'DRAFT' || selectedBatch?.status === 'CALCULATED'

  return (
    <div>
      <PageHeader title="Payroll Batches" description="Monthly payroll batch runner, reconciliation & statutory reporting" />

      <Card className="mb-6">
        <h2 className="mb-3 text-sm font-semibold text-slate-700">New Payroll Batch</h2>
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
              type="number"
              value={form.cycleYear}
              onChange={(e) => setForm({ ...form, cycleYear: Number(e.target.value) })}
              className="w-28 rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Cycle Month</label>
            <input
              type="number"
              min={1}
              max={12}
              value={form.cycleMonth}
              onChange={(e) => setForm({ ...form, cycleMonth: Number(e.target.value) })}
              className="w-20 rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Batch Type</label>
            <select
              value={form.batchType}
              onChange={(e) => setForm({ ...form, batchType: e.target.value as PayrollBatchType })}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="REGULAR">Regular</option>
              <option value="SUPPLEMENTARY">Supplementary</option>
            </select>
          </div>
          <PrimaryButton type="submit" disabled={createMutation.isPending}>
            <Plus size={15} /> Create Batch
          </PrimaryButton>
        </form>
        {createMutation.isError && <ErrorState message={describeApiError(createMutation.error, 'Could not create the batch (one may already exist for that cycle).')} />}
      </Card>

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load payroll batches." />}
      {data && data.content.length === 0 && <EmptyState message="No payroll batches yet." />}

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="space-y-3">
          {data?.content.map((batch) => (
            <PayrollBatchCard
              key={batch.id}
              batch={batch}
              selected={selectedBatchId === batch.id}
              onSelect={() => {
                setSelectedBatchId(batch.id)
                setExpandedRecord(null)
                setTab('reconciliation')
              }}
              onCompute={() => computeMutation.mutate(batch.id)}
              onFinalize={() => finalizeMutation.mutate(batch.id)}
              onDisburse={() => disburseMutation.mutate(batch.id)}
              onOpenReports={() => {
                setSelectedBatchId(batch.id)
                setTab('reports')
              }}
              computing={computeMutation.isPending && computeMutation.variables === batch.id}
              finalizing={finalizeMutation.isPending && finalizeMutation.variables === batch.id}
              disbursing={disburseMutation.isPending && disburseMutation.variables === batch.id}
            />
          ))}
        </div>

        <Card>
          {!selectedBatchId && <h2 className="text-sm font-semibold text-slate-700">Select a batch to reconcile</h2>}
          {selectedBatchId && (
            <>
              <div className="mb-3 flex gap-2 border-b border-slate-100 pb-2 text-sm">
                <button
                  className={`rounded-md px-3 py-1.5 font-medium ${tab === 'reconciliation' ? 'bg-brand-forest text-white' : 'text-slate-500'}`}
                  onClick={() => setTab('reconciliation')}
                >
                  Reconciliation
                </button>
                <button
                  className={`rounded-md px-3 py-1.5 font-medium ${tab === 'reports' ? 'bg-brand-forest text-white' : 'text-slate-500'}`}
                  onClick={() => setTab('reports')}
                >
                  Reports & Schedules
                </button>
              </div>

              {tab === 'reconciliation' && records && (
                <>
                  <dl className="mb-3 grid grid-cols-3 gap-3 text-sm">
                    <div>
                      <dt className="text-slate-400">Employees</dt>
                      <dd className="font-semibold text-slate-700">{records.content.length}</dd>
                    </div>
                    <div>
                      <dt className="text-slate-400">Held</dt>
                      <dd className="font-semibold text-slate-700">{records.content.filter((r) => r.salaryHeld).length}</dd>
                    </div>
                    <div>
                      <dt className="text-slate-400">Total Net Pay</dt>
                      <dd className="font-semibold text-brand-forest">
                        ₹{records.content.reduce((sum, r) => sum + r.netAmount, 0).toFixed(2)}
                      </dd>
                    </div>
                  </dl>
                  <div className="max-h-72 overflow-y-auto rounded-lg border border-slate-100">
                    <table className="w-full text-xs">
                      <thead className="sticky top-0 bg-slate-50 text-slate-500">
                        <tr>
                          <th className="px-2 py-1.5 text-left">Employee</th>
                          <th className="px-2 py-1.5 text-right">Basic</th>
                          <th className="px-2 py-1.5 text-right">Gross</th>
                          <th className="px-2 py-1.5 text-right">Deductions</th>
                          <th className="px-2 py-1.5 text-right">Net</th>
                          <th className="px-2 py-1.5 text-right">Held</th>
                          <th className="px-2 py-1.5"></th>
                        </tr>
                      </thead>
                      <tbody>
                        {records.content.map((r) => (
                          <tr
                            key={r.tranId}
                            className={`cursor-pointer border-t border-slate-100 hover:bg-slate-50 ${expandedRecord?.tranId === r.tranId ? 'bg-slate-50' : ''}`}
                            onClick={() => setExpandedRecord(expandedRecord?.tranId === r.tranId ? null : r)}
                          >
                            <td className="px-2 py-1">{r.employeeName} ({r.empCode})</td>
                            <td className="px-2 py-1 text-right tabular-nums">{r.basicPay.toFixed(2)}</td>
                            <td className="px-2 py-1 text-right tabular-nums">{r.grossAmount.toFixed(2)}</td>
                            <td className="px-2 py-1 text-right tabular-nums">{r.totalDeductions.toFixed(2)}</td>
                            <td className="px-2 py-1 text-right tabular-nums">{r.netAmount.toFixed(2)}</td>
                            <td className="px-2 py-1 text-right">{r.salaryHeld ? 'Yes' : ''}</td>
                            <td className="px-2 py-1 text-right">
                              {editable && (
                                <button
                                  className="text-brand-forest underline"
                                  onClick={(e) => {
                                    e.stopPropagation()
                                    setEditingRecord(r)
                                  }}
                                >
                                  Edit
                                </button>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>

                  {expandedRecord && (
                    <div className="mt-3 rounded-lg border border-slate-100 p-3">
                      <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
                        Full Head Matrix - {expandedRecord.employeeName}
                      </p>
                      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                        {(['EARNING', 'DEDUCTION', 'STATUTORY'] as const).map((category) => (
                          <div key={category}>
                            <p className="mb-1 text-[11px] font-semibold text-slate-500">{category}</p>
                            <table className="w-full text-[11px]">
                              <tbody>
                                {expandedRecord.fullHeadLines
                                  .filter((h) => h.category === category)
                                  .map((h) => (
                                    <tr key={h.headCount} className="border-t border-slate-100">
                                      <td className="py-0.5 pr-1">{h.shortName || h.description}</td>
                                      <td className="py-0.5 text-right tabular-nums">{h.amount.toFixed(2)}</td>
                                    </tr>
                                  ))}
                              </tbody>
                            </table>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </>
              )}

              {tab === 'reports' && <PayrollReportsPanel batchId={selectedBatchId} />}
            </>
          )}
        </Card>
      </div>

      {editingRecord && selectedBatchId && (
        <EditEmployeeSalaryModal batchId={selectedBatchId} record={editingRecord} onClose={() => setEditingRecord(null)} />
      )}
    </div>
  )
}
