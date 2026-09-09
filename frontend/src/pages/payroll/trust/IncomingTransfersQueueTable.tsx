import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, XCircle } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { useAuth } from '../../../auth/AuthContext'
import { useToast } from '../../../components/common/ToastProvider'
import { Modal } from '../../../components/common/Modal'
import { DatePicker } from '../../../components/common/DatePicker'
import { formatDate } from '../../../lib/date'
import { describeApiError } from '../../../lib/apiError'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PrimaryButton, SecondaryButton } from '../../../components/common/ui'
import type { CreditLedgerRequest, IncomingFundTransferResponse, IncomingTransferStatus, Page, RejectRemarksRequest } from '../../../types/api'

const STATUS_TABS: { value: IncomingTransferStatus; label: string; tone: 'warning' | 'success' | 'neutral' }[] = [
  { value: 'SUBMITTED', label: 'Submitted', tone: 'warning' },
  { value: 'CREDITED_TO_LEDGER', label: 'Credited', tone: 'success' },
  { value: 'REJECTED', label: 'Rejected', tone: 'neutral' },
]

function statusTone(status: IncomingTransferStatus): 'warning' | 'success' | 'neutral' {
  return STATUS_TABS.find((t) => t.value === status)?.tone ?? 'neutral'
}

/** Confirmation summary + Bank Realization Date picker before crediting the Trust ledger - PUT .../credit-ledger. */
function CreditLedgerModal({ transfer, onClose }: { transfer: IncomingFundTransferResponse; onClose: () => void }) {
  const { show } = useToast()
  const { employeeId } = useAuth()
  const queryClient = useQueryClient()
  const [bankRealizationDate, setBankRealizationDate] = useState(transfer.bankRealizationDate)
  const [remarks, setRemarks] = useState('')

  const mutation = useMutation({
    mutationFn: async () => {
      const payload: CreditLedgerRequest = { trustOfficerId: employeeId ?? 0, remarks: remarks || null }
      return (await apiClient.put(`/v1/payroll/trust/incoming-transfers/${transfer.id}/credit-ledger`, payload)).data
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'Transfer credited to the CPF Trust ledger.' })
      queryClient.invalidateQueries({ queryKey: ['incoming-fund-transfers'] })
      onClose()
    },
  })

  return (
    <Modal title={`Verify & Credit - ${transfer.transferReferenceNo}`} onClose={onClose}>
      <div className="mb-3 space-y-1 rounded-md bg-slate-50 p-3 text-sm">
        <p><span className="text-slate-400">Employee: </span>{transfer.employeeName} ({transfer.employeeCode})</p>
        <p><span className="text-slate-400">Source: </span>{transfer.sourceOrganizationName}</p>
        <p><span className="text-slate-400">Total CPF Transferred: </span>{transfer.totalCpfTransferred.toFixed(2)}</p>
      </div>
      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault()
          mutation.mutate()
        }}
      >
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Bank Realization Date</label>
          <DatePicker value={bankRealizationDate} onChange={setBankRealizationDate} disabled />
          <p className="mt-1 text-[11px] text-slate-400">As submitted on the voucher - amend the voucher itself to change this before crediting.</p>
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">Verification Remarks (optional)</label>
          <textarea
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            rows={2}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <div className="flex gap-2">
          <SecondaryButton type="button" onClick={onClose} className="flex-1 justify-center">
            Cancel
          </SecondaryButton>
          <PrimaryButton type="submit" disabled={mutation.isPending} className="flex-1 justify-center">
            <CheckCircle2 size={14} /> Confirm Credit
          </PrimaryButton>
        </div>
        {mutation.isError && <ErrorState message={describeApiError(mutation.error, 'Could not credit the ledger.')} />}
      </form>
    </Modal>
  )
}

