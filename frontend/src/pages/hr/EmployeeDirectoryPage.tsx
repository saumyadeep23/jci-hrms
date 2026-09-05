import { useEffect, useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight, Plus, RefreshCw, Search, UserRound } from 'lucide-react'
import { apiClient } from '../../api/client'
import { Employee360Drawer } from '../../components/reports/Employee360Drawer'
import { RenewContractModal } from '../../components/employee/RenewContractModal'
import { Badge, Card, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../components/common/ui'
import type { DepartmentResponse, DesignationResponse, EmployeeResponse, EmploymentCategory, Page, RegionalOfficeResponse } from '../../types/api'

const EMPLOYMENT_TYPES: EmploymentCategory[] = ['REGULAR', 'CONTRACTUAL', 'OUTSOURCED', 'CASUAL']
const PAGE_SIZE_OPTIONS = [10, 15, 25, 50, 100]
const DEBOUNCE_MS = 300

/** '' means the backend's own default (ACTIVE) - see EmployeeSpecification.filterEmployees. SEPARATED/ALL are status-group keywords that Specification also understands. */
const STATUS_FILTERS: { value: string; label: string }[] = [
  { value: '', label: 'Active (Default)' },
  { value: 'RETIRED', label: 'Retired' },
  { value: 'RESIGNED', label: 'Resigned' },
  { value: 'DECEASED', label: 'Deceased' },
  { value: 'SEPARATED', label: 'All Separated' },
  { value: 'ALL', label: 'All Records' },
]

const STATUS_BADGE_TONE: Record<string, 'success' | 'neutral' | 'warning' | 'danger'> = {
  ACTIVE: 'success',
  RETIRED: 'neutral',
  RESIGNED: 'warning',
  DECEASED: 'danger',
}

export function EmployeeDirectoryPage() {
  const navigate = useNavigate()
  const [profileEmployeeId, setProfileEmployeeId] = useState<number | null>(null)
  const [renewingEmployeeId, setRenewingEmployeeId] = useState<number | null>(null)

  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [roId, setRoId] = useState('')
  const [departmentId, setDepartmentId] = useState('')
  const [designationId, setDesignationId] = useState('')
  const [employmentType, setEmploymentType] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(15)

  useEffect(() => {
    const handle = setTimeout(() => {
      setDebouncedSearch(search)
      setPage(0)
    }, DEBOUNCE_MS)
    return () => clearTimeout(handle)
  }, [search])

  function updateFilter(setter: (value: string) => void, value: string) {
    setter(value)
    setPage(0)
  }

  function resetFilters() {
    setSearch('')
    setDebouncedSearch('')
    setRoId('')
    setDepartmentId('')
    setDesignationId('')
    setEmploymentType('')
    setStatus('')
    setPage(0)
  }

  const officesQuery = useQuery({
    queryKey: ['regional-offices-all'],
    queryFn: async () => (await apiClient.get<Page<RegionalOfficeResponse>>('/regional-offices', { params: { size: 500 } })).data.content,
  })
  const departmentsQuery = useQuery({
    queryKey: ['departments-all'],
    queryFn: async () => (await apiClient.get<Page<DepartmentResponse>>('/departments', { params: { size: 200 } })).data.content,
  })
  const designationsQuery = useQuery({
    queryKey: ['designations-all'],
    queryFn: async () => (await apiClient.get<Page<DesignationResponse>>('/designations', { params: { size: 200 } })).data.content,
  })

  const { data, isLoading, isFetching, isError } = useQuery({
    queryKey: ['employees', debouncedSearch, roId, departmentId, designationId, employmentType, status, page, pageSize],
    queryFn: async () =>
      (
        await apiClient.get<Page<EmployeeResponse>>('/employees', {
          params: {
            search: debouncedSearch || undefined,
            roId: roId || undefined,
            departmentId: departmentId || undefined,
            designationId: designationId || undefined,
            employmentType: employmentType || undefined,
            status: status || undefined,
            page,
            size: pageSize,
          },
        })
      ).data,
    placeholderData: keepPreviousData,
  })

  const totalElements = data?.totalElements ?? 0
  const totalPages = data?.totalPages ?? 0
  const fromRow = totalElements === 0 ? 0 : page * pageSize + 1
  const toRow = Math.min((page + 1) * pageSize, totalElements)
  const isPageTransition = isFetching && !isLoading

  return (
    <div>
      <PageHeader
        title="Employee Directory"
        description="Onboarding & master record management"
        actions={
          <PrimaryButton onClick={() => navigate('/onboarding/new')}>
            <Plus size={15} /> Onboard Employee
          </PrimaryButton>
        }
      />

      <Card className="mb-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <div className="relative lg:col-span-2">
            <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by Code or Name..."
              className="w-full rounded-md border border-slate-300 py-2 pl-9 pr-3 text-sm"
            />
          </div>
          <select
            value={roId}
            onChange={(e) => updateFilter(setRoId, e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">All Regions</option>
            {(officesQuery.data ?? []).map((o) => (
              <option key={o.id} value={o.id}>
                {o.name}
              </option>
            ))}
          </select>
          <select
            value={departmentId}
            onChange={(e) => updateFilter(setDepartmentId, e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">All Departments</option>
            {(departmentsQuery.data ?? []).map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </select>
          <select
            value={designationId}
            onChange={(e) => updateFilter(setDesignationId, e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">All Designations</option>
            {(designationsQuery.data ?? []).map((d) => (
              <option key={d.id} value={d.id}>
                {d.title}
              </option>
            ))}
          </select>
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <select
            value={employmentType}
            onChange={(e) => updateFilter(setEmploymentType, e.target.value)}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">All Types</option>
            {EMPLOYMENT_TYPES.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
          <select
            value={status}
            onChange={(e) => updateFilter(setStatus, e.target.value)}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            {STATUS_FILTERS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
          <SecondaryButton onClick={resetFilters}>Reset Filters</SecondaryButton>
        </div>
      </Card>

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load employees." />}

      {data && (
        <Card className="overflow-x-auto p-0">
          <table className={`w-full text-sm transition-opacity ${isPageTransition ? 'opacity-50' : ''}`}>
            <thead className="bg-slate-50 text-left text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-2.5">Code</th>
                <th className="px-4 py-2.5">Name</th>
                <th className="px-4 py-2.5">Department</th>
                <th className="px-4 py-2.5">Designation</th>
                <th className="px-4 py-2.5">Status</th>
                <th className="px-4 py-2.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {data.content.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-sm text-slate-400">
                    No employees match the current search/filters.
                  </td>
                </tr>
              )}
              {data.content.map((emp) => (
                <tr
                  key={emp.id}
                  className="cursor-pointer border-t border-slate-100 hover:bg-slate-50"
                  onClick={() => setProfileEmployeeId(emp.id)}
                >
                  <td className="px-4 py-2.5 font-medium text-brand-forest">{emp.employeeCode}</td>
                  <td className="px-4 py-2.5">{emp.fullName}</td>
                  <td className="px-4 py-2.5">{emp.departmentName}</td>
                  <td className="px-4 py-2.5">{emp.designationTitle}</td>
                  <td className="px-4 py-2.5">
                    <Badge tone={STATUS_BADGE_TONE[emp.status] ?? 'neutral'}>{emp.status}</Badge>
                  </td>
                  <td className="px-4 py-2.5 text-right">
                    {emp.employmentCategory === 'CONTRACTUAL' || emp.employmentCategory === 'OUTSOURCED' ? (
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation()
                          setRenewingEmployeeId(emp.id)
                        }}
                        title="Renew Contract / Deployment"
                        className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-brand-forest hover:bg-slate-100"
                      >
                        <RefreshCw size={13} /> Renew
                      </button>
                    ) : (
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation()
                          setProfileEmployeeId(emp.id)
                        }}
                        title="View Profile"
                        className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-slate-500 hover:bg-slate-100"
                      >
                        <UserRound size={13} /> View Profile
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="flex flex-col items-center justify-between gap-3 border-t border-slate-100 px-4 py-3 sm:flex-row">
            <div className="flex items-center gap-3 text-xs text-slate-500">
              <span>
                Showing {fromRow} to {toRow} of {totalElements} employees
              </span>
              <label className="flex items-center gap-1.5">
                Rows:
                <select
                  value={pageSize}
                  onChange={(e) => {
                    setPageSize(Number(e.target.value))
                    setPage(0)
                  }}
                  className="rounded-md border border-slate-300 px-1.5 py-1 text-xs"
                >
                  {PAGE_SIZE_OPTIONS.map((n) => (
                    <option key={n} value={n}>
                      {n}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <PaginationControls page={page} totalPages={totalPages} onChange={setPage} />
          </div>
        </Card>
      )}

      {profileEmployeeId !== null && (
        <Employee360Drawer employeeId={profileEmployeeId} onClose={() => setProfileEmployeeId(null)} />
      )}

      {renewingEmployeeId !== null && (
        <RenewContractModal employeeId={renewingEmployeeId} onClose={() => setRenewingEmployeeId(null)} />
      )}
    </div>
  )
}

/** First/Previous/numbered-pages (a window of up to 5 around the current page)/Next/Last - page is 0-indexed internally, shown 1-indexed. */
function PaginationControls({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null

  const windowSize = 5
  const start = Math.max(0, Math.min(page - Math.floor(windowSize / 2), totalPages - windowSize))
  const pages = Array.from({ length: Math.min(windowSize, totalPages) }, (_, i) => start + i)

  const buttonClass = (active: boolean) =>
    `inline-flex h-7 min-w-7 items-center justify-center rounded-md px-1.5 text-xs font-medium ${
      active ? 'bg-brand-forest text-white' : 'text-slate-600 hover:bg-slate-100'
    }`

  return (
    <div className="flex items-center gap-1">
      <button type="button" disabled={page === 0} onClick={() => onChange(0)} className={`${buttonClass(false)} disabled:opacity-30`} title="First">
        <ChevronsLeft size={14} />
      </button>
      <button
        type="button"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
        className={`${buttonClass(false)} disabled:opacity-30`}
        title="Previous"
      >
        <ChevronLeft size={14} />
      </button>
      {pages.map((p) => (
        <button key={p} type="button" onClick={() => onChange(p)} className={buttonClass(p === page)}>
          {p + 1}
        </button>
      ))}
      <button
        type="button"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
        className={`${buttonClass(false)} disabled:opacity-30`}
        title="Next"
      >
        <ChevronRight size={14} />
      </button>
      <button
        type="button"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(totalPages - 1)}
        className={`${buttonClass(false)} disabled:opacity-30`}
        title="Last"
      >
        <ChevronsRight size={14} />
      </button>
    </div>
  )
}
