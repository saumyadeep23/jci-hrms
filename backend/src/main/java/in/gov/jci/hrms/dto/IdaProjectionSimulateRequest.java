package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.ScaleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IdaProjectionSimulateRequest(
        @NotNull ScaleType scaleType,
        @NotNull @DecimalMin(value = "0.00", message = "must not be negative") BigDecimal newDaRate,
        @NotNull LocalDate effectiveFrom,
        @NotNull @Min(1) @Max(12) Integer drawalMonth,
        @NotNull @Min(2000) Integer drawalYear
) {
}
