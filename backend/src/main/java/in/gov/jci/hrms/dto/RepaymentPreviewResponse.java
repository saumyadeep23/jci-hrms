package in.gov.jci.hrms.dto;

import java.math.BigDecimal;

/**
 * Non-mutating repayment preview for a refundable purpose - every figure here is computed by the same
 * helpers CpfApplicationService.sanction()/createLinkedLoan() already use at real sanction/disbursement
 * time (CpfLoanApplicationService.computeTotalInterest/computeMonthlyRecovery/
 * resolveInterestInstallmentsFromPolicy) - nothing here is a second calculation engine, only a preview
 * call to the existing one. Principal-first: interest installments (interestInstallmentCount of them)
 * only begin after every principal installment (principalInstallmentCount, = tenureMonths) is recovered -
 * interestPhaseStartInstallment is that first interest-only installment number, 1-based.
 */
public record RepaymentPreviewResponse(
        int tenureMonths,
        BigDecimal principalAmount,
        BigDecimal monthlyPrincipalInstallment,
        int principalInstallmentCount,
        int interestInstallmentCount,
        int interestPhaseStartInstallment,
        BigDecimal monthlyInterestInstallment,
        BigDecimal totalInterest,
        BigDecimal totalRecovery
) {
}
