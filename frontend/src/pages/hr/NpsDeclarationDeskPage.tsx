import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Search } from 'lucide-react'
import { apiClient } from '../../api/client'
import { useDebouncedValue } from '../../lib/useDebouncedValue'
import { useToast } from '../../components/common/ToastProvider'
import { describeApiErrorList, getFieldErrors } from '../../lib/apiError'
import { FieldError, Badge, Card, ErrorState, LoadingState, PageHeader, PrimaryButton, errorInputClass } from '../../components/common/ui'
import { FormErrorBanner } from '../../components/common/FormErrorBanner'
import type { EmployeeResponse, NpsDeclarationRequest, NpsDeclarationResponse, NpsPreviewResponse, Page } from '../../types/api'

const MIN_PERCENTAGE = 3
const MAX_PERCENTAGE = 10

function rupees(amount: number): string {
  return `₹${amount.toLocaleString('en-IN')}`
}

/** round((Basic + DA) * pct / 100) - mirrors NpsSubmittedRow's own server-side calculation, computed live client-side as the percentage input changes. */
function estimatedMonthlyDeduction(basicPay: number, dearnessAllowance: number, percentage: number): number {
  return Math.round(((basicPay + dearnessAllowance) * percentage) / 100)
}

/** Employee NPS Declaration Desk - autocomplete employee search, a live Basic+DA-based deduction preview, and a lock warning if the employee has already declared for the current FY (NpsDeclarationServiceImpl enforces the once-per-FY rule server-side; this banner is a heads-up, not the enforcement itself). */
export function NpsDeclarationDeskPage() {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [searchInput, setSearchInput] = useState('')
  const search = useDebouncedValue(searchInput, 300)
  const [selectedEmployee, setSelectedEmployee] = useState<EmployeeResponse | null>(null)
  const [npsPercentage, setNpsPercentage] = useState('')
  const [remarks, setRemarks] = useState('')

  const employeeSearchQuery = useQuery({
    queryKey: ['nps-desk-employee-search', search],
    queryFn: async () => (await apiClient.get<Page<EmployeeResponse>>('/employees', { params: { search, size: 10 } })).data,
    enabled: search.trim().length >= 2 && !selectedEmployee,
  })

  const previewQuery = useQuery({
    queryKey: ['nps-preview', selectedEmployee?.id],
    queryFn: async () =>
      (await apiClient.get<NpsPreviewResponse>(`/v1/payroll/declarations/nps/employee/${selectedEmployee!.id}/preview`)).data,
    enabled: selectedEmployee !== null,
  })

  const declareMutation = useMutation({
    mutationFn: async () => {
      const payload: NpsDeclarationRequest = {
        employeeId: selectedEmployee!.id,
        npsPercentage: Number(npsPercentage),
        remarks: remarks || null,
      }
      return (await apiClient.post<NpsDeclarationResponse>('/v1/payroll/declarations/nps', payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['nps-preview', selectedEmployee?.id] })
      show({ tone: 'success', message: `NPS declaration recorded for ${selectedEmployee?.fullName}.` })
      setNpsPercentage('')
      setRemarks('')
    },
  })
  const fieldErrors = getFieldErrors(declareMutation.error)

  function pickEmployee(employee: EmployeeResponse) {
    setSelectedEmployee(employee)
    setSearchInput(employee.fullName)
  }

  function clearEmployee() {
    setSelectedEmployee(null)
    setSearchInput('')
    setNpsPercentage('')
    setRemarks('')
  }

  const percentageValue = Number(npsPercentage)
  const formValid = npsPercentage !== '' && percentageValue >= MIN_PERCENTAGE && percentageValue <= MAX_PERCENTAGE
  const preview = previewQuery.data
  const liveDeduction =
    preview && formValid ? estimatedMonthlyDeduction(preview.basicPay, preview.dearnessAllowance, percentageValue) : null

  return (
    <div>
      <PageHeader title="Employee NPS Declaration Desk" description="Record an employee's NPS deduction percentage for the current financial year" />

      <Card className="mb-6">
        <label className="mb-1 block text-xs font-medium text-slate-600">Employee</label>
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
          <input
            value={searchInput}
            onChange={(e) => {
              setSearchInput(e.target.value)
              setSelectedEmployee(null)
            }}
            placeholder="Search by name or employee code..."
            className="w-full rounded-md border border-slate-300 py-2 pl-9 pr-3 text-sm"
          />
        </div>

        {!selectedEmployee && search.trim().length >= 2 && (
          <div className="mt-2 max-h-64 overflow-y-auto rounded-md border border-slate-200">
            {employeeSearchQuery.isLoading && <LoadingState label="Searching employees..." />}
            {employeeSearchQuery.data?.content.length === 0 && <p className="p-3 text-sm text-slate-500">No employees found.</p>}
            {employeeSearchQuery.data?.content.map((employee) => (
              <button
                key={employee.id}
                type="button"
                onClick={() => pickEmployee(employee)}
                className="flex w-full items-center justify-between border-b border-slate-100 px-3 py-2 text-left text-sm last:border-b-0 hover:bg-slate-50"
              >
                <span>{employee.fullName}</span>
                <span className="text-xs text-slate-400">{employee.employeeCode}</span>
              </button>
            ))}
          </div>
        )}

        {selectedEmployee && (
          <div className="mt-3 flex items-center justify-between rounded-md bg-slate-50 px-3 py-2 text-sm">
            <span>
              <span className="font-medium">{selectedEmployee.fullName}</span>{' '}
              <span className="text-slate-400">({selectedEmployee.employeeCode})</span>
            </span>
            <button type="button" onClick={clearEmployee} className="text-xs text-brand-forest hover:underline">
              Change
            </button>
          </div>
        )}
      </Card>

      {selectedEmployee && (
        <Card className="mb-6">
          {previewQuery.isLoading && <LoadingState label="Loading pay details..." />}
          {previewQuery.isError && <ErrorState message="Could not load this employee's pay details." />}

          {preview && (
            <>
              <div className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded-md bg-brand-forest/10 px-3 py-2">
                <span className="text-sm font-medium text-brand-forest-dark">Financial Year</span>
                <Badge tone="brand">FY {preview.currentFinancialYear}</Badge>
              </div>

              <div className="mb-4 grid grid-cols-2 gap-3 text-sm">
                <div className="rounded-md border border-slate-200 px-3 py-2">
                  <p className="text-xs text-slate-500">Active Basic Pay</p>
                  <p className="font-medium tabular-nums">{rupees(preview.basicPay)}</p>
                </div>
                <div className="rounded-md border border-slate-200 px-3 py-2">
                  <p className="text-xs text-slate-500">Active Dearness Allowance</p>
                  <p className="font-medium tabular-nums">{rupees(preview.dearnessAllowance)}</p>
                </div>
              </div>

              {preview.alreadyDeclaredForCurrentFy && preview.currentFyDeclaration && (
                <div className="mb-4 flex items-start gap-2 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                  <AlertTriangle size={16} className="mt-0.5 shrink-0" />
                  <span>
                    Already declared <strong>{preview.currentFyDeclaration.npsPercentage}%</strong> for FY{' '}
                    {preview.currentFinancialYear} on {new Date(preview.currentFyDeclaration.createdAt).toLocaleDateString('en-IN')}
                    . The NPS deduction percentage can only be changed once per financial year, so this employee cannot submit
                    another declaration until the next FY.
                  </span>
                </div>
              )}

              {!preview.alreadyDeclaredForCurrentFy && (
                <form
                  className="space-y-3"
                  onSubmit={(e) => {
                    e.preventDefault()
                    declareMutation.mutate()
                  }}
                >
                  {declareMutation.isError && (
                    <FormErrorBanner
                      title="Could not record declaration"
                      errors={describeApiErrorList(declareMutation.error, 'Could not record declaration.')}
                    />
                  )}

                  <div>
                    <label className="mb-1 block text-xs font-medium text-slate-600">
                      NPS Deduction Percentage ({MIN_PERCENTAGE.toFixed(2)}% - {MAX_PERCENTAGE.toFixed(2)}%)
                    </label>
                    <input
                      required
                      type="number"
                      min={MIN_PERCENTAGE}
                      max={MAX_PERCENTAGE}
                      step="0.01"
                      value={npsPercentage}
                      onChange={(e) => setNpsPercentage(e.target.value)}
                      className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.npsPercentage))}`}
                    />
                    <FieldError message={fieldErrors?.npsPercentage} />
                  </div>

                  {liveDeduction !== null && (
                    <div className="rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
                      Estimated Monthly Deduction: <strong>{rupees(liveDeduction)}</strong>
                    </div>
                  )}

                  <div>
                    <label className="mb-1 block text-xs font-medium text-slate-600">Remarks (optional)</label>
                    <textarea
                      value={remarks}
                      onChange={(e) => setRemarks(e.target.value)}
                      rows={3}
                      className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                    />
                  </div>

                  <PrimaryButton type="submit" disabled={declareMutation.isPending || !formValid} className="w-full justify-center">
                    Record Declaration
                  </PrimaryButton>
                </form>
              )}
            </>
          )}
        </Card>
      )}

      {preview && preview.history.length > 0 && (
        <Card>
          <h3 className="mb-3 text-sm font-semibold text-slate-700">Declaration History</h3>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Financial Year</th>
                  <th className="py-2 pr-3 text-right">Declared %</th>
                  <th className="py-2 pr-3 text-right">Monthly Deduction (est.)</th>
                  <th className="py-2 pr-3">Submission Date</th>
                  <th className="py-2 pr-3">Remarks</th>
                </tr>
              </thead>
              <tbody>
                {preview.history.map((row) => (
                  <tr key={row.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3 font-medium">{row.financialYear}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{row.npsPercentage}%</td>
                    <td className="py-2 pr-3 text-right tabular-nums">
                      {rupees(estimatedMonthlyDeduction(preview.basicPay, preview.dearnessAllowance, row.npsPercentage))}
                    </td>
                    <td className="py-2 pr-3">{new Date(row.createdAt).toLocaleDateString('en-IN')}</td>
                    <td className="py-2 pr-3 text-slate-500">{row.remarks ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  )
}
