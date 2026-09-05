package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.LoanTypeCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record LoanTypeRequest(
        @NotNull LoanTypeCode code,
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("0.0") BigDecimal interestRateAnnual,
        @NotNull @Positive Integer maxInstallments,
        @NotNull Boolean isReducingBalance,
        @NotNull Boolean active
) {
}
