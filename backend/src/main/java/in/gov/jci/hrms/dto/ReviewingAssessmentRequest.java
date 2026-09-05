package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ReviewingAssessmentRequest(
        @NotNull @DecimalMin("0.0") BigDecimal reviewingScore,
        @NotBlank String reviewingRemarks
) {
}
