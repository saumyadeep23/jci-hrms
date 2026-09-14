package in.gov.jci.hrms.dto;

import java.math.BigDecimal;

/**
 * One member's row in a Calculate-Interest preview or a posted run's detail view (Part 15 of the module
 * spec). Always recomputed on demand from the live ledger (never persisted) - see
 * CpfInterestRunService.previewMembers()'s own javadoc for why a stale persisted snapshot would be the
 * wrong thing to show anyway.
 */
public record CpfInterestMemberBreakdown(
        Long employeeId,
        String employeeCode,
        String employeeName,
        BigDecimal openingBalance,
        BigDecimal totalContributions,
        BigDecimal totalWithdrawals,
        BigDecimal eeInterest,
        BigDecimal erInterest,
        BigDecimal vpfInterest,
        BigDecimal totalInterest,
        BigDecimal projectedClosingBalance,
        boolean dataReviewRequired,
        String dataReviewReason,
        boolean legacyAnomalyWarning,
        String legacyAnomalyMessage
) {
}
