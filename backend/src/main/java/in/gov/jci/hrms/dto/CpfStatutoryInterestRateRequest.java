package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

/** POST /api/v1/payroll/trust/interest/rates - CpfStatutoryInterestRateService.create(). effectiveFrom/effectiveTo and effectiveLoanRate are server-computed, never client-supplied - see the entity's constructor. */
public record CpfStatutoryInterestRateRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{4}-[0-9]{4}$", message = "finYear must be in \"YYYY-YYYY\" form") String finYear,
        @NotNull @DecimalMin("0.0") BigDecimal baseCpfRate,
        @NotNull @DecimalMin("0.0") BigDecimal loanMarkupRate,
        @NotBlank String ministryOrderNo,
        @NotNull LocalDate orderDate
) {
}
