import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import type { DepartmentResponse, Page } from '../../types/api'

/** Shared Department dropdown filter, used by every PIMS reporting-hub tab that supports department scoping. */
export function DepartmentFilter({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const { data } = useQuery({
    queryKey: ['departments-all'],
    queryFn: async () => (await apiClient.get<Page<DepartmentResponse>>('/departments', { params: { size: 200 } })).data.content,
  })

  return (
    <select value={value} onChange={(e) => onChange(e.target.value)} className="rounded-md border border-slate-300 px-3 py-2 text-sm">
      <option value="">All Departments</option>
      {(data ?? []).map((d) => (
        <option key={d.id} value={d.id}>
          {d.name}
        </option>
      ))}
    </select>
  )
}
