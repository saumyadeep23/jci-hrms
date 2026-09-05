import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Circle, XCircle } from 'lucide-react'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { useToast } from '../common/ToastProvider'
import { describeApiErrorList } from '../../lib/apiError'
import { DatePicker } from '../common/DatePicker'
import { Modal } from '../common/Modal'
import { Badge, PrimaryButton, SecondaryButton } from '../common/ui'
import { TerminalSettlementModal } from './TerminalSettlementModal'
import type {
  ExitClearanceDepartment,
  ExitClearanceItemResponse,
  ExitClearanceItemStatus,
  ExitClearanceRequestResponse,
  SeparationType,
} from '../../types/api'

const SEPARATION_TYPES: SeparationType[] = ['SUPERANNUATION', 'RESIGNATION', 'VRS', 'DECEASED', 'TERMINATED']

const ITEM_STATUS_ICON: Record<ExitClearanceItemStatus, React.ReactNode> = {
  PENDING: <Circle size={14} className="text-slate-300" />,
  CLEARED: <CheckCircle2 size={14} className="text-emerald-500" />,
  REJECTED_WITH_DUES: <XCircle size={14} className="text-red-500" />,
}

/** Exit Formalities: 7-department clearance checklist -> release order -> hands off to the Terminal Settlement sheet. */
export function ExitClearanceModal({
  employeeId,
  employeeName,
  onClose,
}: {
  employeeId: number
  employeeName: string
  onClose: () => void
}) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [separationType, setSeparationType] = useState<SeparationType>('SUPERANNUATION')
  const [targetReleaseDate, setTargetReleaseDate] = useState('')
  const [remarks, setRemarks] = useState('')
  const [orderRef, setOrderRef] = useState('')
  const [releaseDate, setReleaseDate] = useState('')
  const [showSettlement, setShowSettlement] = useState(false)

  const requestQuery = useQuery({
    queryKey: ['exit-clearance', employeeId],
    queryFn: async () => (await apiClient.get<ExitClearanceRequestResponse>(`/v1/exit-clearances/by-employee/${employeeId}`)).data,
    retry: false,
  })

  const initiateMutation = useMutation({
    mutationFn: async () =>
      (
        await apiClient.post<ExitClearanceRequestResponse>('/v1/exit-clearances', {
          employeeId,
          separationType,
          targetReleaseDate: formatDate(targetReleaseDate),
          remarks: remarks || null,
        })
      ).data,
    onSuccess: () => {
      show({ tone: 'success', message: 'Exit clearance initiated - 7 department checklist created.' })
      queryClient.invalidateQueries({ queryKey: ['exit-clearance', employeeId] })
    },
    onError: (error) => show({ tone: 'error', message: describeApiErrorList(error, 'Failed to initiate exit clearance.').join('; ') }),
  })

  const updateItemMutation = useMutation({
    mutationFn: async ({ itemId, status, duesRecoveryAmount, itemRemarks }: { itemId: number; status: ExitClearanceItemStatus; duesRecoveryAmount: number | null; itemRemarks: string }) =>
      (await apiClient.put<ExitClearanceItemResponse>(`/v1/exit-clearances/items/${itemId}`, { status, duesRecoveryAmount, remarks: itemRemarks || null })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['exit-clearance', employeeId] }),
    onError: (error) => show({ tone: 'error', message: describeApiErrorList(error, 'Failed to update department clearance.').join('; ') }),
  })

  const finalizeMutation = useMutation({
    mutationFn: async () =>
      (
        await apiClient.post<ExitClearanceRequestResponse>(`/v1/exit-clearances/${requestQuery.data!.id}/finalize`, {
          releaseOrderRefNo: orderRef,
          releaseOrderDate: formatDate(releaseDate),
        })
      ).data,
    onSuccess: () => {
      show({ tone: 'success', message: 'Release order issued - employee released.' })
      queryClient.invalidateQueries({ queryKey: ['exit-clearance', employeeId] })
    },
    onError: (error) => show({ tone: 'error', message: describeApiErrorList(error, 'Failed to finalize release order.').join('; ') }),
  })

  const request = requestQuery.data

  return (
    <>
      <Modal title={`Exit Formalities - ${employeeName}`} onClose={onClose} maxWidthClassName="max-w-2xl">
        {requestQuery.isLoading && <p className="py-6 text-center text-sm text-slate-400">Loading...</p>}

        {!requestQuery.isLoading && !request && (
          <div className="space-y-3">
            <p className="text-sm text-slate-500">No exit clearance request exists for this employee yet.</p>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Separation Type</label>
                <select value={separationType} onChange={(e) => setSeparationType(e.target.value as SeparationType)} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm">
                  {SEPARATION_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">Target Release Date</label>
                <DatePicker value={targetReleaseDate} onChange={setTargetReleaseDate} />
              </div>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Remarks</label>
              <textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} rows={2} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
            </div>
            <div className="flex justify-end">
              <PrimaryButton onClick={() => initiateMutation.mutate()} disabled={!targetReleaseDate || initiateMutation.isPending}>
                {initiateMutation.isPending ? 'Initiating...' : 'Initiate Exit Clearance'}
              </PrimaryButton>
            </div>
          </div>
        )}

        {request && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-xs text-slate-500">
                {request.separationType} · Target Release {formatDate(request.targetReleaseDate)}
              </span>
              <Badge tone={request.status === 'RELEASE_ORDER_ISSUED' ? 'success' : request.status === 'CANCELLED' ? 'danger' : 'neutral'}>
                {request.status.replaceAll('_', ' ')}
              </Badge>
            </div>

            <ul className="divide-y divide-slate-100 rounded-md border border-slate-200">
              {request.items.map((item) => (
                <ClearanceItemRow
                  key={item.id}
                  item={item}
                  disabled={request.status === 'RELEASE_ORDER_ISSUED' || request.status === 'CANCELLED'}
                  onSave={(status, dues, itemRemarks) => updateItemMutation.mutate({ itemId: item.id, status, duesRecoveryAmount: dues, itemRemarks })}
                />
              ))}
            </ul>

            {request.status === 'CLEARANCES_COMPLETED' && (
              <div className="space-y-2 rounded-md border border-emerald-200 bg-emerald-50/60 p-3">
                <p className="text-xs font-medium text-emerald-700">All departments cleared - issue the release order to finalize.</p>
                <div className="grid grid-cols-2 gap-3">
                  <input
                    value={orderRef}
                    onChange={(e) => setOrderRef(e.target.value)}
                    placeholder="Release Order Ref No."
                    className="rounded-md border border-slate-300 px-3 py-2 text-sm"
                  />
                  <DatePicker value={releaseDate} onChange={setReleaseDate} />
                </div>
                <div className="flex justify-end">
                  <PrimaryButton onClick={() => finalizeMutation.mutate()} disabled={!orderRef || !releaseDate || finalizeMutation.isPending}>
                    {finalizeMutation.isPending ? 'Finalizing...' : 'Finalize Release Order'}
                  </PrimaryButton>
                </div>
              </div>
            )}

            {request.status === 'RELEASE_ORDER_ISSUED' && (
              <div className="flex items-center justify-between rounded-md border border-slate-200 bg-slate-50 p-3">
                <span className="text-xs text-slate-600">
                  Released via order {request.releaseOrderRefNo} on {formatDate(request.releaseOrderDate ?? '')}
                </span>
                <SecondaryButton onClick={() => setShowSettlement(true)}>Open Terminal Settlement Sheet</SecondaryButton>
              </div>
            )}
          </div>
        )}
      </Modal>

      {showSettlement && request && (
        <TerminalSettlementModal
          employeeId={employeeId}
          employeeName={employeeName}
          separationType={request.separationType}
          separationDate={request.releaseOrderDate ?? request.targetReleaseDate}
          clearanceRequestId={request.id}
          onClose={() => setShowSettlement(false)}
        />
      )}
    </>
  )
}

