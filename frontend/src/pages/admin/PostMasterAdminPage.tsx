import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router-dom'
import { Ban, History, Pencil, Plus, Snowflake, UserPlus } from 'lucide-react'
import { apiClient } from '../../api/client'
import { describeApiErrorList, getFieldErrors } from '../../lib/apiError'
import { formatDate } from '../../lib/date'
import { Modal } from '../../components/common/Modal'
import { FormErrorBanner } from '../../components/common/FormErrorBanner'
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
} from '../../components/common/ui'
import type {
  AssignIncumbencyRequest,
  AssignmentType,
  DepartmentResponse,
  DesignationResponse,
  DpcResponse,
  EmployeeResponse,
  Page,
  PostIncumbencyResponse,
  PostInventorySummaryResponse,
  PostMasterRequest,
  PostMasterResponse,
  PostStatusUpdateRequest,
  RegionalOfficeResponse,
  VacancyStatus,
} from '../../types/api'

type LocationType = 'HEAD_OFFICE' | 'REGIONAL_OFFICE' | 'DPC'

const VACANCY_TONE: Record<VacancyStatus, 'success' | 'warning' | 'neutral' | 'danger'> = {
  OCCUPIED: 'success',
  VACANT: 'warning',
  FROZEN: 'neutral',
  ABOLISHED: 'danger',
}

const EMPTY_ROS: RegionalOfficeResponse[] = []
const EMPTY_DPCS: DpcResponse[] = []

const EMPTY_FORM = {
  postCode: '',
  title: '',
  departmentId: '',
  designationId: '',
  locationType: 'HEAD_OFFICE' as LocationType,
  roId: '',
  dpcId: '',
  operationalReportingPostId: '',
  administrativeReportingPostId: '',
  acceptingAuthorityPostId: '',
  isBudgeted: true,
  active: true,
}

