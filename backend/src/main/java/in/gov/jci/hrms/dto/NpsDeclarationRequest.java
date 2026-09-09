package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record NpsDeclarationRequest(
        @NotNull Long employeeId,
        @NotNull
        @DecimalMin(value = "3.00", message = "must be at least 3.00%")
        @DecimalMax(value = "10.00", message = "must not exceed 10.00%")
        BigDecimal npsPercentage,
        String remarks
) {
}
