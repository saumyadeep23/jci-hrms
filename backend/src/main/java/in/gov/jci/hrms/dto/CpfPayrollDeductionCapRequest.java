package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CpfPayrollDeductionCapRequest(
        @NotNull BigDecimal normalPercent, BigDecimal cooperativePercent, String applicability, String legalReference,
        @NotNull LocalDate effectiveFrom, String remarks
) {
}
