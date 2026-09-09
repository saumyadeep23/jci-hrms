package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayrollHraRateRequest(
        @NotNull @Pattern(regexp = "X|Y|Z", message = "must be X, Y or Z") String cityClass,
        @NotNull @DecimalMin(value = "0.00", message = "must not be negative") @DecimalMax(value = "100.00", message = "must not exceed 100.00")
        BigDecimal ratePercentage,
        @NotNull @DecimalMin(value = "0.00", message = "must not be negative") BigDecimal minAmount,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @Size(max = 255) String remarks
) {
}