function RejectTransferModal({ transfer, onClose }: { transfer: IncomingFundTransferResponse; onClose: () => void }) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [remarks, setRemarks] = useState('')

  const mutation = useMutation({
    mutationFn: async () => {
      const payload: RejectRemarksRequest = { remarks }
      return (await apiClient.put(`/v1/payroll/trust/incoming-transfers/${transfer.id}/reject`, payload)).data
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'Transfer voucher rejected.' })
      queryClient.invalidateQueries({ queryKey: ['incoming-fund-transfers'] })
      onClose()
    },
  })

  return (
    <Modal title={`Reject - ${transfer.transferReferenceNo}`} onClose={onClose}>
      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault()
          if (remarks.trim().length === 0) return
          mutation.mutate()
        }}
      >
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">
            Rejection Reason <span className="text-red-500">*</span>
          </label>
          <textarea
            required
            autoFocus
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            rows={3}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <div className="flex gap-2">
          <SecondaryButton type="button" onClick={onClose} className="flex-1 justify-center">
            Cancel
          </SecondaryButton>
          <PrimaryButton type="submit" disabled={mutation.isPending || remarks.trim().length === 0} className="flex-1 justify-center bg-red-600 hover:bg-red-700">
            <XCircle size={14} /> Confirm Rejection
          </PrimaryButton>
        </div>
        {mutation.isError && <ErrorState message={describeApiError(mutation.error, 'Could not reject the voucher.')} />}
      </form>
    </Modal>
  )
}

/** Section 3 - the voucher queue with status filter tabs and Trust-officer actions. */
export function IncomingTransfersQueueTable() {
  const [statusFilter, setStatusFilter] = useState<IncomingTransferStatus>('SUBMITTED')
  const [crediting, setCrediting] = useState<IncomingFundTransferResponse | null>(null)
  const [rejecting, setRejecting] = useState<IncomingFundTransferResponse | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['incoming-fund-transfers', statusFilter],
    queryFn: async () =>
      (
        await apiClient.get<Page<IncomingFundTransferResponse>>('/v1/payroll/trust/incoming-transfers/all', {
          params: { status: statusFilter, size: 50 },
        })
      ).data,
  })

  return (
    <div>
      <div className="mb-3 flex gap-2">
        {STATUS_TABS.map((tab) => (
          <button
            key={tab.value}
            type="button"
            onClick={() => setStatusFilter(tab.value)}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${
              statusFilter === tab.value ? 'bg-brand-forest text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {isLoading && <LoadingState />}
      {isError && <ErrorState message="Could not load incoming transfer vouchers." />}
      {data && data.content.length === 0 && <EmptyState message={`No ${statusFilter.toLowerCase().replace(/_/g, ' ')} vouchers.`} />}

      <div className="space-y-2">
        {data?.content.map((t) => (
          <Card key={t.id} className="p-3">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="font-medium text-slate-800">
                  {t.transferReferenceNo} <span className="font-normal text-slate-400">- {t.employeeName} ({t.employeeCode})</span>
                </p>
                <p className="mt-0.5 text-xs text-slate-500">
                  {t.sourceOrganizationName} · Realized {formatDate(t.bankRealizationDate)} · Total {t.totalCpfTransferred.toFixed(2)}
                </p>
                {t.status === 'REJECTED' && t.rejectionRemarks && (
                  <p className="mt-1 text-xs text-red-600">Rejected: {t.rejectionRemarks}</p>
                )}
              </div>
              <div className="flex items-center gap-2">
                <Badge tone={statusTone(t.status)}>{t.status.replace(/_/g, ' ')}</Badge>
                {t.status === 'SUBMITTED' && (
                  <>
                    <SecondaryButton onClick={() => setRejecting(t)} className="border-red-300 text-red-600 hover:bg-red-50">
                      Reject
                    </SecondaryButton>
                    <PrimaryButton onClick={() => setCrediting(t)}>Verify &amp; Credit to Ledger</PrimaryButton>
                  </>
                )}
              </div>
            </div>
          </Card>
        ))}
      </div>

      {crediting && <CreditLedgerModal transfer={crediting} onClose={() => setCrediting(null)} />}
      {rejecting && <RejectTransferModal transfer={rejecting} onClose={() => setRejecting(null)} />}
    </div>
  )
}
