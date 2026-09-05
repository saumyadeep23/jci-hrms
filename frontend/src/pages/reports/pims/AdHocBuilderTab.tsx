import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ArrowDown, ArrowUp, Columns3 } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { describeApiError } from '../../../lib/apiError'
import { Modal } from '../../../components/common/Modal'
import { Card, EmptyState, ErrorState, LoadingState, PrimaryButton, SecondaryButton } from '../../../components/common/ui'
import { DepartmentFilter } from '../../../components/reports/DepartmentFilter'
import { ReportExportButtons } from '../../../components/reports/ReportExportButtons'
import type { AdHocReportRequest, PimsReportFilter, TabularReportResponse } from '../../../types/api'

/** Mirrors AdHocReportService.ALLOWED_COLUMNS on the backend (vw_jci_employee_master_360) - keep in sync if that list changes. */
const AVAILABLE_COLUMNS: { key: string; label: string }[] = [
  { key: 'employee_code', label: 'Employee Code' },
  { key: 'full_name', label: 'Full Name' },
  { key: 'gender', label: 'Gender' },
  { key: 'date_of_birth', label: 'Date of Birth' },
  { key: 'age', label: 'Age' },
  { key: 'marital_status', label: 'Marital Status' },
  { key: 'employment_status', label: 'Employment Status' },
  { key: 'date_of_joining', label: 'Date of Joining' },
  { key: 'employment_category', label: 'Employment Category' },
  { key: 'compensation_tier_summary', label: 'Compensation Tier' },
  { key: 'department_name', label: 'Department' },
  { key: 'designation_title', label: 'Designation' },
  { key: 'scale_grade', label: 'Scale Grade' },
  { key: 'ro_name', label: 'Regional Office' },
  { key: 'dpc_name', label: 'DPC' },
  { key: 'current_post_title', label: 'Current Post' },
  { key: 'current_assignment_type', label: 'Assignment Type' },
  { key: 'personal_email', label: 'Personal Email' },
  { key: 'personal_mobile', label: 'Personal Mobile' },
  { key: 'social_category', label: 'Social Category' },
  { key: 'is_pwbd', label: 'PwBD' },
  { key: 'superannuation_date', label: 'Superannuation Date' },
  { key: 'is_board_director', label: 'Board Director' },
  { key: 'active_bank_name', label: 'Bank' },
  { key: 'bank_verification_status', label: 'Bank Verification Status' },
]
const DEFAULT_COLUMNS = ['employee_code', 'full_name', 'department_name', 'designation_title', 'employment_status', 'employment_category']
const PAGE_SIZE = 25

