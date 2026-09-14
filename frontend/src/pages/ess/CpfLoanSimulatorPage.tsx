import { useEffect, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { Badge, Card, ErrorState, LoadingState, PageHeader, PrimaryButton, SecondaryButton } from '../../components/common/ui'
import { describeApiError } from '../../lib/apiError'
import { ApplyCpfLoanModal } from '../payroll/trust/loans/ApplyCpfLoanModal'
import { inr, METRIC_LABELS, relevantInputMetrics, repaymentPhases } from '../payroll/trust/loans/cpfEligibilityUi'
import type { CpfApplicationEligibilityResponse, CpfWithdrawalPurposeResponse, EmployeeResponse } from '../../types/api'

const toNumberOrNull = (value: string): number | null => (value.trim() === '' ? null : Number(value))

/**
 * Task 4 - "CPF Loans & Advances Simulator" (Route: /ess/cpf/loan-simulator). Strictly read-only: every
 * figure shown here comes from the same GET /v1/payroll/trust/withdrawal-applications/eligibility endpoint
 * Apply-for-Loan uses, evaluated by the existing CpfWithdrawalRuleEngine/CpfApplicationService - this file
 * never imports or calls a POST/PUT of any kind, never creates a CpfApplication, and never posts a ledger
 * entry. No "CPF Loans & Advances Simulator" existed anywhere in this codebase before this file (the only
 * prior "simulator" was CpfWithdrawalRulesPage.tsx, an admin-only tool for testing rule *definitions*, not
 * a member-facing eligibility/repayment preview) - this is new construction whose entire calculation
 * surface is delegated to the already-existing, already-tested rule engine.
 *
 * The employee is always resolved from the logged-in session (useAuth -> GET /employees/{id}), the same
 * pattern ProfilePage/PfStatementPage already use - never a free-text/picker input, so a member can only
 * ever simulate their own eligibility (the backend's @cpfApplicationSec.isSelf check enforces this
 * server-side regardless).
 */
export function CpfLoanSimulatorPage() {
  const { employeeId } = useAuth()
  const [purposeCode, setPurposeCode] = useState('')
  const [propertyCost, setPropertyCost] = useState('')
  const [payrollDeductionCapacity, setPayrollDeductionCapacity] = useState('')
  const [requestedAmount, setRequestedAmount] = useState('')
  const [tenureMonths, setTenureMonths] = useState('')
  const [tenureTouched, setTenureTouched] = useState(false)
  const [eligibility, setEligibility] = useState<CpfApplicationEligibilityResponse | null>(null)
  const [applying, setApplying] = useState(false)

  const meQuery = useQuery({
    queryKey: ['employee', employeeId],
    queryFn: async () => (await apiClient.get<EmployeeResponse>(`/employees/${employeeId}`)).data,
    enabled: employeeId !== null,
  })

  const purposesQuery = useQuery({
    queryKey: ['cpf-withdrawal-purposes'],
    queryFn: async () => (await apiClient.get<CpfWithdrawalPurposeResponse[]>('/v1/payroll/trust/withdrawal-masters/purposes')).data,
  })
  const purposes = (purposesQuery.data ?? []).filter((p) => p.active && p.typeCode !== 'FINAL_SETTLEMENT')

  const eligibilityMutation = useMutation({
    mutationFn: async () => {
      const response = await apiClient.get<CpfApplicationEligibilityResponse>('/v1/payroll/trust/withdrawal-applications/eligibility', {
        params: {
          employeeCode: meQuery.data!.employeeCode,
          purposeCode,
          propertyCost: toNumberOrNull(propertyCost) ?? undefined,
          payrollDeductionCapacity: toNumberOrNull(payrollDeductionCapacity) ?? undefined,
          requestedAmount: toNumberOrNull(requestedAmount) ?? undefined,
          tenureMonths: toNumberOrNull(tenureMonths) ?? undefined,
        },
      })
      return response.data
    },
    onSuccess: (data) => setEligibility(data),
  })

  useEffect(() => {
    if (meQuery.data && purposeCode) {
      setEligibility(null)
      setTenureTouched(false)
      eligibilityMutation.mutate()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [meQuery.data?.employeeCode, purposeCode])

  useEffect(() => {
    if (eligibility?.carriesRepaymentSchedule && eligibility.defaultTenureMonths != null && !tenureTouched) {
      setTenureMonths(String(eligibility.defaultTenureMonths))
    }
  }, [eligibility?.carriesRepaymentSchedule, eligibility?.defaultTenureMonths, tenureTouched])

  const inputMetrics = eligibility ? relevantInputMetrics(eligibility.ceilingComponents) : []
  const preview = eligibility?.repaymentPreview ?? null
  const canSimulate = meQuery.data != null && purposeCode !== ''

  if (meQuery.isLoading) return <LoadingState label="Loading your employee record..." />
  if (meQuery.isError || !meQuery.data) return <ErrorState message="Could not load your employee record." />

  return (
    <div>
      <PageHeader
        title="CPF Loans & Advances Simulator"
        description="Explore your eligibility, ceiling and repayment schedule for any CPF Trust loan/withdrawal purpose - nothing here is submitted or saved."
      />

      <Card className="mb-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs font-medium text-slate-600">Purpose</label>
            <select
              value={purposeCode}
              onChange={(e) => setPurposeCode(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="">Select a purpose...</option>
              {purposes.map((p) => (
                <option key={p.code} value={p.code}>
                  {p.name} ({p.typeCode === 'REFUNDABLE' ? 'Loan' : 'Withdrawal'})
                </option>
              ))}
            </select>
          </div>
        </div>

        {eligibilityMutation.isPending && !eligibility && <p className="mt-3 text-xs text-slate-400">Loading eligibility rules for this purpose...</p>}

        {eligibility && inputMetrics.length > 0 && (
          <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-3">
            {inputMetrics.includes('PROPERTY_COST') && (
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">{METRIC_LABELS.PROPERTY_COST}</label>
                <input
                  type="number"
                  min={0}
                  step="0.01"
                  value={propertyCost}
                  onChange={(e) => setPropertyCost(e.target.value)}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
            )}
            {inputMetrics.includes('PAYROLL_DEDUCTION_CAPACITY') && (
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">{METRIC_LABELS.PAYROLL_DEDUCTION_CAPACITY}</label>
                <input
                  type="number"
                  min={0}
                  step="0.01"
                  value={payrollDeductionCapacity}
                  onChange={(e) => setPayrollDeductionCapacity(e.target.value)}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
            )}
          </div>
        )}
        {eligibility && eligibility.ceilingComponents.some((c) => c.sourceMetric === 'BASIC_PLUS_DA') && (
          <p className="mt-2 text-[11px] text-slate-400">
            Your current Basic + DA is retrieved automatically from your latest payroll record.
          </p>
        )}
        {eligibility && purposeCode.toUpperCase() === 'HOUSING_LOAN_REPAYMENT' && (
          <p className="mt-2 text-[11px] text-slate-400">
            Outstanding housing loan balance is retrieved automatically from your existing CPF Trust housing loan.
          </p>
        )}

        {eligibility && (
          <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-600">Amount to Simulate</label>
              <input
                type="number"
                min={0}
                step="0.01"
                value={requestedAmount}
                onChange={(e) => setRequestedAmount(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            {eligibility.carriesRepaymentSchedule && (
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-600">
                  Tenure (months)
                  {eligibility.minTenureMonths != null && eligibility.maxTenureMonths != null && (
                    <span className="text-slate-400">
                      {' '}
                      ({eligibility.minTenureMonths}-{eligibility.maxTenureMonths})
                    </span>
                  )}
                </label>
                <input
                  type="number"
                  min={eligibility.minTenureMonths ?? undefined}
                  max={eligibility.maxTenureMonths ?? undefined}
                  value={tenureMonths}
                  onChange={(e) => {
                    setTenureTouched(true)
                    setTenureMonths(e.target.value)
                  }}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
            )}
          </div>
        )}

        <SecondaryButton
          type="button"
          className="mt-3"
          disabled={!canSimulate || eligibilityMutation.isPending}
          onClick={() => eligibilityMutation.mutate()}
        >
          {eligibilityMutation.isPending ? 'Simulating...' : 'Simulate'}
        </SecondaryButton>

        {eligibilityMutation.isError && (
          <div className="mt-3">
            <ErrorState message={describeApiError(eligibilityMutation.error, 'Could not simulate eligibility for this purpose.')} />
          </div>
        )}
      </Card>

      {eligibility && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <Card>
            <p className="mb-2 text-xs font-medium text-slate-600">Eligibility &amp; Ceiling</p>
            <div className="space-y-1 text-sm">
              <p>
                <span className="text-slate-400">Total Eligible Balance: </span>
                <span className="font-medium text-slate-800">{inr(eligibility.totalEligibleBalance)}</span>
              </p>
              <p>
                <span className="text-slate-400">Maximum Eligible Amount: </span>
                <span className="font-medium text-slate-800">{inr(eligibility.eligibleAmount)}</span>
              </p>
              {eligibility.ceilingComponents.length > 0 && (
                <div className="mt-2 space-y-0.5 border-t border-slate-200 pt-2">
                  {eligibility.ceilingComponents.map((c) => (
                    <p key={c.componentName} className="flex justify-between text-[11px] text-slate-500">
                      <span>
                        {c.componentName} <span className="text-slate-400">({c.sourceMetric})</span>
                      </span>
                      <span className="font-medium text-slate-700">{inr(c.calculatedValue)}</span>
                    </p>
                  ))}
                </div>
              )}
              <div className="mt-2 flex flex-wrap gap-1">
                {eligibility.eligible ? (
                  <Badge tone="success">Eligible</Badge>
                ) : (
                  <Badge tone="danger">{eligibility.eligibilityReason}</Badge>
                )}
                {!eligibility.serviceEligible && <Badge tone="danger">{eligibility.serviceEligibilityReason}</Badge>}
                {!eligibility.frequencyEligible && <Badge tone="danger">{eligibility.frequencyReason}</Badge>}
              </div>
            </div>

            {eligibility.headAllocation.length > 0 && (
              <div className="mt-4 border-t border-slate-200 pt-3">
                <p className="mb-2 text-xs font-medium text-slate-600">Head-wise Debit Allocation</p>
                <table className="w-full text-left text-[11px]">
                  <thead>
                    <tr className="text-slate-400">
                      <th className="pb-1 pr-2 font-medium">Priority</th>
                      <th className="pb-1 pr-2 font-medium">Head</th>
                      <th className="pb-1 font-medium">Debit Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    {eligibility.headAllocation.map((h) => (
                      <tr key={h.headCode} className="border-t border-slate-100">
                        <td className="py-1 pr-2 text-slate-500">{h.debitPriority}</td>
                        <td className="py-1 pr-2 text-slate-700">{h.headName}</td>
                        <td className="py-1 text-slate-700">{inr(h.previewDebitAmount)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>

          <Card>
            <p className="mb-2 text-xs font-medium text-slate-600">Repayment Preview</p>
            {preview ? (
              <>
                <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-[12px] text-slate-600">
                  <p>
                    Tenure: <span className="font-medium text-slate-800">{preview.tenureMonths} mo</span>
                  </p>
                  <p>
                    Total Interest: <span className="font-medium text-slate-800">{inr(preview.totalInterest)}</span>
                  </p>
                  <p>
                    Total Recovery: <span className="font-medium text-slate-800">{inr(preview.totalRecovery)}</span>
                  </p>
                </div>
                <div className="mt-3 overflow-x-auto">
                  <table className="w-full text-left text-[11px]">
                    <thead>
                      <tr className="text-slate-400">
                        <th className="pb-1 pr-2 font-medium">Phase</th>
                        <th className="pb-1 pr-2 font-medium">Installments</th>
                        <th className="pb-1 pr-2 font-medium">Per Installment</th>
                        <th className="pb-1 font-medium">Phase Total</th>
                      </tr>
                    </thead>
                    <tbody>
                      {repaymentPhases(preview).map((phase) => (
                        <tr key={phase.label} className="border-t border-slate-200">
                          <td className="py-1 pr-2 text-slate-700">
                            {phase.label}
                            <span className="block text-slate-400">
                              Installment {phase.startInstallment}-{phase.endInstallment}
                            </span>
                          </td>
                          <td className="py-1 pr-2 text-slate-700">{phase.installmentCount}</td>
                          <td className="py-1 pr-2 text-slate-700">{inr(phase.perInstallmentAmount)}</td>
                          <td className="py-1 text-slate-700">{inr(phase.phaseTotal)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </>
            ) : (
              <p className="text-xs text-slate-400">
                {eligibility.carriesRepaymentSchedule
                  ? 'Enter an amount (and tenure, if not already defaulted) and simulate again to see a repayment preview.'
                  : 'This purpose is a withdrawal, not a loan - it carries no repayment schedule.'}
              </p>
            )}

            <div className="mt-4 border-t border-slate-200 pt-3">
              <PrimaryButton type="button" disabled={!eligibility.eligible} onClick={() => setApplying(true)}>
                Apply for this Loan
              </PrimaryButton>
              {!eligibility.eligible && <p className="mt-1 text-[11px] text-slate-400">Not currently eligible under the rules shown above.</p>}
            </div>
          </Card>
        </div>
      )}

      {applying && meQuery.data && (
        <ApplyCpfLoanModal onClose={() => setApplying(false)} lockedEmployee={meQuery.data} initialPurposeCode={purposeCode} />
      )}
    </div>
  )
}
