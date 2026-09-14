package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfAnnualInterestRun;
import in.gov.jci.hrms.entity.CpfInterestRunScope;
import in.gov.jci.hrms.entity.CpfInterestRunStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CpfAnnualInterestRunResponse(
        Long id,
        String finYear,
        CpfInterestRunScope scope,
        Long memberEmployeeId,
        BigDecimal declaredInterestRate,
        String interestOrderNo,
        LocalDate interestOrderDate,
        LocalDate runDate,
        int totalMembersProcessed,
        BigDecimal totalInterestCreditedEe,
        BigDecimal totalInterestCreditedEr,
        BigDecimal totalInterestCreditedVpf,
        CpfInterestRunStatus status,
        boolean dataReviewRequired,
        Long calculatedByEmployeeId,
        Instant calculatedAt,
        Long postedByEmployeeId,
        Instant postedAt,
        Long reversedByEmployeeId,
        Instant reversedAt,
        String remarks
) {
    public static CpfAnnualInterestRunResponse from(CpfAnnualInterestRun r) {
        return new CpfAnnualInterestRunResponse(
                r.getId(), r.getFinYear(), r.getScope(), r.getMemberEmployee() != null ? r.getMemberEmployee().getId() : null,
                r.getDeclaredInterestRate(), r.getInterestOrderNo(), r.getInterestOrderDate(), r.getRunDate(),
                r.getTotalMembersProcessed(), r.getTotalInterestCreditedEe(), r.getTotalInterestCreditedEr(), r.getTotalInterestCreditedVpf(),
                r.getStatus(), r.isDataReviewRequired(),
                r.getCalculatedBy() != null ? r.getCalculatedBy().getId() : null, r.getCalculatedAt(),
                r.getPostedBy() != null ? r.getPostedBy().getId() : null, r.getPostedAt(),
                r.getReversedBy() != null ? r.getReversedBy().getId() : null, r.getReversedAt(),
                r.getRemarks());
    }
}
