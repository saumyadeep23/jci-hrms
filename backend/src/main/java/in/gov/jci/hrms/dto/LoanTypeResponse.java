package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.LoanType;
import in.gov.jci.hrms.entity.LoanTypeCode;

import java.math.BigDecimal;
import java.time.Instant;

public record LoanTypeResponse(
        Long id,
        LoanTypeCode code,
        String name,
        BigDecimal interestRateAnnual,
        Integer maxInstallments,
        boolean isReducingBalance,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static LoanTypeResponse from(LoanType loanType) {
        return new LoanTypeResponse(
                loanType.getId(),
                loanType.getCode(),
                loanType.getName(),
                loanType.getInterestRateAnnual(),
                loanType.getMaxInstallments(),
                loanType.isReducingBalance(),
                loanType.isActive(),
                loanType.getCreatedAt(),
                loanType.getUpdatedAt()
        );
    }
}
