import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { CheckCircle2, FileText, HelpCircle, MapPin, Plus, XCircle } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { describeApiErrorList } from '../../../lib/apiError'
import { formatDate } from '../../../lib/date'
import { openPdf } from '../../../lib/pdfDownload'
import { DatePicker } from '../../../components/common/DatePicker'
import { Modal } from '../../../components/common/Modal'
import { FormErrorBanner } from '../../../components/common/FormErrorBanner'
import { AddMovementOrderModal } from '../../../components/pims/AddMovementOrderModal'
import {
  Badge,
  Card,
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  PrimaryButton,
  SecondaryButton,
} from '../../../components/common/ui'
import type {
  ClarificationRequest,
  EmployeeMovementRecordResponse,
  JoiningDecisionRequest,
  MovementReleaseRequest,
  Page,
  PayrollMovementInputResponse,
  SessionType,
} from '../../../types/api'

const TABS = [
  { key: 'orders', label: 'Movement Orders' },
  { key: 'releases', label: 'Pending Releases' },
  { key: 'joining', label: 'Joining Verifications' },
  { key: 'payroll', label: 'Payroll & LPC Clearance' },
] as const
type TabKey = (typeof TABS)[number]['key']
function isTabKey(value: string | null): value is TabKey {
  return value !== null && (TABS as readonly { key: string }[]).some((t) => t.key === value)
}

function statusTone(status: string): 'neutral' | 'success' | 'warning' | 'danger' | 'brand' {
  if (status === 'JOINED' || status === 'ACCEPTED' || status === 'LPC_ACCEPTED') return 'success'
  if (status === 'RELIEVED' || status === 'PENDING_VERIFICATION' || status === 'LPC_ISSUED') return 'brand'
  if (status === 'REJECTED') return 'danger'
  if (status === 'ORDERED') return 'warning'
  return 'neutral'
}

