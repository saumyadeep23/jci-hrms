package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfInterestRunStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of the CPF Interest Management dashboard's FY list (Part 6/39 of the module spec) -
 * CpfInterestRunService.listFinancialYearStatuses(). dependencyBlockedReason is non-null only when
 * postingAllowed is false for a reason other than "already posted" - e.g. "Previous financial year
 * interest is pending." (Part 7) or "Opening-balance basis could not be confirmed for N member(s)."
 * (Part 9/31, DATA_REVIEW_REQUIRED).
 */
public record CpfInterestFinancialYearStatusResponse(
        String finYear,
        BigDecimal configuredRate,
        boolean calculationBasisAvailable,
        boolean fullYearDataAvailable,
        Long activeRunId,
        CpfInterestRunStatus status,
        boolean postingAllowed,
        String dependencyBlockedReason,
        int membersProcessed,
        BigDecimal totalInterestPosted,
        Long postedByEmployeeId,
        Instant postedAt
) {
}