function ClearanceItemRow({
  item,
  disabled,
  onSave,
}: {
  item: ExitClearanceItemResponse
  disabled: boolean
  onSave: (status: ExitClearanceItemStatus, dues: number | null, remarks: string) => void
}) {
  const [status, setStatus] = useState<ExitClearanceItemStatus>(item.status)
  const [dues, setDues] = useState(item.duesRecoveryAmount != null ? String(item.duesRecoveryAmount) : '')
  const [remarks, setRemarks] = useState(item.remarks ?? '')
  const dirty = status !== item.status || dues !== (item.duesRecoveryAmount != null ? String(item.duesRecoveryAmount) : '') || remarks !== (item.remarks ?? '')

  return (
    <li className="flex flex-wrap items-center gap-2 p-2.5">
      <span className="flex w-40 items-center gap-1.5 text-sm font-medium text-slate-700">
        {ITEM_STATUS_ICON[item.status]} {DEPARTMENT_LABEL[item.departmentCode]}
      </span>
      <select value={status} onChange={(e) => setStatus(e.target.value as ExitClearanceItemStatus)} disabled={disabled} className="rounded-md border border-slate-300 px-2 py-1 text-xs">
        <option value="PENDING">Pending</option>
        <option value="CLEARED">Cleared</option>
        <option value="REJECTED_WITH_DUES">Rejected (Dues)</option>
      </select>
      {status === 'REJECTED_WITH_DUES' && (
        <input
          value={dues}
          onChange={(e) => setDues(e.target.value)}
          disabled={disabled}
          placeholder="Dues ₹"
          className="w-24 rounded-md border border-slate-300 px-2 py-1 text-xs"
        />
      )}
      <input
        value={remarks}
        onChange={(e) => setRemarks(e.target.value)}
        disabled={disabled}
        placeholder="Remarks"
        className="min-w-[120px] flex-1 rounded-md border border-slate-300 px-2 py-1 text-xs"
      />
      {!disabled && (
        <button
          type="button"
          disabled={!dirty}
          onClick={() => onSave(status, status === 'REJECTED_WITH_DUES' ? Number(dues) || 0 : 0, remarks)}
          className="rounded-md bg-brand-forest px-2.5 py-1 text-xs font-medium text-white disabled:opacity-30"
        >
          Save
        </button>
      )}
    </li>
  )
}

const DEPARTMENT_LABEL: Record<ExitClearanceDepartment, string> = {
  ESTABLISHMENT: 'Establishment',
  VIGILANCE: 'Vigilance',
  ESTATE: 'Estate',
  IT: 'IT',
  FINANCE: 'Finance',
  STORES: 'Stores',
  CPF_TRUST: 'CPF Trust',
}
