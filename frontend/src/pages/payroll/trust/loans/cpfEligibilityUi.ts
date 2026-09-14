import type { CpfCeilingComponent, CpfRepaymentPreview } from '../../../../types/api'

/**
 * Task 4 - purely UI-side helpers shared by the Apply-for-Loan wizard and the read-only Simulator. Neither
 * of these computes any CPF business value: relevantInputMetrics only decides which of the fixed input
 * fields to *show* (the actual ceiling math stays server-side in CpfWithdrawalRuleEngine), and
 * repaymentPhases only re-labels numbers CpfApplicationService.checkEligibility already computed via the
 * existing CpfLoanApplicationService helpers - no interest/installment arithmetic happens here.
 */
export type CpfInputMetric = 'PROPERTY_COST' | 'PAYROLL_DEDUCTION_CAPACITY'

const INPUT_METRICS: readonly CpfInputMetric[] = ['PROPERTY_COST', 'PAYROLL_DEDUCTION_CAPACITY']

/** BASIC_PLUS_DA and OUTSTANDING_LOAN are both deliberately excluded - BASIC_PLUS_DA is always
 * server-resolved from the employee's own latest DISBURSED payroll record (CpfApplicationService.
 * resolveCurrentBasicPlusDa), OUTSTANDING_LOAN is always server-resolved for HOUSING_LOAN_REPAYMENT
 * (resolveOutstandingHousingLoan) and never used by any other live purpose; ELIGIBLE_BALANCE and
 * FIXED_AMOUNT need no employee-entered value at all. */
export function relevantInputMetrics(components: CpfCeilingComponent[]): CpfInputMetric[] {
  const present = new Set(components.map((c) => c.sourceMetric))
  return INPUT_METRICS.filter((metric) => present.has(metric))
}

export const METRIC_LABELS: Record<CpfInputMetric, string> = {
  PROPERTY_COST: 'Property Cost',
  PAYROLL_DEDUCTION_CAPACITY: 'Payroll Deduction Capacity',
}

export interface RepaymentPhase {
  label: string
  installmentCount: number
  perInstallmentAmount: number
  startInstallment: number
  endInstallment: number
  phaseTotal: number
}

/** Two-phase (principal-first, then interest-only) summary of an already-computed RepaymentPreviewResponse -
 * a rendering split of aggregate figures the backend returned, not a recalculation. */
export function repaymentPhases(preview: CpfRepaymentPreview): RepaymentPhase[] {
  return [
    {
      label: 'Principal recovery',
      installmentCount: preview.principalInstallmentCount,
      perInstallmentAmount: preview.monthlyPrincipalInstallment,
      startInstallment: 1,
      endInstallment: preview.principalInstallmentCount,
      phaseTotal: preview.principalAmount,
    },
    {
      label: 'Interest recovery (starts only after principal is fully recovered)',
      installmentCount: preview.interestInstallmentCount,
      perInstallmentAmount: preview.monthlyInterestInstallment,
      startInstallment: preview.interestPhaseStartInstallment,
      endInstallment: preview.interestPhaseStartInstallment + preview.interestInstallmentCount - 1,
      phaseTotal: preview.totalInterest,
    },
  ]
}

export const inr = (value: number): string =>
  new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value)
