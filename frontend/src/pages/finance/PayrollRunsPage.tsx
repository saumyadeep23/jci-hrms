import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, Plus } from 'lucide-react'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../components/common/ui'
import type { Page, PayrollRunResponse } from '../../types/api'

interface PayslipRow {
  id: number
  employeeCode: string
  netPay: number
  totalEarnings: number
  totalDeductions: number
  isHold: boolean
}

const STATUS_TONE: Record<string, 'neutral' | 'success' | 'brand'> = {
  DRAFT: 'neutral',
  COMPUTED: 'brand',
  FINALIZED: 'success',
}

export function PayrollRunsPage() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState({ cycleYear: new Date().getFullYear(), cycleMonth: new Date().getMonth() + 1 })
  const [selectedRunId, setSelectedRunId] = useState<number | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['payroll-runs'],
    queryFn: async () => (await apiClient.get<Page<PayrollRunResponse>>('/payroll/runs', { params: { size: 24 } })).data,
  })

  const { data: payslips } = useQuery({
    queryKey: ['payroll-run-payslips', selectedRunId],
    queryFn: async () => (await apiClient.get<PayslipRow[]>(`/payroll/runs/${selectedRunId}/payslips`)).data,
    enabled: selectedRunId !== null,
  })

  const createMutation = useMutation({
    mutationFn: async () => (await apiClient.post('/payroll/runs', form)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['payroll-runs'] }),
  })

  const computeMutation = useMutation({
    mutationFn: async (id: number) => (await apiClient.post(`/payroll/runs/${id}/compute`)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['payroll-runs'] }),
  })

  const finalizeMutation = useMutation({
    mutationFn: async (id: number) => (await apiClient.post(`/payroll/runs/${id}/finalize`, null, { params: { finalizedBy: 'finance.admin' } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['payroll-runs'] }),
  })

  async function downloadBankFile(id: number) {
    const response = await apiClient.get(`/reports/payroll/${id}/bank-file`, { responseType: 'blob' })
    const url = window.URL.createObjectURL(response.data as Blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `bank-disbursement-run-${id}.csv`
    link.click()
    window.URL.revokeObjectURL(url)
  }

  const totalNet = payslips?.reduce((sum, p) => sum + p.netPay, 0) ?? 0
  const heldCount = payslips?.filter((p) => p.isHold).length ?? 0

  return (
    <div>
      <PageHeader title="Payroll Runs" description="Monthly payroll runner, reconciliation & bank disbursement" />

      <Card className="mb-6">
        <h2 className="mb-3 text-sm font-semibold text-slate-700">New Payroll Run</h2>
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
          <PrimaryButton type="submit" disabled={createMutation.isPending}>
            <Plus size={15} /> Create Run
          </PrimaryButton>
        </form>
        {createMutation.isError && <ErrorState message="Could not create the run (a run may already exist for that cycle)." />}
      </Card>

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load payroll runs." />}
      {data && data.content.length === 0 && <EmptyState message="No payroll runs yet." />}

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="space-y-3">
          {data?.content.map((run) => (
            <Card
              key={run.id}
              className={`cursor-pointer transition-shadow hover:shadow-md ${selectedRunId === run.id ? 'ring-2 ring-brand-forest' : ''}`}
            >
              <div onClick={() => setSelectedRunId(run.id)}>
                <div className="flex items-center justify-between">
                  <p className="font-medium text-slate-800">
                    {run.cycleYear}-{String(run.cycleMonth).padStart(2, '0')} (Run #{run.id})
                  </p>
                  <Badge tone={STATUS_TONE[run.status] ?? 'neutral'}>{run.status}</Badge>
                </div>
                <p className="text-xs text-slate-400">
                  {formatDate(run.startDate)} to {formatDate(run.endDate)} · {run.runType}
                </p>
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                {run.status === 'DRAFT' && (
                  <SecondaryButton onClick={() => computeMutation.mutate(run.id)} disabled={computeMutation.isPending}>
                    Compute
                  </SecondaryButton>
                )}
                {run.status === 'COMPUTED' && (
                  <SecondaryButton onClick={() => finalizeMutation.mutate(run.id)} disabled={finalizeMutation.isPending}>
                    Finalize
                  </SecondaryButton>
                )}
                {run.status === 'FINALIZED' && (
                  <SecondaryButton onClick={() => downloadBankFile(run.id)}>
                    <Download size={14} /> Bank CSV
                  </SecondaryButton>
                )}
              </div>
            </Card>
          ))}
        </div>

        <Card>
          <h2 className="mb-3 text-sm font-semibold text-slate-700">
            {selectedRunId ? `Reconciliation - Run #${selectedRunId}` : 'Select a run to reconcile'}
          </h2>
          {selectedRunId && payslips && (
            <>
              <dl className="mb-3 grid grid-cols-3 gap-3 text-sm">
                <div>
                  <dt className="text-slate-400">Payslips</dt>
                  <dd className="font-semibold text-slate-700">{payslips.length}</dd>
                </div>
                <div>
                  <dt className="text-slate-400">Held</dt>
                  <dd className="font-semibold text-slate-700">{heldCount}</dd>
                </div>
                <div>
                  <dt className="text-slate-400">Total Net Pay</dt>
                  <dd className="font-semibold text-brand-forest">₹{totalNet.toFixed(2)}</dd>
                </div>
              </dl>
              <div className="max-h-72 overflow-y-auto rounded-lg border border-slate-100">
                <table className="w-full text-xs">
                  <thead className="sticky top-0 bg-slate-50 text-slate-500">
                    <tr>
                      <th className="px-2 py-1.5 text-left">Employee</th>
                      <th className="px-2 py-1.5 text-right">Net Pay</th>
                      <th className="px-2 py-1.5 text-right">Hold</th>
                    </tr>
                  </thead>
                  <tbody>
                    {payslips.map((p) => (
                      <tr key={p.id} className="border-t border-slate-100">
                        <td className="px-2 py-1">{p.employeeCode}</td>
                        <td className="px-2 py-1 text-right tabular-nums">{p.netPay.toFixed(2)}</td>
                        <td className="px-2 py-1 text-right">{p.isHold ? 'Yes' : ''}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </Card>
      </div>
    </div>
  )
}
