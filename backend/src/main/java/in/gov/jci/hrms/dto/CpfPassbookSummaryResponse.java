package in.gov.jci.hrms.dto;

import java.math.BigDecimal;

/**
 * GET /api/v1/ess/cpf/passbook/summary?finYear= - bundles CpfTrustPassbookService's existing authoritative
 * summary figures with CpfContributionBreakdownService's FY contribution summary (Parts 4.2/4.3/15). Every
 * figure here already existed in CpfPassbookResponseDto/CpfTrustPassbookService - this DTO doesn't compute
 * anything new, it just reshapes the existing admin passbook's summary fields for the self-service
 * endpoint's own response envelope (summary + contributionSummary, per Part 15).
 */
public record CpfPassbookSummaryResponse(
        String finYear,
        BigDecimal auditedBalance,
        BigDecimal accruedInterest,
        BigDecimal effectiveCorpus,
        BigDecimal outstandingLoan,
        BigDecimal rateApplied,
        String rateSourceFinYear,
        boolean isProvisionalRate,
        String provisionalNotice,
        CpfContributionSummaryResponse contributionSummary
) {
}
