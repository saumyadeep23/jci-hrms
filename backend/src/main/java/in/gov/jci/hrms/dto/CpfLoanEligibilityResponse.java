package in.gov.jci.hrms.dto;

import java.math.BigDecimal;

/** GET /api/v1/payroll/trust/loans/eligibility/{employeeId} - CpfLoanApplicationService.checkEligibility(). */
public record CpfLoanEligibilityResponse(
        Long employeeId,
        BigDecimal runningEeBalance,
        BigDecimal runningVpfBalance,
        BigDecimal totalEligibleCorpus,
        BigDecimal maxPermissibleAmount,
        boolean activeLoanExists,
        BigDecimal outstandingActiveLoanBalance,
        String eligibilityReason
) {
}
