import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { formatDate } from '../../lib/date'
import { EditAddressModal } from '../../components/employee/EditAddressModal'
import { AddBankAccountModal } from '../../components/employee/AddBankAccountModal'
import { Badge, Card, ErrorState, LoadingState, PageHeader, SecondaryButton } from '../../components/common/ui'
import type { EmployeeAddressResponse, EmployeeBankAccountResponse, EmployeeResponse, FunctionalRoleAssignmentResponse } from '../../types/api'

export function ProfilePage() {
  const { employeeId } = useAuth()
  const [editingAddress, setEditingAddress] = useState(false)
  const [addingBankAccount, setAddingBankAccount] = useState(false)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['employee', employeeId],
    queryFn: async () => (await apiClient.get<EmployeeResponse>(`/employees/${employeeId}`)).data,
    enabled: employeeId !== null,
  })

  const addressesQuery = useQuery({
    queryKey: ['employee-addresses', employeeId],
    queryFn: async () =>
      (await apiClient.get<EmployeeAddressResponse[]>(`/v1/employees/${employeeId}/addresses`)).data,
    enabled: employeeId !== null,
  })

  const bankAccountsQuery = useQuery({
    queryKey: ['employee-bank-accounts', employeeId],
    queryFn: async () =>
      (await apiClient.get<EmployeeBankAccountResponse[]>(`/v1/employees/${employeeId}/bank-accounts`)).data,
    enabled: employeeId !== null,
  })

  const functionalRolesQuery = useQuery({
    queryKey: ['employee-functional-roles', employeeId],
    queryFn: async () =>
      (
        await apiClient.get<FunctionalRoleAssignmentResponse[]>('/v1/master/functional-roles/assignments', {
          params: { employeeId, activeOnly: true },
        })
      ).data,
    enabled: employeeId !== null,
  })

  const presentAddress = addressesQuery.data?.find((a) => a.addressType === 'PRESENT') ?? null
  const permanentAddress = addressesQuery.data?.find((a) => a.addressType === 'PERMANENT') ?? null

  return (
    <div>
      <PageHeader title="My Profile" />

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load your profile." />}
      {!employeeId && <ErrorState message="Your token has no employee_id claim, so this page cannot resolve who you are." />}

      {data && (
        <Card className="mx-auto max-w-2xl">
          <div className="mb-4 flex items-center gap-4">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-brand-forest text-xl font-semibold text-white">
              {data.firstName[0]}
              {data.lastName[0]}
            </div>
            <div>
              <h2 className="text-lg font-semibold text-slate-800">{data.fullName}</h2>
              <p className="text-sm text-slate-500">{data.employeeCode}</p>
            </div>
            <Badge tone={data.status === 'ACTIVE' ? 'success' : 'neutral'} className="ml-auto">
              {data.status}
            </Badge>
          </div>

          <dl className="grid grid-cols-1 gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
            <Field label="Personal Email" value={data.personalEmail} />
            <Field label="Personal Mobile" value={data.phone} />
            <Field label="Official Email" value={data.officialEmail ?? '—'} />
            <Field label="Date of Joining" value={formatDate(data.dateOfJoining)} />
            <Field label="Date of Birth" value={formatDate(data.dateOfBirth)} />
            <Field label="Department" value={data.departmentName} />
            <Field label="Designation" value={data.designationTitle} />
            <Field label="Regional Office" value={data.roName ?? '—'} />
            <Field label="DPC" value={data.dpcName ?? '—'} />
            <Field label="Office Type" value={data.officeType ?? '—'} />
            <Field label="Pay Scale" value={data.payScaleGrade ?? '—'} />
          </dl>

          <hr className="my-4 border-slate-100" />

          <div className="mb-2 flex items-center justify-between">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Address</p>
            <SecondaryButton onClick={() => setEditingAddress(true)}>Edit Address</SecondaryButton>
          </div>

          <dl className="grid grid-cols-1 gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
            <Field label="Present Address" value={joinAddress(presentAddress)} />
            <Field label="Permanent Address" value={joinAddress(permanentAddress)} />
          </dl>

          <div className="mt-4 flex items-center gap-2">
            <span className="text-sm text-slate-400">Geofence:</span>
            <Badge tone={data.geofenceExempted ? 'warning' : 'neutral'}>{data.geofenceExempted ? 'Exempted' : 'Enforced'}</Badge>
          </div>

          <hr className="my-4 border-slate-100" />

          <div className="mb-2 flex items-center justify-between">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Bank Accounts</p>
            <SecondaryButton onClick={() => setAddingBankAccount(true)}>Add Bank Account</SecondaryButton>
          </div>

          {bankAccountsQuery.data && bankAccountsQuery.data.length === 0 && (
            <p className="text-sm text-slate-400">No bank account on record.</p>
          )}
          <div className="space-y-2">
            {(bankAccountsQuery.data ?? []).map((account) => (
              <div key={account.id} className="flex items-center justify-between rounded-md border border-slate-200 px-3 py-2 text-sm">
                <div>
                  <p className="font-medium text-slate-700">
                    {account.bankName} - {account.bankBranch}
                  </p>
                  <p className="text-xs text-slate-400">
                    A/C {account.bankAccountNumber} &middot; {account.bankIfsc}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {account.primaryDisbursal && <Badge tone="brand">Primary</Badge>}
                  <Badge tone={account.status === 'ACTIVE' ? 'success' : 'neutral'}>{account.status.replace('_', ' ')}</Badge>
                </div>
              </div>
            ))}
          </div>
        </Card>
      )}

      {functionalRolesQuery.data && functionalRolesQuery.data.length > 0 && (
        <Card className="mx-auto mt-4 max-w-2xl">
          <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-slate-500">Ex-Officio &amp; Statutory Appointments</p>
          <div className="space-y-2">
            {functionalRolesQuery.data.map((role) => (
              <div key={role.id} className="flex items-center justify-between rounded-md border border-slate-200 px-3 py-2">
                <div>
                  <p className="text-sm font-medium text-slate-700">{role.roleName}</p>
                  <p className="text-xs text-slate-400">
                    {[role.departmentName, role.officeName, role.zoneCode ? `Zone ${role.zoneCode}` : null].filter(Boolean).join(' / ') ||
                      'Organization-wide'}{' '}
                    · Order {role.officeOrderRef}
                  </p>
                </div>
                <Badge tone="brand">{role.roleCategory}</Badge>
              </div>
            ))}
          </div>
        </Card>
      )}

      {editingAddress && data && (
        <EditAddressModal
          employeeId={data.id}
          presentAddress={presentAddress}
          permanentAddress={permanentAddress}
          onClose={() => setEditingAddress(false)}
        />
      )}

      {addingBankAccount && data && (
        <AddBankAccountModal employeeId={data.id} onClose={() => setAddingBankAccount(false)} />
      )}
    </div>
  )
}

function joinAddress(address: EmployeeAddressResponse | null): string {
  if (!address) return '—'
  const parts = [address.addressLine1, address.addressLine2, address.city, address.district, address.state, address.pinCode]
  const filtered = parts.filter((part): part is string => Boolean(part && part.trim()))
  return filtered.length > 0 ? filtered.join(', ') : '—'
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-slate-400">{label}</dt>
      <dd className="font-medium text-slate-700">{value}</dd>
    </div>
  )
}
