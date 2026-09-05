package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CityClass;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * hoursAway and cityClass are supplied by the HR verifier rather than
 * derived automatically - tour_requests only stores dates (no time), and
 * destination is free text with no link to a city_class-bearing master
 * (only ro_master carries city_class). See TadaClaimService Javadoc.
 */
public record TadaClaimVerifyRequest(
        @NotBlank String verifiedBy,
        @NotNull @DecimalMin("0.0") BigDecimal allowedAmount,
        @NotNull @DecimalMin("0.0") BigDecimal hoursAway,
        @NotNull CityClass cityClass
) {
}