/** PIMS_SPEC.md Section 2: Sanctioned Post Master Management GUI. */
export function PostMasterAdminPage() {
  const queryClient = useQueryClient()

  const [filters, setFilters] = useState({
    departmentId: '',
    designationId: '',
    locationType: '',
    vacancyStatus: '',
    isBudgeted: '',
    search: '',
  })
  const [pageIndex, setPageIndex] = useState(0)
  const pageSize = 20

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<PostMasterResponse | null>(null)
  const [form, setForm] = useState(EMPTY_FORM)

  const [historyPost, setHistoryPost] = useState<PostMasterResponse | null>(null)
  const [statusRemark, setStatusRemark] = useState('')

  const [assignChargePost, setAssignChargePost] = useState<PostMasterResponse | null>(null)
  const [chargeForm, setChargeForm] = useState({
    employeeSearch: '',
    employeeId: '',
    assignmentType: 'ADDITIONAL_CHARGE' as AssignmentType,
    startDate: '',
    orderReference: '',
    orderDate: '',
  })

  const departmentsQuery = useQuery({
    queryKey: ['departments-all'],
    queryFn: async () => (await apiClient.get<Page<DepartmentResponse>>('/departments', { params: { size: 200 } })).data.content,
  })
  const designationsQuery = useQuery({
    queryKey: ['designations-all'],
    queryFn: async () => (await apiClient.get<Page<DesignationResponse>>('/designations', { params: { size: 200 } })).data.content,
  })
  const regionalOfficesQuery = useQuery({
    queryKey: ['ro-masters-all'],
    queryFn: async () => (await apiClient.get<Page<RegionalOfficeResponse>>('/regional-offices', { params: { size: 200 } })).data.content,
  })
  const dpcsQuery = useQuery({
    queryKey: ['dpc-masters-all'],
    queryFn: async () => (await apiClient.get<Page<DpcResponse>>('/dpcs', { params: { size: 500 } })).data.content,
  })
  const allPostsQuery = useQuery({
    queryKey: ['posts-all-for-picker'],
    queryFn: async () => (await apiClient.get<Page<PostMasterResponse>>('/v1/posts', { params: { size: 2000 } })).data.content,
  })

  const departments = departmentsQuery.data ?? []
  const designations = designationsQuery.data ?? []
  const regionalOffices = regionalOfficesQuery.data ?? EMPTY_ROS
  const dpcs = dpcsQuery.data ?? EMPTY_DPCS
  const allPosts = allPostsQuery.data ?? []
  const reportingPostOptions = allPosts.filter((p) => !editing || p.id !== editing.id)

  const summaryQuery = useQuery({
    queryKey: ['posts-summary'],
    queryFn: async () => (await apiClient.get<PostInventorySummaryResponse>('/v1/posts/summary')).data,
  })

  const listQuery = useQuery({
    queryKey: ['posts-list', filters, pageIndex],
    queryFn: async () =>
      (
        await apiClient.get<Page<PostMasterResponse>>('/v1/posts', {
          params: {
            departmentId: filters.departmentId || undefined,
            designationId: filters.designationId || undefined,
            locationType: filters.locationType || undefined,
            vacancyStatus: filters.vacancyStatus || undefined,
            isBudgeted: filters.isBudgeted || undefined,
            search: filters.search || undefined,
            page: pageIndex,
            size: pageSize,
          },
        })
      ).data,
  })

  const historyQuery = useQuery({
    queryKey: ['post-incumbency-history', historyPost?.id],
    queryFn: async () => (await apiClient.get<PostIncumbencyResponse[]>(`/v1/posts/${historyPost!.id}/incumbency-history`)).data,
    enabled: historyPost !== null,
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload: PostMasterRequest = {
        postCode: form.postCode,
        title: form.title,
        departmentId: Number(form.departmentId),
        designationId: Number(form.designationId),
        roId: form.roId ? Number(form.roId) : null,
        dpcId: form.locationType === 'DPC' && form.dpcId ? Number(form.dpcId) : null,
        operationalReportingPostId: form.operationalReportingPostId ? Number(form.operationalReportingPostId) : null,
        administrativeReportingPostId: form.administrativeReportingPostId ? Number(form.administrativeReportingPostId) : null,
        acceptingAuthorityPostId: form.acceptingAuthorityPostId ? Number(form.acceptingAuthorityPostId) : null,
        isBudgeted: form.isBudgeted,
        active: form.active,
      }
      if (editing) {
        return (await apiClient.put<PostMasterResponse>(`/v1/posts/${editing.id}`, payload)).data
      }
      return (await apiClient.post<PostMasterResponse>('/v1/posts', payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['posts-list'] })
      queryClient.invalidateQueries({ queryKey: ['posts-summary'] })
      queryClient.invalidateQueries({ queryKey: ['posts-all-for-picker'] })
      closeDrawer()
    },
  })

  const statusMutation = useMutation({
    mutationFn: async ({ postId, targetStatus }: { postId: number; targetStatus: VacancyStatus }) => {
      const payload: PostStatusUpdateRequest = { targetStatus, remark: statusRemark || null }
      return (await apiClient.patch<PostMasterResponse>(`/v1/posts/${postId}/status`, payload)).data
    },
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['posts-list'] })
      queryClient.invalidateQueries({ queryKey: ['posts-summary'] })
      setHistoryPost(updated)
      setStatusRemark('')
    },
  })

  const employeesQuery = useQuery({
    queryKey: ['employees-all-for-charge-picker'],
    queryFn: async () => (await apiClient.get<Page<EmployeeResponse>>('/employees', { params: { size: 500 } })).data.content,
    enabled: assignChargePost !== null,
  })
  const employeeMatches = (employeesQuery.data ?? []).filter((e) => {
    const q = chargeForm.employeeSearch.trim().toLowerCase()
    if (!q) return false
    return e.fullName.toLowerCase().includes(q) || e.employeeCode.toLowerCase().includes(q)
  })

  const assignChargeMutation = useMutation({
    mutationFn: async () => {
      const payload: AssignIncumbencyRequest = {
        employeeId: Number(chargeForm.employeeId),
        assignmentType: chargeForm.assignmentType,
        startDate: chargeForm.startDate,
        orderReference: chargeForm.orderReference || null,
        orderDate: chargeForm.orderDate || null,
      }
      return (await apiClient.post<PostIncumbencyResponse>(`/v1/posts/${assignChargePost!.id}/incumbency`, payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['post-incumbency-history'] })
      queryClient.invalidateQueries({ queryKey: ['posts-list'] })
      closeAssignCharge()
    },
  })
  const chargeFieldErrors = getFieldErrors(assignChargeMutation.error)

  const relieveMutation = useMutation({
    mutationFn: async (incumbencyId: number) => (await apiClient.patch<PostIncumbencyResponse>(`/v1/posts/incumbency/${incumbencyId}/relieve`)).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['post-incumbency-history'] })
      queryClient.invalidateQueries({ queryKey: ['posts-list'] })
    },
  })

  function openAssignCharge(post: PostMasterResponse) {
    setAssignChargePost(post)
    setChargeForm({ employeeSearch: '', employeeId: '', assignmentType: 'ADDITIONAL_CHARGE', startDate: '', orderReference: '', orderDate: '' })
  }
  function closeAssignCharge() {
    setAssignChargePost(null)
  }

  const roOptionsForLocation = useMemo(
    () =>
      form.locationType === 'HEAD_OFFICE'
        ? regionalOffices.filter((ro) => ro.officeType === 'HEAD_OFFICE')
        : regionalOffices.filter((ro) => ro.officeType !== 'HEAD_OFFICE'),
    [regionalOffices, form.locationType],
  )
  const dpcOptionsForRo = useMemo(
    () => dpcs.filter((dpc) => String(dpc.roId) === form.roId),
    [dpcs, form.roId],
  )

  function suggestPostCode() {
    const dept = departments.find((d) => String(d.id) === form.departmentId)
    const loc = form.locationType === 'DPC' ? dpcs.find((d) => String(d.id) === form.dpcId)?.code
      : regionalOffices.find((r) => String(r.id) === form.roId)?.code
    if (!dept) return
    setForm({ ...form, postCode: `POST-${dept.code}-${loc ?? 'HO'}-` })
  }

  function openCreate() {
    setEditing(null)
    setForm(EMPTY_FORM)
    setDrawerOpen(true)
  }

  const location = useLocation()
  const navigate = useNavigate()
  useEffect(() => {
    if (location.pathname === '/admin/posts/new') {
      openCreate()
      navigate('/admin/posts', { replace: true })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname])

  function openEdit(post: PostMasterResponse) {
    setEditing(post)
    setForm({
      postCode: post.postCode,
      title: post.title,
      departmentId: String(post.departmentId),
      designationId: String(post.designationId),
      locationType: post.dpcId ? 'DPC' : post.roId ? 'REGIONAL_OFFICE' : 'HEAD_OFFICE',
      roId: post.roId ? String(post.roId) : '',
      dpcId: post.dpcId ? String(post.dpcId) : '',
      operationalReportingPostId: post.operationalReportingPostId ? String(post.operationalReportingPostId) : '',
      administrativeReportingPostId: post.administrativeReportingPostId ? String(post.administrativeReportingPostId) : '',
      acceptingAuthorityPostId: post.acceptingAuthorityPostId ? String(post.acceptingAuthorityPostId) : '',
      isBudgeted: post.isBudgeted,
      active: post.active,
    })
    setDrawerOpen(true)
  }

  function closeDrawer() {
    setDrawerOpen(false)
    setEditing(null)
    setForm(EMPTY_FORM)
  }

  const formValid = form.postCode.trim() && form.title.trim() && form.departmentId && form.designationId
  const saveFieldErrors = getFieldErrors(saveMutation.error)

  return (
    <div>
      <PageHeader
        title="Sanctioned Post Master"
        description="Sanctioned seat inventory, reporting hierarchy, and incumbency ledger"
        actions={
          <PrimaryButton onClick={openCreate}>
            <Plus size={15} /> Add Post
          </PrimaryButton>
        }
      />

      <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <KpiCard label="Total Sanctioned" value={summaryQuery.data?.totalSanctioned} tone="brand" />
        <KpiCard label="Occupied" value={summaryQuery.data?.occupied} tone="success" />
        <KpiCard label="Vacant" value={summaryQuery.data?.vacant} tone="warning" />
        <KpiCard label="Frozen / Abolished" value={(summaryQuery.data?.frozen ?? 0) + (summaryQuery.data?.abolished ?? 0)} tone="neutral" />
      </div>

      <Card className="mb-4">
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          <input
            value={filters.search}
            onChange={(e) => {
              setFilters({ ...filters, search: e.target.value })
              setPageIndex(0)
            }}
            placeholder="Search post code/title..."
            className="col-span-2 rounded-md border border-slate-300 px-3 py-2 text-sm lg:col-span-2"
          />
          <select
            value={filters.departmentId}
            onChange={(e) => {
              setFilters({ ...filters, departmentId: e.target.value })
              setPageIndex(0)
            }}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">All Departments</option>
            {departments.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </select>
          <select
            value={filters.designationId}
            onChange={(e) => {
              setFilters({ ...filters, designationId: e.target.value })
              setPageIndex(0)
            }}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">All Designations</option>
            {designations.map((d) => (
              <option key={d.id} value={d.id}>
                {d.title}
              </option>
            ))}
          </select>
          <select
            value={filters.locationType}
            onChange={(e) => {
              setFilters({ ...filters, locationType: e.target.value })
              setPageIndex(0)
            }}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">All Locations</option>
            <option value="HEAD_OFFICE">Head Office</option>
            <option value="REGIONAL_OFFICE">Regional Office</option>
            <option value="DPC">DPC</option>
          </select>
          <select
            value={filters.vacancyStatus}
            onChange={(e) => {
              setFilters({ ...filters, vacancyStatus: e.target.value })
              setPageIndex(0)
            }}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">All Statuses</option>
            <option value="VACANT">Vacant</option>
            <option value="OCCUPIED">Occupied</option>
            <option value="FROZEN">Frozen</option>
            <option value="ABOLISHED">Abolished</option>
          </select>
          <select
            value={filters.isBudgeted}
            onChange={(e) => {
              setFilters({ ...filters, isBudgeted: e.target.value })
              setPageIndex(0)
            }}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">Budgeted: All</option>
            <option value="true">Budgeted Only</option>
            <option value="false">Unbudgeted Only</option>
          </select>
        </div>
      </Card>

      <Card>
        {listQuery.isLoading && <LoadingState label="Loading posts..." />}
        {listQuery.isError && <ErrorState message="Could not load posts." />}
        {listQuery.data && listQuery.data.content.length === 0 && <EmptyState message="No posts match these filters." />}

        {listQuery.data && listQuery.data.content.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[960px] border-collapse text-sm">
                <thead>
                  <tr className="border-b border-slate-300 text-left text-slate-500">
                    <th className="py-2 pr-3">Post Code</th>
                    <th className="py-2 pr-3">Title</th>
                    <th className="py-2 pr-3">Department</th>
                    <th className="py-2 pr-3">Designation</th>
                    <th className="py-2 pr-3">Location</th>
                    <th className="py-2 pr-3">Budgeted</th>
                    <th className="py-2 pr-3">Status</th>
                    <th className="py-2 pl-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {listQuery.data.content.map((post) => (
                    <tr key={post.id} className="border-b border-slate-100">
                      <td className="py-2 pr-3 font-medium">{post.postCode}</td>
                      <td className="py-2 pr-3">{post.title}</td>
                      <td className="py-2 pr-3 text-slate-500">{post.departmentName}</td>
                      <td className="py-2 pr-3 text-slate-500">{post.designationTitle}</td>
                      <td className="py-2 pr-3 text-xs text-slate-500">{post.dpcName ?? post.roName ?? 'Head Office'}</td>
                      <td className="py-2 pr-3">
                        <Badge tone={post.isBudgeted ? 'brand' : 'neutral'}>{post.isBudgeted ? 'Budgeted' : 'Unbudgeted'}</Badge>
                      </td>
                      <td className="py-2 pr-3">
                        <Badge tone={VACANCY_TONE[post.vacancyStatus]}>{post.vacancyStatus}</Badge>
                      </td>
                      <td className="py-2 pl-3">
                        <div className="flex justify-end gap-2">
                          <button
                            type="button"
                            onClick={() => openEdit(post)}
                            className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
                            title="Edit"
                          >
                            <Pencil size={15} />
                          </button>
                          <button
                            type="button"
                            onClick={() => setHistoryPost(post)}
                            className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
                            title="Incumbency History"
                          >
                            <History size={15} />
                          </button>
                          <button
                            type="button"
                            onClick={() => openAssignCharge(post)}
                            className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
                            title="Assign Additional Charge"
                          >
                            <UserPlus size={15} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="mt-4 flex items-center justify-between text-xs text-slate-500">
              <span>
                Page {listQuery.data.number + 1} of {Math.max(1, listQuery.data.totalPages)} ({listQuery.data.totalElements} total)
              </span>
              <div className="flex gap-2">
                <SecondaryButton onClick={() => setPageIndex((p) => Math.max(0, p - 1))} disabled={listQuery.data.first}>
                  Previous
                </SecondaryButton>
                <SecondaryButton onClick={() => setPageIndex((p) => p + 1)} disabled={listQuery.data.last}>
                  Next
                </SecondaryButton>
              </div>
            </div>
          </>
        )}
      </Card>

      {drawerOpen && (
        <Modal title={editing ? `Edit ${editing.postCode}` : 'Add Sanctioned Post'} onClose={closeDrawer} maxWidthClassName="max-w-2xl">
          <form
            className="space-y-4"
            onSubmit={(e) => {
              e.preventDefault()
              saveMutation.mutate()
            }}
          >
            {saveMutation.isError && (
              <FormErrorBanner title="Could not save the post" errors={describeApiErrorList(saveMutation.error, 'Could not save the post.')} />
            )}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1 flex items-center justify-between text-xs font-medium text-slate-600">
                  Post Code
                  <button type="button" onClick={suggestPostCode} className="text-brand-forest hover:underline">
                    Suggest
                  </button>
                </label>
                <input
                  required
                  value={form.postCode}
                  onChange={(e) => setForm({ ...form, postCode: e.target.value })}
                  placeholder="POST-DEPT-LOC-SEQ"
                  className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(saveFieldErrors?.postCode))}`}
                />
                <FieldError message={saveFieldErrors?.postCode} />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Title</label>
                <input
                  required
                  value={form.title}
                  onChange={(e) => setForm({ ...form, title: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Department</label>
                <select
                  required
                  value={form.departmentId}
                  onChange={(e) => setForm({ ...form, departmentId: e.target.value })}
                  className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(saveFieldErrors?.departmentId))}`}
                >
                  <option value="">Select...</option>
                  {departments.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.name}
                    </option>
                  ))}
                </select>
                <FieldError message={saveFieldErrors?.departmentId} />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Designation</label>
                <select
                  required
                  value={form.designationId}
                  onChange={(e) => setForm({ ...form, designationId: e.target.value })}
                  className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(saveFieldErrors?.designationId))}`}
                >
                  <option value="">Select...</option>
                  {designations.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.title}
                    </option>
                  ))}
                </select>
                <FieldError message={saveFieldErrors?.designationId} />
              </div>
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Location</label>
              <div className="mb-2 flex flex-wrap gap-2">
                {(['HEAD_OFFICE', 'REGIONAL_OFFICE', 'DPC'] as LocationType[]).map((type) => (
                  <button
                    key={type}
                    type="button"
                    onClick={() => setForm({ ...form, locationType: type, roId: '', dpcId: '' })}
                    className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                      form.locationType === type ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600'
                    }`}
                  >
                    {type === 'HEAD_OFFICE' ? 'Head Office' : type === 'REGIONAL_OFFICE' ? 'Regional Office' : 'DPC'}
                  </button>
                ))}
              </div>
              <div className="grid grid-cols-2 gap-3">
                <select
                  required={form.locationType !== 'HEAD_OFFICE'}
                  value={form.roId}
                  onChange={(e) => setForm({ ...form, roId: e.target.value, dpcId: '' })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                >
                  <option value="">{form.locationType === 'HEAD_OFFICE' ? 'Head Office (optional)' : 'Select Regional Office...'}</option>
                  {roOptionsForLocation.map((ro) => (
                    <option key={ro.id} value={ro.id}>
                      {ro.code} - {ro.name}
                    </option>
                  ))}
                </select>
                {form.locationType === 'DPC' && (
                  <select
                    required
                    value={form.dpcId}
                    onChange={(e) => setForm({ ...form, dpcId: e.target.value })}
                    disabled={!form.roId}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-50"
                  >
                    <option value="">{form.roId ? 'Select DPC...' : 'Select a Regional Office first'}</option>
                    {dpcOptionsForRo.map((dpc) => (
                      <option key={dpc.id} value={dpc.id}>
                        {dpc.code} - {dpc.name}
                      </option>
                    ))}
                  </select>
                )}
              </div>
            </div>

            <div className="rounded-md border border-slate-200 p-3">
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
                Reporting Hierarchy (cycle-safe - checked server-side)
              </p>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                <PostPicker
                  label="Operational Reporting"
                  value={form.operationalReportingPostId}
                  options={reportingPostOptions}
                  onChange={(v) => setForm({ ...form, operationalReportingPostId: v })}
                />
                <PostPicker
                  label="Administrative Reporting"
                  value={form.administrativeReportingPostId}
                  options={reportingPostOptions}
                  onChange={(v) => setForm({ ...form, administrativeReportingPostId: v })}
                />
                <PostPicker
                  label="Accepting Authority"
                  value={form.acceptingAuthorityPostId}
                  options={reportingPostOptions}
                  onChange={(v) => setForm({ ...form, acceptingAuthorityPostId: v })}
                />
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-4">
              <label className="flex items-center gap-2 text-sm text-slate-700">
                <input type="checkbox" checked={form.isBudgeted} onChange={(e) => setForm({ ...form, isBudgeted: e.target.checked })} />
                Budgeted
              </label>
              <label className="flex items-center gap-2 text-sm text-slate-700">
                <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
                Active
              </label>
            </div>

            <PrimaryButton type="submit" disabled={saveMutation.isPending || !formValid} className="w-full justify-center">
              Save Post
            </PrimaryButton>
          </form>
        </Modal>
      )}

      {historyPost && (
        <Modal title={`${historyPost.postCode} - Incumbency History`} onClose={() => setHistoryPost(null)} maxWidthClassName="max-w-2xl">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
            <div className="flex flex-wrap gap-2">
              <Badge tone={VACANCY_TONE[historyPost.vacancyStatus]}>{historyPost.vacancyStatus}</Badge>
              <span className="text-sm text-slate-600">{historyPost.title}</span>
            </div>
            <SecondaryButton onClick={() => openAssignCharge(historyPost)}>
              <UserPlus size={14} /> Assign Additional Charge
            </SecondaryButton>
          </div>

          <div className="mb-4 rounded-md border border-slate-200 p-3">
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Change Status</p>
            <input
              value={statusRemark}
              onChange={(e) => setStatusRemark(e.target.value)}
              placeholder="Audit remark (reason for this status change)"
              className="mb-2 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            <div className="flex flex-wrap gap-2">
              {historyPost.vacancyStatus !== 'FROZEN' && historyPost.vacancyStatus !== 'ABOLISHED' && (
                <SecondaryButton
                  onClick={() => statusMutation.mutate({ postId: historyPost.id, targetStatus: 'FROZEN' })}
                  disabled={statusMutation.isPending}
                >
                  <Snowflake size={14} /> Freeze
                </SecondaryButton>
              )}
              {historyPost.vacancyStatus === 'FROZEN' && (
                <SecondaryButton
                  onClick={() => statusMutation.mutate({ postId: historyPost.id, targetStatus: 'VACANT' })}
                  disabled={statusMutation.isPending}
                >
                  Unfreeze
                </SecondaryButton>
              )}
              {historyPost.vacancyStatus !== 'ABOLISHED' && (
                <SecondaryButton
                  onClick={() => statusMutation.mutate({ postId: historyPost.id, targetStatus: 'ABOLISHED' })}
                  disabled={statusMutation.isPending}
                  className="border-red-200 text-red-600 hover:bg-red-50"
                >
                  <Ban size={14} /> Abolish
                </SecondaryButton>
              )}
            </div>
            {statusMutation.isError && (
              <FormErrorBanner title="Could not update status" errors={describeApiErrorList(statusMutation.error, 'Could not update status.')} />
            )}
          </div>

          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Chronological Timeline</p>
          {historyQuery.isLoading && <LoadingState label="Loading history..." />}
          {historyQuery.data && historyQuery.data.length === 0 && <EmptyState message="No incumbency records yet." />}
          {historyQuery.data && historyQuery.data.length > 0 && (
            <ul className="space-y-2">
              {historyQuery.data.map((entry) => (
                <li key={entry.id} className="flex items-center justify-between rounded-md border border-slate-200 p-3 text-sm">
                  <div>
                    <p className="font-medium text-slate-700">
                      {entry.employeeCode}
                      {entry.active && <Badge tone="success" className="ml-2">Current</Badge>}
                    </p>
                    <p className="text-xs text-slate-500">
                      {entry.assignmentType} - {formatDate(entry.startDate)} to {entry.endDate ? formatDate(entry.endDate) : 'present'}
                      {entry.orderReference && ` - Order: ${entry.orderReference}`}
                    </p>
                  </div>
                  {entry.active && (
                    <SecondaryButton onClick={() => relieveMutation.mutate(entry.id)} disabled={relieveMutation.isPending}>
                      Relieve
                    </SecondaryButton>
                  )}
                </li>
              ))}
            </ul>
          )}
        </Modal>
      )}

      {assignChargePost && (
        <Modal title={`${assignChargePost.postCode} - Assign Additional Charge`} onClose={closeAssignCharge} maxWidthClassName="max-w-lg">
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault()
              assignChargeMutation.mutate()
            }}
          >
            {assignChargeMutation.isError && (
              <FormErrorBanner title="Could not assign incumbency" errors={describeApiErrorList(assignChargeMutation.error, 'Could not assign incumbency.')} />
            )}
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Employee</label>
              <input
                value={chargeForm.employeeSearch}
                onChange={(e) => setChargeForm({ ...chargeForm, employeeSearch: e.target.value, employeeId: '' })}
                placeholder="Search by name or employee code..."
                className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(chargeFieldErrors?.employeeId))}`}
              />
              {chargeForm.employeeSearch && !chargeForm.employeeId && (
                <div className="mt-1 max-h-40 overflow-y-auto rounded-md border border-slate-200">
                  {employeeMatches.length === 0 && <p className="p-2 text-xs text-slate-400">No matches.</p>}
                  {employeeMatches.slice(0, 20).map((emp) => (
                    <button
                      key={emp.id}
                      type="button"
                      onClick={() => setChargeForm({ ...chargeForm, employeeId: String(emp.id), employeeSearch: `${emp.employeeCode} - ${emp.fullName}` })}
                      className="block w-full px-2 py-1.5 text-left text-sm hover:bg-slate-50"
                    >
                      {emp.employeeCode} - {emp.fullName}
                    </button>
                  ))}
                </div>
              )}
              <FieldError message={chargeFieldErrors?.employeeId} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Assignment Type</label>
              <select
                value={chargeForm.assignmentType}
                onChange={(e) => setChargeForm({ ...chargeForm, assignmentType: e.target.value as AssignmentType })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="ADDITIONAL_CHARGE">Additional Charge</option>
                <option value="ACTING">Acting</option>
                <option value="LOOK_AFTER">Look After</option>
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Start Date</label>
              <input
                required
                type="date"
                value={chargeForm.startDate}
                onChange={(e) => setChargeForm({ ...chargeForm, startDate: e.target.value })}
                className={`w-full rounded-md border border-slate-300 px-3 py-2 text-sm ${errorInputClass(Boolean(chargeFieldErrors?.startDate))}`}
              />
              <FieldError message={chargeFieldErrors?.startDate} />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Order Reference</label>
                <input
                  value={chargeForm.orderReference}
                  onChange={(e) => setChargeForm({ ...chargeForm, orderReference: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Order Date</label>
                <input
                  type="date"
                  value={chargeForm.orderDate}
                  onChange={(e) => setChargeForm({ ...chargeForm, orderDate: e.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
            </div>
            <PrimaryButton
              type="submit"
              disabled={assignChargeMutation.isPending || !chargeForm.employeeId || !chargeForm.startDate}
              className="w-full justify-center"
            >
              Assign
            </PrimaryButton>
          </form>
        </Modal>
      )}
    </div>
  )
}

function KpiCard({ label, value, tone }: { label: string; value: number | undefined; tone: 'brand' | 'success' | 'warning' | 'neutral' }) {
  const tones: Record<string, string> = {
    brand: 'border-brand-forest/30 bg-brand-forest/5',
    success: 'border-emerald-200 bg-emerald-50',
    warning: 'border-brand-jute/40 bg-brand-jute/10',
    neutral: 'border-slate-200 bg-slate-50',
  }
  return (
    <div className={`rounded-xl border p-4 ${tones[tone]}`}>
      <p className="text-xs font-medium text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-slate-800">{value ?? '—'}</p>
    </div>
  )
}

function PostPicker({
  label,
  value,
  options,
  onChange,
}: {
  label: string
  value: string
  options: PostMasterResponse[]
  onChange: (value: string) => void
}) {
  const [query, setQuery] = useState('')
  const filtered = query.trim()
    ? options.filter(
        (p) => p.postCode.toLowerCase().includes(query.toLowerCase()) || p.title.toLowerCase().includes(query.toLowerCase()),
      )
    : options
  return (
    <div>
      <label className="mb-1 block text-xs font-medium text-slate-600">{label}</label>
      <input
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Filter..."
        className="mb-1 w-full rounded-md border border-slate-300 px-2 py-1 text-xs"
      />
      <select value={value} onChange={(e) => onChange(e.target.value)} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm">
        <option value="">None</option>
        {filtered.map((p) => (
          <option key={p.id} value={p.id}>
            {p.postCode} - {p.title}
          </option>
        ))}
      </select>
    </div>
  )
}
