package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfStatutoryInterestRate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One notified CPF interest rate entry - GET/POST /api/v1/payroll/trust/interest/rates. */
public record CpfStatutoryInterestRateResponse(
        Long id,
        String finYear,
        BigDecimal baseCpfRate,
        BigDecimal loanMarkupRate,
        BigDecimal effectiveLoanRate,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String ministryOrderNo,
        LocalDate orderDate,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
    public static CpfStatutoryInterestRateResponse from(CpfStatutoryInterestRate r) {
        return new CpfStatutoryInterestRateResponse(r.getId(), r.getFinYear(), r.getBaseCpfRate(), r.getLoanMarkupRate(),
                r.getEffectiveLoanRate(), r.getEffectiveFrom(), r.getEffectiveTo(), r.getMinistryOrderNo(), r.getOrderDate(),
                r.isActive(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
