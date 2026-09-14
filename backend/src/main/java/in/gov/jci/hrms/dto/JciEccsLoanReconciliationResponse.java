package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.JciEccsReconciliationStatus;
import in.gov.jci.hrms.service.JciEccsReconciliationService;

import java.math.BigDecimal;

public record JciEccsLoanReconciliationResponse(
        Long loanId,
        BigDecimal originalPrincipal,
        BigDecimal postedPrincipalRecovery,
        BigDecimal postedPrincipalReversals,
        BigDecimal derivedOutstanding,
        BigDecimal storedOutstanding,
        BigDecimal outstandingVariance,
        BigDecimal schedulePrincipalRecovered,
        BigDecimal ledgerPrincipalRecovered,
        BigDecimal scheduleInterestRecovered,
        BigDecimal ledgerInterestRecovered,
        JciEccsReconciliationStatus status
) {
    public static JciEccsLoanReconciliationResponse from(JciEccsReconciliationService.LoanReconciliationResult r) {
        return new JciEccsLoanReconciliationResponse(r.loanId(), r.originalPrincipal(), r.postedPrincipalRecovery(),
                r.postedPrincipalReversals(), r.derivedOutstanding(), r.storedOutstanding(), r.outstandingVariance(),
                r.schedulePrincipalRecovered(), r.ledgerPrincipalRecovered(), r.scheduleInterestRecovered(), r.ledgerInterestRecovered(),
                r.status());
    }
}
