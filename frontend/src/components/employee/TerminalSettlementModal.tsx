import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Printer, Trash2 } from 'lucide-react'
import { apiClient } from '../../api/client'
import { formatDate } from '../../lib/date'
import { useToast } from '../common/ToastProvider'
import { describeApiErrorList } from '../../lib/apiError'
import { Modal } from '../common/Modal'
import { Badge, PrimaryButton, SecondaryButton } from '../common/ui'
import type {
  BeneficiaryType,
  SeparationType,
  TerminalSettlementBeneficiaryRequest,
  TerminalSettlementBeneficiaryResponse,
  TerminalSettlementGenerateRequest,
  TerminalSettlementResponse,
} from '../../types/api'

function money(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return `₹${value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

type LocalBeneficiary = {
  beneficiaryType: BeneficiaryType
  beneficiaryName: string
  relationship: string
  sharePercentage: string
  allocatedAmount: string
  bankAccountNo: string
  bankIfsc: string
  bankName: string
  panNumber: string
}

const EMPTY_BENEFICIARY: LocalBeneficiary = {
  beneficiaryType: 'NOMINEE',
  beneficiaryName: '',
  relationship: '',
  sharePercentage: '',
  allocatedAmount: '',
  bankAccountNo: '',
  bankIfsc: '',
  bankName: '',
  panNumber: '',
}

function toRequest(b: LocalBeneficiary): TerminalSettlementBeneficiaryRequest {
  return {
    beneficiaryType: b.beneficiaryType,
    beneficiaryName: b.beneficiaryName,
    relationship: b.relationship,
    sharePercentage: Number(b.sharePercentage) || 0,
    allocatedAmount: Number(b.allocatedAmount) || 0,
    bankAccountNo: b.bankAccountNo,
    bankIfsc: b.bankIfsc,
    bankName: b.bankName || null,
    panNumber: b.panNumber || null,
  }
}

function fromResponse(b: TerminalSettlementBeneficiaryResponse): LocalBeneficiary {
  return {
    beneficiaryType: b.beneficiaryType,
    beneficiaryName: b.beneficiaryName,
    relationship: b.relationship,
    sharePercentage: String(b.sharePercentage),
    allocatedAmount: String(b.allocatedAmount),
    bankAccountNo: b.bankAccountNo,
    bankIfsc: b.bankIfsc,
    bankName: b.bankName ?? '',
    panNumber: b.panNumber ?? '',
  }
}

/**
 * Greenfield Terminal Settlement sheet: gratuity / DoPT Rule 39 EL+HPL
 * encashment / CPF Trust ledger breakdown, plus beneficiary disbursement.
 * Fetches a live preview (never persisted) until "Generate Settlement" is
 * clicked, which persists a DRAFT via POST /v1/settlements/generate/:id -
 * see TerminalSettlementService for why the two share one calculation.
 */
export function TerminalSettlementModal({
  employeeId,
  employeeName,
  separationType,
  separationDate,
  clearanceRequestId,
  onClose,
}: {
  employeeId: number
  employeeName: string
  separationType: SeparationType
  separationDate: string
  clearanceRequestId?: number | null
  onClose: () => void
}) {
  const { show } = useToast()
  const queryClient = useQueryClient()
  const [settlementId, setSettlementId] = useState<number | null>(null)
  const [beneficiaries, setBeneficiaries] = useState<LocalBeneficiary[]>([])
  const isDeceased = separationType === 'DECEASED'

  const previewQuery = useQuery({
    queryKey: ['settlement-preview', employeeId, separationType, separationDate],
    queryFn: async () =>
      (
        await apiClient.get<TerminalSettlementResponse>(`/v1/settlements/preview/${employeeId}`, {
          params: { separationType, separationDate, clearanceRequestId: clearanceRequestId ?? undefined },
        })
      ).data,
    enabled: settlementId === null,
  })

  const settlementQuery = useQuery({
    queryKey: ['settlement', settlementId],
    queryFn: async () => (await apiClient.get<TerminalSettlementResponse>(`/v1/settlements/${settlementId}`)).data,
    enabled: settlementId !== null,
  })

  const data = settlementId !== null ? settlementQuery.data : previewQuery.data
  const isLoading = settlementId !== null ? settlementQuery.isLoading : previewQuery.isLoading

  const generateMutation = useMutation({
    mutationFn: async () => {
      const payload: TerminalSettlementGenerateRequest = {
        separationType,
        separationDate,
        clearanceRequestId: clearanceRequestId ?? null,
        cpfAccruedInterest: null,
      }
      return (await apiClient.post<TerminalSettlementResponse>(`/v1/settlements/generate/${employeeId}`, payload)).data
    },
    onSuccess: (settlement) => {
      setSettlementId(settlement.id)
      setBeneficiaries(settlement.beneficiaries.map(fromResponse))
      show({ tone: 'success', message: 'Settlement generated as DRAFT.' })
    },
    onError: (error) => show({ tone: 'error', message: describeApiErrorList(error, 'Failed to generate settlement.').join('; ') }),
  })

  const saveBeneficiariesMutation = useMutation({
    mutationFn: async () => {
      if (settlementId === null) throw new Error('Generate the settlement first.')
      return (
        await apiClient.post<TerminalSettlementBeneficiaryResponse[]>(`/v1/settlements/${settlementId}/beneficiaries`, {
          beneficiaries: beneficiaries.map(toRequest),
        })
      ).data
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'Beneficiary allocation saved.' })
      queryClient.invalidateQueries({ queryKey: ['settlement', settlementId] })
    },
    onError: (error) => show({ tone: 'error', message: describeApiErrorList(error, 'Failed to save beneficiaries.').join('; ') }),
  })

  const approveMutation = useMutation({
    mutationFn: async () => {
      if (settlementId === null) throw new Error('Generate the settlement first.')
      return (await apiClient.post<TerminalSettlementResponse>(`/v1/settlements/${settlementId}/approve`)).data
    },
    onSuccess: () => {
      show({ tone: 'success', message: 'Settlement approved.' })
      queryClient.invalidateQueries({ queryKey: ['settlement', settlementId] })
    },
    onError: (error) => show({ tone: 'error', message: describeApiErrorList(error, 'Failed to approve settlement.').join('; ') }),
  })

  const shareSum = useMemo(() => beneficiaries.reduce((sum, b) => sum + (Number(b.sharePercentage) || 0), 0), [beneficiaries])
  const amountSum = useMemo(() => beneficiaries.reduce((sum, b) => sum + (Number(b.allocatedAmount) || 0), 0), [beneficiaries])
  const netPayable = data?.netTerminalPayable ?? 0
  const splitValid = Math.abs(shareSum - 100) < 0.01 && Math.abs(amountSum - netPayable) < 0.01 && beneficiaries.length > 0

  function addBeneficiaryRow() {
    setBeneficiaries((prev) => [...prev, { ...EMPTY_BENEFICIARY, beneficiaryType: 'NOMINEE' }])
  }
  function removeBeneficiaryRow(index: number) {
    setBeneficiaries((prev) => prev.filter((_, i) => i !== index))
  }
  function updateBeneficiaryRow(index: number, field: keyof LocalBeneficiary, value: string) {
    setBeneficiaries((prev) => prev.map((b, i) => (i === index ? { ...b, [field]: value } : b)))
  }

  return (
    <Modal title={`Terminal Settlement Sheet - ${employeeName}`} onClose={onClose} maxWidthClassName="max-w-4xl">
      {isLoading && <p className="py-6 text-center text-sm text-slate-400">Calculating settlement...</p>}

      {data && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <span className="text-xs text-slate-500">Separation: {separationType} · {formatDate(separationDate)}</span>
            {data.status && (
              <Badge tone={data.status === 'APPROVED' || data.status === 'DISBURSED' ? 'success' : 'neutral'}>{data.status}</Badge>
            )}
          </div>

          {/* Card 1: Service tenure and last drawn emoluments */}
          <SettlementCard title="Service Tenure & Last Drawn Emoluments">
            <Grid
              rows={[
                ['Last Basic Pay', money(data.lastBasicPay)],
                ['DA Rate', `${data.daRatePercentage}%`],
                ['DA Amount', money(data.daAmount)],
                ['Monthly Emoluments (Basic + DA)', money(data.monthlyEmoluments)],
                ['Qualifying Service', `${data.qualifyingServiceYears}y ${data.qualifyingServiceMonths}m (rounded: ${data.roundedQualifyingYears}y)`],
              ]}
            />
          </SettlementCard>

          {/* Card 2: Gratuity breakdown */}
          <SettlementCard title="Gratuity">
            <Grid
              rows={[
                ['Type', data.isDeathGratuity ? 'Death Gratuity (statutory slab)' : 'Retirement/Resignation Gratuity'],
                ['Qualifying Years Used', data.isDeathGratuity ? `${data.qualifyingServiceYears}y ${data.qualifyingServiceMonths}m` : `${data.roundedQualifyingYears}y`],
                ['Gratuity Amount', money(data.gratuityAmount)],
                ['Statutory Ceiling Check', `Capped at ₹20,00,000 (₹25,00,000 if DA ≥ 50%) - applied: ${data.daRatePercentage >= 50 ? '₹25,00,000' : '₹20,00,000'}`],
              ]}
            />
          </SettlementCard>

          {/* Card 3: DoPT leave encashment breakdown */}
          <SettlementCard title="DoPT Rule 39 - Leave Encashment (EL + HPL Shortfall)">
            <Grid
              rows={[
                ['EL Balance / Encashed', `${data.elBalanceAtRetirement} / ${data.elDaysEncashed} days`],
                ['HPL Balance / Encashed (shortfall top-up)', `${data.hplBalanceAtRetirement} / ${data.hplDaysEncashed} days`],
                ['EL Encashment (Basic+DA / 30 × days)', money(data.leaveEncashmentElAmount)],
                ['HPL Encashment (half Basic+DA / 30 × days)', money(data.leaveEncashmentHplAmount)],
                ['Total Leave Encashment', money(data.totalLeaveEncashment)],
              ]}
            />
          </SettlementCard>

          {/* Card 4: CPF Trust ledger balance breakdown */}
          <SettlementCard title="CPF Trust Ledger">
            <Grid
              rows={[
                ['Employee Fund Balance', money(data.cpfEmployeeBalance)],
                ['Employer Fund Balance', money(data.cpfEmployerBalance)],
                ['VPF Balance', money(data.cpfVpfBalance)],
                ['Accrued Interest', money(data.cpfAccruedInterest)],
                ['Total CPF Payable', money(data.totalCpfPayable)],
              ]}
            />
            {data.cpfAccruedInterest === 0 && (
              <p className="mt-2 rounded-md bg-amber-50 px-2.5 py-1.5 text-[11px] text-amber-700">
                Interest pending CPF Trust certification - this settlement currently assumes ₹0.00 accrued interest.
              </p>
            )}
          </SettlementCard>

          {/* Card 5: Departmental recoveries -> net disbursal */}
          <SettlementCard title="Recoveries & Net Disbursal">
            <Grid
              rows={[
                ['Gross Terminal Dues', money(data.grossTerminalDues)],
                ['Departmental Recoveries/Deductions', `- ${money(data.totalRecoveriesDeductions)}`],
                ['Net Terminal Payable', money(data.netTerminalPayable)],
              ]}
              emphasizeLast
            />
          </SettlementCard>

          {/* Card 6: Beneficiary disbursement allocation */}
          <SettlementCard title="Beneficiary Disbursement Allocation">
            {!isDeceased ? (
              beneficiaries.length > 0 ? (
                <Grid
                  rows={[
                    ['Beneficiary', beneficiaries[0].beneficiaryName],
                    ['Bank Account', beneficiaries[0].bankAccountNo],
                    ['IFSC', beneficiaries[0].bankIfsc],
                    ['Bank', beneficiaries[0].bankName || '—'],
                    ['Allocated Amount', money(Number(beneficiaries[0].allocatedAmount))],
                  ]}
                />
              ) : (
                <p className="text-xs text-slate-500">
                  {settlementId ? 'No beneficiary on file yet.' : 'Generate the settlement to see the employee\'s verified bank account.'}
                </p>
              )
            ) : (
              <div className="space-y-3">
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[820px] border-collapse text-xs">
                    <thead>
                      <tr className="border-b border-slate-200 text-left text-slate-500">
                        <th className="py-1.5 pr-2">Name</th>
                        <th className="py-1.5 pr-2">Relationship</th>
                        <th className="py-1.5 pr-2">Share %</th>
                        <th className="py-1.5 pr-2">Allocated Amount</th>
                        <th className="py-1.5 pr-2">Bank Account</th>
                        <th className="py-1.5 pr-2">IFSC</th>
                        <th className="py-1.5 pr-2">PAN</th>
                        <th className="py-1.5"></th>
                      </tr>
                    </thead>
                    <tbody>
                      {beneficiaries.map((b, i) => (
                        <tr key={i} className="border-b border-slate-100">
                          <td className="py-1 pr-2">
                            <input value={b.beneficiaryName} onChange={(e) => updateBeneficiaryRow(i, 'beneficiaryName', e.target.value)} className="w-28 rounded border border-slate-300 px-1.5 py-1" />
                          </td>
                          <td className="py-1 pr-2">
                            <input value={b.relationship} onChange={(e) => updateBeneficiaryRow(i, 'relationship', e.target.value)} className="w-20 rounded border border-slate-300 px-1.5 py-1" />
                          </td>
                          <td className="py-1 pr-2">
                            <input value={b.sharePercentage} onChange={(e) => updateBeneficiaryRow(i, 'sharePercentage', e.target.value)} className="w-16 rounded border border-slate-300 px-1.5 py-1" />
                          </td>
                          <td className="py-1 pr-2">
                            <input value={b.allocatedAmount} onChange={(e) => updateBeneficiaryRow(i, 'allocatedAmount', e.target.value)} className="w-24 rounded border border-slate-300 px-1.5 py-1" />
                          </td>
                          <td className="py-1 pr-2">
                            <input value={b.bankAccountNo} onChange={(e) => updateBeneficiaryRow(i, 'bankAccountNo', e.target.value)} className="w-28 rounded border border-slate-300 px-1.5 py-1" />
                          </td>
                          <td className="py-1 pr-2">
                            <input value={b.bankIfsc} onChange={(e) => updateBeneficiaryRow(i, 'bankIfsc', e.target.value)} className="w-24 rounded border border-slate-300 px-1.5 py-1" />
                          </td>
                          <td className="py-1 pr-2">
                            <input value={b.panNumber} onChange={(e) => updateBeneficiaryRow(i, 'panNumber', e.target.value)} className="w-20 rounded border border-slate-300 px-1.5 py-1" />
                          </td>
                          <td className="py-1">
                            <button type="button" onClick={() => removeBeneficiaryRow(i)} className="text-slate-400 hover:text-red-600">
                              <Trash2 size={14} />
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <button type="button" onClick={addBeneficiaryRow} className="inline-flex items-center gap-1 text-xs font-medium text-brand-forest hover:underline">
                  <Plus size={13} /> Add Nominee
                </button>
                <div className={`rounded-md border px-3 py-2 text-xs ${splitValid ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : 'border-amber-200 bg-amber-50 text-amber-700'}`}>
                  Share total: {shareSum.toFixed(2)}% (need 100.00%) · Amount total: {money(amountSum)} (need {money(netPayable)})
                  {splitValid ? ' — balanced' : ' — not yet balanced'}
                </div>
                {settlementId && (
                  <SecondaryButton onClick={() => saveBeneficiariesMutation.mutate()} disabled={!splitValid || saveBeneficiariesMutation.isPending}>
                    {saveBeneficiariesMutation.isPending ? 'Saving...' : 'Save Beneficiary Allocation'}
                  </SecondaryButton>
                )}
              </div>
            )}
          </SettlementCard>

          <div className="flex flex-wrap items-center justify-end gap-2 border-t border-slate-100 pt-4">
            <SecondaryButton onClick={() => window.print()}>
              <Printer size={14} /> Generate Sanction Order (Printable View)
            </SecondaryButton>
            {settlementId === null ? (
              <PrimaryButton onClick={() => generateMutation.mutate()} disabled={generateMutation.isPending}>
                {generateMutation.isPending ? 'Generating...' : 'Generate Settlement'}
              </PrimaryButton>
            ) : (
              <PrimaryButton
                onClick={() => approveMutation.mutate()}
                disabled={approveMutation.isPending || data.status === 'APPROVED' || data.status === 'DISBURSED' || (isDeceased && !splitValid)}
              >
                {approveMutation.isPending ? 'Approving...' : data.status === 'APPROVED' || data.status === 'DISBURSED' ? 'Approved' : 'Approve Settlement'}
              </PrimaryButton>
            )}
          </div>
        </div>
      )}
    </Modal>
  )
}

function SettlementCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-slate-200 p-3">
      <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">{title}</h3>
      {children}
    </div>
  )
}

function Grid({ rows, emphasizeLast = false }: { rows: [string, string][]; emphasizeLast?: boolean }) {
  return (
    <dl className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-xs sm:grid-cols-1 sm:gap-y-1">
      {rows.map(([label, value], i) => (
        <div key={label} className={`flex items-center justify-between gap-3 ${emphasizeLast && i === rows.length - 1 ? 'border-t border-slate-200 pt-1.5 font-semibold text-brand-forest' : ''}`}>
          <dt className="text-slate-500">{label}</dt>
          <dd className="text-right text-slate-800">{value}</dd>
        </div>
      ))}
    </dl>
  )
}
