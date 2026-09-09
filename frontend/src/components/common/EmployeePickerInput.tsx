import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Search, X } from 'lucide-react'
import { apiClient } from '../../api/client'
import type { EmployeeResponse, Page } from '../../types/api'

/**
 * Debounced employee code/name search + dropdown, backed by GET /v1/employees?search=... - the same
 * endpoint EmployeeDirectoryPage's own filter bar uses, now also reachable by CPF_ADMIN/FINANCE_ADMIN
 * (see EmployeeController's widened @PreAuthorize) for the CPF Trust module's own employee pickers
 * (Incoming Transfers, CPF Loan origination).
 */
export function EmployeePickerInput({
  selected,
  onSelect,
  placeholder = 'Search by employee code or name...',
  disabled,
}: {
  selected: EmployeeResponse | null
  onSelect: (employee: EmployeeResponse | null) => void
  placeholder?: string
  disabled?: boolean
}) {
  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handle = setTimeout(() => setDebouncedQuery(query), 300)
    return () => clearTimeout(handle)
  }, [query])

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const { data, isFetching } = useQuery({
    queryKey: ['employee-picker-search', debouncedQuery],
    queryFn: async () =>
      (await apiClient.get<Page<EmployeeResponse>>('/v1/employees', { params: { search: debouncedQuery, size: 10 } })).data.content,
    enabled: debouncedQuery.trim().length >= 2,
  })

  if (selected) {
    return (
      <div className="flex items-center justify-between rounded-md border border-slate-300 bg-slate-50 px-3 py-2 text-sm">
        <span>
          <span className="font-medium text-slate-800">{selected.fullName}</span>{' '}
          <span className="text-slate-400">({selected.employeeCode})</span>
        </span>
        {!disabled && (
          <button type="button" onClick={() => onSelect(null)} className="text-slate-400 hover:text-slate-600" aria-label="Clear selection">
            <X size={15} />
          </button>
        )}
      </div>
    )
  }

  return (
    <div ref={containerRef} className="relative">
      <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
      <input
        value={query}
        disabled={disabled}
        onChange={(e) => {
          setQuery(e.target.value)
          setOpen(true)
        }}
        onFocus={() => setOpen(true)}
        placeholder={placeholder}
        className="w-full rounded-md border border-slate-300 py-2 pl-9 pr-3 text-sm disabled:bg-slate-50"
      />
      {open && debouncedQuery.trim().length >= 2 && (
        <div className="absolute z-10 mt-1 max-h-64 w-full overflow-y-auto rounded-md border border-slate-200 bg-white shadow-lg">
          {isFetching && <p className="px-3 py-2 text-xs text-slate-400">Searching...</p>}
          {!isFetching && (data ?? []).length === 0 && <p className="px-3 py-2 text-xs text-slate-400">No employees match.</p>}
          {(data ?? []).map((emp) => (
            <button
              key={emp.id}
              type="button"
              onClick={() => {
                onSelect(emp)
                setQuery('')
                setOpen(false)
              }}
              className="block w-full px-3 py-2 text-left text-sm hover:bg-slate-50"
            >
              <span className="font-medium text-slate-800">{emp.fullName}</span>{' '}
              <span className="text-slate-400">({emp.employeeCode})</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
