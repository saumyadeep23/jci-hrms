import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import { useAuth } from './AuthContext'
import type { EmployeeResponse, EmploymentCategory } from '../types/api'

/**
 * The signed-in user's own EmploymentCategory (REGULAR/CASUAL/CONTRACTUAL/OUTSOURCED) - drives
 * gating like the Sidebar's e-Service Book & Career Timeline link (REGULAR only, since CASUAL/
 * CONTRACTUAL/OUTSOURCED staff aren't on a grade-scale career ladder). Only GET /employees/{id}
 * (not the list endpoint) returns this field - see EmployeeResponse.employmentCategory's javadoc.
 * null while loading, on error, or when the token carries no employeeId (e.g. roles couldn't be
 * parsed) - callers should treat null as "don't show the gated item yet", not "definitely not REGULAR".
 */
export function useMyEmploymentCategory(): EmploymentCategory | null {
  const { employeeId } = useAuth()

  const { data } = useQuery({
    queryKey: ['my-employment-category', employeeId],
    queryFn: async () => (await apiClient.get<EmployeeResponse>(`/employees/${employeeId}`)).data,
    enabled: employeeId !== null,
    staleTime: 5 * 60 * 1000,
  })

  return data?.employmentCategory ?? null
}
