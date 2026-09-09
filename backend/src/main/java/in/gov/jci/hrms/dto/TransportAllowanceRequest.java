package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransportAllowanceRequest(
        @NotNull Long gradeScaleId,
        @NotNull @Pattern(regexp = "X|Y|Z", message = "must be X, Y or Z") String cityClass,
        @NotNull @DecimalMin(value = "0", message = "must not be negative") BigDecimal baseRate,
        @NotNull LocalDate effectiveFrom
) {
}
