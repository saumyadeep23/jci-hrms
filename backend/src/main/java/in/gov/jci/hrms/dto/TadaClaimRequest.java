package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TadaClaimRequest(
        @NotNull Long tourRequestId,
        @NotNull Long employeeId,
        @NotBlank @Size(max = 50) String claimNumber,
        @NotNull @DecimalMin("0.01") BigDecimal outOfPocketClaimed
) {
}
