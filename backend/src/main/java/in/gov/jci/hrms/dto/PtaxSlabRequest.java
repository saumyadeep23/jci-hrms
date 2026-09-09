package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PtaxSlabRequest(
        @NotBlank String stateCode,
        @NotNull @DecimalMin(value = "0", message = "must not be negative") BigDecimal slabMin,
        BigDecimal slabMax,
        @NotNull @DecimalMin(value = "0", message = "must not be negative") BigDecimal taxAmount,
        @Min(1) @Max(12) Integer specialMonth,
        @DecimalMin(value = "0", message = "must not be negative") BigDecimal specialMonthTax,
        @NotNull LocalDate effectiveFrom
) {
}
