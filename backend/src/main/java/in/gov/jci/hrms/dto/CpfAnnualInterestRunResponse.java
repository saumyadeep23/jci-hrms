package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfAnnualInterestRun;
import in.gov.jci.hrms.entity.CpfInterestRunStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CpfAnnualInterestRunResponse(
        Long id,
        String finYear,
        BigDecimal declaredInterestRate,
        String interestOrderNo,
        LocalDate interestOrderDate,
        LocalDate runDate,
        int totalMembersProcessed,
        BigDecimal totalInterestCreditedEe,
        BigDecimal totalInterestCreditedEr,
        BigDecimal totalInterestCreditedVpf,
        CpfInterestRunStatus status,
        Long postedByEmployeeId,
        Instant postedAt
) {
    public static CpfAnnualInterestRunResponse from(CpfAnnualInterestRun r) {
        return new CpfAnnualInterestRunResponse(
                r.getId(), r.getFinYear(), r.getDeclaredInterestRate(), r.getInterestOrderNo(), r.getInterestOrderDate(),
                r.getRunDate(), r.getTotalMembersProcessed(), r.getTotalInterestCreditedEe(), r.getTotalInterestCreditedEr(),
                r.getTotalInterestCreditedVpf(), r.getStatus(), r.getPostedBy() != null ? r.getPostedBy().getId() : null,
                r.getPostedAt());
    }
}
