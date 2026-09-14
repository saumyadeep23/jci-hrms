import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Download } from 'lucide-react'
import { apiClient } from '../../api/client'
import { EmptyState, ErrorState, LoadingState, SecondaryButton } from '../../components/common/ui'
import { describeApiError } from '../../lib/apiError'
import type { PayrollReportType, TabularReportResponse } from '../../types/api'

const REPORT_TYPES: { value: PayrollReportType; label: string }[] = [
  { value: 'SUMMARY_SHEET', label: 'Summary Sheet' },
  { value: 'CPF_SCHEDULE', label: 'CPF Schedule' },
  { value: 'INCOME_TAX_SCHEDULE', label: 'Income Tax Schedule' },
  { value: 'NPS_SCHEDULE', label: 'NPS Schedule' },
  { value: 'PTAX_SCHEDULE', label: 'P.Tax Schedule' },
  { value: 'COOPERATIVE_SCHEDULE', label: 'Co-operative Schedule' },
  { value: 'RECREATION_CLUB_SCHEDULE', label: 'Recreation Club Schedule' },
]

/** Bill Section statutory schedule reports for one batch - GET /v1/payroll/batches/{id}/reports/{reportType}, JSON preview + CSV export. */
export function PayrollReportsPanel({ batchId }: { batchId: number }) {
  const [reportType, setReportType] = useState<PayrollReportType>('SUMMARY_SHEET')

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['payroll-batch-report', batchId, reportType],
    queryFn: async () =>
      (
        await apiClient.get<TabularReportResponse>(`/v1/payroll/batches/${batchId}/reports/${reportType}`, {
          params: { format: 'JSON' },
        })
      ).data,
  })

  async function downloadCsv() {
    const response = await apiClient.get(`/v1/payroll/batches/${batchId}/reports/${reportType}`, {
      params: { format: 'CSV' },
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(response.data as Blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${reportType.toLowerCase()}-batch-${batchId}.csv`
    link.click()
    window.URL.revokeObjectURL(url)
  }

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-end justify-between gap-2">
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Report</label>
          <select
            value={reportType}
            onChange={(e) => setReportType(e.target.value as PayrollReportType)}
            className="w-64 rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            {REPORT_TYPES.map((r) => (
              <option key={r.value} value={r.value}>{r.label}</option>
            ))}
          </select>
        </div>
        <SecondaryButton onClick={downloadCsv}>
          <Download size={14} /> Download CSV
        </SecondaryButton>
      </div>

      {isLoading && <LoadingState label="Loading report..." />}
      {isError && <ErrorState message={describeApiError(error, 'Could not load the report.')} />}
      {data && data.rows.length === 0 && <EmptyState message="No rows for this batch/report." />}

      {data && data.rows.length > 0 && (
        <div className="max-h-96 overflow-auto rounded-lg border border-slate-100">
          <table className="w-full text-xs">
            <thead className="sticky top-0 bg-slate-50 text-slate-500">
              <tr>
                {data.columns.map((col) => (
                  <th key={col} className="px-2 py-1.5 text-left">{col}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.rows.map((row, idx) => (
                <tr key={idx} className="border-t border-slate-100">
                  {data.columns.map((col) => (
                    <td key={col} className="px-2 py-1">{formatCell(row[col])}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function formatCell(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'number') return value.toFixed(2)
  return String(value)
}
