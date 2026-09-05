package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.ScaleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** effectiveTo is intentionally absent - it's server-computed by DaRateHistoryService.create(), never client-supplied. */
public record DaRateHistoryRequest(
        @NotNull ScaleType scaleType,
        @NotNull LocalDate effectiveFrom,
        @NotNull @DecimalMin("0.0") BigDecimal daPercentage,
        @NotNull Boolean active,
        @Size(max = 50) String orderNumber,
        LocalDate orderDate,
        @Size(max = 255) String remarks
) {
}
