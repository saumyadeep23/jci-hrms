import { useState } from 'react'
import { FileSpreadsheet, FileText, Printer } from 'lucide-react'
import { apiClient } from '../../api/client'
import { SecondaryButton } from '../common/ui'
import type { PimsReportFilter, ReportExportFormat } from '../../types/api'

/** PIMS_SPEC.md reports task, Section 4: one-click Excel/PDF/Print export buttons, shared by every reporting-hub tab. */
export function ReportExportButtons({ reportType, filter }: { reportType: string; filter: PimsReportFilter }) {
  const [pending, setPending] = useState<ReportExportFormat | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function doExport(format: ReportExportFormat) {
    setPending(format)
    setError(null)
    try {
      const response = await apiClient.post(
        '/v1/reports/pims/export',
        { reportType, format, filter },
        { responseType: 'blob' },
      )
      const blob = new Blob([response.data])
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${reportType.toLowerCase()}-report.${format === 'PDF' ? 'pdf' : 'xlsx'}`
      link.click()
      URL.revokeObjectURL(url)
    } catch {
      setError('Export failed. Please try again.')
    } finally {
      setPending(null)
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <SecondaryButton onClick={() => doExport('XLSX')} disabled={pending !== null}>
        <FileSpreadsheet size={14} /> {pending === 'XLSX' ? 'Exporting...' : 'Excel'}
      </SecondaryButton>
      <SecondaryButton onClick={() => doExport('PDF')} disabled={pending !== null}>
        <FileText size={14} /> {pending === 'PDF' ? 'Exporting...' : 'PDF'}
      </SecondaryButton>
      <SecondaryButton onClick={() => window.print()}>
        <Printer size={14} /> Print
      </SecondaryButton>
      {error && <span className="text-xs text-red-600">{error}</span>}
    </div>
  )
}