export function MovementManagementPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const requestedTab = searchParams.get('tab')
  const [tab, setTab] = useState<TabKey>(isTabKey(requestedTab) ? requestedTab : 'orders')

  function selectTab(next: TabKey) {
    setTab(next)
    setSearchParams({ tab: next }, { replace: true })
  }

  return (
    <div>
      <PageHeader title="Transfers, Promotions & Joining Reports" description="Movement order lifecycle: release, joining verification, and payroll/LPC clearance" />

      <div className="mb-6 flex gap-1 overflow-x-auto border-b border-slate-200">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => selectTab(t.key)}
            className={`whitespace-nowrap border-b-2 px-4 py-2 text-sm font-medium transition-colors ${
              tab === t.key ? 'border-brand-forest text-brand-forest' : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'orders' && <MovementOrdersTab />}
      {tab === 'releases' && <PendingReleasesTab />}
      {tab === 'joining' && <JoiningVerificationsTab />}
      {tab === 'payroll' && <PayrollLpcTab />}
    </div>
  )
}

// ==================== Tab 1: Movement Orders ====================

function MovementOrdersTab() {
  const [drawerOpen, setDrawerOpen] = useState(false)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['movement-records'],
    queryFn: async () => (await apiClient.get<Page<EmployeeMovementRecordResponse>>('/v1/pims/movements/records', { params: { size: 200 } })).data,
  })

  return (
    <div>
      <div className="mb-4 flex justify-end">
        <PrimaryButton onClick={() => setDrawerOpen(true)}>
          <Plus size={15} /> Add Movement Order
        </PrimaryButton>
      </div>

      <Card>
        {isLoading && <LoadingState label="Loading movement orders..." />}
        {isError && <ErrorState message="Could not load movement orders." />}
        {data && data.content.length === 0 && <EmptyState message="No movement orders yet." />}

        {data && data.content.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Employee</th>
                  <th className="py-2 pr-3">Order</th>
                  <th className="py-2 pr-3">From &rarr; To</th>
                  <th className="py-2 pr-3">Movement</th>
                  <th className="py-2 pr-3">Joining</th>
                  <th className="py-2 pl-3 text-right">Documents</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((row) => (
                  <tr key={row.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3">
                      <span className="font-medium">{row.employeeName}</span>
                      <span className="block text-xs text-slate-400">{row.employeeCode}</span>
                    </td>
                    <td className="py-2 pr-3">
                      <span>{row.orderType.replace(/_/g, ' ')}</span>
                      <span className="block text-xs text-slate-400">{row.orderRefNo}</span>
                    </td>
                    <td className="py-2 pr-3 text-xs">
                      {row.fromDpcName ?? row.fromOfficeName} &rarr; {row.toDpcName ?? row.toOfficeName}
                    </td>
                    <td className="py-2 pr-3">
                      <Badge tone={statusTone(row.movementStatus)}>{row.movementStatus}</Badge>
                    </td>
                    <td className="py-2 pr-3">
                      <Badge tone={statusTone(row.joiningStatus)}>{row.joiningStatus.replace(/_/g, ' ')}</Badge>
                    </td>
                    <td className="py-2 pl-3 text-right">
                      <SecondaryButton onClick={() => openPdf(`/v1/pims/movements/orders/${row.orderId}/pdf`)}>
                        <FileText size={13} /> Order PDF
                      </SecondaryButton>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {drawerOpen && <AddMovementOrderModal onClose={() => setDrawerOpen(false)} />}
    </div>
  )
}

// ==================== Tab 2: Pending Releases ====================

function PendingReleasesTab() {
  const queryClient = useQueryClient()
  const [releasing, setReleasing] = useState<EmployeeMovementRecordResponse | null>(null)
  const [releaseForm, setReleaseForm] = useState<{ releaseOrderRef: string; releaseDate: string; releaseSession: SessionType }>({
    releaseOrderRef: '',
    releaseDate: '',
    releaseSession: 'FORENOON',
  })

  const { data, isLoading, isError } = useQuery({
    queryKey: ['movement-pending-releases'],
    queryFn: async () => (await apiClient.get<EmployeeMovementRecordResponse[]>('/v1/pims/movements/records/pending-release')).data,
  })

  const { data: allRecords } = useQuery({
    queryKey: ['movement-records'],
    queryFn: async () => (await apiClient.get<Page<EmployeeMovementRecordResponse>>('/v1/pims/movements/records', { params: { size: 200 } })).data,
  })
  const releasedRows = (allRecords?.content ?? []).filter((row) => row.releaseDate !== null)

  const releaseMutation = useMutation({
    mutationFn: async () => {
      const payload: MovementReleaseRequest = releaseForm
      return (await apiClient.patch(`/v1/pims/movements/records/${releasing!.id}/release`, payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['movement-pending-releases'] })
      queryClient.invalidateQueries({ queryKey: ['movement-records'] })
      setReleasing(null)
      setReleaseForm({ releaseOrderRef: '', releaseDate: '', releaseSession: 'FORENOON' })
    },
  })

  return (
    <>
      <Card className="mb-4">
        {isLoading && <LoadingState label="Loading pending releases..." />}
        {isError && <ErrorState message="Could not load pending releases." />}
        {data && data.length === 0 && <EmptyState message="No movements awaiting release." />}

        {data && data.length > 0 && (
          <div className="space-y-2">
            {data.map((row) => (
              <div key={row.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-slate-200 p-3">
                <div>
                  <p className="text-sm font-medium text-slate-800">
                    {row.employeeName} <span className="text-slate-400">({row.employeeCode})</span>
                  </p>
                  <p className="text-xs text-slate-500">
                    {row.fromOfficeName} &rarr; {row.toOfficeName} &middot; Order {row.orderRefNo}
                  </p>
                </div>
                <SecondaryButton onClick={() => setReleasing(row)}>Release</SecondaryButton>
              </div>
            ))}
          </div>
        )}
      </Card>

      {releasedRows.length > 0 && (
        <Card>
          <p className="mb-3 text-xs font-semibold uppercase text-slate-400">Released</p>
          <div className="space-y-2">
            {releasedRows.map((row) => (
              <div key={row.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-slate-200 p-3">
                <div>
                  <p className="text-sm font-medium text-slate-800">
                    {row.employeeName} <span className="text-slate-400">({row.employeeCode})</span>
                  </p>
                  <p className="text-xs text-slate-500">
                    Released {row.releaseDate ? formatDate(row.releaseDate) : '—'} ({row.releaseSession}) &middot; Ref {row.releaseOrderRef}
                  </p>
                </div>
                <SecondaryButton onClick={() => openPdf(`/v1/pims/movements/records/${row.id}/release/pdf`)}>
                  <FileText size={13} /> Release Order PDF
                </SecondaryButton>
              </div>
            ))}
          </div>
        </Card>
      )}

      {releasing && (
        <Modal title={`Release ${releasing.employeeName}`} onClose={() => setReleasing(null)}>
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault()
              releaseMutation.mutate()
            }}
          >
            {releaseMutation.isError && (
              <FormErrorBanner title="Could not release" errors={describeApiErrorList(releaseMutation.error, 'Could not release.')} />
            )}
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Release Order Ref</label>
              <input
                required
                value={releaseForm.releaseOrderRef}
                onChange={(e) => setReleaseForm({ ...releaseForm, releaseOrderRef: e.target.value })}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Release Date</label>
              <DatePicker value={releaseForm.releaseDate} onChange={(v) => setReleaseForm({ ...releaseForm, releaseDate: v })} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Session</label>
              <div className="flex gap-2">
                {(['FORENOON', 'AFTERNOON'] as SessionType[]).map((session) => (
                  <button
                    key={session}
                    type="button"
                    onClick={() => setReleaseForm({ ...releaseForm, releaseSession: session })}
                    className={`flex-1 rounded-md border px-3 py-2 text-xs font-medium ${
                      releaseForm.releaseSession === session ? 'border-brand-forest bg-brand-forest text-white' : 'border-slate-300 text-slate-600'
                    }`}
                  >
                    {session === 'FORENOON' ? 'Forenoon (FN)' : 'Afternoon (AN)'}
                  </button>
                ))}
              </div>
            </div>
            <PrimaryButton
              type="submit"
              disabled={releaseMutation.isPending || releaseForm.releaseOrderRef.trim() === '' || releaseForm.releaseDate === ''}
              className="w-full justify-center"
            >
              Confirm Release
            </PrimaryButton>
          </form>
        </Modal>
      )}
    </>
  )
}

// ==================== Tab 3: Joining Verifications ====================

function JoiningVerificationsTab() {
  const queryClient = useQueryClient()
  const [clarifying, setClarifying] = useState<EmployeeMovementRecordResponse | null>(null)
  const [clarificationRemarks, setClarificationRemarks] = useState('')

  const { data, isLoading, isError } = useQuery({
    queryKey: ['movement-pending-joining'],
    queryFn: async () => (await apiClient.get<EmployeeMovementRecordResponse[]>('/v1/pims/movements/records/pending-joining')).data,
  })

  const decisionMutation = useMutation({
    mutationFn: async ({ id, decision }: { id: number; decision: JoiningDecisionRequest }) =>
      (await apiClient.post(`/v1/pims/movements/records/${id}/joining-report/decision`, decision)).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['movement-pending-joining'] })
      queryClient.invalidateQueries({ queryKey: ['movement-records'] })
    },
  })

  const clarificationMutation = useMutation({
    mutationFn: async () => {
      const payload: ClarificationRequest = { remarks: clarificationRemarks }
      return (await apiClient.post(`/v1/pims/movements/records/${clarifying!.id}/joining-report/request-clarification`, payload)).data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['movement-pending-joining'] })
      queryClient.invalidateQueries({ queryKey: ['movement-records'] })
      setClarifying(null)
      setClarificationRemarks('')
    },
  })

  return (
    <Card>
      {isLoading && <LoadingState label="Loading joining verifications..." />}
      {isError && <ErrorState message="Could not load joining verifications." />}
      {data && data.length === 0 && <EmptyState message="No joining reports awaiting verification." />}

      {data && data.length > 0 && (
        <div className="space-y-3">
          {data.map((row) => (
            <div key={row.id} className="rounded-md border border-slate-200 p-3">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="text-sm font-medium text-slate-800">
                    {row.employeeName} <span className="text-slate-400">({row.employeeCode})</span>
                  </p>
                  <p className="text-xs text-slate-500">
                    Joined {row.joiningDate ? formatDate(row.joiningDate) : '—'} at {row.toOfficeName} &middot; Report {row.joiningReportNo}
                  </p>
                </div>
                <div className="flex items-center gap-1.5">
                  <Badge tone={row.joiningSession === 'FORENOON' ? 'success' : 'brand'}>
                    DB Session: {row.joiningSession === 'FORENOON' ? 'FN' : 'AN'}
                  </Badge>
                  <Badge tone={row.geoVerified ? 'success' : 'warning'}>
                    <MapPin size={11} className="mr-1 inline" />
                    {row.geoVerified ? 'GPS Verified' : 'GPS Unverified'}
                  </Badge>
                </div>
              </div>

              <div className="mt-2 grid grid-cols-4 gap-2 rounded-md bg-slate-50 p-2 text-center text-xs">
                <div>
                  <p className="text-slate-400">Admissible JT</p>
                  <p className="font-semibold text-slate-800">{row.admissibleJtDays}</p>
                </div>
                <div>
                  <p className="text-slate-400">Availed</p>
                  <p className="font-semibold text-slate-800">{row.joiningTimeAvailedDays}</p>
                </div>
                <div>
                  <p className="text-slate-400">Unavailed</p>
                  <p className="font-semibold text-slate-800">{row.unavailedJtDays}</p>
                </div>
                <div>
                  <p className="text-slate-400">Excess (LWP)</p>
                  <p className="font-semibold text-red-600">{row.excessTransitLwpDays}</p>
                </div>
              </div>

              {row.joiningRemarks && <p className="mt-2 text-xs text-slate-500">Remarks: {row.joiningRemarks}</p>}

              <div className="mt-3 flex justify-end gap-2">
                <SecondaryButton
                  onClick={() => decisionMutation.mutate({ id: row.id, decision: { approve: false, remarks: null } })}
                  disabled={decisionMutation.isPending}
                  className="text-red-600"
                >
                  <XCircle size={14} /> Reject
                </SecondaryButton>
                <SecondaryButton onClick={() => setClarifying(row)} disabled={decisionMutation.isPending}>
                  <HelpCircle size={14} /> Seek Clarification
                </SecondaryButton>
                <PrimaryButton
                  onClick={() => decisionMutation.mutate({ id: row.id, decision: { approve: true, remarks: null } })}
                  disabled={decisionMutation.isPending}
                >
                  <CheckCircle2 size={14} /> Approve &amp; Accept Charge
                </PrimaryButton>
              </div>
            </div>
          ))}
        </div>
      )}

      {clarifying && (
        <Modal title={`Seek Clarification - ${clarifying.employeeName}`} onClose={() => setClarifying(null)}>
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault()
              clarificationMutation.mutate()
            }}
          >
            {clarificationMutation.isError && (
              <FormErrorBanner title="Could not send" errors={describeApiErrorList(clarificationMutation.error, 'Could not send.')} />
            )}
            <p className="text-sm text-slate-600">
              This returns the joining report to {clarifying.employeeName} for amendment. They will see your remarks and can amend and
              resubmit the report.
            </p>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Remarks (required)</label>
              <textarea
                required
                rows={3}
                value={clarificationRemarks}
                onChange={(e) => setClarificationRemarks(e.target.value)}
                placeholder="e.g. GPS coordinates do not match the destination office - please resubmit from the correct location."
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <PrimaryButton
              type="submit"
              disabled={clarificationMutation.isPending || clarificationRemarks.trim() === ''}
              className="w-full justify-center"
            >
              Send for Clarification
            </PrimaryButton>
          </form>
        </Modal>
      )}
    </Card>
  )
}

// ==================== Tab 4: Payroll & LPC Clearance ====================

function PayrollLpcTab() {
  const queryClient = useQueryClient()
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['movement-payroll-inputs', year, month],
    queryFn: async () =>
      (await apiClient.get<PayrollMovementInputResponse[]>('/v1/pims/movements/payroll-inputs', { params: { year, month } })).data,
  })

  const acceptLpcMutation = useMutation({
    mutationFn: async (movementId: number) => (await apiClient.post(`/v1/pims/movements/records/${movementId}/lpc/accept`)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['movement-payroll-inputs'] }),
  })

  return (
    <div>
      <div className="mb-4 flex items-center gap-2">
        <select value={month} onChange={(e) => setMonth(Number(e.target.value))} className="rounded-md border border-slate-300 px-3 py-2 text-sm">
          {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
            <option key={m} value={m}>
              {new Date(2000, m - 1, 1).toLocaleString('en-US', { month: 'long' })}
            </option>
          ))}
        </select>
        <input
          type="number"
          value={year}
          onChange={(e) => setYear(Number(e.target.value))}
          className="w-24 rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </div>

      <Card>
        {isLoading && <LoadingState label="Loading payroll impact..." />}
        {isError && <ErrorState message="Could not load payroll movement inputs." />}
        {data && data.length === 0 && <EmptyState message="No payroll-impacting movements for this month." />}

        {data && data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[860px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-left text-slate-500">
                  <th className="py-2 pr-3">Employee</th>
                  <th className="py-2 pr-3">Releasing Office Days</th>
                  <th className="py-2 pr-3">Receiving Office Days</th>
                  <th className="py-2 pr-3">Revised HRA Tier</th>
                  <th className="py-2 pr-3">Revised Basic</th>
                  <th className="py-2 pr-3">Transit JT / LWP</th>
                  <th className="py-2 pr-3">LPC</th>
                  <th className="py-2 pl-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.map((row) => (
                  <tr key={row.id} className="border-b border-slate-100">
                    <td className="py-2 pr-3">
                      <span className="font-medium">{row.employeeName}</span>
                      <span className="block text-xs text-slate-400">{row.employeeCode}</span>
                    </td>
                    <td className="py-2 pr-3">
                      {row.releasingOfficeDays} <span className="text-xs text-slate-400">({row.releasingOfficeName ?? '—'})</span>
                    </td>
                    <td className="py-2 pr-3">
                      {row.receivingOfficeDays} <span className="text-xs text-slate-400">({row.receivingOfficeName ?? '—'})</span>
                    </td>
                    <td className="py-2 pr-3">{row.revisedHraTier ?? '—'}</td>
                    <td className="py-2 pr-3">{row.revisedBasicPay != null ? `₹${row.revisedBasicPay.toLocaleString('en-IN')}` : '—'}</td>
                    <td className="py-2 pr-3">
                      {row.transitJtDays} / <span className="text-red-600">{row.transitLwpDays}</span>
                    </td>
                    <td className="py-2 pr-3">
                      <Badge tone={statusTone(row.payrollSyncStatus)}>{row.payrollSyncStatus.replace(/_/g, ' ')}</Badge>
                      {row.lpcNumber && <span className="block text-xs text-slate-400">{row.lpcNumber}</span>}
                    </td>
                    <td className="py-2 pl-3 text-right">
                      <div className="flex justify-end gap-2">
                        <SecondaryButton onClick={() => openPdf(`/v1/pims/movements/records/${row.movementId}/lpc/pdf`)}>
                          <FileText size={13} /> Generate / Download LPC
                        </SecondaryButton>
                        {row.payrollSyncStatus === 'LPC_ISSUED' && (
                          <SecondaryButton onClick={() => acceptLpcMutation.mutate(row.movementId)} disabled={acceptLpcMutation.isPending}>
                            Accept LPC
                          </SecondaryButton>
                        )}
                      </div>
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
