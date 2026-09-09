import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Download } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { Card, EmptyState, ErrorState, LoadingState, PageHeader, SecondaryButton } from '../../../components/common/ui'
import type { NpsAdminSummaryResponse } from '../../../types/api'

function currentFinancialYear(): string {
  const today = new Date()
  const month = today.getMonth() + 1
  const startYear = month >= 4 ? today.getFullYear() : today.getFullYear() - 1
  return `${startYear}-${startYear + 1}`
}

function shiftFinancialYear(fy: string, delta: number): string {
  const startYear = Number(fy.split('-')[0]) + delta
  return `${startYear}-${startYear + 1}`
}

function rupees(amount: number | null): string {
  return amount != null ? `₹${amount.toLocaleString('en-IN')}` : '—'
}

function downloadCsv(filename: string, rows: string[][]) {
  const csv = rows.map((row) => row.map((cell) => `"${cell.replace(/"/g, '""')}"`).join(',')).join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

type Tab = 'submitted' | 'pending'

/** HR Admin / Bill Section NPS Compliance Dashboard - submitted vs. pending declarations for a selected financial year. */
export function NpsComplianceDashboardPage() {
  const [fy, setFy] = useState(currentFinancialYear())
  const [tab, setTab] = useState<Tab>('submitted')
  const [pendingSearch, setPendingSearch] = useState('')

  const { data, isLoading, isError } = useQuery({
    queryKey: ['nps-admin-summary', fy],
    queryFn: async () => (await apiClient.get<NpsAdminSummaryResponse>('/v1/payroll/declarations/nps/admin/summary', { params: { fy } })).data,
  })

  const filteredPending = useMemo(() => {
    const rows = data?.pending ?? []
    const q = pendingSearch.trim().toLowerCase()
    if (!q) return rows
    return rows.filter((r) => r.employeeName.toLowerCase().includes(q) || r.employeeCode.toLowerCase().includes(q))
  }, [data, pendingSearch])

  function exportPendingCsv() {
    const rows: string[][] = [
      ['Employee Code', 'Name', 'Office/DPC'],
      ...filteredPending.map((r) => [r.employeeCode, r.employeeName, r.officeOrDpc ?? '']),
    ]
    downloadCsv(`nps-pending-${fy}.csv`, rows)
  }

  return (
    <div>
      <PageHeader
        title="NPS Compliance Dashboard"
        description="Submitted vs. pending NPS declarations for HR / Bill Section review"
        actions={
          <div className="flex items-center gap-1 rounded-md border border-slate-300 px-2 py-1">
            <button type="button" onClick={() => setFy((f) => shiftFinancialYear(f, -1))} className="p-1 text-slate-500 hover:text-slate-800">
              <ChevronLeft size={16} />
            </button>
            <span className="px-1 text-sm font-medium">FY {fy}</span>
            <button type="button" onClick={() => setFy((f) => shiftFinancialYear(f, 1))} className="p-1 text-slate-500 hover:text-slate-800">
              <ChevronRight size={16} />
            </button>
          </div>
        }
      />

      <div className="mb-4 flex gap-2 border-b border-slate-200 pb-3">
        <button
          type="button"
          onClick={() => setTab('submitted')}
          className={`rounded-md px-3 py-1.5 text-sm font-medium ${tab === 'submitted' ? 'bg-brand-forest text-white' : 'text-slate-600 hover:bg-slate-100'}`}
        >
          Submitted Declarations {data ? `(${data.submitted.length})` : ''}
        </button>
        <button
          type="button"
          onClick={() => setTab('pending')}
          className={`rounded-md px-3 py-1.5 text-sm font-medium ${tab === 'pending' ? 'bg-brand-forest text-white' : 'text-slate-600 hover:bg-slate-100'}`}
        >
          Pending / Not Submitted {data ? `(${data.pending.length})` : ''}
        </button>
      </div>

      <Card>
        {isLoading && <LoadingState label="Loading NPS declaration summary..." />}
        {isError && <ErrorState message="Could not load the NPS declaration summary." />}

        {data && tab === 'submitted' && data.submitted.length === 0 && <EmptyState message={`No declarations submitted for FY ${fy}.`} />}
        {data && tab === 'submitted' && data.submitted.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Emp Code</th>
                  <th className="py-2 pr-3">Name</th>
                  <th className="py-2 pr-3">Office/DPC</th>
                  <th className="py-2 pr-3 text-right">Declared %</th>
                  <th className="py-2 pr-3 text-right">Monthly Deduction (₹)</th>
                  <th className="py-2 pr-3">Submission Date</th>
                  <th className="py-2 pr-3">Remarks</th>
                </tr>
              </thead>
              <tbody>
                {data.submitted.map((row) => (
                  <tr key={row.employeeId} className="border-b border-slate-100">
                    <td className="py-2 pr-3 font-medium">{row.employeeCode}</td>
                    <td className="py-2 pr-3">{row.employeeName}</td>
                    <td className="py-2 pr-3 text-slate-500">{row.officeOrDpc ?? '—'}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{row.declaredPercentage}%</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{rupees(row.monthlyDeduction)}</td>
                    <td className="py-2 pr-3">{new Date(row.submissionDate).toLocaleDateString('en-IN')}</td>
                    <td className="py-2 pr-3 text-slate-500">{row.remarks ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {data && tab === 'pending' && (
          <>
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <input
                value={pendingSearch}
                onChange={(e) => setPendingSearch(e.target.value)}
                placeholder="Search by name or employee code..."
                className="w-full max-w-xs rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
              <SecondaryButton onClick={exportPendingCsv} disabled={filteredPending.length === 0}>
                <Download size={14} /> Export CSV
              </SecondaryButton>
            </div>

            {filteredPending.length === 0 && <EmptyState message="No pending employees match." />}
            {filteredPending.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[560px] border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-slate-300 text-left text-slate-500">
                      <th className="py-2 pr-3">Emp Code</th>
                      <th className="py-2 pr-3">Name</th>
                      <th className="py-2 pr-3">Office/DPC</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredPending.map((row) => (
                      <tr key={row.employeeId} className="border-b border-slate-100">
                        <td className="py-2 pr-3 font-medium">{row.employeeCode}</td>
                        <td className="py-2 pr-3">{row.employeeName}</td>
                        <td className="py-2 pr-3 text-slate-500">{row.officeOrDpc ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </Card>
    </div>
  )
}
