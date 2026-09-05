import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Printer } from 'lucide-react'
import { apiClient } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { formatDate } from '../../lib/date'
import { Card, ErrorState, LoadingState, PageHeader, PrimaryButton } from '../../components/common/ui'
import { PrintableHeader } from '../../components/common/PrintableHeader'
import type { PayslipSummaryResponse } from '../../types/api'

/**
 * There is no self-service "list my payroll runs" endpoint (PayrollRunController.list
 * is FINANCE_ADMIN/SUPER_ADMIN only), so an employee has no way to discover valid
 * run IDs. Pending that, the run is picked by ID directly rather than by
 * year/month - ask HR/Finance for the run ID for the month you need.
 */
export function PayslipViewerPage() {
  const { employeeId } = useAuth()
  const [runId, setRunId] = useState('')
  const [activeRunId, setActiveRunId] = useState<number | null>(null)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['payslip-summary', activeRunId, employeeId],
    queryFn: async () => {
      const response = await apiClient.get<PayslipSummaryResponse>(`/reports/payroll/${activeRunId}/payslip/${employeeId}`)
      return response.data
    },
    enabled: activeRunId !== null && employeeId !== null,
  })

  return (
    <div>
      <PageHeader
        title="Payslip Viewer"
        description="Head-wise earnings & deductions for a payroll cycle"
        actions={
          data && (
            <PrimaryButton onClick={() => window.print()}>
              <Printer size={15} /> Print / Save as PDF
            </PrimaryButton>
          )
        }
      />

      <Card className="mb-6 no-print">
        <form
          className="flex flex-wrap items-end gap-3"
          onSubmit={(e) => {
            e.preventDefault()
            const parsed = Number(runId)
            if (!Number.isNaN(parsed) && parsed > 0) setActiveRunId(parsed)
          }}
        >
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Payroll Run ID</label>
            <input
              value={runId}
              onChange={(e) => setRunId(e.target.value)}
              className="w-40 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-forest focus:outline-none"
              placeholder="e.g. 12"
              inputMode="numeric"
            />
          </div>
          <PrimaryButton type="submit">View payslip</PrimaryButton>
        </form>
      </Card>

      {isLoading && <LoadingState label="Fetching payslip..." />}
      {isError && <ErrorState message={error instanceof Error ? error.message : 'Could not load payslip.'} />}

      {data && (
        <Card className="print-sheet mx-auto max-w-2xl">
          <PrintableHeader
            documentTitle="Payslip"
            subtitle={`${monthName(data.cycleMonth)} ${data.cycleYear} (${formatDate(data.startDate)} to ${formatDate(data.endDate)})`}
          />

          <div className="mb-4 grid grid-cols-2 gap-2 text-sm">
            <div>
              <span className="text-slate-500">Employee:</span> <span className="font-medium">{data.employeeName}</span>
            </div>
            <div className="text-right">
              <span className="text-slate-500">Code:</span> <span className="font-medium">{data.employeeCode}</span>
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
              {data.lineItems.map((item) => (
                <tr key={item.id} className="border-b border-slate-100">
                  <td className="py-1.5">{item.salaryHeadName}</td>
                  <td className="py-1.5 text-right tabular-nums">{item.amount.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="border-t-2 border-slate-300 font-semibold">
                <td className="py-2">Gross Earnings</td>
                <td className="py-2 text-right tabular-nums">{data.totalEarnings.toFixed(2)}</td>
              </tr>
              <tr>
                <td className="py-1">Total Deductions</td>
                <td className="py-1 text-right tabular-nums">{data.totalDeductions.toFixed(2)}</td>
              </tr>
              <tr className="text-base font-bold text-brand-forest">
                <td className="py-2">Net Pay</td>
                <td className="py-2 text-right tabular-nums">₹{data.netPay.toFixed(2)}</td>
              </tr>
            </tfoot>
          </table>

          <div className="mt-6 grid grid-cols-2 gap-4 border-t border-slate-200 pt-4 text-xs sm:grid-cols-4">
            <Stat label="LOP Days" value={data.attendance.lopDays.toFixed(1)} />
            <Stat label="Present Days" value={String(data.attendance.presentDays)} />
            <Stat label="Employee EPF" value={`₹${data.pfBucketSplit.employeeEpf.toFixed(2)}`} />
            <Stat label="Employer EPF+EPS" value={`₹${(data.pfBucketSplit.employerEpf + data.pfBucketSplit.employerEps).toFixed(2)}`} />
          </div>
        </Card>
      )}
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-slate-400">{label}</p>
      <p className="font-semibold text-slate-700">{value}</p>
    </div>
  )
}

function monthName(month: number): string {
  return new Date(2000, month - 1, 1).toLocaleString('en-IN', { month: 'long' })
}
