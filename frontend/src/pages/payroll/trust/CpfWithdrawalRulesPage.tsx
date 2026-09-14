import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Calculator, CheckCircle2, FlaskConical, ShieldCheck, XCircle } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { useAuth } from '../../../auth/AuthContext'
import { describeApiError } from '../../../lib/apiError'
import { notifyToast } from '../../../lib/toastBridge'
import { Badge, Card, EmptyState, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../../components/common/ui'
import type {
  CpfRuleSimulatorRequest,
  CpfRuleSimulatorResponse,
  CpfRuleStatus,
  CpfWithdrawalPurposeResponse,
  CpfWithdrawalRuleVersionRequest,
  CpfWithdrawalRuleVersionResponse,
} from '../../../types/api'

const MASTERS_BASE = '/v1/payroll/trust/withdrawal-masters'
const RULES_BASE = '/v1/payroll/trust/withdrawal-rules'

function inr(value: number): string {
  return `₹${value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function statusTone(status: CpfRuleStatus): 'neutral' | 'success' | 'warning' | 'danger' | 'brand' {
  switch (status) {
    case 'APPROVED':
      return 'success'
    case 'REJECTED':
    case 'EXPIRED':
      return 'danger'
    case 'SUPERSEDED':
      return 'neutral'
    case 'REQUIRES_CONFIRMATION':
      return 'warning'
    default:
      return 'brand'
  }
}

/**
 * CPF Trust Loan & Advances rule engine console: the DRAFT -> PENDING_VERIFICATION -> PENDING_APPROVAL ->
 * APPROVED workflow (CpfWithdrawalRuleController) plus the Rule Simulator (Part 24 of the module spec -
 * "highly important": lets an official test a rule's math, including a not-yet-approved DRAFT, before it
 * ever affects a real member). Distinct from CpfLoansPage/cpf_loan_applications (a separate, older flow
 * this module does not replace).
 */
export function CpfWithdrawalRulesPage() {
  const { hasRole } = useAuth()
  const canAct = hasRole('CPF_ADMIN', 'SUPER_ADMIN')
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<'simulator' | 'rules'>('simulator')
  const [proposeFor, setProposeFor] = useState<CpfWithdrawalPurposeResponse | null>(null)

  const purposesQuery = useQuery({
    queryKey: ['cpf-withdrawal-purposes'],
    queryFn: async () => (await apiClient.get<CpfWithdrawalPurposeResponse[]>(`${MASTERS_BASE}/purposes`)).data,
  })
  const rulesQuery = useQuery({
    queryKey: ['cpf-withdrawal-rules'],
    queryFn: async () => (await apiClient.get<CpfWithdrawalRuleVersionResponse[]>(RULES_BASE)).data,
  })

  function invalidateRules() {
    queryClient.invalidateQueries({ queryKey: ['cpf-withdrawal-rules'] })
  }

  return (
    <div>
      <PageHeader
        title="CPF Withdrawal Rule Engine"
        description="Every withdrawal type/purpose/ceiling/head-priority/frequency rule is database-driven and version-approved - no business value is hardcoded in application code."
      />

      <div className="mb-4 flex gap-2 border-b border-slate-200">
        <button
          className={`border-b-2 px-3 py-2 text-sm font-medium ${tab === 'simulator' ? 'border-brand-forest text-brand-forest' : 'border-transparent text-slate-500'}`}
          onClick={() => setTab('simulator')}
        >
          <FlaskConical size={14} className="mr-1 inline" /> Rule Simulator
        </button>
        <button
          className={`border-b-2 px-3 py-2 text-sm font-medium ${tab === 'rules' ? 'border-brand-forest text-brand-forest' : 'border-transparent text-slate-500'}`}
          onClick={() => setTab('rules')}
        >
          <ShieldCheck size={14} className="mr-1 inline" /> Withdrawal Rules
        </button>
      </div>

      {tab === 'simulator' && <RuleSimulatorPanel purposes={purposesQuery.data ?? []} />}

      {tab === 'rules' && (
        <Card>
          <h2 className="mb-3 text-sm font-semibold text-slate-700">Rule Versions (all purposes)</h2>
          {rulesQuery.isLoading && <LoadingState label="Loading rules..." />}
          {rulesQuery.isError && <ErrorState message={describeApiError(rulesQuery.error, 'Failed to load rules.')} />}
          {rulesQuery.data && rulesQuery.data.length === 0 && <EmptyState message="No withdrawal rules configured yet." />}
          {rulesQuery.data && rulesQuery.data.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[900px] text-left text-sm">
                <thead>
                  <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                    <th className="py-2 pr-3">Purpose</th>
                    <th className="py-2 pr-3">Version</th>
                    <th className="py-2 pr-3">Status</th>
                    <th className="py-2 pr-3">Effective From</th>
                    <th className="py-2 pr-3">Effective To</th>
                    <th className="py-2 pr-3">Min Service</th>
                    <th className="py-2 pr-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {rulesQuery.data.map((rule) => (
                    <tr key={rule.id} className="border-b border-slate-100 last:border-0">
                      <td className="py-2 pr-3 font-medium text-slate-800">{rule.purposeCode}</td>
                      <td className="py-2 pr-3">{rule.versionTag}</td>
                      <td className="py-2 pr-3">
                        <Badge tone={statusTone(rule.status)}>{rule.status}</Badge>
                      </td>
                      <td className="py-2 pr-3">{rule.effectiveFrom}</td>
                      <td className="py-2 pr-3">{rule.effectiveTo ?? '—'}</td>
                      <td className="py-2 pr-3">{rule.minServiceMonths} mo</td>
                      <td className="py-2 pr-3 text-right">
                        {canAct && <RuleWorkflowActions rule={rule} onChanged={invalidateRules} />}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {canAct && (
            <div className="mt-4 border-t border-slate-200 pt-3">
              <SecondaryButton onClick={() => purposesQuery.data && setProposeFor(purposesQuery.data[0] ?? null)}>
                Propose a Ceiling-Percentage Change
              </SecondaryButton>
            </div>
          )}
        </Card>
      )}

      {proposeFor && (
        <ProposeCeilingChangeModal
          purposes={purposesQuery.data ?? []}
          initialPurpose={proposeFor}
          onClose={() => setProposeFor(null)}
          onCreated={() => {
            setProposeFor(null)
            invalidateRules()
          }}
        />
      )}
    </div>
  )
}

function RuleWorkflowActions({ rule, onChanged }: { rule: CpfWithdrawalRuleVersionResponse; onChanged: () => void }) {
  const submitMutation = useMutation({
    mutationFn: async () => (await apiClient.post(`${RULES_BASE}/${rule.id}/submit`)).data,
    onSuccess: () => {
      notifyToast({ tone: 'success', title: 'Submitted for verification', message: `${rule.purposeCode} ${rule.versionTag}` })
      onChanged()
    },
    onError: (error) => notifyToast({ tone: 'error', title: 'Failed', message: describeApiError(error, 'Could not submit.') }),
  })
  const verifyMutation = useMutation({
    mutationFn: async () => (await apiClient.post(`${RULES_BASE}/${rule.id}/verify`)).data,
    onSuccess: () => {
      notifyToast({ tone: 'success', title: 'Verified', message: `${rule.purposeCode} ${rule.versionTag} - now pending approval.` })
      onChanged()
    },
    onError: (error) => notifyToast({ tone: 'error', title: 'Failed', message: describeApiError(error, 'Could not verify.') }),
  })
  const approveMutation = useMutation({
    mutationFn: async () => (await apiClient.post(`${RULES_BASE}/${rule.id}/approve`, { remarks: 'Approved from CPF Withdrawal Rules console' })).data,
    onSuccess: () => {
      notifyToast({ tone: 'success', title: 'Approved', message: `${rule.purposeCode} ${rule.versionTag} is now active from ${rule.effectiveFrom}.` })
      onChanged()
    },
    onError: (error) => notifyToast({ tone: 'error', title: 'Approval failed', message: describeApiError(error, 'Could not approve.') }),
  })
  const rejectMutation = useMutation({
    mutationFn: async () => (await apiClient.post(`${RULES_BASE}/${rule.id}/reject`, { remarks: 'Rejected from CPF Withdrawal Rules console' })).data,
    onSuccess: () => {
      notifyToast({ tone: 'success', title: 'Rejected', message: `${rule.purposeCode} ${rule.versionTag}` })
      onChanged()
    },
    onError: (error) => notifyToast({ tone: 'error', title: 'Failed', message: describeApiError(error, 'Could not reject.') }),
  })

  if (rule.status === 'DRAFT') {
    return (
      <SecondaryButton onClick={() => submitMutation.mutate()} disabled={submitMutation.isPending}>
        Submit for Verification
      </SecondaryButton>
    )
  }
  if (rule.status === 'PENDING_VERIFICATION') {
    return (
      <SecondaryButton onClick={() => verifyMutation.mutate()} disabled={verifyMutation.isPending}>
        Verify (Secretariat)
      </SecondaryButton>
    )
  }
  if (rule.status === 'PENDING_APPROVAL') {
    return (
      <div className="flex justify-end gap-2">
        <SecondaryButton onClick={() => rejectMutation.mutate()} disabled={rejectMutation.isPending}>
          <XCircle size={14} /> Reject
        </SecondaryButton>
        <PrimaryButton onClick={() => approveMutation.mutate()} disabled={approveMutation.isPending}>
          <CheckCircle2 size={14} /> Approve
        </PrimaryButton>
      </div>
    )
  }
  return <span className="text-xs text-slate-400">—</span>
}

function ProposeCeilingChangeModal({
  purposes,
  initialPurpose,
  onClose,
  onCreated,
}: {
  purposes: CpfWithdrawalPurposeResponse[]
  initialPurpose: CpfWithdrawalPurposeResponse
  onClose: () => void
  onCreated: () => void
}) {
  const [purposeCode, setPurposeCode] = useState(initialPurpose.code)
  const [versionTag, setVersionTag] = useState('')
  const [effectiveFrom, setEffectiveFrom] = useState('')
  const [percent, setPercent] = useState('')
  const [minServiceMonths, setMinServiceMonths] = useState('0')
  const [changeReason, setChangeReason] = useState('')

  const createMutation = useMutation({
    mutationFn: async () => {
      const body: CpfWithdrawalRuleVersionRequest = {
        purposeCode,
        versionTag,
        effectiveFrom,
        changeReason,
        minServiceMonths: Number(minServiceMonths) || 0,
        includePreviousService: true,
        allowBreakInService: false,
        frequencyScope: 'NONE',
        maxOccurrences: 1,
        maxActiveConcurrency: 1,
        balanceRetentionPct: 0,
        repaymentCreditMethod: 'ORIGINAL_DEBIT_HEAD',
        minTenureMonths: null,
        maxTenureMonths: null,
        defaultTenureMonths: null,
        interestRateAnnual: null,
        interestMethod: null,
        allowPrepayment: null,
        allowConversion: null,
        payrollCapType: 'NORMAL',
        taxRuleReference: null,
        taxServiceThresholdMonths: null,
        workflowDefinitionCode: 'CPF_STANDARD_APPROVAL',
        heads: [
          { headCode: 'HEAD_B', eligible: true, debitPriority: 1, recreditPriority: 1 },
          { headCode: 'HEAD_A', eligible: true, debitPriority: 2, recreditPriority: 2 },
          { headCode: 'HEAD_C', eligible: false, debitPriority: 99, recreditPriority: 99 },
        ],
        ceilings: [
          { componentName: 'BALANCE_PERCENTAGE_CAP', sourceMetric: 'ELIGIBLE_BALANCE', operator: 'PERCENTAGE', factorValue: Number(percent), displayOrder: 1 },
        ],
        documents: [],
      }
      return (await apiClient.post<CpfWithdrawalRuleVersionResponse>(RULES_BASE, body)).data
    },
    onSuccess: (created) => {
      notifyToast({ tone: 'success', title: 'Draft created', message: `${created.purposeCode} ${created.versionTag} - submit it for verification next.` })
      onCreated()
    },
    onError: (error) => notifyToast({ tone: 'error', title: 'Failed', message: describeApiError(error, 'Could not create draft rule.') }),
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="w-full max-w-lg rounded-xl bg-white p-6 shadow-xl">
        <h3 className="mb-1 text-lg font-semibold text-slate-800">Propose a Ceiling-Percentage Change</h3>
        <p className="mb-4 text-xs text-slate-500">
          Creates a simple "X% of eligible A+B balance" rule as a new DRAFT version - submit, verify, then approve it to
          take effect from the date below. The version currently active for this purpose is never edited, only superseded.
        </p>
        <div className="space-y-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Purpose</label>
            <select className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" value={purposeCode} onChange={(e) => setPurposeCode(e.target.value)}>
              {purposes.map((p) => (
                <option key={p.code} value={p.code}>
                  {p.name} ({p.code})
                </option>
              ))}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Version Tag</label>
              <input className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" value={versionTag} onChange={(e) => setVersionTag(e.target.value)} placeholder="e.g. 2027.1" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Effective From</label>
              <input type="date" className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" value={effectiveFrom} onChange={(e) => setEffectiveFrom(e.target.value)} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Ceiling % of Eligible Balance</label>
              <input className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" value={percent} onChange={(e) => setPercent(e.target.value)} placeholder="e.g. 60" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Min. Service (months)</label>
              <input className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" value={minServiceMonths} onChange={(e) => setMinServiceMonths(e.target.value)} />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Reason for Change</label>
            <textarea className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" rows={2} value={changeReason} onChange={(e) => setChangeReason(e.target.value)} />
          </div>
        </div>
        <div className="mt-4 flex justify-end gap-2">
          <SecondaryButton onClick={onClose}>Cancel</SecondaryButton>
          <PrimaryButton
            disabled={!versionTag || !effectiveFrom || !percent || !changeReason || createMutation.isPending}
            onClick={() => createMutation.mutate()}
          >
            Create Draft
          </PrimaryButton>
        </div>
      </div>
    </div>
  )
}

function RuleSimulatorPanel({ purposes }: { purposes: CpfWithdrawalPurposeResponse[] }) {
  const [purposeCode, setPurposeCode] = useState('')
  const [employeeCode, setEmployeeCode] = useState('')
  const [headA, setHeadA] = useState('')
  const [headB, setHeadB] = useState('')
  const [headC, setHeadC] = useState('')
  const [basicPlusDa, setBasicPlusDa] = useState('')
  const [propertyCost, setPropertyCost] = useState('')
  const [requestedAmount, setRequestedAmount] = useState('')
  const [tenureMonths, setTenureMonths] = useState('')
  const [result, setResult] = useState<CpfRuleSimulatorResponse | null>(null)

  const simulateMutation = useMutation({
    mutationFn: async () => {
      const body: CpfRuleSimulatorRequest = {
        purposeCode,
        employeeCode: employeeCode || null,
        headABalance: headA ? Number(headA) : null,
        headBBalance: headB ? Number(headB) : null,
        headCBalance: headC ? Number(headC) : null,
        basicPlusDa: basicPlusDa ? Number(basicPlusDa) : null,
        propertyCost: propertyCost ? Number(propertyCost) : null,
        requestedAmount: requestedAmount ? Number(requestedAmount) : null,
        loanTenureMonths: tenureMonths ? Number(tenureMonths) : null,
      }
      return (await apiClient.post<CpfRuleSimulatorResponse>(`${RULES_BASE}/simulate`, body)).data
    },
    onSuccess: (data) => setResult(data),
    onError: (error) => notifyToast({ tone: 'error', title: 'Simulation failed', message: describeApiError(error, 'Could not simulate.') }),
  })

  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <Card>
        <h2 className="mb-3 text-sm font-semibold text-slate-700">Inputs</h2>
        <div className="space-y-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Purpose</label>
            <select className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" value={purposeCode} onChange={(e) => setPurposeCode(e.target.value)}>
              <option value="">Select a purpose...</option>
              {purposes.map((p) => (
                <option key={p.code} value={p.code}>
                  {p.name} ({p.code})
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Employee Code (optional)</label>
            <input className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" value={employeeCode} onChange={(e) => setEmployeeCode(e.target.value)} placeholder="For real service-eligibility/frequency checks" />
          </div>
          <div className="grid grid-cols-3 gap-2">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Head A (EE)</label>
              <input className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" value={headA} onChange={(e) => setHeadA(e.target.value)} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Head B (VPF)</label>
              <input className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" value={headB} onChange={(e) => setHeadB(e.target.value)} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Head C (ER)</label>
              <input className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" value={headC} onChange={(e) => setHeadC(e.target.value)} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Basic + DA</label>
              <input className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" value={basicPlusDa} onChange={(e) => setBasicPlusDa(e.target.value)} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Property Cost</label>
              <input className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" value={propertyCost} onChange={(e) => setPropertyCost(e.target.value)} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Requested Amount</label>
              <input className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" value={requestedAmount} onChange={(e) => setRequestedAmount(e.target.value)} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Loan Tenure (months)</label>
              <input className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" value={tenureMonths} onChange={(e) => setTenureMonths(e.target.value)} />
            </div>
          </div>
          <PrimaryButton disabled={!purposeCode || simulateMutation.isPending} onClick={() => simulateMutation.mutate()} className="w-full justify-center">
            <Calculator size={14} /> {simulateMutation.isPending ? 'Simulating...' : 'Simulate'}
          </PrimaryButton>
        </div>
      </Card>

      <Card>
        <h2 className="mb-3 text-sm font-semibold text-slate-700">Result</h2>
        {!result && <EmptyState message="Run a simulation to see eligibility, ceiling, and head allocation." />}
        {result && (
          <div className="space-y-3 text-sm">
            <div className="flex items-center justify-between">
              <span className="text-slate-500">Rule Version</span>
              <span className="font-medium">
                {result.ruleVersionTag} <Badge tone={statusTone(result.ruleStatus as CpfRuleStatus)}>{result.ruleStatus}</Badge>
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-slate-500">Service Eligible</span>
              <span className={result.serviceEligible ? 'text-emerald-700' : 'text-rose-600'}>{result.serviceEligibilityReason}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-slate-500">Frequency Eligible</span>
              <span className={result.frequencyEligible ? 'text-emerald-700' : 'text-rose-600'}>{result.frequencyReason}</span>
            </div>
            <div className="flex items-center justify-between border-t border-slate-200 pt-2">
              <span className="text-slate-500">Total Eligible Balance</span>
              <span className="font-medium">{inr(result.totalEligibleBalance)}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-slate-500">Final Eligible Amount</span>
              <span className="text-base font-semibold text-brand-forest">{inr(result.finalEligibleAmount)}</span>
            </div>
            {result.projectedEmi != null && (
              <div className="flex items-center justify-between">
                <span className="text-slate-500">Projected EMI</span>
                <span className="font-medium">{inr(result.projectedEmi)}</span>
              </div>
            )}
            <div>
              <span className="text-slate-500">Debit Allocation</span>
              <ul className="mt-1 space-y-1">
                {Object.entries(result.debitAllocationByHead).map(([head, amount]) => (
                  <li key={head} className="flex justify-between text-xs">
                    <span>{head}</span>
                    <span>{inr(amount)}</span>
                  </li>
                ))}
              </ul>
            </div>
            <details className="mt-2">
              <summary className="cursor-pointer text-xs font-medium text-slate-500">Calculation Trace</summary>
              <ul className="mt-1 space-y-0.5 text-xs text-slate-600">
                {result.calculationTrace.map((line, i) => (
                  <li key={i}>{line}</li>
                ))}
              </ul>
            </details>
          </div>
        )}
      </Card>
    </div>
  )
}
