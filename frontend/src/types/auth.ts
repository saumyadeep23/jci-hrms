export type Role =
  | 'EMPLOYEE'
  | 'HR_ADMIN'
  | 'FINANCE_ADMIN'
  | 'CPF_ADMIN'
  | 'COOP_ADMIN'
  | 'SUPER_ADMIN'
  /** JCI Payroll Engine master screens (PayrollMasterController) - see its own @PreAuthorize. */
  | 'BILL_SUPERVISOR'

export interface DecodedToken {
  sub?: string
  employee_id?: string | number
  /** Flat root-level roles claim - the shape a hand-minted dev JWT (or plenty of real IdPs) uses instead of Keycloak's nested realm_access/resource_access. */
  roles?: string[]
  realm_access?: { roles?: string[] }
  resource_access?: Record<string, { roles?: string[] }>
  exp?: number
  [key: string]: unknown
}

export interface AuthState {
  token: string | null
  roles: Role[]
  employeeId: number | null
  subject: string | null
}
