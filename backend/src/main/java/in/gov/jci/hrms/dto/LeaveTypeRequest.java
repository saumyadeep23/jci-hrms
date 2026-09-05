package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record LeaveTypeRequest(
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("0.0") BigDecimal annualQuota,
        @PositiveOrZero Integer maxAccumulationDays,
        @NotNull Boolean isEncashable,
        @PositiveOrZero Integer careerLimitDays,
        @NotNull Boolean active
) {
}
