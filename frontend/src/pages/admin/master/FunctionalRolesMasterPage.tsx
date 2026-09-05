import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ShieldCheck } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { describeApiError, getFieldErrors } from '../../../lib/apiError'
import { formatDate } from '../../../lib/date'
import { DatePicker } from '../../../components/common/DatePicker'
import { Modal } from '../../../components/common/Modal'
import { useToast } from '../../../components/common/ToastProvider'
import {
  Badge,
  Card,
  EmptyState,
  ErrorState,
  FieldError,
  LoadingState,
  PageHeader,
  PrimaryButton,
  SecondaryButton,
  errorInputClass,
} from '../../../components/common/ui'
import type {
  DepartmentResponse,
  EmployeeResponse,
  FunctionalRoleAssignmentRequest,
  FunctionalRoleAssignmentResponse,
  FunctionalRoleMasterResponse,
  Page,
  RegionalOfficeResponse,
} from '../../../types/api'

const TABS = [
  { key: 'incumbents', label: 'Active Role Incumbents' },
  { key: 'assign', label: 'Assign Concurrent Role' },
  { key: 'definitions', label: 'Role Definitions' },
] as const
type TabKey = (typeof TABS)[number]['key']

/** The four roles that get their own summary card - the others are still visible in the full table below. */
const SUMMARY_ROLE_CODES = ['HOD', 'CISO', 'CPIO', 'FAA'] as const

/** ZONAL_MGR's fixed jurisdiction list - UAT issue #2 replaced this from a free-text field. */
const ZONE_OPTIONS = ['South Bengal', 'North Bengal', 'Bihar', 'Assam', 'Odisha', 'Andhra Pradesh'] as const

const ROLE_CATEGORY_TONE: Record<FunctionalRoleMasterResponse['roleCategory'], 'brand' | 'success' | 'warning' | 'neutral'> = {
  FUNCTIONAL: 'brand',
  STATUTORY: 'warning',
  GOVERNANCE: 'success',
  TRUST: 'neutral',
}

/**
 * PIMS/ALMS Functional & Statutory Role Management Subsystem - assigns
 * non-sanctioned concurrent roles (HOD, CISO, CPIO, FAA, BOT_SEC,
 * HINDI_OFFICER, VIGILANCE_OFFICER, ZONAL_MGR) to an employee alongside
 * their substantive post, with zero cadre headcount impact. Restricted to
 * HR_ADMIN/SUPER_ADMIN at the route level (see App.tsx).
 */
