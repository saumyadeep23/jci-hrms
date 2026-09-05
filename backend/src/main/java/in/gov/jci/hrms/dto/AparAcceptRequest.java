package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.FinalGrading;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AparAcceptRequest(
        @NotNull @DecimalMin("0.0") BigDecimal finalScore,
        @NotNull FinalGrading finalGrading
) {
}
