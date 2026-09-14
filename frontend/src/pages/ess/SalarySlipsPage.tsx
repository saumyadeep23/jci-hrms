import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Download, Printer } from 'lucide-react'
import { apiClient } from '../../api/client'
import { Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../components/common/ui'
import { PrintableHeader } from '../../components/common/PrintableHeader'
import { describeApiError } from '../../lib/apiError'
import type { EssSalarySlipDetailResponse, EssSalarySlipSummaryResponse } from '../../types/api'

function monthName(month: number): string {
  return new Date(2000, month - 1, 1).toLocaleString('en-IN', { month: 'long' })
}

export function SalarySlipsPage() {
  const [openTranId, setOpenTranId] = useState<number | null>(null)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['ess-salary-slips'],
    queryFn: async () => (await apiClient.get<EssSalarySlipSummaryResponse[]>('/v1/ess/salary-slips')).data,
  })

  const { data: detail } = useQuery({
    queryKey: ['ess-salary-slip-detail', openTranId],
    queryFn: async () => (await apiClient.get<EssSalarySlipDetailResponse>(`/v1/ess/salary-slips/${openTranId}`)).data,
    enabled: openTranId !== null,
  })

  async function download(tranId: number, year: number, month: number) {
    const response = await apiClient.get(`/v1/ess/salary-slips/${tranId}/download`, { responseType: 'blob' })
    const url = window.URL.createObjectURL(response.data as Blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `salary-slip-${year}-${String(month).padStart(2, '0')}.pdf`
    link.click()
    window.URL.revokeObjectURL(url)
  }

  return (
    <div>
      <PageHeader title="My Salary Slips" description="Monthly salary slip history (disbursed months only)" />

      {isLoading && <LoadingState />}
      {isError && <ErrorState message={describeApiError(error, 'Could not load your salary slips.')} />}
      {data && data.length === 0 && <EmptyState message="No disbursed salary slips yet." />}

      {data && data.length > 0 && (
        <Card>
          <table className="w-full text-sm">
            <thead className="text-left text-slate-500">
              <tr>
                <th className="pb-2">Month</th>
                <th className="pb-2 text-right">Gross</th>
                <th className="pb-2 text-right">Deductions</th>
                <th className="pb-2 text-right">Net Pay</th>
                <th className="pb-2"></th>
              </tr>
            </thead>
            <tbody>
              {data.map((slip) => (
                <tr key={slip.tranId} className="border-t border-slate-100">
                  <td className="py-2">{monthName(slip.month)} {slip.year}</td>
                  <td className="py-2 text-right tabular-nums">{slip.grossAmount.toFixed(2)}</td>
                  <td className="py-2 text-right tabular-nums">{slip.totalDeductions.toFixed(2)}</td>
                  <td className="py-2 text-right font-semibold tabular-nums text-brand-forest">{slip.netAmount.toFixed(2)}</td>
                  <td className="py-2 text-right">
                    <div className="flex justify-end gap-2">
                      <SecondaryButton onClick={() => setOpenTranId(slip.tranId)}>View</SecondaryButton>
                      <SecondaryButton onClick={() => download(slip.tranId, slip.year, slip.month)}>
                        <Download size={14} />
                      </SecondaryButton>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {openTranId && detail && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={() => setOpenTranId(null)}>
          <div
            className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-xl bg-white p-6 shadow-xl print-sheet"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-4 flex items-center justify-between no-print">
              <h2 className="text-lg font-semibold text-slate-800">Salary Slip</h2>
              <PrimaryButton onClick={() => window.print()}>
                <Printer size={15} /> Print / Save as PDF
              </PrimaryButton>
            </div>
            <PrintableHeader documentTitle="Salary Slip" subtitle={`${monthName(detail.month)} ${detail.year}`} />
            <div className="mb-4 grid grid-cols-2 gap-2 text-sm">
              <div>
                <span className="text-slate-500">Employee:</span> <span className="font-medium">{detail.employeeName}</span>
              </div>
              <div className="text-right">
                <span className="text-slate-500">Code:</span> <span className="font-medium">{detail.empCode}</span>
              </div>
            </div>
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-1.5">Head</th>
                  <th className="py-1.5 text-right">Amount (₹)</th>
                </tr>
              </thead>
              <tbody>
                {detail.headLines
                  .filter((h) => h.category !== 'STATUTORY')
                  .map((h) => (
                    <tr key={h.headCount} className="border-b border-slate-100">
                      <td className="py-1.5">{h.description}</td>
                      <td className="py-1.5 text-right tabular-nums">{h.amount.toFixed(2)}</td>
                    </tr>
                  ))}
              </tbody>
              <tfoot>
                <tr className="border-t-2 border-slate-300 font-semibold">
                  <td className="py-2">Gross Earnings</td>
                  <td className="py-2 text-right tabular-nums">{detail.grossAmount.toFixed(2)}</td>
                </tr>
                <tr>
                  <td className="py-1">Total Deductions</td>
                  <td className="py-1 text-right tabular-nums">{detail.totalDeductions.toFixed(2)}</td>
                </tr>
                <tr className="text-base font-bold text-brand-forest">
                  <td className="py-2">Net Pay</td>
                  <td className="py-2 text-right tabular-nums">₹{detail.netAmount.toFixed(2)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}