export function FunctionalRolesMasterPage() {
  const [tab, setTab] = useState<TabKey>('incumbents')

  return (
    <div>
      <PageHeader
        title="Functional &amp; Statutory Roles"
        description="Concurrent HoD, CISO, CPIO, FAA, and other ex-officio appointments - decoupled from sanctioned cadre headcount"
      />

      <div className="mb-4 flex gap-2 overflow-x-auto">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setTab(t.key)}
            className={`whitespace-nowrap rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${
              tab === t.key ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'incumbents' && <ActiveIncumbentsTab />}
      {tab === 'assign' && <AssignRoleTab onAssigned={() => setTab('incumbents')} />}
      {tab === 'definitions' && <RoleDefinitionsTab />}
    </div>
  )
}

function useFunctionalRoles() {
  return useQuery({
    queryKey: ['functional-roles'],
    queryFn: async () => (await apiClient.get<FunctionalRoleMasterResponse[]>('/v1/master/functional-roles')).data,
  })
}

function ActiveIncumbentsTab() {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [relieving, setRelieving] = useState<FunctionalRoleAssignmentResponse | null>(null)
  const [validTo, setValidTo] = useState('')

  const rolesQuery = useFunctionalRoles()
  const assignmentsQuery = useQuery({
    queryKey: ['functional-role-assignments', 'active'],
    queryFn: async () =>
      (await apiClient.get<FunctionalRoleAssignmentResponse[]>('/v1/master/functional-roles/assignments', { params: { activeOnly: true } })).data,
  })

  const relieveMutation = useMutation({
    mutationFn: async () => {
      if (!relieving) return
      await apiClient.patch(`/v1/master/functional-roles/assignments/${relieving.id}/relieve`, null, {
        params: validTo ? { validTo } : undefined,
      })
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'Assignment relieved.' })
      queryClient.invalidateQueries({ queryKey: ['functional-role-assignments'] })
      setRelieving(null)
      setValidTo('')
    },
    onError: (error) => show({ tone: 'error', message: describeApiError(error, 'Could not relieve this assignment.') }),
  })

  const assignments = assignmentsQuery.data ?? []
  const summaryCounts = useMemo(() => {
    const counts = new Map<string, number>()
    for (const a of assignmentsQuery.data ?? []) {
      counts.set(a.roleCode, (counts.get(a.roleCode) ?? 0) + 1)
    }
    return counts
  }, [assignmentsQuery.data])

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {SUMMARY_ROLE_CODES.map((code) => {
          const role = rolesQuery.data?.find((r) => r.roleCode === code)
          return (
            <Card key={code} className="text-center">
              <p className="text-xs font-medium uppercase tracking-wide text-slate-400">{role?.roleName ?? code}</p>
              <p className="mt-1 text-2xl font-semibold text-slate-800">{summaryCounts.get(code) ?? 0}</p>
              <p className="text-xs text-slate-400">current incumbent{summaryCounts.get(code) === 1 ? '' : 's'}</p>
            </Card>
          )
        })}
      </div>

      <Card>
        {assignmentsQuery.isLoading && <LoadingState label="Loading assignments..." />}
        {assignmentsQuery.isError && <ErrorState message="Could not load functional role assignments." />}
        {assignments.length === 0 && !assignmentsQuery.isLoading && !assignmentsQuery.isError && (
          <EmptyState message="No active concurrent role assignments." />
        )}
        {assignments.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[900px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Employee</th>
                  <th className="py-2 pr-3">Designation</th>
                  <th className="py-2 pr-3">Functional Role</th>
                  <th className="py-2 pr-3">Jurisdiction</th>
                  <th className="py-2 pr-3">Office Order Ref</th>
                  <th className="py-2 pr-3">Effective From</th>
                  <th className="py-2 pr-3">Status</th>
                  <th className="py-2 pl-3" />
                </tr>
              </thead>
              <tbody>
                {assignments.map((a) => (
                  <tr key={a.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3">
                      <span className="font-medium">{a.employeeCode}</span> - {a.employeeName}
                    </td>
                    <td className="py-2 pr-3">{a.designationTitle}</td>
                    <td className="py-2 pr-3">
                      <Badge tone={ROLE_CATEGORY_TONE[a.roleCategory]}>{a.roleName}</Badge>
                    </td>
                    <td className="py-2 pr-3">{jurisdictionLabel(a)}</td>
                    <td className="py-2 pr-3">{a.officeOrderRef}</td>
                    <td className="py-2 pr-3">{formatDate(a.validFrom)}</td>
                    <td className="py-2 pr-3">
                      <Badge tone={a.active ? 'success' : 'neutral'}>{a.active ? 'Active' : 'Relieved'}</Badge>
                    </td>
                    <td className="py-2 pl-3 text-right">
                      {a.active && (
                        <button
                          type="button"
                          onClick={() => setRelieving(a)}
                          className="inline-flex items-center gap-1 rounded-md border border-slate-300 px-2 py-1 text-xs font-medium text-slate-600 hover:bg-slate-50"
                        >
                          Relieve / End Tenure
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {relieving && (
        <Modal
          title={`Relieve ${relieving.employeeCode} - ${relieving.roleName}`}
          onClose={() => {
            setRelieving(null)
            setValidTo('')
          }}
        >
          <div className="space-y-4">
            <p className="text-sm text-slate-600">
              This ends the {relieving.roleName} appointment for {relieving.employeeName}, effective the date below. The
              employee's substantive post and cadre standing are unaffected.
            </p>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Relieved Effective (Valid To)</label>
              <DatePicker value={validTo} onChange={setValidTo} min={relieving.validFrom} />
              <p className="mt-1 text-xs text-slate-400">Leave blank to relieve effective today.</p>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <SecondaryButton
                type="button"
                onClick={() => {
                  setRelieving(null)
                  setValidTo('')
                }}
              >
                Cancel
              </SecondaryButton>
              <PrimaryButton type="button" onClick={() => relieveMutation.mutate()} disabled={relieveMutation.isPending}>
                {relieveMutation.isPending ? 'Relieving...' : 'Confirm Relieve'}
              </PrimaryButton>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}

function jurisdictionLabel(a: FunctionalRoleAssignmentResponse): string {
  const parts: string[] = []
  if (a.departmentName) parts.push(a.departmentName)
  if (a.officeName) parts.push(a.officeName)
  if (a.zoneCode) parts.push(`Zone ${a.zoneCode}`)
  return parts.length > 0 ? parts.join(' / ') : 'Organization-wide'
}

interface AssignForm {
  employeeId: string
  roleId: string
  departmentId: string
  officeId: string
  zoneCode: string
  officeOrderRef: string
  orderDate: string
  validFrom: string
  validTo: string
  isPrimaryRole: boolean
}

const EMPTY_ASSIGN_FORM: AssignForm = {
  employeeId: '',
  roleId: '',
  departmentId: '',
  officeId: '',
  zoneCode: '',
  officeOrderRef: '',
  orderDate: '',
  validFrom: '',
  validTo: '',
  isPrimaryRole: false,
}

function AssignRoleTab({ onAssigned }: { onAssigned: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<AssignForm>(EMPTY_ASSIGN_FORM)
  const [employeeSearch, setEmployeeSearch] = useState('')

  const rolesQuery = useFunctionalRoles()
  const employeesQuery = useQuery({
    queryKey: ['employees-for-functional-role-assign'],
    queryFn: async () => (await apiClient.get<Page<EmployeeResponse>>('/employees', { params: { size: 500 } })).data.content,
  })
  const departmentsQuery = useQuery({
    queryKey: ['departments-for-functional-role-assign'],
    queryFn: async () => (await apiClient.get<Page<DepartmentResponse>>('/v1/departments', { params: { size: 200 } })).data.content,
  })
  const officesQuery = useQuery({
    queryKey: ['offices-for-functional-role-assign'],
    queryFn: async () => (await apiClient.get<Page<RegionalOfficeResponse>>('/v1/offices', { params: { size: 200 } })).data.content,
  })

  const selectedRole = rolesQuery.data?.find((r) => r.id === form.roleId)
  const selectedEmployee = (employeesQuery.data ?? []).find((e) => String(e.id) === form.employeeId)
  const employeeMatches = (employeesQuery.data ?? []).filter((e) => {
    const q = employeeSearch.toLowerCase()
    return q.length > 0 && (e.employeeCode.toLowerCase().includes(q) || e.fullName.toLowerCase().includes(q))
  })

  const [fieldErrors, setFieldErrors] = useState<Record<string, string> | null>(null)

  const assignMutation = useMutation({
    mutationFn: async () => {
      const payload: FunctionalRoleAssignmentRequest = {
        roleId: form.roleId,
        employeeId: Number(form.employeeId),
        departmentId: form.departmentId ? Number(form.departmentId) : null,
        officeId: form.officeId ? Number(form.officeId) : null,
        zoneCode: form.zoneCode || null,
        officeOrderRef: form.officeOrderRef,
        orderDate: form.orderDate,
        validFrom: form.validFrom,
        validTo: form.validTo || null,
        isPrimaryRole: form.isPrimaryRole,
      }
      await apiClient.post('/v1/master/functional-roles/assignments', payload)
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'Role assigned.' })
      queryClient.invalidateQueries({ queryKey: ['functional-role-assignments'] })
      setForm(EMPTY_ASSIGN_FORM)
      setEmployeeSearch('')
      setFieldErrors(null)
      onAssigned()
    },
    onError: (error) => {
      setFieldErrors(getFieldErrors(error))
      show({ tone: 'error', message: describeApiError(error, 'Could not assign this role.') })
    },
  })

  const jurisdictionValid =
    selectedRole?.roleCode === 'HOD'
      ? form.departmentId !== ''
      : selectedRole?.roleCode === 'CPIO' || selectedRole?.roleCode === 'FAA'
        ? form.officeId !== ''
        : selectedRole?.roleCode === 'ZONAL_MGR'
          ? form.zoneCode !== ''
          : true

  const formValid =
    form.employeeId !== '' &&
    form.roleId !== '' &&
    form.officeOrderRef.trim() !== '' &&
    form.orderDate !== '' &&
    form.validFrom !== '' &&
    (form.validTo === '' || form.validTo >= form.validFrom) &&
    jurisdictionValid

  return (
    <Card>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="relative sm:col-span-2">
          <label className="mb-1 block text-xs font-medium text-slate-600">Employee</label>
          <input
            value={form.employeeId ? `${selectedEmployee?.employeeCode} - ${selectedEmployee?.fullName}` : employeeSearch}
            onChange={(e) => {
              setEmployeeSearch(e.target.value)
              setForm((f) => ({ ...f, employeeId: '' }))
            }}
            placeholder="Search by employee code or name..."
            className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.employeeId))}`}
          />
          {employeeSearch && !form.employeeId && (
            <div className="absolute z-10 mt-1 max-h-48 w-full overflow-y-auto rounded-md border border-slate-200 bg-white shadow-lg">
              {employeeMatches.length === 0 && <p className="p-2 text-xs text-slate-400">No matches.</p>}
              {employeeMatches.slice(0, 20).map((emp) => (
                <button
                  key={emp.id}
                  type="button"
                  onClick={() => {
                    setForm((f) => ({ ...f, employeeId: String(emp.id) }))
                    setEmployeeSearch('')
                  }}
                  className="block w-full px-3 py-2 text-left text-sm hover:bg-slate-50"
                >
                  <span className="font-medium">{emp.employeeCode}</span> - {emp.fullName}
                  <span className="block text-xs text-slate-400">
                    {emp.designationTitle} · {emp.departmentName}
                  </span>
                </button>
              ))}
            </div>
          )}
          <FieldError message={fieldErrors?.employeeId} />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Functional Role</label>
          <select
            value={form.roleId}
            onChange={(e) => setForm((f) => ({ ...f, roleId: e.target.value, departmentId: '', officeId: '', zoneCode: '' }))}
            className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.roleId))}`}
          >
            <option value="">Select a role...</option>
            {(rolesQuery.data ?? []).map((r) => (
              <option key={r.id} value={r.id}>
                {r.roleName} ({r.roleCode})
              </option>
            ))}
          </select>
          <FieldError message={fieldErrors?.roleId} />
        </div>

        {selectedRole?.roleCode === 'HOD' && (
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Concerned Department *</label>
            <select
              value={form.departmentId}
              onChange={(e) => setForm((f) => ({ ...f, departmentId: e.target.value }))}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.departmentId))}`}
            >
              <option value="">Select a department...</option>
              {(departmentsQuery.data ?? []).map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </select>
            <FieldError message={fieldErrors?.departmentId} />
          </div>
        )}

        {(selectedRole?.roleCode === 'CPIO' || selectedRole?.roleCode === 'FAA') && (
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Office / Location *</label>
            <select
              value={form.officeId}
              onChange={(e) => setForm((f) => ({ ...f, officeId: e.target.value }))}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.officeId))}`}
            >
              <option value="">Select an office...</option>
              {(officesQuery.data ?? []).map((o) => (
                <option key={o.id} value={o.id}>
                  {o.name}
                </option>
              ))}
            </select>
            <FieldError message={fieldErrors?.officeId} />
          </div>
        )}

        {selectedRole?.roleCode === 'ZONAL_MGR' && (
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Zone *</label>
            <select
              value={form.zoneCode}
              onChange={(e) => setForm((f) => ({ ...f, zoneCode: e.target.value }))}
              className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.zoneCode))}`}
            >
              <option value="">Select a zone...</option>
              {ZONE_OPTIONS.map((zone) => (
                <option key={zone} value={zone}>
                  {zone}
                </option>
              ))}
            </select>
            <FieldError message={fieldErrors?.zoneCode} />
          </div>
        )}

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Office Order Reference</label>
          <input
            value={form.officeOrderRef}
            onChange={(e) => setForm((f) => ({ ...f, officeOrderRef: e.target.value }))}
            placeholder="JCI/HO/Pers/2026/112"
            className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(fieldErrors?.officeOrderRef))}`}
          />
          <FieldError message={fieldErrors?.officeOrderRef} />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Order Date</label>
          <DatePicker
            value={form.orderDate}
            onChange={(v) => setForm((f) => ({ ...f, orderDate: v }))}
            hasError={Boolean(fieldErrors?.orderDate)}
          />
          <FieldError message={fieldErrors?.orderDate} />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Valid From</label>
          <DatePicker
            value={form.validFrom}
            onChange={(v) => setForm((f) => ({ ...f, validFrom: v }))}
            hasError={Boolean(fieldErrors?.validFrom)}
          />
          <FieldError message={fieldErrors?.validFrom} />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Valid To (optional)</label>
          <DatePicker
            value={form.validTo}
            onChange={(v) => setForm((f) => ({ ...f, validTo: v }))}
            min={form.validFrom || undefined}
          />
          {form.validTo !== '' && form.validFrom !== '' && form.validTo < form.validFrom && (
            <p className="mt-1 text-xs text-red-600">Must not be before Valid From.</p>
          )}
        </div>

        <label className="flex items-center gap-2 text-sm text-slate-600">
          <input
            type="checkbox"
            checked={form.isPrimaryRole}
            onChange={(e) => setForm((f) => ({ ...f, isPrimaryRole: e.target.checked }))}
          />
          Primary role for this employee
        </label>
      </div>

      <div className="mt-4 flex justify-end">
        <PrimaryButton type="button" onClick={() => assignMutation.mutate()} disabled={!formValid || assignMutation.isPending}>
          {assignMutation.isPending ? 'Assigning...' : 'Assign Role'}
        </PrimaryButton>
      </div>
    </Card>
  )
}

function RoleDefinitionsTab() {
  const rolesQuery = useFunctionalRoles()

  return (
    <div className="space-y-4">
      <Card className="flex items-start gap-3 border-emerald-200 bg-emerald-50">
        <ShieldCheck className="mt-0.5 shrink-0 text-emerald-600" size={20} />
        <div>
          <p className="text-sm font-semibold text-emerald-800">Zero Cadre Headcount Impact</p>
          <p className="mt-1 text-sm text-emerald-700">
            Every role below is an appointment layered on top of an employee's substantive sanctioned post - assigning or
            relieving one never changes post_master vacancy counts or cadre strength.
          </p>
        </div>
      </Card>

      <Card>
        {rolesQuery.isLoading && <LoadingState label="Loading role definitions..." />}
        {rolesQuery.isError && <ErrorState message="Could not load role definitions." />}
        {rolesQuery.data && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Code</th>
                  <th className="py-2 pr-3">Role</th>
                  <th className="py-2 pr-3">Category</th>
                  <th className="py-2 pr-3">Financial Delegation</th>
                  <th className="py-2 pr-3">Administrative Delegation</th>
                </tr>
              </thead>
              <tbody>
                {rolesQuery.data.map((r) => (
                  <tr key={r.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3 font-medium">{r.roleCode}</td>
                    <td className="py-2 pr-3">{r.roleName}</td>
                    <td className="py-2 pr-3">
                      <Badge tone={ROLE_CATEGORY_TONE[r.roleCategory]}>{r.roleCategory}</Badge>
                    </td>
                    <td className="py-2 pr-3">
                      <Badge tone={r.hasFinancialDelegation ? 'success' : 'neutral'}>{r.hasFinancialDelegation ? 'Yes' : 'No'}</Badge>
                    </td>
                    <td className="py-2 pr-3">
                      <Badge tone={r.hasAdministrativeDelegation ? 'success' : 'neutral'}>
                        {r.hasAdministrativeDelegation ? 'Yes' : 'No'}
                      </Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}
