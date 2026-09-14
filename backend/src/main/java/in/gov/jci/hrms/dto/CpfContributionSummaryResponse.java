package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.service.CpfContributionBreakdownService;

import java.math.BigDecimal;

/** Part 4.3/15 "contributionSummary" - CpfContributionBreakdownService.summaryFor(). Every figure is server-computed; React never derives or re-sums these. */
public record CpfContributionSummaryResponse(
        BigDecimal employeeContribution,
        BigDecimal employerContribution,
        BigDecimal epsContribution,
        BigDecimal vpfContribution,
        BigDecimal totalContribution
) {
    public static CpfContributionSummaryResponse from(CpfContributionBreakdownService.ContributionSummary s) {
        return new CpfContributionSummaryResponse(s.employeeContribution(), s.employerContribution(), s.epsContribution(),
                s.vpfContribution(), s.totalContribution());
    }
}
