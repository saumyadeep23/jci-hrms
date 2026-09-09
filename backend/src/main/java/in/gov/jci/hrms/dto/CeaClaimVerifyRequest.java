package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/** adjustedAdmissibleAmount is optional - null keeps the amount computed at submission time; set it only when the verifying officer needs to correct it. */
public record CeaClaimVerifyRequest(
        @DecimalMin(value = "0.00", message = "must not be negative") BigDecimal adjustedAdmissibleAmount
) {
}
