package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** POST /api/v1/payroll/trust/interest/annual-run - CpfInterestComputationService.computeAnnualInterest(). */
public record CpfAnnualInterestRunRequest(
        @NotNull String finYear,
        @NotNull BigDecimal declaredInterestRate,
        @NotBlank String interestOrderNo,
        @NotNull LocalDate interestOrderDate,
        @NotNull Long postedByOfficerId
) {
}
