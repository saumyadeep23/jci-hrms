package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.ScaleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PayScaleRequest(
        @NotNull ScaleType scaleType,
        @NotBlank @Size(max = 20) String grade,
        @NotNull @DecimalMin("0.0") BigDecimal minimumBasic,
        @NotNull @DecimalMin("0.0") BigDecimal maximumBasic,
        @NotNull @DecimalMin("0.0") BigDecimal incrementRate,
        @NotNull Boolean active
) {
}
