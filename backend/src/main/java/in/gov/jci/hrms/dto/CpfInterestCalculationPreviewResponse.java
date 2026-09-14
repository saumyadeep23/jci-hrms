package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfInterestRunScope;

import java.math.BigDecimal;
import java.util.List;

/** Result of a Calculate Interest action - the persisted run's id/status plus the full per-member breakdown (Part 15 of the module spec). */
public record CpfInterestCalculationPreviewResponse(
        Long runId,
        String finYear,
        CpfInterestRunScope scope,
        BigDecimal interestRate,
        int memberCount,
        BigDecimal totalOpeningBalance,
        BigDecimal totalContributions,
        BigDecimal totalWithdrawals,
        BigDecimal totalEeInterest,
        BigDecimal totalErInterest,
        BigDecimal totalVpfInterest,
        BigDecimal totalStatutoryInterest,
        boolean dataReviewRequired,
        List<CpfInterestMemberBreakdown> members
) {
}
