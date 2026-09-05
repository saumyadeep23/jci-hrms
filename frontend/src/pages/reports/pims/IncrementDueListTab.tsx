import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import { describeApiErrorList } from '../../../lib/apiError'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PrimaryButton } from '../../../components/common/ui'
import { FormErrorBanner } from '../../../components/common/FormErrorBanner'
import { KpiCard } from '../../../components/reports/KpiCard'
import { DepartmentFilter } from '../../../components/reports/DepartmentFilter'
import { IncrementMonthFilter } from '../../../components/reports/IncrementMonthFilter'
import { ReportExportButtons } from '../../../components/reports/ReportExportButtons'
import type { IncrementBatchProcessRequest, IncrementBatchProcessResponse, IncrementDueEntry, PimsReportFilter } from '../../../types/api'

/** PIMS_SPEC.md reports task, Tab 6 Batch Actions: Increment Due List with multi-select bulk approval. */
export function IncrementDueListTab() {
  const queryClient = useQueryClient()
  const [departmentId, setDepartmentId] = useState('')
  const [incrementMonth, setIncrementMonth] = useState(() => String(new Date().getMonth() + 1))
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [orderNumber, setOrderNumber] = useState('')
  const [orderDate, setOrderDate] = useState('')
  const [result, setResult] = useState<IncrementBatchProcessResponse | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['pims-increment-due-list', departmentId, incrementMonth],
    queryFn: async () =>
      (
        await apiClient.get<IncrementDueEntry[]>('/v1/increments/due-list', {
          params: { departmentId: departmentId || undefined, incrementMonth: incrementMonth || undefined },
        })
      ).data,
  })

  const eligible = (data ?? []).filter((e) => !e.isWithheld)

  const processMutation = useMutation({
    mutationFn: async () => {
      const payload: IncrementBatchProcessRequest = {
        employeeIds: Array.from(selected),
        orderNumber,
        orderDate,
        remarks: null,
      }
      return (await apiClient.post<IncrementBatchProcessResponse>('/v1/increments/process-batch', payload)).data
    },
    onSuccess: (response) => {
      setResult(response)
      setSelected(new Set())
      queryClient.invalidateQueries({ queryKey: ['pims-increment-due-list'] })
    },
  })

  function toggle(id: number) {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  function toggleAll() {
    setSelected((prev) => (prev.size === eligible.length ? new Set() : new Set(eligible.map((e) => e.employeeId))))
  }

  const filter: PimsReportFilter = {
    departmentId: departmentId ? Number(departmentId) : null,
    incrementMonth: incrementMonth ? Number(incrementMonth) : null,
  }
  const canProcess = selected.size > 0 && orderNumber.trim() !== '' && orderDate !== ''

  return (
    <div>
      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
        <KpiCard label="Due" value={data?.length} tone="brand" />
        <KpiCard label="Eligible" value={eligible.length} tone="success" />
        <KpiCard label="Withheld" value={(data ?? []).filter((e) => e.isWithheld).length} tone="warning" />
      </div>

      <Card className="mb-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-3">
            <DepartmentFilter value={departmentId} onChange={setDepartmentId} />
            <IncrementMonthFilter value={incrementMonth} onChange={setIncrementMonth} />
          </div>
          <ReportExportButtons reportType="INCREMENT_DUE_LIST" filter={filter} />
        </div>
      </Card>

      <Card className="mb-4">
        <div className="flex flex-wrap items-end gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Order Number</label>
            <input
              value={orderNumber}
              onChange={(e) => setOrderNumber(e.target.value)}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
              placeholder="e.g. HR/INC/2026/014"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Order Date</label>
            <input
              type="date"
              value={orderDate}
              onChange={(e) => setOrderDate(e.target.value)}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <PrimaryButton onClick={() => processMutation.mutate()} disabled={!canProcess || processMutation.isPending}>
            Process {selected.size > 0 ? `(${selected.size})` : ''} Selected
          </PrimaryButton>
        </div>
        {processMutation.isError && (
          <FormErrorBanner
            title="Could not process increments"
            errors={describeApiErrorList(processMutation.error, 'Could not process increments.')}
          />
        )}
        {result && (
          <div className="mt-3 rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-700">
            Processed {result.processedCount}, skipped {result.skippedCount}.
            {result.skippedReasons.length > 0 && (
              <ul className="mt-1 list-disc pl-5 text-xs text-emerald-800">
                {result.skippedReasons.map((r) => (
                  <li key={r}>{r}</li>
                ))}
              </ul>
            )}
          </div>
        )}
      </Card>

      <Card>
        {isLoading && <LoadingState label="Loading increment due list..." />}
        {isError && <ErrorState message="Could not load the increment due list." />}
        {data && data.length === 0 && <EmptyState message="No REGULAR-cadre employees due for increment." />}
        {data && data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[860px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">
                    <input type="checkbox" checked={selected.size === eligible.length && eligible.length > 0} onChange={toggleAll} />
                  </th>
                  <th className="py-2 pr-3">Employee Code</th>
                  <th className="py-2 pr-3">Name</th>
                  <th className="py-2 pr-3">Grade</th>
                  <th className="py-2 pr-3 text-right">Current Basic</th>
                  <th className="py-2 pr-3 text-right">Increment</th>
                  <th className="py-2 pr-3 text-right">New Basic</th>
                  <th className="py-2 pl-3">Status</th>
                </tr>
              </thead>
              <tbody>
                {data.map((e) => (
                  <tr key={e.employeeId} className="border-b border-slate-100">
                    <td className="py-2 pr-3">
                      <input
                        type="checkbox"
                        checked={selected.has(e.employeeId)}
                        disabled={e.isWithheld}
                        onChange={() => toggle(e.employeeId)}
                      />
                    </td>
                    <td className="py-2 pr-3 font-medium">{e.employeeCode}</td>
                    <td className="py-2 pr-3">{e.employeeName}</td>
                    <td className="py-2 pr-3 text-slate-500">{e.payScaleGrade ?? '—'}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{e.currentBasicPay.toLocaleString('en-IN')}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{e.incrementAmount.toLocaleString('en-IN')}</td>
                    <td className="py-2 pr-3 text-right tabular-nums font-medium">{e.newBasicPay.toLocaleString('en-IN')}</td>
                    <td className="py-2 pl-3">
                      {e.isWithheld ? (
                        <Badge tone="danger">Withheld</Badge>
                      ) : e.atStagnationCeiling ? (
                        <Badge tone="warning">At Ceiling</Badge>
                      ) : (
                        <Badge tone="success">Eligible</Badge>
                      )}
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