/** PIMS_SPEC.md reports task: Ad-Hoc Report Builder tab (dynamic column selection over vw_jci_employee_master_360). */
export function AdHocBuilderTab() {
  const [columns, setColumns] = useState<string[]>(DEFAULT_COLUMNS)
  const [columnsModalOpen, setColumnsModalOpen] = useState(false)
  const [departmentId, setDepartmentId] = useState('')
  const [employmentCategory, setEmploymentCategory] = useState('')
  const [page, setPage] = useState(0)

  const filter: PimsReportFilter = {
    departmentId: departmentId ? Number(departmentId) : null,
    employmentCategory: employmentCategory || null,
    columns,
  }

  const runMutation = useMutation({
    mutationFn: async () => {
      const request: AdHocReportRequest = { columns, filter, page, size: PAGE_SIZE }
      return (await apiClient.post<TabularReportResponse>('/v1/reports/pims/ad-hoc', request)).data
    },
  })

  function runReport(nextPage = page) {
    setPage(nextPage)
    runMutation.mutate()
  }

  function toggleColumn(key: string) {
    setColumns((prev) => (prev.includes(key) ? prev.filter((c) => c !== key) : [...prev, key]))
  }

  function moveColumn(index: number, direction: -1 | 1) {
    setColumns((prev) => {
      const next = [...prev]
      const target = index + direction
      if (target < 0 || target >= next.length) return prev
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
  }

  const data = runMutation.data
  const totalPages = data ? Math.max(1, Math.ceil(data.totalElements / PAGE_SIZE)) : 1

  return (
    <div>
      <Card className="mb-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap gap-2">
            <SecondaryButton onClick={() => setColumnsModalOpen(true)}>
              <Columns3 size={14} /> Columns ({columns.length})
            </SecondaryButton>
            <DepartmentFilter value={departmentId} onChange={setDepartmentId} />
            <select
              value={employmentCategory}
              onChange={(e) => setEmploymentCategory(e.target.value)}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="">All Categories</option>
              <option value="REGULAR">Regular</option>
              <option value="CASUAL">Casual</option>
              <option value="CONTRACTUAL">Contractual</option>
              <option value="OUTSOURCED">Outsourced</option>
            </select>
            <PrimaryButton onClick={() => runReport(0)} disabled={runMutation.isPending || columns.length === 0}>
              Run Report
            </PrimaryButton>
          </div>
          <ReportExportButtons reportType="AD_HOC" filter={filter} />
        </div>
      </Card>

      <Card>
        {runMutation.isPending && <LoadingState label="Running report..." />}
        {runMutation.isError && <ErrorState message={describeApiError(runMutation.error, 'Could not run the report.')} />}
        {!data && !runMutation.isPending && <EmptyState message="Select columns and click Run Report." />}
        {data && data.rows.length === 0 && <EmptyState message="No matching employees." />}
        {data && data.rows.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px] border-collapse text-sm">
                <thead>
                  <tr className="border-b border-slate-300 text-left text-slate-500">
                    {data.columns.map((col) => (
                      <th key={col} className="py-2 pr-3">
                        {AVAILABLE_COLUMNS.find((c) => c.key === col)?.label ?? col}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {data.rows.map((row, i) => (
                    <tr key={i} className="border-b border-slate-100">
                      {data.columns.map((col) => (
                        <td key={col} className="py-2 pr-3">
                          {row[col] != null ? String(row[col]) : '—'}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="mt-4 flex items-center justify-between text-xs text-slate-500">
              <span>
                Page {page + 1} of {totalPages} ({data.totalElements} total)
              </span>
              <div className="flex gap-2">
                <SecondaryButton onClick={() => runReport(Math.max(0, page - 1))} disabled={page === 0}>
                  Previous
                </SecondaryButton>
                <SecondaryButton onClick={() => runReport(page + 1)} disabled={page + 1 >= totalPages}>
                  Next
                </SecondaryButton>
              </div>
            </div>
          </>
        )}
      </Card>

      {columnsModalOpen && (
        <Modal title="Select Columns" onClose={() => setColumnsModalOpen(false)} maxWidthClassName="max-w-2xl">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Available</p>
              <div className="max-h-80 space-y-1 overflow-y-auto">
                {AVAILABLE_COLUMNS.filter((c) => !columns.includes(c.key)).map((c) => (
                  <label key={c.key} className="flex items-center gap-2 rounded-md px-2 py-1 text-sm hover:bg-slate-50">
                    <input type="checkbox" checked={false} onChange={() => toggleColumn(c.key)} />
                    {c.label}
                  </label>
                ))}
              </div>
            </div>
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Selected (reorderable)</p>
              <div className="max-h-80 space-y-1 overflow-y-auto">
                {columns.map((key, i) => (
                  <div key={key} className="flex items-center justify-between gap-2 rounded-md bg-slate-50 px-2 py-1 text-sm">
                    <span>{AVAILABLE_COLUMNS.find((c) => c.key === key)?.label ?? key}</span>
                    <div className="flex items-center gap-1">
                      <button type="button" onClick={() => moveColumn(i, -1)} className="text-slate-400 hover:text-slate-700">
                        <ArrowUp size={13} />
                      </button>
                      <button type="button" onClick={() => moveColumn(i, 1)} className="text-slate-400 hover:text-slate-700">
                        <ArrowDown size={13} />
                      </button>
                      <button type="button" onClick={() => toggleColumn(key)} className="ml-1 text-red-400 hover:text-red-600">
                        &times;
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
          <PrimaryButton onClick={() => setColumnsModalOpen(false)} className="mt-4 w-full justify-center">
            Done
          </PrimaryButton>
        </Modal>
      )}
    </div>
  )
}
